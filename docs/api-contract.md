# 多聊天室群聊 REST API 与 WebSocket 协议契约

> 版本：1.0  
> 依赖：T00-01《多聊天室群聊业务规则与状态机》  
> 状态：可供前端、后端并行实现。若本文件与 T00-01 的聊天室生命周期、消息顺序、逻辑删除、审核或 WebSocket 会话规则冲突，以 T00-01 为准。

## 1. 适用范围与约定

- REST 根路径为 `/api/v1`，请求和响应均使用 `application/json; charset=utf-8`。
- WebSocket 地址为 `wss://{host}/ws/v1/chat`，文本帧编码为 UTF-8 JSON。
- 所有字段使用 lower camel case；枚举值使用全大写 snake case；时间字段以 `At` 结尾；状态字段以实体名加 `Status` 结尾，禁止用语义不明的通用 `status`。
- 服务端从 JWT 解析身份和角色，并在每次写入、订阅、审核和管理读取时重新查询成员关系、房间状态及管理员授权。客户端传入的 `userId`、角色或权限范围不作为授权依据。
- 除 `GET /health`、注册、登录和刷新令牌外，REST 接口均要求认证。接口表中的 `登录用户` 指任意已认证用户（包括管理员）。

### 1.1 标识符、序列、时间与状态

| 名称 | JSON 类型与格式 | 规则 |
| --- | --- | --- |
| `requestId` | UUID v4 字符串 | REST 写请求由 `X-Request-Id` 携带；WebSocket 客户端命令在根对象携带。用于关联日志和写请求幂等。 |
| `messageId` | UUID 字符串 | 服务端生成；全局唯一。客户端以它去重展示和处理重复投递。 |
| `roomId`、`userId` | 十进制整数的字符串 | 业务主键在 JSON 中一律用字符串，避免 JavaScript 大整数精度丢失，例如 `"1001"`。 |
| `roomSeq`、`notificationSeq` | 非负十进制整数的字符串 | `roomSeq` 只用于 `CHAT`、`ADMIN_MESSAGE`；`notificationSeq` 只用于 `SYSTEM_NOTIFICATION`。两者均按房间独立递增且不可复用。 |
| `createdAt`、`updatedAt`、`reviewedAt`、`publishedAt`、`reviewDeadlineAt`、`deletedAt`、`occurredAt` | RFC 3339 UTC 字符串，毫秒精度 | 例如 `2026-09-06T10:30:00.123Z`。客户端提交时间不作为业务排序依据。 |
| `roomStatus` | `ACTIVE`、`PAUSED`、`CLOSED`、`DELETED` | `DELETED` 是逻辑删除终态；常规查询不返回。 |
| `memberStatus` | `PENDING`、`ACTIVE`、`REJECTED`、`EXITED` | 仅 `ACTIVE` 是有效成员。 |
| `messageStatus` | `PENDING_REVIEW`、`APPROVED`、`PUBLISHED`、`REJECTED`、`TIMEOUT`、`CANCELLED_BY_ROOM_DELETION` | 仅 `PUBLISHED` 对公共历史和订阅可见。 |
| `messageType` | `CHAT`、`ADMIN_MESSAGE`、`SYSTEM_NOTIFICATION` | 紧急通知不占用 `roomSeq`，其 `roomSeq` 必为 `null`。 |

`roomSeq` 的首条普通消息为 `"1"`；订阅中 `lastMessageSeq`、`lastNotificationSeq` 未提供时按 `"0"` 处理。所有序列比较必须按整数值而非字符串字典序进行。

### 1.1.1 系统审计查询

`GET /api/v1/admin/audits` 仅允许 `SYSTEM_ADMIN` 调用；普通用户和 `ROOM_ADMIN` 一律返回 `403`，即使其对某个房间拥有管理授权。可选筛选参数为 `actorId`、`roomId`、`messageId`、`action`、`from`、`to`（RFC 3339 UTC 时间）。`action` 仅接受审计动作枚举。结果以 `createdAt DESC, id DESC` 排序，并使用 `page`（从 1 起）和 `size`（1..100，默认 50）分页。

每条审计记录包含操作者、资源和关联的房间/消息 ID、`beforeState`、`afterState`、`detail` 与 `createdAt`。状态变更和它的审计插入属于同一数据库事务。

### 1.2 认证与写入幂等

REST 使用 `Authorization: Bearer <accessToken>`。登录、刷新令牌成功后返回短期 `accessToken` 与刷新用 `refreshToken`；刷新令牌仅能用于刷新和注销。

所有会改变状态的 REST 请求必须携带合法 `X-Request-Id`（UUID v4）。同一认证主体、同一路由、同一 `requestId` 的重试必须返回初次已提交的 HTTP 状态码和响应体；若请求体哈希不同，返回 `409 IDEMPOTENCY_KEY_REUSED`。WebSocket 的 `CHAT_SUBMIT` 以同一规则幂等：重发相同 `requestId` 不得创建第二条消息。只读请求未携带时服务端生成 `requestId`，并通过响应头和响应体返回。

### 1.3 通用 REST 响应与分页

成功响应：

```json
{
  "requestId": "d69a69d5-3586-4b35-bdf8-932b4640f401",
  "data": {}
}
```

