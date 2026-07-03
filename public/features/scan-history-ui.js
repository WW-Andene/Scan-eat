/**
 * Scan history UI — recent-scans list, search/filter, summary chip,
 * persistence on each scan, "reopen" handler.
 *
 * Owns:
 *   - <ul#recent-list> rendering (thumbnail + grade chip + name + ago).
 *   - <input#history-search> + <select#history-grade> live filtering,
 *     debounced 80 ms so high-frequency typing doesn't re-render on
 *     every keystroke (was unbounded before this extraction).
 *   - <div#recent-summary> "12 scans · 4A · 3B …" chip with per-grade
 *     toggle filters.
 *   - <button#export-history> JSON dump.
 *   - persistToHistory(data) called from the post-scan hook to stash
 *     the snapshot for later reopen.
 *   - reopenScan(item) used by the list-item activation handler.
 *
 * Pure UI — IDB ops are delegated through the deps so this module
 * has no direct dependency on the data layer.
 *
 * Init shape:
 *   const api = initScanHistoryUi({
 *     $, t, hide, show, toast,
 *     recentScansEl, recentListEl, historySearchInput, historyGradeSelect,
 *     clearHistoryBtn, exportHistoryBtn,
 *     errorEl, statusEl, resultEl,
 *     listScans, deleteScan, clearScans, saveScan,
 *     filterScanHistory, summarizeScanHistory, timeAgoBucket, todayISO,
 *     getQueue,                  // () => current queue items (for thumbnail src)
 *     setLastData, getLastData,  // () => / (data) => the active scan
 *     renderAudit, renderIngredients, renderNutrition,
 *     makeActivatable,
 *   });
 *   api → { renderRecentScans, persistToHistory, reopenScan }
 */

const HISTORY_FILTER_DEBOUNCE_MS = 80;

/** Debounce helper — trailing edge, last-call-wins. */
function debounce(fn, ms) {
  let timer = null;
  return (...args) => {
    if (timer) clearTimeout(timer);
    timer = setTimeout(() => { timer = null; fn(...args); }, ms);
  };
}

/**
 * Downscale a data-URL to a square JPEG thumbnail. Keeps IDB records tiny
 * so even 30 scans × 30 dashboard renders feel instant and stay well clear
 * of the browser quota. Returns the original if anything fails — safer to
 * waste a few KB than to lose the reference image.
 */
async function makeThumbnail(dataUrl, size = 96) {
  if (!dataUrl || !dataUrl.startsWith('data:image')) return '';
  try {
    const img = await new Promise((resolve, reject) => {
      const i = new Image();
      i.onload = () => resolve(i);
      i.onerror = () => reject(new Error('thumbnail decode failed'));
      i.src = dataUrl;
    });
    const canvas = document.createElement('canvas');
    canvas.width = size;
    canvas.height = size;
    const ctx = canvas.getContext('2d');
    // Cover-crop: fill the square, crop overflow so the result is a uniform
    // tile regardless of the source aspect ratio.
    const scale = Math.max(size / img.width, size / img.height);
    const w = img.width * scale;
    const h = img.height * scale;
    ctx.drawImage(img, (size - w) / 2, (size - h) / 2, w, h);
    return canvas.toDataURL('image/jpeg', 0.7);
  } catch {
    return dataUrl; // fall back — original is still valid
  }
}

