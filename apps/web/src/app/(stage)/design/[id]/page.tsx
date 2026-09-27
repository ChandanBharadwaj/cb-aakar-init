import type { Metadata } from "next";
import { DesignStudio } from "@/components/design/DesignStudio";

export const metadata: Metadata = { title: "Create" };

interface DesignPageProps {
  params: Promise<{ id: string }>;
  /** `job`: the generation job to follow; `duniya`: the Duniya experience the piece is made for (its slug). */
  searchParams: Promise<{ job?: string; duniya?: string }>;
}

export default async function DesignPage({ params, searchParams }: DesignPageProps) {
  const [{ id }, { job, duniya }] = await Promise.all([params, searchParams]);
  return <DesignStudio designId={id} jobId={job || undefined} duniya={duniya || undefined} />;
}
