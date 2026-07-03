/**
 * Telemetry panel (Settings → local-only log) UI wiring.
 *
 * The actual log storage/format/clear logic lives in /core/telemetry.js
 * (pure, IDB/localStorage-backed). This module only owns the DOM glue:
 * the enable toggle, view/copy/export/clear buttons inside the
 * settings dialog. Extracted from app.js (Phase 14) — same deps-object
 * convention as the rest of public/features/*.
 */
export function initTelemetryUi({
  $, t, toast, show, hide, shareOrCopy, todayISO,
  setEnabled, format, clear,
}) {
  $('telemetry-enabled')?.addEventListener('change', (e) => {
    setEnabled(!!e.target.checked);
  });

  $('telemetry-view')?.addEventListener('click', () => {
    const out = $('telemetry-output');
    if (!out) return;
    out.textContent = format();
    show(out);
  });

  $('telemetry-copy')?.addEventListener('click', async () => {
    // Route via shareOrCopy so a native share sheet is offered on mobile
    // (iOS Mail / Android messenger = easy way for the user to file a bug
    // report). Clipboard is the fallback everywhere else.
    await shareOrCopy({
      title: t('telemetryTitle'),
      text: format(),
      toasts: { copied: t('telemetryCopied'), failed: t('telemetryCopyFailed') },
      toast,
    });
  });

  // R7.3: Export as a downloadable .txt file. Useful when the user wants
  // to attach the log to an email or issue report without copy-paste.
  $('telemetry-export')?.addEventListener('click', () => {
    const text = format();
    const blob = new Blob([text], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `scanneat-telemetry-${todayISO()}.txt`;
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 5000);
    toast(t('telemetryExported'), 'ok');
  });

  $('telemetry-clear')?.addEventListener('click', () => {
    if (!window.confirm(t('telemetryClearConfirm'))) return;
    clear();
    const out = $('telemetry-output');
    if (out) { out.textContent = ''; hide(out); }
    toast(t('telemetryCleared'), 'ok');
  });
}
