# OA School 接口文档

本文档按当前后端代码整理，适用于本地环境和 Render 云端环境。

## 1. 访问地址

### 本地环境

- 前端地址：`http://localhost:5173`
- API Base URL：`http://localhost:8080/api/v1`
- WebSocket：`ws://localhost:8080/ws/hub`

### 云端环境

- 前端地址：`https://oa-school-web.onrender.com`
- API Base URL：`https://oa-school-api.onrender.com/api/v1`
- WebSocket：`wss://oa-school-api.onrender.com/ws/hub`

### 通用请求头

除健康检查、注册、登录外，`/api/v1/**` 接口都需要 JWT：

```http
Authorization: Bearer <token>
Content-Type: application/json
X-Device-Id: <device-uuid>
```

`X-Device-Id` 用于多设备会话管理。注册和登录时也可以在请求体里传 `deviceId` 或 `device_uuid`。

### 通用错误返回

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "请求参数不正确"
  }
}
```

常见状态码：

- `401`：未登录、Token 无效或会话失效
- `403`：无权限
- `404`：资源不存在
- `409`：业务状态冲突
- `422`：参数校验失败
- `500`：服务器内部错误

## 2. 健康检查

### 2.1 获取服务状态

请求方法：`GET`

接口路径：`/health`

完整地址：

- 本地：`GET http://localhost:8080/api/v1/health`
- 云端：`GET https://oa-school-api.onrender.com/api/v1/health`

是否需要登录：否

请求示例：

```bash
curl http://localhost:8080/api/v1/health
```

返回示例：

```json
{
  "ok": true,
  "service": "oa-school-api"
}
```

兼容路径：`GET /api/health`

## 3. 认证与会话

### 3.1 注册

请求方法：`POST`

接口路径：`/auth/register`

完整地址：

- 本地：`POST http://localhost:8080/api/v1/auth/register`
- 云端：`POST https://oa-school-api.onrender.com/api/v1/auth/register`

是否需要登录：否

请求示例：

```json
{
  "username": "20260001",
  "password": "123456",
  "name": "张三",
  "deviceId": "demo-device-001"
}
```

返回示例：

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "user": {
    "id": "4ff5b5c1-77c6-4f4a-a8cb-6b84b8a4d111",
    "username": "20260001",
    "name": "张三"
  }
}
```

说明：

- `username` 长度 2 到 64
- `password` 长度 6 到 128
- `name` 为空时默认使用 `username`

### 3.2 登录

请求方法：`POST`

接口路径：`/auth/login`

完整地址：

- 本地：`POST http://localhost:8080/api/v1/auth/login`
- 云端：`POST https://oa-school-api.onrender.com/api/v1/auth/login`

是否需要登录：否

请求示例：

```json
{
  "username": "20260001",
  "password": "123456",
  "deviceId": "demo-device-001"
}
```

返回示例：

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "user": {
    "id": "4ff5b5c1-77c6-4f4a-a8cb-6b84b8a4d111",
    "username": "20260001",
    "name": "张三"
  }
}
```

### 3.3 获取当前用户

请求方法：`GET`

接口路径：`/auth/me`

完整地址：

- 本地：`GET http://localhost:8080/api/v1/auth/me`
- 云端：`GET https://oa-school-api.onrender.com/api/v1/auth/me`

是否需要登录：是

请求示例：

```bash
curl -H "Authorization: Bearer <token>" http://localhost:8080/api/v1/auth/me
```

返回示例：

```json
{
  "user": {
    "id": "4ff5b5c1-77c6-4f4a-a8cb-6b84b8a4d111",
    "username": "20260001",
    "name": "张三"
  }
}
```

### 3.4 获取登录设备列表

请求方法：`GET`

接口路径：`/auth/sessions`

完整地址：

- 本地：`GET http://localhost:8080/api/v1/auth/sessions`
- 云端：`GET https://oa-school-api.onrender.com/api/v1/auth/sessions`

是否需要登录：是

请求示例：

```bash
curl -H "Authorization: Bearer <token>" http://localhost:8080/api/v1/auth/sessions
```

返回示例：

