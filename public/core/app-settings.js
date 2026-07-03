/**
 * Central app-settings store. Thin shim over localStorage with a typed
 * schema, default values, and validation.
 *
 * Motivation: until now, each preference lived in its own localStorage key
 * read/written ad hoc throughout app.js. That meant:
 *   - No single place to audit what we persist
 *   - Default values duplicated wherever the key was read
 *   - No validation (any string could be written to any key)
 *
 * This module doesn't REPLACE the existing direct localStorage calls
 * immediately (that would be an invasive migration). It adds a unified
 * typed surface that new code uses and that app.js can progressively
 * migrate toward.
 *
 * NOT covered here:
 *   - scanneat.hydration.YYYY-MM-DD (per-day keys — dynamic, not schema)
 *   - scanneat.profiles.* (managed by profiles.js)
 *   - scanneat.telemetry.* (managed by telemetry.js)
 *   - scanneat.reminder.* (per-meal keys — managed inline)
 */

const SCHEMA = {
  // API
  'scanneat.key':           { default: '',        type: 'string' },
  'scanneat.mode':          { default: 'auto',    type: 'enum', values: ['auto', 'server', 'direct'] },

  // Localisation + theme + accessibility
  'scanneat.lang':          { default: 'fr',      type: 'enum', values: ['fr', 'en'] },
  'scanneat.theme':         { default: 'dark',    type: 'enum', values: ['dark', 'oled', 'light', 'auto'] },
  'scanneat.fontSize':      { default: 'normal',  type: 'enum', values: ['normal', 'large', 'xlarge'] },
  'scanneat.fontFamily':    { default: 'atkinson', type: 'enum', values: ['atkinson', 'lexend', 'system'] },
  'scanneat.motion':        { default: 'normal',  type: 'enum', values: ['normal', 'reduced'] },

  // One-time flags
  'scanneat.onboarded':     { default: '',        type: 'string' }, // '1' or ''
  'scanneat.dismissed_update': { default: '',     type: 'string' }, // tag or ''

  // Compare-next buffer
  'scanneat.compare_armed':     { default: '',    type: 'string' },
  'scanneat.compare_prev':      { default: '',    type: 'string' },
};

export function getSetting(key) {
  const schema = SCHEMA[key];
  if (!schema) {
    // Unknown key — return raw, no validation. Keeps this shim forward-
    // compatible with future keys not yet in the schema.
    return localStorage.getItem(key);
  }
  const raw = localStorage.getItem(key);
  if (raw == null) return schema.default;
  if (schema.type === 'enum' && !schema.values.includes(raw)) {
    return schema.default;
  }
  return raw;
}

export function setSetting(key, value) {
  const schema = SCHEMA[key];
  const str = value == null ? '' : String(value);
  if (schema?.type === 'enum' && !schema.values.includes(str)) {
    throw new Error(`Invalid ${key}: ${str}`);
  }
  if (str === '') {
    localStorage.removeItem(key);
  } else {
    localStorage.setItem(key, str);
  }
}

/** Debug helper — dump every managed key's current value. */
export function snapshotSettings() {
  const out = {};
  for (const key of Object.keys(SCHEMA)) {
    out[key] = getSetting(key);
  }
  return out;
}
