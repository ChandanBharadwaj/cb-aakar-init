"use client";

import { useEffect, useRef } from "react";
import { api, isApiError } from "@/lib/api/client";
import type { JobStageEvent } from "@/lib/api/types";

export interface JobStreamHandlers {
  onEvent(ev: JobStageEvent): void;
  /** Called once, with the terminal `ready` or `failed` event. */
  onDone(ev: JobStageEvent): void;
}

const POLL_MS = 2500;

/**
 * Follows `GET /api/jobs/{id}/events` (SSE, `event: stage`). The browser reconnects on its own
 * and sends `Last-Event-ID`; if the stream is closed for good before a terminal stage we poll
 * `GET /api/jobs/{id}` instead. The stream is closed explicitly on `ready` / `failed` because
 * a clean server close would otherwise trigger another reconnect.
 */
export function useJobStream(jobId: string | undefined, handlers: JobStreamHandlers): void {
  const ref = useRef(handlers);
  ref.current = handlers;

  useEffect(() => {
    if (!jobId || typeof EventSource === "undefined") return;
    let done = false;
    let pollTimer: ReturnType<typeof setTimeout> | undefined;
    let polling = false;
    const es = new EventSource(api.jobs.eventsUrl(jobId));

    const finish = (ev: JobStageEvent) => {
      if (done) return;
      done = true;
      es.close();
      if (pollTimer) clearTimeout(pollTimer);
      ref.current.onDone(ev);
    };

    const poll = async () => {
      if (done) return;
      try {
        const job = await api.jobs.get(jobId);
        const stage = job.status === "failed" ? "failed" : job.status === "succeeded" ? "ready" : job.stage;
        const ev: JobStageEvent = {
          job_id: job.id,
          sequence: 0,
          stage,
          message: job.message ?? "",
          version_id: job.version_id,
          error_code: job.error_code,
          at: job.finished_at ?? job.started_at ?? job.created_at,
        };
        ref.current.onEvent(ev);
        if (stage === "ready" || stage === "failed") {
          finish(ev);
          return;
        }
      } catch (err) {
        if (isApiError(err) && err.status === 404) {
          finish({ job_id: jobId, sequence: 0, stage: "failed", message: "This job isn't known to the studio any more.", error_code: "not_found", at: new Date().toISOString() });
          return;
        }
        /* otherwise keep polling; the API may be restarting */
      }
      pollTimer = setTimeout(poll, POLL_MS);
    };

    es.addEventListener("stage", (raw) => {
      if (done) return;
      let ev: JobStageEvent;
      try {
        ev = JSON.parse((raw as MessageEvent<string>).data) as JobStageEvent;
      } catch {
        return;
      }
      ref.current.onEvent(ev);
      if (ev.stage === "ready" || ev.stage === "failed") finish(ev);
    });

    es.onerror = () => {
      if (done || polling) return;
      if (es.readyState === EventSource.CLOSED) {
        polling = true;
        void poll();
      }
    };

    return () => {
      done = true;
      es.close();
      if (pollTimer) clearTimeout(pollTimer);
    };
  }, [jobId]);
}
