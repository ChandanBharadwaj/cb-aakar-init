import type { Metadata } from "next";
import { EnvironmentsPage } from "@/components/environments/EnvironmentsPage";

export const metadata: Metadata = { title: "Backgrounds" };

export default function Page() {
  return <EnvironmentsPage />;
}
