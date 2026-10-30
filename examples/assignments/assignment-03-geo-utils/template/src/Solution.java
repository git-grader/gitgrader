# Copyright the GitGrader contributors.
# SPDX-License-Identifier: Apache-2.0

/**
 * Geo utils.
 *
 * <p>Implement the small planar-geometry helpers the README describes. The
 * public checks in {@code public-tests/} cover the happy paths; the graded
 * suite covers more. The methods are static: the grading shim serves the
 * submission's static public methods to the hidden suite over a socket, so
 * an instance-based design cannot be reached.
 */
public class Solution {

	/**
	 * Returns the Euclidean distance between the points (x1, y1) and (x2, y2).
	 */
	public static double distance(double x1, double y1, double x2, double y2) {
		throw new UnsupportedOperationException("distance");
	}

	/**
	 * Returns the compass bearing in degrees from the point (x1, y1) to the
	 * point (x2, y2), measured clockwise from due north and kept in
	 * [0, 360). North is +y; east is +x.
	 */
	public static double bearing(double x1, double y1, double x2, double y2) {
		throw new UnsupportedOperationException("bearing");
	}

	/**
	 * Returns the midpoint of the segment between the points (x1, y1) and
	 * (x2, y2) as {@code [midX, midY]}.
	 */
	public static java.util.List<Double> midpoint(double x1, double y1, double x2, double y2) {
		throw new UnsupportedOperationException("midpoint");
	}

	/**
	 * Returns whether the point (x, y) lies within {@code range} of the point
	 * (cx, cy), with points exactly on the circle counted as within.
	 */
	public static boolean isWithinRange(double x, double y, double cx, double cy, double range) {
		throw new UnsupportedOperationException("isWithinRange");
	}

}
