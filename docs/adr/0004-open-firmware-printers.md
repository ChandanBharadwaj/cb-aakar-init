# ADR-0004: Open-firmware printers for the studio farm

- Status: Proposed
- Date: 2026-09-26
- Deciders: Chandan Bharadwaj (product owner)

## Context
The farm agent needs live progress, layer counts and camera frames to drive order tracking and the WhatsApp time-lapse. Closed printer ecosystems expose this partially or through unofficial APIs.

## Decision
Recommend: buy printers running **Klipper with Moonraker** (or OctoPrint-compatible firmware) for launch; integrate closed ecosystems only through their documented local bridges. Count and models for launch to be decided with the studio ops owner.

## Consequences
Farm-agent development targets one well-documented WebSocket API. Printer purchasing is constrained.