export function initScanHistoryUi(deps) {
  const {
    $, t, hide, show, toast,
    recentScansEl, recentListEl, historySearchInput, historyGradeSelect,
    clearHistoryBtn, exportHistoryBtn,
    errorEl, statusEl, resultEl,
    listScans, deleteScan, clearScans, saveScan,
    filterScanHistory, summarizeScanHistory, timeAgoBucket, todayISO,
    getQueue, setLastData,
    renderAudit, renderIngredients, renderNutrition,
    makeActivatable,
  } = deps;

  function timeAgo(ts) {
    const b = timeAgoBucket(Date.now() - ts);
    if (b.kind === 'justNow') return t('justNow');
    if (b.kind === 'minutes') return t('minutesAgo', { n: b.n });
    if (b.kind === 'hours') return t('hoursAgo', { n: b.n });
    return t('daysAgo', { n: b.n });
  }

  async function renderRecentScans() {
    const all = await listScans().catch(() => []);
    recentListEl.textContent = '';
    if (all.length === 0) { hide(recentScansEl); return; }
    const items = filterScanHistory(all, {
      query: historySearchInput?.value || '',
      gradeFilter: historyGradeSelect?.value || '',
    });

    // Summary chip — "12 scans · 4A · 3B …". Uses the unfiltered `all`
    // so the chip stays stable while the user filters the list.
    const summaryEl = $('recent-summary');
    if (summaryEl) {
      summaryEl.textContent = '';
      const sum = summarizeScanHistory(all);
      const total = document.createElement('span');
      total.textContent = t('recentSummaryTotal', { n: sum.total });
      summaryEl.appendChild(total);
      const activeGrade = historyGradeSelect?.value || '';
      for (const g of ['A+', 'A', 'B', 'C', 'D', 'F']) {
        if (sum.byGrade[g] === 0) continue;
        summaryEl.appendChild(document.createTextNode(' · '));
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'recent-summary-grade';
        btn.dataset.grade = g;
        if (g === activeGrade) btn.dataset.active = 'true';
        btn.textContent = `${sum.byGrade[g]}${g}`;
        btn.setAttribute('aria-label', t('recentSummaryFilter', { n: sum.byGrade[g], grade: g }));
        btn.setAttribute('aria-pressed', g === activeGrade ? 'true' : 'false');
        btn.addEventListener('click', () => {
          if (!historyGradeSelect) return;
          // Toggle: clicking the active grade clears the filter.
          historyGradeSelect.value = (g === activeGrade) ? '' : g;
          renderRecentScans();
        });
        summaryEl.appendChild(btn);
      }
      show(summaryEl);
    }

    if (items.length === 0) {
      const li = document.createElement('li');
      li.className = 'recent-empty';
      li.textContent = '—';
      recentListEl.appendChild(li);
      show(recentScansEl);
      return;
    }

    for (const item of items.slice(0, 12)) {
      const li = document.createElement('li');
      li.className = 'recent-item';
      li.dataset.id = item.id;
      const thumb = document.createElement('div');
      thumb.className = 'recent-thumb';
      if (item.thumbnail) {
        const img = document.createElement('img');
        img.src = item.thumbnail;
        img.alt = '';
        img.loading = 'lazy';
        img.decoding = 'async';
        img.width = 48;
        img.height = 48;
        thumb.appendChild(img);
      } else {
        thumb.textContent = '📦';
      }
      const meta = document.createElement('div');
      meta.className = 'recent-meta';
      const grade = document.createElement('span');
      grade.className = 'recent-grade';
      grade.dataset.grade = item.grade;
      grade.textContent = item.grade;
      const name = document.createElement('strong');
      name.className = 'recent-name';
      name.textContent = item.name;
      const when = document.createElement('small');
      when.className = 'recent-when';
      // Fix #17: show personal score when it was stored (profile was active at
      // scan time). Format: "72/100 · personal 65 • 3 days ago"
      const personalPart = item.personal_score != null
        ? ` · personal ${item.personal_score}`
        : '';
      when.textContent = `${item.score}/100${personalPart} • ${timeAgo(item.createdAt)}`;
      meta.appendChild(grade);
      meta.appendChild(name);
      meta.appendChild(when);
      const del = document.createElement('button');
      del.className = 'recent-del';
      del.type = 'button';
      del.setAttribute('aria-label', item.name
        ? `${t('removeFromHistory')} — ${item.name}`
        : t('removeFromHistory'));
      del.textContent = '×';
      del.addEventListener('click', async (e) => {
        e.stopPropagation();
        await deleteScan(item.id);
        renderRecentScans();
      });
      li.appendChild(thumb);
      li.appendChild(meta);
      li.appendChild(del);
      makeActivatable(li, () => reopenScan(item));
      li.setAttribute('aria-label', item.name
        ? `${t('reopenScan')} — ${item.name}`
        : t('reopenScan'));
      recentListEl.appendChild(li);
    }
    if (items.length > 12) {
      const hint = document.createElement('li');
      hint.className = 'recent-overflow';
      hint.textContent = t('recentOverflow', { shown: 12, total: items.length });
      recentListEl.appendChild(hint);
    }
    show(recentScansEl);
  }

  function reopenScan(item) {
    if (!item.snapshot) return;
    setLastData(item.snapshot);
    hide(errorEl);
    hide(statusEl);
    renderAudit(item.snapshot);
    renderIngredients(item.snapshot.product);
    renderNutrition(item.snapshot.product);
    show(resultEl);
    // Respect prefers-reduced-motion + the in-app motion preference so the
    // screen doesn't lurch for users sensitive to smooth-scroll animations.
    const reduced = document.body.classList.contains('reduce-motion')
      || window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
    resultEl.scrollIntoView({ behavior: reduced ? 'auto' : 'smooth', block: 'start' });
  }

  async function persistToHistory(data) {
    const queue = getQueue();
    const raw = queue.find((q) => q.dataUrl && q.dataUrl.startsWith('data:image'))?.dataUrl
      ?? '';
    try {
      const thumb = raw ? await makeThumbnail(raw, 96) : '';
      await saveScan({
        id: crypto.randomUUID(),
        createdAt: Date.now(),
        thumbnail: thumb,
        name: data.audit.product_name || data.product.name,
        grade: data.audit.grade,
        score: data.audit.score,
        // Fix #17: store personal_score so the history view can show how
        // the user's dietary context changed the evaluation over time.
        personal_score: data.personal_score ?? null,
        category: data.audit.category,
        // Nutri-Score from OFF for weight calibration correlation.
        nutriscore_grade: data.audit.nutriscore_grade ?? null,
        source: data.source,
        snapshot: data,
      });
      renderRecentScans();
    } catch (err) {
      // History is a convenience surface — never fail the scan flow because
      // we couldn't persist the snapshot. Quota-recovery already tried once.
      console.warn('[history] persist failed', err);
    }
  }

  // -- Wire control surface --

  clearHistoryBtn?.addEventListener('click', async () => {
    await clearScans();
    renderRecentScans();
  });

  exportHistoryBtn?.addEventListener('click', async () => {
    const items = await listScans().catch(() => []);
    if (items.length === 0) { toast(t('exportHistoryEmpty'), 'warn'); return; }
    const blob = new Blob([JSON.stringify(items, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    // todayISO() uses the user's local date via Intl.DateTimeFormat —
    // replaces `new Date().toISOString().slice(0, 10)` which picks UTC and
    // gave western-tz users a filename for tomorrow around midnight.
    a.href = url;
    a.download = `scanneat-history-${todayISO()}.json`;
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 5000);
    toast(t('exportHistoryDone', { n: items.length }), 'ok');
  });

  // Debounced filter wiring — search input fires on every keystroke,
  // unbounded re-renders were a perceptible jank source on histories
  // with 100+ scans.
  const debouncedRender = debounce(renderRecentScans, HISTORY_FILTER_DEBOUNCE_MS);
  historySearchInput?.addEventListener('input', debouncedRender);
  historyGradeSelect?.addEventListener('change', () => renderRecentScans());

  return { renderRecentScans, persistToHistory, reopenScan };
}
