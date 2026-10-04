"use client";

import { useEffect, useState } from "react";
import { api, toProblem } from "@/lib/api/client";
import type { Motif, Problem } from "@/lib/api/types";

/** One request per tab: the composer, the studio and every Buti tab share the library. A failure is retried on demand. */
let shared: Promise<Motif[]> | undefined;

function loadLibrary(): Promise<Motif[]> {
  if (!shared) {
    const pending = api.motifs.list();
    shared = pending;
    pending.catch(() => {
      if (shared === pending) shared = undefined;
    });
  }
  return shared;
}

export interface MotifLibrary {
  motifs?: Motif[];
  problem?: Problem;
  loading: boolean;
  /** Ask again after a failure. */
  retry(): void;
}

/** The Buti library (`GET /api/motifs`), fetched lazily in the browser when `enabled`. */
export function useMotifs(enabled = true): MotifLibrary {
  const [state, setState] = useState<{ motifs?: Motif[]; problem?: Problem }>({});
  const [round, setRound] = useState(0);

  useEffect(() => {
    if (!enabled) return;
    let cancelled = false;
    loadLibrary()
      .then((motifs) => {
        if (!cancelled) setState({ motifs });
      })
      .catch((err) => {
        if (!cancelled) setState({ problem: toProblem(err) });
      });
    return () => {
      cancelled = true;
    };
  }, [enabled, round]);

  return {
    ...state,
    loading: enabled && !state.motifs && !state.problem,
    retry: () => {
      setState({});
      setRound((r) => r + 1);
    },
  };
}
