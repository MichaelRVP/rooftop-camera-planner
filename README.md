# Rooftop Camera Planner

Rooftop Camera Planner helps a player find and keep a low-mouse-travel camera
layout for all nine OSRS rooftop Agility courses.

## What it does

- Detects the current rooftop course and its ordered obstacles.
- Highlights live obstacle clickboxes and the expected next obstacle.
- Records each obstacle's real screen-space clickbox during complete laps.
- Scores the cyclic route by maximizing consecutive clickbox overlaps, then
  minimizing the remaining gaps and cursor travel.
- Learns the best yaw, pitch, and zoom the player has shown it for each course.
- Draws the best learned clickboxes as static ghost markers so the cursor can
  be positioned before the next obstacle becomes clickable.
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

Enable `Rooftop Camera Planner`, enter a rooftop course, and hold the camera
still for two complete laps. A lap includes the final obstacle through the
first obstacle of the next lap. Follow the next-experiment prompt for two laps
at a time; the best marker layout and camera profile are retained locally.
