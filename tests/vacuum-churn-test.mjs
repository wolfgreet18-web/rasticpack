// اندازه‌گیری اثر «عدم اجرای VACUUM دوره‌ای» + هزینه‌ی خودِ VACUUM — نسخه‌ی ۴.۸ نقشه‌راه
//
// این تست، ادامه‌ی مستقیم db-file-size-test.mjs (نسخه‌ی ۴.۷) است. آن تست فقط
// رشد یک‌طرفه (insert-only) را اندازه گرفت و صراحتاً نوشت: «بدون VACUUM دوره‌ای
// ... فایل واقعی به‌مرور از این عدد بزرگ‌تر می‌ماند چون صفحات حذف‌شده توسط
// SQLite بازیافت نمی‌شوند مگر VACUUM دستی اجرا شود» — ولی هیچ عددی برای این
// موضوع ثبت نکرد. این همان ردیف باز مانده در جدول ریسک (بخش ۶): «تصمیم نهایی
// درباره‌ی نیاز به VACUUM دوره‌ای ... هنوز باز است».
//
// این تست آن شکاف را با یک سناریوی واقع‌بینانه‌تر از یک اپ فاکتورنویسی که
// سال‌ها در حال استفاده است می‌بندد: نه فقط insert خالص، بلکه چرخه‌ی
// حذف+درج (churn) — دقیقاً همان الگویی که صفحات آزاد/خالی در فایل SQLite
// تولید می‌کند و باعث می‌شود VACUUM موضوعیت پیدا کند (یک دیتابیس insert-only
// خالص اصلاً به VACUUM نیازی ندارد؛ مسئله فقط وقتی مطرح می‌شود که حذف هم رخ دهد).
//
// روش:
//   ۱) دیتابیس فایلی واقعی روی دیسک با ۱۵٬۰۰۰ مشتری + ۵۰۰٬۰۰۰ فاکتور seed می‌شود
//      (steady-state realistic، نه جدول خالی) — حجم پایه اندازه‌گیری می‌شود.
//   ۲) ۱۰ دور «churn» شبیه‌سازی می‌شود: در هر دور، ۵٪ فاکتورهای موجود (تصادفی،
//      نه انتهای جدول) حذف می‌شوند و همان تعداد فاکتور جدید insert می‌شود —
//      شبیه یک اپ که فاکتورهای اشتباه/لغوشده را پاک و فاکتور جدید ثبت می‌کند.
//      تعداد کل ردیف‌ها عملاً ثابت می‌ماند (۵۰۰k)، فقط جابه‌جایی داخلی رخ می‌دهد.
//   ۳) دو مسیر مستقل و کاملاً جدا مقایسه می‌شوند:
//      - «بدون VACUUM»: بعد از تمام ۱۰ دور churn، حجم نهایی فایل چقدر است؟
//      - «با VACUUM دوره‌ای»: اگر بعد از هر دور VACUUM اجرا شود، حجم نهایی و
//        مجموع زمان صرف‌شده در خودِ VACUUM چقدر است؟
//   ۴) نتیجه: هم عدد «باد کردن فایل» (bloat) بدون VACUUM، هم «هزینه‌ی زمانی»
//      VACUUM دوره‌ای، تا تصمیم آگاهانه درباره‌ی نیاز به آن گرفته شود.
//
// محدودیت شناخته‌شده (مثل همه‌ی تست‌های قبلی این پروژه): node:sqlite روی
// کانتینر توسعه، نه پلاگین @capacitor-community/sqlite روی دستگاه واقعی.
// VACUUM روی دستگاه‌های ضعیف‌تر (CPU/IO کندتر از سرور) می‌تواند کندتر باشد —
// این تست فقط نسبت هزینه/فایده را در همان مرتبه‌ی بزرگی نشان می‌دهد، نه عدد
// دقیق روی موبایل. VACUUM هم‌چنین دیتابیس را حین اجرا قفل می‌کند (نوشتن دیگر
// در آن لحظه ممکن نیست)، که این تست زمانش را اندازه می‌گیرد ولی رفتار قفل را
// مستقیماً تست نمی‌کند (تک‌رشته‌ای است).
//
// اجرا: node --experimental-sqlite tests/vacuum-churn-test.mjs

