"use client";

import { useEffect, useState } from "react";
import { api, isApiError } from "@/lib/api/client";
import type { Upload } from "@/lib/api/types";
import { REVIEW_POLL_LIMIT_MS, REVIEW_POLL_MS } from "@/lib/uploads";
import { useDesignStore } from "@/store/design";

export interface UploadReview {
  /** Undefined when this tab never saw the upload (a feature from a stored spec, which the API already accepted). */
  status?: Upload["status"];
  /** The file's own name, when this tab uploaded it. */
  name?: string;
  /** A preview URL once the file is cleared. */
  url?: string;
  /** With a reviewer, and still being polled. */
  checking: boolean;
  /** With a reviewer after two minutes of polling (or the API lost track of it): polling has stopped. */
  stalled: boolean;
  rejected: boolean;
  /** Why it was turned down: the API's words or the default copy. */
  message?: string;
  /** Start another two minutes of polling after a stall. */
  recheck(): void;
}

/**
 * Follows an upload the content scanner sent to a reviewer: while its status is `pending_review`, polls
 * `GET /api/uploads/{id}` every 4 s and records each answer in the design store, stopping when the file is
 * cleared or turned down, after two minutes, or on unmount.
 */
export function useUploadReview(uploadId: string | undefined): UploadReview {
  const note = useDesignStore((s) => (uploadId ? s.uploads[uploadId] : undefined));
  const pending = note?.status === "pending_review";
  const [round, setRound] = useState(0);
  const [stalledId, setStalledId] = useState<string>();

  useEffect(() => {
    if (!uploadId || !pending) return;
    let cancelled = false;
    let timer: ReturnType<typeof setTimeout> | undefined;
    const deadline = Date.now() + REVIEW_POLL_LIMIT_MS;
    const tick = async () => {
      try {
        const fresh = await api.uploads.get(uploadId);
        if (cancelled) return;
        useDesignStore.getState().rememberUpload(fresh);
        if (fresh.status !== "pending_review") return;
      } catch (err) {
        if (cancelled) return;
        // Not ours any more, or gone: stop and let the customer check again or pick another file.
        if (isApiError(err) && err.status === 404) {
          setStalledId(uploadId);
          return;
        }
        // Anything else is a missed poll; the next tick tries again.
      }
      if (Date.now() + REVIEW_POLL_MS > deadline) {
        setStalledId(uploadId);
        return;
      }
      timer = setTimeout(tick, REVIEW_POLL_MS);
    };
    timer = setTimeout(tick, REVIEW_POLL_MS);
    return () => {
      cancelled = true;
      if (timer) clearTimeout(timer);
    };
  }, [uploadId, pending, round]);

  const stalled = pending && stalledId !== undefined && stalledId === uploadId;
  return {
    status: note?.status,
    name: note?.name,
    url: note?.url,
    checking: pending && !stalled,
    stalled,
    rejected: note?.status === "rejected",
    message: note?.message,
    recheck: () => {
      setStalledId(undefined);
      setRound((r) => r + 1);
    },
  };
}
