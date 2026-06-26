<template>
  <main v-if="!user" class="auth-screen">
    <section class="auth-panel">
      <div class="brand-mark">
        <el-icon class="brand-icon"><Grid /></el-icon>
        <div>
          <h1>校园小组协同 OA</h1>
          <p>AI 驱动的小组项目工作台</p>
        </div>
      </div>

      <el-segmented v-model="authMode" :options="authOptions" block />
      <el-form class="auth-form" label-position="top" @submit.prevent="submitAuth">
        <el-form-item label="学号/账号">
          <el-input v-model="authForm.username" autocomplete="username" />
        </el-form-item>
        <el-form-item v-if="authMode === 'register'" label="姓名">
          <el-input v-model="authForm.name" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="authForm.password" type="password" show-password autocomplete="current-password" />
        </el-form-item>
        <el-alert v-if="authError" :title="authError" type="error" show-icon :closable="false" />
        <el-button type="primary" native-type="submit" :loading="busy" :icon="authMode === 'login' ? Key : UserFilled">
          {{ authMode === "login" ? "登录" : "创建账号" }}
        </el-button>
      </el-form>
    </section>
  </main>

  <div v-else class="app-shell">
    <aside class="sidebar">
      <div class="side-brand">
        <el-icon><Grid /></el-icon>
        <div>
          <strong>OA School</strong>
          <span>Campus Suite</span>
        </div>
      </div>
      <nav class="side-nav">
        <button :class="{ active: view === 'projects' }" @click="view = 'projects'"><el-icon><Folder /></el-icon>项目</button>
        <button :class="{ active: view === 'skills' }" @click="view = 'skills'"><el-icon><Medal /></el-icon>技能</button>
        <button :class="{ active: view === 'sessions' }" @click="view = 'sessions'"><el-icon><Monitor /></el-icon>设备</button>
      </nav>
    </aside>

    <section class="main-area">
      <header class="topbar">
        <div class="topbar-title">
          <span>{{ topSubtitle }}</span>
          <strong>{{ topTitle }}</strong>
        </div>
        <div class="top-actions">
          <el-tag v-if="view === 'workspace' && workspace" :type="isProjectLeader ? 'primary' : 'info'">
            {{ isProjectLeader ? "组长" : "组员" }}
          </el-tag>
          <el-button :icon="Refresh" @click="refreshAll">刷新</el-button>
          <el-button :icon="SwitchButton" @click="logout">退出</el-button>
        </div>
      </header>

      <section v-if="view === 'projects'" class="workspace-grid project-center">
        <el-card class="full option-card" shadow="never">
          <template #header><PanelTitle :icon="Folder" title="项目操作" /></template>
          <div class="action-switcher">
            <button :class="{ active: projectAction === 'create' }" @click="toggleProjectAction('create')">
              <span class="option-icon"><el-icon><Plus /></el-icon></span>
              <span>
                <strong>创建项目</strong>
                <small>生成小组号</small>
              </span>
            </button>
            <button :class="{ active: projectAction === 'join' }" @click="toggleProjectAction('join')">
              <span class="option-icon"><el-icon><Search /></el-icon></span>
              <span>
                <strong>加入小组</strong>
                <small>输入小组号</small>
              </span>
            </button>
          </div>

          <el-collapse-transition>
            <el-form v-show="projectAction === 'create'" class="fold-form" label-position="top" @submit.prevent="createProject">
              <el-form-item label="项目名称"><el-input v-model="projectForm.name" /></el-form-item>
              <el-form-item label="项目说明"><el-input v-model="projectForm.description" type="textarea" :rows="3" /></el-form-item>
              <el-button type="primary" :icon="Plus" :loading="isActionBusy('createProject')" @click="createProject">创建</el-button>
            </el-form>
          </el-collapse-transition>

          <el-collapse-transition>
            <div v-show="projectAction === 'join'" class="fold-form">
              <div class="inline-form">
                <el-input v-model="joinCode" placeholder="输入小组号" @blur="previewGroup" />
                <el-button :icon="Search" @click="previewGroup">预检</el-button>
              </div>
              <div v-if="preview" class="preview-band">
                <div>
                  <strong>{{ preview.name }}</strong>
                  <span>组长：{{ preview.owner_name }}</span>
                  <span>当前人数：{{ preview.members_count }}</span>
                </div>
                <el-button type="primary" :icon="UserFilled" :loading="isActionBusy('joinGroup')" @click="joinGroup">确认加入</el-button>
              </div>
            </div>
          </el-collapse-transition>
        </el-card>

        <el-card class="full project-list-card" shadow="never">
          <template #header><PanelTitle :icon="Folder" title="我的项目" /></template>
          <div class="project-list">
            <button v-for="project in projects" :key="project.id" class="project-row" @click="loadWorkspace(project.id)">
              <div>
                <strong>{{ project.name }}</strong>
                <span>{{ project.description || "未填写说明" }}</span>
              </div>
              <div class="project-meta">
                <span class="code">{{ project.group_id }}</span>
                <span>{{ project.members_count }} 人</span>
                <span>{{ Number(project.project_progress || 0).toFixed(0) }}%</span>
              </div>
            </button>
            <el-empty v-if="projects.length === 0" description="暂无项目" />
          </div>
        </el-card>
      </section>

      <section v-if="view === 'skills'" class="skill-workspace" :class="{ 'has-quiz': quiz || quizGeneratingSkill }">
        <el-card class="skill-panel" shadow="never">
          <template #header><PanelTitle :icon="Medal" title="技能画像" /></template>
          <div class="skill-overview">
            <div class="skill-stat">
              <strong>{{ skills.length }}</strong>
              <span>技能标签</span>
            </div>
            <div class="skill-stat">
              <strong>{{ verifiedSkillsCount }}</strong>
              <span>已核验</span>
            </div>
            <div class="skill-stat">
              <strong>{{ averageSkillLevel }}</strong>
              <span>平均自评</span>
            </div>
            <el-button type="primary" plain :icon="Plus" @click="toggleSkillCreator">
              {{ skillCreatorOpen ? "收起添加" : "添加技能" }}
            </el-button>
          </div>

          <el-collapse-transition>
            <div v-show="skillCreatorOpen" class="skill-editor">
              <div class="skill-picker">
                <div class="skill-picker-head">
                  <div>
                    <strong>选择技能标签</strong>
                    <span>{{ skillSearch.trim() ? "搜索结果" : "常用标签" }}</span>
                  </div>
                  <el-input v-model="skillSearch" class="skill-search" clearable placeholder="搜索技术栈，如 Docker / React / Redis" />
                </div>
                <div class="skill-tag-options">
                  <button
                    v-for="tag in visibleSkillTags"
                    :key="tag"
                    type="button"
                    :class="{ active: skillForm.skillTag === tag }"
                    @click="chooseSkillTag(tag)"
                  >
                    {{ tag }}
                  </button>
                  <button v-if="canUseSkillSearch" type="button" class="custom" @click="chooseSkillTag(skillSearch.trim())">
                    {{ skillSearch.trim() }}
                  </button>
                </div>
                <p v-if="skillSearch.trim() && visibleSkillTags.length === 0 && !canUseSkillSearch" class="skill-search-empty">没有匹配的技术栈</p>
              </div>

              <el-collapse-transition>
                <div v-show="skillForm.skillTag" class="skill-level-panel">
                  <div class="skill-level-title">
                    <div>
                      <strong>{{ skillForm.skillTag }}</strong>
                      <span>选择你当前最接近的掌握程度</span>
                    </div>
                    <b>{{ skillForm.selfLevel }}/5</b>
                  </div>
                  <el-rate v-model="skillForm.selfLevel" />
                  <p>{{ skillLevelTip(skillForm.selfLevel) }}</p>
                  <el-button type="primary" :icon="Plus" :loading="isActionBusy('saveSkill')" @click="saveSkill">保存技能</el-button>
                </div>
              </el-collapse-transition>
            </div>
          </el-collapse-transition>

          <div class="skill-card-grid">
            <article v-for="skill in skills" :key="skill.id" class="skill-card">
              <div class="skill-card-top">
                <span class="skill-avatar">{{ skillInitial(skill.skill_tag) }}</span>
                <div>
                  <strong>{{ skill.skill_tag }}</strong>
                  <span>{{ skill.is_verified ? "AI 已核验" : "等待核验" }}</span>
                </div>
                <el-tag :type="skill.is_verified ? 'success' : 'warning'">{{ skill.is_verified ? "金标" : "待核验" }}</el-tag>
              </div>

              <div class="skill-meter">
                <div>
                  <span>自评等级</span>
                  <b>{{ skillLevelValue(skill) }}/5</b>
                </div>
                <i><em :style="{ width: skillLevelPercent(skillLevelValue(skill)) }" /></i>
              </div>

              <div class="skill-comment-list">
                <p v-for="line in skillCommentLines(skill)" :key="line">{{ line }}</p>
              </div>

              <div class="skill-card-actions">
                <div class="skill-level-edit" :class="{ 'is-saving': isSkillLevelSaving(skill.id) }">
                  <el-rate
                    :model-value="skillLevelValue(skill)"
                    :clearable="false"
                    :disabled="isSkillInteractionLocked(skill)"
                    @change="updateSkillLevel(skill, $event)"
                  />
                  <span v-if="isSkillLevelSaving(skill.id)" class="level-saving-dot" aria-label="星级保存中" />
                </div>
                <el-button
                  type="primary"
                  plain
                  :icon="MagicStick"
                  :loading="isActionBusy(`quiz:${skill.id}`)"
                  :disabled="isSkillQuizDisabled(skill)"
                  @click="openQuiz(skill)"
                >
                  核验
                </el-button>
              </div>
            </article>
            <el-empty v-if="skills.length === 0" class="skill-empty" description="暂无技能标签" />
          </div>
        </el-card>

        <el-card v-if="quiz || quizGeneratingSkill" class="quiz-card is-open" shadow="never">
          <template #header>
            <div class="quiz-titlebar">
              <PanelTitle :icon="DocumentChecked" title="AI 试卷" />
              <el-button v-if="quiz" text :icon="CloseBold" @click="quiz = null">收起</el-button>
            </div>
          </template>
          <div v-if="quizGeneratingSkill" class="quiz-loading-panel" aria-live="polite">
            <span class="quiz-loading-spinner" aria-hidden="true"><i /></span>
            <strong>AI 正在生成 {{ quizGeneratingSkill.skill_tag }} {{ skillLevelValue(quizGeneratingSkill) }} 星试卷</strong>
            <span>根据当前星级调整题目难度，请稍候。</span>
          </div>
          <div v-else-if="quiz" class="quiz-stack">
            <div class="quiz-heading">
              <span>正在核验</span>
              <h3>{{ quiz.skill.skill_tag }}</h3>
            </div>
            <label v-for="question in quiz.questions" :key="question.id" class="quiz-question">
              <strong>{{ question.title }}</strong>
              <span>{{ question.prompt }}</span>
              <el-input v-model="answers[question.id]" type="textarea" :rows="4" />
            </label>
            <el-button type="primary" :icon="Promotion" :loading="isActionBusy('submitQuiz')" @click="submitQuiz">提交阅卷</el-button>
          </div>
        </el-card>
      </section>

      <section v-if="view === 'sessions'">
        <el-card shadow="never">
          <template #header><PanelTitle :icon="Monitor" title="在线设备" /></template>
          <article v-for="session in sessions" :key="session.id" class="session-row">
            <div>
              <strong>{{ session.device_id }}</strong>
              <span>{{ session.user_agent || "Unknown" }}</span>
            </div>
            <el-tag :type="session.current ? 'success' : 'info'">{{ session.current ? "当前" : "在线" }}</el-tag>
            <el-button v-if="!session.current" :icon="CloseBold" @click="kickSession(session.id)">下线</el-button>
          </article>
        </el-card>
      </section>

      <section v-if="view === 'workspace' && workspace" class="project-workspace">
        <div class="project-header">
          <div class="project-main">
            <div class="project-kicker">
              <span class="code">{{ workspace.project.group_id }}</span>
              <span>{{ isProjectLeader ? "组长工作台" : "成员工作台" }}</span>
            </div>
            <h1>{{ workspace.project.name }}</h1>
            <p>{{ workspace.project.description || "未填写说明" }}</p>
          </div>
          <div class="project-stats">
            <div class="workspace-stat">
              <span>总任务</span>
              <strong>{{ taskSummary.total }}</strong>
            </div>
            <div class="workspace-stat">
              <span>进行中</span>
              <strong>{{ taskSummary.inProgress }}</strong>
            </div>
            <div class="workspace-stat">
              <span>审查中</span>
              <strong>{{ taskSummary.review }}</strong>
            </div>
            <div class="progress-pill">
              <strong>{{ Number(workspace.dashboard.progress || 0).toFixed(0) }}%</strong>
              <span>进度</span>
            </div>
          </div>
        </div>

        <el-tabs v-model="workspaceTab" class="oa-tabs">
          <el-tab-pane label="敏捷看板" name="board">
            <div class="kanban">
              <section v-for="column in columns" :key="column.value" class="kanban-column" :class="`status-${column.value}`">
                <header>
                  <span>{{ column.label }}</span>
                  <div class="column-actions">
                    <el-button
                      v-if="column.value === 'todo' && isProjectLeader && dispatchableTasksCount() > 0"
                      size="small"
                      type="primary"
                      plain
                      :icon="Promotion"
                      :loading="busy"
                      @click="dispatchTodoTasks"
                    >
                      一键派发
                    </el-button>
                    <b>{{ tasksByStatus(column.value).length }}</b>
                  </div>
                </header>
                <article v-for="task in tasksByStatus(column.value)" :key="task.id" class="task-card" :class="{ collapsed: !isTaskOpen(task.id) }">
                  <button class="task-summary" type="button" @click="toggleTaskOpen(task.id)">
                    <span class="task-title">
                      <strong>{{ task.title }}</strong>
                      <small>{{ task.description || "未填写说明" }}</small>
                    </span>
                    <span class="task-summary-meta">
                      <el-tag size="small" type="info">{{ task.assignee_name || "未分配" }}</el-tag>
                      <el-tag size="small" :type="statusTag(task.status)">{{ column.label }}</el-tag>
                      <b>{{ isTaskOpen(task.id) ? "收起" : "展开" }}</b>
                    </span>
                  </button>

                  <el-collapse-transition>
                    <div v-show="isTaskOpen(task.id)" class="task-detail">
                      <div class="feature-list">
                        <span v-for="feature in task.features_json || []" :key="feature">{{ feature }}</span>
                      </div>
                      <div v-if="task.ai_review_status && task.ai_review_status !== 'pending'" class="review-result" :class="task.ai_review_status">
                        {{ reviewLabel(task) }}
                      </div>
                      <el-alert v-if="task.ai_review_comment" :type="reviewAlertType(task)" :title="task.ai_review_comment" show-icon :closable="false" />

                      <div v-if="isProjectLeader" class="task-controls assignee-controls">
                        <template v-if="canDirectEditAssignee(task)">
                          <el-select v-model="task.assignee_id" placeholder="负责人" @change="patchTask(task.id, { assigneeId: task.assignee_id || null })">
                            <el-option label="未分配" value="" />
                            <el-option v-for="member in workspace.members" :key="member.id" :label="member.name" :value="member.id" />
                          </el-select>
                          <el-button :icon="Promotion" :loading="busy" @click.stop="dispatchTask(task)">派发</el-button>
                        </template>

                        <template v-else-if="canRequestAssigneeChange(task)">
                          <div class="assignee-readonly">
                            <span>负责人</span>
                            <strong>{{ task.assignee_name || "未分配" }}</strong>
                          </div>
                          <el-button plain @click.stop="beginAssigneeChange(task)">修改</el-button>
                        </template>

                        <template v-else>
                          <div class="assignee-readonly locked">
                            <span>负责人</span>
                            <strong>{{ task.assignee_name || "未分配" }}</strong>
                            <small>{{ task.status === "done" ? "任务已完成，负责人已锁定" : "当前状态不可修改负责人" }}</small>
                          </div>
                        </template>
                      </div>
                      <div v-else class="task-controls compact-meta">
                        <el-tag type="info">{{ task.assignee_name || "未分配" }}</el-tag>
                      </div>

                      <el-collapse-transition>
                        <div v-show="assigneeEditTaskId === task.id" class="assignee-change-panel">
                          <el-select v-model="assigneeDrafts[task.id]" placeholder="选择新负责人">
                            <el-option label="未分配" value="" />
                            <el-option v-for="member in workspace.members" :key="member.id" :label="member.name" :value="member.id" />
                          </el-select>
                          <el-input v-model="assigneeReasons[task.id]" type="textarea" :rows="2" maxlength="160" show-word-limit placeholder="说明修改负责人原因，如成员请假、任务拆分或技能更匹配" />
                          <div class="assignee-change-actions">
                            <el-button @click="cancelAssigneeChange">取消</el-button>
                            <el-button type="primary" :loading="isActionBusy(`assignee:${task.id}`)" @click="submitAssigneeChange(task)">确认修改</el-button>
                          </div>
                        </div>
                      </el-collapse-transition>

                      <div v-if="canSubmitTask(task)" class="submit-row">
                        <el-input v-model="repoInputs[task.id]" :disabled="task.status === 'review'" placeholder="https://github.com/owner/repo" />
                        <el-button type="primary" :icon="Upload" :loading="busy" :disabled="task.status === 'review'" @click="submitTask(task.id)">
                          {{ task.status === "review" ? "审查中" : "提交" }}
                        </el-button>
                      </div>
                    </div>
                  </el-collapse-transition>
                </article>
                <el-empty v-if="tasksByStatus(column.value).length === 0" class="column-empty" description="暂无任务" />
              </section>
            </div>
          </el-tab-pane>

          <el-tab-pane v-if="isProjectLeader" label="WBS 拆解" name="wbs">
            <el-card shadow="never">
              <div class="wbs-form">
                <el-input v-model="requirementText" type="textarea" :rows="10" placeholder="粘贴课程作业、竞赛题目或项目需求..." />
                <div class="wbs-toolbar">
                  <div class="file-actions">
                    <el-upload
                      action="#"
                      accept=".txt,.md,.markdown,.docx,.pdf"
                      :auto-upload="false"
                      :show-file-list="false"
                      :limit="1"
                      :on-change="handleRequirementFileChange"
                      :on-exceed="handleRequirementFileExceed"
                    >
                      <el-button :icon="Upload">添加文件</el-button>
                    </el-upload>
                    <div v-if="requirementFile" class="file-chip">
                      <el-icon><DocumentChecked /></el-icon>
                      <span>{{ requirementFile.name }}</span>
                      <el-tooltip content="移除文件" placement="top">
                        <el-button text circle :icon="CloseBold" aria-label="移除文件" @click.stop="clearRequirementFile" />
                      </el-tooltip>
                    </div>
                  </div>
                  <el-button type="primary" :icon="MagicStick" :loading="busy" @click="generateWbs">AI 拆解任务</el-button>
                </div>
              </div>
            </el-card>
          </el-tab-pane>

          <el-tab-pane label="数据大屏" name="dashboard">
            <div class="dashboard-grid">
              <el-card shadow="never"><template #header>总进度</template><div ref="ringRef" class="chart" /></el-card>
              <el-card shadow="never"><template #header>燃尽图</template><div ref="burnRef" class="chart" /></el-card>
              <el-card shadow="never"><template #header>贡献率</template><div ref="pieRef" class="chart" /></el-card>
            </div>
          </el-tab-pane>

          <el-tab-pane label="成员" name="members">
            <div class="member-grid">
              <article v-for="member in workspace.members" :key="member.id" class="member-row">
                <div><strong>{{ member.name }}</strong><span>{{ member.username }}</span></div>
                <el-tag :type="member.member_role === 'leader' ? 'primary' : 'info'">{{ member.member_role === "leader" ? "组长" : "组员" }}</el-tag>
                <div class="member-skills">
                  <span v-for="skill in member.skills || []" :key="skill.skillTag">{{ skill.skillTag }}{{ skill.isVerified ? " 金标" : "" }}</span>
                </div>
              </article>
            </div>
          </el-tab-pane>
        </el-tabs>
      </section>
    </section>
  </div>
