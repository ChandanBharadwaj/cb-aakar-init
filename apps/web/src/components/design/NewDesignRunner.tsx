"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { api, toProblem, type CreateDesignBody } from "@/lib/api/client";
import type { Problem } from "@/lib/api/types";
import { BloomLoader } from "@/components/brand/BloomLoader";
import { ProblemCard } from "@/components/ui/ProblemCard";

/** /design/new?item=slug or ?template=id → POST /api/designs → /design/{id}?job=… */
export function NewDesignRunner() {
  const params = useSearchParams();
  const router = useRouter();
  const item = params.get("item");
  const template = params.get("template");
  const started = useRef(false);
  const [problem, setProblem] = useState<Problem>();

  useEffect(() => {
    if (started.current || (!item && !template)) return;
    started.current = true;
    const body: CreateDesignBody = item ? { source: "shop", catalog_item_slug: item } : { source: "remix", template_id: template ?? undefined };
    api.designs
      .create(body)
      .then((accepted) => router.replace(`/design/${accepted.design_id}?job=${encodeURIComponent(accepted.job_id)}`))
      .catch((err) => setProblem(toProblem(err)));
  }, [item, template, router]);

  if (!item && !template) {
    return (
      <ProblemCard
        problem={{ title: "Nothing to start from", detail: "Pick a piece in the Shop or a template on Create and we'll take it from there.", code: "missing_source" }}
        action={{ href: "/shop", label: "Browse the Shop" }}
      />
    );
  }

  if (problem) {
    return (
      <ProblemCard problem={problem} action={{ href: item ? `/shop/${item}` : "/create", label: item ? "Back to the piece" : "Back to templates" }}>
        <p className="text-xs text-surface-muted">
          Or <Link href="/shop" className="underline">go back to the Shop</Link>.
        </p>
      </ProblemCard>
    );
  }

  return <BloomLoader size={132} label="Preparing your piece" />;
}
