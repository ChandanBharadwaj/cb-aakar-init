import type { Metadata } from "next";
import { SharedPiece } from "@/components/share/SharedPiece";

export const metadata: Metadata = { title: "Crafted by Aakar" };

/** /k/{code}: the link printed on the packaging card. Reprint the exact piece or remix it. */
export default async function SharePage({ params }: { params: Promise<{ code: string }> }) {
  const { code } = await params;
  return (
    <main className="mx-auto flex w-full max-w-[880px] flex-1 flex-col px-4 pb-16 pt-6 sm:px-8 lg:px-11">
      <SharedPiece code={code.toUpperCase()} />
    </main>
  );
}