失败响应：

```json
{
  "requestId": "d69a69d5-3586-4b35-bdf8-932b4640f401",
  "error": {
    "code": "ROOM_PAUSED",
    "message": "The room does not accept user chat while paused.",
    "details": [
      { "field": "roomId", "reason": "ROOM_PAUSED" }
    ]
  }
}
```

所有列表使用游标分页，不使用 offset/page。通用查询参数如下：

| 参数 | 必填 | 校验 | 说明 |
| --- | --- | --- | --- |
| `limit` | 否 | 整数，`1..100`，默认 `50` | 返回条数。 |
| `cursor` | 否 | 仅接受服务端此前返回的同一资源、同一筛选条件游标 | 不透明 base64url 字符串；篡改、过期或筛选条件变更时返回 `INVALID_CURSOR`。 |

分页响应的 `data` 固定为：

```json
{
  "items": [],
  "page": {
    "nextCursor": "eyJ2IjoxfQ",
    "hasMore": true
  }
}
```

最后一页的 `nextCursor` 为 `null`。默认排序及可用筛选字段由各接口明确指定。

### 1.4 统一错误码

`message` 是面向用户的稳定、无敏感信息文案；前端以 `code` 处理逻辑，不能解析 `message`。未在接口表重复列出的通用失败同样适用。

| HTTP | 错误码 | 含义与前端处理 |
| --- | --- | --- |
| 400 | `VALIDATION_FAILED` | 参数格式、枚举、长度或字段组合不合法；读取 `details` 标记表单项。 |
| 400 | `INVALID_CURSOR` | 游标无效、过期或与筛选条件不匹配；从第一页重新加载。 |
| 400 | `INVALID_LAST_SEQUENCE` | 重连游标不是非负整数，或大于服务器已知的当前序列；客户端不得用该游标继续补偿。 |
| 401 | `INVALID_CREDENTIALS` | 用户名或密码不正确；不得说明究竟是哪一项错误。 |
| 401 | `UNAUTHENTICATED`、`TOKEN_EXPIRED` | 缺少、无效或过期令牌；刷新或重新登录。 |
| 403 | `FORBIDDEN`、`ROOM_ACCESS_DENIED` | 已认证但缺少系统角色、房间授权或有效成员资格。 |
| 404 | `ROOM_NOT_FOUND`、`MESSAGE_NOT_FOUND`、`MEMBER_NOT_FOUND`、`AUDIT_NOT_FOUND` | 对调用者不可见的资源一律按不存在处理，避免泄露。 |
| 409 | `IDEMPOTENCY_KEY_REUSED` | 相同 `requestId` 的请求体不同。 |
| 409 | `ROOM_FULL`、`MEMBERSHIP_ALREADY_ACTIVE`、`JOIN_REQUEST_ALREADY_PENDING` | 资源状态与请求冲突。 |
| 409 | `REVIEW_ALREADY_PROCESSED`、`MEMBER_REQUEST_ALREADY_PROCESSED` | 审核/审批被其他操作者或超时任务先处理；刷新数据。 |
| 409 | `ROOM_STATE_CONFLICT` | 非法房间状态迁移。 |
| 409 | `USERNAME_ALREADY_EXISTS` | 注册用户名已被占用。 |
| 422 | `ROOM_PAUSED`、`ROOM_CLOSED`、`ROOM_DELETED`、`ADMIN_ROLE_REQUIRED` | 目标房间当前不能执行该操作，或授权目标不具有 `ROOM_ADMIN` 角色。 |
| 422 | `MESSAGE_RATE_LIMITED`、`SENSITIVE_CONTENT_REJECTED`、`MESSAGE_NOT_SENDABLE` | 消息未持久化、未分配 `roomSeq`；敏感词错误不暴露命中内容。 |
| 429 | `TOO_MANY_REQUESTS` | HTTP 接口限流；根据 `Retry-After` 重试。 |
| 500 | `INTERNAL_ERROR` | 未预期错误，携带 `requestId` 联系支持。 |
| 503 | `SERVICE_UNAVAILABLE` | 依赖不可用或系统维护，可退避重试。 |

## 2. 资源表示

### 2.1 Room、成员与消息

```json
{
  "roomId": "1001",
  "name": "A 股直播间",
  "description": "交易日讨论",
  "maxMembers": 500,
  "activeMemberCount": 120,
  "joinMode": "APPROVAL",
  "roomStatus": "ACTIVE",
  "createdByUserId": "2001",
  "createdAt": "2026-09-06T10:30:00.000Z",
  "updatedAt": "2026-09-06T10:30:00.000Z"
}
```

`joinMode` 仅为 `OPEN` 或 `APPROVAL`。常规房间详情和列表不包含已删除房间。

```json
{
  "membershipId": "9001",
  "roomId": "1001",
  "userId": "2002",
  "displayName": "小陈",
  "memberStatus": "ACTIVE",
  "joinedAt": "2026-09-06T10:31:00.000Z",
  "createdAt": "2026-09-06T10:31:00.000Z"
}
```

