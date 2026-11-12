# R0 前端视觉审计 · 宿舍电力哨兵 / 安宿

> 范围：只做 R0（读文档、基线截图、问题清单），**未修改任何前端/后端代码**。  
> 生成方式：Playwright core + 本机 Edge headless；视口 375×900 / 768×1024 / 1024×900 / 1440×1000；截图 32 张。  
> 客观指标：`docs/ui/baseline-metrics.json`（溢出、低对比度、触控尺寸、计算样式）。  
> 说明：低对比度脚本对“半透明白底上的白字”会误报（如学生端退出按钮、告警标签）；此类已在人工看图中排除，不计入问题。

---

## 1. 文档摘要（与视觉相关）

| 主题 | 结论 | 来源 |
|---|---|---|
| 产品定位 | B2B2C 宿舍用电安全共治：学校端监测/处置/复盘，学生端证据/申诉/学习/恢复 | `05-全量版范围与RAG学习方案` §1；原始产品文档 §0.1 |
| 六步闭环 | 识别 → 提醒 → 解释 → 处置 → 学习 → 复盘 | `05` §2；原始产品文档 §5.4 |
| 三层状态机 | 寝室状态 / 告警状态 / 申诉状态分开，不能揉成一个 status | `02-补充规格` §3；`04` X03/X05 |
| 演示主链路 | 90 秒：注入 800W → 学生证据 → 申诉/学习 → 恢复 → 简报 | `03-任务拆解与验收` §5；`05` §1 |
| 角色 | 学生、宿管、辅导员、维修工、管理员、访客；P0 演示以学生/宿管/管理员为主 | `05` §3；`02` §4.3 |
| 页面范围 | 6 个核心页 + P1 页面；当前已实现登录、大屏、学生卡、告警详情、申诉、学习 | `05` §4；`02` §4 |
| 学生端形态 | 手机 H5 优先（375px），可嵌入公众号/企业微信；当前已加宽屏双列 | 原始产品文档 §5.9；`05` §10 |
| 大屏形态 | 1920×1080 基准、3 米外可读；状态灯配文字/图标 | 原始产品文档 §5.9；`05` §10 |
| 颜色与可访问性 | 正文对比度 ≥4.5:1；状态不能只靠颜色 | 原始产品文档 §7.4；`07-前端视觉优化系统提示词` §2.3 |
| 隐私与文案 | 大屏不显示姓名/学号；学生只看本宿舍；文案不羞辱 | `02` §10；`05` §3 |
| 信用分 | 宿舍级、0–100；基础 100；学习/无违规可加分 | `02` §3.4；原始产品文档 §6.4 |

---

## 2. 当前页面清单

| 优先级 | 页面 | 路由 | 文件 | 角色 | 核心信息 | 主要状态 |
|---|---|---|---|---|---|---|
| P0 | 登录/演示身份 | `/login` | `src/views/LoginView.vue` | 公开 | 角色、宿舍号、进入演示 | 默认、错误 |
| P0 | 管理安全大屏 | `/admin/dashboard` | `src/views/admin/DashboardView.vue` | manager/admin | 20 寝状态、曲线、指标、告警流、注入/重置 | empty、warned、cut_off、error、loading |
| P0 | 学生宿舍卡 | `/m/home` | `src/views/student/HomeView.vue` | student | 功率、电量、信用、未读、当前告警 | normal、cut_off、error、loading |
| P0 | 告警证据页 | `/m/alerts/:id` | `src/views/student/AlertDetailView.vue` | student | 证据曲线、三段式、时间线、动作 | cut_off、resolved、loading、error |
| P1 | 申诉工作台 | `/admin/appeals` | `src/views/admin/AppealsView.vue` | manager/admin | 待办、原因、陈述、SLA、通过/驳回 | pending、empty、loading、error |
| P1 | 安全学习页 | `/m/learning/:id` | `src/views/student/LearningView.vue` | student | 材料、5 题、结果、证书 | task、pass、fail、loading、error |
| P2 | 后续页面 | — | — | — | 报修、凭证、知识库、出题、维修端、辅导员 | 未实现，本轮不扩 |

