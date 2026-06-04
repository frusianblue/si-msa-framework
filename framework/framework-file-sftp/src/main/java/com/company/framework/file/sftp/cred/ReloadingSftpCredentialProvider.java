package com.company.framework.file.sftp.cred;

import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * 키 파일 변경을 감지해 자격증명을 다시 로드하는 {@link SftpCredentialProvider} — <b>키 회전</b> 구현.
 * 실제 키 파일 읽기({@code FileKeyPairProvider})는 {@code loader} 로, 변경 감지 지문(mtime+size 등)은
 * {@code fingerprint} 로 주입받으므로 이 클래스 자체는 SSHD/파일시스템 무의존 → 로직을 JDK 단독으로 검증할 수 있다.
 *
 * <p>동작:
 * <ul>
 *   <li>최초 {@link #current()} 시 1회 로드(실패는 전파 — 기동/최초 사용 단계에서 명확히 실패).
 *   <li>이후엔 {@code checkInterval} 마다만 지문을 확인 — 매 세션 생성마다 파일을 stat 하지 않도록 게이트.
 *   <li>지문이 바뀌었으면 재로드. 재로드 실패 시 <b>기존 자격증명을 유지</b>(가용성 우선)하고 다음 주기에 재시도.
 * </ul>
 */
public final class ReloadingSftpCredentialProvider implements SftpCredentialProvider {

    private final Supplier<SftpCredentials> loader;
    private final Supplier<Object> fingerprint;
    private final long checkIntervalNanos;
    private final LongSupplier clock;

    private final Object lock = new Object();
    private volatile SftpCredentials cached; // 게시(volatile read)
    private Object lastFingerprint; // lock 보호
    private long lastCheckNanos; // lock 보호
    private boolean loaded = false; // lock 보호

    /**
     * @param loader 키 파일에서 자격증명을 읽는 로더(락 밖 호출). 최초 실패는 전파, 재로드 실패는 무시(기존 유지).
     * @param fingerprint 변경 감지 지문(예: mtime+size 묶음). null 가능값 허용.
     * @param checkIntervalNanos 지문 확인 최소 간격(ns). <=0 이면 매번 확인.
     * @param clock 단조 시계(보통 {@code System::nanoTime}).
     */
    public ReloadingSftpCredentialProvider(
            Supplier<SftpCredentials> loader,
            Supplier<Object> fingerprint,
            long checkIntervalNanos,
            LongSupplier clock) {
        this.loader = Objects.requireNonNull(loader, "loader");
        this.fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
        this.checkIntervalNanos = checkIntervalNanos;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public SftpCredentials current() {
        SftpCredentials c = cached;
        long now = clock.getAsLong();

        synchronized (lock) {
            if (!loaded) {
                // 최초 로드 — 실패는 전파.
                SftpCredentials loadedCreds = loader.get();
                cached = loadedCreds;
                lastFingerprint = safeFingerprint();
                lastCheckNanos = now;
                loaded = true;
                return loadedCreds;
            }
            boolean due = checkIntervalNanos <= 0 || (now - lastCheckNanos) >= checkIntervalNanos;
            if (due) {
                lastCheckNanos = now;
                Object fp = safeFingerprint();
                if (!Objects.equals(fp, lastFingerprint)) {
                    try {
                        SftpCredentials reloaded = loader.get();
                        cached = reloaded;
                        lastFingerprint = fp; // 성공 시에만 지문 갱신 → 실패 시 다음 주기 재시도
                        return reloaded;
                    } catch (RuntimeException e) {
                        // 재로드 실패: 기존 자격증명 유지(가용성 우선). 지문 미갱신 → 다음 주기 재시도.
                        return cached;
                    }
                }
            }
            return cached;
        }
    }

    private Object safeFingerprint() {
        try {
            return fingerprint.get();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
