// تست فاز ۷ (نسخه‌ی ۴.۲۸) — denormalize کردن آیتم‌های فاکتور برای Aggregation
// تب آمار (سناریو ۵ فاز ۷، باقی‌مانده‌ی ثبت‌شده در یادداشت ۴.۲۵/۴.۲۶/۴.۲۷):
//   ۱) InvoiceRepo.save باید به‌ازای هر عنصر items[] یک ردیف در invoice_items
//      بنویسد، و ویرایش یک فاکتور (تغییر تعداد آیتم‌ها) نباید ردیف یتیم/تکراری
//      جا بگذارد (delete-then-reinsert).
//   ۲) InvoiceRepo.remove باید ردیف‌های invoice_items همان فاکتور را هم حذف کند.
//   ۳) InvoiceRepo.clearAll باید invoice_items را هم خالی کند.
//   ۴) ensureInvoiceItemsBackfilled باید فاکتورهایی را که مستقیم با SQL خام
//      (بدون عبور از InvoiceRepo.save، یعنی «داده‌ی قبل از این نسخه») درج
//      شده‌اند پوشش دهد؛ idempotent باشد (اجرای دوباره چیزی را دوبار درج نکند).
//   ۵) بعد از این تغییر، getTotalsByRange/getStatsLeaders (که در
//      stats-aggregation-test.mjs/stats-leaders-test.mjs از قبل با عدد دقیق
//      تست شده‌اند) دیگر به هیچ ردیفی در invoices.data وابسته نیستند — اینجا
//      مستقیماً روی invoice_items تأیید می‌شود تا اگر کسی روزی جدول را دستکاری
//      کند (نه از طریق save)، این تست جدا آن را می‌گیرد.
// اجرا: node --experimental-sqlite tests/invoice-items-denormalize-test.mjs

import { InvoiceRepo } from './_node_test_copies/InvoiceRepo.js';
import { getDb } from './testConnection.node-only.mjs';
import { ensureInvoiceItemsBackfilled } from './_node_test_copies/schema.js';

let pass = 0, fail = 0;
function assert(cond, msg) { if (cond) pass++; else { fail++; console.error('❌ FAIL:', msg); } }
function approx(a, b, eps = 1e-6) { return Math.abs(a - b) < eps; }