import { DatabaseSync } from 'node:sqlite';
import { SCHEMA_STATEMENTS } from '../rasticpack-capacitor/src/db/schema.js';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';

const N_CUSTOMERS = 15000;
const N_INVOICES_BASE = 500000;
const CHURN_ROUNDS = 10;
const CHURN_FRACTION = 0.05; // ۵٪ از رکوردهای موجود در هر دور حذف+جایگزین می‌شوند

function fmtMB(bytes) {
  return (bytes / (1024 * 1024)).toFixed(2) + ' MB';
}

function seedSchema(dbPath) {
  if (fs.existsSync(dbPath)) fs.unlinkSync(dbPath);
  const db = new DatabaseSync(dbPath);
  for (const stmt of SCHEMA_STATEMENTS) db.exec(stmt);
  return db;
}

function seedBaseline(db) {
  db.exec('BEGIN');
  const custStmt = db.prepare(
    `INSERT INTO customers (id, name, company, phone) VALUES (?, ?, ?, ?)`
  );
  for (let i = 1; i <= N_CUSTOMERS; i++) {
    custStmt.run(i, `مشتری تستی ${i}`, '', '0912' + String(1000000 + i));
  }
  db.exec('COMMIT');

  const statuses = ['draft', 'partial', 'paid'];
  db.exec('BEGIN');
  const invStmt = db.prepare(
    `INSERT INTO invoices (id, customerId, customerName, date, status, sent, sentToProduction, paidAmount, totalSheets, data)
     VALUES (?,?,?,?,?,?,?,?,?,?)`
  );
  for (let i = 1; i <= N_INVOICES_BASE; i++) {
    const custId = (i % N_CUSTOMERS) + 1;
    const date = new Date(2024, 0, 1 + (i % 700)).toISOString();
    invStmt.run(
      i, custId, `مشتری تستی ${custId}`, date, statuses[i % 3], i % 2, 0,
      (i % 3 === 1) ? 50000 : 0, (i % 20) + 1,
      JSON.stringify({ items: [{ lineTotal: 1000 }] })
    );
  }
  db.exec('COMMIT');
}

// یک دور churn: CHURN_FRACTION از رکوردهای موجود (با id تصادفی پراکنده در کل
// بازه‌ی id ها، نه فقط انتهای جدول) حذف و همان تعداد رکورد جدید insert می‌شود.
function churnRound(db, nextIdRef, currentIds) {
  const delStmt = db.prepare(`DELETE FROM invoices WHERE id = ?`);
  const insStmt = db.prepare(
    `INSERT INTO invoices (id, customerId, customerName, date, status, sent, sentToProduction, paidAmount, totalSheets, data)
     VALUES (?,?,?,?,?,?,?,?,?,?)`
  );
  const statuses = ['draft', 'partial', 'paid'];
  const churnCount = Math.floor(currentIds.length * CHURN_FRACTION);

  db.exec('BEGIN');
  for (let k = 0; k < churnCount; k++) {
    // انتخاب تصادفی یک id موجود برای حذف (پراکنده در کل جدول، نه فقط انتها)
    const idx = Math.floor(Math.random() * currentIds.length);
    const idToDelete = currentIds[idx];
    delStmt.run(idToDelete);

    const newId = nextIdRef.value++;
    const custId = (newId % N_CUSTOMERS) + 1;
    const date = new Date(2024, 0, 1 + (newId % 700)).toISOString();
    insStmt.run(
      newId, custId, `مشتری تستی ${custId}`, date, statuses[newId % 3], newId % 2, 0,
      (newId % 3 === 1) ? 50000 : 0, (newId % 20) + 1,
      JSON.stringify({ items: [{ lineTotal: 1000 }] })
    );
    currentIds[idx] = newId; // جایگزینی در آرایه‌ی ردیابی
  }
  db.exec('COMMIT');

  return churnCount;
}

