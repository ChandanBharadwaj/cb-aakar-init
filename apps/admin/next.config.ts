import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  reactStrictMode: true,
  // @aakar/design-tokens ships TypeScript source and JSON; let Next compile it.
  transpilePackages: ["@aakar/design-tokens"],
};

export default nextConfig;
