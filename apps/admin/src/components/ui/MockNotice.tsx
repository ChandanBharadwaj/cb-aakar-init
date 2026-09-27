export interface MockNoticeProps {
  children: React.ReactNode;
  className?: string;
}

/** Every mock-only surface is labelled as such (ADR-0013). */
export function MockNotice({ children, className }: MockNoticeProps) {
  return (
    <div className={["ak-mock flex items-start gap-2.5 px-3.5 py-2.5 text-[12.5px] text-surface-text", className].filter(Boolean).join(" ")} role="note">
      <span className="mt-px inline-flex flex-none rounded-pill bg-marigold px-1.5 py-px text-[10px] font-bold uppercase tracking-wider text-ink">Mock</span>
      <span>{children}</span>
    </div>
  );
}
