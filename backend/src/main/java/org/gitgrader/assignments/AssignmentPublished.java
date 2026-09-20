/*
 * Copyright the GitGrader contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package org.gitgrader.assignments;

import java.time.Instant;
import java.util.UUID;

/** Published after an assignment becomes available to students. */
public record AssignmentPublished(UUID assignmentId, UUID courseId, String assignmentKey, Instant publishedAt) {
}
