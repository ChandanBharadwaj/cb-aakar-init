// Tailwind preset built from tokens.json. Usage: presets: [require("@aakar/design-tokens/tailwind.preset.cjs")]
const tokens = require("./tokens.json");

module.exports = {
  theme: {
    extend: {
      colors: {
        cream: tokens.color.cream,
        paper: tokens.color.paper,
        sand: tokens.color.sand,
        line: tokens.color.line,
        ink: { DEFAULT: tokens.color.ink, muted: tokens.color.ink_muted },
        indigo: { DEFAULT: tokens.color.indigo, deep: tokens.color.indigo_deep, surface: tokens.color.indigo_surface },
        terracotta: { DEFAULT: tokens.color.terracotta, deep: tokens.color.terracotta_deep },
        marigold: tokens.color.marigold,
        sage: tokens.color.sage,
        success: tokens.color.success,
        warning: tokens.color.warning,
        danger: tokens.color.danger,
        surface: {
          bg: "var(--ak-bg)",
          card: "var(--ak-card)",
          border: "var(--ak-border)",
          text: "var(--ak-text)",
          muted: "var(--ak-text-muted)",
          accent: "var(--ak-accent)",
        },
      },
      fontFamily: {
        display: ["'Cormorant Garamond'", "Georgia", "serif"],
        ui: ["Manrope", "system-ui", "sans-serif"],
      },
      borderRadius: {
        control: tokens.radius.control,
        card: tokens.radius.card,
        pill: tokens.radius.pill,
      },
      boxShadow: {
        card: tokens.shadow.card,
        float: tokens.shadow.float,
        glow: tokens.shadow.stage_glow,
      },
      transitionTimingFunction: { ak: tokens.motion.ease },
      transitionDuration: { fast: "120ms", base: "220ms", slow: "420ms" },
      backgroundImage: { jaali: "var(--ak-jaali)" },
      keyframes: {
        "ak-spin": { to: { transform: "rotate(360deg)" } },
      },
      animation: {
        mandala: `ak-spin ${tokens.motion.mandala_spin} linear infinite`,
      },
    },
  },
};
