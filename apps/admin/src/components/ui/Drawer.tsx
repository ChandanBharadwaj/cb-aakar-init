"use client";

import { useEffect } from "react";

export interface DrawerProps {
  open: boolean;
  title: string;
  eyebrow?: string;
  onClose(): void;
  children: React.ReactNode;
  /** Sticky footer, usually the Save / Cancel row. */
  footer?: React.ReactNode;
}

/** Right-hand edit drawer over a dimmed page. Escape closes; the backdrop click closes. */
export function Drawer({ open, title, eyebrow, onClose, children, footer }: DrawerProps) {
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    document.addEventListener("keydown", onKey);
    const prev = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.removeEventListener("keydown", onKey);
      document.body.style.overflow = prev;
    };
  }, [open, onClose]);

  if (!open) return null;
  return (
    <div className="fixed inset-0 z-40 flex justify-end">
      <button type="button" className="absolute inset-0 bg-ink/35 backdrop-blur-[1px]" aria-label="Close" onClick={onClose} />
      <aside role="dialog" aria-modal="true" aria-labelledby="drawer-title" className="relative flex h-full w-full max-w-[520px] flex-col bg-surface-card shadow-float animate-fade-in">
        <header className="flex items-start justify-between gap-4 border-b border-surface-border px-6 py-5">
          <div className="grid gap-1">
            {eyebrow && <span className="ak-eyebrow">{eyebrow}</span>}
            <h2 id="drawer-title" className="font-display text-2xl font-semibold leading-tight">
              {title}
            </h2>
          </div>
          <button type="button" className="ak-btn ak-btn-secondary ak-btn-sm ak-btn-pill" onClick={onClose}>
            Close
          </button>
        </header>
        <div className="flex-1 overflow-y-auto px-6 py-5">{children}</div>
        {footer && <footer className="border-t border-surface-border px-6 py-4">{footer}</footer>}
      </aside>
    </div>
  );
}
