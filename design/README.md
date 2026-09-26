# Aakar — design concepts

AI-assisted 3D printing studio. Design boards exported from the design tool (formerly filed under the working name "KalaForge"; the product is **Aakar**).

## Contents

- `standalone/` — self-contained HTML files, open directly in any browser (no server needed)
  - `aakar-concepts.html` — the customer journey: Home, Shop, Create, AR, Remix, Checkout, Order tracking, WhatsApp time-lapse, Unboxing (desktop + mobile)
  - `aakar-logo.html` — Bloom mark: primary, on indigo, embossed, lockups, app icons
  - `aakar-logo-v2.html` — one-piece Bloom variants (seated / rising)
  - `aakar-loader.html` — animated SVG loading mark (blue neon draws the Bloom)
- `source/` — editable source files (`*.dc.html` + runtime). Serve the folder over HTTP to open, e.g. `npx serve design/source`

## Identity

- Name: **Aakar** (आकार, form / shape). Tagline: *Things you imagine, made real.*
- Palette: cream `#F5F0E6` · paper `#FBF8F1` · sand `#ECE3D2` · line `#E3DACA` · ink `#2A2F3A` · indigo `#34426B` · terracotta `#B56E52` · marigold `#D8AE5B` · sage `#7E9A7B`
- Type: Cormorant Garamond (display) · Manrope (UI)
- Mark: Bloom — three outlined jaali petals, one solid terracotta petal

The machine-readable version of these tokens lives in [`packages/design-tokens`](../packages/design-tokens).
