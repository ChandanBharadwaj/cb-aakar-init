import type { PrintabilityCheck, PrintabilityCheckId, PrintabilityReport } from "@/lib/api/types";

const LABELS: Record<PrintabilityCheckId, string> = {
  centre_of_gravity: "Centre of gravity",
  thinnest_wall: "Thinnest wall",
  tipping_margin: "Tipping margin",
  manifold: "Watertight",
  fits_bed: "Fits the printer",
  overhangs: "Overhangs",
  load_capacity: "Load",
  connector_fit: "Fits its base",
};

/** Order from the Checkout board: balance first, then walls, then the rest; a Jod's Kadi fit (hybrid pieces only) sits with the balance checks. */
const ORDER: PrintabilityCheckId[] = ["centre_of_gravity", "thinnest_wall", "tipping_margin", "connector_fit", "manifold", "fits_bed", "overhangs", "load_capacity"];

const STATUS_STYLE: Record<PrintabilityCheck["status"], { dot: string; text: string; label: string }> = {
  pass: { dot: "bg-success", text: "", label: "Pass" },
  warn: { dot: "bg-warning", text: "text-warning", label: "Check" },
  fail: { dot: "bg-danger", text: "text-danger", label: "Fail" },
  skipped: { dot: "bg-surface-border", text: "text-surface-muted", label: "Later" },
};

export interface StabilityCardProps {
  report?: PrintabilityReport;
  /** True while a new version is being checked. */
  pending?: boolean;
  className?: string;
}

export function StabilityCard({ report, pending, className }: StabilityCardProps) {
  const checks = report ? (Object.entries(report.checks) as [PrintabilityCheckId, PrintabilityCheck][]) : [];
  const ordered = ORDER.map((id) => checks.find(([k]) => k === id)).filter((x): x is [PrintabilityCheckId, PrintabilityCheck] => Boolean(x));
  const worst = ordered.reduce<PrintabilityCheck["status"]>((acc, [, c]) => {
    if (c.status === "fail" || acc === "fail") return "fail";
    if (c.status === "warn" || acc === "warn") return "warn";
    return acc;
  }, "pass");

  const headline = pending
    ? "Checking physics"
    : !report
      ? "Stability check pending"
      : report.passed && worst === "pass"
        ? "Stability check passed"
        : report.passed
          ? "Stable, with notes"
          : "Needs a small change";

  const badge = pending || !report ? "bg-surface-border text-surface-muted" : report.passed ? "bg-sage text-cream" : "bg-danger text-cream";

  return (
    <section className={["ak-card grid gap-3.5 p-4", className].filter(Boolean).join(" ")} aria-labelledby="stability-heading">
      <div className="flex items-center gap-3">
        <span className={`grid h-8 w-8 flex-none place-items-center rounded-full text-sm font-bold ${badge} ${pending ? "animate-pulse-soft" : ""}`} aria-hidden="true">
          {pending || !report ? "·" : report.passed ? "✓" : "!"}
        </span>
        <div className="grid gap-0.5">
          <h3 id="stability-heading" className="font-display text-lg font-semibold leading-tight">
            {headline}
          </h3>
          {report && !pending && <div className="text-[11px] text-surface-muted">Checked in {(report.check_duration_ms / 1000).toFixed(1)} s</div>}
        </div>
      </div>
      {ordered.length > 0 ? (
        <dl className="grid grid-cols-2 gap-2 text-xs">
          {ordered.map(([id, check]) => {
            const style = STATUS_STYLE[check.status];
            return (
              <div key={id} className="ak-well grid gap-1 p-2.5">
                <dt className="flex items-center gap-1.5 text-surface-muted">
                  <span className={`h-1.5 w-1.5 rounded-full ${style.dot}`} aria-hidden="true" />
                  {LABELS[id]}
                  <span className="sr-only">: {style.label}</span>
                </dt>
                <dd className={`font-semibold ${style.text}`}>{check.summary}</dd>
              </div>
            );
          })}
        </dl>
      ) : (
        <p className="text-xs text-surface-muted">Balance, wall thickness and fit are checked before anything can be priced.</p>
      )}
    </section>
  );
}
