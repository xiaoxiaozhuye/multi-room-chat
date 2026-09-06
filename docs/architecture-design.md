# 多聊天室群聊系统架构设计

> 聊天室生命周期、消息顺序、逻辑删除后的访问、审核配置、留存和 WebSocket 会话的规范以 [《多聊天室群聊业务规则与状态机》](business-rules-state-machine.md) 为唯一行为基线；本文件中任何概述性描述与其不一致时，以该规范为准。

## 1. 项目目标

本系统面向投研投顾直播间群聊场景，主要解决多聊天室并行管理、用户消息审核、管理员广播、实时消息推送以及聊天室成员管理等问题。

系统核心目标：

- 支持多个聊天室独立管理。
- 支持聊天室创建、修改、关闭、删除。
- 支持开放加入和审批加入。
- 普通用户发言必须经过审核。
- 管理员可向指定一个或多个聊天室发送消息。
- 支持管理员紧急通知。
- 使用 WebSocket 完成实时消息推送。
- 支持审核超时、批量审核以及审核日志。
- 支持 WebSocket 心跳、断线重连和消息补偿。
- 保证关键业务状态一致性。
- 支持至少 1000 个并发 WebSocket 连接。
- 支持峰值约 100 条消息/秒。
- 核心业务单元测试覆盖率达到 80% 以上。

---

## 2. 设计原则

本项目主要遵循以下原则：

### 2.1 满足题目要求优先

只实现题目明确要求或直接服务于题目要求的功能。

不为了展示技术复杂度额外引入：

- Spring Cloud
- Nacos
- Gateway
- RocketMQ
- Kafka
- Elasticsearch
- Kubernetes
- Seata
- 分库分表
- Service Mesh

### 2.2 模块化单体优先

系统采用：

```text
Spring Boot 模块化单体
```

而不是微服务架构。

主要原因：

- 目标并发规模不大。
- 1000 个 WebSocket 连接单实例即可支撑。
- 100 条消息/秒不需要复杂消息中间件。
- 单体事务处理更简单。
- 笔试项目部署和演示成本更低。
- 更容易保证代码质量和测试覆盖率。

代码内部仍按照业务领域拆分，为未来扩展保留空间。

### 2.3 PostgreSQL 为最终数据源

业务最终状态统一保存在 PostgreSQL。

包括：

```text
用户
聊天室
成员关系
消息
审核状态
审核记录
管理员权限
```

Redis只负责：

```text
缓存
在线状态
待审核消息ID
审核超时调度
限流
```

Redis不是业务最终数据源。

---

## 3. 技术栈

### 后端

```text
Java 17+
Spring Boot 3.x
Spring Security
JWT
Spring WebSocket
MyBatis
PostgreSQL 14+
Redis 6+
JUnit 5
Mockito
JaCoCo
Spring Boot Actuator
```

### 前端

```text
Vue 3
Vite
Element Plus
Pinia
Axios
WebSocket
```

---

## 4. 总体架构

```text
                     Vue3
              ┌────────┴────────┐
              │                 │
           用户端             管理端
              │                 │
              └──── HTTP / WS ──┘
                      │
              ┌───────▼────────┐
              │ Spring Boot 3  │
              └───────┬────────┘
                      │
       ┌──────────────┼──────────────┐
       │              │              │
     用户认证       聊天室管理      消息审核
       │              │              │
       └──────────────┼──────────────┘
                      │
                WebSocket推送
                      │
             ┌────────┴────────┐
             │                 │
        PostgreSQL           Redis
```

---

## 5. 分层架构

题目要求采用分层架构，因此系统按照以下方式划分。

### 5.1 表现层

包括：

```text
REST Controller
WebSocket Handler
```

职责：

- 请求接收。
- DTO转换。
- 参数校验。
- 用户身份获取。
- WebSocket连接处理。

表现层不直接编写核心业务逻辑。

### 5.2 业务层

主要Service：

```text
AuthService
RoomService
RoomMemberService
MessageService
AuditService
MessagePushService
ReviewTimeoutService
NotificationService
PermissionService
```

职责分别对应：

```text
认证
聊天室
成员
消息
审核
推送
超时
通知
权限
```

### 5.3 数据访问层

使用 MyBatis。

主要Mapper：

```text
UserMapper
ChatRoomMapper
UserChatRoomMapper
MessageMapper
AuditLogMapper
AdminRoomPermissionMapper
```

### 5.4 缓存层

Redis主要用于：

```text
在线用户状态
热门聊天室信息
待审核消息ID
审核超时调度
用户发言限流
```

