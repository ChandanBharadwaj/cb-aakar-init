import type { Metadata } from "next";
import { HardwarePage } from "@/components/hardware/HardwarePage";

export const metadata: Metadata = { title: "Hardware" };

export default function Page() {
  return <HardwarePage />;
}