</template>

<script setup lang="ts">
import * as echarts from "echarts";
import {
  CloseBold,
  DocumentChecked,
  Folder,
  Grid,
  Key,
  MagicStick,
  Medal,
  Monitor,
  Plus,
  Promotion,
  Refresh,
  Search,
  SwitchButton,
  Upload,
  UserFilled
} from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import { computed, defineComponent, h, nextTick, onMounted, ref, watch } from "vue";
import { api, getDeviceUuid, getToken, setToken, wsUrl } from "./api";

const PanelTitle = defineComponent({
  props: { icon: Object, title: String },
  setup(props) {
    return () =>
      h("div", { class: "panel-title" }, [
        props.icon ? h("span", { class: "panel-title-icon" }, [h(props.icon as any)]) : null,
        h("h2", props.title)
      ]);
  }
});

const columns = [
  { value: "todo", label: "待开始" },
  { value: "in_progress", label: "进行中" },
  { value: "review", label: "审查中" },
  { value: "done", label: "已完成" }
];

const authOptions = [
  { label: "登录", value: "login" },
  { label: "注册", value: "register" }
];

const commonSkillTags = ["Vue", "Java", "Spring Boot", "MySQL", "UI", "测试"];
const skillTagOptions = [
  "Vue",
  "Nuxt",
  "React",
  "Next.js",
  "Angular",
  "Svelte",
  "TypeScript",
  "JavaScript",
  "HTML",
  "CSS",
  "Sass",
  "Tailwind CSS",
  "Element Plus",
  "Ant Design",
  "ECharts",
  "Pinia",
  "Vuex",
  "Redux",
  "Vite",
  "Webpack",
  "Axios",
  "REST API",
  "GraphQL",
  "JWT",
  "Node.js",
  "Express",
  "NestJS",
  "Prisma",
  "Java",
  "Spring Boot",
  "Spring Security",
  "MyBatis",
  "JPA",
  "Hibernate",
  "Maven",
  "Gradle",
  "Python",
  "FastAPI",
  "Django",
  "Flask",
  "Go",
  "Gin",
  "C",
  "C++",
  "C#",
  ".NET",
  "Rust",
  "PHP",
  "Laravel",
  "MySQL",
  "PostgreSQL",
  "MariaDB",
  "Oracle",
  "SQL Server",
  "Redis",
  "MongoDB",
  "SQLite",
  "Elasticsearch",
  "Supabase",
  "RabbitMQ",
  "Kafka",
  "Docker",
  "Kubernetes",
  "Nginx",
  "Linux",
  "Shell",
  "Git",
  "GitHub Actions",
  "Jenkins",
  "Ansible",
  "Terraform",
  "Prometheus",
  "Grafana",
  "CI/CD",
  "AWS",
  "阿里云",
  "腾讯云",
  "微信小程序",
  "UniApp",
  "Flutter",
  "React Native",
  "Android",
  "iOS",
  "UI",
  "Figma",
  "Axure",
  "测试",
  "单元测试",
  "接口测试",
  "自动化测试",
  "性能测试",
  "安全测试",
  "TensorFlow",
  "PyTorch",
  "数据分析",
  "部署",
  "需求分析",
  "项目管理"
];

