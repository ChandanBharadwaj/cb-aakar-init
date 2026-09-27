"use client";

import { useEffect } from "react";
import { usePathname, useRouter } from "next/navigation";
import { useSession } from "@/store/session";
import { Sidebar } from "./Sidebar";
import { TopBar } from "./TopBar";
import { Loading } from "@/components/ui/Loading";

/**
 * Sidebar + top bar around every page except /signin, and the auth guard:
 * without a token (or with a rejected one) the browser goes to /signin?next=…
 */
export function PortalShell({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const status = useSession((s) => s.status);
  const restore = useSession((s) => s.restore);

  useEffect(() => {
    if (status === "unknown") void restore();
  }, [status, restore]);

  useEffect(() => {
    if (status === "signed_out") {
      const next = pathname && pathname !== "/" ? `?next=${encodeURIComponent(pathname)}` : "";
      router.replace(`/signin${next}`);
    }
  }, [status, pathname, router]);

  return (
    <div data-surface="paper" className="relative flex min-h-dvh flex-col bg-jaali lg:flex-row">
      <Sidebar />
      <div className="flex min-w-0 flex-1 flex-col">
        <TopBar />
        <main className="mx-auto w-full max-w-[1180px] flex-1 px-4 pb-16 pt-6 sm:px-8">
          {status === "signed_in" ? children : <Loading label={status === "signed_out" ? "Redirecting to sign-in" : "Checking your session"} />}
        </main>
      </div>
    </div>
  );
}