---

## 3. 当前技术约束

- Vue 3 + Vite + TypeScript；`main.ts` 同时全局引入 **Element Plus CSS** 与 **Vant CSS**（Vant 当前页面未使用）。
- ECharts 5 用于大屏曲线和证据曲线；Pinia + axios。
- 样式以 SFC `<style scoped>` 为主；**没有全局 token 文件**；颜色/间距/圆角/阴影大量硬编码。
- 没有全局 `box-sizing: border-box` 重置；登录页 375 出现 8px 横向溢出。
- 路由：`/login`、`/admin/dashboard`、`/admin/appeals`、`/m/home`、`/m/alerts/:id`、`/m/learning/:id`。
- 业务约束：不改 API、状态语义、权限、信用规则；视觉可改颜色/间距/布局/组件呈现。

---

## 4. 基线截图与客观指标

### 4.1 截图目录

```text
docs/ui/screenshots/
├─ login/{375,768,1024,1440}/normal.png
├─ admin-dashboard/{375,768,1024,1440}/cut_off.png
├─ admin-dashboard/{768,1024,1440}/warned.png
├─ admin-dashboard/1440/{empty,error,loading}.png
├─ student-home/{375,768,1024,1440}/cut_off.png
├─ student-home/1440/normal.png
├─ student-home/375/{error,loading}.png
├─ alert-detail/{375,768,1024,1440}/cut_off.png
├─ learning/{375,768,1024,1440}/task.png
└─ appeals/{375,1024,1440}/pending.png
```

关键截图：

- 管理大屏 1440：`screenshots/admin-dashboard/1440/cut_off.png`
- 管理大屏 375：`screenshots/admin-dashboard/375/cut_off.png`
- 管理大屏 768：`screenshots/admin-dashboard/768/cut_off.png`
- 学生宿舍卡 375：`screenshots/student-home/375/cut_off.png`
- 学生宿舍卡 1440：`screenshots/student-home/1440/cut_off.png`
- 告警证据 1440：`screenshots/alert-detail/1440/cut_off.png`
- 学习页 1440：`screenshots/learning/1440/task.png`
- 申诉工作台 375：`screenshots/appeals/375/pending.png`
- 错误态：`screenshots/student-home/375/error.png`、`screenshots/admin-dashboard/1440/error.png`
- 加载态：`screenshots/student-home/375/loading.png`、`screenshots/admin-dashboard/1440/loading.png`

### 4.2 指标摘要

| 指标 | 结果 | 证据 |
|---|---|---|
| 登录 375 横向溢出 | `scrollWidth 383 / clientWidth 375`，8px | `baseline-metrics.json` login/375 |
| 管理大屏 375 横向溢出 | `668 / 375`；topbar `648/335`；layout `588/335` | admin-dashboard/375 |
| 管理大屏 768 指标溢出 | metrics `212/140`；单卡 `28px` 宽，文字竖排 | admin-dashboard/768 |
| 管理大屏 1024 指标溢出 | metrics `417/396`；总功率卡 `92/71` | admin-dashboard/1024 |
| 登录主按钮对比度 | 白字 on `#409eff` = **2.78:1** | login/375 |
| 登录辅助文字 | `#78879b` on white = **3.66:1** | login/375 |
| 管理端 muted | `#78879b` on `#f2f5f9` = **3.35:1** | admin-dashboard |
| 告警标签/辅助 | `#0ea5e9` on white = **2.77:1**；`#78879b` = 3.66 | alert-detail/learning |
| 申诉按钮 | 白字 on `#67c23a` = **2.24:1**；白字 on `#f56c6c` = 2.9:1 | appeals |
| 申诉状态标签 | `#e6a23c` on `#fdf6ec` = **2.04:1** | appeals |
| 触控尺寸 | 登录/学习/告警按钮高 32；申诉按钮高 24；学生退出高 30 | metrics |
| 圆角/阴影 | 登录/hero 20；卡片/面板 16；阴影 24/30/40 三套并存 | metrics samples |
| 主按钮颜色 | Element Plus 默认 `#409eff`，未使用项目 `#1d4ed8` | login/learning/alert-detail |

