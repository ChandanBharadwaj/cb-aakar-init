import Link from "next/link";
import { SiteNav } from "@/components/nav/SiteNav";
import { BloomMark } from "@/components/brand/BloomMark";

export default function NotFound() {
  return (
    <div className="min-h-dvh bg-jaali">
      <SiteNav />
      <main className="mx-auto grid max-w-xl gap-6 px-4 py-24 text-center">
        <BloomMark size={56} className="mx-auto" />
        <h1 className="font-display text-4xl font-semibold">This piece isn&apos;t on the shelf</h1>
        <p className="text-surface-muted">The page you were looking for doesn&apos;t exist. Let&apos;s go back to the studio.</p>
        <div className="flex justify-center gap-3">
          <Link href="/" className="ak-btn ak-btn-primary ak-btn-pill">
            Home
          </Link>
          <Link href="/shop" className="ak-btn ak-btn-secondary ak-btn-pill">
            Browse the Shop
          </Link>
        </div>
      </main>
    </div>
  );
}
