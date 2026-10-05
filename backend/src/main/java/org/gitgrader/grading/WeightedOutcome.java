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

package org.gitgrader.grading;

import java.math.BigDecimal;

/**
 * One graded test together with the weight its manifest gave it.
 *
 * @param outcome what the test did
 * @param weight how much of the assignment it is worth; never negative, and {@code 1}
 * when the manifest does not say
 */
public record WeightedOutcome(TestOutcome outcome, BigDecimal weight) {

	public WeightedOutcome {
		weight = normalise(weight);
	}

	/**
	 * Normalises a weight so a manifest cannot score a run into nonsense.
	 *
	 * <p>
	 * A negative weight would let a student gain marks by failing a test, and a weight of
	 * zero in the denominator would make the division undefined. Both are treated as one
	 * rather than rejected, because refusing to grade at all over a malformed weight is
	 * worse than grading it as an ordinary test.
	 * @param weight the declared weight, possibly null or out of range
	 * @return a usable weight, never negative and never null
	 */
	public static BigDecimal normalise(@org.jspecify.annotations.Nullable BigDecimal weight) {
		if (weight == null || weight.signum() <= 0) {
			return BigDecimal.ONE;
		}
		return weight;
	}

}
