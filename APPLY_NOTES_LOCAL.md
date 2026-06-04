# 적용 노트 — 로컬 k8s 테스트(overlays/local) (2026-06-04)

기존 k8s/CI-CD 멀티서비스 산출물 위에 **로컬 자기완결 테스트 오버레이**를 추가하는 패키지다.
레포 루트 기준 그대로 덮어쓰면 된다(신규 4 + 수정 2 파일, 삭제 없음).

## 포함 파일
- `deploy/k8s/overlays/local/kustomization.yaml`  (신규)
- `deploy/k8s/overlays/local/postgres.yaml`       (신규 — 인-클러스터 PG: authdb/sidb + 역할)
- `deploy/k8s/overlays/local/secrets-local.yaml`  (신규 — prod 가드 통과용 강한 시크릿)
- `docs/modules/LOCAL_K8S_TEST.md`                (신규 — kind 단계별 가이드)
- `docs/modules/K8S_CICD_MULTISERVICE.md`         (수정 — local 오버레이 포인터)
- `HANDOFF_SUMMARY.md`                            (수정 — local 오버레이 기록)

## 한 줄 테스트 (kind 기준)
```bash
kind create cluster --name si-msa
# 4개 이미지 빌드 후 노드 적재 (:local)
for svc in gateway auth-server user-service admin-service; do
  ./gradlew :services:$svc:bootJar
  docker build -f deploy/docker/Dockerfile \
    --build-arg JAR_FILE=services/$svc/build/libs/$svc-1.0.0.jar \
    -t registry.example.com/si-msa/$svc:local .
  kind load docker-image registry.example.com/si-msa/$svc:local --name si-msa
done
# 배포
kubectl apply -k deploy/k8s/overlays/local
kubectl -n si-msa get pods -w        # 앱이 postgres ready 까지 몇 번 재시작하는 건 정상
```

상세(스모크 체크·트러블슈팅·관측 옵션)는 `docs/modules/LOCAL_K8S_TEST.md`.

## 핵심 설계
- base 의 `DB_URL` 이 이미 `postgres` 호스트를 가리켜 **DB_URL 패치 불필요** — Service 이름 `postgres` 로 맞춤.
- initdb 역할(authuser/siuser)·비번이 `secrets-local.yaml` 의 DB_USER/DB_PASSWORD 와 일치.
- ServiceMonitor 는 `$patch: delete` 로 제거(로컬엔 Prometheus Operator CRD 없음). 관측까지 보려면 kube-prometheus-stack 설치 후 그 패치만 빼면 된다.
- base 가 `prod` 프로파일이라 dev 의 placeholder AES 는 `AesMasterKeySafetyGuard` 에 막힘 → 32바이트 AES 등 강한 값을 local 전용으로 동봉.