> 说明：低对比度脚本对半透明背景会误报（学生退出、告警标签），已人工排除；上表只列实际可见问题。

---

## 5. 问题清单（R0）

### P0（主链路/响应式/空白态，必须先修）

| ID | 页面 | 问题 | 证据/度量 | 影响 | 建议 |
|---|---|---|---|---|---|
| UI-001 | 管理大屏 | 375 完全不响应：body 横向溢出 668/375，topbar 648/335，layout 588/335，中间指标/图表被挤出 | `admin-dashboard/375/cut_off.png`；metrics | 手机/窄屏不可用；违反“无 body 横向滚动” | 增加 1200/900 断点：≥1200 三栏；900–1199 两栏；<900 单列；topbar 允许换行；指标 2 列 |
| UI-002 | 管理大屏 | 768 指标卡被压成 28px 宽，文字竖排/截断；center 212/140 | `admin-dashboard/768/cut_off.png`；metrics | 平板/小笔记本不可读 | 768–899 用 2×3 指标网格；告警栏下移；曲线最小高度 240 |
| UI-003 | 管理大屏 | 1024 指标溢出 417/396，总功率值被截断 | `admin-dashboard/1024/cut_off.png`；metrics | 1024 笔记本常见尺寸，数字不可信 | 指标用 `minmax(0,1fr)` + 字号 clamp；≥1200 才五列；否则 3/2 列 |
| UI-004 | 学生首页/管理大屏 | 加载态与错误态不可区分：学生端都显示“加载中/0”；管理端错误显示 0/0/0、空列表、标签“未知”，无错误提示/重试 | `student-home/375/error.png` vs `loading.png`；`admin-dashboard/1440/error.png` | 用户以为“没数据/没告警”，而不是请求失败 | 增加显式 `error` 横幅 + 重试按钮；`loading` 用骨架屏；`empty` 与 `error` 文案区分 |
| UI-005 | 登录页 | 375 页面横向溢出 383/375，卡片被裁切；无全局 box-sizing | `login/375/normal.png`；metrics | 小手机首次进入即横向滚动 | 全局 `*,*::before,*::after{box-sizing:border-box}`；卡片 `width:100%`、页面 padding 16，移除溢出 |

### P1（可读性、可访问性、桌面体验）

