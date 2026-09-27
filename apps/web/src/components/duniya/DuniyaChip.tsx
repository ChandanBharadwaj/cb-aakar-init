import Link from "next/link";
import type { DuniyaPreset } from "@/lib/experiences";
import { experienceLabel } from "@/lib/experiences";

export interface DuniyaChipProps {
  duniya: Pick<DuniyaPreset, "slug" | "codename" | "title" | "accent">;
  className?: string;
}

/** "● Utsav · Festive & gifting": the world a piece is being made for, linking back to its Duniya page. */
export function DuniyaChip({ duniya, className }: DuniyaChipProps) {
  return (
    <Link
      href={`/duniya/${encodeURIComponent(duniya.slug)}`}
      className={["ak-chip min-h-8 justify-self-start px-3 py-1 text-[11.5px] text-surface-text", className].filter(Boolean).join(" ")}
      style={{ borderColor: duniya.accent }}
      title={`Made for ${experienceLabel(duniya)}: back to its Duniya page`}
    >
      <span aria-hidden="true" className="h-2 w-2 flex-none rounded-full ring-1 ring-white/40" style={{ background: duniya.accent }} />
      <span className="sr-only">Duniya: </span>
      {experienceLabel(duniya)}
    </Link>
  );
}