const user = ref<any>(null);
const view = ref("projects");
const busy = ref(false);
const actionBusy = ref("");
const authMode = ref("login");
const authError = ref("");
const authForm = ref({ username: "", name: "", password: "" });
const projects = ref<any[]>([]);
const skills = ref<any[]>([]);
const sessions = ref<any[]>([]);
const workspace = ref<any>(null);
const workspaceTab = ref("board");
const projectForm = ref({ name: "", description: "" });
const joinCode = ref("");
const preview = ref<any>(null);
const projectAction = ref<"" | "create" | "join">("");
const skillForm = ref({ skillTag: "", selfLevel: 3 });
const skillCreatorOpen = ref(false);
const skillSearch = ref("");
const quiz = ref<any>(null);
const quizGeneratingSkill = ref<any>(null);
const answers = ref<Record<string, string>>({});
const skillLevelDrafts = ref<Record<string, number>>({});
const savingSkillLevelIds = ref<Record<string, boolean>>({});
const requirementText = ref("");
const requirementFile = ref<File | null>(null);
const repoInputs = ref<Record<string, string>>({});
const openTaskIds = ref<Record<string, boolean>>({});
const assigneeEditTaskId = ref("");
const assigneeDrafts = ref<Record<string, string>>({});
const assigneeReasons = ref<Record<string, string>>({});
const socket = ref<WebSocket | null>(null);
const ringRef = ref<HTMLDivElement | null>(null);
const burnRef = ref<HTMLDivElement | null>(null);
const pieRef = ref<HTMLDivElement | null>(null);
let ringChart: echarts.ECharts | null = null;
let burnChart: echarts.ECharts | null = null;
let pieChart: echarts.ECharts | null = null;

