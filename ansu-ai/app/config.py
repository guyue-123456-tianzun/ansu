import os
from dataclasses import dataclass


@dataclass(frozen=True)
class Settings:
    internal_token: str
    llm_mode: str
    llm_model: str
    llm_base_url: str
    llm_api_key: str
    rag_enabled: bool
    rag_chroma_dir: str
    rag_top_k: int
    rag_score_threshold: float


def load_settings() -> Settings:
    return Settings(
        internal_token=os.getenv("INTERNAL_TOKEN", "dev-internal-token"),
        llm_mode=os.getenv("RAG_LLM_MODE", "api"),
        llm_model=os.getenv("RAG_LLM_MODEL", "glm-4-plus"),
        llm_base_url=os.getenv("RAG_LLM_BASE_URL", "https://open.bigmodel.cn/api/paas/v4"),
        llm_api_key=os.getenv("RAG_LLM_API_KEY", ""),
        rag_enabled=os.getenv("RAG_ENABLED", "true").lower() == "true",
        rag_chroma_dir=os.getenv("RAG_CHROMA_DIR", "data/chroma"),
        rag_top_k=int(os.getenv("RAG_TOP_K", "6")),
        rag_score_threshold=float(os.getenv("RAG_SCORE_THRESHOLD", "0.35")),
    )


settings = load_settings()
