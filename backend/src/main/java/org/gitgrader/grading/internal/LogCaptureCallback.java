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

package org.gitgrader.grading.internal;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.StreamType;

/**
 * Collects a grading container's output, up to a limit.
 *
 * <p>
 * Frames arrive on a Docker client thread while the worker thread reads the result, so
 * every access is synchronised: without it the reader is not merely racing for the last
 * few frames, it has no guarantee of seeing any of them.
 *
 * <p>
 * Bytes are kept as bytes and decoded once, at the end. Decoding each frame on its own
 * splits any character whose UTF-8 encoding straddles a frame boundary - which is decided
 * by how the engine happened to chunk the stream, not by the output - and both halves
 * become replacement characters. An assertion message naming a variable in Greek or a
 * test whose name contains an em dash came back mangled, in the one place an instructor
 * reads to explain a failure.
 */
final class LogCaptureCallback extends ResultCallback.Adapter<Frame> {

	private final long limitBytes;

	private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();

	private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

	private long currentBytes;

	LogCaptureCallback(long limitBytes) {
		this.limitBytes = limitBytes;
	}

	@Override
	public synchronized void onNext(Frame frame) {
		if (this.currentBytes >= this.limitBytes) {
			return;
		}
		byte[] payload = frame.getPayload();
		int allowed = (int) Math.min(payload.length, this.limitBytes - this.currentBytes);
		if (frame.getStreamType() == StreamType.STDOUT) {
			this.stdout.write(payload, 0, allowed);
		}
		else if (frame.getStreamType() == StreamType.STDERR) {
			this.stderr.write(payload, 0, allowed);
		}
		this.currentBytes += allowed;
	}

	synchronized String getStdout() {
		return this.stdout.toString(StandardCharsets.UTF_8);
	}

	synchronized String getStderr() {
		return this.stderr.toString(StandardCharsets.UTF_8);
	}

}