const isProjectLeader = computed(() => {
  const project = workspace.value?.project;
  if (!project || !user.value) return false;
  return project.member_role === "leader" || project.owner_id === user.value.id;
});

const topTitle = computed(() => {
  if (view.value === "workspace" && workspace.value) return workspace.value.project.name;
  if (view.value === "skills") return "技能画像";
  if (view.value === "sessions") return "在线设备";
  return "项目中心";
});

const topSubtitle = computed(() => {
  if (view.value === "workspace" && workspace.value) return `你好，${user.value?.name || ""}`;
  return `你好，${user.value?.name || ""}`;
});

const taskSummary = computed(() => {
  const tasks = workspace.value?.tasks || [];
  return {
    total: tasks.length,
    inProgress: tasks.filter((task: any) => task.status === "in_progress").length,
    review: tasks.filter((task: any) => task.status === "review").length,
    done: tasks.filter((task: any) => task.status === "done").length
  };
});

const verifiedSkillsCount = computed(() => skills.value.filter((skill) => skill.is_verified).length);

const averageSkillLevel = computed(() => {
  if (!skills.value.length) return "0.0";
  const total = skills.value.reduce((sum, skill) => sum + Number(skill.self_level || 0), 0);
  return (total / skills.value.length).toFixed(1);
});

