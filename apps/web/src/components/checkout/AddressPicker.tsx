"use client";

import type { Address, Serviceability } from "@/lib/api/types";
import { formatPhone } from "@/lib/identity";
import { serviceabilityLabel } from "./serviceability";

export interface AddressPickerProps {
  addresses: Address[];
  selectedId?: string;
  onSelect(id: string): void;
  onAdd(): void;
  onEdit(address: Address): void;
  /** Serviceability by pincode, when known. */
  serviceability: Record<string, Serviceability | undefined>;
  disabled?: boolean;
}

export function addressLines(a: Address): string {
  return [a.line1, a.line2, `${a.city} ${a.pincode}`].filter(Boolean).join(", ");
}

/** "Deliver to" radio cards from the Checkout board, with Edit and Add. */
export function AddressPicker({ addresses, selectedId, onSelect, onAdd, onEdit, serviceability, disabled }: AddressPickerProps) {
  return (
    <div className="grid gap-2" role="radiogroup" aria-label="Deliver to">
      {addresses.length === 0 && <p className="text-sm text-surface-muted">No addresses yet. Add one to see if we deliver there and how fast.</p>}
      {addresses.map((a) => {
        const checked = a.id === selectedId;
        const svc = serviceability[a.pincode];
        const label = svc ? serviceabilityLabel(svc) : undefined;
        return (
          <div key={a.id} className={`flex items-start gap-3 rounded-control border p-3.5 ${checked ? "border-surface-accent bg-surface-card ak-ring-accent" : "border-surface-border bg-surface-card/60"}`}>
            <button
              type="button"
              role="radio"
              aria-checked={checked}
              disabled={disabled}
              onClick={() => onSelect(a.id)}
              className="mt-0.5 grid h-5 w-5 flex-none place-items-center rounded-full border border-surface-border bg-surface-card"
              aria-label={`Deliver to ${a.name}, ${addressLines(a)}`}
            >
              {checked && <span className="h-2.5 w-2.5 rounded-full bg-surface-accent" aria-hidden="true" />}
            </button>
            <button type="button" className="grid min-w-0 flex-1 gap-0.5 text-left" onClick={() => onSelect(a.id)} disabled={disabled} tabIndex={-1}>
              <span className="flex flex-wrap items-baseline gap-2">
                <span className="font-semibold">{a.name}</span>
                {a.label && <span className="ak-label">{a.label}</span>}
                {a.is_default && <span className="text-[10px] uppercase tracking-wider text-surface-muted">default</span>}
              </span>
              <span className="text-[13px] text-surface-muted">{addressLines(a)}</span>
              <span className="text-[11px] text-surface-muted">
                {a.state} · {formatPhone(a.phone)}
              </span>
              {label && (
                <span className={`text-[11px] font-semibold ${label.ok ? "text-success" : "text-danger"}`}>
                  {label.text}
                  {label.mock && <span className="ml-1 font-normal text-surface-muted">(mock)</span>}
                </span>
              )}
            </button>
            <button type="button" className="text-xs text-surface-muted underline-offset-2 hover:text-surface-text hover:underline" onClick={() => onEdit(a)} disabled={disabled}>
              Edit
            </button>
          </div>
        );
      })}
      <button type="button" className="ak-btn ak-btn-secondary min-h-10 justify-self-start text-xs" onClick={onAdd} disabled={disabled}>
        + Add {addresses.length > 0 ? "another" : "an"} address
      </button>
    </div>
  );
}
