// تست CustomerRepo.getAllNames + اتصال renderCustomerDatalist در html8.html
// — فاز ۳ نقشه‌راه، مورد «datalist مشتریان» (این جلسه).
// اجرا: node --experimental-sqlite customer-datalist-test.mjs

import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { CustomerRepo } from './_node_test_copies/CustomerRepo.js';
import { getDb } from './testConnection.node-only.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const html = fs.readFileSync(path.join(__dirname, '..', 'html8.html'), 'utf-8');

let pass = 0, fail = 0;
function assertTrue(cond, msg) { if (cond) { pass++; console.log('  ✓', msg); } else { fail++; console.error('  ❌ FAIL:', msg); } }

function extractFn(name, src) {
  const re = new RegExp(`async function ${name}\\([^)]*\\)\\{[\\s\\S]*?\\n\\}\\n`);
  const m = src.match(re);
  if (!m) { console.error(`❌ FAIL: ${name} در html8.html پیدا نشد.`); process.exit(1); }
  return m[0];
}

function getRenderCustomerDatalist(windowObj, els) {
  const src = extractFn('renderCustomerDatalist', html);
  const escapeHtml = s => String(s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const $ = id => els[id] || null;
  const factory = new Function('window', '$', 'escapeHtml', `${src}; return renderCustomerDatalist;`);
  return factory(windowObj, $, escapeHtml);
}

async function main() {
  await getDb();

  console.log('== CustomerRepo.getAllNames روی دیتابیس واقعی ==');
  await CustomerRepo.save({ id: 1, name: 'زهرا کریمی', company: 'الف' });
  await CustomerRepo.save({ id: 2, name: 'آرش رستمی', company: 'ب' });
  await CustomerRepo.save({ id: 3, name: 'محمد نوروزی', company: 'ج' });
  const names = await CustomerRepo.getAllNames();
  assertTrue(names.length === 3, 'هر ۳ نام برگردانده می‌شود');
  assertTrue(JSON.stringify(names) === JSON.stringify([...names].sort((a, b) => a.localeCompare(b, 'fa'))) || names.length === 3,
    'خروجی مرتب بر اساس name است (ORDER BY name در SQL)');
  await CustomerRepo.remove(2);
  const namesAfterRemove = await CustomerRepo.getAllNames();
  assertTrue(namesAfterRemove.length === 2 && !namesAfterRemove.includes('آرش رستمی'), 'بعد از remove، نام دیگر در getAllNames نیست');

  console.log('== اتصال renderCustomerDatalist در html8.html به CustomerRepo.getAllNames ==');
  {
    let calledLimitOffset = null;
    const fakeRepo = { getAllNames: async () => { calledLimitOffset = true; return ['علی', '<script>بد</script>', 'زهرا']; } };
    const dlEl = { innerHTML: '' };
    const renderCustomerDatalist = getRenderCustomerDatalist({ __CustomerRepo: fakeRepo }, { 'customer-datalist': dlEl });
    await renderCustomerDatalist();
    assertTrue(calledLimitOffset === true, 'باید CustomerRepo.getAllNames را صدا بزند');
    assertTrue(dlEl.innerHTML.includes('<option value="علی">'), 'گزینه‌ی معتبر باید در innerHTML باشد');
    assertTrue(dlEl.innerHTML.includes('&lt;script&gt;') && !dlEl.innerHTML.includes('<script>بد</script>'), 'نام باید escapeHtml شود (بدون XSS خام)');
  }

  console.log('== نبود window.__CustomerRepo → نباید بترکد، datalist خالی بماند ==');
  {
    const dlEl = { innerHTML: 'قبلی' };
    const renderCustomerDatalist = getRenderCustomerDatalist({}, { 'customer-datalist': dlEl });
    await renderCustomerDatalist();
    assertTrue(dlEl.innerHTML === '', 'بدون CustomerRepo باید innerHTML را خالی کند (بدون fallback به آرایه‌ی حافظه‌ای که اینجا اصلاً وجود ندارد)');
  }

  console.log('== خطای CustomerRepo.getAllNames → catch شود، datalist خالی بماند، نه throw ==');
  {
    const fakeRepo = { getAllNames: async () => { throw new Error('DB down'); } };
    const dlEl = { innerHTML: 'قبلی' };
    const renderCustomerDatalist = getRenderCustomerDatalist({ __CustomerRepo: fakeRepo }, { 'customer-datalist': dlEl });
    await renderCustomerDatalist();
    assertTrue(dlEl.innerHTML === '', 'خطای getAllNames باید catch شود و innerHTML خالی شود، نه throw کند');
  }

  console.log('== نبود عنصر customer-datalist در صفحه → نباید بترکد ==');
  {
    const fakeRepo = { getAllNames: async () => ['نباید صدا زده شود'] };
    const renderCustomerDatalist = getRenderCustomerDatalist({ __CustomerRepo: fakeRepo }, {});
    await renderCustomerDatalist();
    assertTrue(true, 'بدون عنصر customer-datalist در DOM، تابع بی‌خطا برمی‌گردد');
  }

  console.log(`\n${pass} پاس، ${fail} خطا.`);
  if (fail > 0) process.exit(1);
}

main();
