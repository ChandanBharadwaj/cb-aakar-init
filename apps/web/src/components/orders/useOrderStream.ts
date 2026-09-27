"use client";

import { useRef } from "react";
import { api } from "@/lib/api/client";
import type { Order, OrderEvent } from "@/lib/api/types";
import { isFinalStage, latestEvent } from "@/lib/orders";
import { useEventStream, type EventStreamState } from "@/lib/useEventStream";

export interface OrderStreamHandlers {
  onEvent(ev: OrderEvent): void;
  /** Fresh order from the polling fallback (carries eta, shipment, payment). */
  onOrder?(order: Order): void;
}

/**
 * Follows `GET /api/orders/{id}/events` (SSE `event: stage`, OrderEvent data) with the bearer
 * token; closes on delivered / cancelled; polls `GET /api/orders/{id}` when the stream is gone.
 */
export function useOrderStream(orderId: string | undefined, handlers: OrderStreamHandlers): EventStreamState {
  const ref = useRef(handlers);
  ref.current = handlers;

  return useEventStream<OrderEvent>(orderId ? api.orders.eventsUrl(orderId) : undefined, {
    event: "stage",
    onEvent: (ev) => ref.current.onEvent(ev),
    isTerminal: (ev) => isFinalStage(ev.stage),
    pollMs: 4000,
    poll: async () => {
      if (!orderId) return undefined;
      const order = await api.orders.get(orderId);
      ref.current.onOrder?.(order);
      return (
        latestEvent(order.events) ?? {
          sequence: 0,
          status: order.status,
          stage: order.stage,
          message: "",
          at: order.placed_at,
        }
      );
    },
  });
}
