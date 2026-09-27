import type { Metadata } from "next";
import { CatalogPage } from "@/components/catalog/CatalogPage";

export const metadata: Metadata = { title: "Catalog" };

export default function Page() {
  return <CatalogPage />;
}
