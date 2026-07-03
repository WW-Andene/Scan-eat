/**
 * Global keyboard shortcuts.
 *
 *   Escape       — closes every open <dialog>. Browser does this for
 *                  modal dialogs automatically; we extend it so non-
 *                  modal dialogs (settings-like) also close.
 *   /            — focuses the history search input.
 *   Enter        — triggers the Scan button when the user isn't typing
 *                  in a form field.
 *   q / t / r / w — open Quick Add / Templates / Recipes / Weight
 *                  (R9.5, R11.6). Only fires when the user isn't
 *                  typing in a field and no dialog is already open —
 *                  so typing "r" into a text input never hijacks
 *                  focus into a dialog.
 *   f            — scrolls the fasting tile into view (R12.6). The
 *                  fasting feature is a tile, not a dialog, so the
 *                  best UX is "show me where it is" rather than
 *                  open/close.
 *   ?            — emits a toast cheat-sheet of the above (R10.2).
 *                  Same typing/dialog guard as q/t/r.
 *
 * ADR-0004 feature-folder pattern. Single init; no render loop.
 *
 * Deps shape:
 *   { scanBtn, historySearchInput, quickAddBtn, templatesBtn, recipesBtn,
 *     t, toast }
 */

export function initKeybindings({
  scanBtn, historySearchInput,
  quickAddBtn, templatesBtn, recipesBtn, weightBtn,
  t, toast,
}) {
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
      for (const d of document.querySelectorAll('dialog[open]')) d.close();
      return;
    }
    const tag = (e.target?.tagName || '').toLowerCase();
    const typing = tag === 'input' || tag === 'textarea' || tag === 'select';
    if (typing) return;
    const dialogOpen = !!document.querySelector('dialog[open]');
    if (e.key === '/' && historySearchInput) {
      e.preventDefault();
      historySearchInput.focus();
      return;
    }
    if (dialogOpen) return;
    if (e.key === 'Enter' && scanBtn && !scanBtn.disabled) {
      scanBtn.click();
      return;
    }
    if (e.key === 'q' && quickAddBtn) { e.preventDefault(); quickAddBtn.click(); return; }
    if (e.key === 't' && templatesBtn) { e.preventDefault(); templatesBtn.click(); return; }
    if (e.key === 'r' && recipesBtn) { e.preventDefault(); recipesBtn.click(); return; }
    if (e.key === 'w' && weightBtn) { e.preventDefault(); weightBtn.click(); return; }
    if (e.key === 'f') {
      const tile = document.getElementById('fasting-tile');
      if (tile && !tile.hidden) {
        e.preventDefault();
        tile.scrollIntoView({ behavior: 'smooth', block: 'center' });
        return;
      }
    }
    if (e.key === '?' && t && toast) {
      e.preventDefault();
      // 6-second display — long enough to read the sheet but not
      // sticky; user can re-press '?' to re-open.
      toast(t('keybindingsHelp'), 6000);
      return;
    }
  });
}
