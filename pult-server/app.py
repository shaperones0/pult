"""Remote PC Controller API server."""

import asyncio
import os
from logging import getLogger

import uvicorn
from dotenv import load_dotenv
from fastapi import (
    Depends,
    FastAPI,
    Header,
    HTTPException,
    WebSocket,
    WebSocketDisconnect,
)

import pult.schema as pult_schema
import pult.system_controller as pult_controller

logger = getLogger(__name__)

load_dotenv()

API_KEY = os.getenv('API_KEY')
if API_KEY is None:
    raise ValueError('API_KEY environment variable not set.')
PORT = int(os.getenv('PULT_PORT', 8000))


def security_verify(x_api_key: str = Header(default=None)) -> None:
    """Verify X-API-Key header.

    :param x_api_key: X-API-Key header.
    :raises HTTPException: 401 Unauthorized if check failed.
    """
    if x_api_key != API_KEY:
        raise HTTPException(status_code=401, detail='Unauthorized')


app = FastAPI(
    title='Pult: Remote PC Controller API',
    dependencies=[Depends(security_verify)],
)


@app.get('/metrics')
def get_metrics() -> pult_schema.SystemMetrics:
    """## Get system metrics.

    Query CPU load and RAM usage.

    ### Returns:
    System metrics.
    """
    return pult_controller.get_system_metrics()


@app.post('/action')
def post_action(
    request: pult_schema.ActionRequest,
) -> pult_schema.ActionResponse:
    """## Perform system action.

    ### Parameters:
    - `action_name`: Action name. Possible actions:
        - `lock` - locks the computer.

    ### Returns:
    Action response (message).
    """
    try:
        return pult_controller.action_execute(request)
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e)) from e


@app.websocket('/ws/metrics')
async def ws_metrics(
    websocket: WebSocket,
) -> None:
    """## Subscribe to metrics events."""
    logger.info('WebSocket connected.')
    await websocket.accept()
    try:
        while True:
            metrics = pult_controller.get_system_metrics()
            await websocket.send_json(metrics.model_dump())
            await asyncio.sleep(2)
    except WebSocketDisconnect:
        logger.info('WebSocket disconnected.')


if __name__ == '__main__':
    uvicorn.run(app, host='127.0.0.1', port=PORT)
