# NEXT_CI_KANIKO_MULTIBUILD.md — 다음 세션 착수용 (Kaniko 다중 이미지 빌드 재설계)

> 상태(2026-06-07 세션4): 🟡 미해결. 호스트 접속(B안)·CI 파이프라인 1차 가동은 완료.
>   `si-msa-cd` 가 **gateway 1개는 빌드+harbor.local push 성공**(split-horizon push 실증).
>   문제는 **4서비스 루프 중 2회차부터 실패**.

---

## 증상
`Build & Push (Kaniko)` 스테이지의 `container('kaniko'){ script { [4서비스].each { sh "/kaniko/executor ..." } } }` 에서
1회차(gateway) 빌드+push 성공 직후 2회차(auth-server)의 `sh` 가:
```
[Pipeline] sh
ERROR: Process exited immediately after creation. See output below
Executing sh script inside container kaniko of pod si-msa-cd-...
```
→ gateway 만 Harbor 에 올라가고(`si-msa/gateway:dev`+`:<sha>`+cache), 나머지 3서비스 누락.

## 원인(분석)
Kaniko `executor:debug` 는 빌드 시 "Taking snapshot of full filesystem" 으로 컨테이너 rootfs 를 스냅샷/추출하며
`/busybox`(셸 `sh`/`sleep` 제공) 등을 덮어쓸 수 있다. Jenkins durable-task 의 `sh` 스텝은 컨테이너 안 셸 래퍼에
의존하는데, 1회차 executor 가 rootfs 를 건드린 뒤 2회차 `sh` 가 쓸 셸이 사라져 "Process exited immediately
after creation". **Kaniko 는 본래 "컨테이너당 이미지 1개" 모델** — 한 컨테이너에서 executor 를 여러 번 돌리는
패턴이 깨지기 쉽다.

## 해결 후보 (다음 세션 검증)
- **(a) 서비스별 stage + 별도 kaniko 컨테이너** — podTemplate 에 kaniko1~4 또는 stage 마다 새 컨테이너. 가장 명확하나 파드 무거움.
- **(b) `parallel` 매트릭스 — 서비스당 독립 agent 파드** — 서비스별로 완전히 분리된 kaniko 파드(1컨테이너1이미지 원칙 준수), 병렬 가속. **권장 1순위.**
- **(c) `--cleanup` + 단일 `sh` 연쇄** — executor 에 `--cleanup` 주고, 4번을 **한 번의 `sh` 호출** 안에서 `&&` 로 연쇄(셸 래퍼 재생성 회피). 가장 가벼운 수정. **권장 우선 시도.**
- **(d) 단일 `sh` + `for svc` 루프** — Groovy `.each{ sh }`(매번 새 sh 래퍼) 대신 한 `sh '''for svc in ...; do /kaniko/executor ...; done'''` 로 셸 1회만 생성. (c)와 결합 가능.

## 검증 게이트
1. 4서비스 모두 `harbor.local/si-msa/<svc>:dev`+`:<sha>` push (Harbor 저장소 4개 + cache).
2. 노드 pull 경로(certs.d→/etc/hosts→CP:80→ingress) 로 `kubectl apply -k overlays/dev` → 6파드 rollout.
3. `set image :<sha>` 불변태그 배포 + rollout status 통과.

## 참고
- push 경로(split-horizon: CoreDNS→ingress ClusterIP)·노드 해소는 gateway 로 이미 실증 → **빌드 오케스트레이션만** 재설계.
- Jenkinsfile = `deploy/cicd/Jenkinsfile.kind`. SCM 파이프라인이므로 수정 후 **commit & push** 해야 반영.
- 관련 함정: PITFALLS §9-B(Kaniko 다중빌드 항목).
