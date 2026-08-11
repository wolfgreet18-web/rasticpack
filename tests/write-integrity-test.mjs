// tests/write-integrity-test.mjs — فاز ۵ نقشه‌راه (نسخه‌ی ۴.۲۲)
//
// این فایل دو چیز را با node:sqlite واقعی (نه mock) تأیید می‌کند:
//
//  ۱) معیار پذیرش رسمی فاز ۵: «شبیه‌سازی کرش وسط عملیات → داده هرگز نیمه‌کاره
//     باقی نماند (rollback خودکار)». تا امروز هیچ تستی این را با یک کرش
//     واقعیِ تزریق‌شده وسط تراکنش امتحان نکرده بود (run-integration-tests.mjs
//     فقط مسیر موفق insertWithStockUpdate/deleteWithStockRestore را چک می‌کرد).
//     اینجا با پاس‌دادن یک فاکتور بی‌id (که InvoiceRepo.save/remove عمداً رویش
//     throw می‌کنند) بعد از این‌که UPDATE موجودی داخل همان تراکنش قبلاً اجرا
//     شده، یک «کرش وسط عملیات» واقعی شبیه‌سازی می‌شود.
//
//  ۲) رفتار جدید ۴.۲۲: `INSERT OR IGNORE` قبل از UPDATE دلتایی در
//     insertWithStockUpdate/deleteWithStockRestore — هم پارity با رفتار قدیم
//     (وقتی ردیف inventory از قبل هست) و هم رفتار امن جدید (وقتی نیست).
import assert from 'node:assert/strict';
import { getDb, resetDb } from './testConnection.node-only.mjs';
import { InvoiceRepo } from './_node_test_copies/InvoiceRepo.js';

let passed = 0;
function ok(msg) { passed++; console.log('  ✓ ' + msg); }

async function seedCustomer(db, id = 1, name = 'مشتری تست') {
  await db.run(`INSERT INTO customers (id, name) VALUES (?, ?)`, [id, name]);
}

