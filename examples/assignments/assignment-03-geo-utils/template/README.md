# Geo utils

Implement four small planar-geometry helpers in `src/Solution.java`. Each
method's Javadoc pins down its behaviour; the public checks in
`public-tests/` cover the happy paths and are yours to run and read.

## Methods

| Method | One-line contract |
| --- | --- |
| `distance(x1, y1, x2, y2)` | Euclidean distance between two points |
| `bearing(x1, y1, x2, y2)` | compass bearing in degrees, clockwise from north, in `[0, 360)` |
| `midpoint(x1, y1, x2, y2)` | segment midpoint as a list `[midX, midY]` |
| `isWithinRange(x, y, cx, cy, range)` | is the point within `range` of the centre (edge counts) |

Bearings measure clockwise from due north with north along +y and east along
+x, so due north is `0`, due east is `90`, due south is `180` and due west
is `270`.

## Running the public checks

The graded environment bakes Maven and JUnit 5 into the runtime image and
runs without network access; locally you need a JDK and Maven on your PATH:

```console
$ mvn --quiet test
```
