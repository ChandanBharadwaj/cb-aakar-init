import type { Metadata } from "next";
import { Suspense } from "react";
import { AuditPage } from "@/components/audit/AuditPage";
import { Loading } from "@/components/ui/Loading";

export const metadata: Metadata = { title: "Audit" };

export default function Page() {
  return (
    <Suspense fallback={<Loading />}>
      <AuditPage />
    </Suspense>
  );
}
