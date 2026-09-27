"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useRef } from "react";
import { formatPhone } from "@/lib/identity";
import { useCartStore } from "@/store/cart";
import { displayName, useSession } from "@/store/session";

/** "Cart · n", live from the cart store. */
export function CartLink({ className }: { className?: string }) {
  const count = useCartStore((s) => s.count);
  return (
    <Link href="/cart" className={["text-xs font-medium text-surface-muted transition-colors duration-base ease-ak hover:text-surface-text", className].filter(Boolean).join(" ")} aria-label={`Cart, ${count} ${count === 1 ? "item" : "items"}`}>
      Cart · {count}
    </Link>
  );
}

/** "Sign in", or the signed-in name with a small menu (Orders · Sign out). */
export function NavIdentity({ compact }: { compact?: boolean }) {
  const { status, user, signOut } = useSession();
  const pathname = usePathname();
  const ref = useRef<HTMLDetailsElement>(null);

  // Close the menu on outside click or Escape; the header persists across client navigations.
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const onDoc = (e: MouseEvent) => {
      if (el.open && !el.contains(e.target as Node)) el.open = false;
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") el.open = false;
    };
    document.addEventListener("click", onDoc);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("click", onDoc);
      document.removeEventListener("keydown", onKey);
    };
  }, [status]);

  if (status === "loading") {
    return <span className="block h-8 w-8 rounded-full bg-surface-border animate-pulse-soft" aria-hidden="true" />;
  }

  if (status === "guest" || !user) {
    const next = pathname && pathname !== "/signin" ? `?next=${encodeURIComponent(pathname)}` : "";
    return (
      <Link href={`/signin${next}`} className="ak-btn ak-btn-secondary ak-btn-pill min-h-8 px-3.5 py-1 text-xs">
        Sign in
      </Link>
    );
  }

  const name = displayName(user, formatPhone);
  const initial = (user.name?.trim().charAt(0) || user.phone.replace(/^\+91/, "").charAt(0) || "A").toUpperCase();
  const close = () => {
    if (ref.current) ref.current.open = false;
  };

  return (
    <details ref={ref} className="relative">
      <summary className="flex cursor-pointer list-none items-center gap-2 rounded-pill" aria-label={`Account: ${name}`}>
        <span className="grid h-8 w-8 place-items-center rounded-full bg-surface-accent text-xs font-bold text-surface-bg" aria-hidden="true">
          {initial}
        </span>
        {!compact && <span className="hidden max-w-[10rem] truncate text-xs font-medium sm:inline">{name}</span>}
      </summary>
      <div className="ak-card absolute right-0 top-11 z-30 grid min-w-48 gap-1 p-2 text-sm">
        <div className="grid gap-0.5 px-3 py-1.5">
          <span className="truncate text-[13px] font-semibold">{name}</span>
          {user.name && <span className="text-[11px] text-surface-muted">{formatPhone(user.phone)}</span>}
        </div>
        <Link href="/orders" onClick={close} className="rounded-control px-3 py-2 hover:bg-surface-bg">
          Orders
        </Link>
        <Link href="/cart" onClick={close} className="rounded-control px-3 py-2 hover:bg-surface-bg">
          Cart
        </Link>
        <button
          type="button"
          onClick={() => {
            close();
            void signOut();
          }}
          className="rounded-control px-3 py-2 text-left text-surface-muted hover:bg-surface-bg hover:text-surface-text"
        >
          Sign out
        </button>
      </div>
    </details>
  );
}
