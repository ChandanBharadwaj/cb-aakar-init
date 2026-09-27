# ADR-0003: Launch embossing scripts

- Status: Accepted (decided by the product owner on 2026-09-27; the original recommendation of three scripts was not taken)
- Date: 2026-09-27
- Deciders: Chandan Bharadwaj (product owner)

## Context
Embossed text is the most personal feature and the most error-prone across Indic scripts (conjuncts, vowel signs, reordering). Every script needs a shaping font, golden-image tests and native-speaker review. The engineering recommendation was to launch with three scripts and add four in Phase 2.

## Decision
Text embossing supports **all seven scripts at launch (Phase 1)**: Latin, Devanagari, Telugu, Tamil, Kannada, Bengali and Gujarati. Shaping is done with HarfBuzz using the Noto Sans family for each script; the Design Spec already enumerates these seven in `emboss_text.script`.

## Consequences
- The Phase 1 embossing workstream grows from three to seven scripts: seven fonts to license-check and bundle, a golden-image test set per script, and a native-speaker review for each before launch.
- Minimum stroke width rules (0.8 mm at a 0.4 mm nozzle) must be validated per script; Bengali and Gujarati have finer strokes at small sizes and may need a larger minimum text height.
- Phase 2 no longer carries an "add four scripts" item; Phase 4 keeps only the regional-language UI.
