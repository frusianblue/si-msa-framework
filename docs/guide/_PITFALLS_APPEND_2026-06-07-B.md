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

### [겪음 encountered] ingress-nginx kind provider 매니페스트(v1.13.0)가 `ingress-ready` nodeSelector 를 더 이상 포함하지 않음
- **증상:** kind-config 로 control-plane 에 `ingress-ready=true` 라벨을 부여하고 ingress-nginx(kind, controller-v1.13.0)를 설치했는데, 컨트롤러 파드가 라벨 없는 `worker` 노드로 스케줄됨. 호스트에서 `curl http://localhost/` → `curl: (56) Recv failure: Connection reset by peer`(refused 아님).
- **원인:** 과거 kind provider 매니페스트엔 `nodeSelector: ingress-ready: "true"` + control-plane toleration 이 있어 컨트롤러가 CP 노드에 고정됐으나, controller-v1.13.0 의 `deploy/static/provider/kind/deploy.yaml` 은 `nodeSelector: kubernetes.io/os: linux` 만 둔다. 따라서 컨트롤러가 임의 노드(worker)로 떠 hostPort 80 을 worker 에 잡고, `extraPortMappings` 가 게시한 것은 control-plane:80 이라 호스트→control-plane:80 은 빈 포트(백엔드 없음) → docker-proxy 가 연결을 reset. (refused 가 아니라 reset 인 것이 단서: 포트 게시는 됐고 백엔드만 없음.)
- **해결:** 매니페스트 apply 직후 컨트롤러 Deployment 를 patch 해 control-plane 에 고정한다. nodeSelector 에 `ingress-ready=true`(+`kubernetes.io/os=linux`)를, tolerations 에 `node-role.kubernetes.io/control-plane:NoSchedule` 을 추가. taint toleration 을 빼면 CP 노드 스케줄이 막혀 Pending 이 되므로 둘 다 필요.
  ```bash
  kubectl -n ingress-nginx patch deploy ingress-nginx-controller --type merge -p \
    '{"spec":{"template":{"spec":{"nodeSelector":{"ingress-ready":"true","kubernetes.io/os":"linux"},"tolerations":[{"key":"node-role.kubernetes.io/control-plane","operator":"Exists","effect":"NoSchedule"}]}}}}'
  ```
  `10-ingress-nginx.sh` 1.5단계에 내장(멱등 — 재실행/재apply 해도 유지). 매니페스트 버전을 올릴 때 nodeSelector 포함 여부를 매번 재확인할 것(버전 회귀 가능).

### [겪음 encountered] 09-jenkins-install 의 jenkins-rbac.yaml → `namespaces "si-msa" not found`
- **증상:** `09-jenkins-install.sh` 실행 중 `serviceaccount/jenkins-deployer created` 뒤에 `Error from server (NotFound): error when creating "jenkins-rbac.yaml": namespaces "si-msa" not found` 가 3회. Jenkins 자체(Helm)는 deployed, 로그인 정상.
- **원인:** `jenkins-rbac.yaml` 의 ServiceAccount 는 `jenkins` ns(존재)이나, RoleBinding/Role/RoleBinding 3개는 `si-msa` ns 대상. `si-msa` 는 앱 오버레이(`overlays/dev` → `base/namespace.yaml`)가 만드는데, 클러스터 재생성 후 앱 오버레이를 아직 apply 하지 않아 ns 부재 → 네임스페이스 종속 객체 생성 실패. (또한 앱 이미지는 `harbor.local/si-msa/*:dev` 핀이라 Jenkins CI 첫 push 전엔 어차피 ImagePullBackOff → 오버레이를 RBAC 전제로 강제하면 닭-달걀.)
- **해결:** 09 가 RBAC apply 직전에 si-msa ns 를 멱등 생성해 앱 배포 순서와 분리한다.
  ```bash
  kubectl create namespace si-msa --dry-run=client -o yaml | kubectl apply -f -
  ```
  나중 `kubectl apply -k overlays/dev` 가 동일 ns(라벨 포함)를 흡수(no-op). 권장 순서: ① ns+RBAC(09) → ② Jenkins 잡 1회 Build(이미지 push) → ③ `apply -k overlays/dev`.

### [겪음 encountered] Jenkinsfile 선언형 `options { timestamps() }` → `Invalid option type "timestamps"`
- **증상:** `si-msa-cd` 첫 빌드가 `WorkflowScript: 52: Invalid option type "timestamps"` 로 파싱 단계에서 실패(스테이지 진입 전). 유효 옵션 목록에 `timestamps` 없음.
- **원인:** 선언형 파이프라인의 `timestamps()` 옵션은 **Timestamper 플러그인**이 제공한다. 이 Jenkins(Helm 기본 플러그인 셋)엔 미설치(또는 옵션 미지원 버전)라 파서가 거부.
- **해결:** 장식용이므로 `options {}` 에서 `timestamps()` 제거가 가장 단순(즉시 해소·플러그인 의존 제거). 시각표시가 필요하면 `jenkins-values.yaml` 의 `controller.additionalPlugins` 에 `timestamper` 추가 후 `helm upgrade` → 옵션 복원.
- **★ 운영 함정(SCM 파이프라인):** 잡이 *Pipeline script from SCM* 이면 Jenkins 는 **git(master)** 의 Jenkinsfile 을 가져온다. 로컬 작업트리/드롭인 zip 만 고쳐선 반영 안 됨 → 수정 후 **commit & push** 필수.

### [겪음 encountered] Jenkins agent 파드 ContainerCreating 고착 — `bitnami/kubectl:latest` (Bitnami 카탈로그 삭제)
- **증상:** `si-msa-cd` 빌드가 파싱은 통과했으나 agent 파드(jenkins ns)가 `Pending`/`ContainersNotReady`, 컨테이너 `kaniko/kubectl/jnlp` 가 `ContainerCreating` 에서 안 올라옴. 컨트롤러는 agent "offline" 으로 작업 미할당.
- **원인:** podTemplate 의 kubectl 컨테이너가 `bitnami/kubectl:latest` 참조. Bitnami 가 2025-08-28~09-29 사이 `docker.io/bitnami` 공개 카탈로그를 삭제하고 `bitnamilegacy` 로 이전 → 해당 이미지 pull 실패(ImagePullBackOff). (단, 큰 이미지 첫 pull 지연과 구분할 것: `kubectl describe pod` Events 에서 `Pulling`/`ImagePullBackOff`/`FailedMount` 확인.)
- **해결:** kubectl 컨테이너 이미지를 유지되는 것으로 교체. **`alpine/kubectl:<ver>`**(alpine 기반 → `/bin/sh`+`sleep` 존재) 사용. 잡이 SCM 파이프라인이므로 **commit & push** 후 재빌드.
- **★ 부수 함정:** 교체 시 `registry.k8s.io/kubectl` 은 **distroless**(셸·`sleep` 없음)라 이 podTemplate 패턴(`command: sleep infinity` 유지 + Jenkins `container('kubectl'){ sh ... }`)엔 부적합 → `CreateContainerError` 또는 sh 스텝 실패. 셸 포함 이미지를 쓸 것.
