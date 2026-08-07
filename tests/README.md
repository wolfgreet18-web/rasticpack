# تست‌های ادغام (Integration Tests) — فقط برای Node، نه بخشی از اپ

این پوشه بخشی از اپ Capacitor نیست؛ فقط برای صحت‌سنجی منطق SQL واقعیِ
`InvoiceRepo` / `CustomerRepo` / `migrateFromIndexedDB` روی یک موتور
**واقعی SQLite** (نه mock) قبل از رفتن سراغ تست روی دستگاه/شبیه‌ساز واقعی
اندروید ساخته شده — چون در این محیط به Android SDK/Capacitor دسترسی نیست.

## چطور اجرا کنیم

```bash
node --experimental-sqlite run-integration-tests.mjs
node --experimental-sqlite perf-test.mjs       # ۲۰۰k رکورد (نسخه‌ی ۴.۲/۴.۳)
node --experimental-sqlite perf-test-1m.mjs    # ۱M رکورد — معیار پذیرش رسمی فاز ۱ (نسخه‌ی ۴.۴)
node --experimental-sqlite single-write-cost-test.mjs  # هزینه‌ی نوشتن تکی ایندکس سوم (نسخه‌ی ۴.۶)
node --experimental-sqlite db-file-size-test.mjs       # حجم واقعی فایل .db روی دیسک (نسخه‌ی ۴.۷)
node --experimental-sqlite vacuum-churn-test.mjs       # اثر churn + هزینه/فایده‌ی VACUUM دوره‌ای (نسخه‌ی ۴.۸)
```

نیازمند Node.js نسخه ۲۲+ (به‌خاطر ماژول تجربی `node:sqlite`).

## این تست‌ها چه چیزی را تأیید می‌کنند

- `CustomerRepo`: `save` / `findByExactName` / `searchByName` (prefix) / `getPage`
- `InvoiceRepo.save` / `.remove` (متدهای اضافه‌شده در `InvoiceRepo.additions.js`) —
  round-trip واقعی روی ستون JSON `data`
- `InvoiceRepo.getPage` / `getStatusCounts` با فیلتر `monthStart`/`monthEnd`/`customerId`
  — همان معنایی که `invoiceStatusOf`/`isInvoiceDebtor` در `html8.html` دارند
  (بدون هیچ عدم‌تطابق در محاسبه‌ی وضعیت‌ها)
- `InvoiceRepo.getMonthCount` / `getAllDates` (ریزمرحله‌ی ۲.۵)
- `perf-test-1m.mjs`: همان سنجه‌های `perf-test.mjs` ولی روی ۱,۰۰۰,۰۰۰ فاکتور / ۱۵,۰۰۰ مشتری —
  این دقیقاً معیار پذیرش رسمی فاز ۱ است (نه ۲۰۰k که فقط یک سنجه‌ی میانی بود)
- `insertWithStockUpdate` / `deleteWithStockRestore` (پیش‌نمایش فاز ۵) — تراکنشی
  بودن واقعاً با یک UPDATE موجودی + یک INSERT/DELETE فاکتور تست شد
- `migrateFromIndexedDB.migrate()` با یک منبع داده‌ی fake تزریق‌شده (۱۲۰۰ مشتری /
  ۲۶۰۰ فاکتور)، شامل شمارش تطبیقی قبل از ست‌کردن پرچم، و skip شدن اجرای دوباره
- یک ریگرسیون مشخص: فاکتورهایی که به یک `customerId` حذف‌شده اشاره می‌کنند
  (orphan) دیگر مهاجرت را با خطای FOREIGN KEY نمی‌شکنند
- `single-write-cost-test.mjs`: هزینه‌ی خالص ایندکس پوششی `idx_inv_date_status_sent`
  روی یک `INSERT`/`UPDATE` تکی (نه bulk) — با مقایسه‌ی دو schema (با/بدون آن ایندکس)
  روی ۱M ردیف seed‌شده (نسخه‌ی ۴.۶)
- `db-file-size-test.mjs`: تنها تستی که از `:memory:` استفاده نمی‌کند — یک فایل
  SQLite واقعی در `os.tmpdir()` می‌سازد و حجم آن را با `fs.statSync` در سه نقطه‌ی
  رشد (۱۰۰k/۵۰۰k/۱M فاکتور) قبل و بعد از `VACUUM` اندازه می‌گیرد؛ در پایان فایل
  موقت را پاک می‌کند (نسخه‌ی ۴.۷)
- `vacuum-churn-test.mjs`: ادامه‌ی مستقیم `db-file-size-test.mjs` — آن تست فقط
  insert خالص را اندازه گرفت؛ این تست چرخه‌ی واقعی حذف+درج (churn، ۱۰ دور، هر
  دور ۵٪ از ۵۰۰k فاکتور) را روی دو دیتابیس فایلی جدا شبیه‌سازی می‌کند: یکی
  بدون VACUUM، یکی با VACUUM بعد از هر دور — هم میزان «باد کردن» فایل بدون
  VACUUM دوره‌ای، هم هزینه‌ی زمانی خودِ VACUUM را گزارش می‌دهد (نسخه‌ی ۴.۸)

## محدودیت مهم

`testConnection.node-only.mjs` جایگزین **آزمایشیِ** `connection.js` واقعی
است — همان قرارداد `query`/`run`/`withTransaction` را با `node:sqlite`
پیاده می‌کند، نه با پلاگین `@capacitor-community/sqlite`. یعنی این تست‌ها
صحت **منطق SQL** را تضمین می‌کنند، نه صحت خودِ اتصال Capacitor روی
اندروید/iOS واقعی — آن هنوز نیاز به تست روی دستگاه واقعی دارد (طبق نقشه‌راه).

فایل‌های داخل `_node_test_copies/` کپی‌هایی از `../rasticpack-capacitor/src/db/*.js`
هستند که فقط خط `import ... from './connection.js'` در آن‌ها به
`testConnection.node-only.mjs` تغییر کرده — بدون این تغییر، Node در محیط
بدون Capacitor نمی‌تواند این فایل‌ها را اجرا کند. **این کپی‌ها را در پروژه‌ی
اصلی استفاده نکنید** — فقط `rasticpack-capacitor/src/db/*.js` (که خودش
`connection.js` واقعی را import می‌کند) باید در اپ قرار بگیرد.