Redis不存储完整消息正文。

---

## 6. 后端项目结构

```text
backend/
└── src/main/java/com/example/chat/

    ├── auth/
    │   ├── controller
    │   ├── service
    │   └── dto
    │
    ├── user/
    │
    ├── room/
    │
    ├── member/
    │
    ├── message/
    │
    ├── audit/
    │
    ├── websocket/
    │
    ├── security/
    │
    ├── common/
    │   ├── exception
    │   ├── response
    │   └── util
    │
    └── infrastructure/
        ├── mapper
        └── redis
```

按照业务模块组织代码，避免所有Controller、Service、Mapper堆在同一目录。

---

## 7. 前端结构

```text
frontend/src/

├── api/
├── views/
│   ├── Login
│   ├── Register
│   ├── RoomList
│   ├── MyRooms
│   ├── ChatRoom
│   ├── MyMessages
│   ├── AdminRoom
│   ├── AdminJoinRequest
│   ├── AdminAudit
│   └── AdminBroadcast
│
├── components/
├── stores/
├── websocket/
├── router/
└── utils/
```

不开发复杂Dashboard或报表系统。

---

## 8. 数据库设计

核心表控制在6张：

```text
users

chat_rooms

user_chat_rooms

messages

audit_logs

admin_room_permissions
```

---

## 9. users 用户表

主要字段：

```text
id

username

password_hash

role

status

created_at

updated_at
```

角色：

```text
USER

ROOM_ADMIN

SYSTEM_ADMIN
```

状态：

```text
ACTIVE

DISABLED
```

---

## 10. chat_rooms 聊天室表

```text
id

name

description

max_users

join_mode

status

deleted

created_by

created_at

updated_at
```

加入模式：

```text
OPEN

APPROVAL
```

聊天室状态：

```text
ACTIVE

PAUSED

CLOSED
```

### 10.1 ACTIVE

正常允许：

```text
加入
发送消息
审核
接收消息
```

### 10.2 PAUSED

暂停状态：

```text
普通用户不可加入

普通用户不可发送消息

管理员仍可发送通知
```

### 10.3 CLOSED

聊天室关闭：

```text
不允许新用户加入

不允许普通聊天

历史消息仍然可以查询
```

---

## 11. 聊天室删除

API：

```text
DELETE /api/admin/rooms/{id}
```

数据库采用逻辑删除：

```text
deleted = true
```

不物理删除聊天室。

主要目的是保留：

```text
历史消息

成员记录

审核日志
```

---

## 12. 聊天室查询和过滤

题目要求聊天室列表支持查询与过滤。

API：

```text
GET /api/rooms
```

支持：

```text
name

status

joinMode

page

size
```

例如：

```text
GET /api/rooms?name=A股&status=ACTIVE&joinMode=OPEN&page=1&size=20
```

不设计复杂全文搜索。

---

## 13. user_chat_rooms 用户聊天室关系

字段：

```text
id

user_id

room_id

status

joined_at

created_at

updated_at
```

状态：

```text
PENDING

ACTIVE

REJECTED

EXITED
```

---

## 14. 用户加入聊天室

### 14.1 开放聊天室

流程：

```text
用户浏览聊天室
      ↓
选择聊天室
      ↓
发起加入
      ↓
检查聊天室状态
      ↓
检查最大人数
      ↓
创建ACTIVE成员关系
      ↓
允许WebSocket订阅
```

### 14.2 审批聊天室

```text
用户申请加入
      ↓
user_chat_rooms
status=PENDING
      ↓
管理员审批
      ↓
ACTIVE / REJECTED
```

审批通过以后才能订阅聊天室。

---

## 15. 最大用户数控制

聊天室有：

```text
max_users
```

加入时必须避免并发超限。

例如：

```text
当前99人
最大100人
```

两个用户同时加入时，不能出现最终101人。

采用PostgreSQL事务控制：

```text
BEGIN

SELECT聊天室记录 FOR UPDATE

统计ACTIVE成员数量

检查是否小于max_users

创建成员关系

COMMIT
```

对于审批聊天室：

> 用户申请时不占聊天室名额。

管理员审批通过时再次检查最大人数。

---

## 16. 用户退出聊天室

流程：

```text
用户主动退出
      ↓
user_chat_rooms.status = EXITED
      ↓
取消WebSocket聊天室订阅
      ↓
更新在线状态
```

退出后不能继续接收该聊天室消息。

---

## 17. messages 消息表

核心字段：

