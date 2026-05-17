"""Remote PC Controller business logic."""

import subprocess

import psutil

import pult.schema as pult_schema


def str_crop_ellipsis(s: str, max_len: int) -> str:
    """Crop the string.

    Replaces remainder of the string with ellipsis.
    :param s: String to crop.
    :param max_len: Maximum length of string.
    :return: Cropped string.
    """
    if len(s) > max_len:
        return s[: max_len - 3] + '...'
    return s


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
    if action.action_name == 'cmd':
        cmd = action.payload
        if cmd is None:
            return pult_schema.response('Error: missing command')
        try:
            result = subprocess.run(  # noqa: S602
                cmd, shell=True, capture_output=True, text=True, timeout=15
            )
        except subprocess.TimeoutExpired:
            return pult_schema.response(
                'Error: Command timed out after 15 seconds.'
            )
        except Exception as e:  # noqa: BLE001
            return pult_schema.response(f'Error: {e}')

        output = result.stdout.strip()
        if not output:
            output = result.stderr.strip()
        if not output:
            output = 'Command executed successfully (no output)'

        return pult_schema.response(str_crop_ellipsis(output, 500))

    return pult_schema.response('Error. Action does not exist.')
