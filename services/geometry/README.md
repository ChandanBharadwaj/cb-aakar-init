# aakar-geometry

Template registry, CAD, exports, build pipeline, HTTP API and RabbitMQ worker (PLAN §7).
Turns a **Design Spec** (`packages/contracts/schemas/design-spec.v1.json`) into printable
geometry, exports GLB / 3MF / STL to storage, runs the inspect service and returns a
`design.completed` payload. No executable code ever crosses the spec boundary.

## CAD path

Templates are built with **build123d 0.13** (OCCT via `cadquery-ocp-novtk`): each template
describes itself as extruded 2D profiles (shapely) plus rigid transforms and booleans, and
`aakar_geometry/cad.py` is the only module that touches the kernel. Solids are tessellated
(0.05 mm / 0.3 rad) into a merged, watertight `trimesh.Trimesh` in mm, Z-up, sitting on Z = 0.

## Templates

`aakar_geometry/templates/` — `Template` base class with `descriptor()` (validated against
`template-descriptor.v1.json`), `validate(params)` (defaults filled, every out-of-range key
reported at once as `param_out_of_range`, unknown keys as `invalid_spec`; **never clamped**; then
`validate_combination`), `build(params, features=(), fetcher=None) -> trimesh.Trimesh`,
`karigar_note(params)`. The recipe for a new carrier is in [Features (Chhaap)](#features-chhaap).
Every template is registered in `templates/__init__.py`; `register()` refuses a family that is not
in `families.json`.

| Template | Family (codename) | Params: range (default) | Anchors: default printable size | Hardware |
|---|---|---|---|---|
| `jharokha_phone_stand@1` | `phone_stand` (Sahara) | see below | `side_left`, `side_right` (21.2 × 18.1 mm, text ≤ 12 mm), `back` (74 × 8.6 mm): name, motif | — |
| `keychain_tag@1` | `keychain` (Saathi) | `shape` rect · rounded · circle · heart (rounded); `width_mm` 30–60 (45), longest side with the loop; `thickness_mm` 2.4–4 (3); `hole_d_mm` 4–6 (4.2) | `face` (+Z): photo, name, motif; `back` (−Z): photo, name; 32 × 23.5 mm | `split_ring_25` ×1 |
| `fridge_magnet@1` | `fridge_magnet` (Chumbak) | `shape` rect · rounded · circle (rounded); `size_mm` 40–70 (55), longest side; `thickness_mm` 4.5–6 (5); `magnet_count` 1–2 (1) | `face` (+Z): photo, name, motif; 47.2 × 35 mm | `magnet_d10x3` × `magnet_count` |
| `hanging_ornament@1` | `ornament` (Jhoomar) | `silhouette` disc · star · bauble (disc); `diameter_mm` 50–90 (70), longest side; `thickness_mm` 3–5 (4); `border_mm` 0–6 (3), 0 or ≥ 1.5 | `face_front` (+Z) and `face_back` (−Z): photo, name, motif; 48.7 × 36.6 mm | `cord_200` ×1 |
| `desk_nameplate@1` | `nameplate` (Pehchaan) | `width_mm` 120–250 (180); `height_mm` 40–100 (60), face up the slope; `tilt_deg` 60–80 (70); `base_depth_mm` 30–60 (40); `thickness_mm` 4–8 (5) | `face` (tilted): name, motif, photo; 176 × 56 mm, text ≤ 36 mm; `base_front` (−Y): name, photo; 178 × 8 mm | `adhesive_pads` ×1 |
| `raw_print@1` | `raw_print` (Swaroop) | none: size and orientation live on the `hero_mesh` feature | `body` (volume): 240 × 240 × 240 mm | — |
| `lithophane_plate@1` | `lithophane` (Roshni) | `size` square 100 × 100 · portrait 104 × 140 (portrait); `min_mm` 0.8–1.8 (0.8), the lightest parts; `max_mm` 2–3 (3), the darkest, at least 1.2 more; `stand` none · night_light (night_light) | `plate` (−Y standing, +Z lying): the photo in `lithophane` mode only; 95 × 131 mm (square 91 × 91) | `led_base_usb` ×1 with the night light; none for the plate alone |
| `plinth_round@1` | `figurine_base` (Pratima) | `shape` round · rounded_square (round); `diameter_mm` 40–120 (70); `height_mm` 6–20 (12) | `top` (volume): your form, `diameter_mm` × `diameter_mm` × 180 mm; `base_front` (−Y): name; 39 × 11 mm | — |
| `pet_tag@1` | `keychain` (Saathi) | `shape` bone · disc (bone); `size_mm` 25–35 (30), longest side; `thickness_mm` 3–4 (3.5); the ring hole is 4 mm | `face` (+Z) and `back` (−Z): name first, motif, photo; 23.1 × 10.8 mm | `split_ring_25` ×1 |
| `photo_frame_std@1` | `photo_frame` (Chaukhat) | `orientation` portrait · landscape (portrait), for a 4 × 6 in photo; `border_mm` 12–25 (18); `stand` easel · hanger (easel) | `border` (the top rail): motif, name; `base_front` (the bottom rail): name; 121.9 × 15 mm; cut in, never raised | `acrylic_4x6` ×1 |
| `keycap_mx@1` | `keycap` (Kunji) | `stem_slop_mm` 0.3–0.5 (0.4) | `top` (+Z, dished): photo relief or 1–3 letters, at most 0.6 mm; 11.1 × 11.1 mm | — |

The first four carriers publish `min_feature_mm` 0.8, constraints min wall 1.2 / overhang 55° / bed 250³,
and take names and motifs besides photos (`features_supported`: `relief_image`, `emboss_text`,
`motif`; the nameplate lists `emboss_text` first, its primary content), every surface anchor with a
relief or letter depth up to `max_relief_mm` 1.5 (the Jharokha's 1.2); `raw_print` takes one `hero_mesh`.
The keepsakes and the second wave (PR 8, below) keep the 250³ bed; the lithophane's and the keycap's
minimum wall is 0.8 mm (the contract's floor: a lithophane is 0.8 mm where the photo is lightest, and an MX
stem is thinner still at its cross's tips), and the keycap's letters keep 1 mm strokes (`min_feature_mm`).
Descriptors publish anchor sizes at the default parameters; `anchor_frame(anchor_id, params)` gives
the exact frame for any parameters.

### `jharokha_phone_stand@1` (family `phone_stand`)

| Param | Range | Default | Handle |
|---|---|---|---|
| `width_mm` | 70–110 | 92 | yes |
| `depth_mm` | 60–100 | 78 | yes |
| `height_mm` | 90–150 | 120 | yes |
| `tilt_deg` (back rest from horizontal) | 60–78 | 70 | |
| `lip_height_mm` | 8–18 | 12 | |
| `wall_mm` | 2.4–4.0 | 3.2 | |
| `arch_cusps` (integer) | 3–7 | 5 | |

Base plate; back rest leaning back by `tilt_deg` with a Mughal multifoil (cusped) arch window
cut through it; a parallel front lip; two side gussets joining lip, base and back rest; a 12 mm
cable notch through the lip and base. Overall bounds equal width × depth × height (within
kernel rounding). Anchors `side_left`, `side_right`, `back` (planar) each take a name and/or a motif
(`features_supported: ["emboss_text", "motif"]`, `min_feature_mm` 0.8), raised or cut up to 1.2 mm
(`max_relief_mm`: the contract's default letter depth, and a 1.2 mm cut into the thinnest 2.4 mm wall
still leaves the 1.2 mm minimum). The back band is only 8.6 mm tall: short names in every script and
every library motif fit there, a long or fine-stroked name (Kannada) is refused as too thin and fits a
side rail instead. Constraints min wall 1.2, max overhang 55°, bed 250³. Materials: the six ids from
`packages/design-tokens/materials.json`. A non-`none` style fails with `unsupported_feature`.

One coupled rule: a tall, shallow, far-leaning combination whose back rest would leave less than
12 mm of phone slot at the base fails with `param_out_of_range` on `depth_mm, height_mm, tilt_deg`.

Example (`packages/contracts/examples/jharokha-phone-stand.spec.json`): 92 × 78 × 120 mm,
44.6 cm³, 680 triangles, walls 3.2 mm, COG 2 mm inside the base, 37 mm tipping margin,
≈55 g of PLA, 3 h 33 by the heuristic estimator.

### The flat carriers: `keychain_tag@1`, `fridge_magnet@1`, `hanging_ornament@1`

Each lies face up: X across (the viewer's right), Y up the picture, the back on the bed at Z = 0 and
the face at Z = thickness, bounding box centred in X and Y. Outlines are shapely polygons extruded
by `cad.prism`; shared helpers live in `templates/plates.py` (circles and circumscribed holes, the
heart / star / bauble outlines, `symmetric_rect` for the printable area, the skin rule). The
printable rectangle is the one holding the largest 4:3 picture (phone photos) inside the face,
`ANCHOR_MARGIN_MM` 1.5 from the edge and clear of any hole and its rim (bleed 1 mm). A back anchor
is the same rectangle seen with the piece turned over left to right (u = −X, v = +Y, normal −Z),
so it stays right-handed and the loop stays at the top of the picture.

* **Skin rule** (`validate_content`): a cut-in (deboss) photo, name or motif must leave 1.2 mm of
  plastic behind it; cut-ins on opposite faces add up; a name and a motif side by side on one face
  count once (the deeper); over a magnet pocket the pocket depth counts too. Raised (emboss) content
  never counts. Refused with `param_out_of_range` naming the feature keys and `thickness_mm` and what is
  cut in ("the motif would leave only 0.8 mm of plastic between the motif and the magnet"), before
  anything is built. So a motif cut its default 1 mm into a default 5 mm magnet is refused: raise it,
  cut it 0.6 mm, or make the magnet 5.4 mm thick.
* **Keychain**: the outline is scaled so that the longest side *including the loop* is exactly
  `width_mm` (the family envelope, 30–60 mm). The ring hole is cut `hole_d_mm` + 0.2 mm (printed
  holes close up) as a circumscribed polygon, in a round loop above the plate (in the heart's
  notch) that keeps exactly 2 mm of plastic all round (a 25 mm split ring's 1.2 mm wire). Default:
  45 × 35.4 × 3 mm, 3.6 cm³, ≈4 g.
* **Magnet**: pockets 10.2 × 3.2 mm (a 10 × 3 mm magnet + 0.2 mm) open on the back, one in the
  middle or two at ±`size_mm`/4, each ≥ 2 mm from the edge and each other; 1.3–2.8 mm of plastic
  over them. `hardware_for(params)` packs `magnet_count` magnets, so the completed payload carries
  the right quantity. Default: 55 × 41.25 × 5 mm, ≈13 g.
* **Ornament**: `diameter_mm` is the longest side of the silhouette (star and bauble are scaled to
  it); the hanging hole (3.5 mm + 0.2 mm) sits as high as a 2 mm rim allows; the raised border
  stands 1 mm proud of the front only, so the back prints flat; a border between 0 and 1.5 mm is
  refused (it would print as a hair-thin line), as is a border that leaves less than 8 mm of
  picture. Default: 70 × 70 × 5 mm, ≈20 g.

### `desk_nameplate@1` (family `nameplate`)

A 10 mm foot (`width_mm` × `base_depth_mm`) with a `thickness_mm` plate leaning back at `tilt_deg`,
built like the Jharokha back rest: the face starts 3 mm behind the foot's front edge and its lower
back edge is buried in the foot. `face` frame: normal perpendicular to the plate, towards the reader
(`(0, −sin t, cos t)`), v up the slope. `base_front`: the foot's front strip. One coupled rule: the
(Y, Z) cross-section is the same along the width, so its centroid is the centre of gravity; a
combination whose centre of gravity sits less than 6.5 mm from the back of the foot (the inspect
service's 5 mm tipping threshold + 1.5 mm for content) is refused on `base_depth_mm, height_mm,
tilt_deg` (of the 32 parameter corners, only the four with a 100 mm face at 60° on a 30 mm foot).
Width stops at 250 mm, the bed and the Pehchaan family envelope's longest side (120–250 mm). Default:
180 × 40 × 66.4 mm, 18 mm tipping margin, ≈93 g.

### `raw_print@1` (family `raw_print`, Swaroop)

The customer's own model file printed as it is (ADR-0014): no params, `build_body` is empty, one
volume anchor `body` (the bed limited by the family envelope: 240³, seated on Z = 0 at the centre),
`features_supported: ["hero_mesh"]`, no hardware. `validate_content` refuses a spec without its
model (`invalid_spec`: "Add your model file to print it as it is", even when `features` is empty),
insists on `fit: longest` (so a raw print is never silently bed-sized), and refuses a `longest_mm`
outside the family envelope 20–240 mm (`param_out_of_range` on `features[i].longest_mm`, never
clamped; the contract itself allows 5–250). A model with walls under the printability threshold
still completes, with `printability.passed=false`.

### The keepsakes and the second wave (PR 8)

**`lithophane_plate@1` (family `lithophane`, Roshni).** The photo becomes the plate: a `relief_image` in
`lithophane` mode is required (a spec without it is `invalid_spec` "Add the photo that becomes your Roshni
night light"; a photo raised or cut in is `unsupported_feature`), and the template's `lithophane` hook
turns its heightmap into the plate: the darkest pixel `max_mm`, the lightest `min_mm` (`invert` swaps them
upstream), a photo that does not fill the plate edged dark at `max_mm`. The feature's `relief_mm` does not
apply. The plate is one closed heightfield over a 4 mm frame (`max_mm` + 1 mm, proud of the photo on the
front), a 0.5 mm bevel and the photo (`features.heightfield.grid_solid`, at the photo's own spacing, at most
120k samples ≈ 240k triangles), so no boolean touches the photo. With `stand: night_light` (default) the
plate stands fused 3 mm into a 12 mm base: **one print**, printed standing as modelled (the thickness is
then drawn by the nozzle rather than stepped by layers), no supports; behind it a 70.6 mm pocket, 8 mm
deep, takes the 70 mm USB LED puck face up, with a 12 mm cable notch out through the back. The portrait
night light is 116 × 88 × 149 mm, inside the family's 100–150 mm envelope. With `stand: none` the plate
lies on its flat back and ships without the light (`hardware_for` returns no `led_base_usb`). Constraints
min wall 0.8 mm; the lightest pixel is 0.02 mm thicker than `min_mm`, because the printability check
measures walls from 0.01 mm inside the skin and would read a 0.8 mm light area a hair under 0.8. The family
allows `basic_white` only; any other finish is `invalid_spec` "Roshni photo night light comes in Basic White
only". The karigar's note asks for white, 0.1–0.12 mm layers and 100% infill.

**`plinth_round@1` (family `figurine_base`, Pratima).** A round plinth (flat at the front, a D-shaped
footprint, so the name has a flat face) or a rounded square. The `top` volume anchor sits at the footprint's
centroid with `bounds_mm` = diameter × diameter × 180, so plinth and form stay under the family's 200 mm
(20 + 180 − 0.5); the customer's form (`fit: contain`, or `longest`, refused and never shrunk when it does
not fit) is seated centred and fused. After fusing, `build` runs the inspect service's stability check on
the whole piece: the centre of gravity must sit 5 mm inside the footprint and the piece must stand a 10°
nudge, or it is refused (`param_out_of_range` on `diameter_mm`, and `longest_mm` for a set size) with the
narrowest plinth that would hold it, worked out rather than guessed (a wider plinth is heavier, and a
contained form grows with its room), or a request for a smaller form. `base_front` takes a name; below
7 mm tall the plinth leaves under 4 mm for letters and a name is refused as too long. No hardware.

**`pet_tag@1` (family `keychain`, "Saathi Pet").** A bone (end to end `size_mm`, the ring hole in a tab
between the top lobes) or a disc (the hole inside it at the top), 25–35 mm, 3–4 mm thick; the hole is
4 mm cut 4.2 mm with a 2 mm rim for the 25 mm split ring. `face` and `back` take a name first, and a motif
or photo where the lettering and motif rules allow; the text area is the largest 2:1 rectangle clear of the
hole. The skin rule of the flat carriers applies. The karigar's note asks for PETG. 25–29 mm tags sit below
the keychain family's 30 mm floor (the seed needs 25).

**`photo_frame_std@1` (family `photo_frame`, Chaukhat).** For a 4 × 6 in photo (101.6 × 152.4 mm; the only
pane in the seed is `acrylic_4x6`, so 5 × 7 waits for a new hardware SKU), portrait or landscape. The
framer's rules: the lip overlaps the photo by 1/4 in (6.35 mm) on every side (window 88.9 × 139.7 mm) and
the rabbet is the photo + 1/16 in (1.6 mm), 3/8 in (9.5 mm) deep, behind a 3 mm lip. `border_mm` 12–25
is the face round the window. `easel`: two feet behind the side walls, clear of the rabbet's opening, stand
it leaning back 15° (built standing so, stability checked); `hanger`: a tab with a 5 mm nail hole on top
(built lying on its back). Both print face down with no supports, so names and motifs are cut in, never
raised (`unsupported_feature` "cut into the face, not raised"); `border` (the top rail: motif or name) and
`base_front` (the bottom rail: a line of text) lie on the rails outside the window, and a cut of at most
1.5 mm leaves 1.5 mm of lip over the rabbet. Hardware `acrylic_4x6` ×1.

**`keycap_mx@1` (family `keycap`, Kunji).** A 1u DSA-like cap: 18.16 mm at the rim, straight for 1.2 mm,
tapering to a 12.7 mm top 7.4 mm up with a 0.3 mm spherical dish; 1.5 mm walls square to the slope, a
2.2 mm top. A 5.5 mm round stem carries KeyV2's Cherry MX cross, (4.03 + s) × (1.25 + s/3) and
(1.15 + s/3) × (4.23 + s/3) with `stem_slop_mm` s (0.3–0.5, 0.4 for FDM), 4 mm deep (at least the 3.6 mm
the switch needs). Printed stem down as modelled. The `top` anchor sits at the bottom of the dish: a
photo relief (an emblem) or 1–3 letters, raised or cut at most 0.6 mm; letters keep 1 mm strokes, so three
wide letters ("Esc") are refused as too fine. The MX stem is about 0.5 mm thick beyond the cross's tips by
the standard, under FDM's 0.8 mm: the karigar's note recommends resin and a test fit on a switch.

## Pipeline (`build_design`)

`aakar_geometry/pipeline.py`: validate the request against `design.generate.v1.json` (and the
spec against `design-spec.v1.json`) → resolve `id@version` → check family / features / style /
material → validate params → `Template.validate_content(params, features)` → progress
`understanding` → progress `sculpting` → CAD body + features (with a timeout,
`AAKAR_BUILD_TIMEOUT_S`, default 120 s) → refuse an empty result (`build_error`, nothing is
exported) → export + store → progress `checking` → inspect (in-process by default, HTTP when
`AAKAR_INSPECT_URL` is set; the STL is then always exported) → assemble and validate the
`design.completed` payload, whose `hardware` is `Template.hardware_for(params)`. Failures return a
`design.failed` payload with the right `code`; nothing raises out of `build_design`.

* `understanding`/`sculpting`/`checking` progress envelopes (per `envelope.v1.json`, increasing
  `sequence`) are POSTed to `callback_url` when present. Callback failures are logged, never fatal.
  `pricing` and `ready` are emitted by the API service.
* A design whose checks fail (thin wall, tipping) still completes with `printability.passed=false`
  so the agent and the stability card see the details; only a non-manifold result is a
  `not_printable` failure.
* `outputs` `usdz` and `thumb` are accepted but not produced in Phase 0 (logged and skipped).

## Features (Chhaap)

Personal content on a template's anchors (plan §2), `aakar_geometry/features/`: names (Naam),
motifs (Buti), photo reliefs (Chhavi) and customers' own forms (Roop). It runs inside
`Template.build(params, features, fetcher)` after the body:

1. **validate** (`features/validate.py`, also called up front by the pipeline): the type is in the
   template's `features_supported` and the anchor's `accepts`; photos, names and motifs go on surface
   anchors, a customer's own form (`hero_mesh`) only on volume anchors. What one anchor holds: at most
   one photo or form, at most one name and at most one motif; a name and a motif may share an anchor
   (they sit side by side), but a photo never shares its anchor with a name or a motif (refused as
   crowded: "The Face is too crowded for a photo and a name together; put the name on another spot").
   `relief_mm` / `depth_mm` within the anchor's `max_relief_mm` (lithophane mode excepted); names and
   motifs only with `planar` projection; text within the family's `max_text_chars`, in one launch script
   and matching `script` / `font` when given, `height_mm` within the anchor's `max_text_height_mm`;
   `motif_id` in the library and `scale` between its `min_scale` and 1. Contract defaults (and a name's
   detected `script`) are filled in and echoed in the completed spec. Then the template's own
   `validate_content(params, features)` (skin under cut-ins, the raw print's model and size).
2. **prepare names and motifs** (`features.prepare_marks`, no CAD): shape, fit and apply the stroke rule
   to every name and motif at the real anchor size, so every refusal comes before the first boolean.
3. **fetch** photos and model files (`features/fetch.py`): `ContentFetcher.fetch(source) -> bytes`.
   `HttpFetcher` streams `content_source.url` with timeouts and caps (photos 15 MB, models 200 MB) and
   refuses HTML pages; `LocalFileFetcher` reads `file://` URLs or a URL's file name inside a folder
   (tests, CLI `--content-dir`). The format comes from `source.format`, then the URL suffix, then magic
   bytes. Names and motifs fetch nothing.
4. **apply**, in spec order. Names and motifs: the fitted outline is extruded (manifold3d
   `CrossSection` → `extrude`, milliseconds for a name; OCCT's `cad.prism` took about a second) and
   raised (union, starting 0.5 mm inside the body) or cut (difference, from `depth_mm` below the skin to
   0.5 mm above it) in the anchor frame (`features/outlines.py`); the cutter is built in the same frame as
   the raised letters, so cut-in names read left to right from outside without any mirroring.
   `relief_image` (Chhavi, `features/relief_image.py`): Pillow decode (HEIC through `pi-heif`,
   decode-only; `pillow-heif`, whose wheels are GPLv2, is a dev dependency that only encodes the test
   photo) → luminance × alpha → fitted into `size_mm − 2·bleed_mm` at ≈5 px/mm → smoothed → a
   watertight heightfield solid (`features/heightfield.py`) in the anchor frame, bright pixels highest
   (emboss) or cut deepest (deboss, mirrored so it reads right from outside); `lithophane` mode hands
   the heightmap to `Template.lithophane`. `hero_mesh` (Roop, `features/hero_mesh.py`): load with the
   inspect loaders → repair → decimate above 300k faces → orient (`lay_flat`, `yaw_deg`) → scale
   (`contain` into `bounds_mm`, or `longest` to `longest_mm`, never clamped) → seat on the anchor's
   bottom plane; alone when the body is empty (`raw_print`).
5. **booleans** (`features/booleans.py`): trimesh with the `manifold` engine; every result must be a
   closed, consistently wound volume. A boolean that fails is the content's problem, reported as
   `content_unusable` with a customer-safe message (photo, name, motif, model file; never mesh or STL).

Content on the underside (a raised photo or name on a keychain's back) can reach below the bed;
`build` lifts the finished piece back onto Z = 0, so it rests on the raised content. The completed
payload's karigar's note ends with a sentence naming the lettering and motifs
(`Template.karigar_note_for`: “Asha” stands 0.6 mm proud on the back; the Lotus motif is cut 1 mm into
the face).

### Naam: names in seven scripts (`features/emboss_text.py`)

* **Fonts**: one static Noto Sans **Bold** per launch script (ADR-0003: Latin, Devanagari, Telugu,
  Tamil, Kannada, Bengali, Gujarati), SIL Open Font License 1.1, bundled unmodified as package data in
  `aakar_geometry/fonts/` (wheel and image). Sources, versions and checksums:
  [`aakar_geometry/fonts/README.md`](aakar_geometry/fonts/README.md); licence text:
  `aakar_geometry/fonts/OFL.txt`. `font` may only name this lettering (`Noto Sans` or `Noto Sans
  <Script>`); anything else is `unsupported_feature`.
* **Script**: `script` picks the font; without it the script is detected from the letters' Unicode
  blocks. A name uses one script: letters from two scripts ("Asha आशा"), letters from another script
  (Greek, Arabic, ...), symbols no font has (❤), line breaks, or a `script` that does not match the
  letters are refused with a customer-safe `invalid_spec`. Digits, spaces and punctuation a script's
  font lacks (Gujarati and Bengali have no ASCII digits, Devanagari no `&`) come from Noto Sans.
* **Shaping** with uharfbuzz (Apache-2.0; bundles HarfBuzz, Old MIT), direction, script and language
  set explicitly so the result never depends on the server's locale: conjuncts, half forms, vowel signs
  and reordering happen before any outline exists; ZWJ/ZWNJ steer half forms as in any text editor.
  HarfBuzz draws each glyph at its shaped position into a fontTools pen (MIT) that flattens the curves
  to 1/2000 em; each glyph is filled with the nonzero rule and the glyphs are unioned. The fill is
  exact for any winding: the rings are noded into an arrangement and each face is kept by its winding
  number (nonzero or even-odd), so overlapping contours, holes and self-intersections come out right.
  No FreeType is needed. Golden tests pin each script's glyphs (e.g. नमस्ते → na, ma, half sa, ta, e-matra).
* **Fit**: `height_mm` is the height of the lettering as printed, from its lowest to its highest point
  (descenders and vowel signs above or below count). Without it the name is the largest that fits the
  printable area `size_mm − 2·bleed_mm` (or its share beside a motif) and `max_text_height_mm`,
  centred. A name that would be under 4 mm tall (the contract's smallest `height_mm`) is refused as too
  long; a `height_mm` that does not fit is refused on that key.
* **Projection**: planar only. No anchor is curved yet, so `cylindrical` and `conformal` are refused
  with `unsupported_feature` ("Lettering and motifs that wrap around a curved surface are not available
  yet").

### Buti: the motif library (`features/motif.py`)

`packages/design-tokens/motifs/` (`AAKAR_MOTIFS_DIR` in the image, else the repo-relative folder):
`index.json` lists each motif (`id`, a plain `label`, `file`, `tags`, `min_scale`) and each SVG is
exactly one `<path>` (closed subpaths, `fill-rule` nonzero or evenodd, no transforms), original
artwork drawn for Aakar: `paisley`, `lotus`, `star_rangoli`, `jaali_lattice`, `warli_dancer`. Paths are
parsed by fontTools' `svgLib` (every path command, arcs included) into the same pen and fill as the
lettering, and flipped upright (SVG's y runs down).

* **Fit and scale**: at `scale` 1 the motif is as large as fits the printable area (contain) and
  centred; `scale` shrinks it. Above 1 it would run off the edge and is refused (never cropped); below
  the motif's `min_scale` too. `min_scale` is the smallest scale at which the motif's strokes and
  openings stay at least 0.8 mm when it fills the library's 30 mm reference square (a test holds every
  motif to it).
* **Beside a name**: on one anchor the motif takes a square cell at the left end (as tall as the area,
  at most 40 % of its width) and the name the rest after a 2 mm gap (`features/placement.py`).
* An unknown `motif_id` is `invalid_spec` ("We don't have a motif called “tiger”; ..."); a missing
  library is a deployment fault (`build_error`, "Motifs are not available right now").
* **Adding a motif**: draw it as one closed, filled path (convert strokes to outlines, flatten
  transforms and groups; about a 100 × 100 viewBox) with strokes and openings at least 10 % of its size
  so it still prints on small spots such as the Jharokha's 8.6 mm back band; save it as
  `<id>.svg`, add its row to `index.json` with the smallest `min_scale` that keeps it printable, and run
  `uv run pytest tests/test_motifs.py`: it checks the file format, the index and printability at scale
  1 and at `min_scale` on the 30 mm reference.

### The stroke rule (`outlines.stroke_check`)

A 0.4 mm nozzle cannot print a stroke thinner than the template's `min_feature_mm` (0.8 mm; 0.8 when a
template does not say). The fitted outline is opened: eroded by half that width and grown back with
mitred corners, which restores sharp points and corners exactly, so what disappears is the parts too
thin to print. The outline is refused when a separate piece (a dot, a vowel sign) disappears entirely,
or when more than 5 % of its area does: "These letters would be too thin to print at this size; try
fewer letters or a larger piece" (`param_out_of_range` on `features[i].text`, and `height_mm` when it
was given). Motifs apply the same rule to their openings as well (a jaali's holes must not close up):
"This motif would be too fine to print at this size; make it larger or put it on a larger piece".
In practice a name needs about 5.5–6.5 mm of lettering height in Latin, Devanagari, Telugu, Tamil,
Bengali and Gujarati, and about 9–10 mm in Kannada, whose strokes are finer.

**Families** (`aakar_geometry/families.py`) load `packages/design-tokens/families.json`
(`AAKAR_FAMILIES_FILE` in the image; a built-in copy of every family that has a template otherwise),
validated against `template-family.v1.json`. The registry refuses a template whose family is unknown;
`Template.materials()` applies the family's `material_rules` (`allowed`, `heat_safe_only`,
`excluded_finish_classes`; a finish the rules leave out is refused by name, "… comes in Basic White
only"); the content slot (`accepts`, `anchors`, `max_text_chars`) bounds the features;
`size_envelope(family)` is the longest-side range that the templates' tests hold them to and that
`raw_print` enforces; `default_hardware(family)` is what the family promises the customer (a test holds
every template to it).

**Recipe hook points** for a new carrier (`templates/<template_id>.py`, copied from
`keychain_tag.py` or `jharokha_phone_stand.py`):

| Hook | What it does |
|---|---|
| class attributes | `id`, `version`, `family`, `name`, `description`, `environment`, `params` (with `group`), `anchors` (`kind`, `size_mm`/`bounds_mm`, `bleed_mm`, `accepts`, `max_relief_mm` ≥ 1.2 where names go, `max_text_height_mm`), `constraints`, `features_supported` ⊆ the family's `accepts` |
| `hardware` / `hardware_for(params)` | the bought-in parts cut for (`[{sku, qty}]`); override `hardware_for` when a parameter changes quantities (magnets) |
| `min_feature_mm` | smallest printable stroke (0.8 on a 0.4 mm nozzle) |
| `validate_combination(params)` | coupled parameter limits (a phone slot, a printable area, tipping) |
| `validate_content(params, features)` | limits coupling parameters and content, before any CAD (skin under a cut-in, a raw print's model) |
| `build_body(params)` | watertight mesh in mm, Z up, on Z = 0, centred in X/Y (empty only for `raw_print`) |
| `anchor_frame(anchor_id, params)` | a right-handed `AnchorFrame` (u = the viewer's right, v = up the picture, normal out of the body; origin on the skin, or the bottom centre of a volume) |
| `lithophane(...)` | only for templates whose plate *is* the photo (`lithophane_plate`: the heightmap becomes the plate) |
| `build(...)` override | a rule on the finished piece after the content is fused (`plinth_round`: stability with the customer's form) |
| `karigar_note_for(...)` override | a note that depends on the content (`plinth_round`: a line on the form only when there is one) |
| `karigar_note(params)` | one or two lines in the craft register (the pipeline's `karigar_note_for(params, features)` appends the names and motifs set into the piece) |

Then register it in `templates/__init__.py` and add `tests/test_templates_<id>.py` using the shared
checks in `tests/carrier_checks.py` (descriptor vs family, envelope, frames on the skin, holes).

## Exports and storage

* **GLB**: scaled mm → metres (0.001) and rotated Z-up → Y-up (the stand's front faces +Z),
  neutral grey PBR material embedded. For three.js / `<model-viewer>`.
* **STL**: binary, mm. **3MF**: trimesh's writer (`unit="millimeter"`), with a minimal
  `[Content_Types].xml` / `_rels/.rels` / `3D/3dmodel.model` fallback writer.
* Keys `designs/{design_id}/v{n}/model.{glb,3mf,stl}`. `AAKAR_STORAGE=local` writes under
  `AAKAR_ASSET_DIR` and the API serves `GET /assets/{key}` with `AAKAR_PUBLIC_URL` as base;
  `AAKAR_STORAGE=s3` uses boto3 (`AAKAR_S3_BUCKET`, `AAKAR_S3_ENDPOINT` for MinIO,
  `AAKAR_S3_PUBLIC_URL`, standard `AWS_*` credentials; presigned URLs when no public URL).

## Environment

| Variable | Default | Purpose |
|---|---|---|
| `AAKAR_CONTRACTS_DIR` | `../../packages/contracts/schemas` | JSON Schemas (Dockerfile: `/contracts/schemas`) |
| `AAKAR_MATERIALS_FILE` | `../../packages/design-tokens/materials.json` | Material ids for descriptors (falls back to the built-in six) |
| `AAKAR_FAMILIES_FILE` | `../../packages/design-tokens/families.json` | Families (Avatars): registry, material rules, content slots, envelopes (falls back to the built-in launch rows) |
| `AAKAR_MOTIFS_DIR` | `../../packages/design-tokens/motifs` | Motif library (Buti): `index.json` and one SVG per motif (Dockerfile: `/design-tokens/motifs`); no built-in copy, a missing library fails motif builds with `build_error` |
| `AAKAR_STORAGE` | `local` | `local` or `s3` |
| `AAKAR_ASSET_DIR` | `./.aakar-assets` | Local asset root |
| `AAKAR_PUBLIC_URL` | `http://localhost:8081` | URL base for local assets |
| `AAKAR_CORS_ORIGINS` | `http://localhost:3000` | Comma-separated browser origins allowed to fetch `/assets` (local storage only) |
| `AAKAR_S3_BUCKET`, `AAKAR_S3_ENDPOINT`, `AAKAR_S3_PUBLIC_URL`, `AWS_*` | — | S3 / MinIO |
| `AAKAR_INSPECT_URL` | unset (in-process) | Remote inspect service, e.g. `http://inspect:8082` |
| `AAKAR_AMQP_URL` | `amqp://aakar:aakar@localhost:5672/` | RabbitMQ for the worker |
| `AAKAR_BUILD_TIMEOUT_S` | `120` | CAD step timeout → `timeout` failure |

## Run

```sh
cd services/geometry
uv sync                                   # installs build123d (+ ../inspect as an editable path dep)
uv run pytest                             # tests
uv run aakar-geometry templates           # descriptors as JSON
uv run aakar-geometry build ../../packages/contracts/examples/jharokha-phone-stand.spec.json --out out/slice
uv run aakar-geometry serve               # http://localhost:8081
uv run aakar-geometry worker              # consumes geometry.design.generate
curl -s localhost:8081/v1/templates | jq '.[0].id'
curl -s -X POST localhost:8081/v1/build -H 'content-type: application/json' \
  -d '{"job_id":"6f1c1a2e-9d0b-4a8e-8c1e-2f3d4e5f6a7b","design_id":"0b8e7c6d-5a4b-4c3d-9e2f-1a0b9c8d7e6f","version_no":1,"spec":'"$(cat ../../packages/contracts/examples/jharokha-phone-stand.spec.json)"'}'
```

`build` writes `model.glb`, `model.3mf`, `model.stl` and `result.json` into `--out` and prints
one line: template, bounds, volume, triangles, passed, grams (spec material density, PLA 1.24),
print time and hardware.

With customer content, `--content-dir` serves the photos and model files named in the spec's
`content_source` URLs (matched by file name) instead of fetching them over HTTP:

```sh
# a keychain spec whose relief_image source URL ends in photo.png, with photo.png in ./content
uv run aakar-geometry build keychain.spec.json --content-dir ./content --out out/keychain
```

The committed `raw-print.spec.json` and `keychain-photo.spec.json` build as they are
(`--content-dir` holding a model file or a photo named like the source URL). The keychain carries a
photo raised on its face and the name "Asha" raised on its back; the name needs nothing fetched and
the piece passes the printability checks:

```sh
mkdir -p /tmp/content && cp any-photo.png /tmp/content/3f2b6a1e-8c4d-4e5f-9a0b-1c2d3e4f5a6b.png
uv run aakar-geometry build ../../packages/contracts/examples/keychain-photo.spec.json \
  --content-dir /tmp/content --out out/keychain
```

## Worker

`aakar_geometry/worker.py` (pika): declares the durable topic exchange `aakar.design` and the
durable queue `geometry.design.generate` bound with `design.generate`; for each envelope runs
`build_design` and publishes `design.progress` / `design.completed` / `design.failed` envelopes
to the same exchange with routing key = type. Acks after the result is published; nacks without
requeue when the message is not a valid `design.generate` envelope. Invalid specs inside a valid
envelope are answered with `design.failed` (so the API learns about them) and acked.

## Heuristic vs real

* Real: the CAD (OCCT), watertightness, bounds, exports, photo reliefs, names in the seven launch
  scripts (HarfBuzz shaping), library motifs and customer forms with manifold booleans, the stroke
  rule, the inspect checks listed in `services/inspect/README.md`.
* Heuristic: the print estimate (see inspect README), `supports_required`, the stroke rule's 5 %
  thin-area allowance (calibrated on the seven scripts; a native-reader review per script is still due
  before launch, ADR-0003).
* Not yet: lettering and motifs on curved anchors (`cylindrical` / `conformal` projection: no anchor is
  curved yet), motif bands and tiling, cut-out silhouettes, style variants, USDZ and thumbnails
  (Phase 1–2), overhang and load checks (Phase 2–3).