```text
id

message_id

room_id

room_seq

sender_id

message_type

content

status

created_at

reviewed_at

published_at

version
```

message_id使用：

```text
UUID
或
Snowflake ID
```

作为业务消息唯一标识。

---

## 18. 消息类型

```text
CHAT

ADMIN_MESSAGE

SYSTEM_NOTIFICATION
```

---

## 19. 消息状态

```text
PENDING_REVIEW

APPROVED

REJECTED

TIMEOUT

PUBLISHED
```

状态流转：

```text
               PENDING_REVIEW
                /     |      \
               /      |       \
              ↓       ↓        ↓
         APPROVED  REJECTED  TIMEOUT
              |
              ↓
          PUBLISHED
```

---

## 20. 管理员消息

管理员普通广播：

```text
ADMIN_MESSAGE
```

无需普通用户审核。

流程：

```text
保存数据库
   ↓
APPROVED
   ↓
WebSocket推送
   ↓
PUBLISHED
```

---

## 21. 紧急消息

紧急消息类型：

```text
SYSTEM_NOTIFICATION
```

允许有权限管理员绕过审核直接发送。

但是仍然需要：

```text
写入messages

记录audit_logs
```

确保管理员操作也可追踪。

---

## 22. audit_logs 审计日志

不只记录消息审核。

统一用于关键业务行为审计。

字段：

```text
id

actor_id

action_type

resource_type

resource_id

room_id

message_id

before_status

after_status

detail

created_at
```

---

## 23. 审计行为

主要记录：

```text
LOGIN

JOIN_ROOM

LEAVE_ROOM

JOIN_APPROVE

JOIN_REJECT

MESSAGE_SUBMIT

MESSAGE_APPROVE

MESSAGE_REJECT

MESSAGE_TIMEOUT

ROOM_CREATE

ROOM_UPDATE

ROOM_DELETE

ADMIN_BROADCAST

EMERGENCY_PUBLISH
```

不记录普通浏览行为，避免过度审计。

---

## 24. admin_room_permissions

用于：

```text
ROOM_ADMIN
```

管理聊天室范围。

字段：

```text
id

admin_id

room_id
```

SYSTEM_ADMIN默认拥有全部聊天室权限。

---

## 25. 管理员权限

### SYSTEM_ADMIN

拥有：

```text
创建聊天室

修改聊天室

删除聊天室

管理所有聊天室

审核所有聊天室消息

审核加入申请

管理员广播

紧急通知

查看系统审计
```

### ROOM_ADMIN

只能操作授权聊天室：

```text
审核消息

审核加入请求

修改授权聊天室

发送聊天室消息

查看授权聊天室数据
```

不引入完整RBAC平台。

采用：

```text
用户角色
+
聊天室权限范围
```

即可满足题目。

---

## 26. WebSocket连接模型

采用：

> 单用户单WebSocket连接 + 多聊天室订阅

而不是：

```text
一个聊天室建立一条WebSocket连接
```

例如：

```text
用户A
 │
 │ WebSocket
 ↓
Server
 ├── Room 1001
 ├── Room 1002
 └── Personal Channel
```

Personal Channel用于发送：

```text
审核状态通知

审核拒绝通知

审核超时通知
```

---

## 27. WebSocket连接认证

连接建立时：

```text
客户端携带JWT
      ↓
服务端校验JWT
      ↓
获取userId
      ↓
建立WebSocket Session
```

WebSocket订阅某聊天室时仍需再次校验：

```text
user_chat_rooms.status = ACTIVE
```

不能仅依赖前端roomId。

---

## 28. WebSocket会话管理

单实例中：

```text
userId -> WebSocketSession

roomId -> Set<userId>
```

WebSocketSession保存在当前节点内存。

Redis可以保存：

```text
用户在线状态

聊天室在线人数缓存
```

但不保存真正WebSocketSession对象。

---

## 29. WebSocket消息协议

统一JSON格式：

```json
{
  "type": "CHAT_SUBMIT",
  "requestId": "req-10001",
  "timestamp": 1788600000000,
  "payload": {}
}
```

主要type：

```text
SUBSCRIBE_ROOM

UNSUBSCRIBE_ROOM

CHAT_SUBMIT

CHAT_MESSAGE

REVIEW_STATUS

NOTIFICATION

ERROR
```

心跳使用WebSocket标准Ping/Pong，不额外设计复杂业务协议。

---

## 30. 订阅聊天室

客户端：

```json
{
  "type": "SUBSCRIBE_ROOM",
  "requestId": "req-001",
  "payload": {
    "roomId": 1001,
    "lastMessageSeq": 500
  }
}
```

