"""Remote PC Controller schema (unified domain models and DTO)."""

from pydantic import BaseModel


class LogEntry(BaseModel):
    """Represents log entry data."""

    timestamp: int
    level: str
    message: str


class SystemMetrics(BaseModel):
    """Represents system metrics data."""

    cpu_usage_percent: float
    ram_usage_percent: float


class SystemStatus(BaseModel):
    """Represents system status data."""

    metrics: SystemMetrics
    logs: list[LogEntry]


class ActionRequest(BaseModel):
    """Represents action request data."""

    action_name: str
    payload: str | None = None


class ActionResponse(BaseModel):
    """Represents action response data."""

    message: str


def response(message: str) -> ActionResponse:
    """Shortcut for action response.

    :param message: Action response message.
    :return: Action response model.
    """
    return ActionResponse(message=message)
