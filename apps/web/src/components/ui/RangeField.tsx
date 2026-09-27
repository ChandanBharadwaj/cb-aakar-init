"use client";

import { useId } from "react";

export interface RangeFieldProps {
  label: string;
  value: number;
  min: number;
  max: number;
  step?: number;
  onChange(value: number): void;
  /** Readout next to the label, e.g. "80 mm". */
  format?(value: number): string;
  /** A line under the slider, e.g. "about the size of a mug". */
  hint?: string;
  disabled?: boolean;
  className?: string;
}

/** One labelled slider with a readout and end marks, in the studio's style (`ParamSliders` for descriptor params). */
export function RangeField({ label, value, min, max, step = 1, onChange, format = (v) => String(v), hint, disabled, className }: RangeFieldProps) {
  const id = useId();
  const fill = max > min ? ((value - min) / (max - min)) * 100 : 0;
  return (
    <div className={["grid gap-0.5", className].filter(Boolean).join(" ")}>
      <label htmlFor={id} className="flex items-baseline justify-between gap-3 text-xs text-surface-muted">
        <span>{label}</span>
        <output htmlFor={id} className="font-semibold text-surface-text">
          {format(value)}
        </output>
      </label>
      <input
        id={id}
        type="range"
        min={min}
        max={max}
        step={step}
        value={value}
        disabled={disabled}
        aria-valuetext={hint ? `${format(value)}, ${hint}` : format(value)}
        onChange={(e) => onChange(Number(e.target.value))}
        className="ak-range"
        style={{ ["--ak-range-fill" as string]: `${fill}%` }}
      />
      <div className="flex justify-between text-[10px] text-surface-muted" aria-hidden="true">
        <span>{format(min)}</span>
        {hint && <span className="text-surface-text">{hint}</span>}
        <span>{format(max)}</span>
      </div>
    </div>
  );
}