```json
{
  "sessions": [
    {
      "session_id": "96a44d28-1211-4784-a7ab-20a1ec910001",
      "device_id": "demo-device-001",
      "user_agent": "Mozilla/5.0",
      "last_seen_at": "2026-06-29T14:30:00Z",
      "created_at": "2026-06-29T14:00:00Z",
      "current": true
    }
  ]
}
```

### 3.5 下线指定设备

请求方法：`DELETE`

接口路径：`/auth/sessions/{id}`

完整地址：

- 本地：`DELETE http://localhost:8080/api/v1/auth/sessions/{id}`
- 云端：`DELETE https://oa-school-api.onrender.com/api/v1/auth/sessions/{id}`

是否需要登录：是

请求示例：

```bash
curl -X DELETE -H "Authorization: Bearer <token>" http://localhost:8080/api/v1/auth/sessions/96a44d28-1211-4784-a7ab-20a1ec910001
```

返回示例：

```http
204 No Content
```

## 4. 项目与小组

`/api/v1/project` 和 `/api/v1/projects` 均可使用，以下以 `/project` 为例。

### 4.1 获取项目列表

请求方法：`GET`

接口路径：`/project`

完整地址：

- 本地：`GET http://localhost:8080/api/v1/project`
- 云端：`GET https://oa-school-api.onrender.com/api/v1/project`

是否需要登录：是

请求示例：

```bash
curl -H "Authorization: Bearer <token>" http://localhost:8080/api/v1/project
```

返回示例：

```json
{
  "projects": [
    {
      "id": "2a6d3c7f-80d5-41b4-a908-7f3d6e0d0001",
      "group_id": "A7K29QX",
      "name": "校园 OA 协作系统",
      "description": "课程设计项目",
      "owner_id": "4ff5b5c1-77c6-4f4a-a8cb-6b84b8a4d111",
      "project_progress": 0.0,
      "member_role": "leader",
      "owner_name": "张三",
      "members_count": 1,
      "tasks_count": 0,
      "created_at": "2026-06-29T14:00:00Z",
      "updated_at": "2026-06-29T14:00:00Z"
    }
  ]
}
```

### 4.2 创建项目

请求方法：`POST`

接口路径：`/project`

完整地址：

- 本地：`POST http://localhost:8080/api/v1/project`
- 云端：`POST https://oa-school-api.onrender.com/api/v1/project`

是否需要登录：是

请求示例：

```json
{
  "name": "校园 OA 协作系统",
  "description": "面向课程小组的任务协作和 AI 审查系统"
}
```

返回示例：

```json
{
  "project": {
    "id": "2a6d3c7f-80d5-41b4-a908-7f3d6e0d0001",
    "group_id": "A7K29QX",
    "name": "校园 OA 协作系统",
    "description": "面向课程小组的任务协作和 AI 审查系统",
    "requirement_text": "",
    "owner_id": "4ff5b5c1-77c6-4f4a-a8cb-6b84b8a4d111",
    "project_progress": 0.0,
    "created_at": "2026-06-29T14:00:00Z",
    "updated_at": "2026-06-29T14:00:00Z"
  }
}
```

说明：创建者会自动成为项目组长，系统会生成 7 位 `group_id`。

### 4.3 入组预览

请求方法：`GET`

接口路径：`/project/join-preview/{groupId}`

完整地址：

- 本地：`GET http://localhost:8080/api/v1/project/join-preview/{groupId}`
- 云端：`GET https://oa-school-api.onrender.com/api/v1/project/join-preview/{groupId}`

是否需要登录：是

请求示例：

```bash
curl -H "Authorization: Bearer <token>" http://localhost:8080/api/v1/project/join-preview/A7K29QX
```

返回示例：

```json
{
  "preview": {
    "id": "2a6d3c7f-80d5-41b4-a908-7f3d6e0d0001",
    "group_id": "A7K29QX",
    "name": "校园 OA 协作系统",
    "owner_name": "张三",
    "members_count": 1
  }
}
```

兼容路径：`GET /project/info/{groupId}`

### 4.4 加入项目

请求方法：`POST`

接口路径：`/project/join`

完整地址：

- 本地：`POST http://localhost:8080/api/v1/project/join`
- 云端：`POST https://oa-school-api.onrender.com/api/v1/project/join`

是否需要登录：是

请求示例：

```json
{
  "groupId": "A7K29QX"
}
```

返回示例：

