// تست اتصال UI جستجوی مشتری (renderCustomersList) به CustomerRepo.searchByName/getPage
// — فاز ۳ نقشه‌راه (جستجوی متنی مقیاس‌پذیر)، نسخه‌ی ۴.۱۷.
// مثل tests/lazy-load-cache-connect-test.mjs، تابع واقعی (fetchCustomersPage) مستقیم
// با regex از html8.html استخراج می‌شود، نه یک بازنویسی جدا.
// اجرا: node tests/customer-search-wire-test.mjs

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const html = fs.readFileSync(path.join(__dirname, '..', 'html8.html'), 'utf-8');

let pass = 0, fail = 0;
function assert(cond, msg) { if (cond) pass++; else { fail++; console.error('❌ FAIL:', msg); } }

function extractFn(name, src) {
  const re = new RegExp(`async function ${name}\\([^)]*\\)\\{[\\s\\S]*?\\n\\}\\n`);
  const m = src.match(re);
  if (!m) { console.error(`❌ FAIL: ${name} در html8.html پیدا نشد.`); process.exit(1); }
  return m[0];
}

function getFetchCustomersPage(windowObj) {
  const src = extractFn('fetchCustomersPage', html);
  const factory = new Function('window', `${src}; return fetchCustomersPage;`);
  return factory(windowObj);
}

async function run() {
  // ── جستجوی غیرخالی با repo موجود → باید searchByName صدا زده شود ──
  {
    let calledWith = null;
    const fakeRepo = {
      searchByName: async (q, opts) => { calledWith = { q, opts }; return { items: [{ id: 1, name: 'علی' }], total: 1, totalIsExact: true }; },
      getPage: async () => { throw new Error('نباید صدا زده شود وقتی q غیرخالی است'); }
    };
    const fetchCustomersPage = getFetchCustomersPage({ __CustomerRepo: fakeRepo });
    const res = await fetchCustomersPage(0, 50, 'علی', []);
    assert(calledWith && calledWith.q === 'علی', 'باید CustomerRepo.searchByName را با کوئری صحیح صدا بزند');
    assert(calledWith.opts.limit === 50 && calledWith.opts.offset === 0, 'باید limit/offset صحیح را به searchByName بدهد');
    assert(res.items.length === 1 && res.items[0].id === 1, 'باید آیتم‌های برگشتی از searchByName را برگرداند');
    assert(res.total === 1 && res.totalIsExact === true, 'باید total/totalIsExact را از پاسخ repo عبور بدهد');
  }

  // ── جستجوی خالی با repo موجود → باید getPage صدا زده شود، نه searchByName ──
  {
    let getPageCalled = false;
    const fakeRepo = {
      searchByName: async () => { throw new Error('نباید صدا زده شود وقتی q خالی است'); },
      getPage: async ({ limit, offset }) => { getPageCalled = true; return { items: [{ id: 2, name: 'رضا' }], total: 40 }; }
    };
    const fetchCustomersPage = getFetchCustomersPage({ __CustomerRepo: fakeRepo });
    const res = await fetchCustomersPage(0, 50, '', []);
    assert(getPageCalled, 'وقتی q خالی است باید getPage صدا زده شود');
    assert(res.total === 40, 'باید total را از getPage عبور بدهد');
    assert(res.totalIsExact === true, 'وقتی repo مقدار totalIsExact را برنمی‌گرداند، باید true فرض شود (getPage همیشه دقیق است)');
  }

  // ── totalIsExact:false از searchByName باید تا خروجی نهایی حفظ شود ──
  {
    const fakeRepo = {
      searchByName: async () => ({ items: [], total: 2000, totalIsExact: false }),
      getPage: async () => { throw new Error('نباید صدا زده شود'); }
    };
    const fetchCustomersPage = getFetchCustomersPage({ __CustomerRepo: fakeRepo });
    const res = await fetchCustomersPage(0, 50, 'ا', []);
    assert(res.totalIsExact === false, 'وقتی searchByName شمارش سقف‌دار برمی‌گرداند (totalIsExact:false)، باید حفظ شود نه اینکه به true تبدیل شود');
  }

  // ── نبود window.__CustomerRepo → نباید بترکد، باید به fullList برگردد ──
  {
    const fetchCustomersPage = getFetchCustomersPage({});
    const fullList = [{ id: 1, name: 'الف' }, { id: 2, name: 'ب' }, { id: 3, name: 'ج' }];
    const res = await fetchCustomersPage(0, 2, '', fullList);
    assert(res.items.length === 2 && res.items[0].id === 1, 'بدون CustomerRepo باید صفحه‌ی اول fullList برگردد');
    assert(res.total === 3 && res.totalIsExact === true, 'بدون CustomerRepo، total باید طول fullList و totalIsExact همیشه true باشد');
  }

  // ── خطای searchByName → باید بی‌صدا catch و به fullList برگردد ──
  {
    const fakeRepo = { searchByName: async () => { throw new Error('DB down'); } };
    const fetchCustomersPage = getFetchCustomersPage({ __CustomerRepo: fakeRepo });
    const fullList = [{ id: 9, name: 'محمد' }];
    const res = await fetchCustomersPage(0, 50, 'محمد', fullList);
    assert(res.items.length === 1 && res.items[0].id === 9, 'خطای searchByName باید بی‌صدا catch شود و fullList برگردد');
  }

  // ── نبود متد searchByName روی repo (نسخه‌ی ناقص/قدیمی) → نباید بترکد ──
  {
    const fakeRepo = { getPage: async () => ({ items: [{ id: 5, name: 'س' }], total: 1 }) };
    const fetchCustomersPage = getFetchCustomersPage({ __CustomerRepo: fakeRepo });
    const fullList = [{ id: 5, name: 'س' }];
    const res = await fetchCustomersPage(0, 50, 'س', fullList);
    assert(res.items.length === 1 && res.items[0].id === 5, 'نبود searchByName روی repo باید بی‌صدا catch شود و به fullList برگردد');
  }

  console.log(`\n${pass} پاس، ${fail} خطا.`);
  if (fail > 0) process.exit(1);
}

run();
