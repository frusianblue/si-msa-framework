# framework-mybatis

MyBatis 공통 설정 **[코어]**. 카멜케이스 매핑·감사필드 자동주입·암호화 타입핸들러·현재 사용자 주입을 제공한다.

## 켜는 법
`framework-core` 위에 얹히는 [코어] 모듈. DB 를 쓰는 서비스가 의존하면 별도 토글 없이 적용된다.
```gradle
dependencies { implementation project(':framework:framework-mybatis') }
```
> MyBatis 매퍼 스캔 시 **`@MapperScan(annotationClass = Mapper.class)`** 를 반드시 지정한다. 없으면 SPI 인터페이스까지 스캔돼 `ConflictingBeanDefinitionException` 이 난다.

## 쓰는 법

**감사필드 자동주입** — `AuditFieldInterceptor` 가 INSERT/UPDATE 시 `created_by/created_at/updated_by/updated_at` 을 `CurrentUserProvider`(로그인 사용자) 기준으로 채운다. 도메인 엔티티가 해당 컬럼을 가지면 자동.

**컬럼 암호화** — `EncryptedStringTypeHandler` 로 특정 컬럼을 AES-GCM 저장/복호화. 매퍼 XML 또는 `@Result(typeHandler = ...)` 로 지정(키는 core `AesCryptoService` 재사용).

**카멜케이스** — `user_name`(DB) ↔ `userName`(Java) 자동 매핑(`MyBatisConfig`).

**예외 변환** — `PersistenceExceptionHandler` 가 DB 예외를 표준 `ErrorCode` 로 정리.

## 끄는 법
[코어]라 토글로 끄지 않는다. DB 가 없는 서비스(gateway 등)는 이 모듈을 의존하지 않으면 된다.

## 덮어쓰기(프로젝트 커스텀)
`CurrentUserProvider` 빈을 프로젝트가 등록하면 감사필드의 사용자 해소 전략을 교체할 수 있다(`@ConditionalOnMissingBean`).

## 버전 관리
mybatis-spring-boot-starter 버전은 `gradle/libs.versions.toml`(`mybatisStarterVersion`). 변경 시 `STACK.md` 갱신.
