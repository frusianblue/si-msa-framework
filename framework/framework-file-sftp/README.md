# framework-file-sftp

`framework-file` 의 `FileStorage` SPI 에 **SFTP(원격 SSH) 백엔드**를 더하는 선택형 모듈이다.
`framework.file.storage.type=sftp` 일 때만 활성화되며, 전송은 [Apache MINA SSHD](https://mina.apache.org/sshd-project/) (`sshd-core` + `sshd-sftp`) 에 위임한다(순수 JDK SSH 가 없어 라이브러리 필수 — BOM 밖 의존성, 이 모듈에만 포함하고 `implementation` 으로 비노출).

S3 백엔드와 동일하게 `RangeReadableStorage`(부분 다운로드)를 구현하므로, 컨트롤러의 HTTP Range(206) 처리를 그대로 쓸 수 있다.

## 활성화

```yaml
framework:
  file:
    storage:
      type: sftp            # 이 값일 때만 SFTP 저장소 빈이 등록된다
      sftp:
        host: sftp.example.com
        port: 22
        username: appuser
        # 인증: 비밀번호 / 개인키 / 둘 다(키 우선, 비번 폴백)
        password: ${SFTP_PASSWORD}
        private-key-path: /etc/secrets/id_ed25519
        private-key-passphrase: ${SFTP_KEY_PASSPHRASE}
        base-dir: /home/appuser/upload   # 비우면 서버 홈 상대
        # 호스트키 검증: 기본 strict(known_hosts 에 없으면 거부 = fail-closed)
        strict-host-key-checking: true
        known-hosts-path: /etc/ssh/known_hosts   # 비우면 ~/.ssh/known_hosts
        connect-timeout: 10s
        auth-timeout: 10s
```

키는 `yyyy/MM/dd/{uuid}.{ext}` 로 저장되며, 상위 디렉토리는 자동 생성(mkdir -p)되고 없는 파일 삭제는 멱등이다.

> ⚠️ `strict-host-key-checking: false` 는 모든 호스트 키를 수용한다(중간자 공격에 취약). 개발 편의용이며 운영에서는 `true` + `known_hosts` 를 쓴다.

## 연결 풀 (옵트인)

기본은 **작업마다 세션을 새로 열고 닫는다**(예측 가능·stale 연결 회피). 고처리량에서 SSH/TCP/인증 핸드셰이크 비용이 부담되면 세션 풀을 켠다 — 인증된 세션을 재사용한다.

```yaml
framework:
  file:
    storage:
      sftp:
        pool:
          enabled: true
          max-total: 8        # 동시 보유(대여+유휴) 상한
          max-wait: 10s        # 풀 고갈 시 대여 대기 상한(초과 시 작업 실패)
          max-idle: 5m         # 유휴 만료(누적 방지)
          max-lifetime: 30m    # 생성 후 수명 — 키 회전 전파에 중요(아래)
```

- 대여 직전 `isOpen()` 으로 검증해 끊긴 세션은 폐기·재생성한다(validate-on-borrow).
- `max-idle` 을 넘긴 유휴 세션, `max-lifetime` 을 넘긴 세션은 대여 시점에 교체된다.
- `load`/`loadRange` 가 돌려주는 스트림은 세션을 물고 있다가 **스트림 close 시 세션을 풀에 반납**한다. 스트림을 반드시 닫아야(try-with-resources) 세션이 누수되지 않는다.

## 키 회전 (옵트인)

기본은 기동 시 1회 키를 로드해 고정한다. 키 파일을 운영 중 교체(로테이션)하려면 켠다 — `private-key-path` 파일의 변경(mtime+size)을 주기적으로 감지해 자격증명을 다시 읽는다.

```yaml
framework:
  file:
    storage:
      sftp:
        key-rotation:
          enabled: true
          check-interval: 60s   # 파일 변경 확인 최소 간격
```

- 자격증명은 **세션 생성 시점마다** 해석되므로, 키가 바뀌면 **새 세션부터** 새 키로 인증한다.
- 풀과 함께 쓰면, 옛 키로 인증된 기존 세션은 `pool.max-lifetime` 이 지나면 교체되어 점진적으로 새 키로 넘어간다. 즉시 전면 전환이 필요하면 풀을 잠시 비활성화하거나 `max-lifetime` 을 짧게 둔다.
- 재로드가 실패하면(파일 일시적 오류 등) **기존 자격증명을 유지**하고 다음 주기에 재시도한다(가용성 우선).
- `private-key-path` 가 없으면(비밀번호 인증만) 회전 대상이 없으므로 이 설정은 무시된다.

## 설계 메모

- 순수 알고리즘(연결 풀 `pool/BoundedObjectPool`, 키 회전 결정 `cred/ReloadingSftpCredentialProvider`, 자격증명 홀더 `cred/SftpCredentials`)은 **SSHD 무의존**으로 분리되어 JDK 단독 단위테스트가 가능하다. SSHD 에 닿는 부분(`SftpKeyLoader`, `SftpFileStorage` 의 세션 개폐)은 내장 MINA SFTP 서버 왕복 테스트로 검증한다.
- 풀 알고리즘은 범용(`BoundedObjectPool<T>`)이라 생성/검증/파기 훅을 주입받는다. SFTP 세션에는 생성=연결+인증, 검증=`isOpen`, 파기=세션 close 로 배선된다.

## 검증

```bash
./gradlew :framework:framework-file-sftp:test          # 경로/Range 단위 · 풀 · 키회전 · 오토컨피그 · 내장 MINA 서버 왕복(비풀/풀)
./gradlew :framework:framework-file-sftp:spotlessApply
./gradlew :framework:framework-archtest:test           # 아키텍처 규칙(모듈 순환/Jackson2/규약)
```
