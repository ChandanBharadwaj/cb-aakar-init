"use client";

import { api } from "@/lib/api/client";
import { formatDateTime } from "@/lib/format";
import { useQuery } from "@/lib/useQuery";
import { PolicyCard } from "./PolicyCard";
import { PublishPolicyForm } from "./PublishPolicyForm";
import { Loading } from "@/components/ui/Loading";
import { PageHeader } from "@/components/ui/PageHeader";
import { ProblemCard } from "@/components/ui/ProblemCard";
import { Pill } from "@/components/ui/StatusPill";

export function PricingPage() {
  const policies = useQuery(() => api.pricing.policies(), "policies");
  const materials = useQuery(() => api.materials.list(), "materials");
  const versions = policies.data ?? [];
  const active = versions.find((v) => v.active);

  return (
    <>
      <PageHeader eyebrow="Configuration" title="Pricing" description="Rates, fees, margin and shipping rules are versioned policies. Publishing a new version makes it active; older versions stay for the orders priced with them." />
      {policies.problem && !policies.data ? (
        <ProblemCard problem={policies.problem} action={{ label: "Try again", onClick: () => void policies.reload() }} />
      ) : !policies.data || !materials.data ? (
        materials.problem && !materials.data ? (
          <ProblemCard problem={materials.problem} action={{ label: "Try again", onClick: () => void materials.reload() }} />
        ) : (
          <Loading />
        )
      ) : !active ? (
        <ProblemCard problem={{ title: "No active policy", detail: "The API returned no active pricing policy. Publish one to price anything.", code: "no_active_policy" }} />
      ) : (
        <div className="grid gap-6">
          <div className="grid gap-4 lg:grid-cols-[360px_minmax(0,1fr)]">
            <PolicyCard version={active} headline="Active policy" />
            <section className="ak-card overflow-hidden" aria-labelledby="versions-heading">
              <h2 id="versions-heading" className="px-5 pt-5 font-display text-2xl font-semibold">
                Versions
              </h2>
              <div className="overflow-x-auto pt-3">
                <table className="ak-table">
                  <thead>
                    <tr>
                      <th>Version</th>
                      <th>Note</th>
                      <th>Published</th>
                    </tr>
                  </thead>
                  <tbody>
                    {versions.map((v) => (
                      <tr key={v.version}>
                        <td>
                          <div className="flex items-center gap-2">
                            <span className="font-mono text-[13px] font-semibold">{v.version}</span>
                            {v.active && <Pill tone="success">Active</Pill>}
                          </div>
                        </td>
                        <td className="max-w-md text-surface-muted">{v.note ?? "—"}</td>
                        <td className="whitespace-nowrap text-surface-muted">
                          {v.created_by}
                          <br />
                          {formatDateTime(v.created_at)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </section>
          </div>
          <PublishPolicyForm key={active.version} active={active} versions={versions} materials={materials.data} onPublished={() => void policies.reload()} />
        </div>
      )}
    </>
  );
}
