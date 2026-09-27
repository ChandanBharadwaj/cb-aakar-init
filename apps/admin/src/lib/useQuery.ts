"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import type { Problem } from "@/lib/api/types";
import { toProblem } from "@/lib/api/client";

export interface QueryState<T> {
  data?: T;
  problem?: Problem & { code: string };
  loading: boolean;
  /** Re-run the fetch; keeps the previous data on screen while it runs. */
  reload(): Promise<void>;
  /** Replace the data locally (after a successful write) without refetching. */
  setData(next: T | ((prev: T | undefined) => T | undefined)): void;
}

/**
 * Minimal client-side data hook for pages that read from the management API.
 * `key` identifies the query; a change in it refetches. The fetcher is called with no arguments.
 */
export function useQuery<T>(fetcher: () => Promise<T>, key: string): QueryState<T> {
  const [data, setData] = useState<T>();
  const [problem, setProblem] = useState<Problem & { code: string }>();
  const [loading, setLoading] = useState(true);
  const fetcherRef = useRef(fetcher);
  fetcherRef.current = fetcher;
  const seq = useRef(0);

  const run = useCallback(async () => {
    const mine = ++seq.current;
    setLoading(true);
    try {
      const next = await fetcherRef.current();
      if (mine !== seq.current) return;
      setData(next);
      setProblem(undefined);
    } catch (err) {
      if (mine !== seq.current) return;
      setProblem(toProblem(err));
    } finally {
      if (mine === seq.current) setLoading(false);
    }
  }, []);

  useEffect(() => {
    void run();
    // `key` is the dependency by design: callers encode everything the fetch depends on in it.
  }, [key, run]);

  return { data, problem, loading, reload: run, setData };
}
