# _PITFALLS_APPEND_2026-06-07-B.md — `docs/guide/PITFALLS.md §9` 말미에 병합할 신규 항목 (B안: ingress 호스트 접속)

> 적용법: 아래 줄들을 `PITFALLS.md` §9(로컬 통합 실행/멀티서비스 배포 정합) 표 말미에 append.
> 카테고리 표기: [겪음]=이번 트랙에서 실측·결정한 것, [일반]=일반 known.

| 증상 | §9 원인 → 조치 |
|---|---|
| standalone kind 노드 포트가 호스트로 안 나옴(nginx/port-forward 만으론 영구 노출 불가) | [겪음] docker-desktop kind 의 LB→localhost 자동게시 없음. **호스트 노출 = ① port-forward ② `extraPortMappings`(생성시 고정) ③ 프록시 컨테이너** 중 하나. ingress 만 깔아선 불가 → ingress 컨트롤러 hostPort + extraPortMappings 결합(B안) |
| ingress 모드에서 노드가 harbor.local 을 `https://harbor.local:443` 로 시도(connection refused) | [겪음] 토큰 realm=externalURL=`http://harbor.local`(IP 아님) → 노드도 *이름*을 풀어야 함. 노드는 CoreDNS 안 씀 → **노드 `/etc/hosts` 에 `<CP_IP> harbor.local`** 필요(registry-trust DaemonSet 이 CM `node-etc-hosts` 값으로 기입) + certs.d 엔드포인트는 IP 아닌 `http://harbor.local`(ingress Host 라우팅) |
| ingress-nginx 컨트롤러 Pending(스케줄 안 됨) | [일반] kind 매니페스트의 `nodeSelector: ingress-ready=true` 미충족. kind-config 의 control-plane `node-labels: ingress-ready=true` + control-plane toleration 필요 |
| Harbor ingress 가 308 로 https 강제 | [겪음] `nginx.ingress.kubernetes.io/ssl-redirect: "false"` 누락(HTTP 평문 환경) |
| Harbor 이미지 push 가 413 Request Entity Too Large | [일반] ingress 기본 본문 1m. `nginx.ingress.kubernetes.io/proxy-body-size: "0"`(무제한) 필요 |
| 인-클러스터 Kaniko 가 harbor.local push DNS 실패 | [겪음] CoreDNS hosts 플러그인에 `harbor.local → ingress-nginx-controller ClusterIP` 주입(노드 hostPort 우회, 인-클러스터 경로 안정). 08 §6 |
| 재생성으로 CP_IP 변동 → 노드 /etc/hosts·CM stale | [겪음] CP_IP 는 재생성/재부팅으로 바뀔 수 있음 → 08/07 이 매번 `docker inspect`로 재산출해 CM(node-etc-hosts) 갱신+DS rollout. certs.d 엔드포인트는 *이름*이라 불변(IP 변동을 /etc/hosts 가 흡수) |
| extraMounts 제거 후 01-pull-sanity 가 reg.local certs.d 못 찾음 | [겪음] B안에서 extraMounts(휘발) 졸업 → certs.d 공급자가 DaemonSet 으로 이동. sanity 단계는 DS 이전이므로 01 이 `docker exec`로 reg.local certs.d 를 노드에 직접 시드(자기완결) |
| /etc/hosts 를 DaemonSet 으로 편집 시 바인드마운트 깨짐 | [일반] `mv` 로 교체하면 inode 바뀌어 hostPath File 마운트 분리. **`cat tmp > /host-etc-hosts`(내용 덮어쓰기)** 로 inode 보존 |
