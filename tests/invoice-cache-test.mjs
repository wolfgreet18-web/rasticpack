// تست واحد مستقل InvoiceCache — ریزمرحله‌ی ۲.۷.۱ (نسخه‌ی ۴.۱۲)
// اجرا: node tests/invoice-cache-test.mjs
import { InvoiceCache } from '../rasticpack-capacitor/src/cache/InvoiceCache.js';

let pass = 0, fail = 0;
function assert(cond, msg) {
  if (cond) { pass++; }
  else { fail++; console.error('❌ FAIL:', msg); }
}

// 1) get/set پایه
{
  const c = new InvoiceCache(3);
  assert(c.get(1) === undefined, 'miss روی کش خالی باید undefined بدهد');
  c.set(1, { id: 1, v: 'a' });
  assert(c.get(1)?.v === 'a', 'hit باید همان مقدار set‌شده را برگرداند');
  assert(c.size === 1, 'size باید ۱ باشد');
}

// 2) eviction وقتی ظرفیت پر می‌شود (قدیمی‌ترین حذف شود)
{
  const c = new InvoiceCache(2);
  c.set(1, { id: 1 });
  c.set(2, { id: 2 });
  c.set(3, { id: 3 }); // باید 1 را evict کند
  assert(c.size === 2, 'size نباید از ظرفیت بیشتر شود');
  assert(c.has(1) === false, 'قدیمی‌ترین رکورد (id=1) باید evict شده باشد');
  assert(c.has(2) === true && c.has(3) === true, 'رکوردهای جدیدتر باید بمانند');
}

// 3) get باید ترتیب LRU را به‌روز کند (رکورد استفاده‌شده دیرتر evict شود)
{
  const c = new InvoiceCache(2);
  c.set(1, { id: 1 });
  c.set(2, { id: 2 });
  c.get(1); // 1 را «تازه استفاده‌شده» می‌کند؛ حالا 2 قدیمی‌ترین است
  c.set(3, { id: 3 }); // باید 2 را evict کند، نه 1
  assert(c.has(1) === true, 'رکورد اخیراً get‌شده نباید evict شود');
  assert(c.has(2) === false, 'رکورد استفاده‌نشده باید evict شود');
}

// 4) set روی id تکراری، ظرفیت را اشغال نمی‌کند و eviction اضافه نمی‌زند
{
  const c = new InvoiceCache(2);
  c.set(1, { id: 1, v: 1 });
  c.set(2, { id: 2, v: 1 });
  c.set(1, { id: 1, v: 2 }); // به‌روزرسانی، نه رکورد جدید
  assert(c.size === 2, 'به‌روزرسانی id موجود نباید size را افزایش دهد');
  assert(c.get(1)?.v === 2, 'مقدار باید به‌روز شده باشد');
  assert(c.has(2) === true, 'رکورد دیگر نباید تحت تأثیر قرار گیرد');
}

// 5) setMany + values() به ترتیب LRU
{
  const c = new InvoiceCache(5);
  c.setMany([{ id: 1 }, { id: 2 }, { id: 3 }]);
  assert(c.size === 3, 'setMany باید همه‌ی رکوردها را اضافه کند');
  assert(c.values().map(v => v.id).join(',') === '1,2,3', 'values باید ترتیب LRU (قدیمی→جدید) را حفظ کند');
}

// 6) delete/clear
{
  const c = new InvoiceCache(3);
  c.setMany([{ id: 1 }, { id: 2 }]);
  assert(c.delete(1) === true, 'delete روی رکورد موجود باید true برگرداند');
  assert(c.has(1) === false, 'بعد از delete نباید در کش باشد');
  c.clear();
  assert(c.size === 0, 'بعد از clear باید کاملاً خالی باشد');
}

// 7) اعتبارسنجی ظرفیت نامعتبر
{
  let threw = false;
  try { new InvoiceCache(0); } catch { threw = true; }
  assert(threw, 'ظرفیت ۰ یا منفی باید خطا پرتاب کند');
}

console.log(`\n${pass} پاس، ${fail} خطا`);
if (fail > 0) process.exit(1);