const visibleSkillTags = computed(() => {
  const keyword = skillSearch.value.trim().toLowerCase();
  const source = keyword ? skillTagOptions : commonSkillTags;
  return source.filter((tag) => tag.toLowerCase().includes(keyword)).slice(0, keyword ? 24 : commonSkillTags.length);
});

const canUseSkillSearch = computed(() => {
  const value = skillSearch.value.trim();
  if (!value) return false;
  return !skillTagOptions.some((tag) => tag.toLowerCase() === value.toLowerCase());
});

function isActionBusy(key: string) {
  return actionBusy.value === key;
}

function toggleProjectAction(action: "create" | "join") {
  projectAction.value = projectAction.value === action ? "" : action;
}

function toggleSkillCreator() {
  if (skillCreatorOpen.value) {
    skillCreatorOpen.value = false;
    return;
  }
  skillForm.value = { skillTag: "", selfLevel: 3 };
  skillSearch.value = "";
  skillCreatorOpen.value = true;
}

function chooseSkillTag(tag: string) {
  skillForm.value.skillTag = tag;
  skillSearch.value = "";
}

async function submitAuth() {
  busy.value = true;
  authError.value = "";
  try {
    const payload = await api<any>(`/auth/${authMode.value}`, {
      method: "POST",
      body: { ...authForm.value, device_uuid: getDeviceUuid(), deviceId: getDeviceUuid() }
    });
    setToken(payload.token);
    user.value = payload.user;
    await refreshAll();
  } catch (error: any) {
    authError.value = error.message;
  } finally {
    busy.value = false;
  }
}

function logout() {
  socket.value?.close();
  setToken("");
  user.value = null;
  workspace.value = null;
}

async function refreshAll() {
  await Promise.all([loadProjects(), loadSkills(), loadSessions()]);
  if (workspace.value?.project?.id) await loadWorkspace(workspace.value.project.id);
}

async function loadProjects() {
  const payload = await api<any>("/project");
  projects.value = payload.projects || [];
}

async function loadSkills() {
  const payload = await api<any>("/skills");
  skills.value = payload.skills || [];
}

