# Lettering fonts (Naam)

The seven launch scripts of ADR-0003, one static Noto Sans **Bold** font each (a bold weight keeps
strokes printable at small sizes). They ship inside the `aakar_geometry` package (wheel and image)
and are read by `aakar_geometry/features/emboss_text.py`; nothing else loads them.

| Script (`emboss_text.script`) | File | Version | SHA-256 |
|---|---|---|---|
| `latin` | `NotoSans-Bold.ttf` | 2.015 | `87cb2d84472a7d66da659ee47b6cdb9552326e8c128245231f191b6ac72529d9` |
| `devanagari` | `NotoSansDevanagari-Bold.ttf` | 2.007 | `ff2f76a23aad41e0608c2d7dbc4bacd247ff3bec78f0ec2a8fb106b561636e58` |
| `telugu` | `NotoSansTelugu-Bold.ttf` | 2.005 | `44393b05a863057cc2cbfe86623fc20b906b00cf19a2ae9304cdd0f3608889db` |
| `tamil` | `NotoSansTamil-Bold.ttf` | 2.004 | `04a472e49fa83b387976756554fe179de631a950d79f652ebba14a39a44f5d71` |
| `kannada` | `NotoSansKannada-Bold.ttf` | 2.006 | `e989c72f6fec5a0b3366af5f90e3ce8b2b8a350fc5286c6374f0353937e513f5` |
| `bengali` | `NotoSansBengali-Bold.ttf` | 3.011 | `2fe8d779e0f19576d1fc11e39f9154b4326a17b693215017dd1f5a8898c3f2c3` |
| `gujarati` | `NotoSansGujarati-Bold.ttf` | 2.106 | `3ce0cf6e1d0bfe2ef5823772eeb2f25da28ec0c2c1d99c0fce529025723e61a1` |

## Source

Unmodified unhinted TrueType builds from the Noto project's release site repository,
[notofonts/notofonts.github.io](https://github.com/notofonts/notofonts.github.io) at commit
`f145d86c53996717bc4c25d4602eb9294e43dccc` (fetched 27 Sep 2026), path
`fonts/<Family>/unhinted/ttf/<Family>-Bold.ttf`, e.g.

```
https://raw.githubusercontent.com/notofonts/notofonts.github.io/f145d86c53996717bc4c25d4602eb9294e43dccc/fonts/NotoSansDevanagari/unhinted/ttf/NotoSansDevanagari-Bold.ttf
```

Hinting only matters for screen rasterisation, so the smaller unhinted builds are used. The
per-script sources are the `notofonts/latin-greek-cyrillic`, `devanagari`, `telugu`, `tamil`,
`kannada`, `bengali` and `gujarati` repositories.

## Licence

SIL Open Font License 1.1, see [`OFL.txt`](OFL.txt) (the seven upstream copyright lines followed by
the licence text). The fonts are bundled unmodified with the service and never sold on their own;
the printed lettering is a use of the fonts, not a copy of the Font Software.

## Updating or adding a font

Download the new file from the same repository (pin the commit), replace it here, update the table
above (version from the `name` table ID 5, SHA-256), keep `OFL.txt` in step, then run the golden
text tests (`uv run pytest tests/test_emboss_text.py`): glyph counts and shaped widths are pinned per
script, so a font change shows up as a test diff to review with a native reader of the script.
