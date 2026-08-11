// تست کارایی جستجوی نام مشتری روی ۱ میلیون مشتری — این دقیقاً همان معیار
// پذیرش رسمی «جستجوی نام روی ۱M مشتری، زیر ۳۰ms» است که در بخش ۵
// (معیارهای موفقیت) و بخش ۴/فاز ۳ سند نقشه‌راه ذکر شده. نسخه‌ی ۴.۹ این عدد
// را برای اولین بار روی ۱M مشتری واقعی سنجید (قبلش همیشه ۱۵k بود) و کشف کرد
// که پیشوندهای خیلی گسترده (نزدیک تطابق کل جدول) آستانه را رد می‌کنند
// (۴۸-۵۱ms)، چون COUNT(*) باید هر تطابق را واقعاً بشمارد. نسخه‌ی ۴.۱۰ این را
// با یک شمارش «سقف‌دار» (capped count — نگاه کن به CustomerRepo.searchByName)
// رفع کرد؛ این فایل اکنون هم رفع مشکل و هم رفتار totalIsExact را تأیید می‌کند.
//
// اجرا: node --experimental-sqlite customer-search-1m-test.mjs

import { CustomerRepo } from './_node_test_copies/CustomerRepo.js';
import { getDb, resetDb } from './testConnection.node-only.mjs';

const N_CUSTOMERS = 1000000;
const ACCEPTANCE_MS = 30; // معیار پذیرش رسمی: جستجوی نام روی ۱M مشتری زیر ۳۰ms