| ID | 页面 | 问题 | 证据/度量 | 影响 | 建议 |
|---|---|---|---|---|---|
| UI-006 | 登录/学习/告警 | 主按钮使用 Element Plus 默认 `#409eff`，白字对比度 2.78:1 | metrics login/learning/alert-detail | 关键 CTA 不达标，弱视/投影下难读 | 用项目 `--accent #1d4ed8` 覆盖 `--el-color-primary`；或加深到 ≥4.5:1 |
| UI-007 | 全局/学生/管理 | 辅助文字 `#78879b` 3.66:1；管理 muted 3.35；11px 状态文字 3.66 | metrics | 12–13px 说明/状态在投影/阳光下不可读 | 辅助文字改 `#5b6b80`（需复测 ≥4.5）；11px 状态提升到 12px |
| UI-008 | 告警详情/学习 | 桌面 1440 仍固定 430px，页面大量空白；告警证据/解释未利用横向空间 | `alert-detail/1440/cut_off.png`；`learning/1440/task.png` | 桌面体验像“手机壳”，信息密度低 | ≥900 内容宽 760–860；告警证据+解释可双列；学习材料+题目可双列 |
| UI-009 | 管理/申诉 | 状态/类型/原因显示英文：`resistive`、`submitted`、`misjudge` | 管理大屏右栏；`appeals/375/pending.png` | 评委/宿管需要猜；与中文界面不一致 | 建立统一字典：resistive→阻性、submitted→待复核、misjudge→误判等；只改展示不改 API |
| UI-010 | 管理大屏 | 图表阈值线标签被裁成“处”；阈值硬编码 500，夜间 400 不跟随 | `admin-dashboard/1440/cut_off.png`；`1024` | 关键证据说明不完整；规则不一致 | 增加右侧 grid/padding，label 放线内；阈值从当前告警/时间带取 |
| UI-011 | 全局 | 触控目标偏小：登录/学习/告警按钮 32px，高；申诉按钮 24px；学生退出 30px | metrics | 手机误触/难点；不满足 ≥40 | 移动端按钮/输入 `min-height:40/44`；表格行高 ≥44；图标按钮 40×40 |
| UI-012 | 学生首页 | 告警卡片是 `div @click`，无 `role/tabindex/keydown`，键盘不可达 | `HomeView.vue` 告警卡；截图 | 键盘/辅助技术用户无法打开证据 | 改为 `<button>` 或 `role="button" tabindex="0" @keydown.enter/space` |
| UI-013 | 申诉工作台 | 通过/驳回按钮对比度 2.24/2.9；状态标签 2.04；按钮 24px 高 | `appeals/*/pending.png`；metrics | 关键处置按钮不清晰 | 用深色语义底/描边按钮；状态标签改深色文字+浅底；按钮 ≥40 |

### P2（一致性、性能、信息层级）

| ID | 页面 | 问题 | 证据/度量 | 影响 | 建议 |
|---|---|---|---|---|---|
| UI-014 | 全局 | 圆角/阴影不统一：登录/hero 20、卡片/面板 16；阴影 24/30/40 三套 | metrics samples | 视觉不统一，难维护 | 落 token：radius 6/10/14/18/999；shadow 1/2/3 |
| UI-015 | 全局 | 间距/颜色硬编码：9/14/18/20/22/42px 散落；无 CSS 变量 | `ansu-web/src/**` 样式 | 每轮改动重复、回归风险高 | 先建 `tokens.css`；页面只引用 token；例外值登记 |
| UI-016 | 管理大屏 | 曲线 x 轴标签密集/重叠（1 分钟基线 + 1 秒事件混排），1024 更明显 | `admin-dashboard/1024/cut_off.png`、`admin-dashboard/1440/cut_off.png` | 时间读数困难 | 动态 tick、按分辨率降采样、必要时旋转/省略 |
| UI-017 | 申诉工作台 | 信息层级偏薄：只有原因/陈述/时间，没有证据摘要、SLA 倒计时、状态时间线 | `appeals/375/pending.png` | 宿管复核缺上下文，像“简易列表” | 加证据摘要/规则/原扣分/SLA 角标；不改业务数据 |
| UI-018 | 全局 | `main.ts` 同时引入 Element Plus 与 Vant CSS；Vant 当前未用；CSS 561KB，主 JS >1MB | `main.ts`；build 输出 | 首屏慢、样式冲突风险 | 审计 Vant 使用；确认无引用后再移除 import（需用户批准）；按路由/图表分包 |
| UI-019 | 登录页 | 文案“P0 不建复杂账号”是内部术语，面向评委不自然 | `login/*/normal.png` | 首次印象偏工程化 | 改为“演示环境：选择角色进入；正式版接学校统一认证”（内容改动需确认） |
| UI-020 | 学生首页 | 首页仍显示“已处理”的告警卡（最近告警），与“当前没有告警”语义容易混淆 | `student-home/375/cut_off.png` | 用户不确定“现在还有没有事” | 区分“进行中告警”和“最近告警”；需产品确认 |

---

## 6. 优先级与判定依据

