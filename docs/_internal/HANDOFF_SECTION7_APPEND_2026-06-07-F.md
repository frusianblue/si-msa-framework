# HANDOFF.md §7 append — 2026-06-07 (F): 관측(Grafana/Prometheus) B안 ingress 노출

> 다음 세션에서 `HANDOFF.md §7` 본문으로 병합(-C/-D/-E 와 함께). 병합 후 폐기.

## 세션8: 관측 호스트 접속 = ingress (devops)

### 맥락
세션7 까지 CI/CD 1차 완주(6파드 Running, 4서비스 :cd161c73c135 단일 sha). 이어서 관측 스택을
Harbor/Jenkins 와 동일한 B안 ingress 패턴으로 호스트 노출.

### 결정/구현
- 신규 `deploy/k8s/standalone-kind/monitoring-values.yaml` — kube-prometheus-stack values:
  · `grafana.ingress`(host grafana.local, ingressClassName nginx, **ssl-redirect:"false"**) + `grafana.ini.server`(domain=grafana.local, root_url).
  · `prometheus.ingress`(host prometheus.local, ssl-redirect:"false") + `prometheusSpec.externalUrl=http://prometheus.local`.
- `05-prometheus-stack.sh`: helm 에 `-f monitoring-values.yaml` 머지(기존 --set 토글과 공존, 재실행=upgrade 멱등) + 접속안내를 ingress 우선(+ ingress 생성/도달 체크, hosts 안내, port-forward 대안).
- `06-grafana-jvm-dashboard.sh`: Grafana 접속을 grafana.local 우선.

### 함정(되돌리지 말 것)
- **ssl-redirect:"false" 필수** — ingress-nginx 기본 HTTP→HTTPS 308 → 평문 접속 깨짐(Harbor 와 동일 근거).
- **Grafana domain/root_url** 을 호스트에 맞춰야 로그인 리다이렉트/링크 정확. Prometheus externalUrl 동일.
- **관측 UI 는 노드 이름해소 불요** — Harbor(노드가 pull)와 달리 호스트 브라우저만 봄 →
  **호스트(Windows+WSL) hosts 한 줄**(`127.0.0.1 grafana.local prometheus.local`). 노드 certs.d/노드 hosts/split-horizon 전부 불필요.
- 점검: `curl -H "Host: grafana.local" http://localhost/api/health`(hosts 무관, ingress 라우팅만).

### 변경 파일
- `deploy/k8s/standalone-kind/monitoring-values.yaml`(신규)
- `deploy/k8s/standalone-kind/05-prometheus-stack.sh`, `06-grafana-jvm-dashboard.sh`
- 문서: `docs/guide/PITFALLS.md` §9 관측-ingress ★항목 + 자가진단 1행; `deploy/k8s/standalone-kind/README.md`; `docs/_internal/HANDOFF_SUMMARY.md`.

### 검증(저자환경, 오프라인)
- bash -n(05/06). monitoring-values.yaml YAML 키 파싱(grafana/prometheus ingress + ssl-redirect=false + grafana.ini). helm `-f` 연결 확인.
- 실 helm upgrade·브라우저 접속은 받는 쪽 로컬.

### 받는 쪽 액션
1. 세션8 코드 적용 + commit & push.
2. `bash deploy/k8s/standalone-kind/05-prometheus-stack.sh --grafana`(helm upgrade — ingress 생성).
3. 호스트 hosts 에 `127.0.0.1 grafana.local prometheus.local` 추가(Windows + WSL 양쪽).
4. `bash deploy/k8s/standalone-kind/06-grafana-jvm-dashboard.sh --grafana`(JVM 대시보드 + 4/4 타깃 UP).
5. 브라우저: `http://grafana.local`(admin / grafana secret), `http://prometheus.local`.
