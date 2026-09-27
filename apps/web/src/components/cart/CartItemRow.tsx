"use client";

import Link from "next/link";
import { useState } from "react";
import { formatPaise, materialById } from "@aakar/design-tokens";
import { toProblem } from "@/lib/api/client";
import type { CartItem } from "@/lib/api/types";
import { useCartStore } from "@/store/cart";
import { FinishTile } from "./FinishTile";
import { QtyStepper } from "./QtyStepper";

export interface CartItemRowProps {
  item: CartItem;
  /** Show the qty stepper and Remove (cart, checkout review). */
  editable?: boolean;
}

export function finishName(item: { material_id: string; material_name?: string }): string {
  return item.material_name ?? materialById(item.material_id)?.name ?? item.material_id.replace(/_/g, " ");
}

export function CartItemRow({ item, editable = true }: CartItemRowProps) {
  const pending = useCartStore((s) => Boolean(s.pending[item.id]));
  const [error, setError] = useState<string>();

  async function run(fn: () => Promise<unknown>) {
    setError(undefined);
    try {
      await fn();
    } catch (err) {
      setError(toProblem(err).detail ?? "Couldn't update your cart.");
    }
  }

  return (
    <li className={["ak-card grid gap-3 p-4", item.purchasable ? "" : "border-danger/40"].join(" ")}>
      <div className="flex gap-4">
        <FinishTile materialId={item.material_id} thumbnailUrl={item.thumbnail_url} title={item.title} />
        <div className="grid min-w-0 flex-1 content-start gap-1">
          <div className="flex items-start justify-between gap-3">
            <h3 className="font-display text-xl font-semibold leading-tight">
              <Link href={`/design/${item.design_id}`} className="hover:text-surface-accent">
                {item.title}
              </Link>
            </h3>
            <span className="font-display text-xl font-bold">{formatPaise(item.line_total_paise)}</span>
          </div>
          {item.specs_line && <p className="text-xs text-surface-muted">{item.specs_line}</p>}
          <p className="text-xs text-surface-muted">
            <span className="font-semibold text-surface-text">{finishName(item)}</span>
            {item.version_no ? ` · version ${item.version_no}` : ""}
            {item.qty > 1 ? ` · ${formatPaise(item.unit_price.subtotal_paise)} each` : ""}
          </p>
          {editable && (
            <div className="mt-1 flex flex-wrap items-center gap-3">
              <QtyStepper value={item.qty} disabled={pending} onChange={(qty) => void run(() => useCartStore.getState().update(item.id, { qty }))} label={`Quantity of ${item.title}`} />
              <button type="button" className="text-xs text-surface-muted underline-offset-2 hover:text-danger hover:underline disabled:opacity-50" disabled={pending} onClick={() => void run(() => useCartStore.getState().remove(item.id))}>
                Remove
              </button>
            </div>
          )}
        </div>
      </div>
      {item.repriced && (
        <p className="ak-well px-3 py-2 text-xs text-surface-muted">
          <span className="font-semibold text-surface-text">Price refreshed.</span> Our rates changed since you added this piece; this is today&apos;s price.
        </p>
      )}
      {!item.purchasable && (
        <p role="alert" className="rounded-control bg-danger/10 px-3 py-2 text-xs text-danger">
          This piece can&apos;t be printed as it is.{" "}
          <Link href={`/design/${item.design_id}`} className="font-semibold underline">
            Open it in the studio
          </Link>{" "}
          to adjust it, or remove it from the cart.
        </p>
      )}
      {error && (
        <p role="alert" className="text-xs text-danger">
          {error}
        </p>
      )}
    </li>
  );
}
