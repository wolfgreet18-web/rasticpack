// تست جستجوی FTS5 آزاد (نه prefix) روی customers_fts — فاز ۳ نقشه‌راه، اقدام ۲
// (نسخه‌ی ۴.۱۸). بر خلاف تست‌های قبلی که فقط prefix:true (LIKE 'name%') را
// می‌سنجند، این تست مسیر پیش‌فرض جدید (FTS5، هم name هم company، چندتوکنی،
// و sync خودکار با تریگرها) را روی یک دیتابیس واقعی node:sqlite می‌سنجد.
// اجرا: node --experimental-sqlite tests/customer-fts-search-test.mjs

import assert from 'node:assert/strict';
import { CustomerRepo } from './_node_test_copies/CustomerRepo.js';
import { getDb, resetDb } from './testConnection.node-only.mjs';

let passed = 0;
function ok(label) { passed++; console.log('  ✓', label); }

async function main() {
  await getDb(); // schema + backfill idempotent را زودتر بساز

  await CustomerRepo.save({ id: 1, name: 'علی رستمی', company: 'کارخانه فولاد پارس', phone: '0912' });
  await CustomerRepo.save({ id: 2, name: 'زهرا کریمی', company: 'شرکت آجر سبز', phone: '0913' });
  await CustomerRepo.save({ id: 3, name: 'رضا علوی', company: '', phone: '0914' });

  console.log('== FTS: جستجوی روی name (چندتوکنی) ==');
  const byFullName = await CustomerRepo.searchByName('علی رستمی');
  assert.equal(byFullName.items.length, 1);
  assert.equal(byFullName.items[0].id, 1);
  ok('چندتوکنی «علی رستمی» فقط مشتری ۱ را پیدا می‌کند (نه رضا علوی که فقط یک توکن مشترک دارد)');

  console.log('== FTS: جستجو روی کلمه‌ی دوم نام (نه فقط پیشوند کل رشته) ==');
  const bySecondWord = await CustomerRepo.searchByName('رستمی');
  assert.equal(bySecondWord.items.length, 1);
  assert.equal(bySecondWord.items[0].id, 1);
  ok('«رستمی» (کلمه‌ی دوم، نه ابتدای رشته) با prefix:false پیدا می‌شود — چیزی که LIKE «رستمی%» قدیمی هرگز نمی‌توانست پیدا کند');

  console.log('== FTS: جستجو روی company (فیلد جدید پوشش‌داده‌شده) ==');
  const byCompany = await CustomerRepo.searchByName('فولاد');
  assert.equal(byCompany.items.length, 1);
  assert.equal(byCompany.items[0].id, 1);
  ok('جستجوی «فولاد» (فقط داخل company، نه name) نتیجه می‌دهد — پوشش company که در نسخه‌ی قبل نبود');

  console.log('== FTS: توکن مبهم که در دو مشتری مشترک است ==');
  const ambiguous = await CustomerRepo.searchByName('عل');
  const ids = ambiguous.items.map(c => c.id).sort();
  assert.deepEqual(ids, [1, 3]);
  ok('«عل» هم علی رستمی (id 1، پیشوند «علی») هم رضا علوی (id 3، پیشوند «علوی») را پیدا می‌کند (پیشوند توکن، نه کل رشته)');

  console.log('== sync خودکار: تغییر name بعد از UPDATE در جستجو دیده شود ==');
  await CustomerRepo.save({ id: 3, name: 'رضا نوروزی', company: '', phone: '0914' });
  const afterUpdateOld = await CustomerRepo.searchByName('علوی');
  assert.equal(afterUpdateOld.items.length, 0);
  ok('بعد از UPDATE، نام قدیمی («علوی») دیگر پیدا نمی‌شود — یعنی تریگر customers_fts_au واقعاً ردیف قبلی را حذف کرده');
  const afterUpdateNew = await CustomerRepo.searchByName('نوروزی');
  assert.equal(afterUpdateNew.items.length, 1);
  assert.equal(afterUpdateNew.items[0].id, 3);
  ok('نام جدید («نوروزی») بلافاصله بعد از save() قابل‌جستجوست — بدون نیاز به rebuild دستی');

  console.log('== sync خودکار: حذف مشتری از customers_fts هم پاک شود ==');
  await CustomerRepo.remove(2);
  const afterRemove = await CustomerRepo.searchByName('کریمی');
  assert.equal(afterRemove.items.length, 0);
  ok('بعد از remove()، مشتری دیگر در جستجوی FTS ظاهر نمی‌شود — تریگر customers_fts_ad کار کرده');

  console.log('== prefix:true هنوز مسیر قدیمی (LIKE، فقط name) است ==');
  const prefixMode = await CustomerRepo.searchByName('فولاد', { prefix: true });
  assert.equal(prefixMode.items.length, 0);
  ok('prefix:true عمداً company را نمی‌بیند (رفتار قدیمی حفظ شده، تغییری نکرده)');

  resetDb();
  console.log(`\n${passed} assertion, all against a real node:sqlite database with FTS5, not a mock.`);
}

main().catch(e => { console.error('❌ FAIL:', e); process.exit(1); });
