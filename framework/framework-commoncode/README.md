# framework-commoncode

공통코드(코드 그룹/코드) CRUD + 조회 캐시. 코드성 데이터를 DB 로 관리하고 그룹 단위로 캐시한다.

## 켜는 법
```gradle
dependencies { implementation project(':framework:framework-commoncode') }   // core+mybatis 전제
```
```yaml
framework:
  commoncode:
    enabled: true     # 기본 true (matchIfMissing). 끄려면 false
```
> enabled 시 MyBatis 매퍼가 동작하므로 DataSource 가 필요하다.

## 쓰는 법
```java
private final CommonCodeService codes;

List<CommonCodeDto> list = codes.getByGroup("GENDER");   // 그룹 조회(캐시 "commonCodes")
List<String> groups     = codes.getAllGroups();
codes.create(form);   // ADMIN — 해당 그룹 캐시 자동 무효화
codes.update(form);
codes.delete("GENDER", "M");
```
REST 는 `CommonCodeController`(조회 공개, 변경은 `@PreAuthorize` ADMIN). DTO 매핑은 MapStruct(`CommonCodeStructMapper`).

> 같은 그룹 2회 조회 시 2번째는 캐시 히트. 캐시는 core Caffeine(또는 framework-cache-redis 로 분산).


## 실전 사용 예 (코드)

`CommonCodeService` 로 그룹별 코드를 읽고 관리한다(드롭다운/공통분류 등). 내장 컨트롤러도 함께 제공된다.
```java
// com.company.framework.commoncode.service.CommonCodeService
private final CommonCodeService commonCode;

public List<CommonCodeDto> genders() {
    return commonCode.getByGroup("GENDER");          // 그룹의 활성 코드 목록
}
public void addCode() {
    commonCode.create(new CommonCodeForm("GENDER", "X", "선택안함", 30, true));
}
```
```bash
curl http://localhost:8081/api/v1/common-codes/GENDER          # 그룹 코드 조회
curl http://localhost:8081/api/v1/common-codes/groups          # 전체 그룹
```

## 끄는 법
`framework.commoncode.enabled: false` 또는 의존성 미포함.

## 덮어쓰기(프로젝트 커스텀)
`CommonCodeService`/`CommonCodeMapper` 를 프로젝트가 재정의하면 교체된다.

## 버전 관리
MapStruct 버전은 `gradle/libs.versions.toml`(`mapstructVersion`). 신규 외부 의존성 없음.
