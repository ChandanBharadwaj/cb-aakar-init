# Pricing & Unit Economics of Personalized 3D-Printed Consumer Products / 3D Print-on-Demand

> Intended destination (blocked by plan mode, which allows edits only to this plan file):
> `/tmp/claude-0/-home-user-cb-aakar-init/14a5b26c-ddc0-5b33-bbcb-65a744169d2f/scratchpad/research_notes/Custom 3D model product categories/pricing_economics.md`
>
> Method note for the report writer: the session's WebSearch budget was exhausted mid-task and the egress proxy blocked full-page fetches of essentially every commercial/market-research host (Bambu, Etsy, Printful, IMARC, Technavio, CBP, USPS resellers, Indian gifting sites, Meshy/Tripo/Rodin). Findings below therefore come from search-result summaries of the linked pages (dated Sept 2026 unless the page states otherwise) plus public GitHub documents. Treat exact figures as "as reported in search snippets"; where a number could not be corroborated it is flagged. All INR/USD conversions are avoided; where unavoidable they are marked as unsourced. Today's date: 2026-09-27.

## KQ1. Cost drivers 2025–2026 (materials, machine time, electricity, failure, labor, packaging, shipping, duties)

### Takeaway
For a small FDM part the material + machine cost is trivial ($0.10–$4 / ₹10–₹200 for anything under 150 g); the cost floor is dominated by fixed per-order costs — 10–15 minutes of handling labor and $5.90–$8.85 (US) or ~₹40–55 (India) of last-mile shipping — so single-item orders under ~$15 / ₹250 are structurally thin and bundling or free-shipping thresholds are the main margin lever. Cross-border India→US single parcels became uneconomic for sub-$25 items after the US de minimis exemption ended on 29 Aug 2025 (postage ≥₹865 + duties of 50%→18%→~10% over 2025–26).

