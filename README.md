# 安宿 · 全量版工程骨架

> 当前是**第一批工程件**：23 表 DDL、种子、阈值、Java 健康检查/演示鉴权、Python health、Vue 登录壳。
> 还没做：大屏三区、注入/告警、申诉/学习、RAG 上传/解析/出题。按 `docs` 里的阶段顺序推进。

## 目录

```text
ansu/
├─ ansu-server/        Java 21 + Spring Boot 3.3
├─ ansu-ai/            Python 3.11 + FastAPI（health / RAG health）
├─ ansu-web/           Vue3 + Vite（登录 / 管理壳 / 学生壳）
├─ scripts/            smoke / reset
├─ docs/               运行手册（后续）
├─ docker-compose.yml  MySQL 8
└─ .env.example
```

## 前置环境

| 组件 | 版本 |
|---|---|
| JDK | 17+（推荐 21；本项目 17 可跑） |
| Maven | 3.9+ |
| Node | 20 LTS+ |
| Python | 3.11+ |
| MySQL | 8.0.16+（或 Docker） |

## 快速启动（本机 MySQL 版）

### 1) 配置数据库密码

打开 `ansu/.env`，把 `MYSQL_ROOT_PASSWORD=CHANGE_ME` 改成你的 MySQL root 密码。  
不要提交 `.env`；它已在 `.gitignore` 里。

### 2) 建库 + 建应用账号（一次即可）

```bash
cd ansu
python scripts/init-db.py
```

这个脚本用 root 建 `ansu` 数据库和 `ansu/ansu` 应用账号；之后应用不再用 root。

### 3) 启动后端（Flyway 自动建 23 表 + 种子）

Maven 没进 PATH 时，用完整路径：

```bash
cd ansu-server
"D:/DevTools/maven/apache-maven-3.9.16/bin/mvn.cmd" spring-boot:run -Dspring-boot.run.profiles=demo
```

或先把 `D:/DevTools/maven/apache-maven-3.9.16/bin` 加进 PATH，再 `mvn ...`。

### 4) 启动 AI sidecar（另开终端）

```bash
cd ansu-ai
python -m venv .venv
source .venv/bin/activate        # Windows Git Bash: source .venv/Scripts/activate
pip install -r requirements.txt
uvicorn app.main:app --host 127.0.0.1 --port 8000
```

### 5) 启动前端（另开终端）

```bash
cd ansu-web
npm install
npm run dev
```

打开 `http://127.0.0.1:5173/login`，选择角色进入。学生默认 314。

> 有 Docker 时也可以用 `docker compose up -d mysql` 起 MySQL；没有 Docker 不影响本机 MySQL 方案。

打开 `http://127.0.0.1:5173/login`，选择角色进入。学生默认 314。

## 在 VSCode 打开

1. VSCode → `File → Open Folder` → 选择 `ansu` 整个文件夹（不要只打开单个文件）。
2. 推荐扩展：
   - Extension Pack for Java
   - Vue - Official
   - Python
3. 终端在 VSCode 里打开时，默认目录是 `ansu`；按上面的步骤启动即可。

## Git

本地仓库已初始化（`main` 分支），目前只有本地提交，**还没有远程仓库**。  
先在 GitHub/Gitee 建一个空仓库（不要勾选 README），然后：

```bash
cd ansu
git remote add origin <你的仓库地址>
git push -u origin main
```

如果是 HTTPS，第一次 push 会让你登录/填 token；不要在聊天里发密码或 token。  
也可以在 VSCode 的“源代码管理”里点 `Publish Branch`，按提示登录。

## 冒烟

```bash
# 健康
curl -s http://127.0.0.1:8080/api/health | jq .

# 演示身份（学生 314）
curl -s -X POST http://127.0.0.1:8080/api/auth/demo \
  -H 'Content-Type: application/json' \
  -d '{"role":"student","dormId":314}' | jq .

# 阈值（需带 token）
TOKEN=$(curl -s -X POST http://127.0.0.1:8080/api/auth/demo \
  -H 'Content-Type: application/json' -d '{"role":"admin"}' | jq -r '.data.token')
curl -s http://127.0.0.1:8080/api/safety/thresholds \
  -H "Authorization: Bearer $TOKEN" | jq '.data.version'
```

## 已实现

- `V1__schema.sql`：23 表（16 核心 + 6 RAG + 1 刷新令牌）
- `V2__seed.sql`：1 号楼、20 寝（含 314/315）、六角色演示身份
- `thresholds.yaml`：版本、时间带、白名单、信用、定时器（含 `night_limit_w: 400`）
- `POST /api/auth/demo`：按角色/宿舍换 JWT（12h）
- `GET /api/health`：DB + 阈值版本 + demo 状态
- `GET /api/safety/thresholds`：返回当前阈值（需登录）
- Python `GET /ai/health`、`GET /ai/rag/health`
- Vue 登录页 + 管理/学生占位页

## 下一批

1. 大屏三区 + 20 寝状态 + 曲线
2. 演示 run / 注入 800W / 告警 / 证据
3. 学生告警详情 + 三段式
4. 申诉/复核/恢复
5. RAG 上传/解析/分块/检索

## 验证状态（第一批）

| 项 | 状态 | 说明 |
|---|---|---|
| SQL 23 表 | 静态通过 | 表数/重复/外键顺序已检查；未在真实 MySQL 执行 |
| `thresholds.yaml` | 通过 | PyYAML 解析成功；version=2026.11.1，night_limit=400 |
| Python health | 通过 | `/ai/health` 200；无令牌 401；`/ai/rag/health` 200 |
| 前端 | 通过 | `npm run build` 成功 |
| Java | **编译/打包通过** | 用 JDK17 + `-Djava.version=17` 验证；目标 JDK17+，`target/ansu-server-0.1.0.jar` 已生成 |
| MySQL/Flyway | **未执行** | 本机无 Docker/MySQL；装好后启动先看 Flyway 迁移与阈值版本初始化 |

## 注意

- 当前 `user_account.password_hash` 为空，只支持演示身份；正式账号在 P1 补。
- `GET /api/health` 的 `python` 字段暂为 `not_checked`；下一批接入 AI 健康检查。
- 本机若只有 JDK 17 / 无 Maven / 无 Docker，先安装再启动。
- 前端构建有 chunk >500kB 警告；下一批按路由/图表库分包。
