import type { Metadata } from "next";
import { PricingPage } from "@/components/pricing/PricingPage";

export const metadata: Metadata = { title: "Pricing" };

export default function Page() {
  return <PricingPage />;
}
