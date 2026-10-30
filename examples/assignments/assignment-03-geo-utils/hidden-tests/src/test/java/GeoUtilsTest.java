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

import gitgrader.ShimClient;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hidden checks for the geo-utils assignment.
 *
 * <p>
 * This suite runs in the suite container of a shimmed round. The submission is
 * graded in a separate sandbox container that never sees these hidden sources;
 * the shim client reaches the submission's compiled class over the socket the
 * two containers share. The runtime image bakes the shim classes into
 * {@code /opt/gitgrader-shim/classes}, so compiling this suite against the
 * client needs no extra dependency.
 *
 * <p>
 * Every test method name is declared in {@code manifest.json}; the backend
 * refuses to score a name the manifest does not declare. Surefire writes the
 * bare method name into the JUnit XML {@code name} attribute, which is what
 * the manifest must carry.
 */
class GeoUtilsTest {

	private static ShimClient client;

	@BeforeAll
	static void connect() {
		client = ShimClient.connect(null, 0);
	}

	@AfterAll
	static void close() {
		if (client != null) {
			client.close();
		}
	}

	@Test
	void testH01DistanceIsZeroForTheSamePoint() {
		assertEquals(0.0, ((Number) client.call("distance", java.util.List.of(1.0, 1.0, 1.0, 1.0))).doubleValue(),
				1e-9);
	}

	@Test
	void testH02DistanceFollowsPythagoras() {
		assertEquals(5.0, ((Number) client.call("distance", java.util.List.of(0.0, 0.0, 3.0, 4.0))).doubleValue(),
				1e-9);
	}

	@Test
	void testH03DistanceIsSymmetric() {
		double forward = ((Number) client.call("distance", java.util.List.of(1.5, -2.0, -3.0, 4.0))).doubleValue();
		double backward = ((Number) client.call("distance", java.util.List.of(-3.0, 4.0, 1.5, -2.0))).doubleValue();
		assertEquals(forward, backward, 1e-9);
	}

	@Test
	void testH04BearingIsZeroDueNorth() {
		assertEquals(0.0, ((Number) client.call("bearing", java.util.List.of(0.0, 0.0, 0.0, 1.0))).doubleValue(),
				1e-9);
	}

	@Test
	void testH05BearingIsNinetyDueEast() {
		assertEquals(90.0, ((Number) client.call("bearing", java.util.List.of(0.0, 0.0, 1.0, 0.0))).doubleValue(),
				1e-9);
	}

	@Test
	void testH06BearingIsInTheHalfOpenCircle() {
		double bearing = ((Number) client.call("bearing", java.util.List.of(0.0, 0.0, -1.0, -1.0))).doubleValue();
		assertTrue(bearing >= 0.0 && bearing < 360.0, "bearing " + bearing + " outside [0, 360)");
	}

	@Test
	void testH07MidpointStaysOnTheSegment() {
		@SuppressWarnings("unchecked")
		List<Double> mid = (List<Double>) client.call("midpoint", java.util.List.of(0.0, 0.0, 10.0, 10.0));
		assertEquals(5.0, mid.get(0), 1e-9);
		assertEquals(5.0, mid.get(1), 1e-9);
	}

	@Test
	void testH08MidpointHalvesBothCoordinates() {
		@SuppressWarnings("unchecked")
		List<Double> mid = (List<Double>) client.call("midpoint", java.util.List.of(-4.0, -6.0, 12.0, 6.0));
		assertEquals(4.0, mid.get(0), 1e-9);
		assertEquals(0.0, mid.get(1), 1e-9);
	}

	@Test
	void testH09IsWithinRangeAcceptsInsideTheCircle() {
		assertEquals(Boolean.TRUE, client.call("isWithinRange", java.util.List.of(0.0, 0.0, 3.0, 4.0, 5.0)));
	}

	@Test
	void testH10IsWithinRangeRejectsBeyondTheCircle() {
		assertEquals(Boolean.FALSE, client.call("isWithinRange", java.util.List.of(0.0, 0.0, 3.0, 4.0, 4.999)));
	}

}
