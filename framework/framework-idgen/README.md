# framework-idgen (공통 채번)

두 가지 채번을 제공한다.
- **`IdGenerator`** — Snowflake 분산 고유 ID(키 없는 long, 주로 엔티티 PK). DataSource 불필요.
- **`CodeGenerator`** — 업무코드(접두사+일자+0패딩 순번, 예: `ORD20260531000123`). **DataSource 가 있을 때만** 자동 등록(테이블 기반, H2/PostgreSQL 공통).

## 켜는 법
`build.gradle`:
```gradle
dependencies { implementation project(':framework:framework-idgen') }
```
`application.yml`:
```yaml
framework:
  idgen:
    enabled: true
    snowflake:
      node-id: -1            # -1=HOSTNAME 자동. 운영은 인스턴스별 명시 권장(아래 주의)
      epoch: 1704067200000   # 2024-01-01 UTC
    sequence:
      table-name: id_sequence
      initialize: true       # 시작 시 CREATE TABLE IF NOT EXISTS
      default-pad: 6
```

## 쓰는 법
```java
private final IdGenerator idGenerator;
long pk = idGenerator.nextLong();            // 분산 고유 ID

private final CodeGenerator codeGenerator;   // DataSource 있을 때 주입 가능
String order = codeGenerator.next("ORD", "yyyyMMdd", 6);  // ORD20260531000123
String memberNo = codeGenerator.next("M");                 // M000123
```
- `next(prefix, datePattern, pad)` 은 카운터 키에 일자를 포함해 **기간이 바뀌면 자동으로 1부터** 재시작한다.
- 채번 테이블: `id_sequence(seq_key VARCHAR(100) PK, seq_value BIGINT)`. `initialize:true` 면 자동 생성.

## 주의 (운영 다중 인스턴스)
- Snowflake 의 유일성은 **인스턴스별 node-id 가 달라야** 보장된다. `node-id:-1` 은 `HOSTNAME` 해시로
  산출하므로 충돌 가능성이 0은 아니다. k8s 라면 StatefulSet 서수나 Downward API(`POD_NAME`)를
  환경변수로 주입해 인스턴스별 고정 node-id 를 주는 것을 권장(HANDOFF 의 redis 다중 인스턴스 주의와 동일 맥락).
- `CodeGenerator` 의 테이블 채번은 행 잠금으로 단조 증가를 보장하므로 다중 인스턴스에서도 안전하다.

## 끄기 / 우아한 축소 / override
- 끄기: `framework.idgen.enabled:false` 또는 의존성 제거.
- DataSource 가 없는 서비스: `IdGenerator`(Snowflake)만 등록되고 `CodeGenerator`는 스킵된다.
- 프로젝트가 `IdGenerator`/`SequenceStore`/`CodeGenerator` 빈을 직접 등록하면 `@ConditionalOnMissingBean` 으로 양보.
- 새 외부 라이브러리 없음 → `libs.versions.toml`/`STACK.md` 변경 불필요(jdbc/tx 는 호스트 제공).
