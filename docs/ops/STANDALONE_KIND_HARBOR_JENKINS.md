# STANDALONE_KIND_HARBOR_JENKINS.md — 인-클러스터 Harbor + Jenkins CI/CD (standalone kind, ingress 호스트 접속)

> 환경: **standalone kind `kind-sanity`**(3노드, WSL+Docker Desktop). 컨텍스트 `kind-sanity`.
> ⚠️ 이 문서가 `HARBOR_SETUP.md` / `K8S_INGRESS_HARBOR.md`(둘 다 **은퇴한 `docker-desktop` kind** 전제)를 **대체**한다.
> 【B안 (2026-06-07 세션4)】 호스트 접속을 **port-forward 졸업** → 브라우저에서 `http://harbor.local` / `http://jenkins.local`.
>   이전 NodePort(30002/32000)+port-forward 판을 **ingress-nginx + extraPortMappings** 로 교체.

---

## 0. 설계 한눈에 (왜 이렇게)

standalone kind 엔 docker-desktop 의 LB→localhost 매직이 없다. 그래서 **ingress-nginx 컨트롤러를 control-plane
노드에 hostPort(80/443)로 띄우고**, 그 포트를 **kind 생성 시 `extraPortMappings` 로 호스트에 게시**한다.
세 주체가 모두 `harbor.local`(이름) 한 점으로 ingress 에 닿는다 — 해소 IP 만 주체별로 다르다(split-horizon):

| 주체 | `harbor.local` 해소 | 경로 | 설정 주체 |
|---|---|---|---|
| 호스트 브라우저 | `127.0.0.1` (OS hosts) | localhost:80(extraPortMappings) → ingress | 사용자(hosts 파일) |
| 인-클러스터 Kaniko (push) | ingress-nginx **ClusterIP** | CoreDNS hosts | `08` 이 CoreDNS 패치 |
| 노드 containerd (pull) | **CP_IP** (노드 /etc/hosts) | CP:80(ingress hostPort) → ingress | `registry-trust` DaemonSet(`08`/`07` 설정) |

`externalURL=http://harbor.local` → **토큰 realm 도 이름** → 세 주체 모두 자기 해소경로로 도달. 이미지 ref 는
`harbor.local/si-msa/<svc>:<tag>` 그대로(오버레이 무수정).

**재부팅 내성**: 노드 certs.d/`/etc/hosts` 는 `registry-trust` DaemonSet 이 부팅마다 재기입(bind mount 졸업).
CP_IP 가 재부팅으로 바뀌면 `07-reboot-recover.sh` 가 재산출해 CM(node-etc-hosts)·노드를 갱신. base
`imagePullPolicy: IfNotPresent` + 불변 `:<sha>` 라 미러가 잠깐 끊겨도 노드캐시로 기동 유지.

---

## ⚠️ 선행 주의 — 재생성 = 데이터 소실
`extraPortMappings` 는 `kind create` 시점 고정 → **클러스터 재생성 불가피**. 재생성하면 PVC 전부 소실:
dev postgres(데모데이터), Harbor(이미지/프로젝트), Jenkins(잡/히스토리).
- dev postgres: Flyway 스키마 재생성 → 데모데이터만 손실(리허설 수용).
- Harbor/Jenkins: 08/09 멱등 재실행으로 재구축(Jenkins 잡 UI 1회·Harbor 이미지 파이프라인 1회 다시).
- 보존 필요 시 재생성 전 백업: `pg_dump`(postgres-0), Harbor export, Jenkins `$JENKINS_HOME` tar.

---

## 1. 실행 순서 (PASS 게이트 단위 — 저자환경 실행 불가, Chae 로컬 iterate)

```bash
cd deploy/k8s/standalone-kind

# Stage 0 — 재생성(extraPortMappings 박기). ⚠️ PVC 소실.
bash 00-cleanup.sh --teardown-sanity
bash 01-pull-sanity.sh            # 새 kind-config(80/443 + ingress-ready, extraMounts 제거)로 생성 + reg.local 직접시드 + PASS

# Stage 1 — ingress-nginx (호스트 진입 계층)
bash 10-ingress-nginx.sh          # PASS: curl http://localhost → 404

# Stage 2 — 앱(영속 postgres 포함) + 노드 좌표
bash 07-reboot-recover.sh         # DaemonSet + certs.d/+/etc/hosts (08 후 좌표 정합; 우선 1차)
kubectl --context kind-sanity apply -k ../overlays/dev    # 6파드(앱4+redis+postgres)

# Stage 3 — Harbor (ingress, harbor.local)
bash 08-harbor-install.sh         # externalURL=http://harbor.local + CoreDNS→ingress ClusterIP + 노드 /etc/hosts→CP_IP

# Stage 4 — Jenkins (ingress, jenkins.local) + 자격/RBAC
bash 09-jenkins-install.sh

# Stage 5 — 호스트 hosts 등록 (1회)
#   Windows: C:\Windows\System32\drivers\etc\hosts  (관리자)
#   WSL    : /etc/hosts                              (sudo)
#     127.0.0.1 harbor.local jenkins.local

# Stage 6 — 최종 PASS 게이트
bash 11-host-access-verify.sh     # harbor.local/jenkins.local 호스트 접속 + 노드/인-클러스터 좌표 일괄 점검
```

**최종 PASS**: 브라우저 `http://harbor.local`(admin/Harbor12345) · `http://jenkins.local`(admin/admin123)
**port-forward 없이** 접속.

---

## 2. 파이프라인 (Jenkins UI 1회)
1. New Item → Pipeline → `si-msa-cd`
2. Definition = *Pipeline script from SCM* · Git · `https://github.com/frusianblue/si-msa-framework.git` · `*/master`
3. Script Path = `deploy/cicd/Jenkinsfile.kind` → Save → **Build Now**
   - Kaniko build → push `harbor.local/si-msa/<svc>:<git-sha>`+`:dev`(`--insecure-registry`)
   - `kubectl apply -k overlays/dev` → `set image :<sha>`(불변 핀) → rollout
   - 첫 빌드에서 노드 pull(certs.d→/etc/hosts→CP:80→ingress)·:<sha> 불변배포 실증.

---

## 3. 트리아지 빠른 표
| 증상 | 원인 → 조치 |
|---|---|
| `curl http://localhost` connection refused | extraPortMappings 부재(구 kind-config 로 생성). 재생성 필요. host 80 점유도 확인. |
| ingress controller Pending | `ingress-ready=true` 라벨 없음 → kind-config(B안) 재생성 또는 임시 label. |
| `harbor.local` 308→https | ingress annotation `ssl-redirect=false` 누락(harbor-values). |
| Harbor push 413 | `proxy-body-size: "0"` 누락(harbor-values). |
| 노드 pull `connection refused`/`x509` | 노드 /etc/hosts harbor.local 또는 certs.d 미반영 → `07-reboot-recover.sh`(CP_IP 재산출). |
| Kaniko push DNS 실패 | CoreDNS harbor.local 매핑 누락(08 §6) → `kubectl -n kube-system rollout restart deploy/coredns`. |
| 재부팅 후 auth-server 만 ImagePullBackOff | bind mount 휘발(구 방식). DaemonSet 미적용 → `07-reboot-recover.sh`. |

---

## 4. 대안 A(보류) — 프록시 컨테이너(재생성 불요)
재생성 없이 임시로 호스트 노출만 필요하면: `docker run -d --network kind -p 8080:80 alpine/socat \
TCP-LISTEN:80,fork TCP:<INGRESS_CLUSTERIP_또는_CP_IP>:80`. B안 PASS 후엔 불필요.
