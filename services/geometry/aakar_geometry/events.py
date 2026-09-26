"""Event envelopes (events/envelope.v1.json) and progress sinks.

A sink receives ``(type, payload)`` pairs from the pipeline and wraps them in envelopes with a
per-job monotonic ``sequence``. Sinks never raise into the build: a lost progress message must not
fail a design.
"""

from __future__ import annotations

import datetime as dt
import json
import logging
import uuid
from typing import Any, Protocol

import httpx

from .contracts import validate

log = logging.getLogger("aakar.geometry.events")

EXCHANGE = "aakar.design"

PROGRESS_MESSAGES = {
    "understanding": "Understanding your idea",
    "sculpting": "Weaving your design",
    "checking": "Checking physics",
    "pricing": "Pricing",
    "ready": "Ready",
    "failed": "Something went wrong",
}


def utc_now_iso() -> str:
    return dt.datetime.now(dt.timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z")


def new_envelope(type_: str, job_id: str, design_id: str, payload: dict[str, Any], sequence: int | None = None) -> dict[str, Any]:
    env: dict[str, Any] = {
        "type": type_,
        "version": 1,
        "event_id": str(uuid.uuid4()),
        "job_id": job_id,
        "design_id": design_id,
        "occurred_at": utc_now_iso(),
        "payload": payload,
    }
    if sequence is not None:
        env["sequence"] = int(sequence)
    validate("envelope", env)
    return env


def progress_payload(stage: str, percent: int | None = None, message: str | None = None) -> dict[str, Any]:
    payload: dict[str, Any] = {"stage": stage, "message": message or PROGRESS_MESSAGES[stage]}
    if percent is not None:
        payload["percent"] = int(percent)
    validate("design.progress", payload)
    return payload


class ProgressSink(Protocol):
    def emit(self, type_: str, payload: dict[str, Any]) -> None: ...


class NullSink:
    def emit(self, type_: str, payload: dict[str, Any]) -> None:  # noqa: D401 - nothing to do
        return None


class SequencedSink:
    """Base for sinks that build envelopes with an increasing sequence and swallow delivery errors."""

    def __init__(self, job_id: str, design_id: str):
        self.job_id = job_id
        self.design_id = design_id
        self.sequence = 0
        self.envelopes: list[dict[str, Any]] = []

    def emit(self, type_: str, payload: dict[str, Any]) -> None:
        envelope = new_envelope(type_, self.job_id, self.design_id, payload, self.sequence)
        self.sequence += 1
        self.envelopes.append(envelope)
        try:
            self.deliver(envelope)
        except Exception as exc:  # never fail the build because a progress message was lost
            log.warning("could not deliver %s for job %s: %s", type_, self.job_id, exc)

    def deliver(self, envelope: dict[str, Any]) -> None:
        raise NotImplementedError


class CollectingSink(SequencedSink):
    """Keeps envelopes in memory (tests, CLI)."""

    def deliver(self, envelope: dict[str, Any]) -> None:
        return None


class CallbackSink(SequencedSink):
    """POSTs progress envelopes to ``callback_url`` (direct HTTP profile of POST /v1/build)."""

    def __init__(self, callback_url: str, job_id: str, design_id: str, timeout_s: float = 5.0, client: httpx.Client | None = None):
        super().__init__(job_id, design_id)
        self.callback_url = callback_url
        self.timeout_s = timeout_s
        self._client = client

    def deliver(self, envelope: dict[str, Any]) -> None:
        if envelope["type"] != "design.progress":
            return  # completed/failed go back as the HTTP response, not to the callback
        body = json.dumps(envelope).encode("utf-8")
        headers = {"content-type": "application/json"}
        if self._client is not None:
            resp = self._client.post(self.callback_url, content=body, headers=headers, timeout=self.timeout_s)
        else:
            resp = httpx.post(self.callback_url, content=body, headers=headers, timeout=self.timeout_s)
        if resp.status_code >= 400:
            log.warning("callback %s answered %s for job %s", self.callback_url, resp.status_code, self.job_id)
