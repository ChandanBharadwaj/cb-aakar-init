import { materialById } from "@aakar/design-tokens";

export interface FinishTileProps {
  materialId: string;
  /** Thumbnail from the API when present; otherwise the finish colour stands in. */
  thumbnailUrl?: string | null;
  title: string;
  size?: number;
  className?: string;
}

/** Thumbnail placeholder for cart and order rows: a soft form tinted by the finish. */
export function FinishTile({ materialId, thumbnailUrl, title, size = 72, className }: FinishTileProps) {
  const m = materialById(materialId);
  const color = m?.pbr.color ?? "#B56E52";
  const sheen = m?.finish_class === "silk" ? (m.pbr.sheen_color ?? "#fff") : undefined;
  if (thumbnailUrl) {
    return (
      // eslint-disable-next-line @next/next/no-img-element
      <img src={thumbnailUrl} alt="" width={size} height={size} className={["flex-none rounded-control object-cover", className].filter(Boolean).join(" ")} />
    );
  }
  return (
    <div
      className={["relative grid flex-none place-items-center overflow-hidden rounded-control bg-gradient-to-b from-[#EBDFCB] to-[#CBB79A]", className].filter(Boolean).join(" ")}
      style={{ width: size, height: size }}
      aria-hidden="true"
      title={m?.name ?? materialId}
    >
      <div className="absolute inset-x-0 bottom-0 h-1/4 bg-gradient-to-b from-[#8B5A3C] to-[#5E3A24] opacity-70" />
      <div
        className="relative -mt-2 rounded-[50%/60%_60%_35%_35%] shadow-card"
        style={{
          width: size * 0.42,
          height: size * 0.52,
          background: sheen ? `linear-gradient(135deg, ${sheen} 0%, ${color} 45%, ${color} 100%)` : `linear-gradient(#F1C7AE33, ${color})`,
        }}
      >
        <span className="sr-only">{title}</span>
      </div>
    </div>
  );
}
