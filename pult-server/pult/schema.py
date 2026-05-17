"""Remote PC Controller schema (unified domain models and DTO)."""

from pydantic import BaseModel


class SystemMetrics(BaseModel):
    """Represents system metrics data."""

    cpu_usage_percent: float
    ram_usage_percent: float


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
