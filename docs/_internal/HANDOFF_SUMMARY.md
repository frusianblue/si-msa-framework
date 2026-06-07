# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 진입점은 `../00_INDEX.md`, 전체 맥락은 `HANDOFF.md`(같은 폴더), 사용법은 `../../README.md`, 버전은 `../reference/STACK.md`, 모듈 설계는 `../FRAMEWORK_MODULES.md`. (문서는 역할별 재구조화됨 — 작업기록은 `_internal/`.)
> **배치·태스크·스케줄(Spring Cloud Task + Batch + Quartz UI) 트랙은 별도 한 장 `HANDOFF_BATCH_SUMMARY.md`(같은 폴더) 참조.**

---
<!-- 갱신 시작 -->

## 이번 세션 한 줄 요약
**✅ 재부팅 버그 영구수정 + ✅ 인-클러스터 Harbor/Jenkins/Kaniko 설치 성공 + 🚧 호스트 접속 B안 결정(2026-06-07 세션3).** (1) **디버깅**: 재부팅 후 auth-server 만 `ImagePullBackOff`(`dial tcp 127.0.0.1:443: connection refused`) — 근인 복합2 ① 노드 certs.d 가 호스트 bind mount 의존이라 재부팅에 휘발(→harbor.local 기본 https:443 폴백) ② base 태그 `:latest`+imagePullPolicy 미명시→기본 Always→노드캐시 무시·미러/401 종속. **라이브 패치로 복구**. (2) **영구화 산출**: base `imagePullPolicy:IfNotPresent`(+prod `Always`)·**`registry-trust` DaemonSet**(certs.d 를 노드 부팅마다 재기입 → bind mount 졸업)·`07-reboot-recover.sh`. (3) **인-클러스터 Harbor+Jenkins 설치 성공(받는 쪽 실측)**: standalone kind 엔 docker-desktop 의 LB→localhost 매직 없음 → **CP_IP 단일좌표**(externalURL=`http://172.18.0.2:30002`, CoreDNS harbor.local→CP_IP, 노드 certs.d→CP_IP:30002)로 split-horizon 통일. Harbor(NodePort30002·HTTP·영속·trivy off)·Jenkins(NodePort32000·영속·k8s agent)·Kaniko 파이프라인(`Jenkinsfile.kind`) 전부 기동 확인. (4) **호스트 접속 미해결 → B안 채택**: standalone kind 는 `extraPortMappings` 없으면 노드 포트가 호스트로 안 나옴(nginx 만으론 불가) → **B(재생성 + extraPortMappings + ingress-nginx + harbor.local/jenkins.local)** 다음 세션 착수. 임시 접속=`port-forward`.

## 최종 갱신
- 일자: 2026-06-07 · 갱신자: 세션3(재부팅 수정 + 인-클러스터 CI/CD + 호스트노출 B안 결정)
- 대상 브랜치: master · 환경: 프레임워크/스택 무변경(devops). 미커밋(세션2 S5 + 세션3 전부).

## 직전에 한 것 (Done)
| 단계 | 산출/검증 |
|---|---|
| 재부팅 디버깅 | auth-server ImagePullBackOff 근인 2개 규명 → 라이브 `imagePullPolicy IfNotPresent` 패치로 **1/1 복구**(받는 쪽 실측). |
| 영구 pull/재부팅 수정 | base/{4} `IfNotPresent` + prod `Always` 패치. `registry-trust-daemonset.yaml`(노드 부팅마다 certs.d 재기입). `07-reboot-recover.sh`. |
| 인-클러스터 Harbor | `harbor-values.yaml`+`08-harbor-install.sh`. **받는 쪽 설치 성공**(externalURL 172.18.0.2:30002). |
| 인-클러스터 Jenkins | `jenkins-values.yaml`+`jenkins-rbac.yaml`+`09-jenkins-install.sh`. **받는 쪽 설치 성공**(admin/admin123). |
| 파이프라인 | `deploy/cicd/Jenkinsfile.kind`(Kaniko build→push harbor.local→`apply -k overlays/dev`+set image `:<sha>`+rollout). |
| 호스트노출 진단·결정 | standalone kind=extraPortMappings 부재→노드 포트 호스트 비노출. nginx 만으론 불가 규명 → **B안 채택**, 계획서 `NEXT_INGRESS_HOST_ACCESS.md`. |
| 문서 | `STANDALONE_KIND_HARBOR_JENKINS.md`(런북, docker-desktop 전제 문서 대체)·`_PITFALLS_APPEND_2026-06-07.md`·HANDOFF §7 항목·이 SUMMARY. |