async function run() {
  const db = await getDb();
  await db.run(`INSERT INTO customers (id, name) VALUES (?, ?)`, [1, 'مشتری تست']);

  // ── ۱) save() یک فاکتور جدید با ۲ آیتم → باید ۲ ردیف در invoice_items بسازد ──
  {
    const inv = {
      id: 201, customerId: 1, customerName: 'مشتری تست', date: '2026-05-01T08:00:00.000Z',
      status: 'paid', sent: 0, sentToProduction: 0, totalSheets: 0,
      items: [
        { cartonName: 'کارتن الف', cartonLength: 10, cartonWidth: 20, cartonHeight: 5, layer: '۳لایه', cartonQty: 100, lineTotal: 1000, itemProfit: 100 },
        { cartonName: 'کارتن ب', cartonLength: 15, cartonWidth: 25, cartonHeight: 8, layer: '۵لایه', cartonQty: 50, lineTotal: 500, itemProfit: 50 }
      ]
    };
    await InvoiceRepo.save(inv);
    const rows = await db.query(`SELECT * FROM invoice_items WHERE invoiceId = ? ORDER BY cartonName`, [201]);
    assert(rows.values.length === 2, `save باید ۲ ردیف invoice_items بسازد، ${rows.values.length} ساخت`);
    assert(rows.values[0].date === inv.date, 'date باید از خودِ فاکتور denormalize شود');
    assert(rows.values[0].customerId === 1 && rows.values[0].customerName === 'مشتری تست', 'customerId/customerName باید denormalize شوند');
    assert(approx(rows.values.find(r => r.cartonName === 'کارتن الف').lineTotal, 1000), 'lineTotal آیتم اول باید ۱۰۰۰ باشد');
  }

  // ── ۲) ویرایش همان فاکتور با ۱ آیتم (کمتر از قبل) → نباید ردیف یتیم بماند ──
  {
    const invEdited = {
      id: 201, customerId: 1, customerName: 'مشتری تست', date: '2026-05-01T08:00:00.000Z',
      status: 'paid', sent: 0, sentToProduction: 0, totalSheets: 0,
      items: [
        { cartonName: 'کارتن ج', cartonLength: 30, cartonWidth: 30, cartonHeight: 10, layer: '۷لایه', cartonQty: 20, lineTotal: 2000, itemProfit: 200 }
      ]
    };
    await InvoiceRepo.save(invEdited);
    const rows = await db.query(`SELECT * FROM invoice_items WHERE invoiceId = ?`, [201]);
    assert(rows.values.length === 1, `ویرایش با ۱ آیتم باید فقط ۱ ردیف باقی بگذارد (نه ۳)، ${rows.values.length} مانده`);
    assert(rows.values[0].cartonName === 'کارتن ج', 'ردیف باقی‌مانده باید همان آیتم جدید باشد، نه یکی از آیتم‌های قدیمی');
  }

  // ── ۳) remove() باید ردیف‌های invoice_items را هم حذف کند ──
  {
    await InvoiceRepo.remove(201);
    const rows = await db.query(`SELECT * FROM invoice_items WHERE invoiceId = ?`, [201]);
    assert(rows.values.length === 0, 'remove باید همه‌ی ردیف‌های invoice_items همان فاکتور را حذف کند');
  }

  // ── ۴) بدون آیتم (items خالی/نامعتبر) → نباید خطا بدهد، فقط صفر ردیف ──
  {
    await InvoiceRepo.save({ id: 202, customerId: 1, customerName: 'مشتری تست', date: '2026-05-02T08:00:00.000Z', status: 'paid', sent: 0, sentToProduction: 0, totalSheets: 0 });
    const rows = await db.query(`SELECT * FROM invoice_items WHERE invoiceId = ?`, [202]);
    assert(rows.values.length === 0, 'فاکتور بدون items[] نباید خطا بدهد و باید ۰ ردیف بسازد');
  }

  // ── ۵) ensureInvoiceItemsBackfilled — فاکتور «قدیمی» که مستقیم با SQL خام
  //      درج شده (شبیه‌سازی داده‌ی قبل از این نسخه، بدون عبور از InvoiceRepo.save) ──
  {
    const legacyData = JSON.stringify({
      id: 301, customerId: 1, customerName: 'مشتری تست', date: '2026-05-03T08:00:00.000Z',
      items: [{ cartonName: 'کارتن قدیمی', cartonLength: 1, cartonWidth: 1, cartonHeight: 1, layer: 'تک‌لایه', cartonQty: 5, lineTotal: 300, itemProfit: 30 }]
    });
    await db.run(
      `INSERT INTO invoices (id, customerId, customerName, date, status, sent, sentToProduction, paidAmount, totalSheets, editedAt, data)
       VALUES (301, 1, 'مشتری تست', '2026-05-03T08:00:00.000Z', 'paid', 0, 0, NULL, 0, NULL, ?)`,
      [legacyData]
    );
    // قبل از backfill: هیچ ردیفی در invoice_items برای این فاکتور نیست
    const before = await db.query(`SELECT * FROM invoice_items WHERE invoiceId = ?`, [301]);
    assert(before.values.length === 0, 'قبل از backfill، فاکتور درج‌شده با SQL خام نباید در invoice_items باشد');

    // پرچم settings_kv را عمداً پاک می‌کنیم تا حالت «دیتابیسِ هنوز-backfill-نشده»
    // را شبیه‌سازی کنیم — چون این اتصال از قبل (داخل getDb، وقتی invoices خالی
    // بود) یک‌بار flag را ست کرده؛ بدون این پاک‌سازی، فراخوانی زیر فقط skip
    // می‌شد و فاکتور ۳۰۱ (که *بعد* از آن اولین backfill درج شده) هرگز پردازش نمی‌شد.
    await db.run(`DELETE FROM settings_kv WHERE key = 'invoice_items_backfilled_v1'`, []);

    const r1 = await ensureInvoiceItemsBackfilled(db);
    assert(r1.skipped === false, 'با پرچم پاک‌شده، ensureInvoiceItemsBackfilled باید واقعاً اجرا شود (skipped=false)');

    const after = await db.query(`SELECT * FROM invoice_items WHERE invoiceId = ?`, [301]);
    assert(after.values.length === 1 && after.values[0].cartonName === 'کارتن قدیمی', 'بعد از backfill، فاکتور قدیمی باید ۱ ردیف صحیح در invoice_items داشته باشد');
  }

  // ── ۶) idempotency — فراخوانی دوباره‌ی backfill نباید ردیف تکراری بسازد ──
  {
    const r2 = await ensureInvoiceItemsBackfilled(db);
    assert(r2.skipped === true, 'دومین فراخوانی باید به‌خاطر پرچم settings_kv بی‌صدا skip شود');
    const rows = await db.query(`SELECT * FROM invoice_items WHERE invoiceId = ?`, [301]);
    assert(rows.values.length === 1, 'فراخوانی دوباره‌ی backfill نباید ردیف را تکراری کند');
  }

  // ── ۷) getTotalsByRange/getStatsLeaders روی همین داده‌ها (شامل رکورد backfill‌شده) ──
  {
    const totals = await InvoiceRepo.getTotalsByRange('2026-05-03T00:00:00.000Z', '2026-05-04T00:00:00.000Z');
    assert(totals.count === 1 && approx(totals.turnover, 300) && approx(totals.profit, 30), `getTotalsByRange باید فاکتور backfill‌شده را هم به‌درستی جمع بزند — ${JSON.stringify(totals)}`);

    const leaders = await InvoiceRepo.getStatsLeaders('2026-05-03T00:00:00.000Z', '2026-05-04T00:00:00.000Z');
    assert(leaders.topCarton && leaders.topCarton.cartonName === 'کارتن قدیمی' && leaders.topCarton.qty === 5, `getStatsLeaders باید کارتن فاکتور backfill‌شده را پیدا کند — ${JSON.stringify(leaders.topCarton)}`);
    assert(leaders.topBuyer && leaders.topBuyer.customerId === 1 && approx(leaders.topBuyer.turnover, 300), 'getStatsLeaders باید topBuyer را هم از invoice_items بگیرد');
  }

  // ── ۸) clearAll باید invoice_items را هم خالی کند ──
  {
    await InvoiceRepo.clearAll();
    const invRows = await db.query(`SELECT * FROM invoices`, []);
    const itemRows = await db.query(`SELECT * FROM invoice_items`, []);
    assert(invRows.values.length === 0, 'clearAll باید جدول invoices را خالی کند');
    assert(itemRows.values.length === 0, 'clearAll باید جدول invoice_items را هم خالی کند (نه فقط invoices)');
  }

  console.log(`\n${pass} PASS, ${fail} FAIL`);
  if (fail > 0) process.exit(1);
}

run().catch(e => { console.error('❌ خطای غیرمنتظره:', e); process.exit(1); });