服务端：

```text
检查聊天室存在

检查聊天室状态

检查用户成员关系

status必须=ACTIVE
```

通过后建立聊天室订阅关系。

---

## 31. 用户发送消息

客户端：

```json
{
  "type": "CHAT_SUBMIT",
  "requestId": "req-002",
  "timestamp": 1788600000000,
  "payload": {
    "roomId": 1001,
    "content": "今天市场怎么看？"
  }
}
```

服务端处理：

```text
JWT身份验证
   ↓
聊天室权限验证
   ↓
聊天室状态验证
   ↓
消息长度验证
   ↓
发送频率验证
   ↓
生成messageId
   ↓
生成room_seq
   ↓
写入PostgreSQL
   ↓
PENDING_REVIEW
   ↓
messageId加入Redis审核集合
```

聊天室其他用户此时看不到消息。

---

## 32. 服务端返回审核状态

```json
{
  "type": "REVIEW_STATUS",
  "requestId": "req-002",
  "payload": {
    "messageId": "MSG100001",
    "roomId": 1001,
    "status": "PENDING_REVIEW"
  }
}
```

---

## 33. 发布后的标准聊天消息

题目要求消息格式至少包含：

```text
文本

时间戳

发送者ID

聊天室ID

消息状态
```

因此服务器推送：

```json
{
  "type": "CHAT_MESSAGE",
  "timestamp": 1788600000000,
  "payload": {
    "messageId": "MSG100001",
    "roomId": 1001,
    "senderId": 2001,
    "content": "今天市场怎么看？",
    "status": "PUBLISHED",
    "createdAt": "2026-09-06T10:30:00"
  }
}
```

---

## 34. Redis使用范围

Redis只负责以下内容。

### 在线状态

```text
online:user:{userId}
```

### 热门聊天室缓存

```text
room:info:{roomId}
```

### 待审核消息

```text
review:pending
```

使用ZSet。

### 用户限流

```text
rate:user:{userId}
```

---

## 35. 待审核队列设计

Redis使用ZSet：

```text
key = review:pending
```

value：

```text
messageId
```

score：

```text
审核截止时间
```

例如：

```text
消息提交：10:00:00

审核等待：30秒

score：10:00:30
```

一个ZSet即可完成：

```text
待审核消息管理

审核超时调度
```

不需要再设计多个Redis队列。

---

## 36. 审核等待时间配置

审核超时不能写死。

配置：

```yaml
chat:
  review:
    timeout-seconds: 30
```

默认：

```text
30秒
```

后续可以修改成：

```text
60秒
120秒
```

无需修改业务代码。

---

## 37. 管理员审核列表

管理员审核界面采用：

> REST轮询

不额外设计管理员WebSocket审核Topic。

例如前端每2秒：

```text
GET /api/admin/messages?status=PENDING_REVIEW
```

支持筛选：

```text
roomId

status

userId

时间范围
```

主要状态：

```text
PENDING_REVIEW

APPROVED

REJECTED

TIMEOUT
```

完整状态查询以PostgreSQL为准。

Redis只用于快速获取PENDING消息ID和超时调度。

---

## 38. 单条审核

审核通过：

```text
POST /api/admin/messages/{id}/approve
```

审核拒绝：

```text
POST /api/admin/messages/{id}/reject
```

审核完成：

```text
更新messages

+

插入audit_logs
```

必须在同一PostgreSQL事务完成。

---

## 39. 批量审核

API：

```text
POST /api/admin/messages/batch-review
```

例如：

```json
{
  "messageIds": [
    "MSG001",
    "MSG002",
    "MSG003"
  ],
  "action": "APPROVE"
}
```

每条消息仍需要独立验证：

```text
消息是否存在

管理员是否有room权限

当前状态是否=PENDING_REVIEW
```

避免整批操作越权。

---

## 40. 审核并发控制

可能发生：

```text
管理员A审核通过

管理员B审核拒绝

超时任务同时执行
```

不使用Redis分布式锁。

采用PostgreSQL条件更新或乐观锁。

例如：

```sql
UPDATE messages
SET status = 'APPROVED',
    version = version + 1
WHERE id = ?
  AND status = 'PENDING_REVIEW'
  AND version = ?;
```

如果：

```text
affected rows = 0
```

表示消息已经被其他流程处理。

返回：

```text
消息已处理，请刷新后重试
```

---

## 41. 审核超时

后台任务例如：

```text
每1秒
```

扫描Redis：