```json
{
  "project": {
    "id": "2a6d3c7f-80d5-41b4-a908-7f3d6e0d0001",
    "group_id": "A7K29QX",
    "name": "校园 OA 协作系统",
    "member_role": "member"
  },
  "members": [
    {
      "id": "4ff5b5c1-77c6-4f4a-a8cb-6b84b8a4d111",
      "username": "20260001",
      "name": "张三",
      "member_role": "leader",
      "skills": []
    }
  ],
  "tasks": [],
  "dashboard": {
    "progress": 0.0,
    "statusCounts": [],
    "contribution": [],
    "burnDown": []
  }
}
```

说明：用户至少需要有一个已验证技能，否则返回 `409 SKILL_VERIFICATION_REQUIRED`。

### 4.5 获取项目详情

请求方法：`GET`

接口路径：`/project/{id}`

完整地址：

- 本地：`GET http://localhost:8080/api/v1/project/{id}`
- 云端：`GET https://oa-school-api.onrender.com/api/v1/project/{id}`

是否需要登录：是

请求示例：

```bash
curl -H "Authorization: Bearer <token>" http://localhost:8080/api/v1/project/2a6d3c7f-80d5-41b4-a908-7f3d6e0d0001
```

返回示例：

```json
{
  "project": {
    "id": "2a6d3c7f-80d5-41b4-a908-7f3d6e0d0001",
    "group_id": "A7K29QX",
    "name": "校园 OA 协作系统",
    "member_role": "leader",
    "project_progress": 35.5
  },
  "members": [
    {
      "id": "4ff5b5c1-77c6-4f4a-a8cb-6b84b8a4d111",
      "username": "20260001",
      "name": "张三",
      "member_role": "leader",
      "joined_at": "2026-06-29T14:00:00Z",
      "skills": [
        {
          "skillTag": "Java",
          "selfLevel": 4,
          "isVerified": true,
          "dynamicScore": 2.0
        }
      ]
    }
  ],
  "tasks": [
    {
      "id": "b26897d5-2010-4d9f-91cc-f3b840120001",
      "project_id": "2a6d3c7f-80d5-41b4-a908-7f3d6e0d0001",
      "title": "实现登录注册",
      "description": "完成账号注册、登录和 JWT 鉴权",
      "category": "Java",
      "status": "todo",
      "assignee_id": "4ff5b5c1-77c6-4f4a-a8cb-6b84b8a4d111",
      "assignee_name": "张三",
      "features_json": ["注册可用", "登录可用"],
      "estimated_days": 1.5,
      "residual_progress": 0.0,
      "ai_review_status": "pending",
      "ai_review_comment": null
    }
  ],
  "dashboard": {
    "progress": 35.5,
    "statusCounts": [
      {
        "status": "todo",
        "count": 3
      }
    ],
    "contribution": [],
    "burnDown": [
      {
        "name": "T1",
        "remaining": 5.0
      }
    ]
  }
}
```

说明：组长可看到项目全部任务，普通成员只看到分配给自己的任务。

### 4.6 获取项目看板数据

请求方法：`GET`

接口路径：`/project/{id}/dashboard`

完整地址：

- 本地：`GET http://localhost:8080/api/v1/project/{id}/dashboard`
- 云端：`GET https://oa-school-api.onrender.com/api/v1/project/{id}/dashboard`

是否需要登录：是

请求示例：

```bash
curl -H "Authorization: Bearer <token>" http://localhost:8080/api/v1/project/2a6d3c7f-80d5-41b4-a908-7f3d6e0d0001/dashboard
```

返回示例：

```json
{
  "dashboard": {
    "progress": 35.5,
    "statusCounts": [
      {
        "status": "done",
        "count": 2
      }
    ],
    "contribution": [
      {
        "name": "张三",
        "value": 3.0
      }
    ],
    "burnDown": [
      {
        "name": "T1",
        "remaining": 4.5
      }
    ]
  }
}
```

### 4.7 生成 WBS 任务

请求方法：`POST`

接口路径：`/project/{id}/wbs`

完整地址：

- 本地：`POST http://localhost:8080/api/v1/project/{id}/wbs`
- 云端：`POST https://oa-school-api.onrender.com/api/v1/project/{id}/wbs`

是否需要登录：是，仅组长可用

