"""Logging hacks."""

import itertools as it
import logging
import sys
from collections import deque

from pult.schema import LogEntry


class MemoryLogHandler(logging.Handler):
    """In-memory logging bufferer."""

    def __init__(
        self, max_records: int = 100, target: logging.Handler | None = None
    ) -> None:
        """In-memory logging bufferer.

        :param max_records: Maximum number of records to store in the buffer.
        :param target: Where to forward logs after storing.
        """
        super().__init__()
        self.log_records: deque[LogEntry] = deque(maxlen=max_records)
        self.target = target

    def emit(self, record: logging.LogRecord) -> None:
        """Logging log emitter.

        :param record: Log to emit.
        """
        try:
            msg = self.format(record)
            log_entry = LogEntry(
                timestamp=int(record.created * 1000),
                level=record.levelname,
                message=f'[{record.levelname}] {record.name}: {msg}',
            )

            self.log_records.append(log_entry)

            if self.target:
                self.target.emit(record)
        except Exception:  # noqa: BLE001
            self.handleError(record)

    def get_logs_after(self, last_ts: int, limit: int = 20) -> list[LogEntry]:
        """Get logs after a certain timestamp.

        :param last_ts: Timestamp to get logs after.
        :param limit: Max number of logs to return.
        :return: Logs sorted by timestamp.
        """
        return sorted(
            it.islice(
                (log for log in self.log_records if log.timestamp > last_ts),
                limit,
            ),
            key=lambda log: log.timestamp,
        )


memory_handler = MemoryLogHandler(
    max_records=500,
)
memory_handler.setLevel(logging.INFO)
memory_handler.setFormatter(logging.Formatter('%(message)s'))
