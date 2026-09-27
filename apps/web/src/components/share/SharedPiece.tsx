"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { api, toProblem } from "@/lib/api/client";
import type { Problem, SharedPiece as SharedPieceDto } from "@/lib/api/types";
import { useCartStore } from "@/store/cart";
import { toast } from "@/store/toast";
import { BloomLoader } from "@/components/brand/BloomLoader";
import { BloomMark } from "@/components/brand/BloomMark";
import { FinishTile } from "@/components/cart/FinishTile";
import { ProblemCard } from "@/components/ui/ProblemCard";

function formatPrinted(date: string): string {
  const d = new Date(date + "T00:00:00");
  return Number.isNaN(d.getTime()) ? date : d.toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" });
}

/** The unboxing card's page: "Designed by You. Crafted by Aakar." with Reprint and Remix. */
export function SharedPiece({ code }: { code: string }) {
  const router = useRouter();
  const add = useCartStore((s) => s.add);
  const [piece, setPiece] = useState<SharedPieceDto>();
  const [problem, setProblem] = useState<Problem>();
  const [adding, setAdding] = useState(false);

  useEffect(() => {
    let cancelled = false;
    api.share
      .get(code)
      .then((p) => !cancelled && setPiece(p))
      .catch((err) => !cancelled && setProblem(toProblem(err)));
    return () => {
      cancelled = true;
    };
  }, [code]);

  async function reprint() {
    if (!piece) return;
    setAdding(true);
    try {
      await add(piece.version_id, piece.material_id, 1);
      toast({ message: `${piece.title} in ${piece.material_name} is in your cart.`, action: { href: "/cart", label: "View cart" }, tone: "success" });
      router.push("/cart");
    } catch (err) {
      const p = toProblem(err);
      toast({ message: p.detail ?? p.title ?? "Couldn't add it to the cart.", tone: "danger" });
    } finally {
      setAdding(false);
    }
  }

  if (problem) {
    return (
      <ProblemCard
        problem={problem}
        title={problem.code === "not_found" ? "We couldn't find that card" : undefined}
        action={{ href: "/shop", label: "Browse the Shop" }}
      >
        <p className="text-sm text-surface-muted">
          Check the code printed under the QR on your card: eight letters and numbers, like <span className="font-mono">K7MB2QZA</span>.
        </p>
      </ProblemCard>
    );
  }
  if (!piece) return <BloomLoader size={112} label="Finding your piece" className="py-16" />;

  return (
    <div className="grid gap-6">
      <div className="ak-card relative overflow-hidden p-7 sm:p-9">
        <BloomMark size={260} className="pointer-events-none absolute -right-16 -top-14 opacity-[.06]" aria-hidden="true" />
        <div className="flex items-center gap-3">
          <BloomMark size={34} />
          <p className="text-[12px] font-semibold uppercase tracking-[.16em] text-terracotta">From your box</p>
        </div>
        <h1 className="mt-4 font-display text-[40px] font-semibold leading-[1.05] sm:text-[52px]">
          Designed by you. <span className="italic text-terracotta">Crafted by Aakar.</span>
        </h1>
        <p className="mt-3 max-w-xl text-surface-muted">
          This card came with a piece that started as an idea. Print it again exactly as it is, or take it back to the bench and make it yours all over again.
        </p>

        <div className="mt-8 flex flex-wrap items-start gap-5">
          <FinishTile materialId={piece.material_id} thumbnailUrl={piece.thumbnail_url ?? undefined} title={piece.title} size={112} />
          <div className="grid gap-1">
            <h2 className="font-display text-3xl font-semibold leading-tight">{piece.title}</h2>
            {piece.specs_line && <p className="text-sm text-surface-muted">{piece.specs_line}</p>}
            <p className="text-sm">
              <span className="font-semibold">{piece.material_name}</span>
              <span className="text-surface-muted"> · Printed in {piece.studio} · {formatPrinted(piece.printed_at)}</span>
            </p>
            <p className="font-mono text-[11px] text-surface-muted">card {piece.code}</p>
          </div>
        </div>

        <div className="mt-8 flex flex-wrap gap-3">
          <button
            type="button"
            onClick={reprint}
            disabled={adding}
            className="rounded-pill bg-indigo px-6 py-3 text-sm font-semibold text-cream shadow-card transition hover:bg-indigo-deep disabled:opacity-60"
          >
            {adding ? "Adding…" : "Reprint this piece"}
          </button>
          <Link
            href={piece.remix_path}
            className="rounded-pill border border-surface-border bg-surface-card px-6 py-3 text-sm font-semibold transition hover:border-terracotta"
          >
            Remix it
          </Link>
        </div>
      </div>
      <p className="text-center text-xs text-surface-muted">
        Reprints use the same design version and finish. Remixing opens the design in the studio, where every slider and finish is yours to change.
      </p>
    </div>
  );
}
