// Fails the build when widget.js grows past its budget: it loads on every page of every customer site. 32 KB since the
// screen reader labels, focus handling and high-contrast styles (about 1.2 KB, 0.4 KB gzip)
import { readFileSync } from 'node:fs';
import { gzipSync } from 'node:zlib';

const BUDGET_BYTES = 32 * 1024;
const file = new URL('../dist/widget.js', import.meta.url);
const bytes = readFileSync(file);
const gzipped = gzipSync(bytes).length;

console.log(`widget.js: ${(bytes.length / 1024).toFixed(1)} KB, ${(gzipped / 1024).toFixed(1)} KB gzip`);
if (bytes.length > BUDGET_BYTES) {
  console.error(`widget.js exceeds its ${BUDGET_BYTES / 1024} KB budget`);
  process.exit(1);
}