| 优先级 | 问题 | 判定 |
|---|---|---|
| P0 | UI-001/002/003（管理端响应式断裂）、UI-004（错误态空白）、UI-005（登录溢出） | 直接影响演示主链路/首屏可用性；任一出现即可判定 R0 阻塞 |
| P1 | UI-006–013 | 影响可读性、可访问性、桌面体验；不修会在投影/手机/键盘场景暴露 |
| P2 | UI-014–020 | 一致性与长期维护；可在 P0/P1 之后处理 |

排序理由：**演示路径覆盖度 × 信息密度 × 状态复杂度 × 响应式风险 × 可访问性风险**。  
管理大屏 1440 视觉基本可读，但 375/768/1024 断裂；学生首页 375/1440 可用，但辅助文字/触控/错误态需修；告警详情/学习桌面仍是手机壳。

---

## 7. 待确认项（`ui-pending.md` 先记录）

| ID | 类型 | 内容 | 临时方案 | 需要确认 |
|---|---|---|---|---|
| UI-CONFLICT-001 | 文档 vs 用户期望 | 原产品文档：学生端 H5 375 优先；用户希望桌面全屏优化 | **已确认：Web 全宽（100% 视口 + 24/32px 边距，正文 68ch）** | 已在 `ui-pending.md` 记录 |
| UI-CONFLICT-002 | 规则 vs 用户直觉 | 信用分原 0–120，可超过 100 | **已确认：上限 100；作为独立规则变更（后端+测试+文档），不混入视觉 R1** | 已在 `ui-pending.md` 记录 |
| UI-CONFLICT-003 | 数据 vs 展示 | API 返回 `resistive/submitted/misjudge` 等英文枚举 | 前端统一字典中文展示，不改 API | 字典文案由产品确认 |
| UI-CONFLICT-004 | 设计系统 | Element Plus 默认主题 `#409eff` 与项目 `#1d4ed8` 不一致 | **已确认：允许全局覆盖 Element Plus CSS 变量** | 已在 `ui-pending.md` 记录 |
| UI-CONFLICT-005 | 文案 | 登录页“P0 不建复杂账号”是内部术语 | 暂不改 | 是否改为面向评委的“演示环境”文案？ |
| UI-VERIFY-001 | 基线缺口 | 原始文档要求大屏 1920×1080/3m 可读；本轮只截到 1440 | 1440 作为当前演示基准 | 是否补 1920×1080 基线？ |

---

## 8. R0 结论与 R1 建议

R0 结论：

1. 管理大屏在 **375/768/1024 全部存在布局断裂**，是当前最大视觉风险；1440 基本可读但图表标签/英文枚举/对比度需修。
2. 登录页 375 有 8px 横向溢出；主按钮对比度 2.78，是首屏可访问性问题。
3. 学生首页 375/1440 结构已可用；主要问题是辅助文字对比度、触控尺寸、错误/加载态、告警卡键盘可达性。
4. 告警详情/学习页桌面仍是 430px 手机壳；信息密度和桌面体验不足。
5. 全局没有 token，颜色/间距/圆角/阴影散落；应先落 token，再逐页改。

建议 R1（下一轮，只做共享层）：

1. 新建 `src/styles/tokens.css`：间距/字号/颜色/圆角/阴影/断点；全局 `box-sizing`。
2. 覆盖 Element Plus 主色为项目 `#1d4ed8`；验证按钮对比度 ≥4.5。
3. 建统一状态字典（寝室/告警/申诉/原因/类型），只改展示。
4. 不碰业务逻辑；R1 完成后重新跑 375/768/1024/1440 截图，确认登录溢出与主按钮对比度关闭。

> R1 的改动范围仍按提示词：共享 token + 全局壳，最多 8 文件/300 行，不改 API/路由/业务。

---

## 9. 产物清单

- `docs/ui/ui-audit.md`（本文件）
- `docs/ui/baseline-metrics.json`（32 个页面/视口/状态的 DOM 指标）
- `docs/ui/screenshots/**`（32 张基线截图）
- 未创建/未修改任何前端源码。
