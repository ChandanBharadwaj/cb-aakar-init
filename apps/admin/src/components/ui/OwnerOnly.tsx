"use client";

import { useSession } from "@/store/session";

/** Small hint shown to the studio role wherever a write is disabled. */
export function OwnerOnlyHint({ what = "Changes here", className }: { what?: string; className?: string }) {
  const role = useSession((s) => s.staff?.role);
  if (role !== "studio") return null;
  return (
    <p className={["text-xs text-surface-muted", className].filter(Boolean).join(" ")} role="note">
      <span className="ak-pill mr-1.5" data-tone="warning">
        Owner only
      </span>
      {what} need the owner account. Your studio role can view but not change this.
    </p>
  );
}