```json
{
  "messageId": "dd01a1d0-a7e8-4af7-9da4-37b3b28f3fb6",
  "roomId": "1001",
  "roomSeq": "42",
  "notificationSeq": null,
  "senderId": "2002",
  "senderDisplayName": "小陈",
  "messageType": "CHAT",
  "content": "今天市场怎么看？",
  "messageStatus": "PUBLISHED",
  "createdAt": "2026-09-06T10:32:00.000Z",
  "reviewedAt": "2026-09-06T10:32:05.000Z",
  "publishedAt": "2026-09-06T10:32:05.010Z"
}
```

审核和个人消息返回的消息可额外含 `reviewDeadlineAt`。普通成员公共历史绝不返回他人非 `PUBLISHED` 的消息。`SYSTEM_NOTIFICATION` 的 `roomSeq` 为 `null`，`notificationSeq` 为非空；其 `messageStatus` 恒为 `PUBLISHED`。

### 2.2 文本和输入边界

所有 `content` 字段必须是有效 Unicode 标量值组成的字符串，去掉首尾空白后不可为空，最多 320 个 Unicode code point。不接受图片、HTML、附件或客户端指定的消息类型、发送者、状态、序列及时间。`name` 为去空白后 1..64 code point，`description` 最多 500 code point，`maxMembers` 为 `1..100000` 整数。

## 3. REST 接口

接口表中“失败”列是在通用错误码以外、前端应重点处理的错误。所有响应均使用第 1.3 节信封；表中只展示 `data` 内容。

### 3.1 认证与当前用户

| 方法与路径 | 权限 | 请求、校验 | 成功 `data` | 失败 |
| --- | --- | --- | --- | --- |
| `POST /auth/register` | 匿名 | `{username, password, displayName}`；用户名 3..32、`[A-Za-z0-9_-]`，密码 8..72，显示名 1..64 | `{user, accessToken, refreshToken, expiresAt}` | `USERNAME_ALREADY_EXISTS`（409）、`VALIDATION_FAILED` |
| `POST /auth/login` | 匿名 | `{username, password}`；均必填 | `{user, accessToken, refreshToken, expiresAt}` | `INVALID_CREDENTIALS`（401）、`VALIDATION_FAILED` |
| `POST /auth/refresh` | 刷新令牌 | `{refreshToken}` | `{accessToken, refreshToken, expiresAt}`；刷新令牌轮换 | `UNAUTHENTICATED`、`TOKEN_EXPIRED` |
| `POST /auth/logout` | 登录用户 | `{refreshToken}`；令牌必须属于当前用户 | `{loggedOut:true}` | `UNAUTHENTICATED` |
| `GET /users/me` | 登录用户 | 无 | `{userId, username, displayName, roles, createdAt}`；`roles` 是 `USER`、`ROOM_ADMIN`、`SYSTEM_ADMIN` 的数组 | `UNAUTHENTICATED` |

注册和登录无需由客户端提供 `requestId`，服务端仍生成并返回；其余状态写入遵从第 1.2 节。

### 3.2 房间、加入和成员

| 方法与路径 | 权限 | 参数、校验与排序 | 成功 `data` | 失败 |
| --- | --- | --- | --- | --- |
| `GET /rooms` | 登录用户 | `name`（可选，1..64，前缀匹配）、`roomStatus`（可选，默认 `ACTIVE`）、`joinMode`（可选）、分页；按 `createdAt DESC, roomId DESC` | 分页 `Room[]` | `VALIDATION_FAILED`、`INVALID_CURSOR` |
| `GET /rooms/{roomId}` | 登录用户 | `roomId` 十进制字符串 | `Room` | `ROOM_NOT_FOUND`、`ROOM_DELETED` |
| `POST /rooms/{roomId}/memberships` | 登录用户 | 空对象；房间 `OPEN` 时创建 `ACTIVE`，`APPROVAL` 时创建 `PENDING` | `{membership, joinResult:"JOINED"|"PENDING_APPROVAL"}` | `ROOM_FULL`、`MEMBERSHIP_ALREADY_ACTIVE`、`JOIN_REQUEST_ALREADY_PENDING`、`ROOM_PAUSED`、`ROOM_CLOSED`、`ROOM_DELETED` |
| `POST /rooms/{roomId}/leave` | 有效成员 | 空对象；只允许自身退出，服务端撤销本连接对该房间的订阅 | `{membershipId, memberStatus:"EXITED", leftAt}` | `ROOM_ACCESS_DENIED`、`ROOM_DELETED` |
| `GET /users/me/rooms` | 登录用户 | `memberStatus` 可选、分页；按成员记录 `createdAt DESC` | 分页 `Membership[]`，可内嵌未删除 `room` 摘要 | `INVALID_CURSOR` |
| `GET /rooms/{roomId}/members` | 有效成员或授权管理员 | `memberStatus` 可选、分页；成员仅可请求 `ACTIVE`，管理员可请求全部；按 `joinedAt DESC` | 分页 `Membership[]`。普通成员仅见 `userId`、`displayName`、`memberStatus=ACTIVE`、`joinedAt` | `ROOM_ACCESS_DENIED`、`ROOM_DELETED` |

`POST /rooms/{roomId}/memberships` 和 `leave` 均记录审计。批准时而非申请时占用 `maxMembers`；同一用户再次申请必须创建新的成员记录。

