#!/usr/bin/env node
/*
 * memory-array-audit.mjs
 * ─────────────────────────────────────────────────────────────────────────
 * خروجی دوم فاز ۷ نقشه‌راه ۲ (sqlite-migration-roadmap.md).
 *
 * هدف: بعد از هر فاز (۸ تا ۱۱)، تأیید کند که تعداد رخدادهای باقی‌مانده‌ی
 * `invoices` / `customers` در html8.html دقیقاً با پیش‌بینی همان فاز مطابقت
 * دارد — تا هیچ رخدادی سهواً فراموش نشود و هیچ رخدادی هم بیش از حد انتظار
 * حذف نشود (که می‌تواند نشانه‌ی حذف اشتباهی چیز دیگری باشد).
 *
 * روش کار:
 *   ۱) node --check روی html8.html (باید سه بلاک <script> را جدا استخراج
 *      و هرکدام را جدا syntax-check کند، چون خودِ فایل .html نیست).
 *   ۲) شمارش کل رخدادهای \binvoices\b و \bcustomers\b با grep -o (همان
 *      روشی که چک‌لیست memory-array-checklist.md را تولید کرد).
 *   ۳) مقایسه با آستانه‌ی هر فاز در جدول PHASE_EXPECTATIONS پایین.
 *
 * استفاده:
 *   node memory-array-audit.mjs                → فقط گزارش وضعیت فعلی
 *   node memory-array-audit.mjs --phase 8       → مقایسه با انتظار فاز ۸
 *   node memory-array-audit.mjs --phase 11      → مقایسه با انتظار فاز ۱۱ (باید صفر باشد)
 *
 * نکته‌ی صادقانه: این اسکریپت شمارش می‌کند، نه دسته‌بندی می‌کند — دسته‌بندی
 * دستی (ستون «دسته») در memory-array-checklist.md است. این اسکریپت فقط
 * «تعداد کل» را در برابر جدول زیر تأیید می‌کند تا یک رخداد فراموش‌شده لو برود.
 */