请求示例：

```json
{
  "requirementText": "开发一个校园小组协作 OA 系统，包含注册登录、项目创建、任务拆解、看板流转和代码审查。"
}
```

返回示例：

```json
{
  "tasks": [
    {
      "id": "b26897d5-2010-4d9f-91cc-f3b840120001",
      "project_id": "2a6d3c7f-80d5-41b4-a908-7f3d6e0d0001",
      "title": "实现认证模块",
      "description": "完成注册、登录、JWT 鉴权和设备会话管理",
      "category": "Java",
      "features_json": ["注册可用", "登录可用", "JWT 鉴权可用"],
      "estimated_days": 2.0,
      "assignee_id": "4ff5b5c1-77c6-4f4a-a8cb-6b84b8a4d111",
      "status": "todo"
    }
  ]
}
```

说明：

- `requirementText` 长度范围为 10 到 12000
- 最多写入 24 个任务
- 该接口会调用 AI 服务；未配置大模型 Key 时使用本地降级逻辑

### 4.8 删除项目

请求方法：`DELETE`

接口路径：`/project/{id}`

完整地址：

- 本地：`DELETE http://localhost:8080/api/v1/project/{id}`
- 云端：`DELETE https://oa-school-api.onrender.com/api/v1/project/{id}`

是否需要登录：是，仅组长可用

请求示例：

```bash
curl -X DELETE -H "Authorization: Bearer <token>" http://localhost:8080/api/v1/project/2a6d3c7f-80d5-41b4-a908-7f3d6e0d0001
```

返回示例：

```http
204 No Content
```

## 5. AI WBS 接口

### 5.1 通过 JSON 生成 WBS

请求方法：`POST`

接口路径：`/ai/wbs-generate`

完整地址：

- 本地：`POST http://localhost:8080/api/v1/ai/wbs-generate`
- 云端：`POST https://oa-school-api.onrender.com/api/v1/ai/wbs-generate`

是否需要登录：是，仅项目组长可用

请求示例：

```json
{
  "projectId": "2a6d3c7f-80d5-41b4-a908-7f3d6e0d0001",
  "requirementText": "开发一个校园小组协作 OA 系统，包含注册登录、项目创建、任务拆解、看板流转和代码审查。"
}
```

返回示例：

```json
{
  "tasks": [
    {
      "id": "b26897d5-2010-4d9f-91cc-f3b840120001",
      "title": "实现任务看板",
      "category": "Vue",
      "features_json": ["任务列表展示", "状态流转", "负责人显示"],
      "status": "todo"
    }
  ]
}
```

说明：也支持请求字段 `project_id`。

### 5.2 通过文件生成 WBS

请求方法：`POST`

接口路径：`/ai/wbs-generate`

完整地址：

- 本地：`POST http://localhost:8080/api/v1/ai/wbs-generate`
- 云端：`POST https://oa-school-api.onrender.com/api/v1/ai/wbs-generate`

请求类型：`multipart/form-data`

是否需要登录：是，仅项目组长可用

请求示例：

```bash
curl -X POST http://localhost:8080/api/v1/ai/wbs-generate \
  -H "Authorization: Bearer <token>" \
  -F "projectId=2a6d3c7f-80d5-41b4-a908-7f3d6e0d0001" \
  -F "requirementText=课程设计项目" \
  -F "file=@requirements.docx"
```

返回示例：

```json
{
  "tasks": [
    {
      "id": "b26897d5-2010-4d9f-91cc-f3b840120001",
      "title": "实现源码审查",
      "category": "Java",
      "estimated_days": 2.0,
      "status": "todo"
    }
  ]
}
```

说明：

- 文件最大 8MB
- 支持 `.txt`、`.md`、`.markdown`、`.docx`、`.pdf`

## 6. 技能画像

### 6.1 获取技能列表

请求方法：`GET`

接口路径：`/skills`

完整地址：

- 本地：`GET http://localhost:8080/api/v1/skills`
- 云端：`GET https://oa-school-api.onrender.com/api/v1/skills`

是否需要登录：是

请求示例：

```bash
curl -H "Authorization: Bearer <token>" http://localhost:8080/api/v1/skills
```

返回示例：

