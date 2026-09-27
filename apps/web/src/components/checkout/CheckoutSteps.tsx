"use client";

export type CheckoutStep = "review" | "stability" | "pay" | "track";

export const CHECKOUT_STEPS: { id: CheckoutStep; label: string; hint: string }[] = [
  { id: "review", label: "Review", hint: "Your pieces and finishes" },
  { id: "stability", label: "Stability", hint: "Balance, walls and fit" },
  { id: "pay", label: "Pay", hint: "Address and payment" },
  { id: "track", label: "Track", hint: "Live from the studio" },
];

export interface CheckoutStepsProps {
  current: CheckoutStep;
  /** Furthest step the customer has reached; earlier steps are clickable. */
  reached: CheckoutStep;
  onSelect?(step: CheckoutStep): void;
}

/** The board's "1 Review · 2 Stability · 3 Pay · 4 Track" stepper. */
export function CheckoutSteps({ current, reached, onSelect }: CheckoutStepsProps) {
  const cur = CHECKOUT_STEPS.findIndex((s) => s.id === current);
  const max = CHECKOUT_STEPS.findIndex((s) => s.id === reached);
  return (
    <ol className="flex items-center gap-1 text-[13px] font-semibold sm:gap-2" aria-label="Checkout steps">
      {CHECKOUT_STEPS.map((s, i) => {
        const done = i < cur;
        const active = i === cur;
        const clickable = Boolean(onSelect) && i <= max && s.id !== "track" && !active;
        const circle = done ? "bg-surface-accent text-surface-bg" : active ? "bg-surface-text text-surface-bg" : "border border-surface-border text-surface-muted";
        const inner = (
          <>
            <span className={`grid h-6 w-6 flex-none place-items-center rounded-full text-[11px] ${circle}`} aria-hidden="true">
              {done ? "✓" : i + 1}
            </span>
            <span className={active ? "text-surface-text" : done ? "text-surface-text" : "text-surface-muted"}>{s.label}</span>
          </>
        );
        return (
          <li key={s.id} className="flex items-center gap-1 sm:gap-2" aria-current={active ? "step" : undefined}>
            {clickable ? (
              <button type="button" onClick={() => onSelect?.(s.id)} className="flex items-center gap-2 rounded-pill px-1 py-0.5 hover:text-surface-accent">
                {inner}
              </button>
            ) : (
              <span className="flex items-center gap-2 px-1 py-0.5">{inner}</span>
            )}
            {i < CHECKOUT_STEPS.length - 1 && <span className={`h-px w-4 sm:w-8 ${done ? "bg-surface-accent" : "bg-surface-border"}`} aria-hidden="true" />}
          </li>
        );
      })}
    </ol>
  );
}
