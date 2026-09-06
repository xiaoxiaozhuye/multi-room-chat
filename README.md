# 多聊天室群聊项目

## 项目截图

### 登录页面

![登录页面](img/screenshot-1788696630566.png)

### 聊天室列表

![聊天室列表](img/screenshot-1788696646981.png)

### 聊天室会话

![聊天室会话](img/screenshot-1788696575947.png)

### 聊天室运营

![聊天室运营](img/screenshot-1788696957079.png)

### 广播与通知

![广播与通知](img/screenshot-1788696934089.png)

### 运行状态

![运行状态](img/screenshot-1788696969363.png)

## 环境要求

- Java：17
- Maven：3.9.7
- PostgreSQL：14 或更高版本
- Redis：6.2 或更高版本，本地不设置密码
- Node.js：18 或更高版本，建议使用 Node.js 20 LTS

## 数据库初始化

初始化 SQL 文件位于 `sql/migrations` 目录；项目实际启动时由 Flyway 执行 `src/main/resources/db/migration` 目录中的同版本迁移脚本。

首次启动前请先在 PostgreSQL 中创建配置文件指定的数据库。项目启动后会自动执行数据库迁移，并创建所需的数据表和初始化数据，无需手动执行建表 SQL。

## 启动步骤

1. 修改 `src/main/resources/application-dev.yml` 中的 PostgreSQL 配置：

   ```yaml
   spring:
     datasource:
       url: jdbc:postgresql://localhost:5432/postgres
       username: postgres
       password: 你的PostgreSQL密码
   ```

2. 启动 Redis，保持默认无密码配置。

3. 在 IDE 中运行后端启动类 `MultiRoomChatApplication.java`。

4. 打开终端并启动前端：

   ```powershell
   cd frontend
   npm install
   npm run dev
   ```

5. 在浏览器打开终端显示的前端地址，通常为 `http://localhost:5173`。

## 普通用户注册

在登录页面点击“还没有账号？创建账号”，填写用户名、邮箱和密码后，点击“注册并登录”即可创建普通用户账号并自动登录。

- 用户名：3 至 32 个字符，只能包含字母、数字、下划线或连字符。
- 邮箱：有效邮箱地址，最长 254 个字符。
- 密码：8 至 72 个字符。

## 默认账号

初始管理员账号：`admin` / `Admin123!`。
