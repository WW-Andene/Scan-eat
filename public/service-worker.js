// R32.1: bump cache version so clients pick up the refreshed shell list.
// Missing modules used to hydrate via stale-while-revalidate on first
// online use, but first-launch-offline-after-install crashed on their
// imports. Every module the app reaches at boot now precaches.
//
// r36 — bump after ~60 commits of UI/UX rework (coral gradient,
// notebook signature, tile grid, per-meal time-of-day cues, polaroid
// queue, tonal shadows, focal glow, milestone-burst, forced-colors,
// dashboard tile grid HTML restructure, etc.). Any already-installed
// PWA was serving the pre-session CSS/JS out of r35; r36 forces a
// fresh fetch on next load so the visible delta actually lands.
const CACHE = 'scann-eat-shell-0367d5b';
const SHELL = [
  '/',
  '/index.html',
  '/app.js',
  '/backup.js',
  '/profiles.js',
  '/native-bridge.js',
  '/engine.bundle.js',
  '/styles.refactored.css',
  '/styles.rework.css',
  '/manifest.webmanifest',
  '/icon.svg',
  // /state/
  '/state/scan-queue-state.js',
  // /core/
  '/core/i18n.js',
  '/core/dom-helpers.js',
  '/core/explanations.js',
  '/core/allergens.js',
  '/core/diets.js',
  '/core/personal-score.js',
  '/core/telemetry.js',
  '/core/app-settings.js',
  '/core/presenters.js',
  '/core/share.js',
  '/core/date-format.js',
  '/core/dateutil.js',
  '/core/unit-convert.js',
  '/core/nutrition-fields.js',
  '/core/user-content-checks.js',
  // /data/
  '/data/queue-store.js',
  '/data/scan-history.js',
  '/data/profile.js',
  '/data/consumption.js',
  '/data/weight-log.js',
  '/data/meal-templates.js',
  '/data/recipes.js',
  '/data/activity.js',
  '/data/custom-food-db.js',
  '/data/pairings.js',
  '/data/food-db.js',
  // /features/
  '/features/hydration.js',
  '/features/fasting-history.js',
  '/features/fasting.js',
  '/features/day-notes.js',
  '/features/grocery-list.js',
  '/features/meal-plan.js',
  '/features/activity.js',
  '/features/csv-import.js',
  '/features/weight.js',
  '/features/reminders.js',
  '/features/voice-dictate.js',
  '/features/scanner.js',
  '/features/onboarding.js',
  '/features/install-banner.js',
  '/features/backup-io.js',
  '/features/appearance.js',
  '/features/recipe-ideas.js',
  '/features/settings-dialog.js',
  '/features/keybindings.js',
  '/features/add-to-recipe.js',
  '/features/barcode-scanner-detect.js',
  '/features/custom-foods-day-notes.js',
  '/features/dashboard-charts.js',
  '/features/image-compression.js',
  '/features/meal-plan-ui.js',
  '/features/offline-queue-sync.js',
  '/features/profiles-ui.js',
  '/features/qa-photo-identify.js',
  '/features/scan-pipeline.js',
  '/features/scan-queue-ui.js',
  '/features/scan-result-render.js',
  '/features/telemetry-ui.js',
  '/features/profile-dialog.js',
  '/features/menu-scan.js',
  '/features/templates-dialog.js',
  '/features/recipes-dialog.js',
  '/features/qa-autocomplete.js',
  '/features/comparison.js',
  '/features/portion-panel.js',
  '/features/scan-history-ui.js',
  '/features/update-checker.js',
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE).then((cache) => cache.addAll(SHELL)).then(() => self.skipWaiting()),
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))),
    ).then(() => self.clients.claim()),
  );
});

// Temporary holding area for files a user shared via the PWA share_target.
// The browser POSTs the shared files to '/', which we intercept here, stash
// in-memory, and hand to the app on its next message.
let sharedFilesBuffer = null;

self.addEventListener('message', (event) => {
  // The app asks for any pending shared files right after load. Hand them
  // over once and clear the buffer so a later reload doesn't see stale data.
  if (event.data === 'shared-files?') {
    event.ports[0]?.postMessage(sharedFilesBuffer);
    sharedFilesBuffer = null;
  }
});

self.addEventListener('fetch', (event) => {
  const { request } = event;
  const url = new URL(request.url);

  // Share target: the user shared one or more images from their gallery to
  // Scan\'eat. Extract the files, buffer them, and redirect the browser to
  // "/?shared=1" so the app knows to pull from the buffer on load.
  if (request.method === 'POST' && url.pathname === '/') {
    event.respondWith((async () => {
      try {
        const formData = await request.formData();
        const files = formData.getAll('shared_images');
        // Ensure they are actual File objects (spec allows strings too).
        sharedFilesBuffer = files.filter((f) => f instanceof File);
      } catch { sharedFilesBuffer = null; }
      return Response.redirect('/?shared=1', 303);
    })());
    return;
  }

  // Never cache the scoring API — it needs fresh LLM calls.
  if (url.pathname.startsWith('/api/')) return;

  // Stale-while-revalidate for shell assets.
  if (request.method === 'GET') {
    event.respondWith(
      caches.match(request).then((cached) => {
        const fetchPromise = fetch(request)
          .then((res) => {
            if (res.ok && res.type === 'basic') {
              const clone = res.clone();
              caches.open(CACHE).then((cache) => cache.put(request, clone));
            }
            return res;
          })
          .catch(() => {
            // Network failed. If we have a cached copy, hand it back; else
            // return a synthetic 504 so the promise resolves to a Response
            // instead of undefined (which would crash the fetch handler).
            if (cached) return cached;
            return new Response('Offline and not cached', {
              status: 504,
              statusText: 'Gateway Timeout',
              headers: { 'Content-Type': 'text/plain' },
            });
          });
        return cached || fetchPromise;
      }),
    );
  }
});
