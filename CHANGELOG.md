# Changelog

## 0.1.3 - 2026-08-26

### Route correctness and documentation

- Modelled Falador's two valid ledges as alternate object IDs for one logical route step, preventing legitimate laps from being rejected as incomplete.
- Kept calibrated screen markers fixed for the full lap so players can pre-position the mouse before each obstacle becomes clickable.
- Prevented live scene loading and rooftop transitions from moving or spreading predictive markers after the first click.
- Improved reachable-camera exploration and recovery when the game forces a camera shift between roofs.
- Added exact route-count, contiguous-cycle, complete-lap, and alternate-path regression tests across all nine supported courses.
- Expanded the documentation with visual workflow diagrams, the real constrained-route objective, calibration behavior, privacy boundaries, troubleshooting, and release-proof guidance.

## 0.1.2 - 2026-08-25

### Compatibility and release polish

- Rebuilt and passed the full test suite against RuneLite `latest.release`
  following the August 25 Old School update.
- Confirmed the supported rooftop object mappings compile against the current
  RuneLite API; no game-data remap was required.
- Added a Plugin Hub-ready icon, clearer discovery tags, and a human-readable
  description.
- Rewrote the README around what players will actually see: local calibration,
  camera limits, safe marker behavior, and the plugin's no-input-automation
  boundary.

## 0.1.1 - 2026-08-25

- Stabilized marker calibration and added resilient camera-settle handling.
