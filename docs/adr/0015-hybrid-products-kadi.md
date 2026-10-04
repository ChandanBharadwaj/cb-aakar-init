# ADR-0015: Hybrid products: bought-in bases with a standard Kadi interface

- Status: Proposed
- Date: 2026-10-04
- Deciders: Chandan Bharadwaj (product owner)

## Context
Printing a large everyday object whole is slow and expensive for the Indian market: a 300 g headphone stand is about nine printer-hours at the placeholder rates, and the outcome-categories research showed that labour and postage, not plastic, set the floor on what we sell. Customers still want everyday utility (a headphone stand, key hooks, a lamp, a dashboard mount, a bottle, a soap box, a planter) with their own mark on it. ADR-0009 keeps geometry parametric and ADR-0014 allows exactly one raw family; neither says how a printed piece may depend on an object we did not print. Today bought-in parts exist only as small `hardware_items` packed with a print (split rings, magnets, a USB LED puck), cut for with hard-coded clearances per template, priced as one aggregated line, and never shown as geometry. Roshni already works this way in all but name: the lithophane plate carries a pocket for the puck.

## Decision
A third family kind, `hybrid` (brand name **Jod**), pairs a **bought-in base item** with a **printed Chhaap** that attaches through one of a small set of standard connectors, the **Kadi**.

- **Base items are first-class catalogue data** (`base_items`): a product with its own cost or retail price, stock, supplier, weight, dimensions, a 3D preview model, a finish, a mount frame, and a declared **interface** (which Kadi it presents, its nominal size, and a tolerance class for how much its dimensions vary). Staff manage them in the portal; a family of kind `hybrid` lists the bases it fits (`family_bases`), and the API refuses an incompatible pair.
- **The Kadi is a geometry-service module, not a template detail.** Four standards: a ribbed **socket** (press fit into a pin, tube or bought-in dowel), a 60° **dovetail slide**, a **magnetic register** (magnets plus locating cones against a steel face or counter-plate), and a **thread** on standard male threads only (¼-20, M10×1, E27, E14). A **rim clip** is the compliant variant for bases whose size varies and is PETG-only. Every Kadi puts the female feature in a flange on the Chhaap so the piece prints flat with no supports; the male side is native to the base or a stocked adapter.
- **Fit is data, calibrated by coupons.** A per-material compensation table (hole and outer-wall compensation, elephant foot, shrinkage, strain and creep limits) is applied at modelling time; a new `connector_fit` printability check verifies the joint; a `(base interface × material)` pair is sold only after a passing fit test recorded in the portal. Customers never see a slop slider.
- **The price is honest by line.** The breakdown gains a `base` line (the base at its retail price or cost plus a base markup, never lifted by the printed minimum or the margin) and an `assembly` line, keeps `hardware` for adapters, and shipping moves to weight tiers because a base weighs more than a mailer allows. Stock is reserved when an order is paid and consumed when it is packed; an `assembling` stage sits between finishing and QC.
- **The storefront shows two things.** The base renders muted and locked, the Chhaap in the chosen finish, with a lift-off view of the joint; hybrids are promoted inside existing Duniyas and in a "whole or plug-in" compare rather than given a world of their own. The fully-printed path stays available for every paired family.
- **Everything is additive.** `spec_version` stays `1.0`; new fields are optional; `Context.NONE` keeps every current price identical; a family of kind `carrier`, `object` or `raw` is untouched.

## Consequences
- Everyday items that were uneconomic to print whole become orderable at a fraction of the printer-hours, and one base can take many Chhaaps, which is the merchandising story.
- The studio takes on inventory (stock, suppliers, lead times, reorder levels) and an assembly step with its own sheet and QC photo; GST per line needs the ADR-0008 chartered-accountant review because a base and a custom-printed part fall under different HSN codes.
- Fit is a real risk on the bases we do not control (customer-owned bottles and planters): the rim clip waits for a PETG finish, dashboard pieces wait for a heat-safe material, and every new base needs physical coupons before it is switched on.
- The viewer learns to compose two models at true relative scale, which is also the groundwork for later multi-part orders (drainage trays, lamp modules) that PLAN.md §7.3 anticipated.
- Any further connector standard or a second kind of bought-in item (for example a consumable) is a deliberate change: the `kind` CHECK constraint and the Kadi enum make it one.
