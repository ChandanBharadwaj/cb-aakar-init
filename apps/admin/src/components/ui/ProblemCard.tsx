import Link from "next/link";
import type { Problem } from "@/lib/api/types";

export interface ProblemCardProps {
  problem: Problem;
  /** Overrides the problem title, e.g. "Couldn't reach the management API". */
  title?: string;
  action?: { href?: string; label: string; onClick?: () => void };
  children?: React.ReactNode;
  className?: string;
  /** Inline variant: no card chrome, smaller type (for form errors and 409s). */
  compact?: boolean;
}

/** RFC 9457 Problem Details rendered in a paper card, with the stable `code` in small type. */
export function ProblemCard({ problem, title, action, children, className, compact }: ProblemCardProps) {
  const heading = title ?? problem.title ?? "Something went wrong";
  if (compact) {
    return (
      <div role="alert" className={["ak-well grid gap-1 border border-danger/30 bg-danger/5 p-3 text-sm", className].filter(Boolean).join(" ")}>
        <div className="flex items-start gap-2">
          <span className="mt-0.5 grid h-5 w-5 flex-none place-items-center rounded-full bg-danger/15 text-xs font-bold text-danger" aria-hidden="true">
            !
          </span>
          <div className="grid gap-0.5">
            <strong className="font-semibold">{heading}</strong>
            {problem.detail && <span className="text-surface-muted">{problem.detail}</span>}
            <span className="font-mono text-[11px] text-surface-muted">
              {problem.code ?? "error"}
              {problem.status ? ` · ${problem.status}` : ""}
            </span>
          </div>
        </div>
        {children}
      </div>
    );
  }
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
        {action &&
          (action.href ? (
            <Link href={action.href} className="ak-btn ak-btn-secondary ak-btn-pill ak-btn-sm">
              {action.label}
            </Link>
          ) : (
            <button type="button" onClick={action.onClick} className="ak-btn ak-btn-secondary ak-btn-pill ak-btn-sm">
              {action.label}
            </button>
          ))}
      </div>
    </div>
  );
}
