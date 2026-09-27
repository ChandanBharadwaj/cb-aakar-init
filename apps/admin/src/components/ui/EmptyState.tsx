import { BloomMark } from "@/components/brand/BloomMark";

export function EmptyState({ title, children, className }: { title: string; children?: React.ReactNode; className?: string }) {
  return (
    <div className={["ak-card grid justify-items-center gap-2 p-10 text-center", className].filter(Boolean).join(" ")}>
      <BloomMark size={36} className="text-surface-muted opacity-60" />
      <p className="font-display text-xl font-semibold">{title}</p>
      {children && <p className="max-w-prose text-sm text-surface-muted">{children}</p>}
    </div>
  );
}
