import { formatPaise } from "@aakar/design-tokens";
import type { PriceBreakdown as Breakdown } from "@/lib/api/types";

export interface PriceBreakdownProps {
  price?: Breakdown;
  loading?: boolean;
  error?: string;
  className?: string;
}

/**
 * Transparent price lines + shipping + total (Checkout board). Lines render generically from the API
 * (material, print time, finishing, packaging, and for Avatars `hardware` and `setup`), so a new line
 * code needs no UI change. A family minimum that lifted the subtotal gets a footnote.
 */
export function PriceBreakdown({ price, loading, error, className }: PriceBreakdownProps) {
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
          {typeof price.minimum_subtotal_paise === "number" && (
            <p className="text-[11px] leading-snug text-surface-muted">
              Studio minimum for this piece applies · {formatPaise(price.minimum_subtotal_paise)}
            </p>
          )}
          <div className="flex justify-between gap-3">
            <span className="text-surface-muted">{price.shipping_label ?? "Shipping"}</span>
            <span className={price.shipping_paise === 0 ? "font-semibold text-success" : ""}>{price.shipping_paise === 0 ? "Free" : formatPaise(price.shipping_paise)}</span>
          </div>
          <div className="flex justify-between gap-3 border-t border-surface-border pt-2.5 text-[15px] font-bold">
            <span>Total</span>
            <span>{formatPaise(price.total_paise)}</span>
          </div>
        </>
      ) : error ? (
        <p className="text-surface-muted">{error}</p>
      ) : (
        <p className="text-surface-muted">{loading ? "Pricing…" : "Price appears once the piece is checked."}</p>
      )}
    </section>
  );
}
