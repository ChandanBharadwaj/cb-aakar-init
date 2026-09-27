"use client";

import { usePathname, useRouter } from "next/navigation";
import { useEffect } from "react";
import { BloomLoader } from "@/components/brand/BloomLoader";
import { useSession } from "@/store/session";

/** Renders children for a signed-in user; sends guests to /signin?next=<here>. */
export function RequireSignIn({ children, next }: { children: React.ReactNode; next?: string }) {
  const { status } = useSession();
  const router = useRouter();
  const pathname = usePathname();
  const target = next ?? pathname ?? "/";

  useEffect(() => {
    if (status === "guest") router.replace(`/signin?next=${encodeURIComponent(target)}`);
  }, [status, router, target]);

  if (status !== "user") {
    return (
      <div className="grid flex-1 place-items-center px-4 py-24">
        <BloomLoader size={112} label={status === "guest" ? "Taking you to sign in" : "One moment"} />
      </div>
    );
  }
  return <>{children}</>;
}
