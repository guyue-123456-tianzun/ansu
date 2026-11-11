# ansu-ai

安宿 AI sidecar（FastAPI）。当前只有 health / RAG health；下一批接 explain、briefing、simulate、RAG ingest/quiz。

## 运行

```bash
python -m venv .venv
source .venv/bin/activate   # Windows: .venv\Scripts\activate
pip install -r requirements.txt
uvicorn app.main:app --host 127.0.0.1 --port 8000
```

## 健康检查

```bash
curl http://127.0.0.1:8000/ai/health \
  -H "X-Internal-Token: $INTERNAL_TOKEN"

curl http://127.0.0.1:8000/ai/rag/health \
  -H "X-Internal-Token: $INTERNAL_TOKEN"
```

## RAG 依赖

解析/向量/重排依赖较重，单独安装：

```bash
pip install -r requirements-rag.txt
```

默认生成 LLM 为 GLM-4-Plus（`RAG_LLM_MODE=api`），只发送 top-k 片段；若切本地 Qwen，改 `RAG_LLM_MODE=local` 并启动 vLLM。
