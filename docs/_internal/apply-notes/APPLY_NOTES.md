# 적용 노트 — k8s/CI-CD 멀티서비스화 (2026-06-04)

이 zip 은 레포 루트 기준 **그대로 덮어쓰기**(drop-in) 하면 된다. 단, 기존 flat k8s 파일은
Kustomize 트리(`deploy/k8s/base` + `overlays`)로 **대체**되었으므로 아래 4개를 제거해야 한다.

## 1) 덮어쓰기 (이 zip 의 내용)
zip 안의 디렉토리 구조를 레포 루트에 그대로 풀면 된다. (신규/수정 41개 파일)

## 2) 삭제 (필수 — 기존 단일 서비스 flat 매니페스트)
```bash
git rm deploy/k8s/configmap.yaml \
       deploy/k8s/hpa.yaml \
       deploy/k8s/observability.yaml \
       deploy/k8s/user-service.yaml
```
> 이 4개는 Kustomize base/overlay 로 흡수되었다. 남겨두면 `kubectl apply -f deploy/k8s/`(구방식)
> 와 혼동되므로 반드시 제거한다. 신방식은 항상 `apply -k overlays/<env>`.

## 3) 검증 (받는 쪽 — kubectl/kustomize·gradle 가능 환경)
```bash
# 렌더링 확인
kubectl kustomize deploy/k8s/overlays/dev
kubectl kustomize deploy/k8s/overlays/prod
# 스키마/어드미션(서버 드라이런)
kubectl apply -k deploy/k8s/overlays/dev  --dry-run=server
kubectl apply -k deploy/k8s/overlays/prod --dry-run=server
# 실제 배포(dev)
kubectl apply -k deploy/k8s/overlays/dev
kubectl -n si-msa get deploy,po,svc,hpa,servicemonitor
# 빌드/포맷
./gradlew :services:auth-server:bootJar    # build/libs/auth-server-1.0.0.jar
./gradlew spotlessApply
```

## 4) prod 시크릿 (배포 전 사전 주입 — zip 에 미포함)
prod 오버레이는 Secret 을 포함하지 않는다(ESO/SealedSecrets 전제). 다음 이름/키로 사전 주입:
- `gateway-secret`: JWT_SECRET
- `auth-server-secret`: DB_USER, DB_PASSWORD, FRAMEWORK_JWT_SECRET, AES_SECRET
- `user-service-secret`: DB_USER, DB_PASSWORD, JWT_SECRET, AES_SECRET
- `admin-service-secret`: DB_USER, DB_PASSWORD, JWT_SECRET, AES_SECRET

양식 예시는 `deploy/k8s/overlays/prod/secrets-prod.example.yaml`(배포 대상 아님) 참고.
dev 는 `secrets-dev.yaml`(약한 placeholder)이 동봉되어 별도 주입 불필요.

상세: `docs/modules/K8S_CICD_MULTISERVICE.md`