```json
{
  "skills": [
    {
      "id": "fc42217c-f13b-4990-a5d2-889978450001",
      "skill_tag": "Java",
      "self_level": 4,
      "is_verified": true,
      "ai_score": 85,
      "ai_comment": "回答完整，具备独立开发能力。",
      "dynamic_score": 2.0,
      "updated_at": "2026-06-29T14:00:00Z"
    }
  ]
}
```

### 6.2 新增或更新技能

请求方法：`POST`

接口路径：`/skills`

完整地址：

- 本地：`POST http://localhost:8080/api/v1/skills`
- 云端：`POST https://oa-school-api.onrender.com/api/v1/skills`

是否需要登录：是

请求示例：

```json
{
  "skillTag": "Java",
  "selfLevel": 4
}
```

返回示例：

```json
{
  "skill": {
    "id": "fc42217c-f13b-4990-a5d2-889978450001",
    "user_id": "4ff5b5c1-77c6-4f4a-a8cb-6b84b8a4d111",
    "skill_tag": "Java",
    "self_level": 4,
    "is_verified": false,
    "ai_score": null,
    "ai_comment": null,
    "dynamic_score": 0.0,
    "created_at": "2026-06-29T14:00:00Z",
    "updated_at": "2026-06-29T14:00:00Z"
  }
}
```

说明：

- 新增返回 `201`
- 已存在同名技能时更新并返回 `200`
- `selfLevel` 范围为 1 到 5

### 6.3 生成技能试卷

请求方法：`POST`

接口路径：`/skills/{id}/quiz`

完整地址：

- 本地：`POST http://localhost:8080/api/v1/skills/{id}/quiz`
- 云端：`POST https://oa-school-api.onrender.com/api/v1/skills/{id}/quiz`

是否需要登录：是

请求示例：

```bash
curl -X POST -H "Authorization: Bearer <token>" http://localhost:8080/api/v1/skills/fc42217c-f13b-4990-a5d2-889978450001/quiz
```

返回示例：

```json
{
  "skill": {
    "id": "fc42217c-f13b-4990-a5d2-889978450001",
    "skill_tag": "Java",
    "self_level": 4,
    "is_verified": false
  },
  "questions": [
    {
      "id": "q1-demo",
      "title": "Java 模块设计",
      "prompt": "请为一个小组项目模块设计 Java 方案，说明边界、接口和协作方式。"
    }
  ],
  "generatedAt": "2026-06-29T14:00:00Z"
}
```

### 6.4 提交技能阅卷

请求方法：`POST`

接口路径：`/skills/{id}/verify`

完整地址：

- 本地：`POST http://localhost:8080/api/v1/skills/{id}/verify`
- 云端：`POST https://oa-school-api.onrender.com/api/v1/skills/{id}/verify`

是否需要登录：是

请求示例：

```json
{
  "answers": [
    {
      "questionId": "q1-demo",
      "answer": "我会先划分 Controller、Service、Repository，并通过 DTO 约束接口边界。"
    }
  ]
}
```

返回示例：

```json
{
  "skill": {
    "id": "fc42217c-f13b-4990-a5d2-889978450001",
    "skill_tag": "Java",
    "self_level": 4,
    "is_verified": true,
    "ai_score": 85,
    "ai_comment": "回答完整，具备独立开发能力。",
    "dynamic_score": 0.0,
    "updated_at": "2026-06-29T14:00:00Z"
  }
}
```

说明：`ai_score >= 60` 时技能会标记为已验证。

### 6.5 删除技能

请求方法：`DELETE`

接口路径：`/skills/{id}`

完整地址：

- 本地：`DELETE http://localhost:8080/api/v1/skills/{id}`
- 云端：`DELETE https://oa-school-api.onrender.com/api/v1/skills/{id}`

是否需要登录：是

请求示例：

```bash
curl -X DELETE -H "Authorization: Bearer <token>" http://localhost:8080/api/v1/skills/fc42217c-f13b-4990-a5d2-889978450001
```

返回示例：

```http
204 No Content
```

## 7. 任务流转与源码提交

`/api/v1/task` 和 `/api/v1/tasks` 均可使用，以下以 `/task` 为例。

### 7.1 修改任务状态或负责人

请求方法：`PATCH`

接口路径：`/task/{id}`

完整地址：

