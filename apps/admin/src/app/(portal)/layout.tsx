import { PortalShell } from "@/components/nav/PortalShell";

/** Every page except /signin: sidebar, top bar, and the token guard. */
export default function PortalLayout({ children }: { children: React.ReactNode }) {
  return <PortalShell>{children}</PortalShell>;
}
