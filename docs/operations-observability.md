# 运行状态与消息排障

## 基础运行状态

应用提供匿名可访问的 `GET /actuator/health`（以及 Kubernetes 探针
`/actuator/health/liveness`、`/actuator/health/readiness`）。除健康检查外，Actuator
指标端点仅允许携带 `SYSTEM_ADMIN` 身份的访问令牌：

```text
GET /actuator/metrics
GET /actuator/metrics/{metricName}
```

业务指标如下。`chat.review.pending`、`chat.messages.today` 和
`chat.review.average.duration` 从 PostgreSQL 实时查询；其余累计型指标自当前进程启动
后计数。日期按 UTC 日界线计算。

| 指标 | 含义 |
| --- | --- |
| `chat.websocket.connections` | 当前节点活跃 WebSocket 连接数 |
| `chat.review.pending` | 当前待审核普通消息数 |
| `chat.review.timeouts` | 审核超时次数 |
| `chat.messages.today` | 当日提交的消息数 |
| `chat.review.average.duration` | 已完成审核消息的平均耗时（秒） |
| `chat.review.duration` | 审核耗时分布（秒），可用 count 和 total 计算进程内平均值 |
| `chat.push.failures` | WebSocket 消息或审核结果推送失败次数 |
| `chat.http.error.rate` | 本进程 HTTP 4xx/5xx 占完成请求的比例 |
| `chat.http.errors` / `chat.http.requests` | 错误请求和完成请求的累计数 |

接口维度的明细同时由 Actuator 内置 `http.server.requests` 提供，可按 `uri`、`status`
和 `outcome` 标签筛选。连接池同时以业务稳定名称暴露
`chat.database.pool.active`、`chat.database.pool.idle`、`chat.database.pool.max`、
`chat.database.pool.pending` 和 `chat.database.pool.usage`；Actuator/Micrometer 还会提供
Hikari 原生 `hikaricp.connections.*` 指标。`usage` 持续偏高或 `pending > 0` 表明池可能饱和。

## 按 messageId 排障：审核成功但未展示

所有关键日志都带有 `requestId`、`userId`、`roomId`、`messageId` MDC 字段。先从日志
平台搜索精确的 `messageId=<消息 UUID>`，再按下列期望事件串判断断点：

```text
SUBMIT_PERSISTED
  -> REVIEW_RECEIVED:APPROVED
  -> REVIEW_COMPLETED:APPROVED
  -> REVIEW_RESULT_NOTIFY:APPROVED / REVIEW_STATUS_PUSHED:APPROVED
  -> PUBLISH_ATTEMPT
  -> PUSH_DELIVERED ...
  -> PUBLISH_COMPLETED
```

若首次投递失败，会出现 `PUBLISH_DELIVERY_FAILED_WAITING_COMPENSATION`；之后必须能看到
`PUBLISH_COMPENSATION_ATTEMPT`，并以 `PUBLISH_COMPENSATION_COMPLETED` 收敛。持续出现
`PUBLISH_COMPENSATION_RETRY_SCHEDULED` 时，检查 `chat.push.failures`、目标房间的订阅
连接与 WebSocket 网关日志。审核结果个人通知失败会记录
`REVIEW_STATUS_PUSH_FAILED:*`。

同时查询权威状态和不可变审计记录（参数绑定请使用 UUID，避免将用户输入拼进 SQL）：

```sql
SELECT id, request_id, room_id, sender_id, room_seq, status,
       review_deadline_at, reviewed_at, reviewed_by, published_at, created_at
FROM messages
WHERE id = :message_id;

SELECT action, actor_id, request_id, room_id, message_id, before_state, after_state, detail, created_at
FROM audit_logs
WHERE message_id = :message_id
ORDER BY created_at;
```

判读方式：

- `PENDING_REVIEW`：审核并未成功，核对审核请求、权限与截止时间。
- `REJECTED`、`TIMEOUT` 或 `CANCELLED_BY_ROOM_DELETION`：不会展示；审计记录给出终态原因。
- `APPROVED` 且 `published_at IS NULL`：消息尚未通过房间顺序闸门。先查同一 `room_id`
  上小于该 `room_seq` 的消息是否仍为 `PENDING_REVIEW`；若无阻塞，检查补偿日志和调度器。
- `PUBLISHED`：服务端已完成持久化发布。查 `PUSH_DELIVERED`/`PUSH_REPLAY_DELIVERED`，再核对
  客户端是否已订阅该房间、`lastMessageSeq` 是否正确，以及是否按 `messageId` 去重。客户端断线时，
  重新订阅会按 `roomSeq` 回放；若日志显示 `SUBSCRIBED_WITH_GAP`，说明内容已按保留策略清理，
  需要按保留策略处理而不是重复推送。

需要跨 HTTP、WebSocket 和调度线程关联时，先用 `messageId` 找到提交日志中的 `requestId`，再搜索
该 `requestId`；审核和补偿的异步日志仍会保留原始消息的 `requestId` 与全部业务标识。
