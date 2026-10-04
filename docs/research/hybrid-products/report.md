# Hybrid products ("Jod"): a bought-in base + a 3D-printed pluggable Chhaap

Research report, 4 Oct 2026. Companion to [`implementation-plan.md`](implementation-plan.md), which holds the data model, pricing engine, roadmap and open decisions this report refers to (sections 3 and 4, "open decision n").

## Summary

Printing a large everyday object whole is the wrong economics for India: a 300 g headphone stand is about nine printer-hours, and labour plus postage already set the floor on small prints (`docs/research/outcome-categories/report.md`). The hybrid shape keeps the personal part printed and buys the bulk: a mass-produced **base item** (steel headphone stand, key-hook plate, LED lamp base, dashboard mount, steel bottle, acrylic soap box, terracotta planter) plus a small printed **Chhaap** that plugs into it through a standard connector, the **Kadi**. Four Kadi standards cover the brief: a ribbed **socket** for post-topped bases, a 60° **dovetail slide** for flat housings, a **magnetic register** with locating cones for steel faces, and a printed **thread** for the standard male threads that already exist on lamp bases and camera-style mounts, with a PETG-only **rim clip** as the compliant variant for bases whose dimensions vary (terracotta rims, bottle caps). The rule that makes every Kadi printable is that the Chhaap is always the socket side and always prints flat on a parametric flange; the male feature is native to the base or a cheap bought-in adapter. Fit is handled by a per-material compensation table applied at modelling time, a new `connector_fit` printability check, and a physical fit-coupon loop recorded per `(base interface × material)`. In the storefront the base renders muted and locked and the Chhaap in the chosen finish, with a lift-off view that shows the joint; hybrids are promoted inside existing Duniyas rather than given a world of their own. Roshni (the lithophane night light on its USB LED puck) is already a hybrid in all but name, so the platform pieces this needs (hardware BOM, pockets with clearance, hardware price lines, data-driven families) exist and are extended, not replaced.

---

## 1. Mechanical & technical research — the "plug"

### 1.1 Design rule that drives everything: the Chhaap is always the *socket side* and always prints flat on its flange

A decorative Chhaap (a head, an idol, a monogram) has no reliable flat face, and a male peg under it would print on supports or need the piece printed upside down. So every Kadi puts the **female feature in a parametric flange on the Chhaap** (the flange sits on the bed, body up, no supports, the same way `plinth_round` prints), and the **male feature on the base**: either native to the sourced base (a tube top, a molded rail, a ¼-20 stud, an E27 socket) or a cheap **bought-in adapter** (nylon dowel, steel disc, VHB-backed rail) that we stock as `hardware_items`. The Chhaap never carries thin elastic fingers in PLA (see 1.4).

### 1.2 The four Kadi standards

