"""Remote PC Controller business logic."""

import subprocess

import psutil

import pult.schema as pult_schema


def get_system_metrics() -> pult_schema.SystemMetrics:
    """Query system metrics.

    :return: System metrics (CPU load, RAM usage).
    """
    return pult_schema.SystemMetrics(
        cpu_usage_percent=psutil.cpu_percent(interval=0.5),
        ram_usage_percent=psutil.virtual_memory().percent,
    )


def action_execute(
    action: pult_schema.ActionRequest,
) -> pult_schema.ActionResponse:
    """Execute action.

    :param action: Action to execute (action name and optional payload).
    :return: Action response.
    :raises ValueError: Action does not exist.
    """
    if action.action_name == 'lock':
        subprocess.call(['rundll32.exe', 'user32.dll,LockWorkStation'])  # noqa: S607
        return pult_schema.ActionResponse(
            message='Workstation locked successfully'
        )
    if action.action_name == 'test':
        return pult_schema.ActionResponse(
            message='Workstation responded successfully'
        )
    raise ValueError('Action does not exist')
