# framework-openapi

API 문서(Swagger UI / OpenAPI 3) 자동 노출. springdoc 기반.

## 켜는 법
```gradle
dependencies { implementation project(':framework:framework-openapi') }
```
```yaml
framework:
  openapi:
    enabled: true      # 끄면(또는 미설정) 문서 빈 미등록
```

## 쓰는 법
기동 후 `/swagger-ui.html`(또는 springdoc 기본 경로)에서 확인. 컨트롤러의 표준 어노테이션(`@Operation`, `@Schema` 등)이 그대로 반영된다. 제목/버전 등 메타는 `OpenApiProperties` 로 조정.

> 운영 노출 시 `/v3/api-docs`·`/swagger-ui/**` 를 게이트웨이/시큐리티에서 적절히 보호한다.

## 끄는 법
`framework.openapi.enabled: false` 또는 의존성 미포함.

## 덮어쓰기(프로젝트 커스텀)
프로젝트가 `OpenAPI`/`GroupedOpenApi` 빈을 직접 등록하면 그대로 사용된다.

## 버전 관리
springdoc 버전은 `gradle/libs.versions.toml`(`springdocVersion`). 변경 시 `STACK.md` 갱신.