### 3.3 消息历史和个人状态

| 方法与路径 | 权限 | 参数、校验与排序 | 成功 `data` | 失败 |
| --- | --- | --- | --- | --- |
| `GET /rooms/{roomId}/messages` | 有效成员或授权管理员 | 分页；仅返回普通消息，按 `roomSeq DESC`，游标为该排序的续页令牌 | 分页 `Message[]`，均为 `messageStatus=PUBLISHED` | `ROOM_ACCESS_DENIED`、`ROOM_DELETED` |
| `GET /rooms/{roomId}/notifications` | 有效成员或授权管理员 | 分页；按 `notificationSeq DESC` | 分页 `Message[]`，均为 `messageType=SYSTEM_NOTIFICATION`、`PUBLISHED` | `ROOM_ACCESS_DENIED`、`ROOM_DELETED` |
| `GET /users/me/messages` | 登录用户 | `roomId`、`messageStatus`、`messageType`、`createdFrom`、`createdTo` 可选；时间为 RFC 3339，`from <= to`；按 `createdAt DESC, messageId DESC` | 分页本人 `Message[]`；已删除房间消息含 `roomDeleted:true` | `VALIDATION_FAILED`、`INVALID_CURSOR` |
| `GET /users/me/messages/{messageId}` | 登录用户 | `messageId` UUID | 本人 `Message`，或管理员本人所发消息 | `MESSAGE_NOT_FOUND` |

公共消息历史不承担实时补偿；WebSocket 补偿使用第 4.5 节的双游标。客户端无法从已过期的补偿缺口恢复的内容，必须向用户显示“超出留存期”。

### 3.4 房间运营和成员审批

| 方法与路径 | 权限 | 请求、校验 | 成功 `data` | 失败 |
| --- | --- | --- | --- | --- |
| `POST /admin/rooms` | `SYSTEM_ADMIN` | `{name, description?, maxMembers, joinMode}`；采用第 2.2 节校验 | `Room`，初始 `roomStatus=ACTIVE` | `VALIDATION_FAILED` |
| `PATCH /admin/rooms/{roomId}` | 授权 `ROOM_ADMIN` 或 `SYSTEM_ADMIN` | 可改 `name`、`description`、`maxMembers`、`joinMode`、`roomStatus`；至少一个字段。迁移仅允许 `ACTIVE→PAUSED/CLOSED`、`PAUSED→ACTIVE/CLOSED`、`CLOSED` 无回转 | 更新后的 `Room` | `ROOM_ACCESS_DENIED`、`ROOM_STATE_CONFLICT`、`ROOM_DELETED` |
| `DELETE /admin/rooms/{roomId}` | `SYSTEM_ADMIN` | 空对象；执行逻辑删除并原子取消未发布普通消息 | `{roomId, roomStatus:"DELETED", deletedAt}` | `ROOM_NOT_FOUND`、`ROOM_DELETED` |
| `GET /admin/join-requests` | 授权 `ROOM_ADMIN` 或 `SYSTEM_ADMIN` | `roomId`、`memberStatus`（默认 `PENDING`）、`userId`、分页；房间管理员仅能看到已授权房间；按 `createdAt ASC` | 分页 `Membership[]` | `ROOM_ACCESS_DENIED`、`INVALID_CURSOR` |
| `POST /admin/join-requests/{membershipId}/approve` | 授权 `ROOM_ADMIN` 或 `SYSTEM_ADMIN` | 空对象；目标必须为 `PENDING` | `{membership}`，其 `memberStatus=ACTIVE` | `MEMBER_REQUEST_ALREADY_PROCESSED`、`ROOM_FULL`、`ROOM_PAUSED`、`ROOM_CLOSED`、`ROOM_DELETED` |
| `POST /admin/join-requests/{membershipId}/reject` | 授权 `ROOM_ADMIN` 或 `SYSTEM_ADMIN` | 空对象；目标必须为 `PENDING` | `{membership}`，其 `memberStatus=REJECTED` | `MEMBER_REQUEST_ALREADY_PROCESSED`、`ROOM_ACCESS_DENIED` |

房间配置变更、删除和成员审批均写不可变审计。删除后服务端立即取消所有订阅；常规管理员不能通过以上接口恢复或读取已删除房间。

### 3.5 审核

