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
node --experimental-sqlite customer-search-1m-test.mjs # جستجوی نام روی ۱M مشتری واقعی — نه ۱۵k (نسخه‌ی ۴.۹)
node --experimental-sqlite customer-fts-search-test.mjs # جستجوی FTS5 آزاد (name+company، چندتوکنی، sync خودکار) — نسخه‌ی ۴.۱۸
node --experimental-sqlite customer-search-wire-test.mjs # اتصال UI (renderCustomersList) به CustomerRepo.searchByName/getPage — نسخه‌ی ۴.۱۷
node --experimental-sqlite stats-aggregation-test.mjs  # InvoiceRepo.getTotalsByRange/getStatsBuckets + اتصال fetchStatsData (تب آمار) — نسخه‌ی ۴.۱۹
node --experimental-sqlite stats-leaders-test.mjs      # InvoiceRepo.getStatsLeaders + اتصال fetchStatsLeaders (رکورددارهای تب آمار) — نسخه‌ی ۴.۲۰
node stats-worker-test.mjs                             # Web Worker مسیر fallback آمار (statsData/statsLeaders) — نسخه‌ی ۴.۲۱، بدون نیاز به SQLite
node --experimental-sqlite write-integrity-test.mjs    # dbSaveInvoiceWithStock/dbDeleteInvoiceRecordWithStock + rollback تراکنشی — نسخه‌ی ۴.۲۲
node backup-ndjson-test.mjs                            # فرمت بکاپ ndjson-v1: batching/ترتیب FK/تشخیص فرمت/تاب‌آوری — نسخه‌ی ۴.۲۳، بدون نیاز به SQLite
node --expose-gc backup-ram-usage-test.mjs             # RAM واقعی Restore: قدیمی (JSON.parse کامل) در برابر جدید (batch به batch) — نسخه‌ی ۴.۲۴
node --experimental-sqlite invoice-getpage-cap-test.mjs # شمارش سقف‌دار InvoiceRepo.getPage (countCap/totalIsExact) — نسخه‌ی ۴.۲۵
node --experimental-sqlite invoice-getpage-cursor-test.mjs # صحت keyset pagination جدید (InvoiceRepo.getPageByCursor) در برابر getPage — نسخه‌ی ۴.۲۶
node --experimental-sqlite keyset-pagination-perf-test.mjs # کارایی keyset در برابر OFFSET عمیق روی ۱M ردیف — نسخه‌ی ۴.۲۶
node lazy-load-cache-connect-test.mjs                  # اتصال Lazy Loading اسکرول/مودال به InvoiceCache — نسخه‌ی ۴.۱۵ (بازنویسی استخراج در ۴.۲۷ چون fetchInvoicesPage دیگر خودمختار نیست)، بدون نیاز به SQLite
node invoice-cursor-routing-test.mjs                   # مسیریابی خودکار cursor/offset در fetchInvoicesPage (فاز ۷، باقی‌مانده‌ی رسمی ۱ — اتصال UI) — نسخه‌ی ۴.۲۷، بدون نیاز به SQLite
node --experimental-sqlite phase1-migration-resilience-test.mjs # فاز ۱ نقشه‌راه SQLite: idempotency، خطای وسط‌کار، رکورد corrupt/فیلد گمشده، تأیید status ok/skipped/error (نسخه‌ی ۴.۲۹)
node --experimental-sqlite invoice-counts-by-customer-test.mjs # InvoiceRepo.getCountsByCustomerIds — شمارش فاکتور per-customer با یک کوئری aggregate (فاز ۲ گروه ب مورد ۳، renderCustomerViewCard)

