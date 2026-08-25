# Rooftop Camera Planner

Rooftop Camera Planner helps a player find and keep a low-mouse-travel camera
layout for all nine OSRS rooftop Agility courses.

## What it does

- Detects the current rooftop course and its ordered obstacles.
- Highlights live obstacle clickboxes and the expected next obstacle.
- Extracts a guaranteed-clickable inner rectangle from each obstacle's real,
  possibly irregular screen-space click shape during complete laps.
- Scores the cyclic route by maximizing consecutive clickbox overlaps, shared
  overlap area, then minimizing the remaining gaps and cursor travel.
- Persists every tested yaw, pitch, and zoom candidate for each course.
- Finishes initial calibration in six valid laps, then offers an optional
  two-lap refinement pass without discarding prior evidence.
- Learns the active client's reachable pitch and zoom limits when repeated
  adjustment input produces no movement, then replans without user setup.
- Rejects yaw targets that repeated camera drags cannot reach, accounting for
  course-forced camera behavior and standard versus expanded camera limits.
- Draws the best learned clickboxes as static ghost markers only while the
  camera matches their learned view, so stale markers cannot misdirect clicks.
- Pauses the M-markers during each room-to-roof camera transition, waits for
  the camera to settle, then applies one inexpensive per-lap alignment correction.
- Excludes incidental actions such as collecting Marks of Grace from route
  mouse-travel evidence.
- Displays simple rotate, tilt, and zoom guidance to return to that view.
- Keeps every learned profile local in RuneLite configuration.

## What it never does

The plugin never moves the camera, moves the mouse, clicks, selects menu
entries, or sends game input. The player remains in control of every action.

## Development client

Close normal RuneLite and run:

```powershell
.\gradlew.bat run
```

Enable `Rooftop Camera Planner`, enter a rooftop course, and follow the camera
guidance for each valid lap. The final obstacle ends the current lap immediately,
so the next camera instruction appears before obstacle one. Falls, skipped
obstacles, missing click geometry, incidental actions, and camera movement do
not consume calibration laps. Results survive restarts; once six valid laps are
complete, align with the winning camera and the saved click markers appear.
