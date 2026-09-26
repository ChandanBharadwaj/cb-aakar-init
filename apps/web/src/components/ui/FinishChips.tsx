"use client";

import { useRef } from "react";
import type { Material } from "@/lib/api/types";

export interface FinishChipsProps {
  materials: Material[];
  value?: string;
  onChange(id: string): void;
  /** Restrict to the template's allowed materials; others render disabled. */
  allowed?: string[];
  disabled?: boolean;
  className?: string;
}

/** Six finish chips with colour swatches; a keyboard-operable radio group (arrows move, Space/Enter select). */
export function FinishChips({ materials, value, onChange, allowed, disabled, className }: FinishChipsProps) {
  const refs = useRef<(HTMLButtonElement | null)[]>([]);
  const enabledIdx = materials.map((m, i) => (allowed && !allowed.includes(m.id) ? -1 : i)).filter((i) => i >= 0);

  function move(from: number, dir: 1 | -1) {
    if (enabledIdx.length === 0) return;
    const pos = enabledIdx.indexOf(from);
    const next = enabledIdx[(pos + dir + enabledIdx.length) % enabledIdx.length];
    if (next === undefined) return;
    const target = materials[next];
    if (!target) return;
    refs.current[next]?.focus();
    onChange(target.id);
  }

  const checkedIdx = materials.findIndex((m) => m.id === value);
  const checkedEnabled = checkedIdx >= 0 && enabledIdx.includes(checkedIdx);
  const firstEnabled = enabledIdx[0];

  const matte = materials.filter((m) => m.finish_class === "matte").length;
  const silk = materials.length - matte;

  return (
    <div className={["grid gap-2", className].filter(Boolean).join(" ")}>
      <div className="flex items-baseline justify-between">
        <span className="ak-label">Finish</span>
        <span className="text-[11px] text-surface-muted">
          {matte} matte · {silk} silk
        </span>
      </div>
      <div role="radiogroup" aria-label="Finish" className="flex flex-wrap gap-2">
        {materials.map((m, i) => {
          const checked = m.id === value;
          const off = disabled || (allowed ? !allowed.includes(m.id) : false);
          return (
            <button
              key={m.id}
              ref={(el) => {
                refs.current[i] = el;
              }}
              type="button"
              role="radio"
              aria-checked={checked}
              tabIndex={(checkedEnabled && checked) || (!checkedEnabled && i === firstEnabled) ? 0 : -1}
              disabled={off}
              title={off && allowed ? `${m.name} isn't offered for this piece` : `${m.name} · ${m.filament}`}
              className="ak-chip"
              onClick={() => onChange(m.id)}
              onKeyDown={(e) => {
                if (e.key === "ArrowRight" || e.key === "ArrowDown") {
                  e.preventDefault();
                  move(i, 1);
                } else if (e.key === "ArrowLeft" || e.key === "ArrowUp") {
                  e.preventDefault();
                  move(i, -1);
                } else if (e.key === " " || e.key === "Enter") {
                  e.preventDefault();
                  onChange(m.id);
                }
              }}
            >
              <span
                aria-hidden="true"
                className="h-3.5 w-3.5 flex-none rounded-full border border-black/10"
                style={{
                  background:
                    m.finish_class === "silk"
                      ? `linear-gradient(135deg, ${m.pbr.sheen_color ?? "#fff"} 0%, ${m.pbr.color} 45%, ${m.pbr.color} 100%)`
                      : m.pbr.color,
                  boxShadow: m.finish_class === "silk" ? "inset 0 0 0 1px rgba(255,255,255,.35)" : undefined,
                }}
              />
              {m.name}
            </button>
          );
        })}
      </div>
    </div>
  );
}
