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

package org.gitgrader.testsupport;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * Records the SQL issued by the calling thread, so a test can count the statements its
 * own call made.
 * <p>
 * {@code Statistics#getPrepareStatementCount()} is the obvious way to count queries, but
 * it is a single counter shared by everything using the {@code SessionFactory}. Anything
 * the application does on another thread inside the measurement window is counted too:
 * the grading dispatcher tick, and the Spring Modulith listeners that mark an event
 * publication complete in their own transaction after the publishing one commits. A test
 * built on that counter asserts on whatever else the application happened to be doing,
 * which is why the count has flapped before.
 * <p>
 * Hibernate calls {@link #inspect} on the thread preparing the statement, so recording
 * per thread keeps the measurement to the work the test itself asked for. Register it
 * with {@code spring.jpa.properties.hibernate.session_factory.statement_inspector}.
 * <p>
 * Recording is off until {@link #start()} is called, so the inspector costs a
 * thread-local read per statement for anyone else sharing the factory.
 */
public class RecordingStatementInspector implements StatementInspector {

	private static final ThreadLocal<List<String>> RECORDED = new ThreadLocal<>();

	/**
	 * Starts recording on the calling thread, discarding anything recorded before.
	 */
	public static void start() {
		RECORDED.set(new ArrayList<>());
	}

	/**
	 * Stops recording on the calling thread.
	 * @return the SQL that thread issued since {@link #start()}, in order
	 */
	public static List<String> stop() {
		List<String> recorded = RECORDED.get();
		RECORDED.remove();
		return (recorded != null) ? List.copyOf(recorded) : List.of();
	}

	@Override
	public String inspect(String sql) {
		List<String> recorded = RECORDED.get();
		if (recorded != null) {
			recorded.add(sql);
		}
		return sql;
	}

}
