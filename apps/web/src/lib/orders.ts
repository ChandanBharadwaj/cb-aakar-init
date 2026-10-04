// Customer-facing order helpers for the tracking board (06) and the orders list.
import type { OrderEvent, OrderStage, OrderStatus, PaymentStatus } from "@/lib/api/types";

/** The stages shown on the tracking board, in order. `payment` and `cancelled` sit outside the line. */
export const ORDER_STAGES = ["queued", "slicing", "printing", "sanding", "shipped", "delivered"] as const satisfies readonly OrderStage[];

export const STAGE_LABEL: Record<OrderStage, string> = {
  payment: "Awaiting payment",
  queued: "Queued",
  slicing: "Slicing",
  printing: "Printing",
  sanding: "Sanding",
  shipped: "Shipped",
  delivered: "Delivered",
  cancelled: "Cancelled",
};

/** One line under the stage title on the live card. Craft, not CAD. */
export const STAGE_BLURB: Record<OrderStage, string> = {
  payment: "Your piece is reserved. Finish paying and the studio picks it up.",
  queued: "In the queue at the studio. A printer bay is being set aside for your piece.",
  slicing: "Planning every layer of your piece before the first one is laid.",
  printing: "Your piece is taking shape, layer by layer.",
  sanding: "Hand-sanding, sealing and a last look-over by the karigar.",
  shipped: "Packed in kraft and marigold tape, on its way to you.",
  delivered: "Delivered. Designed by you. Crafted by Aakar.",
  cancelled: "This order was cancelled. Nothing was charged for it.",
};

export const STATUS_LABEL: Record<OrderStatus, string> = {
  pending_payment: "Awaiting payment",
  confirmed: "Confirmed",
  queued: "Queued",
  slicing: "Slicing",
  printing: "Printing",
  finishing: "Finishing",
  assembling: "Assembling",
  qc: "Quality check",
  packed: "Packed",
  shipped: "Shipped",
  delivered: "Delivered",
  cancelled: "Cancelled",
  on_hold: "On hold",
  reprint: "Reprinting",
};

export const PAYMENT_LABEL: Record<PaymentStatus, string> = {
  created: "Not paid yet",
  pending: "Payment in progress",
  succeeded: "Paid",
  failed: "Payment failed",
  refunded: "Refunded",
};

/** Index on the board line, or -1 for stages outside it (payment, cancelled). */
export function stageIndex(stage: OrderStage): number {
  return (ORDER_STAGES as readonly string[]).indexOf(stage);
}

export function isFinalStage(stage: OrderStage): boolean {
  return stage === "delivered" || stage === "cancelled";
}

const WEEKDAYS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

function parseDate(value: string): Date | undefined {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  const d = m ? new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3])) : new Date(value);
  return Number.isNaN(d.getTime()) ? undefined : d;
}

/** "2026-10-02" → "Thu, 2 Oct" (the board's "Arrives Thu, 2 Oct"). */
export function formatArrives(date: string | null | undefined): string | undefined {
  if (!date) return undefined;
  const d = parseDate(date);
  if (!d) return undefined;
  return `${WEEKDAYS[d.getDay()]}, ${d.getDate()} ${MONTHS[d.getMonth()]}`;
}

/** ISO date-time → "27 Sep 2026, 4:12 pm". */
export function formatPlaced(iso: string): string {
  const d = parseDate(iso);
  if (!d) return iso;
  let h = d.getHours();
  const ampm = h >= 12 ? "pm" : "am";
  h = h % 12 || 12;
  return `${d.getDate()} ${MONTHS[d.getMonth()]} ${d.getFullYear()}, ${h}:${String(d.getMinutes()).padStart(2, "0")} ${ampm}`;
}

/** The studio facts the tracking board shows when an event carries them. */
export interface LiveDetail {
  studio?: string;
  printerBay?: string;
  layerHeightMm?: number;
  layer?: number;
  layersTotal?: number;
}

function str(v: unknown): string | undefined {
  return typeof v === "string" && v.trim() ? v : undefined;
}
function num(v: unknown): number | undefined {
  return typeof v === "number" && Number.isFinite(v) ? v : undefined;
}

/** Reads the free-form `detail` of an OrderEvent into the fields the board knows. */
export function liveDetail(ev: OrderEvent | undefined): LiveDetail | undefined {
  const d = ev?.detail;
  if (!d) return undefined;
  const out: LiveDetail = {
    studio: str(d.studio),
    printerBay: str(d.printer_bay),
    layerHeightMm: num(d.layer_height_mm),
    layer: num(d.layer),
    layersTotal: num(d.layers_total),
  };
  return Object.values(out).some((v) => v !== undefined) ? out : undefined;
}

/** Latest event, by sequence. */
export function latestEvent(events: OrderEvent[] | undefined): OrderEvent | undefined {
  if (!events || events.length === 0) return undefined;
  return events.reduce((a, b) => (b.sequence > a.sequence ? b : a));
}