- 本地：`PATCH http://localhost:8080/api/v1/task/{id}`
- 云端：`PATCH https://oa-school-api.onrender.com/api/v1/task/{id}`

是否需要登录：是

请求示例：修改负责人

```json
{
  "assigneeId": "4ff5b5c1-77c6-4f4a-a8cb-6b84b8a4d111",
  "reason": "按 Java 技能匹配重新分配"
}
```

请求示例：修改状态

```json
{
  "status": "todo"
}
```

返回示例：

```json
{
  "task": {
    "id": "b26897d5-2010-4d9f-91cc-f3b840120001",
    "title": "实现认证模块",
    "status": "todo",
    "assignee_id": "4ff5b5c1-77c6-4f4a-a8cb-6b84b8a4d111",
    "assignee_name": "张三",
    "updated_at": "2026-06-29T14:00:00Z"
  }
}
```

说明：

- 状态值：`todo`、`in_progress`、`review`、`done`
- 从 `todo` 到 `in_progress` 需要使用派发接口
- `review` 和 `done` 不能直接通过 PATCH 设置，需要走源码提交和 AI 审查流程
- 进行中的任务修改负责人时必须传 `reason`

### 7.2 派发单个任务

请求方法：`POST`

接口路径：`/task/{id}/dispatch`

完整地址：

- 本地：`POST http://localhost:8080/api/v1/task/{id}/dispatch`
- 云端：`POST https://oa-school-api.onrender.com/api/v1/task/{id}/dispatch`

是否需要登录：是，仅组长可用

请求示例：

```bash
curl -X POST -H "Authorization: Bearer <token>" http://localhost:8080/api/v1/task/b26897d5-2010-4d9f-91cc-f3b840120001/dispatch
```

返回示例：

```json
{
  "task": {
    "id": "b26897d5-2010-4d9f-91cc-f3b840120001",
    "status": "in_progress",
    "assignee_id": "4ff5b5c1-77c6-4f4a-a8cb-6b84b8a4d111"
  },
  "progress": 0.0
}
```

说明：任务必须是 `todo`，且已有负责人。

### 7.3 批量派发项目任务

请求方法：`POST`

接口路径：`/task/dispatch`

完整地址：

- 本地：`POST http://localhost:8080/api/v1/task/dispatch`
- 云端：`POST https://oa-school-api.onrender.com/api/v1/task/dispatch`

是否需要登录：是，仅组长可用

请求示例：

```json
{
  "projectId": "2a6d3c7f-80d5-41b4-a908-7f3d6e0d0001"
}
```

返回示例：

```json
{
  "dispatched": 3,
  "progress": 0.0
}
```

说明：也支持请求字段 `project_id`。

### 7.4 提交源码审查

请求方法：`POST`

接口路径：`/task/{id}/submit`

完整地址：

- 本地：`POST http://localhost:8080/api/v1/task/{id}/submit`
- 云端：`POST https://oa-school-api.onrender.com/api/v1/task/{id}/submit`

是否需要登录：是，仅任务负责人可用

请求示例：

```json
{
  "repoUrl": "https://github.com/example/oa-school-demo.git"
}
```

返回示例：

```json
{
  "submission": {
    "id": "0d15eb28-bb80-4129-bd64-163a01d40001",
    "task_id": "b26897d5-2010-4d9f-91cc-f3b840120001",
    "user_id": "4ff5b5c1-77c6-4f4a-a8cb-6b84b8a4d111",
    "repo_url": "https://github.com/example/oa-school-demo.git",
    "submit_type": "GITHUB",
    "ai_passed": true,
    "ai_score": 90,
    "ai_comment": "通过，功能点实现完整。",
    "residual_progress": 100.0,
    "created_at": "2026-06-29T14:00:00Z"
  },
  "task": {
    "id": "b26897d5-2010-4d9f-91cc-f3b840120001",
    "status": "done",
    "ai_review_status": "passed",
    "ai_review_comment": "通过，功能点实现完整。",
    "residual_progress": 100.0
  },
  "progress": 60.0
}
```

说明：

- 支持 GitHub 和 Gitee 仓库链接
- 任务必须处于 `in_progress`
- AI 审查通过后任务变为 `done`
- 审查不通过时任务回到 `in_progress`，并写入 `ai_review_comment`