| 方法与路径 | 权限 | 参数、校验与排序 | 成功 `data` | 失败 |
| --- | --- | --- | --- | --- |
| `GET /admin/review-messages` | 授权 `ROOM_ADMIN` 或 `SYSTEM_ADMIN` | `roomId`、`senderId`、`messageStatus`、`createdFrom`、`createdTo`、分页；`messageStatus` 默认 `PENDING_REVIEW`；按 `createdAt ASC, roomSeq ASC` | 分页 `Message[]`，含所有审核可见状态 | `ROOM_ACCESS_DENIED`、`VALIDATION_FAILED` |
| `POST /admin/review-messages/{messageId}/approve` | 授权 `ROOM_ADMIN` 或 `SYSTEM_ADMIN` | 空对象；仅 `PENDING_REVIEW` 可操作 | `{message}`；可能是 `APPROVED`（等待前序）或已成为 `PUBLISHED` | `REVIEW_ALREADY_PROCESSED`、`ROOM_ACCESS_DENIED` |
| `POST /admin/review-messages/{messageId}/reject` | 授权 `ROOM_ADMIN` 或 `SYSTEM_ADMIN` | 空对象；仅 `PENDING_REVIEW` 可操作 | `{message}`，`messageStatus=REJECTED` | `REVIEW_ALREADY_PROCESSED`、`ROOM_ACCESS_DENIED` |
| `POST /admin/review-messages/batch` | 授权 `ROOM_ADMIN` 或 `SYSTEM_ADMIN` | `{messageIds, action}`；`messageIds` 去重后 1..100 个 UUID，`action` 为 `APPROVE` 或 `REJECT` | `{results:[{messageId, reviewResult:"APPROVED"|"REJECTED"|"ALREADY_PROCESSED"|"NO_PERMISSION"|"NOT_FOUND", messageStatus?, errorCode?}]}` | 请求整体仅在格式错误时失败；逐条结果允许部分成功 |

单条审核和批量中的每条消息都独立进行授权与条件更新。`APPROVED` 不代表立即可见；只有轮到 `nextPublishSeq` 时才转为 `PUBLISHED`。审核状态变更、审计记录和发布游标推进在同一事务内完成。

### 3.6 广播与紧急通知

`ROOM_ADMIN` 只能面向已授权房间；`SYSTEM_ADMIN` 可以面向任意未删除房间。普通管理员消息和紧急通知允许发往 `ACTIVE`、`PAUSED`、`CLOSED` 房间，已删除房间一律拒绝。

| 方法与路径 | 权限 | 请求、校验 | 成功 `data` | 失败 |
| --- | --- | --- | --- | --- |
| `POST /admin/broadcasts` | 授权 `ROOM_ADMIN` 或 `SYSTEM_ADMIN` | `{roomIds, content}`；去重后 `roomIds` 为 1..100 个十进制 ID，`content` 采用第 2.2 节校验 | `BroadcastResult` | 仅全局参数错误返回 400；逐房间业务结果不使整个请求失败 |
| `POST /admin/emergency-notifications` | 授权 `ROOM_ADMIN` 或 `SYSTEM_ADMIN` | `{roomIds, content}`；与广播相同 | `EmergencyNotificationResult` | 同上 |

广播示例：

```json
{
  "requestId": "c471839b-030d-4b34-a817-2ebbd39a38d9",
  "data": {
    "broadcastId": "f9bb9f63-27eb-44ea-a87d-a3dc00efb189",
    "results": [
      {
        "roomId": "1001",
        "deliveryStatus": "SUCCESS",
        "messageId": "dd01a1d0-a7e8-4af7-9da4-37b3b28f3fb6",
        "roomSeq": "42",
        "messageStatus": "APPROVED"
      },
      {
        "roomId": "1002",
        "deliveryStatus": "NO_PERMISSION",
        "errorCode": "ROOM_ACCESS_DENIED"
      },
      {
        "roomId": "1003",
        "deliveryStatus": "ROOM_NOT_SENDABLE",
        "errorCode": "ROOM_DELETED"
      },
      {
        "roomId": "1004",
        "deliveryStatus": "PENDING_COMPENSATION",
        "messageId": "e3a53bac-0d9d-4c53-8d91-1df36de2b6fd",
        "roomSeq": "88",
        "messageStatus": "PUBLISHED"
      }
    ]
  }
}
```

逐房间 `deliveryStatus` 的契约如下：

| 值 | 已持久化 | 含义 | 必填补充字段 |
| --- | --- | --- | --- |
| `SUCCESS` | 是 | 授权、房间校验和持久化成功；普通广播可因前序审核而暂为 `APPROVED`。 | `messageId`、`roomSeq`、`messageStatus` |
| `NO_PERMISSION` | 否 | 操作者对该房间没有授权。 | `errorCode=ROOM_ACCESS_DENIED` |
| `ROOM_NOT_SENDABLE` | 否 | 房间不存在、已删除或状态不允许（当前管理员消息仅已删除会出现）。 | `errorCode`，例如 `ROOM_DELETED` |
| `PENDING_COMPENSATION` | 是 | 消息已发布并持久化，但本次实时投递/入队未完全确认；服务端必须保留补偿任务，不能回滚消息。 | `messageId`、对应序列、`messageStatus=PUBLISHED` |

紧急通知结果结构相同，根字段名为 `notificationId`；每项的 `roomSeq` 为 `null`，必须带 `notificationSeq`，且持久化成功的 `messageStatus` 恒为 `PUBLISHED`。两类操作均记入 `ADMIN_BROADCAST` 或 `EMERGENCY_PUBLISH` 审计，且以每个成功房间的独立 `messageId` 记录。

### 3.7 管理员房间授权、审计与取证

