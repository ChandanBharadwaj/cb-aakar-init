import type { Metadata } from "next";
import { Suspense } from "react";
import { BloomLoader } from "@/components/brand/BloomLoader";
import { StageNav } from "@/components/nav/StageNav";
import { NewDesignRunner } from "@/components/design/NewDesignRunner";

export const metadata: Metadata = { title: "Preparing your piece" };

export default function NewDesignPage() {
  return (
    <div className="flex min-h-dvh flex-col">
      <StageNav section="Create" backHref="/shop" backLabel="Back to the Shop" />
      <main className="grid flex-1 place-items-center p-6">
        <div className="w-full max-w-lg">
          <Suspense fallback={<BloomLoader size={132} label="Preparing your piece" />}>
            <NewDesignRunner />
          </Suspense>
        </div>
      </main>
    </div>
  );
}
