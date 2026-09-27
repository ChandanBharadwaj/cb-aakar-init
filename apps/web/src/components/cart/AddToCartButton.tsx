"use client";

import { useEffect, useRef, useState } from "react";
import { api, toProblem } from "@/lib/api/client";
import { isAvailable, type CatalogItem } from "@/lib/api/types";
import { useJobStream } from "@/components/design/useJobStream";
import { useCartStore } from "@/store/cart";
import { toast } from "@/store/toast";

type Phase = "idle" | "creating" | "sculpting" | "adding" | "added" | "error";

export interface AddToCartButtonProps {
  item: CatalogItem;
  className?: string;
}

/**
 * "Add to Cart" on Shop cards and the item page. Creates the design from the item
 * (`POST /api/designs`), follows its job, then adds the ready version in the item's default
 * finish. The label tells the story: Preparing… → Sculpting… → Adding… → Added ✓.
 */
export function AddToCartButton({ item, className }: AddToCartButtonProps) {
  const available = isAvailable(item);
  const [phase, setPhase] = useState<Phase>("idle");
  const [jobId, setJobId] = useState<string>();
  const [designId, setDesignId] = useState<string>();
  const [error, setError] = useState<string>();
  const resetTimer = useRef<ReturnType<typeof setTimeout>>(undefined);

  useEffect(() => () => clearTimeout(resetTimer.current), []);

  useJobStream(jobId, {
    onEvent: () => undefined,
    onDone: async (ev) => {
      setJobId(undefined);
      if (ev.stage !== "ready") {
        setPhase("error");
        setError(ev.message || "The studio couldn't sculpt this piece just now.");
        return;
      }
      setPhase("adding");
      try {
        let versionId = ev.version_id ?? undefined;
        if (!versionId && designId) versionId = (await api.designs.get(designId)).latest_version?.id;
        if (!versionId) throw new Error("The studio didn't return a version to add.");
        await useCartStore.getState().add(versionId, item.default_material, 1);
        setPhase("added");
        toast({ message: `${item.name} is in your cart.`, action: { href: "/cart", label: "View cart" }, tone: "success" });
        resetTimer.current = setTimeout(() => setPhase("idle"), 2500);
      } catch (err) {
        const p = toProblem(err);
        setPhase("error");
        setError(p.code === "not_printable" ? "This piece needs a small change before it can be printed. Open it with “Modify with AI”." : (p.detail ?? p.title ?? "Couldn't add this piece."));
      }
    },
  });

  async function start() {
    setPhase("creating");
    setError(undefined);
    try {
      const accepted = await api.designs.create({ source: "shop", catalog_item_slug: item.slug });
      setDesignId(accepted.design_id);
      setJobId(accepted.job_id);
      setPhase("sculpting");
    } catch (err) {
      const p = toProblem(err);
      setPhase("error");
      setError(p.detail ?? p.title ?? "Couldn't start this piece.");
    }
  }

  const busy = phase === "creating" || phase === "sculpting" || phase === "adding";
  const label =
    phase === "creating" ? "Preparing…" : phase === "sculpting" ? "Sculpting…" : phase === "adding" ? "Adding…" : phase === "added" ? "Added ✓" : "Add to Cart";

  return (
    <div className={["grid gap-1.5", className].filter(Boolean).join(" ")}>
      <button
        type="button"
        className={["ak-btn ak-btn-primary min-h-10 text-xs", phase === "added" ? "!bg-sage !text-cream" : ""].join(" ")}
        onClick={start}
        disabled={!available || busy || phase === "added"}
        aria-busy={busy}
        title={!available ? "This piece is still being finished" : undefined}
      >
        {label}
      </button>
      {phase === "error" && error && (
        <p role="alert" className="text-[11px] leading-snug text-danger">
          {error}
        </p>
      )}
    </div>
  );
}
