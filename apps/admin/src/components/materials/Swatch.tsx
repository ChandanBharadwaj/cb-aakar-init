import type { MaterialPbr } from "@/lib/api/types";

/** Colour swatch from the PBR preset; silk finishes get a sheen highlight like the storefront's finish chips. */
export function Swatch({ pbr, finishClass, size = 18, className }: { pbr: MaterialPbr; finishClass: "matte" | "silk"; size?: number; className?: string }) {
  return (
    <span
      aria-hidden="true"
      className={["inline-block flex-none rounded-full border border-black/10", className].filter(Boolean).join(" ")}
      style={{
        width: size,
        height: size,
        background: finishClass === "silk" ? `linear-gradient(135deg, ${pbr.sheen_color ?? "#fff"} 0%, ${pbr.color} 45%, ${pbr.color} 100%)` : pbr.color,
        boxShadow: finishClass === "silk" ? "inset 0 0 0 1px rgba(255,255,255,.35)" : undefined,
      }}
    />
  );
}
