"use client";

import type { PaymentMethod } from "@/lib/api/types";

export const PAYMENT_METHODS: { id: PaymentMethod; label: string; caption: string }[] = [
  { id: "upi", label: "UPI", caption: "Google Pay · PhonePe · Paytm · any UPI ID" },
  { id: "card", label: "Card", caption: "Visa · Mastercard · RuPay" },
  { id: "netbanking", label: "Net banking", caption: "All major banks" },
];

export function paymentMethodLabel(method: string | null | undefined): string {
  return PAYMENT_METHODS.find((m) => m.id === method)?.label ?? (method ? method.replace(/_/g, " ") : "—");
}

export interface PaymentMethodChipsProps {
  value: PaymentMethod;
  onChange(method: PaymentMethod): void;
  disabled?: boolean;
  /** Stack with captions (checkout) or a compact row (pay page). */
  compact?: boolean;
}

/** UPI · Card · Net banking. Cosmetic until a real gateway shows its own sheet; the mock records the choice. */
export function PaymentMethodChips({ value, onChange, disabled, compact }: PaymentMethodChipsProps) {
  return (
    <div role="radiogroup" aria-label="Pay with" className={compact ? "flex flex-wrap gap-2" : "grid gap-2"}>
      {PAYMENT_METHODS.map((m) => {
        const checked = m.id === value;
        return compact ? (
          <button key={m.id} type="button" role="radio" aria-checked={checked} className="ak-chip" onClick={() => onChange(m.id)} disabled={disabled}>
            {m.label}
          </button>
        ) : (
          <button
            key={m.id}
            type="button"
            role="radio"
            aria-checked={checked}
            disabled={disabled}
            onClick={() => onChange(m.id)}
            className={`flex items-center justify-between gap-3 rounded-control border px-3.5 py-2.5 text-left transition-colors duration-base ease-ak ${checked ? "border-surface-accent bg-surface-card ak-ring-accent" : "border-surface-border bg-surface-card/60 hover:border-surface-accent"}`}
          >
            <span className="grid gap-0.5">
              <span className="text-sm font-semibold">{m.label}</span>
              <span className="text-[11px] text-surface-muted">{m.caption}</span>
            </span>
            <span className={`grid h-5 w-5 flex-none place-items-center rounded-full border ${checked ? "border-surface-accent" : "border-surface-border"}`} aria-hidden="true">
              {checked && <span className="h-2.5 w-2.5 rounded-full bg-surface-accent" />}
            </span>
          </button>
        );
      })}
    </div>
  );
}
