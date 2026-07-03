/**
 * dateutil.localDateISO — local-day ISO (YYYY-MM-DD) conversion.
 *
 * Pins the contract that resolved a real TZ bug in R2.10: previously
 * `todayISO()` used `new Date().toISOString().slice(0,10)` which returns
 * the UTC day. On a device in UTC+3 or greater, late-evening meals were
 * silently bucketed on the next calendar day.
 */

import { strict as assert } from 'node:assert';
import { describe, it } from 'node:test';

// @ts-expect-error — plain JS
import { localDateISO } from '../public/core/dateutil.js';
// @ts-expect-error — plain JS
import { dateFormatter, localeFor } from '../public/core/date-format.js';

describe('localDateISO', () => {
  it('returns a valid YYYY-MM-DD shape', () => {
    assert.match(localDateISO(), /^\d{4}-\d{2}-\d{2}$/);
  });

  it('uses Date.now() when called with no arguments', () => {
    // Smoke — result must be a non-empty string today.
    const today = localDateISO();
    assert.ok(today.startsWith('20'));
    assert.equal(today.length, 10);
  });

  it('accepts an epoch-ms argument for deterministic output', () => {
    // Noon UTC on Apr 22 2026 is the same local calendar date in every
    // timezone between UTC-12 and UTC+12 — runtime-agnostic.
    const noon = Date.UTC(2026, 3, 22, 12, 0, 0);
    assert.equal(localDateISO(noon), '2026-04-22');
  });

  it('matches toLocaleDateString("en-CA") for the same instant', () => {
    // The whole point of the helper is this equivalence; pin it.
    const instant = new Date(Date.UTC(2026, 3, 22, 12, 0, 0));
    const viaIntl = instant.toLocaleDateString('en-CA');
    assert.equal(localDateISO(instant.getTime()), viaIntl);
  });

  it('zero-pads months and days without ambiguity', () => {
    // A January 5 instant must come back as 2026-01-05, not 2026-1-5.
    const jan5 = Date.UTC(2026, 0, 5, 12, 0, 0);
    const out = localDateISO(jan5);
    assert.equal(out.length, 10);
    assert.equal(out.slice(0, 7), '2026-01');
  });
});

describe('dateFormatter cache', () => {
  it('returns the same instance for identical (lang, shape)', () => {
    const a = dateFormatter('fr-FR', { weekday: 'short' });
    const b = dateFormatter('fr-FR', { weekday: 'short' });
    assert.equal(a, b, 'identical args must yield the same cached instance');
  });

  it('returns different instances for different langs', () => {
    const a = dateFormatter('fr-FR', { weekday: 'short' });
    const b = dateFormatter('en-GB', { weekday: 'short' });
    assert.notEqual(a, b);
  });

  it('returns different instances for different shapes', () => {
    const a = dateFormatter('fr-FR', { weekday: 'short' });
    const b = dateFormatter('fr-FR', { weekday: 'narrow' });
    assert.notEqual(a, b);
  });

  it('key is stable regardless of options-object key order', () => {
    const a = dateFormatter('fr-FR', { weekday: 'short', day: 'numeric' });
    const b = dateFormatter('fr-FR', { day: 'numeric', weekday: 'short' });
    assert.equal(a, b);
  });

  it('localeFor maps the app lang to BCP-47 tags used in UI', () => {
    assert.equal(localeFor('en'), 'en-GB');
    assert.equal(localeFor('fr'), 'fr-FR');
    assert.equal(localeFor('xx'), 'fr-FR'); // fallback
  });

  it('actually formats a date (smoke test)', () => {
    const fmt = dateFormatter('en-GB', { weekday: 'long', day: 'numeric', month: 'long' });
    const out = fmt.format(new Date(Date.UTC(2026, 3, 22, 12)));
    assert.match(out, /Wednesday/);
    assert.match(out, /April/);
    assert.match(out, /22/);
  });
});
