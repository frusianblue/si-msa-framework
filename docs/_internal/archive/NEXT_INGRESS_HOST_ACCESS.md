> ⚠️ **ARCHIVED (2026-06-07 세션8 종료 시점)** — 완료됨. 아래는 착수 당시 기록(보존용).
> 완료: B안 호스트 접속 실행·검증(11-host-access-verify 전 항목 PASS). harbor.local/jenkins.local 직접 접속(HANDOFF §7 세션4, PITFALLS §9).

# NEXT_INGRESS_HOST_ACCESS.md — 다음 세션 착수용 (B안: 재생성 + ingress-nginx)

> 🟢 **상태(2026-06-07 세션4): 호스트 접속 B안 = 실행·검증 완료.** `11-host-access-verify.sh` 전 항목 PASS,
>   `http://harbor.local`/`http://jenkins.local` 호스트 직접 접속(port-forward 졸업) 실측. 실행 중 결함 5건은
>   `10`/`09`/Jenkinsfile 에 영구 반영(상세 PITFALLS §9-B, HANDOFF §7). **이 문서는 host-access 완료 → 아카이브 대상.**
>   잔여 CI 과제(Kaniko 다중 이미지 빌드)는 별도 `NEXT_CI_KANIKO_MULTIBUILD.md` 로 분리.
> ───────────────────────────────────────────────────────────────────────────────

---

## ⚠️ 선행 주의 (재생성 = 데이터 소실)
재생성하면 PVC 가 전부 사라진다: dev postgres(authdb/sidb/admindb 데모데이터), Harbor(이미지/프로젝트), Jenkins(잡 설정/히스토리).
- dev postgres: Flyway 가 스키마 재생성 → 데모데이터만 손실(리허설이라 수용).
- Harbor/Jenkins: 08/09 멱등 재실행으로 재구축. Jenkins 잡(UI 1회)·Harbor 이미지(파이프라인 1회) 다시.
- 보존이 필요하면 재생성 전 백업: `pg_dump`(postgres-0), Harbor export, Jenkins `$JENKINS_HOME` tar.

---

## 작업 순서 (다음 세션, PASS 게이트 단위)

1. **새 `kind-config.yaml`** (standalone-kind/):
   - control-plane 노드에 `extraPortMappings` 80→80, 443→443 (호스트 게시) + `node-labels: ingress-ready=true`.
   - 기존 `extraMounts(./certs.d)` 는 **제거** — 이미 `registry-trust` DaemonSet 이 부팅마다 certs.d 기입(휘발성 마운트 의존 졸업). containerdConfigPatches(config_path) 는 유지.
2. **재생성**: `00-cleanup.sh --teardown-sanity` → `01-pull-sanity.sh`(새 config).
3. **영구수정/앱**: `07-reboot-recover.sh`(DaemonSet+certs.d) → `kubectl apply -k overlays/dev` → 6파드 Ready.
4. **ingress-nginx 설치**(신규 `10-ingress-nginx.sh`):
   - kind 전용 매니페스트(hostPort 80/443 + `nodeSelector: ingress-ready=true` + control-plane toleration). LoadBalancer 아님.
   - PASS: `kubectl -n ingress-nginx get pods` controller Ready, `curl http://localhost` → 404(ingress 정상).
5. **Harbor 를 ingress 노출로 전환**(08 수정 또는 08b):
   - `expose.type=ingress`, `expose.ingress.className=nginx`, `expose.ingress.hosts.core=harbor.local`,
     `externalURL=http://harbor.local`, `--set "expose.ingress.annotations.nginx\.ingress\.kubernetes\.io/ssl-redirect=false"`(308 방지).
6. **Jenkins ingress**(`jenkins-values.yaml` 의 `controller.ingress.enabled=true`, `hostName=jenkins.local`, `ingressClassName=nginx`; serviceType 는 ClusterIP 로 되돌려도 됨).
7. **호스트 이름해소**: Windows `C:\Windows\System32\drivers\etc\hosts` + WSL `/etc/hosts` 에
   `127.0.0.1 harbor.local jenkins.local` (extraPortMappings 로 80 이 호스트 localhost:80 에 게시됨).
8. **파이프라인 1회 재실행**(이미지 재push) → dev 롤아웃.

**최종 PASS**: 브라우저에서 `http://harbor.local`(포털, admin/Harbor12345) · `http://jenkins.local`(admin/admin123)
**port-forward 없이** 접속. 파이프라인 그린.

---

## 미리 풀어둘 난제 (split-horizon, ingress 경유로 이동)
NodePort(30002) → ingress(80) 로 진입점이 바뀌므로 노드 pull / 인-클러스터 Kaniko push 좌표도 재조정:
- 토큰 realm = `externalURL = http://harbor.local` → **노드도 harbor.local 을 해소해야 함**.
  - 노드 certs.d: `harbor.local` → `http://<CP_IP>:80` (ingress hostPort 80).
  - 노드가 realm(`http://harbor.local`)을 풀도록 **노드 /etc/hosts 에 `<CP_IP> harbor.local`** 필요
    → `registry-trust` DaemonSet 에 /etc/hosts 기입 로직 추가(또는 certs.d 엔드포인트를 IP 로 고정해 realm 회피).
  - 인-클러스터: CoreDNS `harbor.local → <CP_IP>`(또는 ingress-nginx-controller svc ClusterIP) 유지.
- `<CP_IP>` 는 재생성으로 바뀔 수 있음 → 08/스크립트가 매번 산출(`docker inspect sanity-control-plane`).
- 이게 B안의 핵심 검증 포인트. 막히면 `describe pod` Events(`x509`/`401`/realm 해소 실패)로 트리아지.

## 다음 세션 산출 예정물
`kind-config.yaml`(개정) · `10-ingress-nginx.sh` · Harbor ingress 전환(08 개정/08b) · `jenkins-values.yaml`(ingress) ·
`harbor-ingress`/`jenkins-ingress` 규칙 · `registry-trust-daemonset.yaml`(/etc/hosts 기입 추가) · 재생성 런북 ·
문서(STANDALONE_KIND_HARBOR_JENKINS 갱신, PITFALLS append).

## 참고
- 이번 세션 산출(미적용분 일부 적용됨): `si-msa-harbor-jenkins-cicd.zip`(base imagePullPolicy, registry-trust DS,
  07/08/09, harbor/jenkins values, Jenkinsfile.kind, 런북). B안은 그 위에 ingress 계층을 얹는 것.
- 대안 A(프록시 컨테이너, 재생성 불요)는 보류 — 필요 시 즉시 전환 가능(socat `--network kind -p 8080:80 → CP_IP:30002`).
