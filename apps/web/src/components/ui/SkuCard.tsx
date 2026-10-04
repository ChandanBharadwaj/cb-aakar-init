import Link from "next/link";
import { formatPaise } from "@aakar/design-tokens";
import { isAvailable, type CatalogItem } from "@/lib/api/types";
import type { MakeItYours } from "@/lib/catalog";
import { AddToCartButton } from "@/components/cart/AddToCartButton";

export interface SkuCardProps {
  item: CatalogItem;
  /** "Make it yours · add a photo or your name" → the Avatar composer, when the item's template takes a Chhaap. */
  makeItYours?: MakeItYours;
}

/** A placeholder "form" for items without media: a soft clay silhouette on sand, tinted by the default finish. */
function Placeholder({ item }: { item: CatalogItem }) {
  const initial = item.name.trim().charAt(0).toUpperCase();
  return (
    <div className="relative grid h-full w-full place-items-center overflow-hidden bg-gradient-to-b from-[#EBDFCB] to-[#CBB79A]">
      <div className="absolute inset-x-0 bottom-0 h-1/4 bg-gradient-to-b from-[#8B5A3C] to-[#5E3A24] opacity-80" aria-hidden="true" />
      <div className="absolute bottom-[22%] h-3 w-24 rounded-full bg-black/30 blur-md" aria-hidden="true" />
      <div className="relative -mt-4 grid h-24 w-20 place-items-center rounded-[50%/60%_60%_35%_35%] bg-gradient-to-b from-[#F1C7AE] to-terracotta shadow-float">
        <span className="font-display text-3xl font-bold text-cream/90">{initial}</span>
      </div>
    </div>
  );
}

export function SkuCard({ item, makeItYours }: SkuCardProps) {
  const available = isAvailable(item);
  const image = item.media?.find((m) => m.kind === "image" || m.kind === "thumbnail")?.url ?? item.media?.[0]?.url;
  return (
    <article className="ak-card relative grid overflow-hidden transition-shadow duration-base ease-ak hover:shadow-float" aria-labelledby={`sku-${item.slug}`}>
      <Link href={`/shop/${item.slug}`} className="relative block aspect-[4/3] overflow-hidden" aria-label={`${item.name} details`}>
        {image ? (
          // Media comes from the API's object store; sizes/hosts aren't known at build time, so a plain <img> is deliberate.
          // eslint-disable-next-line @next/next/no-img-element
          <img src={image} alt="" className="h-full w-full object-cover" loading="lazy" />
        ) : (
          <Placeholder item={item} />
        )}
        {!available && <span className="ak-ribbon">Coming soon</span>}
      </Link>
      <div className="grid gap-3 p-4">
        <div className="flex items-start justify-between gap-3">
          <h3 id={`sku-${item.slug}`} className="font-display text-xl font-semibold leading-tight">
            <Link href={`/shop/${item.slug}`} className="hover:text-surface-accent">
              {item.name}
            </Link>
          </h3>
          <span className="font-display text-xl font-bold">{formatPaise(item.base_price_paise)}</span>
        </div>
        <p className="text-xs text-surface-muted">{item.specs_line}</p>
        {makeItYours && available && (
          <Link href={makeItYours.href} className="text-[12px] font-semibold text-surface-accent hover:underline">
            {makeItYours.label}
          </Link>
        )}
        <div className="grid grid-cols-2 items-start gap-2">
          <AddToCartButton item={item} />
          {available ? (
            <Link href={`/design/new?item=${encodeURIComponent(item.slug)}`} className="ak-btn ak-btn-secondary min-h-10 text-xs">
              Modify with AI
            </Link>
          ) : (
            <button type="button" className="ak-btn ak-btn-secondary min-h-10 text-xs" disabled title="This piece is still being finished">
              Modify with AI
            </button>
          )}
        </div>
      </div>
    </article>
  );
}
