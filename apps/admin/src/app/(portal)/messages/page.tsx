import type { Metadata } from "next";
import { Suspense } from "react";
import { MessagesPage } from "@/components/messages/MessagesPage";
import { Loading } from "@/components/ui/Loading";

export const metadata: Metadata = { title: "Messages" };

export default function Page() {
  return (
    <Suspense fallback={<Loading />}>
      <MessagesPage />
    </Suspense>
  );
}
