const EVENT = 'scanneat:storage-failed';
let failedWrites = 0;

export function recordStorageFailure(detail = {}) {
  failedWrites += 1;
  try {
    window.dispatchEvent(new CustomEvent(EVENT, { detail: { ...detail, failedWrites } }));
  } catch { /* event dispatch is best-effort */ }
  return failedWrites;
}

export function storageFailureEvent() { return EVENT; }
export function storageFailureCount() { return failedWrites; }