| 方法与路径 | 权限 | 请求、校验 | 成功 `data` | 失败 |
| --- | --- | --- | --- | --- |
| `GET /admin/rooms/{roomId}/authorizations` | `SYSTEM_ADMIN` | 分页；按 `createdAt DESC` | 分页 `{authorizationId, roomId, adminUserId, grantedByUserId, createdAt}` | `ROOM_NOT_FOUND` |
| `PUT /admin/rooms/{roomId}/authorizations/{adminUserId}` | `SYSTEM_ADMIN` | 空对象；目标用户必须有 `ROOM_ADMIN` 角色，幂等授予 | `{roomId, adminUserId, grantedAt}` | `ADMIN_ROLE_REQUIRED`（422）、`ROOM_DELETED` |
| `DELETE /admin/rooms/{roomId}/authorizations/{adminUserId}` | `SYSTEM_ADMIN` | 空对象；幂等撤销 | `{roomId, adminUserId, revoked:true, revokedAt}` | `ROOM_NOT_FOUND` |
| `GET /admin/audit-logs` | `SYSTEM_ADMIN` | `actorUserId`、`roomId`、`messageId`、`actionType`、`createdFrom`、`createdTo`、`includeDeleted`（默认 `false`）、分页；按 `createdAt DESC, auditId DESC` | 分页完整 `AuditLog[]` | `VALIDATION_FAILED`、`INVALID_CURSOR` |
| `GET /admin/audits` | `SYSTEM_ADMIN` | 可按 `actorId`、`roomId`、`messageId`、`action`、`from`、`to` 和分页筛选 | 分页 `AuditLog[]`；包含结构化状态快照和详情。普通用户与 `ROOM_ADMIN` 均不可查询 | `FORBIDDEN`、`VALIDATION_FAILED` |
| `GET /admin/forensics/rooms/{roomId}` | `SYSTEM_ADMIN` | 必须带 `includeDeleted=true`；分页参数只用于嵌套成员/消息游标，详见响应 | `{room, membersPage, messagesPage, auditPage}`；只读 | `VALIDATION_FAILED`、`ROOM_NOT_FOUND` |

`AuditLog` 为 `{auditId, actorUserId, actionType, resourceType, resourceId, roomId?, messageId?, beforeStatus?, afterStatus?, requestId, createdAt, detail?}`。审计日志不可由任何业务接口修改或删除；读取已删除房间的取证接口必须记录 `FORENSIC_READ` 审计。`includeDeleted=true` 未显式提供时不得返回已删除房间数据。

### 3.8 运行状态与监控

| 方法与路径 | 权限 | 参数与校验 | 成功 `data` | 失败 |
| --- | --- | --- | --- | --- |
| `GET /health` | 匿名 | 无 | `{serviceStatus:"UP"|"DEGRADED"|"DOWN", checkedAt}` | `503 SERVICE_UNAVAILABLE`（`DOWN`） |
| `GET /admin/system/metrics` | `SYSTEM_ADMIN` | `window` 可选，`CURRENT` 或 `TODAY`，默认 `CURRENT` | `{observedAt, webSocketConnectionCount, pendingReviewCount, reviewTimeoutCountToday, messageCountToday, averageReviewLatencyMsToday, pushFailureCountToday, httpErrorRate, databasePool}` | `FORBIDDEN` |

监控接口只提供聚合值，不泄露用户内容、令牌或 IP。`databasePool` 为 `{active, idle, max}`；每个数值为 JSON number。

## 4. WebSocket 协议

### 4.1 握手、会话和关闭

客户端建立：

```text
wss://{host}/ws/v1/chat
Sec-WebSocket-Protocol: chat.v1, bearer.<JWT>
```

浏览器客户端必须用上述子协议传递 JWT；非浏览器客户端可使用 `Authorization: Bearer <JWT>`，但不得把令牌放入 URL 查询参数或日志。服务端选择并返回子协议 `chat.v1`，不会回显令牌。

握手认证失败时不建立 WebSocket：缺令牌/无效令牌返回 HTTP 401，账户无访问资格返回 HTTP 403。认证成功后，新连接替换该用户旧连接：旧连接先尽力收到 `ERROR`（`SESSION_REPLACED`），随后以关闭码 `4001` 关闭；旧连接不得自动重连。心跳采用标准 WebSocket Ping/Pong，客户端应响应服务端 Ping。

| 关闭码 | 含义 | 客户端动作 |
| --- | --- | --- |
| `4001` | `SESSION_REPLACED` | 停止该连接的重连循环。 |
| `4003` | 令牌撤销或认证失效 | 刷新/重新登录后新建连接。 |
| `4008` | 连续协议或限流违规 | 退避后重连；不要重放无效命令。 |

一条连接可订阅多个房间；订阅、发送和服务端状态变更都会重新校验权限。成员退出、授权撤销或房间删除时服务端立即取消对应订阅，并发送 `ERROR`。

### 4.2 帧信封

客户端命令和服务端事件均使用同一信封：

```json
{
  "type": "SUBSCRIBE_ROOM",
  "requestId": "d69a69d5-3586-4b35-bdf8-932b4640f401",
  "occurredAt": "2026-09-06T10:33:00.000Z",
  "payload": {}
}
```

客户端命令中的 `requestId` 必填且为 UUID v4，`occurredAt` 可省略且不会被服务端用于排序。对命令的直接成功响应复用请求的 `requestId`；异步服务端事件使用服务端生成的 `requestId`，如因某次提交引起，可额外提供 `causationRequestId`。未知顶层字段必须忽略，缺少必填字段或 `type` 未知时服务端发送 `ERROR`，不会关闭连接（持续违规除外）。

