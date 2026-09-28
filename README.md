# cc-transplant

器官分配协调服务：登记供体器官与候选受者，启动分配后按候选顺序逐一发出**限时邀约**，
直至唯一候选人接受或名单穷尽。

## 开发环境

- JDK 21
- Spring Boot 4.1.1（Spring WebMVC + Data JPA + Validation）
- Maven Wrapper 3.9.9
- H2 数据库（开发为文件库，测试为内存库）

## 本地运行

启动服务：

    ./mvnw spring-boot:run

运行测试：

    ./mvnw clean test

## 领域模型

| 实体 | 说明 |
| --- | --- |
| `Donor` | 供体器官：外部编号（唯一）、器官类型、血型 |
| `Recipient` | 候选受者：外部编号（唯一）、器官类型、血型、紧急等级、进入等待名单时间、是否激活、所属中心 |
| `Allocation` | 一次分配流程：持有不可变候选快照、流程状态与最终受者；一个供体器官只能有一个流程 |
| `AllocationEntry` | 候选快照条目：启动时的候选人状态副本 + 顺位 `position`（从 1 开始） |
| `Offer` | 限时邀约：明确的 `issuedAt` / `expiresAt` 与状态（PENDING/ACCEPTED/DECLINED/EXPIRED） |
| `OfferEvent` | 邀约决定事件：客户端幂等标识 `eventId`（唯一）、决定类型与首次结果 |

## 主要业务规则

### 1. 登记与校验

- 供体与受者的外部编号**全库唯一**，由数据库唯一约束（`uk_donor_external_id`、
  `uk_recipient_external_id`）兜底，重复登记返回 409。
- 所有必填字段经 Bean Validation 校验（非空、长度、时间不得晚于当前等），校验失败返回
  400 与字段级错误明细。
- 器官类型与血型为枚举，非法值无法通过请求解析。

### 2. 启动分配与候选快照

- 合格候选人条件：**器官类型一致**、**ABO 血型相容**、候选人处于**激活**状态。
  血型相容性：O 可供给任意血型；AB 可接受任意血型；A→{A,AB}、B→{B,AB}。
- 排序规则（确定性、稳定）：
  1. 紧急等级从高到低：`URGENT > HIGH > STANDARD`
  2. 进入等待名单时间从早到晚（等待越久越优先）
  3. 候选人外部编号升序（同分时的稳定兜底）
- 启动时把候选人当时的姓名、紧急等级、等待时间、激活标志、中心**复制为不可变快照**；
  之后候选人资料变化（停用、改中心等）不影响本次分配。
- **没有合格候选人时明确失败（422 `NO_ELIGIBLE_CANDIDATE`），不会创建空流程。**
- 同一供体器官不可重复启动分配（409，唯一约束 `uk_allocation_donor` 并发兜底）。

### 3. 限时邀约逐一发出

- 一个分配流程**任意时刻最多一份 PENDING 邀约**；
  当前邀约被拒绝（DECLINED）或过期（EXPIRED）后，才能为下一位候选人发邀约。
- 每份邀约带明确过期时间（启动时指定 TTL，对流程内所有邀约一致）。
- 邀约已过期但尚未做过期处理时，不允许跳过它直接发下一位（409 `OFFER_EXPIRED`）。
- 重复发起且当前邀约仍有效时，原样返回当前邀约，不产生第二份。

### 4. 接受、拒绝、过期

- **接受**只允许同时满足：邀约 PENDING、当前时间早于 `expiresAt`、候选人仍为激活状态。
  - 过期后接受 → 409 `OFFER_EXPIRED`；候选人已停用 → 409 `RECIPIENT_INACTIVE`。
  - 接受成功后流程进入终态 `ALLOCATED`、器官已分配，其他候选人不能再接受，也不能再发邀约。
- **拒绝**在邀约 PENDING 时生效，之后顺位推进到下一位。
- **过期处理**只允许在当前时间不早于 `expiresAt` 时进行（提前过期 → 409
  `OFFER_NOT_EXPIRED`），之后顺位推进。
- 末位候选人拒绝或过期后，流程在**同一事务内**收敛为终态 `EXHAUSTED`（无人接受）。

### 5. 事件幂等与并发安全