```text
score <= 当前时间
```

获取到期messageId。

执行：

```sql
UPDATE messages
SET status = 'TIMEOUT'
WHERE message_id = ?
  AND status = 'PENDING_REVIEW';
```

更新成功：

```text
记录audit_logs
      ↓
删除Redis审核ID
      ↓
向提交用户发送超时通知
```

更新失败：

说明：

```text
消息已经被管理员处理
```

删除Redis记录即可。

---

## 42. 审核超时通知

例如：

```json
{
  "type": "REVIEW_STATUS",
  "payload": {
    "messageId": "MSG100001",
    "roomId": 1001,
    "status": "TIMEOUT",
    "reason": "消息审核超时"
  }
}
```

TIMEOUT消息：

```text
不会进入聊天室
```

---

## 43. 消息审核通过

流程：

```text
PENDING_REVIEW
      ↓
管理员APPROVE
      ↓
数据库APPROVED
      ↓
记录audit_logs
      ↓
事务提交
      ↓
MessagePushService
      ↓
WebSocket广播
      ↓
PUBLISHED
```

---

## 44. 消息可靠性

题目要求考虑：

> At-Least-Once

本项目不额外引入MQ。

采用：

```text
数据库状态
+
推送补偿
```

实现。

正常：

```text
APPROVED
   ↓
WebSocket发送成功
   ↓
PUBLISHED
```

失败：

```text
APPROVED
```

保持不变。

后台补偿任务定期扫描：

```text
status = APPROVED
AND published_at IS NULL
```

重新推送。

---

## 45. At-Least-Once语义

因此消息结果为：

```text
至少推送一次
```

可能出现重复推送。

客户端根据：

```text
messageId
```

去重。

相比追求Exactly Once，该方案更加符合WebSocket实际网络特性，也不会增加过多架构复杂度。

---

## 46. 消息顺序

题目要求：

> 按消息提交时间顺序推送。

因此每个聊天室维护：

```text
room_seq
```

例如：

```text
Room1001

101

102

103

104
```

---

## 47. 顺序审核情况

例如：

```text
101 PENDING_REVIEW

102 APPROVED
```

102不能直接越过101发布。

如果101：

```text
APPROVED
```

则：

```text
101发布
102发布
```

如果101：

```text
REJECTED
```

则：

```text
跳过101
发布102
```

如果101一直没有审核：

```text
最多等待配置的审核超时时间
```

之后：

```text
101 TIMEOUT
```

再继续102。

这样保证：

> 同一聊天室消息按提交顺序展示。

不保证不同聊天室之间全局有序。

---

## 48. 管理员向多个聊天室广播

API：

```text
POST /api/admin/messages/broadcast
```

请求：

```json
{
  "roomIds": [
    1001,
    1002,
    1003
  ],
  "content": "下午三点直播开始"
}
```

后台分别生成对应聊天室消息。

每个聊天室拥有独立：

```text
messageId

room_seq
```

然后实时广播。

---

## 49. 消息历史记录

聊天室历史：

```text
GET /api/rooms/{id}/messages
```

只查询：

```text
status = PUBLISHED
```

普通用户不能查询：

```text
PENDING_REVIEW

REJECTED

TIMEOUT
```

的其他用户消息。

---

## 50. 历史消息分页

采用游标分页。

例如：

```text
GET /api/rooms/1001/messages?beforeSeq=500&size=50
```

SQL使用：

```text
room_seq < 500
```

而不是大OFFSET分页。

---

## 51. 个人消息历史

API：

```text
GET /api/users/me/messages
```

用户可以查询自己的：

```text
消息内容

聊天室

提交时间

审核状态
```

包括：

```text
PENDING_REVIEW

PUBLISHED

REJECTED

TIMEOUT
```

---

## 52. 审核状态查询

接口：

```text
GET /api/users/me/messages/{messageId}/status
```

用于主动查询审核状态。

同时WebSocket也会实时通知审核结果。

---

## 53. WebSocket心跳

采用WebSocket标准：

```text
Ping / Pong
```

例如客户端定期发送Ping。

服务端返回Pong。

超过一定时间没有响应：

```text
关闭Session
      ↓
清除在线状态
```

避免僵尸连接。

---

## 54. WebSocket断线重连

客户端断线后：

```text
自动重连
   ↓
重新携带JWT认证
   ↓
恢复之前聊天室订阅
```

客户端保存：

```text
roomId

lastMessageSeq
```

---

## 55. 断线消息补偿

重新订阅：

