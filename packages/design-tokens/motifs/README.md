# Buti motif library

Motifs a customer can set into a piece (the `motif` feature, "Buti" in the storefront): original
artwork drawn for Aakar, no third-party artwork. The geometry service reads this folder
(`services/geometry/aakar_geometry/features/motif.py`; `AAKAR_MOTIFS_DIR` in its image, which copies
the folder to `/design-tokens/motifs`).

| id | Label | Fill | `min_scale` |
|---|---|---|---|
| `paisley` | Paisley | nonzero: a kalka with a border ring and an eye | 0.3 |
| `lotus` | Lotus | nonzero: five petals on a water line, three with open veins | 0.25 |
| `star_rangoli` | Rangoli star | evenodd: an eight-point star, a ring opening and a centre dot (arcs) | 0.3 |
| `jaali_lattice` | Jaali lattice | evenodd: a framed screen with thirteen diamond openings | 0.3 |
| `warli_dancer` | Warli dancer | nonzero: head, two triangles, one arm raised, legs in a step | 0.35 |
| `action_burst` | Action burst | evenodd: a fourteen-point burst with a ten-point burst opening in the middle | 0.35 |
| `speech_bubble` | Speech bubble | evenodd: an oval ring with a tail to the lower left (arcs) | 0.3 |
| `thought_bubble` | Thought bubble | evenodd: a nine-scallop cloud with an oval opening and two trailing bubbles (arcs) | 0.3 |
| `lightning_bolt` | Lightning bolt | nonzero: a two-step bolt | 0.25 |
| `domino_mask` | Domino mask | evenodd: a plain domino mask with two almond eye openings | 0.25 |
| `hero_cape` | Hero cape | nonzero: a cape with a raised collar and a scalloped hem | 0.25 |

## Packs

A tag shared by several motifs names a **pack** that a Duniya experience can list in its `motif_pack`
(`packages/design-tokens/experiences.json`): the storefront offers the pack's motifs first, in library order.
`comic_bursts` is Katha's comic artwork (the last six rows). Tags are snake_case words, like ids.

Every motif is original artwork in generic shapes: comic motifs are bursts, bubbles, bolts, a plain mask and a
cape, never a character, logo, costume, emblem or lettering from any publisher (plan §8: the customer's own hero
is the content).

## Format

* `index.json`: `reference_mm` (30), `min_stroke_mm` (0.8) and one row per motif with `id`
  (snake_case, the `motif_id` in a Design Spec, never renamed), `label` (a plain name, no brand
  codename), `file`, `tags` (snake_case; a shared tag names a pack) and `min_scale`.
* `<id>.svg`: exactly one `<path>` in the SVG namespace inside a `viewBox` (100 × 100 here): any number
  of closed subpaths (each ends with `Z`), `fill-rule` `nonzero` or `evenodd`, no `transform`, no
  strokes, groups, `<use>`, `<image>` or text. Any SVG path command works, arcs included.

At `scale` 1 a motif is as large as fits the spot it is set into, and centred; smaller scales shrink
it. `min_scale` is the smallest scale at which every stroke and every opening is still at least
0.8 mm wide (what a 0.4 mm nozzle prints) when the motif fills the 30 mm reference square, rounded up
with a little margin; the geometry service refuses smaller scales and also checks the real size on the
real spot.

## Adding a motif

1. Draw it as one filled outline: convert strokes to outlines, flatten groups and transforms, and keep
   strokes and openings at least 10 % of the motif's size so it prints on small spots (the Jharokha
   stand's back band is 8.6 mm tall). For a wide motif, keep them 10 % of its height: the band fits
   the motif's height.
2. Save it as `<id>.svg` here and add its row to `index.json` with the smallest printable `min_scale`.
3. Run `cd services/geometry && uv run pytest tests/test_motifs.py`: it checks the format, that the
   index and the files match, and printability at scale 1 and at `min_scale` on the reference square.
