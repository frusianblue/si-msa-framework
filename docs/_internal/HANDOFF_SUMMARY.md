# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 진입점은 `../00_INDEX.md`, 전체 맥락은 `HANDOFF.md`(같은 폴더), 사용법은 `../../README.md`, 버전은 `../reference/STACK.md`, 모듈 설계는 `../FRAMEWORK_MODULES.md`. (문서는 역할별 재구조화됨 — 작업기록은 `_internal/`.)
> **배치·태스크·스케줄(Spring Cloud Task + Batch + Quartz UI) 트랙은 별도 한 장 `HANDOFF_BATCH_SUMMARY.md`(같은 폴더) 참조.**

---
<!-- 갱신 시작 -->

## 이번 세션 한 줄 요약
**✅ 재부팅 버그 영구수정 + 🚧 S7 인-클러스터 CI/CD 산출(2026-06-07 세션3).** (1) **디버깅**: 재부팅 후 auth-server 만 `ImagePullBackOff`(`dial tcp 127.0.0.1:443: connection refused`). 원인 복합2 — ① 노드 certs.d 가 **호스트 bind mount 의존**이라 재부팅에 휘발 → harbor.local 이 기본 https:443 폴백, ② base 태그 `:latest`+imagePullPolicy 미명시 → 기본 Always 처럼 동작해 노드캐시 무시·깨진 미러/401 종속. **라이브 패치로 auth-server 1/1 복구**(`patch deploy ... imagePullPolicy IfNotPresent`). (2) **영구화 산출**(드롭인 zip): base 4개 `imagePullPolicy:IfNotPresent`+prod overlay `Always` 패치 / **`registry-trust` DaemonSet**(certs.d 를 노드 부팅마다 재기입 → bind mount 의존 제거, 재부팅 내성) / `07-reboot-recover.sh`(복구 1줄). (3) **인-클러스터 Harbor+Jenkins 결정·산출**: standalone kind 엔 기존 문서의 LB→localhost 매직 없음 → **CP_IP(control-plane 노드 IP) 단일좌표**로 split-horizon 통일(externalURL=`http://CP_IP:30002`, CoreDNS hosts harbor.local→CP_IP, 노드 certs.d→CP_IP:30002). Harbor(Helm NodePort30002·HTTP·영속PVC·trivy off) + Jenkins(Helm NodePort32000·영속·k8s agent) + **Kaniko 파이프라인**(`Jenkinsfile.kind`: build→push harbor.local→set image `:<sha>`→rollout). **⚠️ 저자환경 미검증 — 받는 쪽에서 Stage A→B→C 순차 PASS 게이트로 실증/iterate.**

## 최종 갱신
- 일자: 2026-06-07 · 갱신자: 세션3(재부팅 수정 + S7 인-클러스터 CI/CD)
- 대상 브랜치: master · 환경: 코드/스택 무변경(devops). 미커밋(세션2 S5 산출 + 이번 세션3 전부).

## 직전에 한 것 (Done)
| 단계 | 산출/검증 |
|---|---|
| 재부팅 디버깅 | auth-server ImagePullBackOff 근인 2개 규명. 라이브 `imagePullPolicy IfNotPresent` 패치 → **auth-server 1/1 Running 복구**(받는 쪽 실측). |
| 영구 pull/재부팅 수정 | base/{4} `imagePullPolicy:IfNotPresent` + overlays/prod `Always` 패치. `registry-trust-daemonset.yaml`(certs.d 노드 부팅마다 재기입). `07-reboot-recover.sh`. |
| 인-클러스터 Harbor | `harbor-values.yaml`(NodePort30002/HTTP/영속/trivy off — 차트 키 실검증) + `08-harbor-install.sh`(helm install + si-msa public + CoreDNS harbor.local→CP_IP + certs.d 전환). |
| 인-클러스터 Jenkins | `jenkins-values.yaml`(NodePort32000/영속/agent — 차트 v5.9.22 키 실검증) + `jenkins-rbac.yaml`(SA jenkins-deployer→si-msa edit) + `09-jenkins-install.sh`(install+RBAC+harbor-push-cred). |
| 파이프라인 | `deploy/cicd/Jenkinsfile.kind` — Kaniko podTemplate(kaniko+kubectl), build&push 4서비스 `:<sha>`+`:dev`(`--insecure-registry`), `apply -k overlays/dev`+`set image :<sha>`+rollout. |
| 문서 | `docs/ops/STANDALONE_KIND_HARBOR_JENKINS.md`(런북, 기존 docker-desktop 전제 문서 대체) + `_PITFALLS_APPEND_2026-06-07.md`(§8/§9 병합용). |

