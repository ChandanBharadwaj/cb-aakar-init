import type { Metadata } from "next";
import { ContentRulesPage } from "@/components/content-rules/ContentRulesPage";

export const metadata: Metadata = { title: "Content rules" };

export default function Page() {
  return <ContentRulesPage />;
}
