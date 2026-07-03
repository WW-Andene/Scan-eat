#!/usr/bin/env node
// Re-drift guard for public/app.js (ADR-0004).
//
// app.js is supposed to be bootstrap + deps-wiring only — every named,
// function-level cluster of behaviour belongs in features/, core/, or
// state/. ADR-0004 was accepted on exactly this premise, drifted once
// already (app.js grew from ~400 lines back up to 3,718 before the
// 13-phase decomposition documented in restructuring-plan.md), and had
// no tooling to catch it. This script is that tooling.
//
// It does NOT enforce the original 400-500 LOC target — that target
// undercounted legitimate inline event-listener wiring (see ADR-0004's
// 2026-06-30 status update). Instead it caps the file at a small margin
// above its current, post-cleanup size, so the next feature that pushes
// it past the cap forces a deliberate "extract this into features/, or
// is this really just glue?" decision instead of silent growth.
//
// Usage: node tools/check-app-size.mjs
// Exit code 0 = under cap, 1 = over cap (fails CI / npm test).

import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const APP_JS = path.join(__dirname, '..', 'public', 'app.js');

// Baseline after the Phase 13 drift-correction sweep was ~1,860 lines;
// a Phase 14 follow-up pass trimmed five more UI clusters out, bringing
// it to ~1,600. Cap gives ~150 lines of slack for small additions
// before requiring a conscious decision (raise the cap with
// justification, or extract).
const CAP = 1750;

function countLines(filePath) {
  const text = readFileSync(filePath, 'utf8');
  // Trailing newline shouldn't count as an extra line.
  const lines = text.split('\n');
  if (lines.length > 0 && lines[lines.length - 1] === '') lines.pop();
  return lines.length;
}

const lineCount = countLines(APP_JS);

if (lineCount > CAP) {
  console.error(
    `\n✗ public/app.js is ${lineCount} lines — over the ${CAP}-line cap.\n\n` +
    `This file is meant to stay bootstrap + deps-wiring only (ADR-0004).\n` +
    `Before adding more code here, ask: does this belong in its own\n` +
    `features/<name>.js (or core/, state/) module instead, wired in via\n` +
    `the existing deps-object convention?\n\n` +
    `If the growth really is unavoidable wiring, raise CAP in\n` +
    `tools/check-app-size.mjs with a one-line reason in the commit\n` +
    `message — don't just delete this check.\n`,
  );
  process.exit(1);
}

console.log(`✓ public/app.js is ${lineCount} lines (cap: ${CAP}).`);
process.exit(0);