```json
{
  "type": "SUBSCRIBE_ROOM",
  "payload": {
    "roomId": 1001,
    "lastMessageSeq": 350
  }
}
```

服务器查询：

```text
room_seq > 350

AND status = PUBLISHED
```

将遗漏消息补发。

因此WebSocket临时中断不会永久丢消息。

---

## 56. 用户消息限流

为了防止直播聊天室刷屏，增加简单限流。

例如：

```text
每秒最多2条

每分钟最多20条
```

使用Redis。

超过限制：

```text
本条消息不进入审核队列
```

返回：

```json
{
  "type": "ERROR",
  "payload": {
    "code": "MESSAGE_RATE_LIMITED",
    "message": "发送消息过于频繁"
  }
}
```

不引入Sentinel等额外流控组件。

---

## 57. 安全设计

主要包括：

```text
JWT认证

Spring Security

WebSocket握手认证

聊天室订阅权限校验

聊天室发送权限校验

ROOM_ADMIN权限范围校验

消息长度限制

用户发言限流

数据库参数化SQL

输出内容XSS转义

关键操作审计
```

服务端不信任前端传递的：

```text
userId

role

roomId权限
```

必须自行校验。

---

## 58. REST API设计

### 用户认证

```text
POST /api/auth/register

POST /api/auth/login

GET /api/users/me
```

### 聊天室

```text
GET /api/rooms

GET /api/rooms/{id}

POST /api/rooms/{id}/join

POST /api/rooms/{id}/leave

GET /api/users/me/rooms
```

### 用户消息

```text
GET /api/rooms/{id}/messages

GET /api/users/me/messages

GET /api/users/me/messages/{messageId}/status
```

### 管理员聊天室

```text
POST /api/admin/rooms

PUT /api/admin/rooms/{id}

DELETE /api/admin/rooms/{id}
```

### 加入审批

```text
GET /api/admin/join-requests

POST /api/admin/join-requests/{id}/approve

POST /api/admin/join-requests/{id}/reject
```

### 消息审核

```text
GET /api/admin/messages

POST /api/admin/messages/{id}/approve

POST /api/admin/messages/{id}/reject

POST /api/admin/messages/batch-review
```

### 管理员广播

```text
POST /api/admin/messages/broadcast
```

### 基础系统监控

```text
GET /api/admin/system/metrics
```

---

## 59. 管理员监控

题目中的Prometheus属于可选，因此项目只实现最小监控能力。

Spring Boot引入：

```text
Actuator
```

提供：

```text
/actuator/health
```

另外提供基础管理指标：

```json
{
  "webSocketConnections": 856,
  "pendingReviewCount": 23,
  "reviewTimeoutCount": 5,
  "todayMessageCount": 3120
}
```

---

## 60. 监控指标

设计上重点关注：

```text
WebSocket在线连接数

消息提交数量

消息处理延迟

审核平均耗时

待审核消息数

审核超时数

WebSocket推送异常数

接口错误率

数据库连接池使用率
```

Prometheus和Grafana作为生产扩展方案说明即可，本次不强制部署。

---

## 61. 日志设计

关键业务日志统一带：

```text
requestId

userId

roomId

messageId
```

例如：

```text
requestId=req-10001
userId=2001
roomId=1001
messageId=MSG10001
action=MESSAGE_APPROVE
```

发生线上问题时，可以根据：

```text
messageId
```

追踪完整生命周期。

---

## 62. 线上问题处理流程

统一流程：

```text
监控告警 / 用户反馈
        ↓
确认影响范围
        ↓
查询应用日志
        ↓
查看关键指标
        ↓
检查PostgreSQL
        ↓
检查Redis
        ↓
检查WebSocket连接
        ↓
恢复服务
        ↓
执行消息补偿
        ↓
问题复盘
```

---

## 63. 示例：审核成功但消息没有展示

排查：

```text
根据messageId查询messages
        ↓
检查status
```

如果：

```text
status = APPROVED
```

说明：

```text
审核成功
WebSocket推送失败
```

由补偿任务重新推送。

---

## 64. PostgreSQL与Redis职责

### PostgreSQL

负责：

```text
用户

聊天室

用户聊天室关系

消息正文

消息最终状态

审核记录

管理员权限
```

属于：

> Source of Truth

### Redis

负责：

```text
在线状态

热门聊天室缓存

待审核messageId

审核超时调度

限流
```

Redis故障不会导致业务数据丢失。

---

## 65. 最终一致性

需要强一致的业务：

```text
消息审核状态 + audit_logs

加入审批状态
```

通过PostgreSQL事务保证。

