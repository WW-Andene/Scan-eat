/**
 * Backup / export flow — JSON backup, Health-app JSON, daily CSV.
 *
 * Wires three Settings buttons:
 *   - #backup-export         → JSON dump of every IDB store
 *   - #health-export         → per-meal JSON shape for Apple Health / Fit
 *   - #csv-export            → per-day totals (entriesToDailyCSV)
 *   - #backup-import-file    → restore JSON backup (overwrites stores)
 *
 * Status is rendered into #backup-status. Errors surface the underlying
 * exception message unless one of the three known branches matches (see
 * restoreBackup in /backup.js for the exception shapes).
 *
 * ADR-0004 feature-folder pattern.
 *
 * Deps shape:
 *   { t, show, hide,
 *     buildBackup, restoreBackup, listAllEntries,
 *     entriesToHealthJSON, entriesToDailyCSV,
 *     renderRecentScans, renderDashboard }
 */

function $(id) { return document.getElementById(id); }

function todayISO() {
  // Mirror /core/dateutil.js so backup filenames use the user's local
  // date, not UTC.
  const parts = new Intl.DateTimeFormat('en-CA', {
    year: 'numeric', month: '2-digit', day: '2-digit',
  }).formatToParts(new Date());
  const y = parts.find((p) => p.type === 'year')?.value ?? '0000';
  const m = parts.find((p) => p.type === 'month')?.value ?? '00';
  const d = parts.find((p) => p.type === 'day')?.value ?? '00';
  return `${y}-${m}-${d}`;
}

export function initBackupIO(deps) {
  const {
    t, show, hide,
    buildBackup, restoreBackup, listAllEntries,
    entriesToHealthJSON, entriesToDailyCSV,
    renderRecentScans, renderDashboard,
  } = deps;

  const setBackupStatus = (text, state) => {
    const el = $('backup-status');
    if (!el) return;
    if (!text) { hide(el); return; }
    el.textContent = text;
    if (state) el.dataset.state = state;
    else delete el.dataset.state;
    show(el);
  };

  const download = (filename, blob) => {
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 5000);
  };

  $('backup-export')?.addEventListener('click', async () => {
    try {
      const payload = await buildBackup();
      const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' });
      download(`scanneat-backup-${todayISO()}.json`, blob);
      setBackupStatus(t('backupExported'));
    } catch (err) {
      console.error('[backup export]', err);
      setBackupStatus(err.message || String(err), 'error');
    }
  });

  $('health-export')?.addEventListener('click', async () => {
    try {
      const all = await listAllEntries().catch(() => []);
      const payload = entriesToHealthJSON(all);
      const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' });
      download(`scanneat-health-${todayISO()}.json`, blob);
      setBackupStatus(t('healthExported', { n: payload.entries.length }));
    } catch (err) {
      console.error('[health export]', err);
      setBackupStatus(err.message || String(err), 'error');
    }
  });

  $('csv-export')?.addEventListener('click', async () => {
    try {
      const all = await listAllEntries().catch(() => []);
      const csv = entriesToDailyCSV(all);
      const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
      download(`scanneat-totals-${todayISO()}.csv`, blob);
      const days = new Set(all.map((e) => e.date).filter(Boolean)).size;
      setBackupStatus(t('csvExported', { days }));
    } catch (err) {
      console.error('[csv export]', err);
      setBackupStatus(err.message || String(err), 'error');
    }
  });

  $('backup-import-file')?.addEventListener('change', async (e) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    try {
      const text = await file.text();
      const data = JSON.parse(text);
      await restoreBackup(data);
      // R20.2: activity entries were silently excluded from the count
      // shown to the user after a v2 backup restore — the count under-
      // reported by however many activity rows the backup contained.
      const counts =
        (data.history?.length || 0) + (data.consumption?.length || 0) +
        (data.weight?.length || 0) + (data.templates?.length || 0) +
        (data.recipes?.length || 0) + (data.activity?.length || 0);
      setBackupStatus(t('backupImported', { items: counts }));
      await renderRecentScans();
      await renderDashboard();
    } catch (err) {
      console.error('[backup import]', err);
      const msg =
        /Scan\'eat backup/i.test(err.message) ? t('backupInvalid')
        : /newer than this version/i.test(err.message) ? t('backupTooNew')
        : err.message || String(err);
      setBackupStatus(msg, 'error');
    }
  });
}
