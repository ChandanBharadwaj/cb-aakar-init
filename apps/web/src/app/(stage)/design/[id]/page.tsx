import type { Metadata } from "next";
import { DesignStudio } from "@/components/design/DesignStudio";

export const metadata: Metadata = { title: "Create" };

interface DesignPageProps {
  params: Promise<{ id: string }>;
  searchParams: Promise<{ job?: string }>;
}

export default async function DesignPage({ params, searchParams }: DesignPageProps) {
  const [{ id }, { job }] = await Promise.all([params, searchParams]);
  return <DesignStudio designId={id} jobId={job || undefined} />;
}
