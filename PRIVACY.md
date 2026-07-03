# Scan'eat Native Privacy

Scan'eat no longer includes the previous PWA/browser storage layer, direct Groq mode, Vercel functions, service worker, or Open Food Facts web lookup.

## Data handling

- Product scoring requests are handled by Kotlin code in this repository.
- Barcode lookup uses the embedded native catalog only.
- Recipe, pantry, menu, and import helpers return deterministic native guidance.
- No browser `localStorage`, IndexedDB, service worker cache, telemetry module, Vercel environment variable, Groq request, or Open Food Facts request is shipped in this native-only codebase.

If a future native mobile shell stores profile or scan data, document that storage before release and keep it on-device by default.
