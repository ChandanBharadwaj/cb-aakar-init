"use client";

import Link from "next/link";
import { api } from "@/lib/api/client";
import { presetBuilt, sortEnvironments } from "@/lib/environments";
import { experienceTitle } from "@/lib/experiences";
import { familyTitle } from "@/lib/families";
import { useQuery } from "@/lib/useQuery";
import { PaletteSwatches } from "./Backdrops";
import { EmptyState } from "@/components/ui/EmptyState";
import { Loading } from "@/components/ui/Loading";
import { PageHeader } from "@/components/ui/PageHeader";
import { ProblemCard } from "@/components/ui/ProblemCard";
import { Pill } from "@/components/ui/StatusPill";

/**
 * Backgrounds (Mahaul): the viewer backdrops every `environment` field names, read-only until presets become
 * uploadable. Each row shows the storefront preset that renders it (flagged when the storefront hasn't built it yet,
 * so it shows as the studio), its palette, and which Duniya, Avatars and Shop items use it.
 */
export function EnvironmentsPage() {
  const environments = useQuery(() => api.environments.list(), "environments");
  const experiences = useQuery(() => api.experiences.list(), "experiences");
  const families = useQuery(() => api.families.list(), "families");
  const catalog = useQuery(() => api.catalog.list(), "catalog");

  const rows = environments.data ? sortEnvironments(environments.data) : undefined;
  const usage = (id: string) => ({
    experiences: experiences.data?.filter((x) => x.environment === id),
    families: families.data?.filter((f) => (f.environment ?? "studio") === id),
    items: catalog.data?.filter((c) => (c.environment ?? "studio") === id),
  });

  return (
    <>
      <PageHeader
        eyebrow="Reference"
        title="Backgrounds · Mahaul"
        description="The viewer backdrops Duniya, Avatars and Shop items can use. Read-only: a new backdrop is engineering (a viewer preset in the storefront) plus a seed row; which backdrop a piece uses is edited on the Duniya, Avatars and Catalog pages."
      />
      {environments.problem && !environments.data ? (
        <ProblemCard problem={environments.problem} action={{ label: "Try again", onClick: () => void environments.reload() }} />
      ) : !rows ? (
        <Loading />
      ) : rows.length === 0 ? (
        <EmptyState title="No backgrounds">The environments seed in packages/design-tokens/experiences.json has not been loaded.</EmptyState>
      ) : (
        <div className="ak-card overflow-hidden">
          <div className="overflow-x-auto">
            <table className="ak-table">
              <thead>
                <tr>
                  <th>Background</th>
                  <th>Surface</th>
                  <th>Viewer preset</th>
                  <th>Palette</th>
                  <th>Used by</th>
                  <th className="num">Sort</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((env) => {
                  const built = presetBuilt(env.preset_key);
                  const used = usage(env.id);
                  return (
                    <tr key={env.id}>
                      <td>
                        <div className="flex items-center gap-3">
                          <span
                            aria-hidden="true"
                            className="grid h-10 w-14 flex-none place-items-end overflow-hidden rounded-[8px] border border-black/10 p-1"
                            style={{ background: `linear-gradient(180deg, ${env.palette?.[0] ?? "#1B2238"} 0%, #1B2238 100%)` }}
                          />
                          <div className="grid gap-0.5">
                            <span className="font-medium">{env.label}</span>
                            <span className="font-mono text-[11px] text-surface-muted">{env.id}</span>
                          </div>
                        </div>
                      </td>
                      <td>
                        <Pill tone={env.surface === "stage" ? "info" : "neutral"}>{env.surface}</Pill>
                      </td>
                      <td>
                        <div className="grid gap-1">
                          <span className="font-mono text-[12px]">{env.preset_key}</span>
                          {built ? (
                            <Pill tone="success">Built</Pill>
                          ) : (
                            <Pill tone="warning" title="The storefront viewer has no preset with this key yet; pieces on this backdrop render as the studio until engineering adds it">
                              Preset pending
                            </Pill>
                          )}
                        </div>
                      </td>
                      <td>
                        <div className="grid gap-1">
                          <PaletteSwatches env={env} size={18} />
                          <span className="font-mono text-[11px] text-surface-muted">{(env.palette ?? []).join(" · ") || "—"}</span>
                        </div>
                      </td>
                      <td>
                        <Usage label="Duniya" href="/experiences" names={used.experiences?.map((x) => experienceTitle(x))} />
                        <Usage label="Avatars" href="/avatars" names={used.families?.map((f) => familyTitle(f))} />
                        <Usage label="Shop items" href="/catalog" names={used.items?.map((c) => c.name)} />
                      </td>
                      <td className="num">{env.sort_order}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </>
  );
}

/** "Duniya · 2: Utsav · Festive & gifting, Jhoomar …" — or a quiet dash while the list loads or when nothing uses it. */
function Usage({ label, href, names }: { label: string; href: string; names?: string[] }) {
  if (!names) {
    return <div className="text-[12px] text-surface-muted">{label} · …</div>;
  }
  if (names.length === 0) {
    return <div className="text-[12px] text-surface-muted">{label} · none</div>;
  }
  return (
    <div className="text-[12px]" title={names.join("\n")}>
      <Link href={href} className="font-medium hover:underline">
        {label} · {names.length}
      </Link>
      <span className="text-surface-muted">: {names.slice(0, 3).join(", ")}{names.length > 3 ? `, +${names.length - 3} more` : ""}</span>
    </div>
  );
}