async function main() {
  console.log('== فاز ۵ / ۴.۲۲: write-integrity-test.mjs ==');
  resetDb();
  const db = await getDb();
  await seedCustomer(db, 1, 'مشتری تست');

  // ── بخش ۱: پاریتی با رفتار قدیم — ردیف inventory از قبل موجود است ──
  await db.run(`INSERT INTO inventory (id, name, qty) VALUES (1, 'ورق ۱', 100)`, []);
  await InvoiceRepo.insertWithStockUpdate(
    { id: 200, customerId: 1, customerName: 'مشتری تست', date: '2025-01-01', items: [] },
    [{ itemId: 1, qtyDelta: 10 }]
  );
  let row = await db.query(`SELECT qty FROM inventory WHERE id = 1`, []);
  assert.equal(row.values[0].qty, 90);
  ok('insertWithStockUpdate: ردیف از قبل موجود → همان کسر دلتایی قبلی (پاریتی حفظ شد)');
  assert.ok(await InvoiceRepo.getById(200));
  ok('insertWithStockUpdate: ردیف فاکتور نوشته شد');

  await InvoiceRepo.deleteWithStockRestore(200, [{ itemId: 1, qtyDelta: 10 }]);
  row = await db.query(`SELECT qty FROM inventory WHERE id = 1`, []);
  assert.equal(row.values[0].qty, 100);
  ok('deleteWithStockRestore: بازگردانی دلتایی قبلی حفظ شد (پاریتی)');
  assert.equal(await InvoiceRepo.getById(200), null);
  ok('deleteWithStockRestore: ردیف فاکتور حذف شد');

  // ── بخش ۲: رفتار جدید — ردیف inventory از قبل موجود نیست ──
  await InvoiceRepo.insertWithStockUpdate(
    { id: 201, customerId: 1, customerName: 'مشتری تست', date: '2025-01-02', items: [] },
    [{ itemId: 999, qtyDelta: 7 }]
  );
  row = await db.query(`SELECT qty FROM inventory WHERE id = 999`, []);
  assert.equal(row.values.length, 1);
  ok('insertWithStockUpdate: ردیف inventory ناموجود دیگر بی‌صدا گم نمی‌شود — با INSERT OR IGNORE ساخته شد');
  assert.equal(row.values[0].qty, -7);
  ok('insertWithStockUpdate: دلتا روی qty پایه‌ی صفر درست اعمال شد (qty=-7)، نه گم‌شده');

  await InvoiceRepo.deleteWithStockRestore(201, [{ itemId: 998, qtyDelta: 5 }]);
  row = await db.query(`SELECT qty FROM inventory WHERE id = 998`, []);
  assert.equal(row.values[0].qty, 5);
  ok('deleteWithStockRestore: ردیف inventory ناموجود هم با INSERT OR IGNORE ساخته و دلتا اعمال شد');

  // ── بخش ۳: معیار پذیرش رسمی فاز ۵ — کرش وسط عملیات → rollback خودکار ──
  // فاکتور بدون id باعث می‌شود InvoiceRepo.save (که insertWithStockUpdate در
  // انتهای تراکنش صدا می‌زند) throw کند — دقیقاً بعد از این‌که UPDATE موجودی
  // از قبل داخل همان تراکنش اجرا شده. اگر rollback واقعی کار نکند، qty=999
  // با وجود شکست عملیات، ۵۰ واحد کم شده باقی می‌ماند.
  await db.run(`INSERT INTO inventory (id, name, qty) VALUES (500, 'ورق کرش‌تست', 200)`, []);
  let threw = false;
  try {
    await InvoiceRepo.insertWithStockUpdate(
      { /* id عمداً حذف شده — save() باید throw کند */ customerId: 1, customerName: 'مشتری تست', date: '2025-01-03', items: [] },
      [{ itemId: 500, qtyDelta: 50 }]
    );
  } catch (e) {
    threw = true;
  }
  assert.ok(threw, 'انتظار می‌رفت insertWithStockUpdate به‌خاطر فاکتور بی‌id throw کند');
  ok('insertWithStockUpdate: کرش شبیه‌سازی‌شده واقعاً throw کرد (نه بی‌صدا رد شد)');
  row = await db.query(`SELECT qty FROM inventory WHERE id = 500`, []);
  assert.equal(row.values[0].qty, 200);
  ok('insertWithStockUpdate: rollback خودکار — qty موجودی هنوز ۲۰۰ است، نه ۱۵۰ (داده نیمه‌کاره باقی نماند)');
  const invCountRes = await db.query(`SELECT COUNT(*) AS c FROM invoices WHERE date = '2025-01-03'`, []);
  assert.equal(Number(invCountRes.values[0].c), 0);
  ok('insertWithStockUpdate: rollback خودکار — هیچ ردیف نیمه‌کاره‌ای در invoices نماند');

  // همان تست برای deleteWithStockRestore: id ناموجود باعث می‌شود remove()
  // throw کند (id==null) — بعد از این‌که UPDATE بازگردانی موجودی اجرا شده.
  await InvoiceRepo.insertWithStockUpdate(
    { id: 202, customerId: 1, customerName: 'مشتری تست', date: '2025-01-04', items: [] },
    []
  );
  threw = false;
  try {
    await InvoiceRepo.deleteWithStockRestore(null, [{ itemId: 500, qtyDelta: 30 }]);
  } catch (e) {
    threw = true;
  }
  assert.ok(threw, 'انتظار می‌رفت deleteWithStockRestore به‌خاطر id=null در remove() throw کند');
  ok('deleteWithStockRestore: کرش شبیه‌سازی‌شده واقعاً throw کرد');
  row = await db.query(`SELECT qty FROM inventory WHERE id = 500`, []);
  assert.equal(row.values[0].qty, 200);
  ok('deleteWithStockRestore: rollback خودکار — qty موجودی هنوز ۲۰۰ است، نه ۲۳۰ (بازگردانی نیمه‌کاره اعمال نشد)');
  assert.ok(await InvoiceRepo.getById(202));
  ok('deleteWithStockRestore: rollback خودکار — فاکتور ۲۰۲ هنوز موجود است (حذف نیمه‌کاره اعمال نشد)');

  // ── بخش ۴: bulkInsert باید all-or-nothing باشد ──
  // یک batch سه‌تایی که آیتم وسطی‌اش id ندارد — InvoiceRepo.save روی آن throw
  // می‌کند؛ انتظار می‌رود هیچ‌کدام از سه فاکتور (نه حتی اولی که قبلش موفق بود)
  // نهایتاً commit نشوند، چون bulkInsert کل batch را در یک تراکنش می‌زند.
  const before = Number((await db.query(`SELECT COUNT(*) AS c FROM invoices`, [])).values[0].c);
  threw = false;
  try {
    await InvoiceRepo.bulkInsert([
      { id: 300, customerId: 1, customerName: 'مشتری تست', date: '2025-02-01', items: [] },
      { customerId: 1, customerName: 'مشتری تست', date: '2025-02-02', items: [] }, // بی‌id — باید throw کند
      { id: 301, customerId: 1, customerName: 'مشتری تست', date: '2025-02-03', items: [] }
    ]);
  } catch (e) {
    threw = true;
  }
  assert.ok(threw, 'انتظار می‌رفت bulkInsert به‌خاطر آیتم بی‌id در وسط batch throw کند');
  ok('bulkInsert: کرش وسط batch واقعاً throw کرد');
  const after = Number((await db.query(`SELECT COUNT(*) AS c FROM invoices`, [])).values[0].c);
  assert.equal(after, before);
  ok('bulkInsert: rollback خودکار — تعداد ردیف‌های invoices قبل/بعد یکی است (نه حتی فاکتور ۳۰۰ که قبل از خطا موفق بود ماند)');
  assert.equal(await InvoiceRepo.getById(300), null);
  ok('bulkInsert: فاکتور ۳۰۰ (اولین آیتم batch، قبل از خطا) هم rollback شد — all-or-nothing واقعی');

  console.log(`\n${passed} assertion پاس شد. ✅`);
}

main().catch(e => {
  console.error('❌ تست شکست خورد:', e);
  process.exit(1);
});
