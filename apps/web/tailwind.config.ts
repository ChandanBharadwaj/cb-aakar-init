import type { Config } from "tailwindcss";
// The preset is CommonJS (it requires tokens.json), hence the .cjs path.
// eslint-disable-next-line @typescript-eslint/no-require-imports
const aakarPreset = require("@aakar/design-tokens/tailwind.preset.cjs");

const config: Config = {
  presets: [aakarPreset],
  content: ["./src/**/*.{ts,tsx,mdx}"],
  theme: {
    extend: {
      keyframes: {
        "ak-draw": {
          "0%": { strokeDashoffset: "1" },
          "60%": { strokeDashoffset: "0" },
          "100%": { strokeDashoffset: "0" },
        },
        "ak-fade-in": {
          from: { opacity: "0", transform: "translateY(4px)" },
          to: { opacity: "1", transform: "translateY(0)" },
        },
        "ak-pulse-soft": {
          "0%, 100%": { opacity: "0.55" },
          "50%": { opacity: "1" },
        },
      },
      animation: {
        "fade-in": "ak-fade-in var(--ak-motion-slow, 420ms) var(--ak-ease) both",
        "pulse-soft": "ak-pulse-soft 2.4s ease-in-out infinite",
      },
    },
  },
  plugins: [],
};

export default config;
