# 多聊天室群聊前端

独立的 Vue 3 / Vite 前端，统一承载用户端和管理端。业务页面后续可直接使用 `api/http.ts` 的 `request` 与 `websocket/client.ts` 的 `chatWebSocket`。

## 启动

```bash
npm install
npm run dev
```

复制 `.env.example` 为 `.env.local` 后可调整后端地址。开发服务器会将 `/api`、`/ws` 代理到 `localhost:8080`。

`VITE_WS_AUTH_MODE=subprotocol` 是默认且符合 API 契约的方式：浏览器会在 `Sec-WebSocket-Protocol` 中发送 `chat.v1` 与 bearer token。`query` 仅用于明确仍依赖旧版 query-token 握手的开发后端，不应在生产环境使用。

## 权限边界

路由和菜单仅为界面控制。所有 REST 与 WebSocket 操作都携带 JWT，关键授权必须继续由服务端重新校验。
