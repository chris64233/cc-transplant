# cc-transplant

器官分配协调服务：登记供体器官与候选受者，器官进入分配流程后按候选顺序逐一发出限时邀约，
直到唯一候选人接受或候选人耗尽。

## 开发环境

- JDK 21
- Spring Boot 4.1.1（Spring Framework 7 / Jackson 3）
- Maven Wrapper 3.9.9
- H2 数据库（本地文件模式，测试内存模式）

## 本地运行

启动服务：

    ./mvnw spring-boot:run

运行测试：

    ./mvnw clean test

## 主要业务规则

### 1. 登记

- 供体字段：外部编号、器官类型、血型、所属中心。
- 候选人字段：外部编号、所需器官类型、血型、紧急等级（1–5）、进入等待名单时间、
  是否激活、所属中心。
- 外部编号（供体、候选人各自范围内）**全局唯一**，由数据库唯一约束保证；重复登记返回 409。
- 所有必填字段在接口层做完整性校验，非法枚举、等级越界、缺字段返回 400。

### 2. 启动分配与候选快照

- 一个供体器官**只能启动一次**分配（数据库唯一约束兜底）。
- 过滤条件（必须同时满足）：
  - 器官类型与供体相同；
  - 血型相容（ABO：O→所有，A→A/AB，B→B/AB，AB→AB）；
  - 候选人当前处于激活状态。
- 排序规则（从高到低）：
  1. 紧急等级从高到低；
  2. 进入等待名单时间从早到晚（等待更久者优先）；
  3. 候选外部编号升序，保证稳定、确定的顺序。
- 排序结果固化为**不可变候选快照**，之后新登记的候选人、候选人属性变化都不影响本次顺序。
- **没有合格候选人时明确失败（409），不会创建空分配流程。**
- 启动成功即向快照首位候选人发出第一条限时邀约。

### 3. 限时邀约流转

- 系统一次只允许一个候选人持有有效邀约；只有在该邀约被拒绝或过期后，才按快照顺序
  向下一位发出邀约，邀约序号连续递增，不跳号。
- 邀约含明确的签发时间与过期时间；到达过期时间后邀约失效。
- **接受**只允许发生在邀约有效期内，且候选人仍为激活状态；接受成功后：
  - 器官进入 `ALLOCATED` 终态，记录最终受者；
  - 当前邀约关闭，不再发出新邀约；
  - 其余候选人不能再接受或作出任何决定。
- 等待期间被停用的候选人在轮到时标记为 `DEACTIVATED` 并跳过，不占用邀约序号。
- 所有候选人均拒绝/过期后流程进入 `EXHAUSTED` 终态。
- 服务内置定时扫描器（默认 10 秒），自动将到期邀约按过期处理并顺位推进。

### 4. 幂等与并发

- 接受、拒绝、过期请求都必须携带调用方提供的 `eventId` 幂等标识：
  - 相同 `eventId` + 相同内容重放：返回首次处理的原结果（`replayed: true`），不重复生效；
  - 相同 `eventId` 但决定类型或目标邀约不同：返回 **409 冲突**；
  - 系统自动过期使用确定性事件号 `system-expire-{offerNo}`，多次扫描幂等。
- 每次决定都在单个数据库事务内完成，并先以**悲观写锁锁定分配行**，再锁定邀约行/候选人行，
  同一分配的所有决定完全串行。接受与过期并发、两个接受并发时，数据库保证**最多一个结果生效**：
  胜出者正常返回，其余返回 409，不会出现双重分配、跳号或部分写入的事件。
- 幂等事件表对 `event_id` 建有数据库唯一约束，作为最终防线。

### 5. 查询与错误

- 分配详情返回：候选快照（含各候选人当前状态）、当前有效邀约、全部邀约、全部决定事件、
  最终受者位置与编号。
- 统一错误结构：

  ```json
  {
    "code": "EVENT_CONFLICT",
    "message": "……",
    "path": "/api/allocations/offers/OF.../accept",
    "timestamp": "2026-09-28T08:00:00Z",
    "details": []
  }
  ```

  错误码：`VALIDATION_ERROR`(400)、`NOT_FOUND`(404)、`DUPLICATE_EXTERNAL_REF`(409)、
  `EVENT_CONFLICT`(409)、`RULE_VIOLATION`(409)、`INTERNAL_ERROR`(500)。

### 数据库唯一约束

| 表 | 唯一约束 |
|---|---|
| donor | external_ref |
| candidate | external_ref |
| allocation | allocation_no；donor_id（一供体一流程） |
| allocation_candidate | (allocation_id, position)；(allocation_id, candidate_id) |
| offer | offer_no；(allocation_id, position)（每人至多一条邀约） |
| offer_event | event_id（幂等标识） |

## HTTP 接口

### 登记供体

`POST /api/registrations/donors`

```json
{"externalRef":"D-1","organType":"KIDNEY","bloodType":"O","centerCode":"CN-BJ"}
```

### 登记候选人

`POST /api/registrations/candidates`

```json
{
  "externalRef": "C-1", "organType": "KIDNEY", "bloodType": "A",
  "urgency": 5, "waitlistedAt": "2026-01-01T00:00:00Z",
  "active": true, "centerCode": "CN-BJ"
}
```

### 启动分配

`POST /api/allocations`

```json
{"donorExternalRef":"D-1","offerTtlSeconds":300}
```

返回分配单与第一条邀约（含 `expiresAt`）。

### 作出决定

- 接受：`POST /api/allocations/offers/{offerNo}/accept`
- 拒绝：`POST /api/allocations/offers/{offerNo}/decline`
- 过期：`POST /api/allocations/offers/{offerNo}/expire`

```json
{"offerNo":"OF...","eventId":"调用方幂等标识"}
```

拒绝/过期响应中的 `nextOfferNo` 为自动顺位发出的下一条邀约；为 `null` 表示流程已耗尽。

### 查询分配详情

`GET /api/allocations/{allocationNo}`

返回 `snapshot`（候选快照）、`currentOffer`（当前邀约）、`offers`（每次邀约）、
`events`（每次决定）、`recipientRef`（最终受者）。
