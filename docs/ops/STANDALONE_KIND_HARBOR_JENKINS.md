# STANDALONE_KIND_HARBOR_JENKINS.md — 인-클러스터 Harbor + Jenkins CI/CD (standalone kind)

> 환경: **standalone kind `kind-sanity`**(3노드, WSL+Docker Desktop). 컨텍스트 `kind-sanity`.
> ⚠️ 이 문서가 `HARBOR_SETUP.md` / `K8S_INGRESS_HARBOR.md`(둘 다 **은퇴한 `docker-desktop` kind**
>   = LoadBalancer→localhost 자동매핑 전제)를 **대체**한다. standalone kind 엔 그 매직이 없다.
> 산출물: `deploy/k8s/standalone-kind/{registry-trust-daemonset.yaml, 07..09-*.sh, harbor-values.yaml,
>   jenkins-values.yaml, jenkins-rbac.yaml}`, `deploy/cicd/Jenkinsfile.kind`, base/prod imagePullPolicy.

---

## 0. 설계 한눈에 (왜 이렇게)

세 주체가 같은 Harbor 에 닿아야 한다. standalone kind 엔 LB→localhost 가 없으므로 **control-plane 노드
IP(`CP_IP`, kind net 172.18.x)를 단일 좌표**로 통일한다(재부팅에도 IP 보존):

| 주체 | 경로 | 설정 주체 |
|---|---|---|
| 노드 containerd (pull) | `harbor.local` → certs.d → `http://CP_IP:30002` | `registry-trust` DaemonSet(부팅마다 재기입) |
| 인-클러스터 파드 Kaniko (push) | `harbor.local` → CoreDNS hosts → `CP_IP` :30002 | `08` 스크립트가 CoreDNS 패치 |
| 호스트(포털/디버그) | `kubectl port-forward` | 수동 |

이미지 ref 는 **`harbor.local/si-msa/<svc>:<tag>` 그대로**(오버레이 무수정). `externalURL=http://CP_IP:30002`
라 토큰 realm 도 동일 좌표 → 노드/파드 모두 도달.

**재부팅 내성의 핵심**: 과거엔 노드 certs.d 를 호스트 bind mount 로 얹었는데 재부팅 시 휘발(2026-06-07 함정).
이제 `registry-trust` DaemonSet 이 ConfigMap 내용을 **노드 부팅마다 /etc/containerd/certs.d 에 재기입**한다.
추가로 base `imagePullPolicy: IfNotPresent` → 노드 캐시·불변 `:<sha>` 면 미러 끊겨도 기동 유지.

---

## 1. 실행 순서 (PASS 게이트 단위)

각 단계는 **독립 검증**하고 통과 후 다음으로. (저자환경에서 실행 불가 — Chae 로컬 실행/iterate.)

### Stage A — 영구 pull/재부팅 수정 (가장 견고, 먼저 적용)
```bash
# base 4개 deployment 에 imagePullPolicy:IfNotPresent, prod overlay 는 Always 패치(동봉).
kubectl --context kind-sanity apply -f deploy/k8s/standalone-kind/registry-trust-daemonset.yaml
bash deploy/k8s/standalone-kind/07-reboot-recover.sh      # 재부팅 후엔 이 한 줄이 복구 루틴
```
**PASS**: `kubectl -n si-msa get pods` 6파드 Ready. 노드에 certs.d 존재
(`docker exec sanity-control-plane cat /etc/containerd/certs.d/harbor.local/hosts.toml`).

### Stage B — 인-클러스터 Harbor
```bash
bash deploy/k8s/standalone-kind/08-harbor-install.sh
```
**PASS**: `kubectl -n harbor get pods` 전부 Running, `get pvc` 전부 Bound.
포털: `kubectl -n harbor port-forward svc/harbor 8080:80` → http://localhost:8080 (admin/Harbor12345),
`si-msa` 프로젝트(public) 보임. CoreDNS 에 `harbor.local` 매핑, 노드 certs.d 가 `CP_IP:30002` 로 갱신됨.