async function loadSessions() {
  const payload = await api<any>("/auth/sessions");
  sessions.value = payload.sessions || [];
}

async function createProject() {
  actionBusy.value = "createProject";
  try {
    const payload = await api<any>("/project", { method: "POST", body: projectForm.value });
    projectForm.value = { name: "", description: "" };
    await loadProjects();
    await loadWorkspace(payload.project.id);
    ElMessage.success("项目已创建");
  } catch (error: any) {
    ElMessage.error(error.message);
  } finally {
    actionBusy.value = "";
  }
}

async function previewGroup() {
  if (joinCode.value.trim().length < 4) return;
  try {
    const payload = await api<any>(`/project/info/${joinCode.value.trim().toUpperCase()}`);
    preview.value = payload.preview;
  } catch (error: any) {
    preview.value = null;
    ElMessage.error(error.message);
  }
}

async function joinGroup() {
  actionBusy.value = "joinGroup";
  try {
    const payload = await api<any>("/project/join", { method: "POST", body: { groupId: joinCode.value.trim() } });
    await loadProjects();
    workspace.value = payload;
    view.value = "workspace";
    ElMessage.success("已加入项目组");
  } catch (error: any) {
    ElMessage.error(error.message);
  } finally {
    actionBusy.value = "";
  }
}

function skillInitial(skillTag: string) {
  const value = String(skillTag || "技").trim();
  return value ? value.slice(0, 1).toUpperCase() : "技";
}

function normalizeSkillLevel(level: any, fallback = 1) {
  const value = Math.round(Number(level) || fallback);
  return Math.max(1, Math.min(5, value));
}

function skillLevelValue(skill: any) {
  const skillId = String(skill?.id || "");
  const draft = skillLevelDrafts.value[skillId];
  return normalizeSkillLevel(draft ?? skill?.self_level, 1);
}

function skillLevelPercent(level: any) {
  const value = Math.max(0, Math.min(5, Number(level) || 0));
  return `${value * 20}%`;
}

function skillLevelTip(level: any) {
  const tips = [
    "1 星：刚接触，能看懂基础概念，需要较多协助。",
    "2 星：能完成简单任务，遇到复杂问题还需要指导。",
    "3 星：能独立完成常规需求，知道常见问题怎么处理。",
    "4 星：熟练掌握，可以负责模块并帮助同学排查问题。",
    "5 星：非常熟练，可以做方案设计、优化和代码评审。"
  ];
  const index = Math.max(1, Math.min(5, Number(level) || 1)) - 1;
  return tips[index];
}

function skillCommentLines(skill: any) {
  const comment = String(skill.ai_comment || "").trim();
  if (!comment) return ["尚未核验，点击右侧按钮生成 AI 试卷。"];
  return comment
    .replace(/【/g, "\n【")
    .split(/\n+/)
    .map((line) => line.trim())
    .filter(Boolean)
    .slice(0, 3);
}

function isSkillLevelSaving(skillId: any) {
  return Boolean(savingSkillLevelIds.value[String(skillId || "")]);
}

function isSkillInteractionLocked(skill: any) {
  return Boolean(skill?.is_verified) || isSkillLevelSaving(skill?.id) || isActionBusy(`quiz:${skill?.id}`);
}

function isSkillQuizDisabled(skill: any) {
  return Boolean(quizGeneratingSkill.value) || isSkillLevelSaving(skill?.id) || isActionBusy("submitQuiz");
}

function clearSkillLevelDraft(skillId: string) {
  const nextDrafts = { ...skillLevelDrafts.value };
  delete nextDrafts[skillId];
  skillLevelDrafts.value = nextDrafts;
}

function setSkillLevelSaving(skillId: string, saving: boolean) {
  const nextSaving = { ...savingSkillLevelIds.value };
  if (saving) nextSaving[skillId] = true;
  else delete nextSaving[skillId];
  savingSkillLevelIds.value = nextSaving;
}

async function updateSkillLevel(skill: any, nextLevel: any) {
  if (!skill?.id || isSkillInteractionLocked(skill)) return;
  const skillId = String(skill.id);
  const level = normalizeSkillLevel(nextLevel, skillLevelValue(skill));
  const previousLevel = normalizeSkillLevel(skill.self_level, 1);
  if (level === previousLevel) {
    clearSkillLevelDraft(skillId);
    return;
  }

  skillLevelDrafts.value = { ...skillLevelDrafts.value, [skillId]: level };
  setSkillLevelSaving(skillId, true);

  try {
    const payload = await api<any>("/skills", {
      method: "POST",
      body: { skillTag: skill.skill_tag, selfLevel: level }
    });
    skills.value = skills.value.map((item) => (item.id === skill.id ? { ...item, ...(payload.skill || {}) } : item));
    clearSkillLevelDraft(skillId);
    if (quiz.value?.skill?.id === skill.id) {
      quiz.value = null;
      answers.value = {};
    }
    ElMessage.success("星级已更新，下一次核验会重新生成试卷");
  } catch (error: any) {
    clearSkillLevelDraft(skillId);
    ElMessage.error(error.message);
  } finally {
    setSkillLevelSaving(skillId, false);
  }
}

function isTaskOpen(taskId: string) {
  return Boolean(openTaskIds.value[taskId]);
}

function toggleTaskOpen(taskId: string) {
  openTaskIds.value = { ...openTaskIds.value, [taskId]: !openTaskIds.value[taskId] };
}

function canDirectEditAssignee(task: any) {
  return task.status === "todo";
}

function canRequestAssigneeChange(task: any) {
  return task.status === "in_progress";
}

function beginAssigneeChange(task: any) {
  assigneeEditTaskId.value = task.id;
  assigneeDrafts.value = { ...assigneeDrafts.value, [task.id]: task.assignee_id || "" };
  assigneeReasons.value = { ...assigneeReasons.value, [task.id]: "" };
}

