# ADR-0004: Studio printers: Klipper/Moonraker and Bambu Lab, two farm-agent bridges

- Status: Deferred (2026-09-27, later the same day: printing is outsourced for now, so no farm agent is built; the two-bridge design below stands for when a studio farm exists)
- Date: 2026-09-27
- Deciders: Chandan Bharadwaj (product owner)

## Context
The farm agent needs live progress, layer counts and camera frames to drive order tracking and the WhatsApp time-lapse. Open-firmware printers expose this through Moonraker's documented WebSocket API. Bambu Lab printers are fast and reliable but closed; their local MQTT interface exposes progress, layer and camera with less stability across firmware updates.

## Decision
The studio runs **both** families and the farm agent ships **two bridges** from Phase 1:
- `MoonrakerBridge` for Klipper printers (WebSocket subscription: state, progress, current layer, camera snapshot URL).
- `BambuBridge` for Bambu Lab printers (local-network MQTT in LAN mode with the access code; camera via the printer's local stream; G-code/3MF upload over FTPS).

Both implement one `PrinterBridge` interface (connect, list jobs, start job, subscribe progress, snapshot) so the fulfilment module never sees the difference.

## Consequences
- **Deferred:** until Aakar prints in-house, orders are fulfilled by an outsourced printer. The management portal gives staff a per-order **print pack** (3MF, STL, print sheet with material, quantity and finish) to hand over, and stages are advanced manually. `services/farm-agent` stays a placeholder.
- Roughly double the Phase 1 farm-agent integration and test work; each bridge gets a fake-printer test double.
- Slicing profiles are maintained per printer family (PrusaSlicer/OrcaSlicer for Klipper, Bambu Studio or OrcaSlicer for Bambu).
- Bambu firmware updates can break the MQTT contract; pin firmware in the studio and keep the bridge behind a feature flag per printer.
