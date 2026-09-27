"use client";

export interface SegmentedOption<T extends string> {
  value: T;
  label: string;
  description?: string;
}

export interface SegmentedProps<T extends string> {
  label: string;
  options: readonly SegmentedOption<T>[];
  value: T;
  onChange(value: T): void;
  disabled?: boolean;
  className?: string;
}

/** Two or three exclusive choices as chips ("Raised · Cut in", "As uploaded · Lay flat"): a labelled radio group. */
export function Segmented<T extends string>({ label, options, value, onChange, disabled, className }: SegmentedProps<T>) {
  return (
    <div className={["flex flex-wrap items-center justify-between gap-2", className].filter(Boolean).join(" ")}>
      <span className="text-xs text-surface-muted">{label}</span>
      <div role="radiogroup" aria-label={label} className="flex gap-1.5">
        {options.map((o) => (
          <button
            key={o.value}
            type="button"
            role="radio"
            aria-checked={o.value === value}
            className="ak-chip min-h-8 px-3 py-1 text-[11.5px]"
            title={o.description}
            disabled={disabled}
            onClick={() => onChange(o.value)}
          >
            {o.label}
          </button>
        ))}
      </div>
    </div>
  );
}