function cancelAssigneeChange() {
  assigneeEditTaskId.value = "";
}

async function loadWorkspace(projectId: string) {
  const payload = await api<any>(`/project/${projectId}`);
  workspace.value = payload;
  syncTaskOpenState(payload.tasks || []);
  requirementText.value = payload.project.requirement_text || "";
  if (workspaceTab.value === "wbs" && payload.project.member_role !== "leader" && payload.project.owner_id !== user.value?.id) {
    workspaceTab.value = "board";
  }
  view.value = "workspace";
  connectHub(projectId);
  await nextTick();
  renderCharts();
}

async function saveSkill() {
  if (!skillForm.value.skillTag) {
    ElMessage.warning("请先选择技能标签");
    return;
  }
  actionBusy.value = "saveSkill";
  try {
    await api("/skills", { method: "POST", body: skillForm.value });
    skillForm.value = { skillTag: "", selfLevel: 3 };
    skillSearch.value = "";
    skillCreatorOpen.value = false;
    await loadSkills();
    ElMessage.success("技能已保存");
  } catch (error: any) {
    ElMessage.error(error.message);
  } finally {
    actionBusy.value = "";
  }
}

async function openQuiz(skill: any) {
  actionBusy.value = `quiz:${skill.id}`;
  const requestedSkill = { ...skill, self_level: skillLevelValue(skill) };
  try {
    quiz.value = null;
    quizGeneratingSkill.value = requestedSkill;
    answers.value = {};
    const payload = await api<any>(`/skills/${skill.id}/quiz`, { method: "POST", cache: "no-store" });
    quiz.value = { skill: payload.skill || requestedSkill, questions: payload.questions || [] };
  } catch (error: any) {
    ElMessage.error(error.message);
  } finally {
    quizGeneratingSkill.value = null;
    actionBusy.value = "";
  }
}

async function submitQuiz() {
  if (!quiz.value) return;
  actionBusy.value = "submitQuiz";
  try {
    await api(`/skills/${quiz.value.skill.id}/verify`, {
      method: "POST",
      body: { answers: quiz.value.questions.map((q: any) => ({ questionId: q.id, answer: answers.value[q.id] || "" })) }
    });
    quiz.value = null;
    await loadSkills();
    ElMessage.success("AI 核验完成");
  } catch (error: any) {
    ElMessage.error(error.message);
  } finally {
    actionBusy.value = "";
  }
}

async function generateWbs() {
  if (!workspace.value) return;
  if (!requirementText.value.trim() && !requirementFile.value) {
    ElMessage.warning("请粘贴需求或添加文件");
    return;
  }
  busy.value = true;
  try {
    const selectedFile = requirementFile.value;
    const body = selectedFile ? new FormData() : { projectId: workspace.value.project.id, requirementText: requirementText.value };
    if (body instanceof FormData) {
      body.append("projectId", workspace.value.project.id);
      body.append("requirementText", requirementText.value);
      body.append("file", selectedFile);
    }
    await api("/ai/wbs-generate", {
      method: "POST",
      body
    });
    requirementFile.value = null;
    await loadWorkspace(workspace.value.project.id);
    workspaceTab.value = "board";
    ElMessage.success("WBS 已生成");
  } catch (error: any) {
    ElMessage.error(error.message);
  } finally {
    busy.value = false;
  }
}

function handleRequirementFileChange(file: any) {
  const raw = file?.raw as File | undefined;
  if (!raw) return;
  if (!isRequirementFile(raw)) {
    ElMessage.error("仅支持 txt、md、docx、pdf 文件");
    return;
  }
  if (raw.size > 8 * 1024 * 1024) {
    ElMessage.error("文件不能超过 8MB");
    return;
  }
  requirementFile.value = raw;
}

function handleRequirementFileExceed(files: File[]) {
  const nextFile = files[0];
  if (nextFile) handleRequirementFileChange({ raw: nextFile });
}

function clearRequirementFile() {
  requirementFile.value = null;
}

function isRequirementFile(file: File) {
  return /\.(txt|md|markdown|docx|pdf)$/i.test(file.name);
}

async function patchTask(taskId: string, body: any) {
  try {
    await api(`/task/${taskId}`, { method: "PATCH", body });
    await loadWorkspace(workspace.value.project.id);
    return true;
  } catch (error: any) {
    if (workspace.value?.project?.id) await loadWorkspace(workspace.value.project.id);
    ElMessage.error(error.message);
    return false;
  }
}

async function submitAssigneeChange(task: any) {
  const nextAssigneeId = assigneeDrafts.value[task.id] || null;
  const reason = String(assigneeReasons.value[task.id] || "").trim();
  if (!reason) {
    ElMessage.warning("请填写修改负责人理由");
    return;
  }
  actionBusy.value = `assignee:${task.id}`;
  try {
    const changed = await patchTask(task.id, { assigneeId: nextAssigneeId, reason });
    if (!changed) return;
    assigneeEditTaskId.value = "";
    ElMessage.success("负责人已修改");
  } finally {
    actionBusy.value = "";
  }
}

async function submitTask(taskId: string) {
  const sourceUrl = repoInputs.value[taskId];
  if (!sourceUrl) return;
  busy.value = true;
  markTaskInReview(taskId);
  try {
    const payload = await api<any>("/task/submit", { method: "POST", body: { taskId, sourceUrl } });
    repoInputs.value[taskId] = "";
    await loadWorkspace(workspace.value.project.id);
    if (payload.task?.ai_review_status === "passed") {
      ElMessage.success("AI 审查通过");
    } else {
      ElMessage.warning("AI 审查打回");
    }
  } catch (error: any) {
    await loadWorkspace(workspace.value.project.id);
    ElMessage.error(error.message);
  } finally {
    busy.value = false;
  }
}

