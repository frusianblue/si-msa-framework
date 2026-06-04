# 샘플 코드 색인 (Samples)

> 복붙 가능한 최소 예제 모음. (🚧 다음 단계에서 코드 추가 — 아래는 색인 골격)
> 현재 살아있는 레퍼런스 구현은 **`services/user-service`** (프레임워크 사용 예시 전반).

## 작성 예정 샘플

| # | 주제 | 사용 모듈 | 비고 |
|---|---|---|---|
| 1 | 표준 CRUD 컨트롤러+서비스+매퍼 | core·mybatis | 응답/예외/페이징 |
| 2 | 로그인·RBAC 보호 엔드포인트 | security | JWT·`@PreAuthorize` |
| 3 | 파일 업/다운로드 | file (+s3/sftp) | Range·콘텐츠검증 |
| 4 | Excel 업로드 검증 + 다운로드 | excel | 양식검증·SXSSF |
| 5 | 멱등 결제 요청 | idempotency | `store.type` 별 |
| 6 | Outbox 이벤트 발행 + 멱등 소비 | messaging | Kafka |
| 7 | Saga 오케스트레이션 | saga (+messaging) | 보상 흐름 |
| 8 | 공통코드 조회 + 캐시 | commoncode·cache-redis | |
| 9 | 외부 API 표준 호출 | client | 재시도·서킷 |
| 10 | 소셜 로그인 콜백 → 자체 JWT | oauth-client·security | |

## 참고
- 모듈별 동작·토글: [`MODULE_COMPOSITION.md`](MODULE_COMPOSITION.md)
- 코드에서 쓰는 표준 규약: [`DEVELOPER_GUIDE.md`](DEVELOPER_GUIDE.md)