| Kadi | Mechanism | Chhaap-side feature (printed) | Base-side feature (sourced or adapter) | Nominal sizes | Where it serves (from the brief) |
|---|---|---|---|---|---|
| **Kadi-S · Socket** (press / snap) | Round socket with **3 crush ribs** (0.25 mm interference each, 120° apart) that yield on first insertion and tolerate ±0.15 mm pin variation; optional **snap groove** (0.8 mm bead on the pin, 0.1 mm seated clearance) for pull-off resistance | Blind bore in a flange ≥ `d + 2 × 2.4 mm` wide, depth 12 / 15 / 20 mm, 0.5 mm entry chamfer, 0.4 mm elephant-foot chamfer at the bed edge | Native: hollow tube top (headphone stand), stem of a dashboard mount. Adapter: **nylon dowel** Ø 8 / 12 / 16 h9 × 30 mm (`dowel_nylon_12x30`), double-ended, one end in the base's tube with the same ribs or a drop of CA | Ø 8 (≤ 40 g Chhaap), **Ø 12** (≤ 150 g, the default), Ø 16 (≤ 400 g, lamps) | Headphone stand topper, planter trellis stake, lamp finial, any post-topped base |
| **Kadi-D · Dovetail slide** | 60° sliding dovetail with an end stop and a 0.4 mm detent ramp; self-aligning, resists rotation, zero elastic preload so PLA does not creep | Female channel in the Chhaap's back: rail width 12 mm at the floor, 15 mm at the undercut, 4 mm deep, +0.2 mm per flank, +0.15 mm depth, 1 mm entry chamfer, length ≥ 25 mm | Native: molded dovetail on a plastic housing. Adapter: **PETG rail** (printed, a stock hardware SKU) or aluminium dovetail extrusion bonded with **3M VHB 4910 pad** (`vhb_pad_25x40`) | one profile, lengths 25 / 40 / 60 mm | Nameplate that slides over a key-hook housing, soap-box lid rail, headphone-stand base plaque |
| **Kadi-M · Magnetic register** | Magnets hold, **two locating cones** carry shear and fix rotation; a steel counter-plate or a second magnet set on the base | Existing `10.2 × 3.2 mm` pockets (`fridge_magnet.py` idiom) ×2–4 plus two cones Ø 4 → 3 mm, 2 mm tall; pocket floor ≥ 1.2 mm skin | Native: the steel puck face of a magnetic dashboard mount, a mild-steel key-hook plate. Adapter: **steel disc Ø 40 × 0.8 mm with adhesive** (`steel_disc_40`) or a printed counter-plate with matching magnets (polarity marked by an embossed dot) | 2 magnets (≤ 60 g, vertical face), 4 magnets (≤ 150 g) | Dashboard idol / monogram, key-hook plaque on steel, lamp-base badge, swap-able fridge-side gallery |
| **Kadi-T · Thread** | Printed female thread on **standard male threads only** (we never match a bottle's proprietary thread) | Modelled with radial clearance 0.30 mm (FDM) / 0.15 mm (SLA), pitch ≥ 1.27 mm, ≥ 4 full turns, 45° thread-start chamfer; load-bearing or hot (car) variants take a **brass heat-set insert** instead of plastic thread | Native: **¼-20 UNC** stud (dashboard / camera mounts), **E27 40 mm** or **E14 28.5 mm** shade ring (lamp bases), **M10×1** lamp nipple | ¼-20, M10×1, E27, E14 | Lamp shade over an LED lamp base, dashboard mount with a ¼-20 ball head, lithophane on a lamp nipple |

**Compliant variant of Kadi-S for variable bases — Kadi-C · Rim clip.** Terracotta rims vary ±3 mm, steel bottle caps come in OD classes 30–50 mm, acrylic boxes ±0.5 mm. A C-clip with four 1.6 mm × 12 mm flex tabs and a 0.6 mm lip covers a range (e.g. cap OD 38–42) and is the only Kadi that holds by elastic preload, so it is **PETG-only** (1.4) and gated until a PETG finish exists (open decision 2). Classes: cap OD 30–34 / 35–39 / 40–44 / 45–50; edge thickness 1.5–3 (steel plate) / 12–18 (wood) / 6–12 (terracotta rim); box-lid lip with 0.5 mm clearance.

**Retention rule (all Kadi):** hold ≥ 3 × the Chhaap's own weight plus the use load (headphones 400 g on a topper), verified by the Phase 1 fit coupons and recorded per `(base interface × material)` in `fit_tests` (3.1).

### 1.3 Per-use-case mapping

| Brief example | Base item (sourced) | Base material | Kadi | Chhaap family (kind `hybrid`) | Content slot | Material rule | Tier |
|---|---|---|---|---|---|---|---|
| Headphone stand + head / emblem | Aluminium or steel pillar stand with a hollow 12 mm tube top (or any stand + `dowel_nylon_12x30`) | metal / wood | S-12 | `headphone_topper` — a **saddle** topper (Ø 70 × 40 mm crown that the headband rests on) with a front **volume anchor** for Roop/hero and a front **surface anchor** for Naam | hero_mesh (constrained: nothing may rise above the saddle crown), emboss_text, motif | any PLA | launch (PoC) |
| Key hooks + family nameplate / motif | Steel or wood plate with 4–5 pre-mounted hooks | steel / wood | D (rail) or M (steel plate) | `hook_plaque` — a plaque with Naam in 7 scripts and a Buti border | emboss_text, motif, relief_image | any PLA | next |
| LED light base + lithophane / geometric shade | E27 LED table-lamp base with cord, switch, 3–5 W LED bulb (`led_lamp_base_e27`) | metal / wood | T (E27 collar) | `lamp_shade_e27` — jaali / lotus shade, and `lithophane` gains a `stand: e27_collar` option | motif, relief_image (lithophane) | PLA allowed (shade stays under 45 °C with a ≤ 5 W LED; the bulb limit is a base `compliance_notes` rule, **not** `heat_safe_only`, which would block all six PLA finishes today) | next |
| Dashboard mount + idol / logo / monogram | Adhesive magnetic dashboard mount with steel puck (`dash_mount_magnetic`) | plastic / steel | M (steel disc on the Chhaap) or T (¼-20) | `dash_idol` — a plinth with a volume anchor and a monogram face | hero_mesh, emboss_text, motif | **heat_safe_only** (70 °C cabin; all six finishes are PLA → blocked until ASA/PETG) | later |
| Steel water bottle + cap cover | Any 500–750 ml steel bottle; the customer's own, by cap class | steel / plastic cap | C (cap clip) | `bottle_cap_cover` — a character or Naam cover | emboss_text, motif, relief_image | PETG only; no magnets, no detachable parts (kids) | next (needs PETG) |
| Acrylic soap box / sponge holder + draining lid | Standard acrylic box, two sizes (`soap_box_acrylic_120x80`) | acrylic | C (lid lip) | `drain_lid` — a slotted lid with a motif pattern | motif, emboss_text | PETG preferred (wet) | next |
| Planter + trellis / decorative rim | Terracotta or plastic pot, rim class | terracotta / plastic | C (rim clip) + S-8 (trellis stake into a clip boss) | `planter_rim` — a clip-on rim band with a motif; `trellis_clip` later | motif, emboss_text | PETG | later |

Katha's trademark guardrail applies unchanged: "Groot" and "Batman" are protected terms (`V13__content_terms.sql`); the customer's own hero or emblem is the content.

### 1.4 Tolerance guidelines — how the automated check adjusts the Chhaap

**Where compensation lives.** A per-material table, seeded in `materials.json` and stored on the `materials` table (new columns, migration `V15`; the existing `MaterialsSeedTest` is updated to the new columns, not extended), applied by the connector builder in the geometry service **at modelling time** (the STL/3MF the studio prints already carries the compensation; nothing depends on slicer hole-compensation settings):

| Field (per material) | PLA (six current finishes) | PETG (proposed) | Resin / SLA (future partner) | Why |
|---|---|---|---|---|
| `xy_hole_comp_mm` | +0.20 (today's `HOLE_ALLOWANCE_MM`) | +0.25 | +0.05 | FDM holes print small (segment chords + extrusion squeeze) |
| `xy_outer_comp_mm` | −0.10 | −0.15 | 0.00 | outer walls print fat |
| `elephant_foot_mm` | 0.30 | 0.35 | 0.00 | first-layer squish; the flange gets a 0.4 mm bed-edge chamfer and the socket mouth is relieved 0.3 mm for the first 0.6 mm |
| `shrink_pct` | 0.3 | 0.6 | 0.3 | linear shrinkage, applied to connector radii only (not to the decorative body) |
| `max_strain_pct` | 1.0 | 3.0 | 2.0 (tough resin) | elastic features: crush ribs are sized so the rib, not the wall, yields; snap beads/clips are refused above this |
| `creep_class` | `high` | `medium` | `low` | `high` forbids any Kadi held by constant elastic preload (rim clip, snap bead) |
| `min_wall_mm` for connector walls | 1.6 | 1.6 | 1.0 | over the family default 1.2 because the socket wall carries the load |

**Fit classes the connector module exposes** (one enum on the template, resolved per material):

| Fit | Target as-printed condition | Modelled as (FDM PLA) |
|---|---|---|
| `slide` (Kadi-D, Kadi-T, lid lips) | 0.15–0.30 mm clearance per side | nominal + `xy_hole_comp` + 0.20 |
| `press_ribbed` (Kadi-S default) | socket wall at nominal + 0.15 clearance; three ribs at −0.25 interference | ribs are the only interference feature, so a slightly big pin shaves ribs instead of cracking the wall |
| `snap` (Kadi-S bead, Kadi-C) | 0.6–1.0 mm undercut, 0.1 mm seated clearance | refused when `creep_class = high` |
| `pocket` (Kadi-M magnets, steel disc) | +0.2 diameter, depth = part thickness + 0.2 | existing fridge-magnet rule |

**Base-side variance drives the Kadi choice** (encoded as `base_items.interface.tolerance_class`): `machined` (metal tube, ±0.1) and `molded` (±0.15) take `press_ribbed` / `slide`; `wood` (±0.5) takes `press_ribbed` with 5 ribs or Kadi-M; `ceramic` (±3) and `cap_class` (±2) take Kadi-C only. The API refuses a `(family, base)` pair whose connector fit class is not allowed for the base's tolerance class (422 `incompatible_base`).

**New printability check `connector_fit`** (inspect service, `services/inspect/aakar_inspect/checks.py`, report v1 gains an optional `checks.connector_fit`; the stability card shows it as "Fits its base · 12 mm Kadi"). Inspect never receives the material today, so the `/v1/inspect` request (`api.py` `InspectRequest`/`ConstraintsIn` L26) and the geometry-side `Inspector.inspect(mesh, constraints, slicing, assets, storage)` protocol (`aakar_geometry/inspection.py:18`, both `InProcessInspector` and `HttpInspector`) gain an optional `connector` block: the connector descriptor the template reports (axis, nominal, fit class, as-modelled radii, rib geometry), the material compensation row, and the base interface. It verifies: (a) the as-modelled minus compensated dimensions land inside the fit window for the base's measured range; (b) the thinnest wall around the socket ≥ 1.6 mm using the existing `wall_thickness_samples` ray cast restricted to the connector's bounding cylinder; (c) rib/finger strain ≤ `max_strain_pct`; (d) the connector axis is within 5° of the print Z (prints without supports); (e) for Kadi-S toppers, the saddle keep-clear volume is empty (no hero geometry above the crown). `fail` blocks purchase like any other failed check; `warn` for a base with no recorded fit test yet.

**Calibration loop (the part no formula replaces).** Every `(base interface × material)` pair gets a **fit coupon**: a 25 mm flange with the Kadi feature only, printed in each finish, measured with a digital caliper (socket Ø at mouth and floor, rib crest Ø), assembled 10 times, pulled off with a spring scale. Results go into a `fit_tests` table (3.1) through the portal; a pair without a passing fit test keeps the base `available=false` for that material, the same gate that keeps `photo_frame` and `keycap` off today. The coupon is a template (`kadi_coupon@1`, hidden family) so it is one `make slice`-style command.

---

---

## 2. UX / UI and creative discovery

### 2.1 Visual separation in the 3D viewer: fixed base vs your Chhaap

Today `Model.tsx` fits one GLB to 1 unit. A hybrid version renders **two objects at one true scale**:

- `base_items.preview_glb_url` — a Draco GLB authored once per base (≤ 1.5 MB, Y-up, mm→m, origin at the base's resting point), plus `base_items.finish_pbr` (brushed steel, oiled wood, matte black plastic) and `mount_frame` `{origin_mm, up, forward}` in the base's own coordinates.
- `latest_version.assets.glb.url` — the Chhaap from the geometry service, whose connector descriptor gives the Chhaap's own mount frame (socket axis and seat plane).

New `components/viewer/HybridModel.tsx` composes `<Model>` twice under one `<group>`: the base at identity, the Chhaap transformed so its seat frame coincides with the base's mount frame, and `fit` computed from the **assembly** bounding box (so a 60 mm head on a 260 mm stand looks like a 60 mm head). `DesignViewer` gets an optional `base?: {glbUrl, pbr, mountFrame}` prop; nothing changes when it is absent. The viewer has no pointer handlers today, so `HybridModel` adds the first `onPointerOver/onClick` on the base group for the tooltip; the Kadi ghost reuses the `PlaceholderForm` wireframe material (`wireframe, transparent, opacity 0.35`); the "Jod · plug-in" ribbon reuses `.ak-ribbon` (`globals.css` L272-285), and `KIND_RANK` in `lib/families.ts:98` ranks `hybrid` before `carrier` so the picker promotes it; `FamilyPicker.tsx:62` and `ContentComposer.tsx:134` get a "Jod · base + your Chhaap" label beside the existing "Object" one.

Three visual cues, all reduced-motion safe:

1. **Fixed treatment on the base**: its own realistic PBR but **chroma −30 % and a 2 px cream hairline** (reuse the ink-outline shader in `materials.ts` with the paper colour and `thicknessPx 1.5`); a small padlock chip "Ready-made · steel stand" floats at its mount point (`drei <Html>`). The Chhaap takes the chosen finish at full chroma, with the marigold anchor dots. On hover or slider drag the base dims to 55 % (the existing `dimmed` filter) so the eye lands on the Chhaap.
2. **Lift-off ("Kadi view") toggle**: a segmented control Assembled · Apart. "Apart" slides the Chhaap 25 mm along the mount axis over 400 ms and draws a dotted guide line and a translucent cyan pin/rail ghost of the Kadi, so the customer sees *how* it attaches. The Checkout stability card gets a row "Fits its base · 12 mm Kadi" from `connector_fit`.
3. **Two-line legend under the stage**: "Base · Steel headphone stand (ready-made, not editable)" with a lock, "Your Chhaap · Terracotta Silk (printed for you)" with the finish swatch. Tapping the base line opens a sheet with the base's photos, dimensions, material and "why we don't print this part" (time, price, strength) — the honesty beat.

### 2.2 Three UX patterns that surface hybrids

1. **"Swap the top" base page.** A base's page (`/shop/base/[sku]`) keeps the base still and runs a horizontal carousel of Chhaap families and curated finished pieces that fit it; swiping live-swaps the Chhaap in the viewer with a 350 ms snap-on (drop along the mount axis, 6 px overshoot, a soft click sound off by default, haptic on mobile). The headline is the economics: "One stand, many tops · each top from ₹349 · ships in 2 days". Entry points: Home hero "Jod of the week", Shop shelf cards with a "Jod · plug-in" ribbon.
2. **"Whole or plug-in?" compare in the studio.** For families that exist in both shapes (headphone stand: fully printed `headphone_stand` vs `headphone_topper` on a steel base; lamp: `table_lamp` vs `lamp_shade_e27`), the studio shows a two-column compare card: price, print hours, delivery day, weight, "what you edit". Switching re-prices live through the existing `/api/versions/{v}/params` flow and sets `base_sku`. Default to the hybrid; the fully-printed column stays one tap away (the brief's "keep the fully printed option alive").
3. **"Fits what you already own" guided fit.** For Kadi-C families (bottle cap cover, planter rim) the composer asks for one measurement with a plain-language helper (a printable paper gauge PDF for cap OD classes; "wrap a thread round the cap and measure it") and picks the class; the viewer shows a generic bottle of that class. This turns the customer's existing object into the base at zero inventory cost and is the natural Masti (kids) entry.

Also worth a line each: a cart cross-sell "Same look as a Jod: ₹400 less, 2 days sooner" when a fully-printed heavy item is in the cart; a `/k/{code}` share page that shows base and Chhaap and offers "Print another top for this base" (reprint-lite, no base).

### 2.3 Duniya placement

A Duniya is a mood, not a manufacturing method, so there is **no "Hybrid" world**. Hybrid families join worlds by data (`experiences.avatars[]`) and are promoted inside them:

- `experience.v1.json` gains `promote: {kinds: ["hybrid"], ribbon: "Faster · lighter on the pocket"}`; the Duniya page sorts promoted kinds first within the avatar strip and renders the ribbon; the Shop "Shelves" persona gets filter chips "Jod · plug-in" and "Poora print · fully printed".
- Seeds: **Adda** adds `headphone_topper`, `hook_plaque`; **Utsav** adds `lamp_shade_e27`; **Yaadein** adds `lamp_shade_e27` (lithophane on the E27 collar); **Masti** adds `bottle_cap_cover`. Two new worlds whose everyday-utility content is mostly hybrid: **Ghar** (home utility: key hooks, soap/sponge lid, planter rim; environment `kitchen_marble`/`balcony_daylight`) and **Safar** (on the road: dashboard idol, bottle cover; environment `dashboard`). Slugs are URLs, so names are decided before the seed (open decision 1).
- Base items are *variants inside a family*, not Shop items: the family page offers "Choose your base" (steel / wood) from `family_bases`; curated finished pieces (`catalog_items`) may point at a `(family, base, template)` triple.

---
