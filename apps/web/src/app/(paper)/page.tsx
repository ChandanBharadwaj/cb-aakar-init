import Link from "next/link";
import { BloomMark } from "@/components/brand/BloomMark";
import { PromptBar } from "@/components/ui/PromptBar";
import { EXAMPLE_PROMPTS } from "@/lib/catalog";

const ENTRIES = [
  { eyebrow: "Ready-made", title: "Shop", copy: "Ready-made pieces, printed to order", href: "/shop" },
  { eyebrow: "From a description", title: "Create", copy: "Describe it. We sculpt it.", href: "/create" },
  { eyebrow: "From an existing piece", title: "Remix", copy: "Take a proven piece, make it yours", href: "/shop?from=remix", note: "Start from any Shop piece with “Modify with AI”." },
] as const;

export default function HomePage() {
  return (
    <main className="relative flex-1">
      <div className="ak-hero-glow pointer-events-none absolute inset-0" aria-hidden="true" />
      <section className="relative mx-auto grid max-w-[1180px] items-center gap-12 px-4 pb-10 pt-6 sm:px-8 lg:grid-cols-[1fr_470px] lg:px-11 lg:pt-10">
        <div className="grid gap-6">
          <p className="ak-eyebrow">AI-crafted · 3D-printed · Delivered across India</p>
          <h1 className="font-display text-[44px] font-semibold leading-[1.02] sm:text-[56px] lg:text-[62px]">
            Things you imagine,
            <br />
            made <em className="text-terracotta">real</em>.
          </h1>
          <p className="max-w-[460px] text-surface-muted">Describe an object in plain words. We sculpt it in 3D, print it in your finish, and ship it to your door.</p>
          <PromptBar placeholder="What do you want to create today?" examples={EXAMPLE_PROMPTS} />
        </div>
        <HeroPanel />
      </section>

      <section className="relative mx-auto grid max-w-[1180px] gap-4 px-4 pb-16 sm:grid-cols-3 sm:px-8 lg:px-11" aria-label="Ways to start">
        {ENTRIES.map((e) => (
          <Link
            key={e.href}
            href={e.href}
            className="ak-card group grid gap-2 p-5 transition-all duration-base ease-ak hover:-translate-y-0.5 hover:shadow-float"
          >
            <span className="ak-label">{e.eyebrow}</span>
            <span className="font-display text-2xl font-semibold group-hover:text-terracotta">{e.title}</span>
            <span className="text-sm text-surface-muted">{e.copy}</span>
            {"note" in e && <span className="mt-1 text-[11px] text-surface-muted">{e.note}</span>}
          </Link>
        ))}
      </section>
    </main>
  );
}

/** The warm still-life panel from the Home board: a clay lotus lamp on a teak table, drawn in CSS. */
function HeroPanel() {
  return (
    <div className="relative hidden h-[300px] overflow-hidden rounded-[18px] bg-gradient-to-br from-[#E9DCC6] to-[#CBB79A] shadow-float lg:block" aria-hidden="true">
      <div className="absolute inset-x-0 bottom-0 h-[90px] bg-gradient-to-b from-[#8B5A3C] to-[#5E3A24]" />
      <div className="absolute inset-0" style={{ background: "radial-gradient(260px 160px at 50% 55%, rgba(232,199,120,.5), transparent 70%)" }} />
      <div className="absolute bottom-[70px] left-1/2 h-4 w-40 -translate-x-1/2 rounded-full bg-black/30 blur-md" />
      <div className="absolute bottom-[84px] left-1/2 h-5 w-24 -translate-x-1/2 rounded-full bg-gradient-to-b from-[#C88462] to-[#8F5238]" />
      <div className="absolute bottom-[98px] left-1/2 h-20 w-5 -translate-x-1/2 rounded-xl bg-gradient-to-r from-[#D9977A] via-terracotta to-[#8F5238]" />
      {[-38, 38, -14, 14].map((deg, i) => (
        <div
          key={deg}
          className="absolute bottom-[160px] left-1/2 w-14 origin-bottom rounded-[50%/80%_80%_20%_20%] bg-gradient-to-b from-[#F1C7AE] to-terracotta"
          style={{ height: i < 2 ? 96 : 112, transform: `translateX(-50%) rotate(${deg}deg)` }}
        />
      ))}
      <div className="absolute bottom-[160px] left-1/2 h-[120px] w-[52px] -translate-x-1/2 rounded-[50%/80%_80%_20%_20%] bg-gradient-to-b from-[#F6D6C1] to-[#D9977A]" />
      <div className="absolute bottom-[214px] left-1/2 h-4 w-4 -translate-x-1/2 rounded-full bg-[#FFF0C2] shadow-[0_0_34px_12px_rgba(232,199,120,.75)]" />
      <div className="absolute right-4 top-4 rounded-pill bg-paper/90 px-3 py-1.5 text-[11px] font-semibold text-ink shadow-card">
        Lotus Table Lamp · Terracotta Silk
      </div>
      <BloomMark size={40} className="absolute bottom-4 right-4 text-ink/70" />
    </div>
  );
}
