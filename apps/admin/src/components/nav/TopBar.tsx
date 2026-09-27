"use client";

import { useRouter } from "next/navigation";
import { useSession } from "@/store/session";
import { Pill } from "@/components/ui/StatusPill";

/** Staff name and role on the right, sign-out next to it. */
export function TopBar() {
  const router = useRouter();
  const staff = useSession((s) => s.staff);
  const signOut = useSession((s) => s.signOut);
  const apiUrl = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";
  return (
    <header className="flex items-center justify-between gap-4 border-b border-surface-border bg-surface-card/60 px-4 py-2.5 text-sm backdrop-blur sm:px-8">
      <span className="truncate text-xs text-surface-muted" title="Management API base URL">
        API · {apiUrl.replace(/^https?:\/\//, "")}
      </span>
      <div className="flex items-center gap-3">
        {staff && (
          <span className="flex items-center gap-2">
            <span className="grid h-7 w-7 place-items-center rounded-full bg-indigo text-[11px] font-bold text-cream" aria-hidden="true">
              {staff.name
                .split(/\s+/)
                .map((p) => p.charAt(0))
                .join("")
                .slice(0, 2)
                .toUpperCase()}
            </span>
            <span className="hidden font-medium sm:inline">{staff.name}</span>
            <Pill tone={staff.role === "owner" ? "accent" : "info"} title={staff.email}>
              {staff.role}
            </Pill>
          </span>
        )}
        <button
          type="button"
          className="ak-btn ak-btn-secondary ak-btn-sm ak-btn-pill"
          onClick={() => {
            signOut();
            router.replace("/signin");
          }}
        >
          Sign out
        </button>
      </div>
    </header>
  );
}
