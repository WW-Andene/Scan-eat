/**
 * Single source of truth for the in-progress scan queue.
 *
 * Extracted per the app.js decomposition plan (Phase 2 / H4): `queue` was a
 * module-level array in app.js, implicitly shared by the queue UI, scan
 * pipeline, and offline-sync clusters. Splitting those clusters across files
 * without giving `queue` one explicit home would silently fork the state.
 * All consumers must import this exact array instance (not copy it) so
 * mutation (push/splice/length=0) stays visible everywhere.
 *
 * Shape: { id, dataUrl, base64, mime, barcode? }[]
 */
export const queue = [];