- 接受、拒绝、过期都必须携带 `eventId`。
  - 相同 `eventId` 重放（同邀约、同决定类型）→ 返回首次原结果，响应中 `replayed=true`，
    不会重复落事件。
  - 相同 `eventId` 但内容冲突（换了邀约或换了决定类型）→ 409 `IDEMPOTENCY_CONFLICT`。
- 所有写操作在事务内先对 `allocation` 主行加**悲观写锁**（`SELECT … FOR UPDATE`），
  同一流程的并发决定被数据库串行化；`offer_event.event_id` 的唯一约束再做一层兜底。
  因此“接受 vs 过期”“多个接受并发”最多一个结果生效——不会跳号、双重分配或产生部分事件。

### 6. 查询与错误结构

- `GET /api/allocations/{allocationNo}` 返回：候选快照、当前有效邀约（无则为 `null`）、
  每次决定事件（按发生顺序）、最终受者（终态 ALLOCATED 时）。
- 所有错误使用统一结构：

```json
{
  "timestamp": "2026-09-28T08:00:00Z",
  "status": 409,
  "error": "OFFER_EXPIRED",
  "message": "邀约已超过过期时间 …，不能接受",
  "path": "/api/allocations/AL…/offers/OF…/accept",
  "fieldErrors": null
}
```

### 数据库唯一约束一览

- `uk_donor_external_id(donor.external_id)`
- `uk_recipient_external_id(recipient.external_id)`
- `uk_allocation_donor(allocation.donor_id)`、`uk_allocation_no(allocation.allocation_no)`
- `uk_entry_allocation_position(allocation_entry.allocation_id, position)`
- `uk_offer_allocation_entry(offer.allocation_id, entry_id)`、`uk_offer_no(offer.offer_no)`
- `uk_offer_event_event_id(offer_event.event_id)`

## HTTP 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/donors` | 登记供体器官（201） |
| POST | `/api/recipients` | 登记候选受者（201） |
| POST | `/api/allocations` | 启动分配并返回快照（201；无候选人 422） |
| GET  | `/api/allocations/{allocationNo}` | 分配详情 |
| POST | `/api/allocations/{allocationNo}/offers` | 向当前候选人发出限时邀约（201） |
| POST | `/api/allocations/{allocationNo}/offers/{offerNo}/accept` | 接受（body 含 `eventId`） |
| POST | `/api/allocations/{allocationNo}/offers/{offerNo}/decline` | 拒绝（body 含 `eventId`） |
| POST | `/api/allocations/{allocationNo}/offers/{offerNo}/expire` | 标记过期（body 含 `eventId`） |

### 典型流程

```bash
# 1. 登记供体与候选人
curl -X POST localhost:8080/api/donors -H 'Content-Type: application/json' -d '{
  "externalId":"D1","donorName":"供体甲","organType":"KIDNEY","bloodType":"A"}'

curl -X POST localhost:8080/api/recipients -H 'Content-Type: application/json' -d '{
  "externalId":"R1","recipientName":"受者乙","organType":"KIDNEY","bloodType":"A",
  "urgency":"URGENT","waitlistedAt":"2026-09-01T00:00:00Z","active":true,"center":"北京中心"}'

# 2. 启动分配（每份邀约 60 秒有效）
curl -X POST localhost:8080/api/allocations -H 'Content-Type: application/json' -d '{
  "donorExternalId":"D1","offerTtlSeconds":60}'

# 3. 发出第一份邀约 -> 返回 offerNo 与 expiresAt
curl -X POST localhost:8080/api/allocations/{allocationNo}/offers

# 4. 决定（接受 / 拒绝 / 过期），均携带幂等 eventId
curl -X POST localhost:8080/api/allocations/{allocationNo}/offers/{offerNo}/accept \
  -H 'Content-Type: application/json' -d '{"eventId":"evt-0001"}'

# 5. 查询详情
curl localhost:8080/api/allocations/{allocationNo}
```

## 测试

- `RegistrationServiceTest`：字段持久化、输入校验、外部编号唯一冲突。
- `AllocationServiceTest`：血型/器官/激活过滤与三级排序、空候选失败、快照不可变、
  邀约顺序与 TTL、接受/拒绝/过期流转、停用候选人拒绝接受、终态穷尽、
  幂等重放与 409 冲突、**8 线程并发接受**与**接受/过期竞态**（验证最多一个结果生效）、
  详情查询。
- `AllocationApiTest`：MockMvc 端到端覆盖统一错误结构、校验、409/422/404 与完整链路。