允许最终一致：

```text
Redis聊天室缓存

在线用户状态

聊天室在线人数
```

允许：

```text
约1秒以内同步延迟
```

即使缓存未更新，业务权限判断仍以PostgreSQL为准。

---

## 66. Redis缓存一致性

聊天室修改：

```text
更新PostgreSQL
      ↓
删除Redis缓存
```

下次查询：

```text
重新读取PostgreSQL
      ↓
写入Redis
```

采用简单Cache Aside策略。

不使用复杂缓存双写方案。

---

## 67. 核心服务无状态设计

以下Service不保存用户业务状态：

```text
RoomService

MessageService

AuditService

RoomMemberService
```

所有业务状态存储于：

```text
PostgreSQL

Redis
```

因此核心业务服务可以水平扩展。

唯一例外：

```text
WebSocketSession
```

由于与具体TCP连接绑定，需要保存在当前节点内存中。

---

## 68. 性能设计

目标：

```text
1000+ WebSocket连接

峰值100消息/秒
```

该规模下：

```text
Spring Boot单实例
+
PostgreSQL
+
Redis
```

足够支撑。

---

## 69. 数据库索引

建议至少建立：

```text
messages(room_id, room_seq)

messages(status, created_at)

messages(sender_id, created_at)

user_chat_rooms(user_id, room_id)

user_chat_rooms(room_id, status)

audit_logs(message_id)

audit_logs(created_at)
```

---

## 70. 性能优化重点

重点：

```text
WebSocket连接及时释放

数据库连接池合理配置

消息历史游标分页

Redis缓存热门聊天室

Redis限流

数据库合理索引
```

不做：

```text
分库分表

读写分离

多级缓存

复杂分布式集群
```

---

## 71. 单元测试设计

重点测试业务Service。

测试类：

```text
AuthServiceTest

RoomServiceTest

RoomMemberServiceTest

MessageServiceTest

AuditServiceTest

MessagePushServiceTest

ReviewTimeoutServiceTest

PermissionServiceTest
```

---

## 72. 核心测试场景

包括：

```text
用户注册成功

重复用户注册失败

登录成功

开放聊天室加入

审批聊天室申请

最大人数限制

管理员批准加入

用户退出聊天室

普通用户发送消息

未加入聊天室发送失败

PAUSED聊天室发送失败

普通消息进入PENDING_REVIEW

管理员审核通过

管理员审核拒绝

批量审核

重复审核失败

审核和TIMEOUT并发只能成功一个

审核超时

ROOM_ADMIN越权失败

SYSTEM_ADMIN操作成功

管理员广播

紧急通知

消息推送失败进入补偿

历史消息只返回PUBLISHED
```

---

## 73. 测试覆盖率

使用：

```text
JUnit 5

Mockito

JaCoCo
```

目标：

```text
Line Coverage >= 80%
```

重点覆盖：

```text
Service业务逻辑
```

不为了覆盖率给：

```text
Getter
Setter
DTO
```

编写大量无意义测试。

---

## 74. JaCoCo构建门禁

Maven配置JaCoCo检查。

执行：

```text
mvn test
```

如果：

```text
Coverage < 80%
```

则构建失败。

这样可以直接证明满足交付要求。

---

## 75. 前端用户功能

实现：

```text
注册

登录

浏览聊天室

聊天室过滤

申请加入

退出聊天室

聊天室实时聊天

查看历史消息

查看个人消息记录

查看审核状态
```

---

## 76. 前端管理员功能

实现：

```text
聊天室增删改查

聊天室状态管理

加入申请审核

待审核消息查看

按聊天室筛选

按审核状态筛选

单条审核

批量审核

广播消息

紧急通知

基础系统指标查看
```

不开发复杂报表和统计Dashboard。

---

## 77. 代码质量要求

遵循Clean Code原则：

```text
Controller只负责接口

Service负责业务

Mapper负责数据访问

DTO和Entity分离

统一异常处理

统一返回结果

类和方法职责单一

避免超大Service

避免重复代码

关键业务添加必要注释
```

---

## 78. 统一异常处理

主要异常：

```text
BusinessException

AuthenticationException

PermissionDeniedException

RoomNotFoundException

RoomFullException

MessageNotFoundException

MessageAlreadyReviewedException
```

使用：

```text
@RestControllerAdvice
```

统一转换为API响应。

---

## 79. 统一响应格式

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

业务异常：

```json
{
  "code": 40001,
  "message": "聊天室人数已满",
  "data": null
}
```

---