async function kickSession(id: string) {
  try {
    await api(`/auth/sessions/${id}`, { method: "DELETE" });
    await loadSessions();
    ElMessage.success("设备已下线");
  } catch (error: any) {
    ElMessage.error(error.message);
  }
}

function tasksByStatus(status: string) {
  return (workspace.value?.tasks || []).filter((task: any) => task.status === status);
}

function syncTaskOpenState(tasks: any[]) {
  const next: Record<string, boolean> = {};
  for (const task of tasks) {
    next[task.id] = openTaskIds.value[task.id] ?? false;
  }
  openTaskIds.value = next;
}

function canSubmitTask(task: any) {
  return task.status === "in_progress" && task.assignee_id === user.value?.id;
}

function dispatchableTasksCount() {
  return (workspace.value?.tasks || []).filter((task: any) => task.status === "todo" && task.assignee_id).length;
}

async function dispatchTask(task: any) {
  if (!task.assignee_id) {
    ElMessage.warning("请先分配负责人");
    return;
  }
  busy.value = true;
  try {
    await api(`/task/${task.id}/dispatch`, { method: "POST" });
    await loadWorkspace(workspace.value.project.id);
    ElMessage.success("任务已派发");
  } catch (error: any) {
    if (workspace.value?.project?.id) await loadWorkspace(workspace.value.project.id);
    ElMessage.error(error.message);
  } finally {
    busy.value = false;
  }
}

async function dispatchTodoTasks() {
  if (!workspace.value?.project?.id) return;
  busy.value = true;
  try {
    const payload = await api<any>("/task/dispatch", {
      method: "POST",
      body: { projectId: workspace.value.project.id }
    });
    await loadWorkspace(workspace.value.project.id);
    const count = Number(payload.dispatched || 0);
    if (count > 0) ElMessage.success(`已派发 ${count} 个任务`);
    else ElMessage.warning("没有可派发的任务");
  } catch (error: any) {
    if (workspace.value?.project?.id) await loadWorkspace(workspace.value.project.id);
    ElMessage.error(error.message);
  } finally {
    busy.value = false;
  }
}

function markTaskInReview(taskId: string) {
  const task = (workspace.value?.tasks || []).find((item: any) => item.id === taskId);
  if (!task) return;
  task.status = "review";
  task.ai_review_status = "pending";
  task.ai_review_comment = "";
}

function statusTag(status: string) {
  return status === "done" ? "success" : status === "review" ? "warning" : status === "in_progress" ? "primary" : "info";
}

function reviewLabel(task: any) {
  if (task.ai_review_status === "passed") return "AI 通过";
  if (task.ai_review_status === "rejected") return "AI 打回";
  if (task.ai_review_status === "pending") return "AI 审查中";
  return "AI 未审查";
}

function reviewAlertType(task: any) {
  if (task.ai_review_status === "passed") return "success";
  if (task.ai_review_status === "rejected") return "error";
  return "info";
}

function connectHub(projectId: string) {
  if (!getToken()) return;
  socket.value?.close();
  const ws = new WebSocket(wsUrl());
  socket.value = ws;
  ws.addEventListener("open", () => ws.send(JSON.stringify({ type: "JOIN_PROJECT", projectId })));
  ws.addEventListener("message", async (event) => {
    const payload = JSON.parse(event.data);
    if (payload.type === "METRICS_UPDATED" && workspace.value?.project?.id === payload.projectId) {
      workspace.value.dashboard = payload.dashboard;
      await nextTick();
      renderCharts();
    }
    if (payload.type === "TASK_REJECTED") {
      ElMessage.error(payload.comment || "任务被 AI 打回");
      await loadWorkspace(projectId);
    }
    if (payload.type === "FORCE_LOGOUT") {
      ElMessage.error(payload.reason || "此设备已下线");
      logout();
    }
  });
}

function renderCharts() {
  if (!workspace.value || workspaceTab.value !== "dashboard") return;
  const dashboard = workspace.value.dashboard || {};
  const progress = Number(dashboard.progress || 0);
  if (ringRef.value) {
    ringChart = ringChart || echarts.init(ringRef.value);
    ringChart.setOption({
      color: ["#16a085", "#dfe6e9"],
      series: [{ type: "pie", radius: ["68%", "86%"], label: { show: true, position: "center", formatter: `${progress.toFixed(0)}%`, fontSize: 28, fontWeight: 700 }, data: [{ value: progress, name: "完成" }, { value: Math.max(0, 100 - progress), name: "剩余" }] }]
    });
  }
  if (burnRef.value) {
    burnChart = burnChart || echarts.init(burnRef.value);
    burnChart.setOption({
      color: ["#3867d6"],
      grid: { left: 36, right: 16, top: 20, bottom: 32 },
      xAxis: { type: "category", data: (dashboard.burnDown || []).map((item: any) => item.name) },
      yAxis: { type: "value" },
      series: [{ type: "line", smooth: true, areaStyle: { opacity: 0.08 }, data: (dashboard.burnDown || []).map((item: any) => item.remaining) }]
    });
  }
  if (pieRef.value) {
    pieChart = pieChart || echarts.init(pieRef.value);
    pieChart.setOption({
      color: ["#16a085", "#3867d6", "#f39c12", "#8e44ad", "#d35400"],
      tooltip: { trigger: "item" },
      series: [{ type: "pie", radius: "72%", data: dashboard.contribution?.length ? dashboard.contribution : [{ name: "暂无", value: 1 }] }]
    });
  }
}

watch(workspaceTab, async () => {
  await nextTick();
  renderCharts();
});

onMounted(async () => {
  if (!getToken()) return;
  try {
    const payload = await api<any>("/auth/me");
    user.value = payload.user;
    await refreshAll();
  } catch {
    setToken("");
  }
});
</script>
