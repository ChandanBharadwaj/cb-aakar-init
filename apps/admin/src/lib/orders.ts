// Order status vocabulary for the queue and the detail page (contract: OrderStatus, OrderStage; PLAN §11.1).
import type { OrderStage, OrderStatus } from "@/lib/api/types";

export const STATUS_LABEL: Record<OrderStatus, string> = {
  pending_payment: "Awaiting payment",
  confirmed: "Confirmed",
  queued: "Queued",
  slicing: "Slicing",
  printing: "Printing",
  finishing: "Finishing",
  qc: "QC",
  packed: "Packed",
  shipped: "Shipped",
  delivered: "Delivered",
  cancelled: "Cancelled",
  on_hold: "On hold",
  reprint: "Reprint",
};

export const STAGE_LABEL: Record<OrderStage, string> = {
  payment: "Payment",
  queued: "Queued",
  slicing: "Slicing",
  printing: "Printing",
  sanding: "Sanding",
  shipped: "Shipped",
  delivered: "Delivered",
  cancelled: "Cancelled",
};

/** The happy path, in order. Used by the detail-page timeline. */
export const LINEAR_PATH: OrderStatus[] = ["confirmed", "queued", "slicing", "printing", "finishing", "qc", "packed", "shipped", "delivered"];

/** Stages whose advance form asks for studio / printer bay / layer details (the tracking board reads them). */
export const PRODUCTION_STATUSES: ReadonlySet<OrderStatus> = new Set<OrderStatus>(["slicing", "printing", "finishing", "qc", "reprint"]);

/** Queue filter pills: one per status group, comma-joined into the `status` query parameter. */
export interface StatusGroup {
  id: string;
  label: string;
  statuses: OrderStatus[];
}

export const STATUS_GROUPS: readonly StatusGroup[] = [
  { id: "all", label: "All", statuses: [] },
  { id: "pending_payment", label: "Awaiting payment", statuses: ["pending_payment"] },
  { id: "queued", label: "Queued", statuses: ["confirmed", "queued"] },
  { id: "production", label: "In production", statuses: ["slicing", "printing", "finishing", "qc", "reprint"] },
  { id: "packed", label: "Packed", statuses: ["packed"] },
  { id: "shipped", label: "Shipped", statuses: ["shipped"] },
  { id: "delivered", label: "Delivered", statuses: ["delivered"] },
  { id: "on_hold", label: "On hold", statuses: ["on_hold"] },
  { id: "cancelled", label: "Cancelled", statuses: ["cancelled"] },
];

export function groupFor(status: OrderStatus): StatusGroup | undefined {
  return STATUS_GROUPS.find((g) => g.statuses.includes(status));
}

/** Pill colour per status: neutral for waiting, accent for work in progress, sage for done, danger for stopped. */
export type Tone = "neutral" | "accent" | "info" | "success" | "warning" | "danger";

export const STATUS_TONE: Record<OrderStatus, Tone> = {
  pending_payment: "neutral",
  confirmed: "info",
  queued: "info",
  slicing: "accent",
  printing: "accent",
  finishing: "accent",
  qc: "accent",
  packed: "info",
  shipped: "info",
  delivered: "success",
  cancelled: "danger",
  on_hold: "warning",
  reprint: "warning",
};

/** Button copy for an advance action, as a verb. */
export function actionLabel(to: OrderStatus, from: OrderStatus): string {
  if (to === "on_hold") return "Put on hold";
  if (from === "on_hold") return `Resume · ${STATUS_LABEL[to]}`;
  if (to === "cancelled") return "Cancel order";
  if (to === "reprint") return "Send to reprint";
  if (to === "queued") return "Accept into queue";
  if (to === "slicing") return "Start slicing";
  if (to === "printing") return from === "reprint" ? "Reprint started" : "Start printing";
  if (to === "finishing") return "Print done · finishing";
  if (to === "qc") return "Finishing done · QC";
  if (to === "packed") return "QC passed · packed";
  if (to === "shipped") return "Handed to carrier";
  if (to === "delivered") return "Mark delivered";
  return STATUS_LABEL[to];
}

/** Destructive or interrupting moves get the secondary button and a confirm step. */
export function isDisruptive(to: OrderStatus): boolean {
  return to === "cancelled" || to === "on_hold" || to === "reprint";
}

/** Packaging card is generated at PACKED (PLAN §11.4). */
export function isPackedOrLater(status: OrderStatus): boolean {
  return status === "packed" || status === "shipped" || status === "delivered";
}

/** Position on the linear timeline; on_hold/reprint/cancelled sit beside the stage they interrupted. */
export function timelineIndex(status: OrderStatus): number {
  return LINEAR_PATH.indexOf(status);
}
