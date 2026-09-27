import Link from "next/link";
import { BloomMark } from "@/components/brand/BloomMark";

export default function NotFound() {
  return (
    <div data-surface="paper" className="grid min-h-dvh place-items-center bg-jaali px-4">
      <main className="ak-card grid max-w-md gap-5 p-8 text-center">
        <BloomMark size={48} className="mx-auto" />
        <h1 className="font-display text-3xl font-semibold">No such page in the portal</h1>
        <p className="text-sm text-surface-muted">Check the link, or start from the dashboard.</p>
        <div className="flex justify-center gap-3">
          <Link href="/" className="ak-btn ak-btn-primary ak-btn-pill">
            Dashboard
          </Link>
          <Link href="/orders" className="ak-btn ak-btn-secondary ak-btn-pill">
            Orders
          </Link>
        </div>
      </main>
    </div>
  );
}
