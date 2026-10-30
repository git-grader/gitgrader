/*
 * Copyright the GitGrader contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.util.List;

/**
 * Partial reference solution for the geo-utils assignment.
 *
 * <p>
 * Deliberately misses three hidden checks (h06, h07, h08) so the worked
 * example can show a 70% score.
 */
public class Solution {

	public static double distance(double x1, double y1, double x2, double y2) {
		double dx = x2 - x1;
		double dy = y2 - y1;
		return Math.sqrt(dx * dx + dy * dy);
	}

	public static double bearing(double x1, double y1, double x2, double y2) {
		double dx = x2 - x1;
		double dy = y2 - y1;
		// Misses h06: negatives stay negative instead of wrapping into
		// [0, 360).
		return Math.toDegrees(Math.atan2(dx, dy));
	}

	public static List<Double> midpoint(double x1, double y1, double x2, double y2) {
		// Misses h07 and h08: adds instead of averaging.
		return List.of(x1 + x2, y1 + y2);
	}

	public static boolean isWithinRange(double x, double y, double cx, double cy, double range) {
		return distance(x, y, cx, cy) <= range;
	}

}
