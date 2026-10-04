import type { Metadata } from "next";
import { AvatarsPage } from "@/components/avatars/AvatarsPage";

export const metadata: Metadata = { title: "Avatars" };

export default function Page() {
  return <AvatarsPage />;
}
