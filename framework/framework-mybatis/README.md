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


## 실전 사용 예 (코드)

엔티티가 `BaseEntity` 를 상속하면 생성/수정자·일시가 `AuditFieldInterceptor` 에 의해 자동 주입된다(현재 사용자는 `CurrentUserProvider` 로 해석). 민감 컬럼은 `EncryptedStringTypeHandler` 로 투명 암복호.
```java
// com.company.framework.mybatis.support.BaseEntity 상속
public class Member extends BaseEntity {   // createdAt/createdBy/updatedAt/updatedBy 자동
    private Long id;
    private String name;
    private String rrn;   // 주민번호 — 암호화 대상
}

@Mapper
public interface MemberMapper {
    @Insert("INSERT INTO member(name, rrn) VALUES(#{name}, "
          + "#{rrn, typeHandler=com.company.framework.mybatis.handler.EncryptedStringTypeHandler})")
    void insert(Member m);   // 저장 시 자동 암호화, 조회 시 자동 복호
}
```
현재 사용자 주입을 커스터마이즈하려면 `CurrentUserProvider` 빈을 등록(기본은 SecurityContext 기반):
```java
@Bean CurrentUserProvider currentUserProvider() {
    return () -> Optional.ofNullable(ContextHolder.get().userId());
}
```

## 끄는 법
[코어]라 토글로 끄지 않는다. DB 가 없는 서비스(gateway 등)는 이 모듈을 의존하지 않으면 된다.

## 덮어쓰기(프로젝트 커스텀)
`CurrentUserProvider` 빈을 프로젝트가 등록하면 감사필드의 사용자 해소 전략을 교체할 수 있다(`@ConditionalOnMissingBean`).

## 버전 관리
mybatis-spring-boot-starter 버전은 `gradle/libs.versions.toml`(`mybatisStarterVersion`). 변경 시 `STACK.md` 갱신.
