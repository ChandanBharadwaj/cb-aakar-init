import type { Metadata, Viewport } from "next";
import { tokens } from "@aakar/design-tokens";
import "@aakar/design-tokens/css/tokens.css";
import "./globals.css";
import { IdentityBoot } from "@/components/identity/IdentityBoot";
import { Toaster } from "@/components/ui/Toaster";

export const metadata: Metadata = {
  title: { default: "Aakar · Things you imagine, made real", template: "%s · Aakar" },
  description:
    "Aakar is an AI-assisted 3D printing studio. Describe an object in plain words; we sculpt it in 3D, print it in your finish, and ship it anywhere in India.",
  applicationName: "Aakar",
  keywords: ["3D printing", "India", "AI design", "custom decor", "Aakar"],
};

export const viewport: Viewport = {
  themeColor: [
    { media: "(prefers-color-scheme: light)", color: tokens.color.cream },
    { media: "(prefers-color-scheme: dark)", color: tokens.color.indigo_deep },
  ],
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
      <body className="min-h-dvh font-ui antialiased">
        <IdentityBoot />
        {children}
        <Toaster />
      </body>
    </html>
  );
}
