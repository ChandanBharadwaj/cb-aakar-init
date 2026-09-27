"use client";

import { useRef } from "react";
import { api, isApiError } from "@/lib/api/client";
import type { JobStageEvent } from "@/lib/api/types";
import { useEventStream, type EventStreamState } from "@/lib/useEventStream";

export interface JobStreamHandlers {
  onEvent(ev: JobStageEvent): void;
  /** Called once, with the terminal `ready` or `failed` event. */
  onDone(ev: JobStageEvent): void;
}

/**
 * Follows `GET /api/jobs/{id}/events` (SSE, `event: stage`) through `useEventStream`, which
 * reconnects with `Last-Event-ID` and, if the stream is closed for good before a terminal
 * stage, polls `GET /api/jobs/{id}` instead. Returns `{ live }` for a LIVE badge.
 */
export function useJobStream(jobId: string | undefined, handlers: JobStreamHandlers): EventStreamState {
  const ref = useRef(handlers);
  ref.current = handlers;

  return useEventStream<JobStageEvent>(jobId ? api.jobs.eventsUrl(jobId) : undefined, {
    event: "stage",
    onEvent: (ev) => ref.current.onEvent(ev),
    isTerminal: (ev) => ev.stage === "ready" || ev.stage === "failed",
    onDone: (ev) => ref.current.onDone(ev),
    poll: async () => {
      if (!jobId) return undefined;
      try {
        const job = await api.jobs.get(jobId);
        const stage = job.status === "failed" ? "failed" : job.status === "succeeded" ? "ready" : job.stage;
        return {
          job_id: job.id,
          sequence: 0,
          stage,
          message: job.message ?? "",
          version_id: job.version_id,
          error_code: job.error_code,
          at: job.finished_at ?? job.started_at ?? job.created_at,
        };
      } catch (err) {
        if (isApiError(err) && err.status === 404) {
          return { job_id: jobId, sequence: 0, stage: "failed", message: "This job isn't known to the studio any more.", error_code: "not_found", at: new Date().toISOString() };
        }
        throw err;
      }
    },
  });
}
