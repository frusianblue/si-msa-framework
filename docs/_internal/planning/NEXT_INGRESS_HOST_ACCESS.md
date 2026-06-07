# NEXT_INGRESS_HOST_ACCESS.md — B안: 재생성 + ingress-nginx (호스트 접속 port-forward 졸업)

> **상태 (2026-06-07 세션4): 🟢 산출 완료 / 🟡 실행 대기(받는 쪽).**
>   B안 자산·런북·문서 전부 작성 완료(아래 "산출물"). Chae 로컬에서 순서대로 실행→검증하면 종료.
>   "claimed-done ≠ executed" 원칙대로, 받는 쪽 11-host-access-verify.sh PASS 전까지 이 스펙은 planning 에 둔다.
>   실행 PASS 후 `docs/_internal/archive/` 로 ARCHIVED 배너와 함께 이동.

---

## 결정(재확인)
호스트 접속을 **B** = 클러스터 재생성 + `extraPortMappings`(80/443) + ingress-nginx + Ingress(`harbor.local`/`jenkins.local`)로 졸업.
배경: standalone kind 는 LB→localhost 자동게시 없음, `extraPortMappings` 는 생성시 고정 → 재생성 불가피.

## 산출물 (세션4 작성 완료)
- `standalone-kind/kind-config.yaml` (개정): control-plane extraPortMappings 80/443 + `node-labels: ingress-ready=true`, **extraMounts 제거**, containerdConfigPatches 유지.
- `standalone-kind/01-pull-sanity.sh` (개정): extraMounts 졸업 → reg.local certs.d 를 노드에 `docker exec` 직접 시드.
- `standalone-kind/registry-trust-daemonset.yaml` (개정): 노드 `/etc/hosts` 멱등 병합(`node-etc-hosts` 키) 추가(ingress realm 해소). certs.d harbor.local 엔드포인트 `http://harbor.local`.
- `standalone-kind/10-ingress-nginx.sh` (신규): kind provider 매니페스트 핀(`controller-v1.13.0`) 설치 + `curl localhost`→404 PASS.
- `standalone-kind/08-harbor-install.sh` (개정→ingress): externalURL=`http://harbor.local`, CoreDNS→ingress ClusterIP, registry-hosts CM(certs.d + node-etc-hosts CP_IP) + DS rollout + 즉효 직접기입.
- `standalone-kind/harbor-values.yaml` (개정): expose.type=ingress, className=nginx, host=harbor.local, ssl-redirect=false, proxy-body-size=0.
- `standalone-kind/09-jenkins-install.sh` + `jenkins-values.yaml` (개정): ingress(jenkins.local), jenkinsUrl, serviceType=ClusterIP.
- `standalone-kind/07-reboot-recover.sh` (개정): CP_IP 재산출 → CM node-etc-hosts 갱신 → 노드 certs.d/+/etc/hosts 재기입 → 앱 롤아웃.
- `standalone-kind/11-host-access-verify.sh` (신규): 호스트/노드/인-클러스터 좌표 일괄 PASS 게이트.
- `standalone-kind/certs.d/harbor.local/hosts.toml` (개정): `http://harbor.local`.
- 문서: `docs/ops/STANDALONE_KIND_HARBOR_JENKINS.md`(ingress 런북 전면 개정), `docs/guide/_PITFALLS_APPEND_2026-06-07-B.md`, HANDOFF §7/SUMMARY.

## 실행 순서 (받는 쪽 — 런북과 동일)
0. `00-cleanup.sh --teardown-sanity` → `01-pull-sanity.sh` (재생성, PASS)
1. `10-ingress-nginx.sh` (PASS: localhost→404)
2. `07-reboot-recover.sh` → `kubectl apply -k overlays/dev` (6파드 Ready)
3. `08-harbor-install.sh` (Harbor ingress) → `09-jenkins-install.sh` (Jenkins ingress)
4. 호스트 hosts: `127.0.0.1 harbor.local jenkins.local` (Windows + WSL)
5. `11-host-access-verify.sh` (최종 PASS) → Jenkins UI 잡 1회 → Build Now

## 핵심 검증점 (막히면 여기)
- realm=`http://harbor.local` → 노드 /etc/hosts(CP_IP) + certs.d(이름) 양쪽 필요. `describe pod` Events 의 x509/401/realm 해소실패로 트리아지.
- CP_IP 변동성 → 08/07 이 매번 산출. certs.d 는 이름이라 불변, IP 변동은 /etc/hosts 가 흡수.
- 인-클러스터 push 는 CoreDNS→ingress ClusterIP(노드 hostPort 우회). 노드 pull 은 /etc/hosts→CP:80.

## 대안 A (보류, 재생성 불요)
프록시 컨테이너(socat `--network kind -p 8080:80 → CP_IP:80`). B안 PASS 후 불필요.