### Stage C — 인-클러스터 Jenkins + 파이프라인
```bash
bash deploy/k8s/standalone-kind/09-jenkins-install.sh
kubectl -n jenkins port-forward svc/jenkins 8088:8080      # http://localhost:8088 (admin/admin123)
```
UI 1회: New Item → Pipeline `si-msa-cd` → *Pipeline script from SCM* →
Git `https://github.com/frusianblue/si-msa-framework.git`, Branch `*/master`,
Script Path `deploy/cicd/Jenkinsfile.kind` → Save → **Build Now**.

**PASS**: 파이프라인 3단계(Checkout → Kaniko build&push → Deploy) 그린.
Harbor 포털 `si-msa` 에 4 repo × (`<sha>`,`dev`). `kubectl -n si-msa get pods` 가 `:<sha>` 이미지로 롤아웃.

---

## 2. 가장 깨지기 쉬운 곳(저자환경 미검증 — 실거동으로 트리아지)

1. **NodePort 토큰 realm**: `externalURL=http://CP_IP:30002`. 노드 pull 시 401→token 요청이 `CP_IP:30002`
   로 가야 한다. 막히면 `kubectl -n si-msa describe pod`(Events)에 `x509`/`401`/`connection refused` 단서.
2. **Kaniko push 이름해소**: 파드가 `harbor.local` 을 CoreDNS 로 풀어야 함. 막히면
   `kubectl -n jenkins exec <agent-pod> -c kaniko -- nslookup harbor.local` 로 `CP_IP` 확인.
   CoreDNS rollout 안 됐으면 `08` 의 §6 재실행.
3. **HTTP insecure**: Kaniko `--insecure-registry/--skip-tls-verify-registry=harbor.local` 필수(동봉).
4. **Gradle 빌드 egress**: Kaniko builder 스테이지가 Maven Central 접근 필요(클러스터 인터넷). 사내망이면
   Kaniko 컨테이너에 프록시 env 주입 필요.
5. **CP_IP 변동**: 클러스터를 **재생성**하면 IP 바뀔 수 있음 → `08` 재실행(재부팅만으론 보존).

막히면 그 단계의 `describe`/로그를 공유 → 환경 맞춤으로 수정(2026-06-07 디버깅과 동일 방식).

---

## 2.5 호스트 접속 (현재 port-forward / 다음 = B안 ingress)
standalone kind 는 `kind-config.yaml` 에 `extraPortMappings` 가 없으면 **노드 포트가 호스트로 안 나온다**
(docker-desktop kind 의 LB→localhost 자동게시가 없음 — 이게 "ingress 깔면 포워딩 불요" 가 여기선 안 되는 이유).
- **지금(임시)**: `kubectl -n harbor port-forward svc/harbor 8080:80` · `kubectl -n jenkins port-forward svc/jenkins 8088:8080`.
- **영구(B안, 재생성 필요)**: 새 kind-config 에 `extraPortMappings`(80/443)+`ingress-ready` 라벨 → 재생성 → ingress-nginx →
  Harbor/Jenkins 를 ingress 노출(`harbor.local`/`jenkins.local`) → Windows·WSL hosts `127.0.0.1 harbor.local jenkins.local`.
  상세 착수 계획 = `docs/_internal/planning/NEXT_INGRESS_HOST_ACCESS.md`(데이터 재구축 주의·토큰 realm 재조정 포함).

## 3. 끄는 법 / 정리
```bash
helm --kube-context kind-sanity -n jenkins uninstall jenkins
helm --kube-context kind-sanity -n harbor  uninstall harbor
kubectl --context kind-sanity delete ns jenkins harbor --ignore-not-found
# registry-trust 는 남겨도 무해(노드 미러 유지). 제거 시:
kubectl --context kind-sanity delete -f deploy/k8s/standalone-kind/registry-trust-daemonset.yaml
```
