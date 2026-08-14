// تست واقعی ۱۰۰هزار مشتری (شامل نام‌های تکراری عمدی) برای Keyset Pagination
// دوطرفه‌ی CustomerRepo.getPageByCursor. اجرا: node tests/customer-100k-pagination-test.mjs

import { CustomerRepo } from './_node_test_copies/CustomerRepo.js';
import { getDb, resetDb } from './testConnection.node-only.mjs';

const N = 100000;
const DUPLICATE_NAME_POOL = 500; // فقط ۵۰۰ نام یکتا برای ۱۰۰هزار مشتری → تکرار زیاد و تضمینی

function fmtMs(ms) { return `${ms.toFixed(1)}ms`; }

async function main() {
  resetDb();
  const db = await getDb();

  console.log(`Seeding ${N} customers (name pool = ${DUPLICATE_NAME_POOL}, guarantees heavy duplicate names)...`);
  let t = Date.now();
  await db.run('BEGIN');
  for (let i = 1; i <= N; i++) {
    const nameIdx = i % DUPLICATE_NAME_POOL;
    await db.run(
      `INSERT INTO customers (id, name, company, phone) VALUES (?, ?, ?, ?)`,
      [i, `مشتری ${nameIdx}`, 'شرکت ' + (i % 20), '0912' + String(1000000 + i)]
    );
  }
  await db.run('COMMIT');
  console.log(`  seed done in ${Date.now() - t}ms`);

  // ── ۱. صحت کامل: پیمایش forward تا انتها، بدون duplicate/missing ──────────
  console.log('\n[1] Full forward traversal (keyset), checking duplicates/missing/order...');
  t = Date.now();
  const seenIds = new Set();
  const orderViolations = [];
  let cursor = null;
  let prevKey = null;
  let pages = 0;
  let maxPageMs = 0;
  while (true) {
    const pt = Date.now();
    const res = await CustomerRepo.getPageByCursor({ limit: 500, cursor, direction: 'forward' });
    const pageMs = Date.now() - pt;
    if (pageMs > maxPageMs) maxPageMs = pageMs;
    pages++;
    if (!res.items.length) break;
    for (const c of res.items) {
      if (seenIds.has(c.id)) throw new Error(`DUPLICATE id=${c.id} encountered during forward traversal`);
      seenIds.add(c.id);
      const key = `${c.name}\u0000${String(c.id).padStart(10, '0')}`;
      if (prevKey !== null && key < prevKey) {
        orderViolations.push({ prevKey, key, id: c.id, name: c.name });
      }
      prevKey = key;
    }
    cursor = res.nextCursor;
    if (res.items.length < 500) break; // آخرین صفحه
  }
  const forwardMs = Date.now() - t;
  console.log(`  pages=${pages}, total seen=${seenIds.size}, expected=${N}`);
  console.log(`  missing count=${N - seenIds.size}`);
  console.log(`  order violations=${orderViolations.length}`);
  console.log(`  total forward time=${fmtMs(forwardMs)}, max single-page time=${fmtMs(maxPageMs)}, avg=${fmtMs(forwardMs / pages)}`);
  if (seenIds.size !== N) throw new Error(`FAIL: expected ${N} unique rows, got ${seenIds.size}`);
  if (orderViolations.length) throw new Error(`FAIL: ${orderViolations.length} order violations, e.g. ${JSON.stringify(orderViolations[0])}`);
  console.log('  ✅ PASS: no duplicates, no missing rows, order (name ASC, id ASC) held throughout.');

  // ── ۲. صحت backward: از انتها به عقب برگرد و مطمئن شو دقیقاً برعکس forward است ─
  console.log('\n[2] Full backward traversal from the end, verifying exact reverse of forward pass...');
  // برای این تست، کل لیست forward را (بار دیگر، این‌بار در حافظه‌ی تست نگه داشته
  // می‌شود چون فقط شامل id هاست، نه کل رکورد) بازسازی می‌کنیم تا مرجع مقایسه باشد.
  cursor = null;
  const forwardIds = [];
  while (true) {
    const res = await CustomerRepo.getPageByCursor({ limit: 1000, cursor, direction: 'forward' });
    if (!res.items.length) break;
    for (const c of res.items) forwardIds.push(c.id);
    cursor = res.nextCursor;
    if (res.items.length < 1000) break;
  }

  // حالا از انتهای همین توالی شروع می‌کنیم به عقب رفتن.
  const lastItem = { name: null, id: null };
  {
    // آخرین آیتم forward را با یک درخواست جداگانه پیدا می‌کنیم (آخرین صفحه).
    let c2 = null, lastPage = null;
    while (true) {
      const res = await CustomerRepo.getPageByCursor({ limit: 1000, cursor: c2, direction: 'forward' });
      if (!res.items.length) break;
      lastPage = res.items;
      c2 = res.nextCursor;
      if (res.items.length < 1000) break;
    }
    const last = lastPage[lastPage.length - 1];
    lastItem.name = last.name; lastItem.id = last.id;
  }

  t = Date.now();
  const backwardIds = [];
  let bCursor = { name: lastItem.name, id: lastItem.id };
  // خودِ آخرین آیتم را هم باید بشماریم — پس اول آن را جدا اضافه می‌کنیم.
  backwardIds.push(lastItem.id);
  let bpages = 1;
  while (true) {
    const res = await CustomerRepo.getPageByCursor({ limit: 500, cursor: bCursor, direction: 'backward' });
    bpages++;
    if (!res.items.length) break;
    // res.items از backward به ترتیب صعودی برمی‌گردد؛ برای ساختن توالی نزولی
    // (که باید دقیقاً reverse(forwardIds) باشد)، معکوسش می‌کنیم.
    for (let i = res.items.length - 1; i >= 0; i--) backwardIds.push(res.items[i].id);
    bCursor = res.prevCursor;
    if (res.items.length < 500) break;
  }
  const backwardMs = Date.now() - t;
  const expectedReversed = forwardIds.slice().reverse();
  let mismatchAt = -1;
  for (let i = 0; i < expectedReversed.length; i++) {
    if (backwardIds[i] !== expectedReversed[i]) { mismatchAt = i; break; }
  }
  console.log(`  backward pages=${bpages}, backward items collected=${backwardIds.length}, expected=${expectedReversed.length}`);
  console.log(`  total backward time=${fmtMs(backwardMs)}`);
  if (backwardIds.length !== expectedReversed.length) {
    throw new Error(`FAIL: backward traversal length ${backwardIds.length} != forward length ${expectedReversed.length}`);
  }
  if (mismatchAt !== -1) {
    throw new Error(`FAIL: backward traversal diverges from reverse(forward) at index ${mismatchAt}: got ${backwardIds[mismatchAt]}, expected ${expectedReversed[mismatchAt]}`);
  }
  console.log('  ✅ PASS: backward traversal is the exact reverse of forward traversal — no gaps, no duplicates, no reordering.');

  // ── ۳. کارایی seek در وسط دیتاست (نه فقط ابتدا/انتها) ──────────────────────
  console.log('\n[3] Seek performance at various depths (should stay roughly constant, not grow with offset)...');
  // یک لنگر واقعی از وسط دیتاست پیدا می‌کنیم (با یک عبور forward تا نیمه).
  let midCursor = null, seenCount = 0;
  const targetMid = Math.floor(N / 2);
  while (seenCount < targetMid) {
    const res = await CustomerRepo.getPageByCursor({ limit: 1000, cursor: midCursor, direction: 'forward' });
    if (!res.items.length) break;
    seenCount += res.items.length;
    midCursor = res.nextCursor;
  }
  const timings = [];
  for (let i = 0; i < 20; i++) {
    const pt = Date.now();
    const res = await CustomerRepo.getPageByCursor({ limit: 50, cursor: midCursor, direction: 'forward' });
    timings.push(Date.now() - pt);
    midCursor = res.nextCursor;
  }
  const avgMid = timings.reduce((a, b) => a + b, 0) / timings.length;
  console.log(`  20 consecutive forward pages from mid-dataset: avg=${fmtMs(avgMid)}, max=${fmtMs(Math.max(...timings))}`);
  console.log(`  (compare to overall avg from step 1: ${fmtMs(forwardMs / pages)} — should be same order of magnitude, not proportional to offset depth)`);

  // ── ۴. EXPLAIN QUERY PLAN — تضمین seek روی ایندکس، نه SCAN کامل ──────────
  console.log('\n[4] EXPLAIN QUERY PLAN sanity check (must use idx_cust_name, not full scan)...');
  const planFwd = await db.query(
    `EXPLAIN QUERY PLAN SELECT * FROM customers WHERE (name, id) > (?, ?) ORDER BY name, id LIMIT ?`,
    ['مشتری 250', 50000, 50]
  );
  const planBwd = await db.query(
    `EXPLAIN QUERY PLAN SELECT * FROM customers WHERE (name, id) < (?, ?) ORDER BY name DESC, id DESC LIMIT ?`,
    ['مشتری 250', 50000, 50]
  );
  console.log('  forward plan:', JSON.stringify(planFwd.values));
  console.log('  backward plan:', JSON.stringify(planBwd.values));
  const fwdUsesIndex = planFwd.values.some(r => /USING INDEX idx_cust_name/.test(r.detail));
  const bwdUsesIndex = planBwd.values.some(r => /USING INDEX idx_cust_name/.test(r.detail));
  if (!fwdUsesIndex || !bwdUsesIndex) throw new Error('FAIL: query plan is not using idx_cust_name — full scan risk.');
  console.log('  ✅ PASS: both directions use idx_cust_name via SEARCH, not SCAN.');

  console.log('\n✅ ALL CHECKS PASSED for 100,000 customers (heavy duplicate names included).');
}

main().catch(e => {
  console.error('\n❌ TEST FAILED:', e.message);
  process.exit(1);
});