### 7.5 直接提交源码审查

请求方法：`POST`

接口路径：`/task/submit`

完整地址：

- 本地：`POST http://localhost:8080/api/v1/task/submit`
- 云端：`POST https://oa-school-api.onrender.com/api/v1/task/submit`

是否需要登录：是，仅任务负责人可用

请求示例：

```json
{
  "taskId": "b26897d5-2010-4d9f-91cc-f3b840120001",
  "sourceUrl": "https://gitee.com/example/oa-school-demo.git"
}
```

返回示例：

```json
{
  "submission": {
    "id": "0d15eb28-bb80-4129-bd64-163a01d40001",
    "submit_type": "GITEE",
    "ai_passed": true,
    "ai_score": 90
  },
  "task": {
    "id": "b26897d5-2010-4d9f-91cc-f3b840120001",
    "status": "done"
  },
  "progress": 60.0
}
```

说明：

- `taskId` 也可写为 `task_id`
- `sourceUrl` 也可写为 `repoUrl`

## 8. WebSocket 实时消息

### 8.1 建立连接

连接地址：

- 本地：`ws://localhost:8080/ws/hub?token=<token>`
- 云端：`wss://oa-school-api.onrender.com/ws/hub?token=<token>`

认证方式：查询参数 `token`，值为登录或注册返回的 JWT。

连接示例：

```js
const ws = new WebSocket("ws://localhost:8080/ws/hub?token=" + token);
```

### 8.2 加入项目房间

客户端发送：

```json
{
  "type": "JOIN_PROJECT",
  "projectId": "2a6d3c7f-80d5-41b4-a908-7f3d6e0d0001"
}
```

说明：只有项目成员能加入项目房间。

### 8.3 服务端推送：指标更新

触发场景：WBS 生成、任务派发、任务状态变化、源码审查完成。

返回示例：

```json
{
  "type": "METRICS_UPDATED",
  "projectId": "2a6d3c7f-80d5-41b4-a908-7f3d6e0d0001",
  "dashboard": {
    "progress": 60.0,
    "statusCounts": [
      {
        "status": "done",
        "count": 3
      }
    ],
    "contribution": [
      {
        "name": "张三",
        "value": 3.0
      }
    ],
    "burnDown": [
      {
        "name": "T1",
        "remaining": 4.0
      }
    ]
  }
}
```

### 8.4 服务端推送：任务被 AI 打回

返回示例：

```json
{
  "type": "TASK_REJECTED",
  "projectId": "2a6d3c7f-80d5-41b4-a908-7f3d6e0d0001",
  "taskId": "b26897d5-2010-4d9f-91cc-f3b840120001",
  "comment": "登录接口缺少失败状态处理，请补充。"
}
```

### 8.5 服务端推送：强制下线

触发场景：当前账号的某个设备会话被删除。

返回示例：

```json
{
  "type": "FORCE_LOGOUT",
  "reason": "该设备已被下线"
}
```

## 9. 典型调用流程

### 本地流程

1. `POST http://localhost:8080/api/v1/auth/register`
2. 保存返回的 `token`
3. `POST http://localhost:8080/api/v1/project`
4. `POST http://localhost:8080/api/v1/skills`
5. `POST http://localhost:8080/api/v1/skills/{id}/quiz`
6. `POST http://localhost:8080/api/v1/skills/{id}/verify`
7. `POST http://localhost:8080/api/v1/ai/wbs-generate`
8. `POST http://localhost:8080/api/v1/task/dispatch`
9. `POST http://localhost:8080/api/v1/task/submit`

### 云端流程

1. `POST https://oa-school-api.onrender.com/api/v1/auth/register`
2. 保存返回的 `token`
3. `POST https://oa-school-api.onrender.com/api/v1/project`
4. `POST https://oa-school-api.onrender.com/api/v1/skills`
5. `POST https://oa-school-api.onrender.com/api/v1/skills/{id}/quiz`
6. `POST https://oa-school-api.onrender.com/api/v1/skills/{id}/verify`
7. `POST https://oa-school-api.onrender.com/api/v1/ai/wbs-generate`
8. `POST https://oa-school-api.onrender.com/api/v1/task/dispatch`
9. `POST https://oa-school-api.onrender.com/api/v1/task/submit`
