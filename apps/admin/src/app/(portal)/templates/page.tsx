import type { Metadata } from "next";
import { TemplatesPage } from "@/components/templates/TemplatesPage";

export const metadata: Metadata = { title: "Templates" };

export default function Page() {
  return <TemplatesPage />;
}
