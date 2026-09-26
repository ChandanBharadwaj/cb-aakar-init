"""RabbitMQ worker: consumes ``design.generate``, publishes progress / completed / failed envelopes.

Topology (durable): topic exchange ``aakar.design``; queue ``geometry.design.generate`` bound with
routing key ``design.generate``. Outgoing envelopes are published to the same exchange with
routing key == envelope type. Messages are acked after the result is published; messages that are
not a valid envelope are nacked without requeue (they can never be answered).
"""

from __future__ import annotations

import json
import logging
import os
import time
from typing import Any, Callable

from .contracts import ContractError, validate
from .events import EXCHANGE, SequencedSink
from .pipeline import build_design, failed_payload, is_completed

log = logging.getLogger("aakar.geometry.worker")

QUEUE = "geometry.design.generate"
ROUTING_KEY = "design.generate"
DEFAULT_AMQP_URL = "amqp://aakar:aakar@localhost:5672/"


def _properties() -> Any:
    try:
        import pika

        return pika.BasicProperties(content_type="application/json", delivery_mode=2)
    except ImportError:  # pragma: no cover - tests use a fake channel
        return None


class AmqpSink(SequencedSink):
    def __init__(self, channel: Any, job_id: str, design_id: str):
        super().__init__(job_id, design_id)
        self.channel = channel

    def deliver(self, envelope: dict[str, Any]) -> None:
        self.channel.basic_publish(
            exchange=EXCHANGE,
            routing_key=envelope["type"],
            body=json.dumps(envelope).encode("utf-8"),
            properties=_properties(),
        )

    def publish_result(self, payload: dict[str, Any]) -> None:
        """Publish completed/failed; unlike progress this must not be swallowed."""
        type_ = "design.completed" if is_completed(payload) else "design.failed"
        from .events import new_envelope

        envelope = new_envelope(type_, self.job_id, self.design_id, payload, self.sequence)
        self.sequence += 1
        self.envelopes.append(envelope)
        self.deliver(envelope)


def declare_topology(channel: Any) -> None:
    channel.exchange_declare(exchange=EXCHANGE, exchange_type="topic", durable=True)
    channel.queue_declare(queue=QUEUE, durable=True)
    channel.queue_bind(queue=QUEUE, exchange=EXCHANGE, routing_key=ROUTING_KEY)


def parse_message(body: bytes | str) -> dict[str, Any]:
    """Return the validated design.generate envelope or raise ValueError."""
    try:
        doc = json.loads(body)
    except (ValueError, TypeError) as exc:
        raise ValueError(f"message is not JSON: {exc}") from exc
    if not isinstance(doc, dict):
        raise ValueError("message is not a JSON object")
    try:
        validate("envelope", doc)
    except ContractError as exc:
        raise ValueError(f"message is not a valid envelope: {exc}") from exc
    if doc["type"] != ROUTING_KEY:
        raise ValueError(f"unexpected envelope type {doc['type']!r}")
    return doc


def handle_message(
    channel: Any,
    method: Any,
    properties: Any,
    body: bytes,
    build: Callable[..., dict[str, Any]] = build_design,
) -> dict[str, Any] | None:
    """pika-style callback. Returns the published result payload (None when the message was rejected)."""
    tag = getattr(method, "delivery_tag", None)
    try:
        envelope = parse_message(body)
    except ValueError as exc:
        log.error("rejecting message: %s", exc)
        channel.basic_nack(delivery_tag=tag, requeue=False)
        return None

    job_id, design_id = envelope["job_id"], envelope["design_id"]
    payload = dict(envelope["payload"])
    payload.setdefault("job_id", job_id)
    payload.setdefault("design_id", design_id)
    payload.pop("callback_url", None)  # progress goes to the exchange, never to a URL, on this profile
    sink = AmqpSink(channel, job_id, design_id)
    try:
        result = build(payload, sink=sink)
    except Exception as exc:  # build_design never raises, but a custom `build` might
        log.exception("build raised for job %s", job_id)
        result = failed_payload(job_id, design_id, "build_error", "Something went wrong while building this design", {"error": str(exc)})
    sink.publish_result(result)
    channel.basic_ack(delivery_tag=tag)
    log.info("job %s -> %s", job_id, "design.completed" if is_completed(result) else f"design.failed/{result.get('code')}")
    return result


def run(amqp_url: str | None = None, prefetch: int = 1) -> None:  # pragma: no cover - needs a broker
    import pika

    url = amqp_url or os.environ.get("AAKAR_AMQP_URL", DEFAULT_AMQP_URL)
    delay = 1.0
    while True:
        try:
            connection = pika.BlockingConnection(pika.URLParameters(url))
        except Exception as exc:
            log.warning("cannot connect to %s (%s); retrying in %.0fs", url.split("@")[-1], exc, delay)
            time.sleep(delay)
            delay = min(delay * 2, 30.0)
            continue
        delay = 1.0
        channel = connection.channel()
        declare_topology(channel)
        channel.basic_qos(prefetch_count=prefetch)
        channel.basic_consume(queue=QUEUE, on_message_callback=handle_message)
        log.info("consuming %s on %s", QUEUE, url.split("@")[-1])
        try:
            channel.start_consuming()
        except KeyboardInterrupt:
            channel.stop_consuming()
            connection.close()
            return
        except Exception as exc:
            log.warning("consumer loop ended (%s); reconnecting", exc)
            try:
                connection.close()
            except Exception:
                pass
            time.sleep(1.0)
