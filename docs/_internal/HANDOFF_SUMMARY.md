# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 진입점은 `../00_INDEX.md`, 전체 맥락은 `HANDOFF.md`(같은 폴더), 사용법은 `../../README.md`, 버전은 `../reference/STACK.md`, 모듈 설계는 `../FRAMEWORK_MODULES.md`. (문서는 역할별 재구조화됨 — 작업기록은 `_internal/`.)
> **배치·태스크·스케줄(Spring Cloud Task + Batch + Quartz UI) 트랙은 별도 한 장 `HANDOFF_BATCH_SUMMARY.md`(같은 폴더) 참조.**

---
<!-- 갱신 시작 -->

## 이번 세션 한 줄 요약
**🟢 devops 섹션 종료 — CI/CD 1차 완주 + 관측 노출 + 문서 정리(2026-06-07 세션5–8, devops).** 한 호흡으로: (5)태그 전략 B(불변 git-sha declarative 주입, 가변 :dev 폐기) (6)Kaniko 서비스별 컨테이너 다중빌드 (7)deploy 결함 2종(ns ClusterRole + ServiceMonitor 분리) → **실잡에서 6파드 `1/1 Running`, 4서비스 전부 `harbor.local/si-msa/*:cd161c73c135` 단일 sha 핀 실측** = **CI/CD 빌드→push→배포 완주**. (8)관측 Grafana/Prometheus 를 B안 ingress(`grafana.local`/`prometheus.local`)로 노출, JVM 대시보드 4서비스 실데이터 확인 = **관측 마감**. 마지막으로 **문서 정리**: §7 append(C/D/E/F) → HANDOFF.md §7 본문 병합, 해결 planning 2건(Kaniko·ingress-host-access) archive 복사. **이번 섹션은 여기서 닫고, 다음 섹션은 아래 '다음 할 일' 메뉴에서 시작.**

## 최종 갱신
- 일자: 2026-06-07 · 갱신자: 세션8(섹션 종료 + 문서 정리)
- 대상 브랜치: master · 환경: 프레임워크/스택 무변경(devops). 코드(세션5–8) 커밋됨. **이번 정리 산출(문서) + 받는 쪽 git rm/mv 미반영.**

## 직전에 한 것 (Done)
| 단계 | 산출/검증 |
|---|---|
| CI/CD 완주(5–7) | 태그 B(sentinel+pin-image-tag.sh)·Kaniko 4컨테이너 순차·ns ClusterRole·SM 분리 → 6파드 Running·단일 sha 핀 실측. |
| 관측 ingress(8) | monitoring-values.yaml(ingress+ssl-redirect=false+grafana domain) · 05/06 ingress 우선 → grafana.local/prometheus.local 실측. |
| 문서 정리(8) | §7 에 세션5–8 병합 · 해결 planning 2건 archive 복사(배너) · 이 SUMMARY 종료판 · PITFALLS §9 누적(CI/CD·관측). |

## 현재 상태 (적용/검증)
- **CI/CD**: ✅ 완주. `si-msa-cd` build(Kaniko 4컨테이너 순차)→push(:<sha> 단일)→pin→apply→6파드 rollout 그린.
- **관측**: ✅ Grafana/Prometheus ingress 노출 + JVM 대시보드 실데이터. (알람/RED/tracing 은 보류.)
- **문서**: §7 최신화, PITFALLS §9 누적 완료. 다음 섹션 진입점 = 이 SUMMARY + 아래 메뉴.
- **커밋**: 코드 push 됨. **받는 쪽 잔여 git 작업** = (a) append 파일 5개 `git rm` (내용 §7 병합됨) (b) 해결 planning 2건 `git rm`(archive 복사본은 zip 동봉) (c) 정리 산출 commit/push.

## 바로 다음 할 일 (Next) — 다음 섹션 메뉴
> 권고 순서: 1(위생) → 2(CI 품질게이트). 트랙은 시작 시 Chae 가 택1.
1. **위생 마무리** — prod overlay `:latest` → dev 와 동일 sentinel+`pin-image-tag.sh` 주입 전환(가변-태그 부채 청산). + 아래 '문서 정리 git 작업' 반영.
2. **CI 품질게이트(devops 최대 공백)** — 지금은 사실상 CD 만(Dockerfile.build `-x test`). 이미지 빌드 *전* test+JaCoCo+Spotless/Palantir+**ArchUnit**(모듈 경계) 게이트 + Harbor **Trivy** 스캔 + rollout 실패 자동롤백/승인.
3. **prod 하드닝** — 시크릿(External Secrets/Sealed/Vault)·TLS(cert-manager)·HPA 실부하 수용시험·로그수집(Loki).
4. **(보류) 관측 심화** — PrometheusRule+Alertmanager(알람), HTTP RED 대시보드, 분산추적(Tempo/Zipkin).
5. **(devops 밖) auth 보안/영속 분리** — `framework-security-rbac-mybatis` 어댑터(설계 잠김, 구현만). README 실전예제 rollout(mybatis/oauth-client/audit/observability/secure-web).

## 문서 정리 — 받는 쪽 git 작업(이번 정리 반영용)
```
# append 파일(내용 §7 본문 병합 완료) 제거
git rm docs/_internal/HANDOFF_SECTION7_APPEND_2026-06-07-{B,C,D,E,F}.md
# 해결 planning → archive 이동(archive 복사본은 zip 으로 추가됨)
git rm docs/_internal/planning/NEXT_CI_KANIKO_MULTIBUILD.md
git rm docs/_internal/planning/NEXT_INGRESS_HOST_ACCESS.md
# (검토) 레포 루트 잔여물 — 의도된 커밋 아니면 제거
git rm docs/_internal/c.txt docs/_internal/cookies.txt   # ← 내용 확인 후
```
- `NEXT_K8S_REAL_DEPLOY.md` 는 docker-desktop kind 전제(은퇴) → standalone 트랙이 대체. 다음 섹션에서 archive 여부 검토.
- `apply-notes/`·`c.txt`·`cookies.txt` 는 작업 잔여물로 보임 — 내용 확인 후 정리 권고.

## 이번 섹션 함정/원칙 (되돌리지 말 것 · 상세 PITFALLS §9)
- **무엇이 뜨는가 = 불변 태그 declarative 핀**(가변 채널 태그·명령형 set image 금지). overlay 기본값=fail-loud sentinel.
- **Kaniko = 컨테이너당 executor 1회·순차**(공유 builder 캐시 보존).
- **CI 배포 SA = ns 객체(클러스터 스코프)에 명시적 ClusterRole 필요**(namespaced edit 불충분, 생성은 admin). **옵셔널 operator CRD 의존 리소스는 코어 배포에서 분리**(add-on 단독 소유).
- **관측 UI ingress = ssl-redirect:false + grafana domain/root_url. 노드 이름해소 불요**(호스트 hosts 한 줄). Harbor(노드 pull)와 결정적 차이.
- **써머리 위치 = `docs/_internal/HANDOFF_SUMMARY.md`**. 배치 트랙 = `HANDOFF_BATCH_SUMMARY.md` 독립.

<!-- 갱신 끝 -->
