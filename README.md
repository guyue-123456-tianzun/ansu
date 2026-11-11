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

## 快速启动

```bash
# 1) MySQL
docker compose up -d mysql

# 2) 后端（Flyway 自动建 23 表 + 种子）
cd ansu-server
mvn spring-boot:run -Dspring-boot.run.profiles=demo

# 3) AI sidecar
cd ../ansu-ai
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --host 127.0.0.1 --port 8000

# 4) 前端
cd ../ansu-web
npm install
npm run dev
```

打开 `http://127.0.0.1:5173/login`，选择角色进入。学生默认 314。

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
