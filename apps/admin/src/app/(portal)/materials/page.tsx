import type { Metadata } from "next";
import { MaterialsPage } from "@/components/materials/MaterialsPage";

export const metadata: Metadata = { title: "Materials" };

export default function Page() {
  return <MaterialsPage />;
}
