"use client";

export interface QtyStepperProps {
  value: number;
  onChange(value: number): void;
  min?: number;
  max?: number;
  disabled?: boolean;
  label?: string;
}

/** − n + with the contract's 1–20 range. */
export function QtyStepper({ value, onChange, min = 1, max = 20, disabled, label = "Quantity" }: QtyStepperProps) {
  const set = (n: number) => onChange(Math.min(max, Math.max(min, n)));
  return (
    <div className="inline-flex items-center overflow-hidden rounded-pill border border-surface-border bg-surface-card text-sm" role="group" aria-label={label}>
      <button type="button" className="grid h-9 w-9 place-items-center text-surface-muted hover:text-surface-text disabled:opacity-40" onClick={() => set(value - 1)} disabled={disabled || value <= min} aria-label="One fewer">
        −
      </button>
      <input
        type="number"
        inputMode="numeric"
        min={min}
        max={max}
        value={value}
        disabled={disabled}
        onChange={(e) => {
          const n = Number(e.target.value);
          if (Number.isFinite(n) && n >= min && n <= max) onChange(n);
        }}
        className="w-9 bg-transparent text-center font-semibold outline-none [appearance:textfield] [&::-webkit-inner-spin-button]:appearance-none [&::-webkit-outer-spin-button]:appearance-none"
        aria-label={label}
      />
      <button type="button" className="grid h-9 w-9 place-items-center text-surface-muted hover:text-surface-text disabled:opacity-40" onClick={() => set(value + 1)} disabled={disabled || value >= max} aria-label="One more">
        +
      </button>
    </div>
  );
}
