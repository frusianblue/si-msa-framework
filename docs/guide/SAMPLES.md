# 샘플 코드 (Samples)

**새 업무 서비스를 세워 첫 기능까지** 가는 최소 경로 + 모듈별 복붙 샘플. 이 문서만으로 시작할 수 있게 기본 셋업을 함께 담았다. 표준 규약의 배경은 [`DEVELOPER_GUIDE.md`](DEVELOPER_GUIDE.md), 어떤 모듈을 켤지는 [`MODULE_COMPOSITION.md`](MODULE_COMPOSITION.md).

> 레퍼런스 구현은 레포의 **`services/user-service`**. 아래 1장은 그 서비스의 실제 구조를 그대로 따른다.

---

## 0. 새 서비스 골격 (기본 셋업)

### 0.1 의존성 — `services/<svc>/build.gradle`
```gradle
dependencies {
    implementation project(':framework:framework-core')      // 항상
    implementation project(':framework:framework-mybatis')   // DB 쓰면
    implementation project(':framework:framework-security')  // 인증 쓰면
    implementation project(':framework:framework-openapi')   // API 문서(선택)
    // 필요 모듈만 추가: framework-commoncode / -file / -idempotency / -excel ...

    implementation 'org.springframework.boot:spring-boot-starter-flyway'
    runtimeOnly 'org.flywaydb:flyway-database-postgresql'
    runtimeOnly 'org.postgresql:postgresql'
    runtimeOnly 'com.h2database:h2'                           // 로컬/테스트
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}
```
> 새 서비스를 만들면 `settings.gradle` 에 `include 'services:<svc>'` 추가 + 서비스 `README.md`(포트·프로파일·기동법) 작성이 프로젝트 규약.

### 0.2 부트 클래스
```java
@SpringBootApplication
@MapperScan("com.company.order.mapper")   // 매퍼 인터페이스 패키지
public class OrderServiceApplication {
    public static void main(String[] args) { SpringApplication.run(OrderServiceApplication.class, args); }
}
```

### 0.3 `application.yml` (최소)
```yaml
server: { port: 8080 }
spring:
  application: { name: order-service }
  profiles: { active: ${SPRING_PROFILES_ACTIVE:local} }   # local | dev | prod
  threads: { virtual: { enabled: true } }                 # Java 21 가상 스레드
  flyway: { enabled: true, locations: classpath:db/migration, baseline-on-migrate: true }
mybatis:
  mapper-locations: classpath*:mapper/**/*.xml
  type-aliases-package: com.company.order.domain
framework:
  security:
    jwt: { secret: ${JWT_SECRET:change-this-to-a-very-long-secret-key-at-least-32-bytes!!} }
    token-store: { type: ${TOKEN_STORE_TYPE:memory} }     # 운영은 redis
  crypto: { aes-secret: ${AES_SECRET:order-project-aes-secret-change-me} }
management:
  endpoints: { web: { exposure: { include: health,info,prometheus,metrics } } }
  endpoint: { health: { probes: { enabled: true } } }
```
`application-local.yml` 은 H2 인메모리(외부 설치 0):
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:userdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1
    username: sa
    password:
  flyway: { locations: classpath:db/migration,classpath:db/seed-local }   # 로컬 시드 추가
```

### 0.4 Flyway 마이그레이션 — `src/main/resources/db/migration/V1__init.sql`
```sql
CREATE TABLE orders (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_no    VARCHAR(40)  NOT NULL UNIQUE,
    amount      BIGINT       NOT NULL,
    status      VARCHAR(20)  NOT NULL,
    created_at  TIMESTAMP, created_by VARCHAR(100),   -- BaseEntity 감사 컬럼
    updated_at  TIMESTAMP, updated_by VARCHAR(100)
);
```

---

## 1. 완결 CRUD 한 슬라이스 (표준 응답·예외·페이징·감사·RBAC)

`user-service` 의 실제 패턴. Controller → Service → Mapper(+XML) → Entity/DTO 한 묶음.

### 1.1 Entity — `BaseEntity` 상속 (감사필드 자동)
```java
public class Order extends BaseEntity {          // createdAt/By, updatedAt/By 상속
    private Long id;
    private String orderNo;
    private long amount;
    private String status;
    // getter/setter (Lombok @Getter/@Setter 가능)
}
```

### 1.2 DTO — record + `@Valid` + 응답 마스킹
```java
public record OrderCreateRequest(
        @NotBlank(message = "주문번호는 필수입니다.") String orderNo,
        @Positive(message = "금액은 0보다 커야 합니다.") long amount) {}

