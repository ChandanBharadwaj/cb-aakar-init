import Link from "next/link";
import { BloomMark } from "@/components/brand/BloomMark";
import { CartLink, NavIdentity } from "./NavIdentity";

export interface StageNavProps {
  section: string;
  /** Where the back arrow goes. */
  backHref?: string;
  backLabel?: string;
  children?: React.ReactNode;
}

/** Compact header for stage (indigo) pages: back · Bloom + Aakar · section · optional right slot · Cart · account. */
export function StageNav({ section, backHref = "/shop", backLabel = "Back", children }: StageNavProps) {
  return (
    <header className="relative z-10 flex items-center justify-between gap-4 px-4 py-3 sm:px-6">
      <div className="flex items-center gap-3">
        <Link
          href={backHref}
          className="grid h-10 w-10 place-items-center rounded-full border border-surface-border bg-surface-card text-base transition-colors duration-base ease-ak hover:border-surface-accent"
          aria-label={backLabel}
        >
          <span aria-hidden="true">←</span>
        </Link>
        <Link href="/" className="flex items-center gap-2" aria-label="Aakar home">
          <BloomMark size={22} />
          <span className="font-display text-xl font-bold leading-none">Aakar</span>
        </Link>
        <span className="ak-eyebrow">{section}</span>
      </div>
      <div className="flex items-center gap-3">
        {children}
        <CartLink className="hidden sm:inline" />
        <NavIdentity compact />
      </div>
    </header>
  );
}
