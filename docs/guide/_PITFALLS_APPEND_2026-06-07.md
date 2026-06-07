# _PITFALLS_APPEND_2026-06-07.md — PITFALLS.md 병합용 스니펫 (덮어쓰기 금지, 수동 병합)

> 큐레이션 원장(`PITFALLS.md`)을 통째로 덮지 말 것. 아래를 §8(운영/환경)·§9(배포 정합)·부록 자가진단에
> 사람이 병합한다. 분류: [겪음] = 이번 세션 실측.

---

## §8 또는 §9 에 추가할 본문 항목

**[겪음] 재부팅 후 auth-server 만 ImagePullBackOff (다른 5파드는 Running)** — standalone kind.
- 증상: PC/Docker Desktop 재부팅 후 auth-server 만 `ImagePullBackOff`. `describe pod` Events 에
  `dial tcp 127.0.0.1:443: connect: connection refused` (harbor.local 을 기본 https://harbor.local:443 으로 시도).
- 원인(복합 2):
  1. 노드 `/etc/containerd/certs.d/harbor.local/` 가 **사라짐**. 이 미러는 kind `extraMounts`(호스트 `./certs.d`
     → 노드 `/etc/containerd/certs.d`) **bind mount 의존**이었는데, Docker Desktop/WSL 재부팅이 이 마운트를
     휘발시킴 → certs.d 부재 → 기본 https:443 폴백 → refused.
  2. base deployment 의 원본 태그가 `:latest` 이고 `imagePullPolicy` 미명시 → kubelet 기본 **Always** 처럼 동작
     → 오버레이가 `:dev` 로 바꿔도 노드 캐시 무시·매 롤아웃마다 레지스트리 재확인 → 깨진 미러/401 에 종속.
  - 다른 5파드가 생존한 이유: 롤아웃 트리거가 없어 새 pull 이 필요 없었고 노드 캐시로 기동.
- 해결(영구):
  1. **`registry-trust` DaemonSet** — ConfigMap(`registry-hosts`) 내용을 hostPath `/etc/containerd/certs.d` 에
     **노드 부팅마다 재기입**. containerd 는 certs.d 를 요청마다 읽으므로 재시작 불요. bind mount 의존 제거 →
     재부팅 내성. (`deploy/k8s/standalone-kind/registry-trust-daemonset.yaml`)
  2. base `imagePullPolicy: IfNotPresent`(노드캐시·불변 `:<sha>` 전제), prod overlay 만 `Always`(가변 채널).
  3. 재부팅 복구 루틴 1줄: `bash deploy/k8s/standalone-kind/07-reboot-recover.sh`(DS 적용 + 노드 직접기입 +
     kind load 폴백 + 롤아웃 + 검증).
- 라이브 패치(당시 즉효): `kubectl -n si-msa patch deploy auth-server --type=json \
  -p='[{"op":"add","path":"/spec/template/spec/containers/0/imagePullPolicy","value":"IfNotPresent"}]'`.

**[겪음] 기존 Harbor 노출 문서가 은퇴 환경 전제** — `HARBOR_SETUP.md`/`K8S_INGRESS_HARBOR.md` 의
"LoadBalancer→localhost 자동매핑"은 **docker-desktop kind 전용**. standalone kind 엔 LB 매직 없음 →
`STANDALONE_KIND_HARBOR_JENKINS.md`(CP_IP 단일좌표 + CoreDNS + certs.d) 로 대체.

---

## 부록 자가진단 표에 추가할 행

| 증상 | 먼저 의심 |
|---|---|
| 재부팅 후 **특정 서비스만** ImagePullBackOff + `dial tcp 127.0.0.1:443: connection refused` | §8 certs.d bind mount 휘발 → `registry-trust` DaemonSet 으로 영구화 + `07-reboot-recover.sh` |
| 오버레이가 `:dev`/`:<sha>` 인데도 노드 캐시 무시하고 매번 레지스트리 재확인 | §8 base 태그 `:latest` + imagePullPolicy 미명시 → 기본 Always. base 에 `IfNotPresent` 명시 |
| standalone kind 에서 ingress-nginx LoadBalancer EXTERNAL-IP 가 `<pending>` | §8 standalone kind 엔 LB→localhost 매직 없음(docker-desktop 전용). NodePort+CP_IP 좌표 사용 |
| 인-클러스터 Kaniko push 가 `harbor.local` 해소 실패 | §9 CoreDNS hosts(harbor.local→CP_IP) 미적용 → `08` §6 재실행 |