### Cited Findings
**Filament (USD)**
- Bambu Lab PLA Basic 1.75 mm 1 kg spool listed at $22.99 (US retailer, Sept 2026) — [3D Universe](https://shop3duniverse.com/products/bambu-lab-pla-basic-1-75mm-1kg); Bambu's own US store product page exists but price not captured — [Bambu Lab US Store](https://us.store.bambulab.com/products/pla-basic-filament)
- Bambu Lab TPU 95A HF 1 kg listed at $33.50 (Black) by European retailer 3DJake (Sept 2026 snippet; currency shown as $) — [3DJake](https://www.3djake.com/bambu-lab/tpu-95a-hf-black); a US listing (WestCoast Products) showing $278 (from $400) is almost certainly a multi-spool/case listing or error and should not be used — [WestCoast Products](https://wcproducts.com/products/wcp-1583)
- Bambu PETG HF price not captured (gap).

**Filament (INR, India)**
- WOL3D 1 kg 2025 PLA PRO+ (Made in India) currently ₹849, price history low ₹769 / high ₹1,849, MRP ₹1,599 — [pricehistory.app](https://pricehistory.app/p/wol3d-1kg-2025-pla-pro-3d-printing-qD1GDvyp); an older WOL3D PLA Flipkart listing shows ₹2,000 (stale/MRP-type listing) — [Flipkart](https://www.flipkart.com/wol3d-pla-printer-filament/p/itma119b91606101); WOL3D manufacturer catalog — [wol3d.com](https://wol3d.com/product-category/filaments/3dfilamentmanufacturer/)
- eSUN PLA+ and PLA Basic 1 kg spools are sold on Robu.in in many colours, but prices were not exposed in search results (gap) — [Robu.in eSUN PLA+](https://robu.in/product/esun-pla-1-75mm-3d-printing-filament-1kg-orange/)
- Indian PETG/TPU per-kg prices: not captured (gap).

**Resin & powder-bed (service) costs**
- Standard grey/white hobby resin $25–40/L; water-washable $25–40/L; tough/engineering $50–80/L; castable $60–120/L; dental $100–300/L; overall range $20–200/L — [ResinCalc guide (2026)](https://resincalc.com/guides/how-much-does-resin-3d-printing-cost); [3DSourced materials cost guide](https://www.3dsourced.com/guides/3d-printing-materials-cost/); [3DPrintBounty](https://3dprintbounty.com/blog/resin-printing-cost)
- Craftcloud (aggregator) review: a palm-size SLS/MJF nylon part typically costs $35–120; Craftcloud has no minimum order — [3DPrintBounty Craftcloud review](https://3dprintbounty.com/blog/craftcloud-review); Sculpteo minimum order €/$10 (2021 post) — [Sculpteo](https://www.sculpteo.com/blog/2021/04/10/economy-pricing-for-pa12-mjf-and-new-resin-materials-discover-sculpteos-april-update/)
- JLC3DP advertises custom 3D-printed parts (SLA/MJF/SLM/FDM/SLS) "from $0.30" with instant quotes — [JLC3DP](https://jlc3dp.com/)
- One blog claims MJF $15.57–41.04 per cm³ and SLS $7.79–20.53 per cm³, with SLS bead-blasting adding $5–20/part — [Hi3DP](https://hi3dp.com/blog/3d-printing-cost-comparison). **Suspect**: these per-cm³ figures are an order of magnitude above what a $35–120 palm-size part implies (a 50 cm³ part would be $780–2,000); do not use them as a cost basis.

**Printer prices (for depreciation)**
- Bambu Lab A1 $459 MSRP; P1S $699 MSRP; X1 Carbon $1,199 (Q1 2026 list) — [StackSheriff Bambu pricing 2026](https://stacksheriff.com/3d-printing/bambu-lab-pricing/); [OriginalPricing](https://originalpricing.com/bambu-lab-printer-prices/)
- P1S sale price $399 (record low, "new year" 2026) — [Tom's Hardware](https://www.tomshardware.com/3d-printing/grab-this-usd399-bambu-lab-p1s-3d-printer-back-down-to-a-record-low-price-for-the-new-year-save-usd300-on-high-speed-enclosed-printer-for-beginners-and-enthusiasts-alike); A1 $339 / A1 Combo $479 / P1P $399 deal — [Slickdeals](https://slickdeals.net/f/18393619-bambu-lab-3d-printers-a1-339-a1-combo-479-p1p-399-more-shipping)

**Electricity / power draw**
- Bambu wiki rated supply power: X1C/P1P/P1S 1100 W at 220 V (350 W at 110 V); A1 1300 W at 220 V (350 W at 110 V) — [Bambu Lab Wiki](https://wiki.bambulab.com/en/general/power-consumption)
- Measured: X1C 103–135 W while printing, up to ~400 W in the first 5 minutes of heat-up — [Bambu forum: power consumption data](https://forum.bambulab.com/t/power-consumption-data/4180); P1S ~850 W for the first 30–60 s then ~30–70 W, ~100 W average — [Bambu forum: P1S power](https://forum.bambulab.com/t/p1s-power-consumption/173002); A1 ~90 W average — [Bambu forum: A1 power](https://forum.bambulab.com/t/power-consumption-for-a1-printer/92095); calculators — [PEA3D](https://pea3d.com/en/bambu-lab-electricity-cost-calculator-x1c-p1s-a1-energy-analysis/), [Call3D](https://www.call-3d.com/blogs/upgrades/bambu-lab-power-consumption-review)
- Electricity tariff (US $/kWh, India ₹/kWh): not captured (gap; assumptions flagged in Inferences).

**Machine-hour cost & failure rate**
- LayerMath print-farm guide (2026): per-printer hourly cost = depreciation (printer cost ÷ lifespan hours) + maintenance buffer (annual maintenance ÷ annual print hours) + electricity — [LayerMath print farm](https://layermath.com/blog/how-to-run-a-3d-print-farm); companion hourly-rate article — [LayerMath hourly rate](https://layermath.com/blog/3d-printing-hourly-rate)
- GrandpaCAD calculator defaults "a common rule of thumb: 10× on materials, $3 per print hour, and $100/hour for design work" — [GrandpaCAD](https://grandpacad.com/en/tools/3d-printing-business-calculator); 3DPCC converts printer price/lifespan/maintenance/power into an hourly machine cost — [3DPCC](https://3dprintingcostcalculator.com/); PrintPal adds a failure-rate markup — [PrintPal](https://printpal.io/tools/3d-print-cost-calculator)
- "Around 10 percent is a common planning assumption for a small mixed fleet" failure rate — [SimplyPrint print-farm guide](https://simplyprint.io/articles/how-to-start-a-3d-print-farm); a research dataset of 5.6 M consumer print tasks >5 min found a 24% failure rate, while "professional 3D print farms have failure rates of at least 2%" — [arXiv 2210.07466](https://arxiv.org/pdf/2210.07466)

**Labor minutes**
- Part removal ≈ 2 min per print; each print needs ≥5 min labor (start, remove, filament change); each order ≈ 10 min to pack, ship and process — [3DQue: real cost benefits of automation](https://www.3dque.com/blog/the-real-cost-benefits-of-3d-print-farm-automation); [3DQue: hidden challenges](https://www.3dque.com/blog/5-hidden-challenges-holding-back-your-3d-print-farm)
- "Every job costs 30 minutes of labor between designing, prepping and shipping" (low-volume custom jobs; 113 h/month at steady state) — [GrandpaCAD](https://grandpacad.com/en/tools/3d-printing-business-calculator)

**Packaging (US)**
- Poly mailers $0.08–0.25/unit at volume (10×13 in: $0.08–0.18 at 500+); bubble mailers $0.20–0.65/unit (10×13 in: $0.20–0.40 in bulk) — [Plus Packaging (2026)](https://www.pluspackaging.com/blog/mailing-bags/poly-mailers-vs-bubble-mailers/); [CLIMB bubble mailer pricing](https://climbtheladder.com/how-much-do-bubble-mailers-cost-bulk-pricing-factors/)
- Indian corrugated box/mailer unit costs: not captured (gap).

**Shipping — US domestic (USPS Ground Advantage)**
- Jan 18, 2026 rate change: Ground Advantage average +7.8% — [ShippingEasy](https://support.shippingeasy.com/hc/en-us/articles/4407007211035-USPS-Rate-Changes-2026); [ShipStation](https://help.shipstation.com/hc/en-us/articles/360039042171-USPS-Rate-Changes-2026)
- July 12, 2026: Ground Advantage Commercial +11.8%; the 4 oz and 8 oz commercial tiers were eliminated, dimensions rounded up, DIM divisor cut 166→139 for >1 cu ft — [TransImpact](https://transimpact.com/blog/usps-rate-to-increase-ground-advantage-commercial-rates-by-11.8); [Pirate Ship July 2026 changes](https://support.pirateship.com/en/articles/15453569-july-2026-usps-rate-and-rule-changes)
- Retail Zone 1 (from July 12, 2026): $5.90 ≤4 oz; $7.30 ≤8 oz; $8.85 for 12 oz, 15.999 oz and 1 lb; $10.00 2 lb; $12.00 5 lb; $14.75 10 lb. Commercial Zone 1: $7.05 (1 lb), $7.40 (2 lb), $8.36 (5 lb), $11.22 (10 lb) — [idshipthat rate tables](https://idshipthat.app/shipping-rates/usps-ground-advantage/); [Seller Essentials](https://selleressentials.com/usps-ground-advantage-rate-increase-2026/)
- Holiday surcharge Oct 4, 2026–Jan 17, 2027: Ground Advantage commercial +$0.40 to +$7.70 per package by weight/zone — [Pirate Ship](https://support.pirateship.com/en/articles/15453569-july-2026-usps-rate-and-rule-changes); [ClickPost GA overview](https://www.clickpost.ai/en-us/blog/usps-ground-advantage)

**Shipping — India domestic**
- Shiprocket advertised floor ₹20–26 per 500 g depending on plan; actual landed cost typically 90–110% above advertised base once COD fees, GST, zone markups and volumetric penalties apply — [CheckThat Shiprocket pricing 2026](https://checkthat.ai/brands/shiprocket/pricing)
- Aggregator surface shipping ₹25–45 per 500 g is cheapest under ~3,000–5,000 orders/month; above that a direct Delhivery contract wins on busy lanes; volumetric example: a 500 g item in a 30×25×15 cm box bills as 2.25 kg (~450% of actual-weight rate) — [TheShizz courier comparison](https://theshizz.in/blog/courier-comparison-shiprocket-delhivery-bluedart); Delhivery rate calculators — [Delhivery business](https://www.delhivery.com/rate-calculator/business-shipment); [Shiprocket Delhivery charges guide 2026](https://www.shiprocket.in/blog/delhivery-courier-charges/)
- India Post domestic Speed Post/parcel rate tables: not captured (gap) — guides exist at [WareIQ](https://wareiq.com/resources/blogs/india-post-courier-charges/), [iThink Logistics](https://www.ithinklogistics.com/blog/speed-post-charges-latest-rates-pricing-guide-india/)

**Shipping — cross-border India→US and US→India**
- India Post EMS/Speed Post to USA: one guide reports ≈₹865 for up to 250 g plus ≈₹100 per additional 250 g — [ClickPost India Post charges 2026](https://www.clickpost.ai/blog/india-post-courier-charges); another reports ₹1,820 for the first 250 g + ₹150 per additional 250 g (₹2,270 for 1 kg before tax) — [WareIQ](https://wareiq.com/resources/blogs/india-post-courier-charges/). **Conflict**: the two guides differ ~2× on the base slab; verify on India Post's calculator.
- India Post international parcel to USA (Zone 5) from ₹1,695 for 1 kg; surface 10 kg ≈₹3,790 (45–60 days) vs air ≈₹11,175 (7–12 days) — [aCourierTracker](https://acouriertracker.com/india-post-parcel-rates-per-kg/); [Tirupati Courier Tracking](https://tirupaticouriertracking.com/india-post-parcel-rates-per-kg/)
- India Post services to the US were "temporarily suspended as of August 2025" — [WareIQ](https://wareiq.com/resources/blogs/india-post-courier-charges/); Shiprocket X offers an India Post international calculator — [Shiprocket X](https://www.shiprocket.in/x/india-post-international-courier-rate-calculator/)
- US de minimis: ended for China/HK on May 2, 2025 and for all countries on Aug 29, 2025; all shipments regardless of value now dutiable — [ShipBob](https://www.shipbob.com/blog/de-minimis-value/); [CBP fact sheet (Aug 18, 2025)](https://www.cbp.gov/sites/default/files/2025-08/factsheet_suspension_of_duty-free_de_minimis_treatment.pdf); [CBP EO 14324 article](https://www.help.cbp.gov/s/article/Article-1919)
- Postal shipments Aug 29, 2025–Feb 28, 2026 could pay either ad valorem duty or a flat $80–$200 per item (by tariff tier); Indian-origin goods faced an additional 25% tariff from late Aug 2025 for a combined ~50% — [WareIQ: end of de minimis](https://wareiq.com/resources/blogs/end-of-de-minimis-exemption-new-tariffs-india-shipments/); [Xportel](https://www.xportel.com/blog/us-de-minimis-export-impact-india-2025)
- US–India deal announced Feb 2, 2026 cut tariffs on most Indian goods from 50% to 18% (reciprocal 25%→18%, the additional 25% rescinded) — [White House fact sheet](https://www.whitehouse.gov/fact-sheets/2026/02/fact-sheet-the-united-states-and-india-announce-historic-trade-deal/); [Morgan Lewis](https://www.morganlewis.com/pubs/2026/02/us-india-trade-deal-cuts-tariffs-eases-tensions); [ClearTax](https://cleartax.in/s/us-tariff-on-india)
- Secondary trackers report India's rate then fell to 10% under Section 122 and, from July 24, 2026, to the 10% tier of a Section 301 tariff that replaced Section 122 — [TariffsTool](https://www.tariffstool.com/tariffs-from-india); [TariffCentral](https://www.tariffcentral.org/tariffs/india); [NewsOnAir (Feb 21, 2026): "temporary 10 per cent tariff"](https://www.newsonair.gov.in/white-house-says-india-to-face-temporary-10-per-cent-tariff-after-new-us-order). **Verify** before relying on 10%.
- US→India: import of goods as "gifts" through courier/post is prohibited except life-saving drugs and Rakhi; gifts may be imported only on payment of full duty — [Chennai Customs FAQ (Oct 2025)](https://chennaicustoms.gov.in/wp-content/uploads/2025/10/Faq.pdf); [Eximguru courier manual](https://www.eximguru.com/exim/indian-customs/customs-manual/import-and-export-through-courier.aspx); the historical ₹5,000 duty-free gift threshold — [Business Standard (2018)](https://www.business-standard.com/article/pf/no-need-to-pay-customs-duty-on-imported-gifts-valued-below-rs-5-000-118110100190_1.html); a July 2023 notice cut the personal-use exemption to ₹2,000 with ~41% total tax above it (10% BCD + 3% cess + 28% IGST as reported) — [Gulf News](https://gulfnews.com/going-out/society/8000-expats-hit-hard-by-indias-new-import-rules-1.2056311); DHL India import-duty guide — [DHL](https://www.dhl.com/discover/en-in/logistics-advice/import-export-advice/import-duty-in-india)

**Indian 3D-printing service (partner) prices**
- iamRapid: FDM PLA from ₹6/g (instant quote) — [iamRapid pricing](https://iamrapid.com/pricing/); [iamRapid cost guide 2026](https://iamrapid.com/knowledge-hub/3d-printing-cost-guide/)
- PLA ₹5–15/g; a 100 g PLA print typically ₹500–1,500 — [Paradise 3D](https://paradise-3d.com/cost-of-3d-printing-in-india/); ₹5–20/g of finished part by city/material/complexity — [Precious3D](https://precious3d.com/cost-of-3d-printing-in-india-complete-pricing-guide/); Chennai vendors ₹3–12/g — [Quora](https://www.quora.com/What-is-price-range-of-plastic-3D-printing-service-in-India-For-example-how-much-does-it-cost-to-print-a-6-inch-cube-15-cm-cube-with-60-fill-and-an-average-Layer-Thickness); Indian hobby-cost calculator — [Zbotic](https://zbotic.in/3d-printing-costs-india-calculate-material-and-electricity/)

### Inferences (derived cost floors — arithmetic shown; grams/hours are the researcher's estimates, not sourced)
Assumptions (flag: unsourced unless cited above): PLA $0.023/g (=$22.99/kg) US; ₹0.85/g (=₹849/kg) India. Failure allowance 1.10× on material+machine (10%). Machine cost US ≈ $0.20/h (A1 $459 ÷ 5,000 h ≈ $0.09 + electricity 0.1 kWh × assumed $0.17 ≈ $0.02 + maintenance ≈ $0.05–0.10; the 5,000 h life, $0.17/kWh and maintenance are unsourced assumptions); India ≈ ₹11/h (assumed ₹35,000 printer ÷ 5,000 h = ₹7 + 0.1 kWh × assumed ₹8 + ₹3 maintenance). Labor 15 min/order (5 min handling + 10 min pack/ship per 3DQue) at assumed $15/h US = $3.75; at assumed ₹150/h India = ₹37.5. Packaging US $0.15 (poly mailer) to $0.75 (bubble + rigid); India assumed ₹15–25. Shipping US: $5.90 (≤4 oz), $7.30 (≤8 oz), $8.85 (≤1 lb) retail Zone 1 (higher zones cost more; commercial ≥$7.05 after July 2026); India ≈ ₹45 landed prepaid per 500 g.
- Keychain 12 g / 0.6 h (US): material 12×0.023×1.1 = $0.30; machine 0.6×0.20 = $0.12; labor $3.75; packaging $0.15; shipping $5.90 → **≈$10.2 delivered (≈$4.3 ex-shipping)**. India: ₹11.2 + ₹6.6 + ₹37.5 + ₹15 + ₹45 → **≈₹115 delivered (≈₹70 ex-shipping)**.
- Three keychains in one order (US): 3×$0.42 + labor $3.75 + 2×2 min extra removal ≈$1.00 + packaging $0.15 + shipping $5.90 (still ≤4 oz) → ≈$12.1, i.e. **≈$4.0 per keychain vs $10.2 single** — bundling cuts landed unit cost ~60%.
- Lithophane 10×15 cm, 60 g / 5 h (US): $1.52 + $1.00 + $3.75 + $0.50 + $7.30 → **≈$14.1 delivered (≈$6.8 ex-ship)**. India: ₹56 + ₹55 + ₹37.5 + ₹25 + ₹45 → **≈₹219 (≈₹174 ex-ship)**.
- Lithophane lamp / night light 120 g / 8 h + LED base (assumed $5 / ₹250, unsourced): US $3.04 + $1.60 + $5.00 (extra assembly) + $5 + $1.00 + $8.85 → **≈$24.5 (≈$15.6 ex-ship)**; India ₹112 + ₹88 + ₹50 + ₹250 + ₹25 + ₹45 → **≈₹570 (≈₹525 ex-ship)**.
- Figurine/bust 120 g / 10 h FDM (US): $3.04 + $2.00 + $4.50 (support removal) + $0.75 + $8.85 → **≈$19.1 (≈$10.3 ex-ship)**; resin alternative material ≈100–150 mL × $0.03/mL = $3–4.5 plus wash/cure labor (unsourced).
- Planter 150 g / 7 h (US): $3.80 + $1.40 + $3.75 + $0.75 + $8.85 → **≈$18.5 (≈$9.7 ex-ship)**; volumetric weight risk if boxed.
- Phone stand 60 g / 3 h (US): $1.52 + $0.60 + $3.75 + $0.30 + $7.30 → **≈$13.5 (≈$6.2 ex-ship)**. Pen holder 90 g / 4.5 h ≈ $15.
- Photo frame 120 g / 6 h (US): $3.04 + $1.20 + $3.75 + $0.75 + $8.85 → **≈$17.6 (≈$8.8 ex-ship)**.
- Fridge magnet 15 g / 0.8 h + magnet (assumed $0.20): $0.38 + $0.16 + $0.20 + $3.75 + $0.15 + $5.90 → **≈$10.5 (≈$4.6 ex-ship)**.
- Pin/badge 5 g / 0.5 h + pin back (assumed $0.15): **≈$10.2 (≈$4.3 ex-ship)**.
- Pendant/earrings 4 g / 0.4 h + findings (assumed $0.30): **≈$10.3 (≈$4.4 ex-ship)**.
- Ornament 20 g / 1.2 h: **≈$10.7 (≈$4.8 ex-ship)**. Cake topper 20 g / 1.2 h: **≈$10.7 (≈$4.8)**. Cookie cutter 25 g PETG / 1.2 h: **≈$10.7 (≈$4.8)**. Coaster 35 g / 1.5 h: **≈$12.4 (≈$5.1)** single; a 4-set ≈ $17 delivered (≈$4.3 each). Nameplate 60 g / 3 h: **≈$13.5 (≈$6.2)**. Keycap 3 g / 0.5 h FDM: **≈$10.0 (≈$4.1)** (resin gives better surface; add wash/cure).
- Partner-fulfilled India at ₹6–15/g: keychain 12 g = ₹72–180 print cost; lithophane 60 g = ₹360–900; bust 120 g = ₹720–1,800 — i.e. 7–18× raw material cost, before shipping.
- Cross-border India→US single item: postage ≥₹865 (250 g EMS, if/when service is available) + 10–18% duty + carrier entry fees (unsourced) makes India-made fulfillment of sub-$25 US orders uneconomic; US orders need US-side printing (own or partner).
- Electricity is immaterial (<2% of cost floor at ~0.1 kWh/h); labor + shipping are 70–90% of a single small item's floor.

### Gaps
- Bambu PETG HF price, Indian PETG/TPU prices (Robu/3Idea/WOL3D), Prusament/Polymaker/eSUN spot prices: not captured.
- US and Indian electricity tariffs, printer lifespan hours, maintenance cost per hour, labor wage assumptions: no sources retrieved; values above are assumptions.
- Indian packaging unit costs; India Post domestic Speed Post/Registered Parcel table; Delhivery direct-contract rates: not captured.
- Real per-gram/per-part pricing from Slant 3D or a US FDM partner (Craftcloud FDM quotes) not captured; MJF/SLS per-cm³ reliable figures not found (Hi3DP figures suspect).
- Resin post-processing labor minutes and consumables (IPA, gloves) per part: not sourced.
- Current (Sept 2026) status of India Post → USA service and the exact duty rate on Indian consumer goods (18% vs 10%) need verification against primary sources (USTR/CBP/India Post).

## KQ2. Observed retail price bands per category, personalization premium, AOV / add-on attach

### Takeaway
Only the keychain band was verifiable ($5.99–15.99 on Etsy, Sept 2026; IGP personalized keychain ~USD 5); every other category's retail band could not be pulled because Etsy/Amazon/IGP/FNP pages are blocked and the search budget ran out, so the per-category bands in the final table are unverified estimates and must be validated before launch.

### Cited Findings
- Etsy personalized 3D-printed keychains: $5.99 sale (from $7.99, −25%), $6.95 sale (from $10.70, −35%), and $15.99; many listings offer free shipping; personalization = names/initials/phrases/numbers with colour and font choices (Sept 2026) — [Etsy: personalized 3D printing keychain](https://www.etsy.com/market/personalized_3d_printing_keychain); [Etsy: 3D printed keychain](https://www.etsy.com/market/3d_printed_keychain); [Etsy: custom name 3D printed keychain](https://www.etsy.com/market/custom_name_3d_printed_keychain); example listing — [Etsy listing 4337313072](https://www.etsy.com/listing/4337313072/3d-printed-personalized-keychain)
- IGP (India) "Endearing Memories Personalized Heart-Shaped Gold Keychain" shown at USD 5 on the international storefront; IGP and FNP both sell personalized photo keychains and name keychains (INR prices not exposed) — [IGP keychains](https://www.igp.com/key-chain-holders); [IGP personalized gifts 2026](https://www.igp.com/personalized-gifts); [FNP personalised keychains](https://www.fnp.com/personalised-key-chains-lp)
- Indian Diwali/gifting consumer trend pieces cite growing demand for personalized items (engraved jewelry, personalized photo frames, custom baskets) without price data — [IMARC India online gifting](https://www.imarcgroup.com/india-online-gifting-market)
- Etsy seller fee structure used by sellers in pricing: $0.20 listing fee, 6.5% transaction fee, 3% + $0.25 payment processing (US) — corroborated in several seller guides mirrored on GitHub (secondary; Etsy's own fee page not reachable) — [GitHub: etsy-shop-launcher skill](https://github.com/FerroxLabs/wayland/blob/main/src/process/resources/skills-library/bodies/skills/business-strategy/etsy-shop-launcher/SKILL.md); [GitHub: ETSY_SELLING_GUIDE](https://github.com/binda7835/AI-character-image-generation/blob/main/ETSY_SELLING_GUIDE.md); [GitHub: imagine-this-printed Etsy API research](https://github.com/ItMoney22/imagine-this-printed/blob/main/docs/ETSY_API_RESEARCH.md)

### Inferences
- With a ~$10.2 single-keychain landed floor (KQ1) and Etsy's ~10–12% fees, a $5.99–7.99 "free shipping" keychain is loss-making on labor-inclusive costs; sellers at that price are either ignoring labor, shipping as letters/large envelopes, or using multi-quantity orders. A defensible single-keychain price is $12.99–15.99 with free shipping, or $7.99 + $4.99 shipping.
- Personalization premium: no quantitative source retrieved; the Etsy examples show personalized 3D keychains clustering at the same $6–16 band as generic ones, suggesting the premium is realised through conversion/AOV rather than a higher sticker price (inference).

### Gaps
- Retail bands for keycaps, desk gadgets, photo frames, lithophanes, lamps, magnets, pins, jewelry, ornaments, figurines/busts, planters, cookie cutters, coasters, nameplates, cake toppers on Etsy/Amazon/Shopify and Indian sites (IGP/FNP/Printo/Vistaprint India) — not captured; the table below marks them "unverified estimate".
- Personalization price premium (% uplift vs generic), gift add-on attach rates, and Etsy/Indian AOVs — no sources retrieved.

## KQ3. Print-on-demand analogs (Printful/Printify/Gelato), 3D print-farm pricing, Etsy seller heuristics

### Takeaway
2D POD norms are a ~2.2× markup on base cost yielding 30–40% net margin (60–82% for low-base-cost décor items), while 3D-print sellers use "(material + machine + labor + overhead) × 2.0–3.0" or the cruder "10× materials + $3/print-hour"; Slant 3D quotes per part from a slicer estimate (printing cost + shipping cost per draft order) rather than publishing per-gram rates.

### Cited Findings
- Printful recommends a 30–40% net margin as a starting point; Printify recommends ~40% and "at least 40%" to fund growth — [Printful: POD profit margins 2026](https://www.printful.com/blog/what-is-a-good-profit-margin-for-print-on-demand); [Printify: t-shirt pricing 2026](https://printify.com/blog/t-shirt-pricing-calculator/); [Printify help: how much will I make per sale](https://help.printify.com/hc/en-us/articles/4483609656721-How-much-will-I-make-per-sale)
- Average POD margin ≈40%; wall décor reaches 60–82% vs apparel 20–40%; example Bella+Canvas tee $11.50 base → $24.99 retail (~2.2×, ~40% before marketplace fees); Printify base costs 20–30% below Printful — [CheckThat Printful pricing 2026](https://checkthat.ai/brands/printful/pricing); [TopBubbleIndex Printful pricing strategies](https://www.topbubbleindex.com/blog/printful-pricing-strategies/); [Printful: is POD profitable 2026](https://www.printful.com/blog/is-print-on-demand-profitable)
- Slant 3D: upload a file for instant estimated cost per part; pricing adjusted per application/volume; Shopify app "Slant 3D Print on Demand" charges a custom price based on each fulfillment request's cost to print — [Slant 3D](https://www.slant3d.com/); [Slant 3D API](https://www.slant3d.com/api); [Shopify app](https://apps.shopify.com/slant); free price-estimate tool released Nov 2025 — [Slant 3D on X](https://x.com/Slant3D/status/1993794822621286533); [SlantPOD calculator post](https://www.slantpod.com/post/calculate-prices-for-3d-prints-with-this-free-calculator)
- Slant 3D API structure (from public integration docs): the Slicer endpoint "slices a 3D model and returns the price"; an uncharged order draft returns `printing_cost`, `shipping_cost` and `total_price` in USD; fulfillment terms are "based on your order volume" — [GitHub: unofficial Slant 3D OpenAPI spec](https://github.com/NateXVI/slant3d-api-spec/blob/main/spec.yaml); [GitHub: AutoGPT Slant3D blocks](https://github.com/Significant-Gravitas/AutoGPT/blob/master/docs/integrations/block-integrations/slant3d/order.md); [GitHub: shapeMint Slant3D API notes](https://github.com/MattSharp05/shapeMint/blob/main/docs/API_information/Slant3D_API_info_NEW.md)
- Etsy/maker pricing formula: Price = (Material + Machine Time + Labor + Overhead) × profit multiplier, 2.0× starting point; community variants 2× wholesale / 2.5–3× retail; overhead = monthly fixed costs ÷ monthly prints — [CraftsTrack: how to price 3D prints](https://craftstrack.app/blog/how-to-price-3d-prints); [Etsy community pricing thread](https://community.etsy.com/t5/Etsy-Success/Pricing/td-p/96331759)
- Rule of thumb "10× on materials, $3 per print hour, $100/hour design" — [GrandpaCAD](https://grandpacad.com/en/tools/3d-printing-business-calculator); Print Farm Academy free pricing tool — [Print Farm Academy](https://www.printfarmacademy.com/print-farm-academy-FREE-pricing-tool); LayerMath "what to charge per hour 2026" — [LayerMath](https://layermath.com/blog/3d-printing-hourly-rate)

### Inferences
- Applying the 10× materials + $3/h rule to a 12 g / 0.6 h keychain gives $2.76 + $1.80 = $4.56 ex-shipping — close to the labor-inclusive floor computed in KQ1 ($4.3) but with no explicit labor line; for 5–10 h items (lithophane, bust) the rule yields $15–30 ex-shipping and does carry margin.
- A 3D-POD marketplace mirroring Printful economics would set the creator/seller share so that base cost ≈ 45% of retail (2.2×): e.g., keychain base $5.50 → retail $12.99; lithophane base $10 → retail $22–25 — consistent with the observed keychain band.

### Gaps
- Gelato margin guidance and Shopify's POD margin blog: not retrieved.
- Slant 3D actual per-gram/per-part numbers and shipping-fee schedule: not public in search results; only the API structure is documented.
- Print-farm hourly market rates (LayerMath article content) and YouTube print-farm channel heuristics: page blocked; only the GrandpaCAD/CraftsTrack heuristics captured.

## KQ4. Market size & growth (personalized gifts global/India, 3D printing services, India online gifting) and seasonality

### Takeaway
Global personalized gifts are ~$31–34B in 2025 growing ~5–9% a year with >55% bought online and North America ~38%; India's *online* gifting is a small, slow-growing ~$300M (IMARC, 3% CAGR) whose seasonality is extreme (Diwali reportedly 60–65% of annual gifting sales), while US holiday (Nov–Dec) sales are ~19% of annual retail.

### Cited Findings
**Personalized gifts (global)**
- Technavio: market to grow by USD 10.76B from 2024/25 to 2029 (press release Jan 2025) — [Technavio via PR Newswire](https://www.prnewswire.com/news-releases/personalized-gifts-market-to-grow-by-usd-10-76-billion-2025-2029-innovation-in-new-product-development-drives-growth-report-highlights-ai-impact-on-trends---technavio-302360261.html); [Technavio report page](https://www.technavio.com/report/personalized-gifts-market-size-industry-analysis)
- USD 31.05B (2025) → 43.5B (2029), 8.8% CAGR — [Research and Markets](https://www.researchandmarkets.com/reports/5989738/personalized-gifts-market-report); USD 32.07B (2025) → 57.19B (2033), 7.5% — [Business Research Insights](https://www.businessresearchinsights.com/market-reports/personalized-gifts-market-102185); USD 33.70B (2025) → 69.20B (2033), 9.4% — [Data Bridge](https://www.databridgemarketresearch.com/reports/global-personalized-gifts-market); USD 31.4B (2025) → 52.9B (2035), 5.4% — [Market Research Future](https://www.marketresearchfuture.com/reports/personalized-gifts-market-10348). **Conflict**: CAGRs range 5.4–9.4% across firms.
- >55% of personalized-gift purchases occur online; North America ≈38% share (~USD 11.9B) in 2025 — [SkyQuest](https://www.skyquestt.com/report/personalized-gifts-market); [Verified Market Research](https://www.verifiedmarketresearch.com/product/personalized-gifts-market/); US-specific report — [Arizton US personalized gifts](https://www.arizton.com/market-reports/united-states-personalized-gifts-market)

**India gifting**
- India online gifting market USD 300.0M (2024) → 398.3M (2033), 3.2% CAGR (IMARC) — [IMARC India online gifting](https://www.imarcgroup.com/india-online-gifting-market); another cut: USD 309.6M (2025) → 406.5M (2034), 3.07% — [Steemit summary of 2025 trends](https://steemit.com/steemit/@samwalter/5-india-online-gifting-market-trends-every-business-must-know-in-2025)
- IMARC "India gifting market" projected to USD 1,089.9M by 2034 — [OpenPR/IMARC](https://www.openpr.com/news/4602815/india-gifting-market-is-on-track-to-touch-usd-1-089-9-million); [IMARC India gifting](https://www.imarcgroup.com/india-gifting-market); [Research and Markets India gifting to 2030](https://www.researchandmarkets.com/report/india-gifting-market). **Scope caution**: these sub-$1.1B figures are far below the tens-of-billions "total gifting" estimates often quoted in Indian trade press (not retrieved) — they appear to cover only organised/online segments.
- India corporate gifting 2025–2030 overview — [Chococraft](https://www.chococraft.in/blogs/corporate-gifts/corporate-gifting-industry-india-2025-2030)

**3D printing services / consumer**
- 3D printing services market USD 10.98B (2025) → 21.07B (2033), 22.7% CAGR — [Market Growth Reports](https://www.marketgrowthreports.com/market-reports/3d-printing-services-market-100994); services to reach USD 16.28B by 2029 at 16.1% — [The Business Research Company](https://www.thebusinessresearchcompany.com/report/3d-printing-services-global-market-report); [Research and Markets 3D printing services 2026](https://www.researchandmarkets.com/reports/5735356/3d-printing-services-market-report)
- Total 3D printing market USD 23.41B (2025), 28.55B (2026) → 136.76B (2034) — [Fortune Business Insights](https://www.fortunebusinessinsights.com/industry-reports/3d-printing-market-101902); services 36.1% of offerings (2025) — [Grand View Research](https://www.grandviewresearch.com/industry-analysis/3d-printing-industry-analysis); services fastest-growing at 16.22% CAGR to 2031 — [Mordor Intelligence](https://www.mordorintelligence.com/industry-reports/3d-printing-market)
- Consumer-grade 3D printers USD 1.91B (2025) → 9.74B (2033), 22.6% CAGR — [Market Growth Reports](https://www.marketgrowthreports.com/market-reports/consumer-grade-3d-printers-market-100204)

**Seasonality**
- India: Diwali generates 60–65% of annual gifting sales (secondary newsletter; original source not verified) — [ReadOn Substack](https://readon.substack.com/p/diwali-gifting-an-inr-20000-cr-grand); festive gifting = 58% of annual corporate gifting spend, peaks at Diwali/New Year/Holi — [Zenodo: festive corporate gifting in India](https://zenodo.org/records/20508483); corporate India cut Diwali gifting budgets 35–40% in one downturn year — [Moneylife](https://www.moneylife.in/article/corporate-india-cuts-gifting-budget-by-35-percentage40-percentage-this-diwali/51928.html); Diwali stats compilations — [TapWell Diwali gifting stats](https://tapwell.in/diwali-shopping-and-gifting-statistics/), [TapWell Diwali spending 2026](https://tapwell.in/diwali-spending-statistics-india/); festive-season advertiser guide — [Microsoft Advertising (Sept 2025)](https://about.ads.microsoft.com/en/blog/post/september-2025/indias-festive-shopping-surge-a-guide-for-advertisers-around-the-globe)
- US: 2024 holiday (Nov–Dec) core retail sales $994.1B vs full-year 2024 core retail $5.28T — [NRF holiday FAQs](https://nrf.com/research-insights/holiday-data-and-trends/winter-holidays/winter-holiday-faqs); [National Jeweler on NRF](https://nationaljeweler.com/articles/13582-holiday-sales-hit-record-high-says-nrf); NRF expected 2025 holiday sales to surpass $1T for the first time — [NRF press release](https://nrf.com/media-center/press-releases/nrf-expects-holiday-sales-to-surpass-1-trillion-for-the-first-time-in-2025); gift-giving stat compilations — [GiftAFeeling 2025](https://www.giftafeeling.com/pages/gift-giving-statistics-2025), [GiftHint](https://gifthint.co/gift-giving-statistics)

### Inferences
- US Nov–Dec ≈ $994.1B ÷ $5.28T ≈ 18.8% of annual core retail (arithmetic on NRF figures); gift-centric categories skew higher (not quantified here).
- For an India-first 3D gifting app, capacity planning must handle a Diwali spike where 2–3 months could carry the majority of annual orders; a US channel (Christmas/Mother's Day/Valentine's) smooths the calendar because the peaks don't overlap (Diwali Oct/Nov vs Christmas Dec vs Valentine's Feb vs Mother's Day May).
- Personalized-gift growth (5–9%) is far slower than 3D-printing-services growth (16–23%), so the 3D angle should be positioned as taking share of personalized gifting rather than riding gifting growth.

### Gaps
- Christmas/Valentine's/Mother's/Father's Day/Rakhi/wedding/corporate shares of annual sales for gifting or 3D-print sellers: not captured (only Diwali 60–65% and US Nov–Dec 18.8%).
- Etsy's Q4 share of GMS; Indian personalized-gift segment size (Redseer/Inc42/YourStory) and the commonly cited $60–70B total India gifting figure: not retrieved.

## KQ5. Business models (own farm vs partner vs hybrid), AI-generation cost in unit economics, bundles, minimum viable order value — with per-category viability table

### Takeaway
AI generation is cheap (≈$0.10–0.75 per model; $0.30–0.45 on Meshy, $0.10–0.50 on Tripo, ≈$0.75 on Rodin at direct-credit prices) and is a rounding error against $9–11 of US labor+shipping per order, so the model choice hinges on fulfillment: an own Bambu farm (A1/P1S at $339–699) for high-volume small SKUs at ~$0.20/h machine cost, partners (Indian services at ₹5–20/g, Slant 3D/Craftcloud in the US) for large or exotic parts and for US-side fulfillment post-de minimis, and pricing built around free-shipping thresholds and 3-packs because per-order fixed costs are 70–90% of a small item's floor.

### Cited Findings
**AI 3D generation cost**
- Meshy: 20 credits per text-to-3D preview (Meshy-6/7; 25 with Ultra), 10 credits for PBR texturing/refine, 30 credits full pipeline, 20 credits image-to-3D without texture — [Meshy docs: pricing](https://docs.meshy.ai/en/webapp/pricing); [Meshy API pricing](https://docs.meshy.ai/en/api/pricing); [Meshy credits guide 2026](https://www.meshy.ai/tutorials/meshy-credits-guide); credit table mirrored in [GitHub: meshy-mcp-vscode README](https://github.com/Maeve-Studios/meshy-mcp-vscode)
- Meshy plans: Free 100 credits/month (no API access); Pro unlocks API with 1,000 credits/month (reported $10–20/month depending on billing); Studio $60/month ($48 annual-equivalent reported) with 4,000 credits — [Meshy pricing](https://www.meshy.ai/pricing); [Meshy help: plans](https://help.meshy.ai/en/articles/12062933-what-are-your-prices-and-plans-offered-and-do-you-have-monthly-annual-plans); [GitHub: mcp-3d-gen README](https://github.com/kevinten-ai/mcp-3d-gen)
- Tripo (per its pricing docs as summarised in a third-party notes file): $1 = 100 credits; text-to-model 10 credits untextured / 20 textured; image-to-model 20/30; multiview 20/30; P1 model (2026-03) 30/40, 40/50, 40/50; texture_model 10; failed tasks refunded; credits never expire — [GitHub: k-code report citing docs.tripo3d.ai/get-started/pricing.html](https://github.com/korallis/k-code/blob/main/data/kz-transform-c2/report.md); [Tripo pricing page](https://www.tripo3d.ai/pricing)
- Rodin (Hyper3D): Creator $30/month ($288/yr), Business $120/month ($1,152/yr), Enterprise custom; direct credits $1.50 each; Gen-2.5 API 0.5 credits base generation, +0.5 for Extreme-High tier, +2.0 for extreme-high texture — [Hyper3D pricing](https://hyper3d.ai/pricing); [Hyper3D API docs](https://developer.hyper3d.ai/api-specification/rodin-generation); [The Rundown: Rodin](https://www.therundown.ai/tools/rodin); [CostBench Rodin free plan](https://costbench.com/software/ai-3d-generation/rodin-hyper3d/free-plan/); third-party estimates $0.50–1.50 per model — [Dupple Rodin review](https://dupple.com/reviews/rodin-ai); "~$0.50–2.00/render" — [GitHub: JediRe render pipeline](https://github.com/Nardo758/JediRe/blob/main/tools/render-pipeline/README.md)
- Open-weight alternatives hosted: TRELLIS on Replicate ≈$0.07 per 3D generation; PiAPI ≈$0.035 per generation — [GitHub: awesome-3dv-weekly](https://github.com/FishWoWater/awesome-3dv-weekly); comparison guides — [3DAI Studio: cost of AI 3D generation 2026](https://www.3daistudio.com/3d-generator-ai-comparison-alternatives-guide/how-much-does-ai-3d-model-generation-cost); [3DAI Studio API comparison](https://www.3daistudio.com/blog/best-3d-model-generation-apis-2026)

**Fulfillment models**
- Own farm economics: printer cost, hourly cost decomposition, 10% failure planning, 2 min removal / 10 min pack per order, "10× materials + $3/h" — see KQ1/KQ3 citations ([LayerMath](https://layermath.com/blog/how-to-run-a-3d-print-farm), [SimplyPrint](https://simplyprint.io/articles/how-to-start-a-3d-print-farm), [3DQue](https://www.3dque.com/blog/the-real-cost-benefits-of-3d-print-farm-automation), [GrandpaCAD](https://grandpacad.com/en/tools/3d-printing-business-calculator)); Bambu farm management guide — [Printago 2026](https://printago.io/blog/bambu-lab-print-farm-guide-2026)
- Partner fulfillment: Slant 3D quote-per-part with printing + shipping cost per order and volume-based terms ([Slant 3D API](https://www.slant3d.com/api); [GitHub AutoGPT Slant3D docs](https://github.com/Significant-Gravitas/AutoGPT/blob/master/docs/integrations/block-integrations/slant3d/order.md)); Indian services ₹5–20/g FDM ([iamRapid](https://iamrapid.com/pricing/), [Precious3D](https://precious3d.com/cost-of-3d-printing-in-india-complete-pricing-guide/)); Craftcloud no-minimum aggregator ([Craftcloud](https://craftcloud3d.com/)); JLC3DP from $0.30 ([JLC3DP](https://jlc3dp.com/))
- POD margin norms 30–40% net at ~2.2× markup ([Printful](https://www.printful.com/blog/what-is-a-good-profit-margin-for-print-on-demand); [Printify](https://printify.com/blog/t-shirt-pricing-calculator/))
- Shipping fixed costs per order: USPS GA $5.90/$7.30/$8.85 retail Zone 1 tiers and July-2026 commercial tier changes ([idshipthat](https://idshipthat.app/shipping-rates/usps-ground-advantage/), [Pirate Ship](https://support.pirateship.com/en/articles/15453569-july-2026-usps-rate-and-rule-changes)); Shiprocket ₹20–26 base per 500 g, landed ~2× ([CheckThat](https://checkthat.ai/brands/shiprocket/pricing)); de minimis end and India tariff path ([CBP](https://www.cbp.gov/sites/default/files/2025-08/factsheet_suspension_of_duty-free_de_minimis_treatment.pdf); [White House Feb 2026](https://www.whitehouse.gov/fact-sheets/2026/02/fact-sheet-the-united-states-and-india-announce-historic-trade-deal/))

### Inferences
**AI cost per order (arithmetic)**
- Meshy Studio: $60 ÷ 4,000 = $0.015/credit → mesh-only generation (20 cr) = $0.30; with texture (30 cr) = $0.45. Pro at $20/1,000 = $0.02/credit → $0.40–0.60. For printing, texture is unnecessary → ~$0.30–0.40 per generation.
- Tripo: textured text-to-model 20 cr = $0.20; image-to-model 30 cr = $0.30; P1 40–50 cr = $0.40–0.50; untextured 10 cr = $0.10.
- Rodin: 0.5 cr × $1.50 = $0.75 per regular generation at direct-credit price (lower on subscription credits).
- If buyers average 4–6 generations before ordering and only ~1 in 5 generating users buys, AI cost per *paid order* ≈ 5 × 5 × $0.10–0.40 = $2.5–10 — larger than material+machine for small items; gate free generations (e.g., 2–3 free, then credits), reuse meshes across variants, and prefer Tripo-untextured/open-weight endpoints for previews. (Conversion and generations-per-order are assumptions.)

**Fulfillment model comparison (US single keychain, delivered floor from KQ1 ≈ $10.2; India ≈ ₹115)**
- Own farm: contribution ≈ retail − $10.2 − marketplace/payment fees (~3–12%) − AI ≈ $0.5–2. At $14.99 free-shipping ⇒ ~$2–4 contribution (15–25%); at 3-pack $29.99 ⇒ ~$14–16 (~50%).
- Partner (India ₹6–15/g + ₹45 ship + ₹20 pack): keychain landed ₹137–245 ⇒ needs ≥₹299–399 retail for 30–45% gross; lithophane 60 g landed ₹425–965 ⇒ ≥₹899–1,499 retail. Partner is viable for India large items and as overflow at Diwali; own farm wins for sub-30 g SKUs where per-gram partner pricing is 7–18× material.
- US partner (Slant 3D/Craftcloud): pricing not public; expect base ≥ own-farm floor plus their margin; still preferable to India→US shipping once postage (₹865+) and duties (10–18%) are counted.
- Hybrid recommendation: Bambu A1/P1S farm in India (₹11/h machine cost, ₹0.85/g PLA) for domestic small SKUs; Indian per-gram partners for >150 g or resin/MJF items; a US partner API (Slant 3D-style: slice → printing_cost + shipping_cost → draft → order) for US demand; charge a platform take rate in the POD norm (base ≈45% of retail, i.e. ~2.2×).

**Bundles & minimum viable order value**
- Per-order fixed costs (labor $3.75 + packaging $0.15–0.75 + shipping $5.90–8.85) = $9.8–13.4 US; ₹80–110 India. A 3-pack of small items (keychains/magnets/ornaments/toppers) cuts per-unit landed cost ~55–60%; coasters should be sold as 4-sets, magnets/pins in sets of 3–6.
- Minimum viable order: US ≈ $15 (free-shipping threshold) for single small items, $25–30 for 3-packs; India ≈ ₹249–299 single small item with ₹499 free-shipping threshold (inference from floors above). Below these, gross margin after fees is <20%.

**Per-category summary table** (US own-farm floor from KQ1; retail bands: keychain verified on Etsy Sept 2026; all others are the researcher's *unverified estimates* pending marketplace scraping; margin = (mid-band price − delivered floor) ÷ mid-band price, before marketplace fees and AI cost)

| Category | Est. grams / hours (assumed) | US cost floor delivered / ex-ship | India floor delivered (own farm) | Retail band (USD; * = unverified estimate) | Implied gross margin at mid-band | Viability verdict |
|---|---|---|---|---|---|---|
| Keychain / bag charm | 12 g / 0.6 h | $10.2 / $4.3 | ₹115 | $5.99–15.99 (Etsy, verified) | ~7% at $10.99 single; ~60% as 3-pack at $29.99 | Viable only as bundles / free-shipping ≥$14.99 |
| Keyboard keycap | 3 g / 0.5 h (resin preferred) | $10.0 / $4.1 | ₹105 | $10–40* | ~50% at $19.99 | Viable (premium niche; resin quality needed) |
| Desk gadget (phone stand / pen holder) | 60–90 g / 3–4.5 h | $13.5–15 / $6.2–7.5 | ₹250–300 | $10–25* | ~15% at $17.99 | Marginal; needs $20+ or bundling |
| Photo frame | 120 g / 6 h | $17.6 / $8.8 | ₹380 | $15–40* | ~35% at $27.99 | Marginal (competes with mass-market frames) |
| Lithophane (flat) | 60 g / 5 h | $14.1 / $6.8 | ₹219 | $25–60* | ~65% at $39.99 | Strongly viable (high perceived value, photo personalization) |
| Fridge magnet | 15 g / 0.8 h + magnet | $10.5 / $4.6 | ₹120 | $6–15* | negative single at $8.99; ~50% as 4-set at $24.99 | Viable only in sets |
| Pin / badge | 5 g / 0.5 h + pin back | $10.2 / $4.3 | ₹105 | $6–15* | negative single; ~45% in sets | Viable only in sets / event bulk |
| Pendant / earring | 4 g / 0.4 h + findings | $10.3 / $4.4 | ₹110 | $12–35* | ~55% at $22.99 | Viable (jewelry perceived value; resin preferred) |
| Ornament | 20 g / 1.2 h | $10.7 / $4.8 | ₹135 | $10–25* | ~40% at $17.99; better in 3-packs | Viable, seasonal (Q4) |
| Figurine / bust | 120 g / 10 h (or resin) | $19.1 / $10.3 | ₹420 | $30–80* | ~60% at $49.99 | Viable at premium; quality/AI-mesh risk highest |
| Planter | 150 g / 7 h | $18.5 / $9.7 | ₹430 | $15–35* | ~25% at $24.99 | Marginal (bulky, volumetric shipping) |
| Lamp / night light (lithophane shade + LED base) | 120 g / 8 h + base | $24.5 / $15.6 | ₹570 | $40–90* | ~60% at $59.99 | Viable (highest AOV; base sourcing needed) |
| Cookie cutter | 25 g PETG / 1.2 h | $10.7 / $4.8 | ₹135 | $8–20* | ~25% at $13.99 | Marginal single; viable in 3-sets |
| Coaster | 35 g / 1.5 h | $12.4 / $5.1 (4-set ≈ $17) | ₹160 | $8–15 each*; sets $25–40* | ~45% as 4-set at $29.99 | Viable only as sets |
| Nameplate | 60 g / 3 h | $13.5 / $6.2 | ₹250 | $15–40* | ~50% at $27.99 | Viable (desk/office gifting, corporate bulk) |
| Cake topper | 20 g / 1.2 h | $10.7 / $4.8 | ₹135 | $10–30* | ~45% at $19.99 | Viable (event-driven, time-sensitive shipping) |
| Raw "print my model" | per quote | material + $0.20/h + $3.75 labor + ship | ₹0.85/g + ₹11/h + ₹37.5 + ship | India services ₹5–20/g (verified); US per-gram not public | set price = 2–3× (material+machine+labor) with $8–10 / ₹150–250 minimum | Viable as utility tier; low margin, high support load |

### Gaps
- Real conversion rates and generations-per-order for AI 3D gifting apps: none found; AI-cost-per-order figures are scenario arithmetic.
- Slant 3D / US partner base prices, Gelato/Shopify POD margin guidance, and Etsy offsite-ads fees: not captured.
- Indian retail comps in INR for every category (IGP/FNP/Printo/Vistaprint) and Indian personalized-gift AOVs: not captured.
- LED base / magnet / pin-back / jewelry-findings component costs: assumed, not sourced.
