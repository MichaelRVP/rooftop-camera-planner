# The optimizer

This is the optimization that is actually implemented. It is not a claim that one camera angle is globally perfect for every player. The plugin searches the camera space available on the current client and chooses the best view it can verify from measured laps.

## 1. Route representation

A course is an ordered cycle of logical obstacle steps: `S = (s0, s1, ... sn-1)`.

A logical step may contain more than one RuneLite object ID when the game lets the player use either object at that point. Those IDs map to the same route index. This prevents an alternate object from looking like a skipped or duplicated obstacle.

## 2. Marker geometry

For a valid lap and camera state `c`, RuneLite supplies one clickbox rectangle for each logical step: `R(c) = (R0, R1, ... Rn-1)`.

The rectangles are normalized to safe inner marker regions before persistence. Saved layouts include their original canvas dimensions and are scaled only when the aspect and verification rules allow it.

## 3. Three travel measurements

### Center travel

The baseline joins rectangle centers:

```text
C = sum ||center(Ri+1) - center(Ri)||2
```

This overstates required travel because a player does not have to click the center.

### Marker gap

For each consecutive pair, the gap is zero when the rectangles overlap. Otherwise it is the Euclidean distance between their closest edges:

```text
G = sum distance(Ri, Ri+1)
```

### Attainable travel

The useful objective chooses one legal point inside every rectangle:

```text
A = min  sum ||pi+1 - pi||2
    pi in Ri
```

The final point connects back to the first. `AttainableRouteOptimizer` uses an Adam-style projected gradient procedure. Each update is clamped to its marker rectangle, so the reported route never depends on an impossible click. If all rectangles share a common intersection, the solver returns `A = 0` immediately.

## 4. Candidate comparison

Each `(yaw, pitch, zoom)` candidate accumulates accepted lap evidence. Candidate comparison is lexicographic:

1. more overlapping transitions;
2. more overlap area;
3. less marker gap;
4. less attainable travel;
5. less center travel.

For choosing a representative layout inside one candidate, attainable travel is the dominant cost, followed by overlap count and overlap area.

## 5. Search strategy

The initial search uses three widely separated yaw anchors, then tests reachable pitch and zoom variations. The bounds tracker learns where movement stops on the current client. Unreachable candidates are rejected rather than repeatedly requested.

`CompoundViewPredictor` can combine measured single-axis effects into a proposed multi-axis candidate. A prediction is only considered when it is new and estimates at least a one-percent attainable-travel improvement. It still requires a real lap before becoming trusted evidence.

## 6. Why observed mouse travel is not the objective

Observed cursor movement is useful feedback, but it is noisy. A player may move to inventory, another monitor, a Mark of Grace, or any unrelated screen location. The stable optimization target is therefore legal route geometry, not every motion made during a lap.

## 7. Verification gates

A mathematically good layout is not automatically safe to draw. Release marker layouts must also have verified inner rectangles, current format/version data, compatible canvas geometry, a settled camera, and a successful verification lap. These gates are why obstacle outlines may remain visible while screen markers are withheld.