### 4.3 客户端命令

#### `SUBSCRIBE_ROOM`

```json
{
  "type": "SUBSCRIBE_ROOM",
  "requestId": "d69a69d5-3586-4b35-bdf8-932b4640f401",
  "payload": {
    "roomId": "1001",
    "lastMessageSeq": "41",
    "lastNotificationSeq": "8"
  }
}
```

`roomId` 必填。两个 `last*Seq` 可省略，省略按 `"0"`；提供时必须是非负十进制整数字符串。调用者必须为有效成员或授权管理员，房间不得删除。

服务端先原子登记订阅并确定两个补偿高水位，再回：

```json
{
  "type": "SUBSCRIBE_ROOM",
  "requestId": "d69a69d5-3586-4b35-bdf8-932b4640f401",
  "occurredAt": "2026-09-06T10:33:00.010Z",
  "payload": {
    "roomId": "1001",
    "subscriptionStatus": "SUBSCRIBED",
    "replay": {
      "messageReplayStatus": "COMPLETE",
      "notificationReplayStatus": "COMPLETE",
      "replayToMessageSeq": "45",
      "replayToNotificationSeq": "9"
    }
  }
}
```

`subscriptionStatus` 为 `SUBSCRIBED` 或 `SUBSCRIBED_WITH_GAP`。后者代表至少一个 `*ReplayStatus` 为 `GAP`：客户端游标早于仍可在线补偿的最小已保留序列；服务端仍订阅，并从可用的最早消息继续投递。`replay` 还会包含相应 `earliestAvailableMessageSeq` 或 `earliestAvailableNotificationSeq`。若客户端游标大于已知最新序列、格式错误、不是成员/管理员或房间已删除，订阅不建立并发送 `ERROR`。

#### `UNSUBSCRIBE_ROOM`

```json
{
  "type": "UNSUBSCRIBE_ROOM",
  "requestId": "0401bb7c-15a9-4f3d-9dd8-ca87fe32e43e",
  "payload": { "roomId": "1001" }
}
```

`roomId` 必填。服务端停止该房间后回送同类型事件：`{"payload":{"roomId":"1001","subscriptionStatus":"UNSUBSCRIBED"}}`。重复取消订阅同样返回成功，确保客户端清理幂等。

#### `CHAT_SUBMIT`

```json
{
  "type": "CHAT_SUBMIT",
  "requestId": "b5c3fe06-4af4-44c2-b84b-9cd0938cb36f",
  "payload": {
    "roomId": "1001",
    "content": "今天市场怎么看？"
  }
}
```

只允许有效成员向 `ACTIVE` 房间提交。`content` 校验遵从第 2.2 节；服务端还校验敏感词与每秒 2 条、每分钟 20 条的用户发送限流。成功持久化后服务端必须向提交用户发送 `REVIEW_STATUS`，其中回显 `causationRequestId`。提交失败不持久化、不分配 `roomSeq`，以 `ERROR` 返回。

### 4.4 服务端事件

#### `CHAT_MESSAGE`

向已订阅房间的成员和授权管理员发送普通已发布消息；重连补偿同样使用此事件。`messages` 必须按 `roomSeq` 递增投递，客户端必须按 `messageId` 去重并以 `roomSeq` 排序。

```json
{
  "type": "CHAT_MESSAGE",
  "requestId": "6db4e9a2-7a73-43cc-beb2-cda9d11844f9",
  "occurredAt": "2026-09-06T10:33:05.010Z",
  "payload": {
    "messageId": "dd01a1d0-a7e8-4af7-9da4-37b3b28f3fb6",
    "roomId": "1001",
    "roomSeq": "42",
    "notificationSeq": null,
    "senderId": "2002",
    "senderDisplayName": "小陈",
    "messageType": "CHAT",
    "content": "今天市场怎么看？",
    "messageStatus": "PUBLISHED",
    "createdAt": "2026-09-06T10:32:00.000Z",
    "publishedAt": "2026-09-06T10:33:05.010Z"
  }
}
```

#### `REVIEW_STATUS`

仅发送给消息提交者，用于接受提交及后续审核状态变化，不向房间其他人泄露待审、拒绝或超时内容。

```json
{
  "type": "REVIEW_STATUS",
  "requestId": "cf8262f9-54da-4cef-bdc5-1e5a35dd6a92",
  "causationRequestId": "b5c3fe06-4af4-44c2-b84b-9cd0938cb36f",
  "occurredAt": "2026-09-06T10:33:00.020Z",
  "payload": {
    "messageId": "dd01a1d0-a7e8-4af7-9da4-37b3b28f3fb6",
    "roomId": "1001",
    "roomSeq": "42",
    "messageStatus": "PENDING_REVIEW",
    "reviewDeadlineAt": "2026-09-06T10:33:30.000Z",
    "reviewedAt": null,
    "publishedAt": null
  }
}
```

后续状态可为 `APPROVED`、`PUBLISHED`、`REJECTED`、`TIMEOUT` 或 `CANCELLED_BY_ROOM_DELETION`。`PUBLISHED` 还会通过 `CHAT_MESSAGE` 对房间投递；提交者必须按 `messageId` 合并两个事件，不能将两者展示为两条消息。

