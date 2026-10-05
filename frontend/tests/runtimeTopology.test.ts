// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from 'vitest';
import { LEGACY_TOPOLOGY, RuntimeDefinitionSchema } from '../src/api';

const VALID = {
  runtimeKey: 'node-24',
  displayName: 'Node.js 24',
  image: 'registry.example.org/runtime-node',
  tag: '24.13.0',
  imageDigest: `sha256:${'a'.repeat(64)}`,
  testCommand: 'npm test',
  reportFormat: 'TAP',
  enabled: true,
};

describe('runtime grading topology', () => {
  it('refuses a runtime that does not state how it grades', () => {
    // The two topologies return different marks for the same submission, so a form that
    // omits the field would otherwise post a runtime the server rejects at submit time.
    expect(RuntimeDefinitionSchema.safeParse(VALID).success).toBe(false);
    expect(RuntimeDefinitionSchema.safeParse({ ...VALID, shimKind: '' }).success).toBe(false);
  });

  it('accepts the explicit single sandbox opt out', () => {
    const parsed = RuntimeDefinitionSchema.safeParse({ ...VALID, shimKind: LEGACY_TOPOLOGY });

    expect(parsed.success).toBe(true);
  });

  it('accepts a named shim and keeps its command', () => {
    const parsed = RuntimeDefinitionSchema.safeParse({
      ...VALID,
      shimKind: 'node',
      shimCommand: 'node /opt/gitgrader-shim/server.js',
    });

    expect(parsed.success).toBe(true);
    expect(parsed.success && parsed.data.shimCommand).toBe('node /opt/gitgrader-shim/server.js');
  });

  it('refuses a topology name that is not a plain lowercase slug', () => {
    expect(RuntimeDefinitionSchema.safeParse({ ...VALID, shimKind: 'Node Shim' }).success).toBe(false);
    expect(RuntimeDefinitionSchema.safeParse({ ...VALID, shimKind: 'node; rm -rf /' }).success).toBe(false);
  });
});
