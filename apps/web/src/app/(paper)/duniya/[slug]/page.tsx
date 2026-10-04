import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { formatPaise } from "@aakar/design-tokens";
import { api, isApiError, toProblem } from "@/lib/api/client";
import type { Environment, Experience, Motif, Problem, TemplateDescriptor } from "@/lib/api/types";
import { makeItYours, shopItems } from "@/lib/catalog";
import { activeSeason, createHref, duniyaSurfaceStyle, experienceLabel, shopHref, studioToday } from "@/lib/experiences";
import { motifArtUrl, orderByPack } from "@/lib/motifs";
import { environmentBackground, environmentLabel, hasViewerPreset, presetKeyFor, viewerPresetFor } from "@/lib/viewer/environments";
import { AvatarCard } from "@/components/design/FamilyPicker";
import { SeasonBadge } from "@/components/duniya/SeasonBadge";
import { StageStrip } from "@/components/duniya/StageStrip";
import { ProblemCard } from "@/components/ui/ProblemCard";
import { SkuCard } from "@/components/ui/SkuCard";

export const dynamic = "force-dynamic";

interface DuniyaPageProps {
  params: Promise<{ slug: string }>;
}

async function load(slug: string): Promise<{ experience?: Experience; problem?: Problem }> {
  try {
    return { experience: await api.experiences.get(slug) };
  } catch (err) {
    if (isApiError(err) && err.status === 404) notFound();
    return { problem: toProblem(err) };
  }
}

export async function generateMetadata({ params }: DuniyaPageProps): Promise<Metadata> {
  const { slug } = await params;
  try {
    const experience = await api.experiences.get(slug);
    return { title: experienceLabel(experience), description: experience.tagline ?? experience.description };
  } catch {
    return { title: "Duniya" };
  }
}

/**
 * A Duniya (experience) page: the page takes the experience's accent (and hero once shot), a stage strip shows its
 * backdrop through the studio viewer's presets, then its orderable Avatars (→ `/create/{family}?duniya=<slug>`), its
 * Buti and its curated Shop items. An experience that isn't open yet (staff keep it closed, `available: false`) gets a
 * "coming soon" page with its copy. Unknown slugs are a 404.
 */
