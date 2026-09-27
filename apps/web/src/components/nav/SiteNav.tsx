import Link from "next/link";
import { BloomMark } from "@/components/brand/BloomMark";
import { CartLink, NavIdentity } from "./NavIdentity";

const LINKS = [
  { href: "/shop", label: "Shop" },
  { href: "/create", label: "Create" },
  { href: "/remix", label: "Remix" },
  { href: "/orders", label: "Orders" },
] as const;

export interface SiteNavProps {
  /** Eyebrow shown after the wordmark, e.g. "Create" on stage pages. */
  section?: string;
  className?: string;
}

/** Paper-surface header: Bloom + "Aakar" · Shop · Create · Remix · Orders · Cart · n · Sign in / account. */
export function SiteNav({ section, className }: SiteNavProps) {
  return (
    <header className={["relative z-10 flex items-center justify-between gap-6 px-4 py-4 sm:px-8 lg:px-11", className].filter(Boolean).join(" ")}>
      <div className="flex items-center gap-6 sm:gap-10">
        <Link href="/" className="flex items-center gap-2.5 rounded-control" aria-label="Aakar home">
          <BloomMark size={26} />
          <span className="font-display text-[22px] font-bold leading-none">Aakar</span>
          {section && <span className="ak-eyebrow ml-1 hidden sm:inline">{section}</span>}
        </Link>
        <nav aria-label="Primary" className="hidden gap-7 text-[13px] font-medium text-surface-muted md:flex">
          {LINKS.map((l) => (
            <Link key={l.href} href={l.href} className="transition-colors duration-base ease-ak hover:text-surface-text">
              {l.label}
            </Link>
          ))}
        </nav>
      </div>
      <div className="flex items-center gap-3.5">
        <CartLink />
        <NavIdentity />
        <MobileMenu />
      </div>
    </header>
  );
}

function MobileMenu() {
  return (
    <details className="relative md:hidden">
      <summary className="ak-btn ak-btn-secondary min-h-9 cursor-pointer list-none px-3 py-1.5 text-xs" aria-label="Menu">
        Menu
      </summary>
      <nav aria-label="Primary" className="ak-card absolute right-0 top-11 z-20 grid min-w-40 gap-1 p-2 text-sm">
        {LINKS.map((l) => (
          <Link key={l.href} href={l.href} className="rounded-control px-3 py-2 hover:bg-surface-bg">
            {l.label}
          </Link>
        ))}
        <Link href="/cart" className="rounded-control px-3 py-2 hover:bg-surface-bg">
          Cart
        </Link>
      </nav>
    </details>
  );
}