public record OrderResponse(Long id, String orderNo, long amount, String status) {
    public static OrderResponse from(Order o) {
        return new OrderResponse(o.getId(), o.getOrderNo(), o.getAmount(), o.getStatus());
    }
}
```
> 민감 필드는 응답 변환 시 `MaskingUtils.maskName/maskEmail/...` 로 마스킹(레퍼런스의 `UserResponse.from` 참고).

### 1.3 Mapper — interface + XML
```java
@Mapper
public interface OrderMapper {
    int insert(Order order);
    Optional<Order> findById(@Param("id") Long id);
    List<Order> findPage(@Param("page") PageRequest page);
    long countAll();
}
```
```xml
<!-- src/main/resources/mapper/OrderMapper.xml -->
<mapper namespace="com.company.order.mapper.OrderMapper">
  <!-- snake_case <-> camelCase 는 MyBatisConfig 가 자동 처리 -->
  <insert id="insert" parameterType="com.company.order.domain.Order"
          useGeneratedKeys="true" keyProperty="id">
    INSERT INTO orders (order_no, amount, status, created_at, created_by)
    VALUES (#{orderNo}, #{amount}, #{status}, #{createdAt}, #{createdBy})
  </insert>
  <select id="findById" resultType="com.company.order.domain.Order">
    SELECT * FROM orders WHERE id = #{id}
  </select>
  <select id="findPage" resultType="com.company.order.domain.Order">
    SELECT * FROM orders ORDER BY id DESC
    LIMIT #{page.size} OFFSET #{page.offset}
  </select>
  <select id="countAll" resultType="long">SELECT COUNT(*) FROM orders</select>
</mapper>
```

### 1.4 Service — 트랜잭션·감사·예외·현재 사용자
```java
@Service
public class OrderService {
    private final OrderMapper mapper;
    private final CurrentUserProvider currentUser;          // 생성자 주입

    public OrderService(OrderMapper mapper, CurrentUserProvider currentUser) {
        this.mapper = mapper; this.currentUser = currentUser;
    }

    @Transactional
    @AuditLog(action = "ORDER_CREATE", target = "ORDER")    // 메서드 감사(framework-audit)
    public OrderResponse create(OrderCreateRequest req) {
        Order o = new Order();
        o.setOrderNo(req.orderNo());
        o.setAmount(req.amount());
        o.setStatus("CREATED");
        o.setCreatedBy(currentUser.getCurrentUser().orElse("system"));
        mapper.insert(o);
        return OrderResponse.from(o);
    }

    @Transactional(readOnly = true)
    public OrderResponse get(Long id) {
        Order o = mapper.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.Common.NOT_FOUND, "주문 없음: " + id));
        return OrderResponse.from(o);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> list(PageRequest page) {
        long total = mapper.countAll();
        var content = mapper.findPage(page).stream().map(OrderResponse::from).toList();
        return PageResponse.of(content, page, total);
    }
}
```

### 1.5 Controller — `ApiResponse` · `@PreAuthorize` · 페이징
```java
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {
    private final OrderService service;
    public OrderController(OrderService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<OrderResponse> create(@Valid @RequestBody OrderCreateRequest req) {
        return ApiResponse.ok(service.create(req), "주문이 생성되었습니다.");
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @GetMapping
    public ApiResponse<PageResponse<OrderResponse>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(service.list(PageRequest.of(page, size)));
    }
}
```

### 1.6 호출
```bash
TOKEN=$(curl -s -X POST localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' -d '{"loginId":"admin","password":"admin123"}' | jq -r .data.accessToken)

curl -X POST localhost:8080/api/v1/orders -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"orderNo":"O-1001","amount":50000}'

curl localhost:8080/api/v1/orders?page=0\&size=20 -H "Authorization: Bearer $TOKEN"
```

---

## 2. 모듈 샘플 (필요한 것만 복붙)

각 모듈은 `build.gradle` 의존 + `framework.<name>.enabled=true` 후 사용. 토글 상세는 각 README.

### 2.1 파일 업로드/다운로드 — `framework-file`
```java
private final FileService files;

@PostMapping("/files")
public ApiResponse<FileMetaDto> upload(@RequestParam MultipartFile file) {
    return ApiResponse.ok(files.upload(file));          // 검증/스캔/암호화 후 저장
}
@GetMapping("/files/{id}")
public ResponseEntity<InputStreamResource> download(@PathVariable Long id) {
    FileMetadata m = files.getMeta(id);
    return ResponseEntity.ok(new InputStreamResource(files.download(m)));  // 한글명 RFC 5987
}
```

### 2.2 Excel 다운로드(스트리밍) — `framework-excel`
```java
private final ExcelExporter exporter;   // framework.excel.enabled=true

@GetMapping("/orders/excel")
public void export(HttpServletResponse res) throws IOException {
    res.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    res.setHeader("Content-Disposition", "attachment; filename=orders.xlsx");
    List<ExcelColumn<OrderResponse>> cols = List.of(
        ExcelColumn.of("주문번호", OrderResponse::orderNo),
        ExcelColumn.of("금액", o -> String.valueOf(o.amount())));
    exporter.write(res.getOutputStream(), "주문", cols, orderService.findAll());  // SXSSF 스트리밍
}
```

### 2.3 멱등 처리(중복요청 차단) — `framework-idempotency`
```java
@PostMapping("/payments")
@Idempotent                                  // framework.idempotency.enabled=true
public ApiResponse<PaymentResult> pay(@Valid @RequestBody PayRequest req) {
    return ApiResponse.ok(paymentService.pay(req));
}
```
클라이언트는 같은 작업에 동일 `Idempotency-Key` 헤더를 보낸다 → 중복 호출은 1회만 처리(`replay.enabled` 면 저장 응답 재생). 멀티 인스턴스는 `store.type=redis`.

### 2.4 외부 API 호출(타임아웃·재시도·서킷) — `framework-client`
```java
private final RestClient client;
public PartnerClient(RestClient.Builder frameworkRestClientBuilder) {   // 주입되는 빌더 사용
    this.client = frameworkRestClientBuilder.baseUrl("https://api.partner.com").build();
}
public PartnerDto get(String id) {
    return client.get().uri("/items/{id}", id).retrieve().body(PartnerDto.class);
}
```
GET/PUT/DELETE 는 자동 재시도, 호스트별 서킷브레이커, `traceId` 전파. 서킷 OPEN 시 `CircuitOpenException`.

### 2.5 공통코드 조회(캐시) — `framework-commoncode`
```java
private final CommonCodeService codes;
List<CommonCodeDto> genders = codes.getByGroup("GENDER");   // 2회차부터 캐시 히트
```

---

## 3. 더 보기
- 모듈 조합·연결·함정 → [`MODULE_COMPOSITION.md`](MODULE_COMPOSITION.md)
- 표준 규약(응답/예외/util/하지 말 것) → [`DEVELOPER_GUIDE.md`](DEVELOPER_GUIDE.md)
- 사업유형 프리셋 → [`USAGE_BY_PROJECT_TYPE.md`](USAGE_BY_PROJECT_TYPE.md)
- 모듈별 상세 → 각 [`../../framework/`](../../framework/) 하위 `README.md`