export default async function DuniyaPage({ params }: DuniyaPageProps) {
  const { slug } = await params;
  const { experience, problem } = await load(slug);
  if (!experience) {
    return (
      <main className="mx-auto w-full max-w-lg flex-1 px-4 py-10">
        <ProblemCard problem={problem ?? { title: "Couldn't reach the studio", code: "unreachable" }} title="Couldn't reach the studio" action={{ href: shopHref("duniya"), label: "Back to Duniya" }} />
      </main>
    );
  }
  // Backdrops, motifs, templates and families are niceties: the page renders without any of them.
  const [environmentsResult, motifsResult, templatesResult, familiesResult] = await Promise.allSettled([
    api.environments.list(),
    api.motifs.list(),
    api.templates.list(),
    api.families.list(),
  ]);
  const environments = environmentsResult.status === "fulfilled" ? environmentsResult.value : undefined;
  if (!experience.available) return <ComingSoon experience={experience} environments={environments} />;

  const motifs = motifsResult.status === "fulfilled" ? motifsResult.value : [];
  const templates = new Map<string, TemplateDescriptor>((templatesResult.status === "fulfilled" ? templatesResult.value : []).map((t) => [t.id, t]));
  const openFamilies =
    familiesResult.status === "fulfilled" ? new Set(familiesResult.value.filter((f) => f.available !== false && f.ready !== false).map((f) => f.id)) : undefined;

  const season = activeSeason(experience.season, studioToday());
  const hero = experience.surface.hero_media ?? undefined;
  const avatars = experience.avatars;
  const items = shopItems(experience.items);
  const buti = orderByPack(motifs, experience.motif_pack).picks;
  const label = experienceLabel(experience);

  // The stage: the experience's backdrop when the viewer has its preset, else the studio standing in for it, with the
  // stand-in form in the experience's look (Katha's comic_pop: cel-shaded with ink outlines).
  const presetKey = presetKeyFor(experience.environment, environments);
  const backdropName = environments?.find((e) => e.id === experience.environment)?.label ?? environmentLabel(presetKey);
  const built = hasViewerPreset(presetKey);
  const stage = (
    <StageStrip
      environment={viewerPresetFor(experience.environment, environments)}
      label={built ? backdropName : environmentLabel("studio")}
      standIn={built ? undefined : `${backdropName} arrives soon`}
      pbr={{ color: experience.surface.accent, roughness: 0.38, metalness: 0.12, clearcoat: 0.3, clearcoat_roughness: 0.4 }}
      look={experience.style}
    />
  );
  const facts = [
    avatars.length > 0 ? `${avatars.length} ${avatars.length === 1 ? "Avatar" : "Avatars"}` : undefined,
    typeof experience.price_from_paise === "number" ? `from ${formatPaise(experience.price_from_paise)}` : undefined,
  ].filter((x): x is string => Boolean(x));

  return (
    <main data-duniya={experience.id} className="mx-auto grid w-full max-w-[1180px] flex-1 content-start gap-10 px-4 pb-16 pt-2 sm:px-8 lg:px-11" style={duniyaSurfaceStyle(experience.surface)}>
      <Breadcrumb experience={experience} />

      <header className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.2fr)] lg:items-center">
        <div className="grid content-start gap-3">
          <span className="ak-eyebrow">Duniya · Experience</span>
          <h1 className="grid gap-1.5">
            <span className="font-display text-[52px] font-semibold leading-none sm:text-[60px]">{experience.codename}</span>
            <span className="sr-only"> · </span>
            <span className="text-lg font-semibold">{experience.title}</span>
          </h1>
          <span aria-hidden="true" className="h-1 w-16 rounded-pill bg-[var(--ak-duniya)]" />
          {season && <SeasonBadge label={season.label} className="justify-self-start" />}
          {experience.tagline && <p className="font-display text-2xl italic text-surface-muted">{experience.tagline}</p>}
          {experience.description && <p className="max-w-prose text-[15px] leading-relaxed">{experience.description}</p>}
          {facts.length > 0 && <p className="text-[13px] font-semibold text-surface-muted">{facts.join(" · ")}</p>}
        </div>
        {hero ? (
          // Hero media is an editable URL or site path from the portal; a plain <img> keeps any host working.
          // eslint-disable-next-line @next/next/no-img-element
          <img src={hero} alt="" className="ak-card aspect-[16/9] w-full object-cover" />
        ) : (
          stage
        )}
      </header>
      {hero && stage}

      <section className="grid gap-4" aria-labelledby="duniya-avatars">
        <div className="grid gap-1">
          <h2 id="duniya-avatars" className="font-display text-3xl font-semibold leading-tight">
            Give your idea an Avatar in {experience.codename}
          </h2>
          <p className="text-sm text-surface-muted">
            Each opens with {experience.codename}&apos;s backdrop and its motifs first, ready for your name, your photo or a motif.
          </p>
        </div>
        {avatars.length === 0 ? (
          <p className="ak-card p-6 text-sm text-surface-muted">The Avatars of {label} are still being finished in the studio. They open here as soon as they&apos;re ready.</p>
        ) : (
          <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3" aria-label={`Avatars in ${label}`}>
            {avatars.map((family) => (
              <li key={family.id} className="min-h-0">
                <AvatarCard family={family} href={createHref(family.id, { duniya: experience.slug })} headingLevel="h3" />
              </li>
            ))}
          </ul>
        )}
      </section>

      {buti.length > 0 && <MotifRow codename={experience.codename} motifs={buti} />}

      {items.length > 0 && (
        <section className="grid gap-4" aria-labelledby="duniya-items">
          <div className="grid gap-1">
            <h2 id="duniya-items" className="font-display text-3xl font-semibold leading-tight">
              From the Shop
            </h2>
            <p className="text-sm text-surface-muted">Proven pieces that belong in {experience.codename}, printed to order.</p>
          </div>
          <ul className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
            {items.map((item) => (
              <li key={item.slug}>
                <SkuCard item={item} makeItYours={makeItYours(item, templates.get(item.template_id), openFamilies, experience.slug)} />
              </li>
            ))}
          </ul>
        </section>
      )}

      <p>
        <Link href={shopHref("duniya")} className="ak-btn ak-btn-secondary ak-btn-pill">
          <span aria-hidden="true">←</span> All Duniya
        </Link>
      </p>
    </main>
  );
}

