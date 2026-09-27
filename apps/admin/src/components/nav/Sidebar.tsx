"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { BloomMark } from "@/components/brand/BloomMark";

export const NAV = [
  { href: "/", label: "Dashboard", glyph: "◫" },
  { href: "/orders", label: "Orders", glyph: "▤" },
  { href: "/pricing", label: "Pricing", glyph: "₹" },
  { href: "/materials", label: "Materials", glyph: "◍" },
  { href: "/catalog", label: "Catalog", glyph: "▦" },
  { href: "/avatars", label: "Avatars", glyph: "❖" },
  { href: "/experiences", label: "Duniya", glyph: "✦" },
  { href: "/environments", label: "Backgrounds", glyph: "◐" },
  { href: "/hardware", label: "Hardware", glyph: "⚙" },
  { href: "/templates", label: "Templates", glyph: "◈" },
  { href: "/reviews", label: "Reviews", glyph: "◎" },
  { href: "/messages", label: "Messages", glyph: "✉" },
  { href: "/audit", label: "Audit", glyph: "≡" },
] as const;

export function isActive(pathname: string, href: string): boolean {
  return href === "/" ? pathname === "/" : pathname === href || pathname.startsWith(`${href}/`);
}

/** Left navigation. On small screens it collapses to a top strip of links. */
export function Sidebar({ className }: { className?: string }) {
  const pathname = usePathname();
  return (
    <aside className={["flex flex-col gap-5 border-b border-surface-border bg-[var(--ak-sidebar)] px-4 py-4 lg:min-h-dvh lg:w-[232px] lg:flex-none lg:border-b-0 lg:border-r lg:px-4 lg:py-6", className].filter(Boolean).join(" ")}>
      <Link href="/" className="flex items-center gap-2.5 px-2" aria-label="Aakar Studio dashboard">
        <BloomMark size={26} />
        <span className="font-display text-[22px] font-bold leading-none">Aakar</span>
        <span className="ak-eyebrow ml-0.5">Studio</span>
      </Link>
      <nav aria-label="Portal" className="no-scrollbar -mx-4 flex gap-1 overflow-x-auto px-4 lg:mx-0 lg:grid lg:px-0">
        {NAV.map((n) => {
          const active = isActive(pathname, n.href);
          return (
            <Link key={n.href} href={n.href} className="ak-navlink flex-none" aria-current={active ? "page" : undefined}>
              <span className="grid w-5 place-items-center text-[15px] text-surface-muted" aria-hidden="true">
                {n.glyph}
              </span>
              {n.label}
            </Link>
          );
        })}
      </nav>
      <p className="mt-auto hidden px-2 text-[11px] leading-relaxed text-surface-muted lg:block">
        Staff-only. Every change is written to the audit log with who, when, before and after.
      </p>
    </aside>
  );
}
