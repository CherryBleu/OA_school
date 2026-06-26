# AI 驱动的校园小组协同 OA

按设计文档实现的前后端分离系统：

- `backend/`：Spring Boot 3 + Java 21 + PostgreSQL，提供 `/api/v1` REST API、JWT 鉴权、Git 仓库审查、AI 代理和 `/ws/hub` WebSocket 总线。
- `frontend/`：Vue 3 + Vite + TypeScript + Element Plus + ECharts。
- `render.yaml`：Render Blueprint，包含 Java API、静态前端和 PostgreSQL。

## 主要功能

- 注册/登录，前端固化 `device_uuid`，后端维护多设备会话并支持精准踢下线。
- 组长创建项目并生成业务小组号，组员通过 `/api/v1/project/info/{group_id}` 预检后加入。
- 入组前置校验：用户至少完成一个 AI 技能核验。
- 技能画像、AI 出题、AI 阅卷，60 分以上激活金标。
- `/api/v1/ai/wbs-generate` 根据需求文本拆解 WBS 任务，并生成 `features_json` 硬性功能清单。
- 看板流转和 `/api/v1/task/submit` GitHub/Gitee 仓库链接提交。
- 后端临时 `git clone`，过滤 `.git`、`node_modules`、`target` 等目录，仅拼接核心源码；审查完成后在 `finally` 删除临时目录。
- WBS 工期加权进度计算，WebSocket 事件：
  - `METRICS_UPDATED`
  - `TASK_REJECTED`
  - `FORCE_LOGOUT`

## API Key 保护

大模型 key 只存在后端环境变量，前端不会打包或接触：

```bash
LLM_API_KEY=你的 key
LLM_BASE_URL=https://api.openai.com/v1
LLM_MODEL=gpt-4o-mini
```

`.env`、`.env.*` 已被 `.gitignore` 排除，仓库里只保留 `.env.example`。如果不配置 `LLM_API_KEY`，后端会启用本地降级逻辑，方便先演示流程。

## 本地运行

1. 安装前端依赖：

```bash
npm --prefix frontend install
```

2. 启动本地 PostgreSQL：

```bash
npm run db:up
```

这会通过 `docker-compose.yml` 启动一个 PostgreSQL 16 容器，端口是 `localhost:5432`，数据库名是 `oa_school`。

如果出现 `failed to connect to the docker API` 或 `docker_engine ... not found`，说明 Docker Desktop 还没有启动。先启动 Docker Desktop，等它显示 Running 后再执行 `npm run db:up`。

如果 Docker Desktop 显示 `Virtualization support not detected`，说明当前 Windows 没有检测到虚拟化支持。可以走两条路：

- 启用虚拟化后继续用 Docker：进 BIOS/UEFI 开启 Intel VT-x 或 AMD-V，并在 Windows 功能中启用 `Windows Subsystem for Linux`、`Virtual Machine Platform`，重启后再启动 Docker Desktop。
- 不用 Docker：安装 PostgreSQL 官方 Windows 版，创建数据库 `oa_school`，然后保持 `backend/.env` 为：

```bash
DATABASE_URL=jdbc:postgresql://localhost:5432/oa_school
DB_USERNAME=postgres
DB_PASSWORD=你的 PostgreSQL 密码
```

也可以直接使用 Render/Neon/Supabase 等云 PostgreSQL，把 `DATABASE_URL` 改成对应连接串。后端支持 `jdbc:postgresql://...`，也支持 Render 常见的 `postgres://...`。

如果出现 `Connection to localhost:5432 refused`，说明后端能启动，但本地 PostgreSQL 没有在 5432 端口接受连接。优先执行 `npm run db:up`，或把 `backend/.env` 改成你自己的 PostgreSQL 地址、账号和密码。

3. 复制后端环境变量：

```bash
copy backend\.env.example backend\.env
```

4. 修改或确认 `backend/.env`：

```bash
DATABASE_URL=jdbc:postgresql://localhost:5432/oa_school
DB_USERNAME=postgres
DB_PASSWORD=postgres
JWT_SECRET=replace-with-a-long-random-secret
```

5. 启动后端和前端：

```bash
npm run dev:backend
npm run dev:frontend
```

前端默认 `http://localhost:5173`，后端默认 `http://localhost:8080/api/v1`。数据库表会由 Spring Boot 启动时自动初始化。

## Render 部署

把源码推到 GitHub 后，在 Render 中使用根目录 `render.yaml` 创建 Blueprint。

部署时检查：

- `CORS_ORIGIN`：前端静态站域名，例如 `https://oa-school-web.onrender.com`。
- `VITE_API_BASE_URL`：后端 API 域名并带 `/api/v1`，例如 `https://oa-school-api.onrender.com/api/v1`。
- `LLM_API_KEY`：`render.yaml` 中为 `sync: false`，在 Render 控制台输入，不会写进 GitHub。

设计文档中的 Redis 令牌环目前用 PostgreSQL `user_session` 表实现同等的多设备会话管理，避免 Render 未配置 Redis 时服务无法启动；后续可以把会话存储替换成 Redis Hash，不影响前端接口。

## 上传 GitHub

```bash
git add .
git commit -m "Build campus OA system"
git branch -M main
git remote add origin https://github.com/<your-name>/<repo>.git
git push -u origin main
```
