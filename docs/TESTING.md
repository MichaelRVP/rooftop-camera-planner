# Testing and release proof

The suite is organized around failures a player can actually notice.

| Area | What is proved |
| --- | --- |
| Course mapping | Nine regions, exact logical step counts, unique object IDs |
| Alternate routes | Either Falador ledge completes the same logical route |
| Lap progression | Ordered clicks complete; skipped steps invalidate evidence |
| Camera alignment | Wrapped yaw, pitch, and zoom tolerances behave predictably |
| Reachability | Repeated movement at a limit becomes a learned client bound |
| Forced shifts | Game-driven camera changes do not poison a candidate |
| Settling | Markers remain hidden while camera geometry is moving |
| Marker safety | Layout format, canvas, scaling, and verification gates hold |
| Scoring | Center travel, gap, overlap, and cyclic route calculations |
| Optimization | Every chosen click point remains inside its legal rectangle |
| Search history | Candidate evidence survives serialization and parsing |
| Prediction | Compound candidates require meaningful estimated improvement |
| Plugin lifecycle | Course state, profile loading, and overlay transitions |

## Local verification

```powershell
.\gradlew.bat clean test build
git diff --check
```

## Manual acceptance pass

1. Start with a clean profile on one supported course.
2. Confirm six clean laps complete calibration.
3. Confirm a camera adjustment prevents that lap from becoming evidence.
4. Confirm a Mark of Grace does not become a route obstacle.
5. Confirm markers stay hidden while the camera is not aligned.
6. Confirm markers return at the verified camera position.
7. Confirm the next marker advances in route order.
8. Confirm **Refine camera** adds two laps without deleting history.
9. Confirm a client restart restores the saved profile locally.
10. On Falador, complete separate laps through both alternate ledges.

Automated tests prove deterministic code behavior. The manual pass proves current game geometry and RuneLite event behavior still match those assumptions.
