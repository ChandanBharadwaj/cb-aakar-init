import type { Metadata, Viewport } from "next";
import { tokens } from "@aakar/design-tokens";
import "@aakar/design-tokens/css/tokens.css";
import "./globals.css";

export const metadata: Metadata = {
  title: { default: "Aakar Studio", template: "%s · Aakar Studio" },
  description: "Staff-only management portal for the Aakar 3D printing studio: orders, pricing, materials, catalog, Avatars, hardware, templates, content reviews, messages, audit.",
  applicationName: "Aakar Studio",
  robots: { index: false, follow: false },
};

export const viewport: Viewport = {
  themeColor: tokens.color.cream,
  width: "device-width",
  initialScale: 1,
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <head>
        {/* Fonts load at runtime so the build never depends on fonts.googleapis.com being reachable. */}
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link rel="preconnect" href="https://fonts.gstatic.com" crossOrigin="anonymous" />
        <link rel="stylesheet" href={tokens.font.google_fonts_url} />
      </head>
      <body className="min-h-dvh font-ui antialiased">{children}</body>
    </html>
  );
}