async function main() {
  resetDb();
  const db = await getDb();

  console.log(`Seeding ${N_CUSTOMERS} customers...`);
  let t = Date.now();
  await db.run('BEGIN');
  for (let i = 1; i <= N_CUSTOMERS; i++) {
    // همان الگوی نام‌گذاری perf-test-1m.mjs («مشتری تستی ${i}») برای
    // سازگاری مستقیم با بقیه‌ی تست‌های این پروژه — پیشوند مشترک یعنی جستجوی
    // یک پیشوند عددی طولانی‌تر، دقیقاً به همان تعداد ردیف که در دنیای واقعی
    // با یک نام رایج (مثل «محمدی») پیش می‌آید، match می‌کند.
    await db.run(`INSERT INTO customers (id, name, company, phone) VALUES (?, ?, ?, ?)`,
      [i, `مشتری تستی ${i}`, '', '0912' + String(1000000 + i)]);
  }
  await db.run('COMMIT');
  console.log(`  done in ${Date.now() - t}ms`);

  // سه سناریوی جستجو با selectivity متفاوت (سطح واقعی تفاوت جستجوی نام رایج
  // در برابر نام کمیاب را نشان می‌دهد):

  // ۱) پیشوند خیلی گسترده — همه‌ی ۱M ردیف را match می‌کند (بدترین حالت واقعی:
  //    وقتی صفحه‌ی اول کاربر با اسکرول لیست مشتریان تعامل می‌کند، نه جستجوی متن).
  //    با شمارش سقف‌دار (پیش‌فرض countCap=2000)، انتظار می‌رود total=2000 و
  //    totalIsExact=false برگردد — نه عدد دقیق ۱M، ولی هزینه‌اش دیگر به تعداد
  //    واقعی تطابق وابسته نیست.
  t = Date.now();
  const wide = await CustomerRepo.searchByName('مشتری', { prefix: true, limit: 50 });
  const wideMs = Date.now() - t;
  console.log(`Wide prefix search ("مشتری", matches ~all ${N_CUSTOMERS}): ${wideMs}ms — total=${wide.total}, totalIsExact=${wide.totalIsExact}, page=${wide.items.length}`);

  // ۲) پیشوند متوسط — نام‌هایی که با «مشتری تستی 15» شروع می‌شوند (۱۵, ۱۵۰-۱۵۹,
  //    ۱۵۰۰-۱۵۹۹, ۱۵۰۰۰-۱۵۹۹۹, ۱۵۰۰۰۰-۱۵۹۹۹۹ → ~۱۱٬۱۱۱ تطابق، معادل یک نام
  //    نسبتاً رایج در دیتای واقعی) — این هم از سقف ۲۰۰۰ عبور می‌کند، پس
  //    totalIsExact باید false باشد، ولی total باید دقیقاً ۲۰۰۰ باشد (نه یک
  //    عدد دلبخواهی)، چون زیرکوئری درست در همان نقطه متوقف می‌شود.
  t = Date.now();
  const medium = await CustomerRepo.searchByName('مشتری تستی 15', { prefix: true, limit: 50 });
  const mediumMs = Date.now() - t;
  console.log(`Medium prefix search ("...15", ~11k matches): ${mediumMs}ms — total=${medium.total}, totalIsExact=${medium.totalIsExact}, page=${medium.items.length}`);

  // ۳) تک‌مچ — دقیقاً یک مشتری (معادل جستجوی یک نام کمیاب/کامل) — زیر سقف
  //    است، پس باید totalIsExact=true و total=1 برگردد.
  t = Date.now();
  const single = await CustomerRepo.searchByName('مشتری تستی 542871', { prefix: true, limit: 50 });
  const singleMs = Date.now() - t;
  console.log(`Single-match prefix search: ${singleMs}ms — total=${single.total}, totalIsExact=${single.totalIsExact}, page=${single.items.length}`);

  // ۴) findByExactName — مسیر جداگانه‌ای که در ۲۰ تابع تک‌فاکتوری استفاده می‌شود
  t = Date.now();
  const exact = await CustomerRepo.findByExactName('مشتری تستی 542871');
  const exactMs = Date.now() - t;
  console.log(`findByExactName: ${exactMs}ms — found=${!!exact}`);

  console.log('');
  console.log('Correctness check (capped-count semantics):');
  const correctnessChecks = [
    ['wide.totalIsExact === false', wide.totalIsExact === false],
    ['wide.total === 2000 (the cap)', wide.total === 2000],
    ['medium.totalIsExact === false', medium.totalIsExact === false],
    ['medium.total === 2000 (the cap)', medium.total === 2000],
    ['single.totalIsExact === true', single.totalIsExact === true],
    ['single.total === 1 (exact, under cap)', single.total === 1],
  ];
  let correctnessFailed = false;
  for (const [label, pass] of correctnessChecks) {
    console.log(`  ${pass ? '✓' : '✗ FAIL'} ${label}`);
    if (!pass) correctnessFailed = true;
  }

  console.log('');
  console.log(`Acceptance check (< ${ACCEPTANCE_MS}ms per official target — name search on 1M customers):`);
  console.log(`  wide prefix (~1M matches, capped):    ${wideMs}ms -> ${wideMs < ACCEPTANCE_MS ? 'PASS' : 'FAIL'}`);
  console.log(`  medium prefix (~11k matches, capped): ${mediumMs}ms -> ${mediumMs < ACCEPTANCE_MS ? 'PASS' : 'FAIL'}`);
  console.log(`  single-match prefix (exact):          ${singleMs}ms -> ${singleMs < ACCEPTANCE_MS ? 'PASS' : 'FAIL'}`);
  console.log(`  findByExactName:                       ${exactMs}ms -> ${exactMs < ACCEPTANCE_MS ? 'PASS' : 'FAIL'}`);
  console.log('');
  console.log('NOTE: this measures node:sqlite (in-process, no IPC) on the dev container\'s CPU,');
  console.log('not the @capacitor-community/sqlite plugin on a real Android/iOS device — same');
  console.log('caveat as every other perf test in this suite (see roadmap risk table).');
  console.log('NOTE 2 (v4.10): total is now a capped count (default cap=2000), not a raw COUNT(*) —');
  console.log('when totalIsExact is false, total means "at least this many", not the true total.');
  console.log('Callers (future UI code) must check totalIsExact before showing total as a precise number.');

  if (wideMs >= ACCEPTANCE_MS || mediumMs >= ACCEPTANCE_MS || correctnessFailed) process.exit(1);
}

main().catch(e => { console.error('CUSTOMER SEARCH 1M TEST FAILED:', e); process.exit(1); });
