import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { formatPaise, materialById } from "@aakar/design-tokens";
import { api, isApiError, toProblem } from "@/lib/api/client";
import { isAvailable, type CatalogItem, type Problem } from "@/lib/api/types";
import { categoryLabel } from "@/lib/catalog";
import { AddToCartButton } from "@/components/cart/AddToCartButton";
import { StartWithPiece } from "@/components/shop/StartWithPiece";
import { ProblemCard } from "@/components/ui/ProblemCard";

export const dynamic = "force-dynamic";

interface ItemPageProps {
  params: Promise<{ slug: string }>;
}

async function load(slug: string): Promise<{ item?: CatalogItem; problem?: Problem }> {
  try {
    return { item: await api.catalog.item(slug) };
  } catch (err) {
    if (isApiError(err) && err.status === 404) notFound();
    return { problem: toProblem(err) };
  }
}

export async function generateMetadata({ params }: ItemPageProps): Promise<Metadata> {
  const { slug } = await params;
  try {
    const item = await api.catalog.item(slug);
    return { title: item.name, description: item.description ?? item.specs_line };
  } catch {
    return { title: "Shop" };
  }
}

export default async function ItemPage({ params }: ItemPageProps) {
  const { slug } = await params;
  const { item, problem } = await load(slug);

  if (!item) {
    return (
      <main className="mx-auto w-full max-w-lg flex-1 px-4 py-10">
        <ProblemCard problem={problem ?? { title: "Couldn't reach the studio", code: "unreachable" }} title="Couldn't reach the studio" action={{ href: "/shop", label: "Back to the Shop" }} />
      </main>
    );
  }

  const available = isAvailable(item);
  const finish = materialById(item.default_material);
  const image = item.media?.find((m) => m.kind === "image" || m.kind === "thumbnail")?.url ?? item.media?.[0]?.url;

  return (
    <main className="mx-auto w-full max-w-[1180px] flex-1 px-4 pb-16 pt-2 sm:px-8 lg:px-11">
      <nav aria-label="Breadcrumb" className="mb-4 text-xs text-surface-muted">
        <Link href="/shop" className="hover:text-surface-text">
          Shop
        </Link>
        <span aria-hidden="true"> · </span>
        <Link href={`/shop?category=${item.category}`} className="hover:text-surface-text">
          {categoryLabel(item.category)}
        </Link>
      </nav>

      <div className="grid gap-8 lg:grid-cols-[1fr_420px]">
        <div className="ak-card relative aspect-[4/3] overflow-hidden">
          {image ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img src={image} alt={item.name} className="h-full w-full object-cover" />
          ) : (
            <div className="grid h-full w-full place-items-center bg-gradient-to-b from-[#EBDFCB] to-[#CBB79A]">
              <div className="absolute inset-x-0 bottom-0 h-1/4 bg-gradient-to-b from-[#8B5A3C] to-[#5E3A24] opacity-80" />
              <div
                className="relative -mt-8 h-40 w-32 rounded-[50%/60%_60%_35%_35%] shadow-float"
                style={{ background: `linear-gradient(#F1C7AE, ${finish?.pbr.color ?? "#B56E52"})` }}
              />
            </div>
          )}
          {!available && <span className="ak-ribbon">Coming soon</span>}
        </div>

        <div className="grid content-start gap-5">
          <div className="grid gap-2">
            <span className="ak-eyebrow">{categoryLabel(item.category)}</span>
            <h1 className="font-display text-[40px] font-semibold leading-none">{item.name}</h1>
            <p className="text-sm text-surface-muted">{item.specs_line}</p>
          </div>
          <div className="font-display text-3xl font-bold">{formatPaise(item.base_price_paise)}</div>
          {item.description && <p className="max-w-prose text-[15px] leading-relaxed">{item.description}</p>}
          <dl className="ak-well grid grid-cols-2 gap-x-4 gap-y-2 p-4 text-xs">
            <dt className="text-surface-muted">Default finish</dt>
            <dd className="font-semibold">{finish?.name ?? item.default_material}</dd>
            <dt className="text-surface-muted">Template</dt>
            <dd className="font-semibold">{item.template_id.replace(/_/g, " ")}</dd>
            {item.environment && (
              <>
                <dt className="text-surface-muted">Shown on</dt>
                <dd className="font-semibold">{item.environment.replace(/_/g, " ")}</dd>
              </>
            )}
          </dl>
          <div className="grid gap-3 sm:grid-cols-[auto_auto] sm:items-start">
            <StartWithPiece slug={item.slug} disabled={!available} />
            <AddToCartButton item={item} className="[&>button]:ak-btn-pill [&>button]:min-h-11 [&>button]:px-6 [&>button]:text-sm" />
          </div>
          <p className="text-xs text-surface-muted">
            Starting a piece opens it in the studio, where you can change its size, pick a finish and see the price live. “Add to Cart” sculpts it as shown, in{" "}
            {finish?.name ?? "the default finish"}. Nothing is printed until you pay.
          </p>
        </div>
      </div>
    </main>
  );
}
