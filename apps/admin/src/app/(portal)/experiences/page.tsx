import type { Metadata } from "next";
import { ExperiencesPage } from "@/components/experiences/ExperiencesPage";

export const metadata: Metadata = { title: "Duniya" };

export default function Page() {
  return <ExperiencesPage />;
}
