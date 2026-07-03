/**
 * Pin the scoring-engine version. Per ADR-0006, ENGINE_VERSION is bumped on
 * deliberate scoring-math changes — the test forces the bump to land in the
 * same diff as the code change so the reason is in the commit history.
 *
 * If you intentionally changed the scoring math, update EXPECTED_VERSION
 * here and add a DECISIONS.md entry describing what changed.
 */

import { strict as assert } from 'node:assert';
import { describe, it } from 'node:test';

import { ENGINE_VERSION } from '../src/scoring-engine.ts';

const EXPECTED_VERSION = '2.2.0';

describe('engine-version', () => {
  it('matches the pinned version (per ADR-0006)', () => {
    assert.equal(
      ENGINE_VERSION,
      EXPECTED_VERSION,
      'ENGINE_VERSION changed without updating engine-version-tests.ts. ' +
      'If the change was intentional, update EXPECTED_VERSION here AND add ' +
      'a DECISIONS.md entry describing what scoring math changed.',
    );
  });

  it('follows semver shape', () => {
    assert.match(ENGINE_VERSION, /^\d+\.\d+\.\d+$/);
  });
});
