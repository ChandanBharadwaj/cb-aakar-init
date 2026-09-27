"use client";

import { useEffect, useRef, useState } from "react";
import { identityHeaders } from "@/lib/identity";

export interface EventStreamOptions<T> {
  /** SSE event name to listen for; the Aakar streams use `stage`. */
  event?: string;
  onEvent(ev: T): void;
  /** True for the event that ends the stream (`ready`/`failed`, `delivered`/`cancelled`). */
  isTerminal(ev: T): boolean;
  /** Called once, with the terminal event. */
  onDone?(ev: T): void;
  /**
   * Fallback while the stream is down: fetch the current state and return it as an event
   * (or undefined to keep waiting). Throwing keeps polling; the API may be restarting.
   */
  poll?(): Promise<T | undefined>;
  pollMs?: number;
  /** Reconnects tried before falling back to polling. */
  retries?: number;
}

export interface EventStreamState {
  /** True while the stream is connected and the terminal event has not arrived. */
  live: boolean;
}

interface SseMessage {
  id?: string;
  event?: string;
  data: string;
}

/** Reads a text/event-stream body message by message until the server closes it. */
async function readSse(body: ReadableStream<Uint8Array>, onMessage: (m: SseMessage) => void): Promise<void> {
  const reader = body.getReader();
  const decoder = new TextDecoder();
  let buf = "";
  let id: string | undefined;
  let event: string | undefined;
  let data: string[] = [];
  for (;;) {
    const { value, done } = await reader.read();
    if (done) break;
    buf += decoder.decode(value, { stream: true });
    let nl: number;
    while ((nl = buf.indexOf("\n")) >= 0) {
      let line = buf.slice(0, nl);
      buf = buf.slice(nl + 1);
      if (line.endsWith("\r")) line = line.slice(0, -1);
      if (line === "") {
        if (data.length > 0) onMessage({ id, event, data: data.join("\n") });
        event = undefined;
        data = [];
        continue;
      }
      if (line.startsWith(":")) continue;
      const colon = line.indexOf(":");
      const field = colon < 0 ? line : line.slice(0, colon);
      let val = colon < 0 ? "" : line.slice(colon + 1);
      if (val.startsWith(" ")) val = val.slice(1);
      if (field === "id") id = val;
      else if (field === "event") event = val;
      else if (field === "data") data.push(val);
    }
  }
}

const sleep = (ms: number, signal: AbortSignal) =>
  new Promise<void>((resolve) => {
    const t = setTimeout(resolve, ms);
    signal.addEventListener("abort", () => {
      clearTimeout(t);
      resolve();
    });
  });

/**
 * Follows a Server-Sent Events URL on the Aakar API with `fetch` (so the identity headers,
 * including the bearer token the order stream needs, go along; `EventSource` cannot send them).
 * Reconnects with `Last-Event-ID` a few times, then falls back to `poll()` until a terminal event.
 * Options are read through a ref, so only `url` restarts the stream.
 */
export function useEventStream<T>(url: string | undefined, options: EventStreamOptions<T>): EventStreamState {
  const ref = useRef(options);
  ref.current = options;
  const [live, setLive] = useState(false);

  useEffect(() => {
    if (!url || typeof window === "undefined") return;
    const ctrl = new AbortController();
    const { signal } = ctrl;
    let done = false;
    let lastId: string | undefined;
    let pollTimer: ReturnType<typeof setTimeout> | undefined;
    const eventName = ref.current.event ?? "stage";
    const retries = ref.current.retries ?? 2;
    const pollMs = ref.current.pollMs ?? 2500;

    const finish = (ev: T) => {
      if (done) return;
      done = true;
      setLive(false);
      if (pollTimer) clearTimeout(pollTimer);
      ctrl.abort();
      ref.current.onDone?.(ev);
    };

    const handle = (ev: T) => {
      if (done) return;
      ref.current.onEvent(ev);
      if (ref.current.isTerminal(ev)) finish(ev);
    };

    const poll = async () => {
      if (done || signal.aborted) return;
      const fn = ref.current.poll;
      if (!fn) return;
      try {
        const ev = await fn();
        if (ev !== undefined) handle(ev);
      } catch {
        /* keep polling */
      }
      if (!done && !signal.aborted) pollTimer = setTimeout(poll, pollMs);
    };

    const connect = async () => {
      let attempt = 0;
      while (!done && !signal.aborted) {
        try {
          const res = await fetch(url, {
            cache: "no-store",
            signal,
            headers: { Accept: "text/event-stream", ...identityHeaders(), ...(lastId ? { "Last-Event-ID": lastId } : {}) },
          });
          if (!res.ok || !res.body) {
            // 401/404 will not fix themselves by reconnecting; let the poller report.
            if (res.status === 401 || res.status === 404) break;
            throw new Error(`stream ${res.status}`);
          }
          setLive(true);
          attempt = 0;
          await readSse(res.body, (m) => {
            if (m.id) lastId = m.id;
            if ((m.event ?? "message") !== eventName) return;
            let ev: T;
            try {
              ev = JSON.parse(m.data) as T;
            } catch {
              return;
            }
            handle(ev);
          });
          setLive(false);
          if (done || signal.aborted) return;
          // Closed without a terminal event: reconnect a couple of times, then poll.
          attempt += 1;
          if (attempt > retries) break;
          await sleep(600 * attempt, signal);
        } catch {
          setLive(false);
          if (done || signal.aborted) return;
          attempt += 1;
          if (attempt > retries) break;
          await sleep(800 * attempt, signal);
        }
      }
      if (!done && !signal.aborted) void poll();
    };

    void connect();

    return () => {
      done = true;
      ctrl.abort();
      if (pollTimer) clearTimeout(pollTimer);
      setLive(false);
    };
  }, [url]);

  return { live };
}
