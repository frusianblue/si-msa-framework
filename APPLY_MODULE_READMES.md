# 적용 방법 (2단계 — 모듈 README 19개)

레포 **루트**에서 이 zip 을 풀면 README 없던 19개 모듈에 `README.md` 가 추가된다(기존 파일과 충돌 없음 — 모두 신규).

```bash
unzip -o module-readmes.zip
git status        # framework/framework-*/README.md 19개 신규
```

## 전제
README 안의 문서 링크(`../../docs/reference/...`, `../../docs/modules/...`)는
**1단계(docs-reorg.zip) 적용 후 구조**를 가리킨다. 1단계를 먼저(또는 함께) 적용할 것.

## 추가된 19개
core · mybatis · security · openapi · redis · commoncode · file · file-s3 · file-batch ·
audit · messaging · excel · batch · notification · mfa · oauth-client · saml-sp · archive · archtest

→ 이제 전 35개 모듈이 README 를 갖는다(양식 통일: 켜는 법 / 쓰는 법 / 끄는 법 / 덮어쓰기 / 버전 관리).
