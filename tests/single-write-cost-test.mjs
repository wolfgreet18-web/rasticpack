// تست هزینه‌ی «نوشتن تکی» (نه bulk-insert) که ایندکس سوم (idx_inv_date_status_sent،
// اضافه‌شده در نسخه‌ی ۴.۵ روادمپ) روی INSERT/UPDATE تک‌ردیفی تحمیل می‌کند.
// این دقیقاً همان شکاف مستندشده در بخش ۶ (ریسک idx_inv_date_status_sent، بند ⓑ)
// و در یادداشت نسخه‌ی ۴.۵ است: «این آزمایش مستقیم هزینه‌ی نوشتن تکی را اندازه نگرفت».
//
// روش: دو دیتابیس node:sqlite در حافظه ساخته می‌شود — یکی دقیقاً با schema فعلی
// (۶ ایندکس روی invoices، شامل idx_inv_date_status_sent)، دیگری با همان schema
// منهای فقط همان یک ایندکس جدید (۵ ایندکس، معادل نسخه‌ی ۴.۴). هر دو با ۱M ردیف
// seed می‌شوند (steady-state، نه جدول خالی) و سپس N عملیات INSERT تکی و N عملیات
// UPDATE تکی (نه در یک تراکنش bulk) روی هرکدام اندازه‌گیری و مقایسه می‌شود.
//
// اجرا: node --experimental-sqlite tests/single-write-cost-test.mjs

import { DatabaseSync } from 'node:sqlite';
import { SCHEMA_STATEMENTS } from '../rasticpack-capacitor/src/db/schema.js';

const N_CUSTOMERS = 15000;
const N_INVOICES = 1000000;
const N_SINGLE_WRITES = 2000; // تعداد نوشتن تکی برای میانگین‌گیری معتبر

// نسخه‌ی schema بدون ایندکس جدید (idx_inv_date_status_sent) — معادل نسخه‌ی ۴.۴
const SCHEMA_WITHOUT_NEW_INDEX = SCHEMA_STATEMENTS.filter(
  s => !s.includes('idx_inv_date_status_sent')
);

function buildDb(statements) {
  const raw = new DatabaseSync(':memory:');
  for (const stmt of statements) raw.exec(stmt);
  return raw;
}

function seed(raw) {
  raw.exec('BEGIN');
  const insCust = raw.prepare(`INSERT INTO customers (id, name, company, phone) VALUES (?, ?, ?, ?)`);
  for (let i = 1; i <= N_CUSTOMERS; i++) {
    insCust.run(i, `مشتری تستی ${i}`, '', '0912' + String(1000000 + i));
  }
  const insInv = raw.prepare(
    `INSERT INTO invoices (id, customerId, customerName, date, status, sent, sentToProduction, paidAmount, totalSheets, data)
     VALUES (?,?,?,?,?,?,?,?,?,?)`
  );
  const statuses = ['draft', 'partial', 'paid'];
  for (let i = 1; i <= N_INVOICES; i++) {
    const custId = (i % N_CUSTOMERS) + 1;
    const date = new Date(2024, 0, 1 + (i % 700)).toISOString();
    insInv.run(i, custId, `مشتری تستی ${custId}`, date, statuses[i % 3], i % 2, 0,
      (i % 3 === 1) ? 50000 : 0, (i % 20) + 1, JSON.stringify({ items: [{ lineTotal: 1000 }] }));
  }
  raw.exec('COMMIT');
}

function measureSingleInserts(raw, n) {
  const stmt = raw.prepare(
    `INSERT INTO invoices (id, customerId, customerName, date, status, sent, sentToProduction, paidAmount, totalSheets, data)
     VALUES (?,?,?,?,?,?,?,?,?,?)`
  );
  const times = [];
  for (let k = 0; k < n; k++) {
    const id = N_INVOICES + 1 + k;
    const custId = (id % N_CUSTOMERS) + 1;
    const date = new Date(2024, 6, 1 + (id % 30)).toISOString();
    const t0 = process.hrtime.bigint();
    stmt.run(id, custId, `مشتری تستی ${custId}`, date, 'paid', 1, 0, 10000, 5,
      JSON.stringify({ items: [{ lineTotal: 1000 }] }));
    const t1 = process.hrtime.bigint();
    times.push(Number(t1 - t0) / 1e6); // ms
  }
  return times;
}