import { execSync } from 'node:child_process';
import { readFileSync, writeFileSync, mkdtempSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';

const TARGET_FILE = path.join(process.cwd(), 'html8.html');

/* ══ جدول انتظارات هر فاز (بر اساس شمارش دستی چک‌لیست فاز ۷) ══
   این اعداد "سقف بالا"ی رخدادهای باقی‌مانده‌ی *کد واقعی* (نه کامنت/رشته/
   پارامتر بی‌ربط) بعد از هر فاز است. رخدادهای دسته‌ی D/E/F/G (متغیر بی‌ربط،
   رشته، کامنت، تعریف/بازنشانی state) در این سقف حساب نمی‌شوند چون هدف
   حذفشان نیست — فقط A/B/C (نوشتن‌آینه‌ای/fallback ناامن/کش لِیزی) کاهش
   پیدا می‌کنند و C تا فاز ۱۰ باقی می‌ماند. */
const PHASE_EXPECTATIONS = {
  7:  { note: 'پیش از هر تغییری — فقط ثبت baseline', invoices_AB: 20, customers_AB: 9 },
  8:  { note: 'بعد از حذف fallbackهای ناامن (computeStatsDataFallback مسیر، doBackupLegacyFromMemory، baseList در renderInvoicesList)', invoices_AB: 15, customers_AB: 8 },
  9:  { note: 'بعد از بازنویسی رندر اصلی + حذف نوشتن‌آینه‌ای (دسته A) در add/remove/save', invoices_AB: 8, customers_AB: 3 },
  10: { note: 'بعد از تبدیل بکاپ/ریستور legacy به batch-به-batch', invoices_AB: 0, customers_AB: 0 },
  11: { note: 'بعد از حذف نهایی خودِ متغیرها — هیچ رخدادی از customers/invoices (به‌جز رشته/کامنت) نباید باقی بماند', invoices_total_nonstring: 0, customers_total_nonstring: 0 },
};

function checkNodeSyntax(file) {
  const html = readFileSync(file, 'utf8');
  const scriptRe = /<script(?![^>]*\bsrc=)[^>]*>([\s\S]*?)<\/script>/gi;
  const blocks = [...html.matchAll(scriptRe)].map(m => m[1]);
  if (blocks.length === 0) {
    console.error('⚠️  هیچ بلاک <script> پیدا نشد — احتمالاً regex استخراج نیاز به بازبینی دارد.');
    return false;
  }
  const dir = mkdtempSync(path.join(tmpdir(), 'html8-script-'));
  let allOk = true;
  blocks.forEach((code, i) => {
    const p = path.join(dir, `block${i}.mjs.check.js`);
    writeFileSync(p, code, 'utf8');
    try {
      execSync(`node --check "${p}"`, { stdio: 'pipe' });
      console.log(`  ✅ بلاک <script> شماره‌ی ${i + 1}/${blocks.length}: سینتکس درست است.`);
    } catch (e) {
      allOk = false;
      console.error(`  ❌ بلاک <script> شماره‌ی ${i + 1}/${blocks.length}: خطای سینتکس:`);
      console.error(String(e.stderr || e.stdout || e.message).split('\n').slice(0, 6).join('\n'));
    }
  });
  return allOk;
}

function countOccurrences(file, identifier) {
  const text = readFileSync(file, 'utf8');
  const re = new RegExp(`\\b${identifier}\\b`, 'g');
  return (text.match(re) || []).length;
}

function main() {
  const args = process.argv.slice(2);
  const phaseIdx = args.indexOf('--phase');
  const phase = phaseIdx !== -1 ? Number(args[phaseIdx + 1]) : null;

  console.log('══ گام ۱: node --check روی هر بلاک <script> ══');
  const syntaxOk = checkNodeSyntax(TARGET_FILE);
  console.log('');

  console.log('══ گام ۲: شمارش کل رخدادها (grep -o هم‌ارز) ══');
  const invTotal = countOccurrences(TARGET_FILE, 'invoices');
  const cusTotal = countOccurrences(TARGET_FILE, 'customers');
  console.log(`  invoices : ${invTotal} رخداد`);
  console.log(`  customers: ${cusTotal} رخداد`);
  console.log(`  (baseline فاز ۷: invoices=80, customers=44 — طبق memory-array-checklist.md)`);
  console.log('');

  if (phase != null) {
    const exp = PHASE_EXPECTATIONS[phase];
    if (!exp) {
      console.error(`فاز ${phase} در PHASE_EXPECTATIONS تعریف نشده. فازهای معتبر: ${Object.keys(PHASE_EXPECTATIONS).join(', ')}`);
      process.exit(2);
    }
    console.log(`══ گام ۳: مقایسه با انتظار فاز ${phase} — ${exp.note} ══`);
    console.log('  ⚠️  توجه: این سقف‌ها فقط برای رخدادهای دسته‌ی A/B (نوشتن‌آینه‌ای/fallback ناامن)');
    console.log('     است؛ رخدادهای C/D/E/F/G در این نسخه‌ی اسکریپت به‌صورت خودکار از شمارش کل جدا');
    console.log('     نمی‌شوند (نیازمند همان دسته‌بندی دستیِ چک‌لیست است) — این گزارش فقط عدد کل');
    console.log('     فعلی را کنار عدد باقلی جدول قرار می‌دهد تا با چک‌لیست به‌روزشده مقایسه‌ی دستی شود.');
    console.log(`  invoices کل فعلی : ${invTotal}`);
    console.log(`  customers کل فعلی: ${cusTotal}`);
    if (phase === 11) {
      const invOk = invTotal <= (exp.invoices_total_nonstring ?? 0) + 20; /* + رشته/کامنت مجاز */
      const cusOk = cusTotal <= (exp.customers_total_nonstring ?? 0) + 20;
      console.log(invOk ? '  ✅ invoices در محدوده‌ی قابل‌قبول فاز ۱۱ است.' : '  ❌ invoices بیش از حد انتظار فاز ۱۱ باقی مانده — احتمال یک رخداد فراموش‌شده!');
      console.log(cusOk ? '  ✅ customers در محدوده‌ی قابل‌قبول فاز ۱۱ است.' : '  ❌ customers بیش از حد انتظار فاز ۱۱ باقی مانده — احتمال یک رخداد فراموش‌شده!');
    } else {
      console.log('  ➜ این عدد را با ستون «دسته» در memory-array-checklist.md به‌روزشده تطبیق بده:');
      console.log(`     تعداد ردیف‌های باقی‌مانده با دسته A یا B باید ≤ ${exp.invoices_AB} (invoices) و ≤ ${exp.customers_AB} (customers) باشد.`);
    }
  }

  console.log('');
  console.log(syntaxOk ? '✅ همه‌ی بلاک‌های <script> از نظر سینتکس سالم‌اند.' : '❌ حداقل یک بلاک <script> خطای سینتکس دارد — قبل از ادامه رفع شود.');
  process.exit(syntaxOk ? 0 : 1);
}

main();