## 80. 项目目录

最终交付结构：

```text
multi-room-chat/

├── backend/
│   ├── src/main/
│   ├── src/test/
│   └── pom.xml
│
├── frontend/
│   ├── src/
│   └── package.json
│
├── sql/
│   ├── schema.sql
│   └── init-data.sql
│
├── docs/
│   ├── system-design.md
│   ├── database-design.md
│   ├── api-design.md
│   └── monitoring.md
│
└── README.md
```

---

## 81. README结构

```text
# Multi Room Chat System

## 1. 项目介绍

## 2. 核心功能

## 3. 技术架构

## 4. 项目结构

## 5. 数据库设计

## 6. API设计

## 7. WebSocket协议

## 8. 本地运行

## 9. 单元测试

## 10. 系统设计
```

---

## 82. ER关系

```text
USERS
  │
  │ 1
  │
  │ N
USER_CHAT_ROOMS
  │
  │ N
  │
  │ 1
CHAT_ROOMS


USERS
  │
  │ 1
  │
  │ N
MESSAGES


MESSAGES
  │
  │ 1
  │
  │ N
AUDIT_LOGS


USERS
  │
  │ 1
  │
  │ N
ADMIN_ROOM_PERMISSIONS
  │
  │ N
  │
  │ 1
CHAT_ROOMS
```

---

## 83. 核心业务完整流程

普通用户：

```text
注册
 ↓
登录
 ↓
浏览聊天室
 ↓
加入聊天室
 ↓
建立WebSocket
 ↓
订阅聊天室
 ↓
发送消息
 ↓
PENDING_REVIEW
 ↓
进入待审核集合
 ↓
管理员审核
 ↓
APPROVED
 ↓
WebSocket广播
 ↓
PUBLISHED
 ↓
聊天室所有在线用户看到
```

---

## 84. 审核拒绝流程

```text
用户发送
 ↓
PENDING_REVIEW
 ↓
管理员REJECT
 ↓
REJECTED
 ↓
记录audit_logs
 ↓
通知发送者
```

消息不会进入聊天室。

---

## 85. 审核超时流程

```text
用户发送
 ↓
PENDING_REVIEW
 ↓
超过最大审核时间
 ↓
后台任务
 ↓
TIMEOUT
 ↓
audit_logs
 ↓
通知提交用户
```

TIMEOUT消息不会进入聊天室。

---

## 86. 架构优化总结

本方案主要做了以下优化：

1. 使用模块化单体，降低笔试实现和部署复杂度。

2. 使用单WebSocket连接多聊天室订阅，减少连接数量。

3. PostgreSQL作为唯一业务最终数据源。

4. Redis只缓存必要临时状态，不保存完整消息正文。

5. Redis ZSet同时解决待审核管理和审核超时调度。

6. 使用PostgreSQL事务保证审核状态和审计日志一致性。

7. 使用条件更新/乐观锁解决审核和超时并发竞争。

8. 使用room_seq保证同一聊天室消息提交顺序。

9. 使用APPROVED状态补偿机制实现至少一次消息推送。

10. 使用messageId进行客户端去重。

11. 使用Ping/Pong保证WebSocket连接健康。

12. 使用lastMessageSeq解决断线后的消息恢复。

13. 使用数据库锁保证聊天室最大人数并发限制。

14. 使用角色 + 管理员聊天室权限实现管理员分级。

15. 使用Actuator及基础业务指标完成最小系统监控。

---

## 87. 明确不做的内容

为了避免过度设计，本次项目不实现：

```text
Spring Cloud

Nacos

Gateway

RocketMQ

Kafka

RabbitMQ

Elasticsearch

Kubernetes

Seata

分库分表

读写分离

完整ELK

完整Prometheus + Grafana部署

复杂RBAC权限平台

微服务集群部署
```

这些技术在当前：

```text
1000 WebSocket

100消息/秒
```

的目标下没有必要。

---

## 88. 最终核心考核能力

整个项目最终重点体现：

```text
1. 多聊天室业务建模

2. WebSocket实时通信

3. 用户加入/退出及审批流程

4. 普通消息审核

5. 批量审核

6. 审核超时

7. 消息顺序

8. 管理员分级权限

9. PostgreSQL事务一致性

10. Redis缓存及超时队列

11. WebSocket断线恢复

12. 消息可靠性

13. 用户行为审计

14. 系统稳定性

15. 单元测试与Clean Code
```

该架构能够完整覆盖题目要求，同时保持实现规模可控，不额外引入与笔试考核目标无关的复杂组件。
