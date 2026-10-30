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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Public checks for the geo-utils assignment. */
class SolutionTest {

	@Test
	void distanceOfTheSamePointIsZero() {
		assertEquals(0.0, Solution.distance(2.0, 7.0, 2.0, 7.0), 1e-9);
	}

	@Test
	void distanceFollowsPythagoras() {
		assertEquals(13.0, Solution.distance(0.0, 0.0, 5.0, 12.0), 1e-9);
	}

	@Test
	void bearingDueNorthIsZero() {
		assertEquals(0.0, Solution.bearing(0.0, 0.0, 0.0, 5.0), 1e-9);
	}

	@Test
	void bearingDueEastIsNinety() {
		assertEquals(90.0, Solution.bearing(0.0, 0.0, 5.0, 0.0), 1e-9);
	}

	@Test
	void midpointSplitsTheSegmentInHalf() {
		List<Double> mid = Solution.midpoint(-2.0, 4.0, 6.0, 0.0);
		assertEquals(2.0, mid.get(0), 1e-9);
		assertEquals(2.0, mid.get(1), 1e-9);
	}

	@Test
	void withinRangeAcceptsTheEdgeAndRejectsBeyond() {
		assertTrue(Solution.isWithinRange(0.0, 0.0, 3.0, 4.0, 5.0));
		assertEquals(false, Solution.isWithinRange(0.0, 0.0, 3.0, 4.0, 4.5));
	}

}
