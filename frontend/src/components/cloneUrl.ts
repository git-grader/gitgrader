// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import type { Meta } from '../api';

/**
 * The repository path for one student and one assignment.
 *
 * Mirrors the backend's GitRepositoryService.repositoryPathFor: the server stores
 * every bare repository under `<courseKey>/<assignmentKey>/<studentUsername>`, so the
 * frontend can compose a clone URL from keys it already shows without asking the
 * server to echo it back.
 */
export function repositoryPath(
  courseKey: string,
  assignmentKey: string,
  studentUsername: string,
): string {
  return `${courseKey}/${assignmentKey}/${studentUsername}`;
}

/**
 * The SCP-style clone URL for one repository, for example
 * `git@grader.example.org:cs101/hw1/12345.git`.
 *
 * SCP-style URLs carry no port; the port is handled by the SSH config, or spelled
 * out in the clone command when it is not the default (see cloneCommand). Everything
 * host-specific comes from the meta endpoint, so no fork has to patch a URL in.
 */
export function cloneUrl(meta: Meta, path: string): string {
  return `${meta.sshUser}@${meta.sshHost}:${path}.git`;
}

/**
 * The full `git clone` command for one repository.
 *
 * SCP-style Git URLs do not carry a port, so when the advertised endpoint uses a
 * non-default port the clone needs a one-off SSH command that supplies it.
 */
export function cloneCommand(meta: Meta, path: string): string {
  const prefix =
    meta.sshPort === 22
      ? 'git clone'
      : `git -c core.sshCommand="ssh -p ${meta.sshPort}" clone`;
  return `${prefix} ${cloneUrl(meta, path)}`;
}
