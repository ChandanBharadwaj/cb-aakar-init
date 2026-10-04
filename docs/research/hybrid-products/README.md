# Hybrid products research (4 Oct 2026)

Question: fully printing large everyday items is too slow and expensive for the Indian market. How does Aakar sell a **mass-produced or fixed base item** (steel headphone stand, key-hook plate, LED lamp base, dashboard mount, steel bottle, acrylic soap box, terracotta planter) plus a small **3D-printed Chhaap that plugs into it**, promote these hybrids heavily, and keep the fully-printed path alive?

| File | What it is |
|---|---|
| `report.md` | Research: the four standard connectors (Kadi: socket, dovetail, magnetic register, thread, plus the PETG-only rim clip), the per-use-case mapping, FDM/SLA tolerance and compensation guidelines, the `connector_fit` check and fit-coupon loop; UX for separating the fixed base from the editable Chhaap in the viewer, three discovery patterns, Duniya placement |
| `implementation-plan.md` | Phased, resumable plan for this repo: contracts, `V15` migration (`base_items`, `family_bases`, `fit_tests`, stock), catalog module, pricing engine (base and assembly lines, weight-tiered shipping), geometry `connectors/` package, portal pages, roadmap (PoC → core platform → UX rollout → scale), verification, open decisions, resume state |

Naming follows the outcome-categories convention (code ids English snake_case, brand words portal data): kind `hybrid` → **Jod**, the connector → **Kadi**, the bought-in product → **Buniyaad** in copy and `base_items` in code; the printed part stays **Chhaap**. Decision: [ADR-0015](../../adr/0015-hybrid-products-kadi.md).

Method and caveat: a codebase exploration (catalog, pricing, inspect, geometry, storefront) plus engineering reasoning from the sourced standards in `../outcome-categories/notes/engineering_standards.md` (hole undersize, magnet pocket clearance, heat-set inserts, lamp collars, PLA heat limits). Connector dimensions, compensation values and the worked price example are **proposals to be settled by the Phase 1 fit coupons and real supplier quotes**, not measured facts.