#### `NOTIFICATION`

向订阅该房间的主体发送紧急通知；它独立置顶，不插入普通聊天列表。

```json
{
  "type": "NOTIFICATION",
  "requestId": "ec773b9a-8fa1-4842-8971-508ba0efa3ee",
  "occurredAt": "2026-09-06T10:34:00.000Z",
  "payload": {
    "messageId": "ff2e961a-59e0-4ea0-a21e-bec2f92d26d5",
    "roomId": "1001",
    "roomSeq": null,
    "notificationSeq": "9",
    "senderId": "2001",
    "messageType": "SYSTEM_NOTIFICATION",
    "content": "风险提示：请注意仓位。",
    "messageStatus": "PUBLISHED",
    "createdAt": "2026-09-06T10:34:00.000Z",
    "publishedAt": "2026-09-06T10:34:00.000Z"
  }
}
```

#### `ERROR`

命令失败或服务端撤销订阅时发送。它不代表 HTTP 错误，也不会改变客户端已处理的序列。其 `code` 使用第 1.4 节错误码，另有会话专用 `SESSION_REPLACED`。

```json
{
  "type": "ERROR",
  "requestId": "d69a69d5-3586-4b35-bdf8-932b4640f401",
  "occurredAt": "2026-09-06T10:33:00.005Z",
  "payload": {
    "code": "ROOM_ACCESS_DENIED",
    "message": "You are not allowed to subscribe to this room.",
    "commandType": "SUBSCRIBE_ROOM",
    "roomId": "1001",
    "details": []
  }
}
```

`commandType` 在命令失败时必填。服务端因成员退出、授权撤销或房间删除取消既有订阅时，使用 `commandType:"SUBSCRIBE_ROOM"` 和相应的 `ROOM_ACCESS_DENIED` 或 `ROOM_DELETED`；客户端应立刻删除本地订阅状态。

### 4.5 订阅补偿和至少一次投递

补偿按两个相互独立的游标执行：

1. `lastMessageSeq` 是客户端**已成功去重并写入本地普通消息列表**的最大 `roomSeq`。服务端只补 `roomSeq > lastMessageSeq AND messageStatus=PUBLISHED` 的普通消息，按升序发送 `CHAT_MESSAGE`。
2. `lastNotificationSeq` 是客户端已成功处理的最大紧急通知序列。服务端只补 `notificationSeq > lastNotificationSeq` 的已发布通知，按升序发送 `NOTIFICATION`。
3. 对每个通道，服务端在登记订阅时确定高水位 `replayTo*Seq`；先投递 `(last, replayTo]` 范围内的补偿数据，再投递登记期间缓冲的实时数据。这样补偿查询与实时推送之间没有丢失窗口。
4. 网络断开或推送失败可以导致重复，服务端采用至少一次投递；客户端必须用 `messageId` 去重。不得依靠 WebSocket 到达顺序跨房间排序。
5. 不补偿待审、被拒绝、超时或被删除取消的普通消息；审核状态通过在线 `REVIEW_STATUS` 或 REST 的“我的消息”查询。已超过 3 个月消息/通知留存的游标产生 `SUBSCRIBED_WITH_GAP`，而不是伪造完整补偿。

## 5. 权限汇总

| 能力 | `USER` / 有效成员 | 授权 `ROOM_ADMIN` | `SYSTEM_ADMIN` |
| --- | --- | --- | --- |
| 浏览未删除房间、查看已发布历史 | 是；历史须为有效成员 | 是；限授权房间 | 是；全部未删除房间 |
| 加入、退出、提交 `CHAT` | 是；仅本人和 `ACTIVE` 房间 | 作为普通用户时同左 | 作为普通用户时同左 |
| 审批加入、审核消息、查看成员完整记录 | 否 | 是；仅授权房间 | 是；全部房间 |
| 修改已授权房间配置、发广播/紧急通知 | 否 | 是；仅授权房间 | 是；全部未删除房间 |
| 创建/删除房间、配置房间管理员授权 | 否 | 否 | 是 |
| 全局审计、已删除房间取证、系统指标 | 否 | 否（仅可看授权房间必要审计字段） | 是 |

房间的 `PAUSED`、`CLOSED` 与 `DELETED` 影响写入和订阅的具体行为必须遵从 T00-01：前两者仍允许授权管理员发普通管理消息和紧急通知，`DELETED` 拒绝所有常规访问和写入。

## 6. 实现验收清单

- 前端可仅依据本契约生成请求、状态枚举、错误提示与 WebSocket reducer，不需要推断字段或权限。
- 后端对每个 REST 写入执行 JWT、权限、参数、`requestId` 幂等及统一错误信封；WebSocket 对每个命令返回关联的成功事件或 `ERROR`。
- 广播和紧急通知的 HTTP 200 只表示请求已被完整处理，逐房间成功与否必须读取 `results[*].deliveryStatus`。
- 客户端分别持久化每个房间的 `lastMessageSeq`、`lastNotificationSeq`，并以 `messageId` 去重；看到 `GAP` 时提示留存缺口，不把它当作可重试的网络错误。