# فاز ۷ — تست فشار در مقیاس واقعی (۲۵۰k مشتری + ۵M فاکتور)، به‌خاطر سقف زمانی
# هر دستور در این sandbox روی یک فایل دیسک، در چند اجرای جدا seed می‌شود؛
# نگاه کن به روش دقیق در roadmap-infinite-records-v4_24.md (یادداشت ۴.۲۵):
#   node --experimental-sqlite seed-chunk.node-only.mjs customers 1 250000 /path/to/db
#   node --experimental-sqlite seed-chunk.node-only.mjs invoices <from> <to> 250000 /path/to/db   # چند بار، برای رسیدن به ۵M
#   STRESS_DB_PATH=/path/to/db node --experimental-sqlite stress-test-phase7-query.node-only.mjs
```

نیازمند Node.js نسخه ۲۲+ (به‌خاطر ماژول تجربی `node:sqlite`).

## این تست‌ها چه چیزی را تأیید می‌کنند

- `CustomerRepo`: `save` / `findByExactName` / `searchByName` (prefix، شامل
  رفتار شمارش سقف‌دار `countCap`/`totalIsExact` از نسخه‌ی ۴.۱۰) / `getPage`
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
- `stats-worker-test.mjs` (نسخه‌ی ۴.۲۱): منطق واقعی داخل `STATS_WORKER_SRC`
  (رشته‌ی متنی Web Worker که با Blob ساخته می‌شود) — با شبیه‌سازی یک محیط
  `self.onmessage`/`postMessage` (چون Node خودِ Worker مرورگری را ندارد)،
  مستقیم از html8.html اجرا و با نتایج computeStatsDataFallback/
  computeStatsLeadersFallback مقایسه می‌شود (تست هم‌ارزی). همچنین
  `fetchStatsData`/`fetchStatsLeaders` با یک `Worker`/`Blob`/`URL` جعلیِ
  تزریق‌شده روی `global` تست می‌شوند تا تأیید شود مسیر Worker واقعاً استفاده
  می‌شود (نه همیشه fallback هم‌زمان)، و بدون آن‌ها بی‌صدا به همان fallback
  قبلی برمی‌گردند.
- `run-integration-tests.mjs` (نسخه‌ی ۴.۱۱): `InvoiceRepo.clearAll()` /
  `CustomerRepo.clearAll()` — پاک‌سازی کامل جدول (نه DROP)، به‌همراه تأیید
  ترتیب صحیح FK (اول invoices، بعد customers) و یک شبیه‌سازی کامل مسیر
  `doRestore` (clearAll → clearAll → bulkInsert → bulkInsert) که نشان می‌دهد
  داده‌ی قدیمی بعد از restore باقی نمی‌ماند
- `customer-search-1m-test.mjs`: تمام تست‌های قبلی (`perf-test.mjs`,
  `perf-test-1m.mjs`) جدول `customers` را همیشه روی ۱۵,۰۰۰ ردیف ثابت نگه
  داشته بودند — حتی وقتی `invoices` به ۱M رسید — چون فقط برای فراهم کردن یک
  `customerId` معتبر لازم بود. این تست جدول مشتریان را هم به مقیاس هدف رسمی
  (۱M ردیف) می‌رساند و `CustomerRepo.searchByName`/`findByExactName` را روی
  آن، با سه سطح selectivity متفاوت (پیشوند خیلی گسترده ~۱M تطابق، پیشوند
  متوسط ~۱۱k تطابق، تک‌تطابق)، در برابر معیار پذیرش رسمی «جستجوی نام روی ۱M
  مشتری زیر ۳۰ms» می‌سنجد (نسخه‌ی ۴.۹) — کشف کرد که پیشوند گسترده این آستانه
  را رد می‌کند (~۴۸-۵۱ms)؛ در نسخه‌ی ۴.۱۰ با یک شمارش سقف‌دار (`countCap`) در
  `CustomerRepo.searchByName` رفع شد (اکنون هر سه سناریو ~۱ms)، و این فایل هم
  رفع کارایی و هم صحت معنایی فیلد جدید `totalIsExact` را تأیید می‌کند
- `invoice-getpage-cap-test.mjs` (نسخه‌ی ۴.۲۵): `InvoiceRepo.getPage` همان
  شمارش سقف‌دار (`countCap`/`totalIsExact`) را که `CustomerRepo.searchByName`
  در نسخه‌ی ۴.۱۰ گرفته بود، اکنون دارد — کشف‌شده حین تست فشار واقعی فاز ۷
  (۵M فاکتور) که `COUNT(*)` خام قدیمی را از آستانه‌ی ۱۰۰ms رد می‌کرد
- `invoice-getpage-cursor-test.mjs` (نسخه‌ی ۴.۲۶): صحت `InvoiceRepo.getPageByCursor`
  (keyset pagination جدید) — راه رفتن کامل صفحه‌به‌صفحه با cursor باید دقیقاً
  همان دنباله‌ی `getPage` (offset-based) را، بدون تکرار/جاافتادگی، با همان
  فیلترها (customerId/status) و همان total، بازتولید کند؛ شامل تست صریح
  tie-break (ردیف‌های هم‌تاریخ) و تشخیص درست پایان دنباله (صفحه‌ی خالی)
- `keyset-pagination-perf-test.mjs` (نسخه‌ی ۴.۲۶): کارایی — رفع مستقیمِ FAIL
  ثبت‌شده در یادداشت ۴.۲۵ (سناریو ۲ فاز ۷، اسکرول تا انتهای لیست روی ۵M ردیف).
  روی ۱M ردیف (به‌خاطر سقف زمانی هر دستور، نه تکرار کامل seed ۵M دیسک‌محور)،
  صفحه‌ی بعدیِ یک اسکرول عمیق را با `getPage(offset≈۱M)` (~۴۶ms) در برابر
  `getPageByCursor` از یک cursor هم‌عمق (~۰.۷ms) مقایسه می‌کند — کشف کرد که
  فرم اولیه‌ی شرط keyset (با `OR`) planner را مجبور به `SCAN` می‌کرد (کندتر
  از OFFSET!)؛ با سینتکس row-value SQLite (`(date,id) < (?,?)`) به `SEARCH`
  (seek واقعی روی `idx_inv_date_id`) تبدیل شد — تفاوت ~۶۰ برابری در این اجرا
- `invoice-cursor-routing-test.mjs` (نسخه‌ی ۴.۲۷): مسیریابی خودکار
  cursor/offset در `fetchInvoicesPage` (`html8.html`) — باقی‌مانده‌ی رسمی (۱)
  فاز ۷ (اتصال UI/Virtual List به `getPageByCursor`). بدون نیاز به SQLite
  (Repo فیک تزریق می‌شود): اولین صفحه (offset=0) باید از `getPage` استفاده
  کند؛ ادامه‌ی دقیقاً پیوسته (offset بلافاصله بعد از چیزی که قبلاً دیده شده)
  باید خودکار به `getPageByCursor` سوییچ کند؛ جهش به یک offset ناشناخته باید
  به `getPage` برگردد؛ فیلترهای متفاوت (مثلاً `customerId` دیگر) نباید انکر
  یکدیگر را به‌اشتباه به‌کار ببرند؛ شکست `getPageByCursor` باید بی‌صدا به
  `getPage` سقوط کند؛ `invalidateInvoiceCursorCache` باید کل کش انکرها را
  پاک کند (بعدش ادامه‌ی قبلی دوباره جهش محسوب می‌شود)؛ نبود `InvoiceRepo` باید
  به `fullList` برگردد
- `lazy-load-cache-connect-test.mjs`: در نسخه‌ی ۴.۲۷ استخراج `fetchInvoicesPage`
  به‌روزرسانی شد — این تابع دیگر خودمختار نیست (به کش انکرهای cursor بالا
  وابسته است)، پس تست به‌جای استخراج تک‌تابعی، کل بلوک هم‌سطح را با هم
  استخراج می‌کند؛ رفتار/assertion های خودِ تست بدون تغییر ماندند
- `seed-chunk.node-only.mjs` / `stress-test-phase7-query.node-only.mjs`
  (نسخه‌ی ۴.۲۵، فاز ۷ اقدام ۱-۳): برخلاف بقیه‌ی تست‌ها که `:memory:` استفاده
  می‌کنند، این دو یک فایل دیسک واقعی seed/کوئری می‌کنند — چون seed ۵M فاکتور
  در یک اجرا از سقف زمانی هر دستور در این sandbox رد می‌شود، seed باید در چند
  اجرای جدا (chunk) روی همان فایل انجام شود؛ `fileDbConnection.node-only.mjs`
  کانکشن فایلی جداگانه‌ای است که `seed-chunk` از آن استفاده می‌کند،
  و `testConnection.node-only.mjs` (که `InvoiceRepo`/`CustomerRepo` واقعاً
  از آن import می‌کنند) با env var اختیاری `STRESS_DB_PATH` به همان فایل وصل
  می‌شود تا `stress-test-phase7-query` بتواند از منطق واقعی Repo (نه یک کپی
  موازی) استفاده کند. نتایج واقعی و تحلیل در نقشه‌راه، یادداشت نسخه‌ی ۴.۲۵،
  ثبت شده.

- `phase1-migration-resilience-test.mjs` (نسخه‌ی ۴.۲۹، فاز ۱): سه یافته/باگ
  واقعی کشف و رفع شد: (۱) رکورد مبدأ بدون فیلد الزامی (مثلاً فاکتور بدون
  `customerId`) باعث throw کل `migrate()` می‌شد — حالا فیلتر و در
  `skippedInvalidCustomers`/`skippedInvalidInvoices` گزارش می‌شود؛ (۲) id
  تکراری در منبع باعث `status:'error'` کاذب در شمارش تطبیقی می‌شد (چون با
  طول خام آرایه مقایسه می‌کرد، نه id های یکتا) — رفع شد؛ (۳) بلاک مصرف‌کننده
  در `html8.html` فیلد `result.skipped` (که اصلاً وجود نداشت) را چک می‌کرد
  به‌جای `result.status` — یعنی حتی در خطا هم پیام «موفق» چاپ می‌شد؛ رفع شد
  و یک `window.__sqliteMigrationStatus` قابل‌مشاهده هم اضافه شد.

- `invoice-counts-by-customer-test.mjs` (فاز ۲ نقشه‌راه SQLite، گروه ب مورد ۳):
  `InvoiceRepo.getCountsByCustomerIds` — شمارش صحیح چند مشتری با یک کوئری
  `GROUP BY` واحد (نه N کوئری per-customer)، غیبت مشتری بدون فاکتور از نگاشت
  خروجی (نه `0` صریح)، محدود ماندن نتیجه به فقط idهای درخواستی حتی وقتی
  مشتری‌های دیگری هم در جدول هستند، و رفتار null-safe با ورودی خالی/null/undefined.

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
