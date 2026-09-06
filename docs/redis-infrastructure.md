# Redis 临时基础设施与降级约定

PostgreSQL 是消息、成员、房间和审计记录的唯一权威来源；Redis 只保存可丢弃的派生状态。所有 Redis 调用均为 best-effort，调用失败只记录 `redis_operation_unavailable`，不会回滚或阻止已提交的 PostgreSQL 事务。

| 功能 | Redis 内容 | 正常路径 | Redis 不可用时 |
| --- | --- | --- | --- |
| 房间缓存 | `room:info:{roomId}`、仅房间元数据、5 分钟 TTL | Cache Aside：先读缓存，未命中读 PostgreSQL 后回填；房间写入提交后删除键 | 直接查询 PostgreSQL；不缓存 |
| 在线状态 | `online:user:{userId}`、值 `1`、70 秒 TTL | 建连和 Pong/客户端消息刷新 TTL，当前连接关闭时删除 | 状态为 `UNKNOWN`，绝不将其作为权限或业务判断 |
| 消息限流 | `rate:user:{userId}` ZSet、只保留 60 秒时间戳 | Lua 原子滑动窗口：每秒 2 条、每分钟 20 条 | fail-open：返回 `allowed=true, enforced=false`，由 PostgreSQL 继续处理消息 |
| 待审索引 | `review:pending` ZSet，member 为 `messageId`，score 为 `review_deadline_at` 的 epoch ms | 到期时先由 ZSet 取候选，仍回查 PostgreSQL 并以条件更新完成状态迁移 | 到期扫描直接查询 PostgreSQL 中截止且仍为 `PENDING_REVIEW` 的消息 |

`PendingReviewIndexRecovery` 会在应用就绪时以及每 30 秒从 PostgreSQL 的 `PENDING_REVIEW` 记录执行幂等 `ZADD`。因此 Redis 被清空或重启后，索引会自动恢复；消息正文、最终状态、成员关系和审计记录从不依赖 Redis。处理审核结果后，调用方应删除相应的 ZSet member；即使删除失败，超时扫描也会再次回查 PostgreSQL，因而不会重复变更或重复产生审计记录。
