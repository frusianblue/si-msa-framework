# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 진입점은 `../00_INDEX.md`, 전체 맥락은 `HANDOFF.md`(같은 폴더), 사용법은 `../../README.md`, 버전은 `../reference/STACK.md`, 모듈 설계는 `../FRAMEWORK_MODULES.md`. (문서는 역할별 재구조화됨 — 작업기록은 `_internal/`.)
> **배치·태스크·스케줄(Spring Cloud Task + Batch + Quartz UI) 트랙은 별도 한 장 `HANDOFF_BATCH_SUMMARY.md`(같은 폴더) 참조.**

---
<!-- 갱신 시작 -->

## 이번 세션 한 줄 요약
**🟢 B안(호스트 접속 ingress화) 자산·런북·문서 전부 작성 완료 / 🟡 받는 쪽 실행 대기 (2026-06-07 세션4, devops).** standalone kind 호스트 접속을 **port-forward 졸업** → 브라우저 `http://harbor.local`/`http://jenkins.local`. 방식 = 클러스터 **재생성 + `extraPortMappings`(80/443) + ingress-nginx(`controller-v1.13.0` 핀) + Ingress + split-horizon 재조정**. 핵심: 세 주체가 같은 이름 `harbor.local` 로 ingress 에 닿되 해소 IP 만 다름 — **호스트=127.0.0.1**(OS hosts→localhost:80 extraPortMappings), **인-클러스터 Kaniko=ingress ClusterIP**(CoreDNS hosts), **노드 containerd=CP_IP**(노드 `/etc/hosts`, registry-trust DaemonSet 이 CM `node-etc-hosts` 로 기입). `externalURL=http://harbor.local`(realm 도 이름) → 세 경로 모두 해소. 재생성 동반(extraPortMappings 생성시 고정) → PVC 소실, 08/09 멱등 재구축. 저자환경 실행 불가 → 받는 쪽이 5스테이지 런북대로 실행.

## 최종 갱신
- 일자: 2026-06-07 · 갱신자: 세션4(B안 ingress 호스트 접속 산출)
- 대상 브랜치: master · 환경: 프레임워크/스택 무변경(devops). 이번 세션 산출 미커밋.

## 직전에 한 것 (Done)
| 단계 | 산출/검증 |
|---|---|
| kind-config 개정 | control-plane `extraPortMappings` 80/443 + `node-labels: ingress-ready=true`, **extraMounts 제거**(재부팅 휘발 졸업), containerdConfigPatches 유지. |
| 01-pull-sanity 개정 | extraMounts 대체 → reg.local certs.d 를 노드에 `docker exec` 직접 시드(sanity 자기완결). |
| registry-trust DS 개정 | 노드 `/etc/hosts` 멱등 병합(`node-etc-hosts` 키) 추가 — ingress realm 이름해소. inode 보존 `cat>` 덮어쓰기. |
| 10-ingress-nginx (신규) | kind provider 매니페스트 핀(`controller-v1.13.0`, 200 확인) 설치 + `curl localhost`→404 PASS 게이트. |
| 08-harbor 개정(ingress) | externalURL=`http://harbor.local`, CoreDNS harbor.local→ingress **ClusterIP**, registry-hosts CM(certs.d + node-etc-hosts CP_IP) + DS rollout + 즉효 직접기입. harbor-values: expose.ingress/ssl-redirect=false/proxy-body-size=0. |
| 09-jenkins 개정(ingress) | jenkins.local, `jenkinsUrl`, serviceType=ClusterIP, Kaniko push cred(harbor.local) 유지. |
| 07-reboot-recover 개정 | CP_IP 재산출 → CM node-etc-hosts 갱신 → 노드 certs.d/+/etc/hosts 재기입 → 앱 롤아웃(재부팅 IP 변동 추종). |
| 11-host-access-verify (신규) | 호스트(harbor/jenkins)·ingress·인-클러스터·노드 좌표 일괄 PASS 게이트. |
| certs.d 개정 | harbor.local/hosts.toml = `http://harbor.local`(이름·ingress Host 라우팅). |
| 문서 | `STANDALONE_KIND_HARBOR_JENKINS.md` ingress 런북 전면 개정 · `_PITFALLS_APPEND_2026-06-07-B.md`(8건) · HANDOFF §7 append · `NEXT_INGRESS_HOST_ACCESS.md` 산출완료/실행대기 갱신 · 이 SUMMARY. |

## 현재 상태 (적용/검증)
- **자산**: B안 전 자산 작성 완료(드롭인 zip). **저자환경에선 실행 불가**(kind/helm/Gradle 차단) — 정적 작성만.
- **클러스터**: 받는 쪽 실행 전. 기존 `kind-sanity`(NodePort 판)는 재생성으로 대체 예정(PVC 소실 수용).
- **커밋**: 세션4 산출 전부 + (이전 누적 백로그) **미커밋**.

## 바로 다음 할 일 (Next)
1. **받는 쪽 B안 실행(5스테이지)** — 런북/`NEXT_INGRESS_HOST_ACCESS.md` 순서: 재생성(00→01) → 10(ingress) → 07+`apply -k overlays/dev` → 08+09 → hosts 등록 → **11-host-access-verify PASS**.
2. **파이프라인 1회** — Jenkins UI 잡 생성(SCM, `deploy/cicd/Jenkinsfile.kind`) → Build Now(Kaniko build→push harbor.local→dev 롤아웃 그린).
3. **11 PASS 후** `NEXT_INGRESS_HOST_ACCESS.md` archive(ARCHIVED 배너) + `_PITFALLS_APPEND_2026-06-07-B.md` 를 `PITFALLS.md §9` 본문 병합 + HANDOFF §7 append 본문 반영.
4. **commit/push 백로그** — 세션4 + 누적 전부.

## 이번 세션 함정/원칙 (되돌리지 말 것)
- **ingress 만으론 standalone kind 호스트 노출 불가** — 컨트롤러 hostPort + `extraPortMappings`(생성시 고정) 결합이라야 호스트 localhost 게시. 그래서 재생성 동반.
- **ingress 모드 realm = 이름(`http://harbor.local`)** → 노드도 이름을 풀어야 함(노드는 CoreDNS 안 씀). **노드 /etc/hosts `<CP_IP> harbor.local`** + certs.d 엔드포인트는 IP 아닌 *이름*(ingress Host 라우팅).
- **split-horizon = 같은 이름, 다른 해소 IP** — 호스트 127.0.0.1 / 인-클러스터 ingress ClusterIP / 노드 CP_IP. CoreDNS 는 ClusterIP(노드 hostPort 우회), 노드는 /etc/hosts(CP:80).
- **CP_IP 는 재생성/재부팅으로 변동** — 08/07 이 매번 산출해 CM 갱신. certs.d 는 이름이라 불변(IP 변동을 /etc/hosts 가 흡수).
- **extraMounts 졸업** = certs.d 공급을 DaemonSet 으로. sanity(DS 이전 단계)는 01 이 직접 시드. /etc/hosts 편집은 `mv` 금지(`cat>` 로 inode 보존).
- **ssl-redirect=false / proxy-body-size=0** — HTTP 평문·이미지 레이어 push 본문.
- **claimed-done ≠ executed** — 자산 작성 ≠ 받는 쪽 PASS. 11-host-access-verify PASS 전까지 planning 유지.
- **배치 트랙 ≠ 메인 트랙** — 배치 상세=`HANDOFF_BATCH_SUMMARY.md`. 독립 갱신.

<!-- 갱신 끝 -->
