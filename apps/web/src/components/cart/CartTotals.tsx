import { formatPaise } from "@aakar/design-tokens";

export interface CartTotalsProps {
  subtotalPaise: number;
  shippingPaise: number;
  shippingLabel?: string;
  totalPaise: number;
  className?: string;
}

/** Subtotal · shipping · total, the Checkout board's price lines at cart level. */
export function CartTotals({ subtotalPaise, shippingPaise, shippingLabel, totalPaise, className }: CartTotalsProps) {
  return (
    <dl className={["grid gap-2 text-[13px]", className].filter(Boolean).join(" ")}>
      <div className="flex justify-between gap-3">
        <dt className="text-surface-muted">Subtotal</dt>
        <dd>{formatPaise(subtotalPaise)}</dd>
      </div>
      <div className="flex justify-between gap-3">
        <dt className="text-surface-muted">{shippingLabel ?? "Shipping"}</dt>
        <dd className={shippingPaise === 0 ? "font-semibold text-success" : ""}>{shippingPaise === 0 ? "Free" : formatPaise(shippingPaise)}</dd>
      </div>
      <div className="flex justify-between gap-3 border-t border-surface-border pt-2.5 text-[15px] font-bold">
        <dt>Total</dt>
        <dd>{formatPaise(totalPaise)}</dd>
      </div>
    </dl>
  );
}
