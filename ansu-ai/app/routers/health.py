from fastapi import APIRouter, Header, HTTPException

from app.config import settings

router = APIRouter()


def require_internal_token(token: str | None) -> None:
    if not token or token != settings.internal_token:
        raise HTTPException(status_code=401, detail="invalid internal token")


@router.get("/health")
def health(x_internal_token: str | None = Header(default=None, alias="X-Internal-Token")):
    require_internal_token(x_internal_token)
    return {
        "code": 0,
        "data": {
            "status": "ok",
            "llm_mode": settings.llm_mode,
            "model": settings.llm_model,
            "rag_enabled": settings.rag_enabled,
            "version": "0.1.0",
        },
    }


@router.get("/rag/health")
def rag_health(x_internal_token: str | None = Header(default=None, alias="X-Internal-Token")):
    require_internal_token(x_internal_token)
    return {
        "code": 0,
        "data": {
            "status": "ok" if settings.rag_enabled else "disabled",
            "embedding_model": "BAAI/bge-m3",
            "rerank_model": "BAAI/bge-reranker-v2-m3",
            "chroma_dir": settings.rag_chroma_dir,
            "top_k": settings.rag_top_k,
            "score_threshold": settings.rag_score_threshold,
        },
    }
