"""The JSON the service returns. Field names are camelCase for the Java client; amounts are strings so
no decimal is ever rounded through a float."""

from datetime import date

from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel


class ApiModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, frozen=True)


class TextField(ApiModel):
    value: str
    confidence: float


class DateField(ApiModel):
    value: date
    confidence: float


class Fields(ApiModel):
    issuer_afm: TextField | None
    issue_date: DateField | None
    net_amount: TextField | None
    vat_amount: TextField | None
    total_amount: TextField | None


class CheckResult(ApiModel):
    name: str
    passed: bool
    detail: str


class CategorySuggestion(ApiModel):
    category: str
    confidence: float


class CategoryResult(ApiModel):
    best: CategorySuggestion
    alternatives: list[CategorySuggestion]
    model_version: str


class ReadResult(ApiModel):
    text: str
    pages: int
    method: str
    fields: Fields
    checks: list[CheckResult]
    category: CategoryResult
    duration_ms: int


class Health(ApiModel):
    status: str
    model_version: str
