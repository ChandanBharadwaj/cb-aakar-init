/** "In season · Diwali": shown on an experience while today is inside one of its season windows. */
export function SeasonBadge({ label, className }: { label: string; className?: string }) {
  return (
    <span
      className={[
        "inline-flex items-center gap-1.5 rounded-pill bg-marigold px-2.5 py-1 text-[10.5px] font-bold uppercase tracking-[0.08em] text-ink shadow-card",
        className,
      ]
        .filter(Boolean)
        .join(" ")}
    >
      <span aria-hidden="true">✦</span>
      <span>
        <span className="sr-only">In season: </span>
        {label}
      </span>
    </span>
  );
}