function measureSingleUpdates(raw, n) {
  const stmt = raw.prepare(`UPDATE invoices SET status = ?, sent = ?, editedAt = ? WHERE id = ?`);
  const times = [];
  for (let k = 0; k < n; k++) {
    const id = (k % N_INVOICES) + 1;
    const t0 = process.hrtime.bigint();
    stmt.run('partial', k % 2, new Date().toISOString(), id);
    const t1 = process.hrtime.bigint();
    times.push(Number(t1 - t0) / 1e6);
  }
  return times;
}

function stats(times) {
  const sorted = [...times].sort((a, b) => a - b);
  const sum = times.reduce((a, b) => a + b, 0);
  const avg = sum / times.length;
  const p50 = sorted[Math.floor(sorted.length * 0.5)];
  const p95 = sorted[Math.floor(sorted.length * 0.95)];
  const p99 = sorted[Math.floor(sorted.length * 0.99)];
  return { avg, p50, p95, p99, total: sum };
}

function fmt(s) {
  return `avg=${s.avg.toFixed(4)}ms  p50=${s.p50.toFixed(4)}ms  p95=${s.p95.toFixed(4)}ms  p99=${s.p99.toFixed(4)}ms`;
}

async function runFor(label, statements) {
  console.log(`\n=== ${label} ===`);
  console.log(`Building schema + seeding ${N_CUSTOMERS} customers / ${N_INVOICES} invoices...`);
  let t = Date.now();
  const raw = buildDb(statements);
  seed(raw);
  console.log(`  seed done in ${Date.now() - t}ms`);

  const insertTimes = measureSingleInserts(raw, N_SINGLE_WRITES);
  const updateTimes = measureSingleUpdates(raw, N_SINGLE_WRITES);
  raw.close();

  const insStats = stats(insertTimes);
  const updStats = stats(updateTimes);
  console.log(`Single INSERT x${N_SINGLE_WRITES}: ${fmt(insStats)}`);
  console.log(`Single UPDATE x${N_SINGLE_WRITES}: ${fmt(updStats)}`);
  return { insStats, updStats };
}

async function main() {
  const withNew = await runFor('WITH idx_inv_date_status_sent (schema فعلی، ۴.۵)', SCHEMA_STATEMENTS);
  const withoutNew = await runFor('WITHOUT idx_inv_date_status_sent (معادل ۴.۴)', SCHEMA_WITHOUT_NEW_INDEX);

  console.log('\n=== مقایسه (هزینه‌ی خالص ایندکس سوم روی نوشتن تکی) ===');
  const insDeltaAvg = withNew.insStats.avg - withoutNew.insStats.avg;
  const insDeltaP99 = withNew.insStats.p99 - withoutNew.insStats.p99;
  const updDeltaAvg = withNew.updStats.avg - withoutNew.updStats.avg;
  const updDeltaP99 = withNew.updStats.p99 - withoutNew.updStats.p99;

  console.log(`INSERT avg delta: ${insDeltaAvg >= 0 ? '+' : ''}${insDeltaAvg.toFixed(4)}ms  (${(insDeltaAvg / withoutNew.insStats.avg * 100).toFixed(1)}%)`);
  console.log(`INSERT p99 delta: ${insDeltaP99 >= 0 ? '+' : ''}${insDeltaP99.toFixed(4)}ms`);
  console.log(`UPDATE avg delta: ${updDeltaAvg >= 0 ? '+' : ''}${updDeltaAvg.toFixed(4)}ms  (${(updDeltaAvg / withoutNew.updStats.avg * 100).toFixed(1)}%)`);
  console.log(`UPDATE p99 delta: ${updDeltaP99 >= 0 ? '+' : ''}${updDeltaP99.toFixed(4)}ms`);

  console.log('\nNOTE: این هم مثل بقیه‌ی تست‌های این پوشه روی node:sqlite (بدون IPC) روی');
  console.log('CPU کانتینر توسعه اندازه‌گیری شده، نه پلاگین @capacitor-community/sqlite');
  console.log('روی دستگاه واقعی. هدف این تست صرفاً جداکردن هزینه‌ی خالص ایندکس سوم از');
  console.log('نویز محیط است (با مقایسه‌ی two schemas پهلوبه‌پهلو)، نه عدد مطلق on-device.');
}

main().catch(e => { console.error('SINGLE-WRITE COST TEST FAILED:', e); process.exit(1); });
