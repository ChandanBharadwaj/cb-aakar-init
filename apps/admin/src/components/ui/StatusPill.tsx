import type { OrderStatus } from "@/lib/api/types";
import { STATUS_LABEL, STATUS_TONE, type Tone } from "@/lib/orders";

export interface PillProps {
  tone?: Tone;
  children: React.ReactNode;
  className?: string;
  title?: string;
}

export function Pill({ tone = "neutral", children, className, title }: PillProps) {
  return (
    <span className={["ak-pill", className].filter(Boolean).join(" ")} data-tone={tone} title={title}>
      {children}
    </span>
  );
}

/** Order status as a coloured pill. */
export function StatusPill({ status, className }: { status: OrderStatus; className?: string }) {
  return (
    <Pill tone={STATUS_TONE[status]} className={className}>
      {STATUS_LABEL[status]}
    </Pill>
  );
}
