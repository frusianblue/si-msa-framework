package com.company.framework.file.sftp.cred;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 키 회전 결정 로직: 최초 로드 / 인터벌 게이트 / 변경감지 재로드 / 동일지문 무재로드 / 재로드 실패 시 기존 유지·재시도 /
 * 인터벌<=0 매번 확인. 가짜 시계로 결정적 검증.
 */
class ReloadingSftpCredentialProviderTest {

    @Test
    @DisplayName("최초 current() 가 1회 로드한다")
    void firstLoad() {
        AtomicInteger loads = new AtomicInteger();
        ReloadingSftpCredentialProvider rp = new ReloadingSftpCredentialProvider(
                () -> {
                    loads.incrementAndGet();
                    return SftpCredentials.password("v1");
                },
                () -> "fp1",
                1000,
                () -> 0L);
        assertThat(rp.current().password()).isEqualTo("v1");
        assertThat(loads.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("인터벌 이내에는 지문이 바뀌어도 재확인하지 않는다")
    void withinIntervalNoRecheck() {
        AtomicInteger loads = new AtomicInteger();
        AtomicReference<String> fp = new AtomicReference<>("fp1");
        AtomicReference<String> ver = new AtomicReference<>("v1");
        AtomicLong t = new AtomicLong(0);
        ReloadingSftpCredentialProvider rp = new ReloadingSftpCredentialProvider(
                () -> {
                    loads.incrementAndGet();
                    return SftpCredentials.password(ver.get());
                },
                fp::get,
                1000,
                t::get);
        rp.current();
        fp.set("fp2");
        ver.set("v2");
        t.set(500);
        assertThat(rp.current().password()).isEqualTo("v1");
        assertThat(loads.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("인터벌 경과 + 지문 변경 → 재로드, 동일 지문 → 무재로드")
    void reloadOnChangeOnly() {
        AtomicInteger loads = new AtomicInteger();
        AtomicReference<String> fp = new AtomicReference<>("fp1");
        AtomicReference<String> ver = new AtomicReference<>("v1");
        AtomicLong t = new AtomicLong(0);
        ReloadingSftpCredentialProvider rp = new ReloadingSftpCredentialProvider(
                () -> {
                    loads.incrementAndGet();
                    return SftpCredentials.password(ver.get());
                },
                fp::get,
                1000,
                t::get);
        rp.current(); // load v1
        fp.set("fp2");
        ver.set("v2");
        t.set(1500);
        assertThat(rp.current().password()).isEqualTo("v2");
        assertThat(loads.get()).isEqualTo(2);
        t.set(3000); // 지문 동일
        assertThat(rp.current().password()).isEqualTo("v2");
        assertThat(loads.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("재로드 실패 시 기존 자격증명을 유지하고 다음 주기에 재시도한다")
    void reloadFailureKeepsOld() {
        AtomicInteger loads = new AtomicInteger();
        AtomicReference<String> fp = new AtomicReference<>("x");
        AtomicReference<String> ver = new AtomicReference<>("a");
        AtomicReference<Boolean> boom = new AtomicReference<>(false);
        AtomicLong t = new AtomicLong(0);
        ReloadingSftpCredentialProvider rp = new ReloadingSftpCredentialProvider(
                () -> {
                    loads.incrementAndGet();
                    if (boom.get()) {
                        throw new RuntimeException("disk error");
                    }
                    return SftpCredentials.password(ver.get());
                },
                fp::get,
                1000,
                t::get);
        assertThat(rp.current().password()).isEqualTo("a");
        fp.set("y");
        boom.set(true);
        ver.set("b");
        t.set(1500);
        assertThat(rp.current().password()).isEqualTo("a"); // 기존 유지
        assertThat(loads.get()).isEqualTo(2);
        boom.set(false);
        t.set(3000);
        assertThat(rp.current().password()).isEqualTo("b"); // 재시도 성공
        assertThat(loads.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("checkInterval<=0 이면 매 호출마다 확인한다")
    void intervalZeroChecksEveryCall() {
        AtomicInteger loads = new AtomicInteger();
        AtomicReference<String> fp = new AtomicReference<>("p");
        AtomicReference<String> ver = new AtomicReference<>("1");
        ReloadingSftpCredentialProvider rp = new ReloadingSftpCredentialProvider(
                () -> {
                    loads.incrementAndGet();
                    return SftpCredentials.password(ver.get());
                },
                fp::get,
                0,
                () -> 0L);
        rp.current();
        fp.set("q");
        ver.set("2");
        assertThat(rp.current().password()).isEqualTo("2");
        assertThat(loads.get()).isEqualTo(2);
    }
}
