package com.company.framework.file.sftp.cred;

/**
 * 세션 생성 시점에 현재 SFTP 자격증명을 공급하는 SPI. 매 세션 생성마다 {@link #current()} 를 호출하므로,
 * 키 회전 구현({@link ReloadingSftpCredentialProvider})은 <b>새 세션</b>부터 새 자격증명을 반영할 수 있다.
 * (기존 세션은 그대로 유지되고, 풀의 maxLifetime 으로 점진 교체된다.)
 */
public interface SftpCredentialProvider {

    /** 지금 인증에 사용할 자격증명. */
    SftpCredentials current();

    /** 기동 시 1회 로드해 고정하는 기본 구현(키 회전 미사용 시 = 기존 동작). */
    static SftpCredentialProvider fixed(SftpCredentials credentials) {
        SftpCredentials c = (credentials == null) ? SftpCredentials.password(null) : credentials;
        return () -> c;
    }
}
