import { formatPaise } from "@aakar/design-tokens";
import type { PriceBreakdown as Breakdown } from "@/lib/api/types";

export interface PriceBreakdownProps {
  price?: Breakdown;
  loading?: boolean;
  error?: string;
  className?: string;
  /** Line under the total, e.g. the policy version the numbers came from. */
  footnote?: string;
}

/** Price lines + shipping ("Free" when 0) + total. Same component as the storefront's checkout card. */
export function PriceBreakdown({ price, loading, error, className, footnote }: PriceBreakdownProps) {
  return (
    <section className={["ak-card grid gap-2.5 p-4 text-[13px]", loading ? "opacity-70" : "", className].filter(Boolean).join(" ")} aria-label="Price" aria-busy={loading}>
      {price ? (
        <>
          {price.lines.map((line) => (
            <div key={`${line.code}-${line.label}`} className="flex justify-between gap-3">
              <span className="text-surface-muted" title={line.detail}>
                {line.label}
              </span>
              <span>{formatPaise(line.amount_paise)}</span>
            </div>
          ))}
          <div className="flex justify-between gap-3 border-t border-surface-border pt-2.5">
            <span className="text-surface-muted">Subtotal · rounded</span>
            <span>{formatPaise(price.subtotal_paise)}</span>
          </div>
          <div className="flex justify-between gap-3">
            <span className="text-surface-muted">{price.shipping_label ?? "Shipping"}</span>
            <span className={price.shipping_paise === 0 ? "font-semibold text-success" : ""}>{price.shipping_paise === 0 ? "Free" : formatPaise(price.shipping_paise)}</span>
          </div>
          <div className="flex justify-between gap-3 border-t border-surface-border pt-2.5 text-[15px] font-bold">
            <span>Total</span>
            <span>{formatPaise(price.total_paise)}</span>
          </div>
          {footnote && <p className="text-[11px] text-surface-muted">{footnote}</p>}
        </>
      ) : error ? (
        <p className="text-surface-muted">{error}</p>
      ) : (
        <p className="text-surface-muted">{loading ? "Pricing…" : "The breakdown appears once the API has priced the sample."}</p>
      )}
    </section>
  );
}
