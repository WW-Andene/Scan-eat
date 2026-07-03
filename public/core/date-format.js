/**
 * Shared Intl.DateTimeFormat cache.
 *
 * Why: the dashboard + weekly view + monthly view + daily-share +
 * fasting history each new up ~5-10 Intl.DateTimeFormat instances per
 * render. Construction isn't cheap, and the fields are identical across
 * renders for a given locale. Caching by (lang, shape) key means we
 * allocate each formatter exactly once per session.
 *
 * Shape is a stable JSON-stringified form of the options object so two
 * callers asking for `{ weekday: 'short', day: 'numeric' }` share the
 * same instance.
 */

const cache = new Map();

function shapeKey(opts) {
  // Stable stringify — Intl.DateTimeFormat only cares about known keys
  // and this is plain-data input, so JSON.stringify with sorted keys is
  // enough (Node's default order already matches insertion for small
  // objects; callers pass literal options).
  const keys = Object.keys(opts).sort();
  const norm = {};
  for (const k of keys) norm[k] = opts[k];
  return JSON.stringify(norm);
}

/**
 * Return a cached Intl.DateTimeFormat instance. `lang` should be a
 * BCP-47 tag ('fr-FR', 'en-GB', 'en-CA', ...).
 */
export function dateFormatter(lang, opts) {
  const key = `${lang}|${shapeKey(opts)}`;
  let fmt = cache.get(key);
  if (!fmt) {
    try { fmt = new Intl.DateTimeFormat(lang, opts); }
    catch { fmt = new Intl.DateTimeFormat('en', opts); }
    cache.set(key, fmt);
  }
  return fmt;
}

/**
 * Convenience — maps the app's two-letter lang code to the locale tag
 * we actually use in UI formatting.
 */
export function localeFor(lang) {
  return lang === 'en' ? 'en-GB' : 'fr-FR';
}
