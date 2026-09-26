# ADR-0003: Launch embossing scripts

- Status: Proposed
- Date: 2026-09-26
- Deciders: Chandan Bharadwaj (product owner)

## Context
Embossed text is the most personal feature and the most error-prone across Indic scripts (conjuncts, vowel signs). Every additional script needs a shaping font, golden-image tests and native-speaker review.

## Decision
Recommend: launch (Phase 1) with **Latin, Devanagari and Telugu** using Noto fonts shaped by HarfBuzz. Add Tamil, Kannada, Bengali and Gujarati in Phase 2 once the pipeline has shipped real orders.

## Consequences
Prompts in other scripts are accepted for design intent but text embossing offers only the launch scripts until Phase 2.
