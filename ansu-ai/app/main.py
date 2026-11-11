from fastapi import FastAPI

from app.routers import health

app = FastAPI(title="ansu-ai", version="0.1.0")
app.include_router(health.router, prefix="/ai")


@app.get("/")
def root():
    return {"service": "ansu-ai", "docs": "/docs", "health": "/ai/health"}
