# Rooftop Camera Planner

Rooftop Camera Planner helps a player find and keep a low-mouse-travel camera
layout for all nine OSRS rooftop Agility courses.

## What it does

- Detects the current rooftop course and its ordered obstacles.
- Highlights live obstacle clickboxes and the expected next obstacle.
- Scores the current layout using visible clickboxes, target size, and the
  cursor distance between consecutive obstacles.
- Learns the best yaw, pitch, and zoom the player has shown it for each course.
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

Enable `Rooftop Camera Planner`, enter a rooftop course, and adjust the camera.
The layout score and saved target update as better arrangements are observed.