## 현재 상태 (적용/검증)
- **클러스터**: standalone `kind-sanity` 3노드. 앱 6파드 Ready + Harbor(harbor ns) + Jenkins(jenkins ns) **전부 기동**.
- **접속**: 현재 `kubectl port-forward` 만 가능(`svc/harbor 8080:80`, `svc/jenkins 8088:8080`). 호스트네임/포워딩 졸업은 다음 세션 B안.
- **커밋**: 세션2 S5 + 세션3 devops 자산/문서 전부 **미커밋**.

## 바로 다음 할 일 (Next)
1. **B안 — 호스트 접속 ingress화(재생성)**: `NEXT_INGRESS_HOST_ACCESS.md` 순서대로. ⚠️ 재생성=PVC 데이터 소실(postgres 데모·Harbor 이미지·Jenkins 잡) → 08/09 재실행 재구축. 새 kind-config(extraPortMappings 80/443 + ingress-ready 라벨, extraMounts 제거) → 재생성 → 07 → ingress-nginx(10-) → Harbor/Jenkins ingress 전환 → Windows·WSL hosts `127.0.0.1 harbor.local jenkins.local` → 파이프라인 1회.
2. **B안 핵심 검증점**: NodePort(30002)→ingress(80) 진입점 이동 → 토큰 realm(`http://harbor.local`)을 노드도 해소해야 함(노드 certs.d→CP_IP:80 + 노드 /etc/hosts harbor.local→CP_IP, DaemonSet 에 /etc/hosts 기입 추가). CP_IP 는 재생성으로 변동 가능 → 스크립트가 매번 산출.
3. **commit/push 백로그** — 세션2 S5 + 세션3 전부.

## 이번 세션 함정/원칙 (되돌리지 말 것)
- **재부팅 내성 = certs.d 를 bind mount 아니라 DaemonSet 으로** — 호스트 마운트는 Docker Desktop/WSL 재부팅에 휘발. `registry-trust` DaemonSet 이 노드 부팅마다 재기입.
- **base 태그 `:latest`+imagePullPolicy 미명시 = 사실상 Always** — 오버레이가 `:dev`/`:<sha>` 로 바꿔도 노드캐시 무시·매 롤아웃 재pull. base 에 `IfNotPresent` 명시(prod 만 Always).
- **standalone kind ≠ docker-desktop kind (호스트 노출)** — docker-desktop 의 LB→localhost 자동게시 없음. 노드 포트를 호스트로 빼려면 ① port-forward ② extraPortMappings(생성시 고정) ③ kind net 프록시 컨테이너 중 하나. **ingress 만 깔아선 호스트 접근 불가.**
- **인-클러스터 Harbor split-horizon = CP_IP 단일좌표** — externalURL/CoreDNS/certs.d 를 control-plane 노드 IP 한 점으로 통일.
- **노드 docker 데몬 없음 → Kaniko 빌드** — 인-클러스터 CI 는 docker build 불가.
- **claimed-done ≠ committed, 설치성공 ≠ 사용가능** — 설치는 됐어도 호스트 접속은 별개(B안에서 해결).
- **배치 트랙 ≠ 메인 트랙** — 배치 상세=`HANDOFF_BATCH_SUMMARY.md`. 독립 갱신.

<!-- 갱신 끝 -->