function Breadcrumb({ experience }: { experience: Experience }) {
  return (
    <nav aria-label="Breadcrumb" className="text-xs text-surface-muted">
      <Link href={shopHref("shelves")} className="hover:text-surface-text">
        Shop
      </Link>
      <span aria-hidden="true"> · </span>
      <Link href={shopHref("duniya")} className="hover:text-surface-text">
        Duniya
      </Link>
      <span aria-hidden="true"> · </span>
      <span aria-current="page">{experience.codename}</span>
    </nav>
  );
}

/** The experience's Buti, as artwork on cream tiles: what its Avatars offer first in the Chhaap panel. */
function MotifRow({ codename, motifs }: { codename: string; motifs: Motif[] }) {
  return (
    <section className="grid gap-3" aria-labelledby="duniya-buti">
      <div className="grid gap-1">
        <h2 id="duniya-buti" className="ak-label">
          Buti · the motifs of {codename}
        </h2>
        <p className="text-[13px] text-surface-muted">Offered first when you add a motif, raised or cut into the piece.</p>
      </div>
      <ul className="flex flex-wrap gap-3">
        {motifs.map((m) => (
          <li key={m.id} className="grid w-24 justify-items-center gap-1.5 text-center">
            <span className="grid aspect-square w-full place-items-center rounded-card border border-surface-border bg-cream p-4 shadow-card">
              {/* Library artwork served by the API; its host isn't known at build time, so a plain <img> is deliberate. */}
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img src={motifArtUrl(m)} alt="" className="h-full w-full object-contain" loading="lazy" />
            </span>
            <span className="text-[12px] font-semibold leading-tight">{m.label}</span>
          </li>
        ))}
      </ul>
    </section>
  );
}

/**
 * An experience that isn't open yet (Katha until staff switch it on): its own copy on a band in its backdrop's palette,
 * no Avatars (nothing there can be ordered yet), and the way back to the open worlds.
 */
function ComingSoon({ experience, environments }: { experience: Experience; environments?: Environment[] }) {
  const palette = environments?.find((e) => e.id === experience.environment)?.palette ?? [];
  const base = palette[0] ?? environmentBackground(experience.environment);
  const second = palette[1] ?? base;
  const dot = palette[2] ?? experience.surface.accent;
  return (
    <main data-duniya={experience.id} className="mx-auto grid w-full max-w-[1180px] flex-1 content-start gap-6 px-4 pb-16 pt-2 sm:px-8 lg:px-11" style={duniyaSurfaceStyle(experience.surface)}>
      <Breadcrumb experience={experience} />
      <section className="ak-card relative mx-auto grid w-full max-w-3xl overflow-hidden" aria-labelledby="duniya-soon">
        <div
          aria-hidden="true"
          className="h-36 sm:h-44"
          style={{
            background: `radial-gradient(circle, ${dot} 1.2px, transparent 1.7px) 0 0 / 12px 12px, radial-gradient(60% 90% at 70% 100%, color-mix(in srgb, var(--ak-duniya) 55%, transparent), transparent 70%), linear-gradient(135deg, ${base} 0%, ${second} 100%)`,
          }}
        />
        <span className="ak-ribbon">Coming soon</span>
        <div className="grid gap-4 p-6 sm:p-10">
          <span className="ak-eyebrow">Duniya · Coming soon</span>
          <h1 id="duniya-soon" className="grid gap-1.5">
            <span className="font-display text-[48px] font-semibold leading-none">{experience.codename}</span>
            <span className="sr-only"> · </span>
            <span className="text-lg font-semibold">{experience.title}</span>
          </h1>
          {experience.tagline && <p className="font-display text-2xl italic text-surface-muted">{experience.tagline}</p>}
          {experience.description && <p className="max-w-prose text-[15px] leading-relaxed">{experience.description}</p>}
          <p className="max-w-prose text-sm text-surface-muted">
            This world is still being made in the studio: its backdrop, its look and its motifs arrive together, and its pieces open here the day they do.
          </p>
          <div className="flex flex-wrap gap-3 pt-1">
            <Link href={shopHref("duniya")} className="ak-btn ak-btn-primary ak-btn-pill px-6">
              Back to Duniya
            </Link>
            <Link href="/create" className="ak-btn ak-btn-secondary ak-btn-pill px-6">
              Create something now
            </Link>
          </div>
        </div>
      </section>
    </main>
  );
}
