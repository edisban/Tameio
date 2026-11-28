"""The assistant's HTTP API (FastAPI). Only the backend calls it, after checking who is signed in.

uvicorn assistant.main:app --port 8091
"""

import time
from contextlib import asynccontextmanager
from typing import Annotated, Any, Literal
from uuid import UUID

import anthropic
from fastapi import FastAPI, HTTPException
from prometheus_client import make_asgi_app
from pydantic import BaseModel, Field, SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict
from sqlalchemy import create_engine

from assistant import agent, metrics
from assistant.knowledge import FastEmbedder, KnowledgeBase
from assistant.tools import runner


class Config(BaseSettings):
    """From the environment, with a TAMEIO_ASSISTANT_ prefix, except the API key under its usual name."""

    model_config = SettingsConfigDict(env_prefix="TAMEIO_ASSISTANT_")

    db_url: str = "postgresql+psycopg://tameio_assistant:tameio_assistant@localhost:5433/tameio"
    model: str = "claude-opus-5-5"
    effort: str = "low"
    anthropic_api_key: SecretStr | None = Field(default=None, validation_alias="ANTHROPIC_API_KEY")


class Message(BaseModel):
    role: Literal["user", "assistant"]
    content: Annotated[str, Field(min_length=1, max_length=2000)]


class ChatRequest(BaseModel):
    business_id: UUID = Field(alias="businessId")
    messages: Annotated[list[Message], Field(min_length=1, max_length=20)]


class ChatReply(BaseModel):
    reply: str
    tools_used: list[str] = Field(serialization_alias="toolsUsed")
    sources: list[dict[str, Any]]


state: dict[str, Any] = {}


@asynccontextmanager
async def lifespan(_: FastAPI):  # type: ignore[no-untyped-def]
    config = Config()
    state["config"] = config
    state["engine"] = create_engine(config.db_url, pool_pre_ping=True)
    # Embedding the documents once at start-up: a few dozen passages, a second or two
    state["knowledge"] = KnowledgeBase(FastEmbedder())
    key = config.anthropic_api_key
    state["client"] = anthropic.Anthropic(api_key=key.get_secret_value()) if key else None
    yield
    state["engine"].dispose()


app = FastAPI(title="Tameio assistant", lifespan=lifespan)
app.mount("/metrics", make_asgi_app())


@app.get("/health/live")
def live() -> dict[str, str]:
    return {"status": "UP"}


@app.post("/v1/chat", response_model=ChatReply, response_model_by_alias=True)
def chat(request: ChatRequest) -> ChatReply:
    if request.messages[-1].role != "user":
        raise HTTPException(status_code=422, detail="The last message must be the user's question")
    client = state.get("client")
    if client is None:
        raise HTTPException(status_code=503, detail="The assistant has no Claude API key configured")
    config: Config = state["config"]
    started = time.perf_counter()
    answer = agent.answer(
        client,
        agent.Settings(model=config.model, effort=config.effort),
        runner(state["engine"], state["knowledge"], request.business_id),
        [m.model_dump() for m in request.messages],
    )
    metrics.CONVERSATION_SECONDS.observe(time.perf_counter() - started)
    return ChatReply(reply=answer.reply, tools_used=answer.tools_used, sources=answer.sources)
