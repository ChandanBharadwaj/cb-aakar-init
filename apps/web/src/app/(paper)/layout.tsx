import { SiteNav } from "@/components/nav/SiteNav";

/** Paper surface: cream pages with the subtle jaali lattice (Home, Shop, Orders, Remix). */
export default function PaperLayout({ children }: { children: React.ReactNode }) {
  return (
    <div data-surface="paper" className="relative flex min-h-dvh flex-col bg-jaali">
      <SiteNav />
      {children}
    </div>
  );
}
