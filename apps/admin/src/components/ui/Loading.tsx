import { BloomLoader } from "@/components/brand/BloomLoader";

export function Loading({ label = "Loading", className }: { label?: string | null; className?: string }) {
  return <BloomLoader size={88} label={label} className={["py-16 text-surface-muted", className].filter(Boolean).join(" ")} />;
}
