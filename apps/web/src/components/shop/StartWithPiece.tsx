"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { api, toProblem } from "@/lib/api/client";
import type { Problem } from "@/lib/api/types";

export interface StartWithPieceProps {
  slug: string;
  disabled?: boolean;
}

/** "Start with this piece" → POST /api/designs {source: "shop", catalog_item_slug} → /design/{id}?job={job}. */
export function StartWithPiece({ slug, disabled }: StartWithPieceProps) {
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<Problem>();

  async function start() {
    setBusy(true);
    setProblem(undefined);
    try {
      const accepted = await api.designs.create({ source: "shop", catalog_item_slug: slug });
      router.push(`/design/${accepted.design_id}?job=${encodeURIComponent(accepted.job_id)}`);
    } catch (err) {
      setProblem(toProblem(err));
      setBusy(false);
    }
  }

  return (
    <div className="grid gap-2">
      <button type="button" className="ak-btn ak-btn-primary ak-btn-pill px-6" onClick={start} disabled={disabled || busy} aria-busy={busy}>
        {busy ? "Preparing your piece…" : "Start with this piece"}
      </button>
      {problem && (
        <p role="alert" className="text-xs text-danger">
          {problem.title}
          {problem.detail ? ` — ${problem.detail}` : ""}
        </p>
      )}
    </div>
  );
}
