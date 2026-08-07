// اندازه‌گیری حجم واقعی فایل دیتابیس روی دیسک — نسخه‌ی ۴.۷ نقشه‌راه
//
// این تست، برخلاف perf-test-1m.mjs و single-write-cost-test.mjs (که هر دو
// از ':memory:' استفاده می‌کنند)، یک دیتابیس *فایل‌محور* واقعی روی دیسک کانتینر
// می‌سازد و بعد از seed در نقاط رشد مختلف (۱۰۰k / ۵۰۰k / ۱M فاکتور، با ۱۵٬۰۰۰
// مشتری ثابت) واقعاً حجم فایل .db را با fs.statSync اندازه می‌گیرد — هم قبل و
// هم بعد از VACUUM. هدف: بستن ردیف باز در بخش ۶ نقشه‌راه («حجم فایل دیتابیس
// روی گوشی‌های حافظه‌محدود مشکل ایجاد کند» — که مقابله‌اش «تعیین سقف واقعی بر
// اساس فضای ذخیره‌سازی هدف در فاز ۰» بود و تا این نسخه هیچ عدد واقعی نداشت).
//
// محدودیت شناخته‌شده (مثل همه‌ی تست‌های قبلی این پروژه): این حجم روی موتور
// node:sqlite در کانتینر توسعه اندازه‌گیری می‌شود، نه پلاگین
// @capacitor-community/sqlite روی یک دستگاه اندروید/iOS واقعی. صفحه‌بندی
// (page_size)، حالت journal، و رفتار VACUUM ممکن است بین SQLite دسکتاپ/سرور
// (که node:sqlite از آن استفاده می‌کند) و نسخه‌ی باندل‌شده‌ی پلاگین کپیسیتور
// کمی فرق داشته باشد؛ ولی چون هر دو از فرمت فایل استاندارد SQLite3 با همان
// schema/داده استفاده می‌کنند، عدد به‌دست‌آمده باید در همان مرتبه‌ی بزرگی
// (order of magnitude) با دستگاه واقعی باشد.
//
// اجرا: node --experimental-sqlite tests/db-file-size-test.mjs

import { DatabaseSync } from 'node:sqlite';
import { SCHEMA_STATEMENTS } from '../rasticpack-capacitor/src/db/schema.js';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';

const N_CUSTOMERS = 15000;
const CHECKPOINTS = [100000, 500000, 1000000]; // فاکتور تجمعی در هر نقطه
const DB_PATH = path.join(os.tmpdir(), `db-file-size-test-${process.pid}.sqlite`);

function fmtMB(bytes) {
  return (bytes / (1024 * 1024)).toFixed(2) + ' MB';
}

function fmtBytesPerRow(bytes, rows) {
  return (bytes / rows).toFixed(1) + ' bytes/row';
}

async function main() {
  if (fs.existsSync(DB_PATH)) fs.unlinkSync(DB_PATH);

  const raw = new DatabaseSync(DB_PATH);
  // page_size پیش‌فرض SQLite را نگه می‌داریم (بدون PRAGMA سفارشی) — دقیقاً
  // همان تنظیمات پیش‌فرضی که connection.js پروژه هم به‌کار می‌برد (بدون
  // override دستی page_size/journal_mode)
  for (const stmt of SCHEMA_STATEMENTS) raw.exec(stmt);

  console.log(`دیتابیس فایلی: ${DB_PATH}`);
  console.log(`Seeding ${N_CUSTOMERS} مشتری...`);
  raw.exec('BEGIN');
  const custStmt = raw.prepare(
    `INSERT INTO customers (id, name, company, phone) VALUES (?, ?, ?, ?)`
  );
  for (let i = 1; i <= N_CUSTOMERS; i++) {
    custStmt.run(i, `مشتری تستی ${i}`, '', '0912' + String(1000000 + i));
  }
  raw.exec('COMMIT');

  const statuses = ['draft', 'partial', 'paid'];
  const invStmt = raw.prepare(
    `INSERT INTO invoices (id, customerId, customerName, date, status, sent, sentToProduction, paidAmount, totalSheets, data)
     VALUES (?,?,?,?,?,?,?,?,?,?)`
  );

  console.log('\n| رکوردها (فاکتور) | حجم فایل (raw, بعد از COMMIT) | حجم فایل بعد از VACUUM | بایت به‌ازای هر فاکتور (بعد از VACUUM) |');
  console.log('|---|---|---|---|');

  let written = 0;
  for (const target of CHECKPOINTS) {
    raw.exec('BEGIN');
    for (let i = written + 1; i <= target; i++) {
      const custId = (i % N_CUSTOMERS) + 1;
      const date = new Date(2024, 0, 1 + (i % 700)).toISOString();
      invStmt.run(
        i, custId, `مشتری تستی ${custId}`, date, statuses[i % 3], i % 2, 0,
        (i % 3 === 1) ? 50000 : 0, (i % 20) + 1,
        JSON.stringify({ items: [{ lineTotal: 1000 }] })
      );
    }
    raw.exec('COMMIT');
    written = target;

    const rawSize = fs.statSync(DB_PATH).size;
    raw.exec('VACUUM');
    const vacuumedSize = fs.statSync(DB_PATH).size;

    console.log(
      `| ${target.toLocaleString('en-US')} | ${fmtMB(rawSize)} | ${fmtMB(vacuumedSize)} | ${fmtBytesPerRow(vacuumedSize, target)} |`
    );
  }

  raw.close();

  const finalSize = fs.statSync(DB_PATH).size;
  console.log(`\nحجم نهایی فایل (۱M فاکتور + ۱۵k مشتری، بعد از VACUUM): ${fmtMB(finalSize)}`);
  console.log(`برون‌یابی خطی برای ۱۰M فاکتور (تقریبی): ${fmtMB(finalSize * 10)}`);

  fs.unlinkSync(DB_PATH);
  console.log('\n(فایل موقت پاک شد)');
}

main().catch(e => {
  console.error('خطا:', e);
  if (fs.existsSync(DB_PATH)) fs.unlinkSync(DB_PATH);
  process.exit(1);
});
