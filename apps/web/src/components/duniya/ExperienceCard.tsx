import Link from "next/link";
import { formatPaise } from "@aakar/design-tokens";
import type { Experience } from "@/lib/api/types";
import { activeSeason, duniyaSurfaceStyle, experienceHref } from "@/lib/experiences";
import { environmentBackground } from "@/lib/viewer/environments";
import { SeasonBadge } from "./SeasonBadge";

export interface ExperienceCardProps {
  experience: Experience;
  /** YYYY-MM-DD in the studio's time zone; the season badge shows when today is inside a window. */
  today: string;
}

/**
 * A Duniya on the Shop: the hero (or, until one is shot, a little stage in the experience's backdrop colour lit by its
 * accent), "Codename · Title", tagline, how many Avatars it opens and the lowest floor price, and a season badge when
 * today is in season. The card takes the experience's accent.
 */
export function ExperienceCard({ experience, today }: ExperienceCardProps) {
  const season = activeSeason(experience.season, today);
  const hero = experience.surface.hero_media ?? undefined;
  const backdrop = environmentBackground(experience.environment);
  const count = experience.avatars.length;
  const headingId = `duniya-${experience.id}`;
  const facts = [
    count > 0 ? `${count} ${count === 1 ? "Avatar" : "Avatars"}` : "Avatars on their way",
    typeof experience.price_from_paise === "number" ? `from ${formatPaise(experience.price_from_paise)}` : undefined,
  ].filter((x): x is string => Boolean(x));
  return (
    <Link
      href={experienceHref(experience)}
      aria-labelledby={headingId}
      className="ak-card group relative grid h-full overflow-hidden transition-all duration-base ease-ak hover:-translate-y-0.5 hover:shadow-float"
      style={duniyaSurfaceStyle(experience.surface)}
    >
      <div className="relative aspect-[16/9] overflow-hidden" aria-hidden="true">
        {hero ? (
          // Hero media is an editable URL or site path from the portal; a plain <img> keeps any host working.
          // eslint-disable-next-line @next/next/no-img-element
          <img src={hero} alt="" className="h-full w-full object-cover" loading="lazy" />
        ) : (
          <MiniStage backdrop={backdrop} />
        )}
        <span className="absolute inset-x-0 bottom-0 h-1 bg-[var(--ak-duniya)]" />
      </div>
      {season && <SeasonBadge label={season.label} className="absolute left-3 top-3" />}
      <div className="grid content-start gap-2 p-5">
        <h2 id={headingId} className="grid gap-1">
          <span className="font-display text-3xl font-semibold leading-none group-hover:text-surface-accent">{experience.codename}</span>
          <span className="sr-only"> · </span>
          <span className="text-sm font-semibold">{experience.title}</span>
        </h2>
        {experience.tagline && <p className="text-sm text-surface-muted">{experience.tagline}</p>}
        <p className="text-[12px] text-surface-muted">{facts.join(" · ")}</p>
        <span className="text-[12px] font-semibold text-surface-accent">
          Enter {experience.codename} <span aria-hidden="true">→</span>
        </span>
      </div>
    </Link>
  );
}

/** Until hero media is shot: the backdrop as a small stage, a floor, a spotlight in the accent and a clay form on it. */
function MiniStage({ backdrop }: { backdrop: string }) {
  return (
    <div
      className="relative h-full w-full"
      style={{
        background: `radial-gradient(38% 55% at 50% 74%, color-mix(in srgb, var(--ak-duniya) 42%, transparent), transparent 72%), linear-gradient(180deg, ${backdrop} 0%, ${backdrop} 64%, #10141F 64%, #1B2238 100%)`,
      }}
    >
      <div className="absolute inset-0 bg-jaali opacity-40" />
      <div className="absolute bottom-[26%] left-1/2 h-3 w-24 -translate-x-1/2 rounded-full bg-black/40 blur-md" />
      <div
        className="absolute bottom-[29%] left-1/2 h-16 w-12 -translate-x-1/2 rounded-[50%/60%_60%_35%_35%] shadow-float"
        style={{ background: "linear-gradient(180deg, #F1C7AE 0%, var(--ak-duniya) 100%)" }}
      />
    </div>
  );
}
