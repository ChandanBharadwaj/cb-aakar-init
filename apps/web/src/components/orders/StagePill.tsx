import type { OrderStage } from "@/lib/api/types";
import { STAGE_LABEL } from "@/lib/orders";

const TONE: Record<OrderStage, string> = {
  payment: "bg-marigold/25 text-ink",
  queued: "bg-indigo/10 text-indigo",
  slicing: "bg-indigo/10 text-indigo",
  printing: "bg-terracotta/15 text-terracotta-deep",
  sanding: "bg-terracotta/15 text-terracotta-deep",
  shipped: "bg-sage/20 text-[#4F6B4D]",
  delivered: "bg-sage text-cream",
  cancelled: "bg-danger/15 text-danger",
};

export function StagePill({ stage, live, className }: { stage: OrderStage; live?: boolean; className?: string }) {
  return (
    <span className={["inline-flex items-center gap-1.5 rounded-pill px-2.5 py-1 text-[11px] font-bold uppercase tracking-wider", TONE[stage], className].filter(Boolean).join(" ")}>
      {live && <span className="h-1.5 w-1.5 rounded-full bg-current animate-pulse-soft" aria-hidden="true" />}
      {STAGE_LABEL[stage]}
    </span>
  );
}