async function runScenario(label, applyVacuumEachRound) {
  const dbPath = path.join(os.tmpdir(), `vacuum-churn-test-${label}-${process.pid}.sqlite`);
  const db = seedSchema(dbPath);
  seedBaseline(db);

  const baselineSize = fs.statSync(dbPath).size;

  const currentIds = Array.from({ length: N_INVOICES_BASE }, (_, i) => i + 1);
  const nextIdRef = { value: N_INVOICES_BASE + 1 };

  let totalVacuumMs = 0;
  const rowSizeLog = [];

  for (let round = 1; round <= CHURN_ROUNDS; round++) {
    churnRound(db, nextIdRef, currentIds);

    if (applyVacuumEachRound) {
      const t0 = process.hrtime.bigint();
      db.exec('VACUUM');
      const t1 = process.hrtime.bigint();
      totalVacuumMs += Number(t1 - t0) / 1e6;
    }

    const sizeNow = fs.statSync(dbPath).size;
    rowSizeLog.push({ round, size: sizeNow });
  }

  const finalSizeRaw = fs.statSync(dbPath).size;
  db.close();
  fs.unlinkSync(dbPath);

  return { label, baselineSize, finalSizeRaw, totalVacuumMs, rowSizeLog, totalRows: currentIds.length };
}

async function main() {
  console.log(`Seed پایه: ${N_INVOICES_BASE.toLocaleString('en-US')} فاکتور / ${N_CUSTOMERS.toLocaleString('en-US')} مشتری`);
  console.log(`${CHURN_ROUNDS} دور churn، هر دور ${(CHURN_FRACTION * 100).toFixed(0)}٪ حذف+جایگزینی (~${Math.floor(N_INVOICES_BASE * CHURN_FRACTION).toLocaleString('en-US')} رکورد/دور)\n`);

  console.log('در حال اجرای سناریوی «بدون VACUUM»...');
  const noVacuum = await runScenario('no-vacuum', false);

  console.log('در حال اجرای سناریوی «با VACUUM بعد از هر دور»...');
  const withVacuum = await runScenario('with-vacuum', true);

  console.log('\n| سناریو | حجم پایه (۵۰۰k، قبل از churn) | حجم نهایی (بعد از ۱۰ دور churn) | نسبت رشد | زمان کل صرف‌شده در VACUUM |');
  console.log('|---|---|---|---|---|');
  console.log(
    `| بدون VACUUM | ${fmtMB(noVacuum.baselineSize)} | ${fmtMB(noVacuum.finalSizeRaw)} | ${(noVacuum.finalSizeRaw / noVacuum.baselineSize).toFixed(2)}x | — |`
  );
  console.log(
    `| با VACUUM هر دور | ${fmtMB(withVacuum.baselineSize)} | ${fmtMB(withVacuum.finalSizeRaw)} | ${(withVacuum.finalSizeRaw / withVacuum.baselineSize).toFixed(2)}x | ${withVacuum.totalVacuumMs.toFixed(0)}ms (میانگین ${(withVacuum.totalVacuumMs / CHURN_ROUNDS).toFixed(0)}ms/دور) |`
  );

  const bloatBytes = noVacuum.finalSizeRaw - withVacuum.finalSizeRaw;
  console.log(`\nتفاوت مطلق حجم نهایی (بدون VACUUM منهای با VACUUM): ${fmtMB(bloatBytes)}`);
  console.log(`تعداد ردیف نهایی هر دو سناریو یکسان است (${noVacuum.totalRows.toLocaleString('en-US')} فاکتور) — این تفاوت فقط از صفحات آزادِ بازیافت‌نشده است، نه داده‌ی واقعی بیشتر.`);
}

main().catch(e => {
  console.error('خطا:', e);
  process.exit(1);
});