## 현재 상태 (적용/검증)
- **클러스터**: standalone `kind-sanity` 3노드. dev overlay 6파드(재부팅 후 라이브패치로 복구). kube-prometheus-stack + JVM 대시보드(세션2 S5).
- **이번 산출**: 전부 **드롭인 zip(미적용)**. Stage A(영구수정)는 고신뢰·저위험, Stage B/C(Harbor/Jenkins/Kaniko split-horizon)는 미검증·iterate 예상.
- **외부 노출**: `kubectl port-forward`(Harbor 포털/Jenkins UI 모두). standalone kind 엔 LB→localhost 없음.

## 바로 다음 할 일 (Next)
1. **드롭인 적용 + Stage A 먼저 PASS** — `unzip -o` 후 base/prod + DaemonSet 적용 + `07-reboot-recover.sh` → 6파드 Ready + 재부팅 후 자동복구 확인.
2. **Stage B Harbor** — `08-harbor-install.sh` → harbor 파드 Running·PVC Bound·포털·si-msa 프로젝트.
3. **Stage C Jenkins+파이프라인** — `09-jenkins-install.sh` → UI 1회 잡 생성(SCM=레포, `Jenkinsfile.kind`) → Build Now → 4 repo×(`<sha>`,`dev`) + `:<sha>` 롤아웃.
4. **막히면**: 해당 단계 `describe pod`/Kaniko 로그/`nslookup harbor.local` 공유 → 환경맞춤 수정(세션3 디버깅과 동일 루프). 최유력 실패점=NodePort 토큰 realm·CoreDNS 해소·Maven egress.
5. **commit/push 백로그** — 세션2 S5 + 세션3 전부 미커밋.

## 이번 세션 함정/원칙 (되돌리지 말 것)
- **재부팅 내성 = certs.d 를 bind mount 가 아니라 DaemonSet 으로** — 호스트 마운트는 Docker Desktop/WSL 재부팅에 휘발. `registry-trust` DaemonSet 이 노드 부팅마다 재기입(containerd 는 certs.d 요청마다 읽음 → 재시작 불요).
- **base 태그 `:latest`+imagePullPolicy 미명시 = 사실상 Always** — 오버레이가 `:dev`/`:<sha>` 로 바꿔도 노드캐시 무시·매 롤아웃 재pull → 미러/401 종속. base 에 `IfNotPresent` 명시(prod 만 Always).
- **standalone kind ≠ docker-desktop kind** — `HARBOR_SETUP.md`/`K8S_INGRESS_HARBOR.md` 의 LB→localhost 자동매핑은 docker-desktop 전용. standalone 은 **CP_IP 단일좌표**(externalURL/CoreDNS/certs.d 동일 IP)로 split-horizon 통일.
- **노드 docker 데몬 없음 → Kaniko 빌드** — 인-클러스터 CI 빌드는 docker build 불가. Kaniko 데몬리스(`--insecure-registry=harbor.local`, HTTP). builder 스테이지 SERVICE 무관 → `--cache` 로 재사용.
- **claimed-done ≠ committed, 미검증≠검증** — Stage B/C 는 저자환경 미실행. PASS 게이트로 받는 쪽 실증 필수.
- **배치 트랙 ≠ 메인 트랙** — 배치 상세=`HANDOFF_BATCH_SUMMARY.md`. 독립 갱신.

<!-- 갱신 끝 -->
