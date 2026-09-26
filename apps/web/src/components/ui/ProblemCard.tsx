import Link from "next/link";
import type { Problem } from "@/lib/api/types";

export interface ProblemCardProps {
  problem: Problem;
  /** Overrides the problem title, e.g. "Couldn't reach the studio". */
  title?: string;
  action?: { href: string; label: string };
  children?: React.ReactNode;
  className?: string;
}

/** RFC 9457 Problem Details rendered in a paper card, with the stable `code` in small type. */
export function ProblemCard({ problem, title, action, children, className }: ProblemCardProps) {
  const heading = title ?? problem.title ?? "Something went wrong";
  return (
    <div role="alert" className={["ak-card grid gap-3 p-6", className].filter(Boolean).join(" ")}>
      <div className="flex items-start gap-3">
        <span className="mt-0.5 grid h-8 w-8 flex-none place-items-center rounded-full bg-terracotta/15 font-display text-lg font-bold text-terracotta" aria-hidden="true">
          !
        </span>
        <div className="grid gap-1">
          <h2 className="font-display text-2xl font-semibold leading-tight">{heading}</h2>
          {problem.detail && <p className="text-sm text-surface-muted">{problem.detail}</p>}
        </div>
      </div>
      {children}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <span className="font-mono text-[11px] text-surface-muted">
          {problem.code ?? "error"}
          {problem.status ? ` · ${problem.status}` : ""}
        </span>
        {action && (
          <Link href={action.href} className="ak-btn ak-btn-secondary ak-btn-pill min-h-9 px-4 py-1.5 text-xs">
            {action.label}
          </Link>
        )}
      </div>
    </div>
  );
}
