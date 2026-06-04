# SPOTLESS_NOTES.md — 코드 포맷 규약

## 포맷터
**Palantir Java Format** (`palantirJavaFormat()`) — google-java-format 의 포크(라인 폭 등 차이). google-java-format 아님.

## 무엇을 검사하나 (개정 후)
| 대상 | 위치 | 적용 단계 |
|---|---|---|
| Java | `subprojects` (모듈별 `src/**/*.java`) | palantir + removeUnusedImports + importOrder + formatAnnotations + toggleOffOn + 화이트스페이스 |
| Gradle | 루트 재귀(`**/*.gradle`, `settings.gradle`) | 화이트스페이스 위생만 (greclipse 선택) |
| YAML | 루트 재귀(`**/*.yml`,`**/*.yaml`) | 화이트스페이스 위생만 (jackson 선택) |
| SQL | 루트 재귀(`**/*.sql`) | 화이트스페이스 위생만 (dbeaver 선택) |
| Markdown | 루트 재귀(`**/*.md`) | endWithNewline 만(줄끝 공백 줄바꿈 보존) |
| misc | `.gitignore`/`.gitattributes` | 화이트스페이스 위생 |

> 개정 전엔 **Java 만**, 그것도 `subprojects` 안이라 **루트 `build.gradle`/`settings.gradle` 은 미검사**였다.
> 이제 루트에도 spotless 를 적용해 전 모듈의 gradle/yaml/sql/md 까지 한곳에서 본다.

## 이번에 추가한 옵션과 이유
- **`encoding 'UTF-8'`** — 한글 주석이 많다. 미지정 시 Windows 는 플랫폼 charset(MS949)로 읽어 **한글 주석이 깨질 수 있다.** (가장 중요)
- **`toggleOffOn()`** — `// spotless:off` ~ `// spotless:on` 구간 포맷 제외(수동 정렬/ASCII 표 보호).
- **`formatAnnotations()`** — 타입 애너테이션 줄정리. spotless 8.x 기본 목록에 `@Valid`/jakarta 제약이 포함돼, 이번에 추가한 `LoginCommand`/컨트롤러 애너테이션과도 맞물린다.

## 의도적으로 "약하게" 둔 것 (의미 보존)
YAML/SQL/Gradle 에 공격적 재포맷터(`yaml{jackson()}`, `sql{dbeaver()}`, `groovyGradle{greclipse()}`)를 **걸지 않았다.**
- YAML: 멀티문서(`---`)·앵커·따옴표·들여쓰기를 바꾸면 Spring 설정 의미가 깨질 위험.
- SQL: 키워드 대문자화/재들여쓰기가 수기 정렬 DDL 과 한글 주석 정렬을 흐트러뜨림.
- Gradle: 주석·블록 정렬이 재배치됨.
필요하면 각 블록 주석의 옵션을 켜서 단계적으로 강화하면 된다.

## 설정 캐시(Configuration Cache) 호환 ★
이 레포는 `org.gradle.configuration-cache=true`. spotless 의 **기본 줄바꿈 정책 `GIT_ATTRIBUTES`** 는
설정 캐시에 직렬화 불가능한 provider(`lineEndingsPolicy`)를 잡아 `:spotlessGroovyGradle` 등에서
다음 에러로 빌드를 깬다(gradle#19113, spotless#987):
```
Configuration cache state could not be cached: field `lineEndingsPolicy` ...
  error writing value of type 'org.gradle.api.internal.provider.DefaultProvider'
> Failed to create MD5 hash for file '...\.gradle\8.14\checksums\checksums.lock'
```
**해결**: 줄바꿈 정책을 정적 값으로 고정 → 두 spotless 블록에 적용했다.
```gradle
spotless {
    lineEndings = com.diffplug.spotless.LineEnding.UNIX   // GIT_ATTRIBUTES provider 제거
    ...
}
```
LF 로 고정한 이유: Windows 개발 + Linux CI 에서 줄바꿈이 갈리면(`PLATFORM_NATIVE`) CI 의 `spotlessCheck`
가 깨진다. LF 통일이 표준.

**적용 순서**(이미 에러가 났다면 캐시가 오염돼 있을 수 있음 → 먼저 비운다):
```bash
rm -rf .gradle/configuration-cache    # PowerShell: Remove-Item -Recurse -Force .gradle\configuration-cache
./gradlew spotlessApply
./gradlew spotlessCheck
```

**그래도 안 되면(폴백)** — Gradle/spotless 버전 따라 다른 필드가 또 걸릴 수 있다. 택1:
- 해당 실행만 캐시 끄기: `./gradlew spotlessApply --no-configuration-cache`
- spotless 태스크만 캐시 대상 제외(루트 build.gradle 끝):
  ```gradle
  tasks.withType(com.diffplug.gradle.spotless.SpotlessTask).configureEach {
      notCompatibleWithConfigurationCache('spotless + config cache 이슈 우회')
  }
  ```
  (이 태스크가 그래프에 있을 때만 설정 캐시 비활성 → 일반 build/test 는 캐시 유지)

## 도입 시 1회 필요 작업 ★
새로 yaml/sql/md/gradle 까지 검사하므로, 기존 파일에 줄끝 공백/마지막 줄바꿈 누락이 있으면 `spotlessCheck`(=CI 게이트)가 처음엔 실패할 수 있다. **한 번 정렬**해 두면 이후 안정:
```bash
./gradlew spotlessApply      # 전체 정렬(diff 발생 → 커밋)
./gradlew spotlessCheck      # 이후엔 통과
```
대량 diff 가 부담되면 루트 spotless 의 `ratchetFrom 'origin/master'` 주석을 해제해 **변경분만** 게이트할 수 있다.
