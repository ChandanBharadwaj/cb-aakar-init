import Link from "next/link";
import { BloomMark } from "@/components/brand/BloomMark";

export interface PhaseCardProps {
  eyebrow: string;
  title: string;
  phase: 1 | 2 | 3;
  children?: React.ReactNode;
  links?: { href: string; label: string; primary?: boolean }[];
}

/** "Arrives in Phase N" placeholder so nav links never 404 (PLAN §4). */
export function PhaseCard({ eyebrow, title, phase, children, links }: PhaseCardProps) {
  return (
    <div className="ak-card relative mx-auto grid max-w-2xl gap-5 overflow-hidden p-8">
      <BloomMark size={220} className="pointer-events-none absolute -right-14 -top-10 opacity-[.06]" aria-hidden="true" />
      <div className="ak-eyebrow">{eyebrow}</div>
      <h1 className="font-display text-4xl font-semibold leading-tight">{title}</h1>
      <p className="max-w-prose text-surface-muted">
        This part of the studio arrives in <strong className="text-surface-text">Phase {phase}</strong>. {children}
      </p>
      {links && (
        <div className="flex flex-wrap gap-3">
          {links.map((l) => (
            <Link key={l.href} href={l.href} className={`ak-btn ak-btn-pill ${l.primary ? "ak-btn-primary" : "ak-btn-secondary"}`}>
              {l.label}
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
