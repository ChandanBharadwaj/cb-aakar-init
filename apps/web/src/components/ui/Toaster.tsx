"use client";

import Link from "next/link";
import { useToastStore } from "@/store/toast";

/** Bottom-centre toasts on a paper card, readable on both surfaces. */
export function Toaster() {
  const toasts = useToastStore((s) => s.toasts);
  const dismiss = useToastStore((s) => s.dismiss);
  if (toasts.length === 0) return null;
  return (
    <div className="pointer-events-none fixed inset-x-0 bottom-4 z-50 grid justify-items-center gap-2 px-4" aria-live="polite">
      {toasts.map((t) => (
        <div
          key={t.id}
          role="status"
          className="pointer-events-auto flex max-w-md items-center gap-3 rounded-pill border border-line bg-paper py-2 pl-4 pr-2 text-[13px] text-ink shadow-float animate-fade-in"
        >
          <span
            aria-hidden="true"
            className={`h-2 w-2 flex-none rounded-full ${t.tone === "danger" ? "bg-danger" : t.tone === "success" ? "bg-sage" : "bg-marigold"}`}
          />
          <span className="min-w-0 flex-1">{t.message}</span>
          {t.action && (
            <Link href={t.action.href} onClick={() => dismiss(t.id)} className="rounded-pill bg-indigo px-3 py-1 text-xs font-semibold text-cream hover:brightness-110">
              {t.action.label}
            </Link>
          )}
          <button type="button" onClick={() => dismiss(t.id)} className="grid h-7 w-7 place-items-center rounded-full text-ink-muted hover:bg-cream" aria-label="Dismiss">
            ×
          </button>
        </div>
      ))}
    </div>
  );
}
