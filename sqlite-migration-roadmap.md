# نقشه‌راه: تبدیل SQLite به تنها منبع حقیقت (Single Source of Truth)

**وضعیت فعلی (بر اساس بررسی html34.zip در تاریخ تحلیل):**
- `migrateFromIndexedDB.js` **وصل است** — در انتهای `html8.html` یک بلاک `<script type="module">` مستقل (`bootstrapSQLitePhase2Step1`, خط ~۶۲۸۶) بعد از `loadState()` اجرا می‌شود و `migrate()` را صدا می‌زند. این خبر خوب است اما **تست‌نشده** باقی مانده.
- ۳۸ فراخوانی `idb*` (idbPut/idbGet/idbBulkPut/idbDelete/idbClear/idbGetAllLimited) هنوز فعال‌اند — به‌صورت عمدی، طبق سیاست «هر دو سیستم زنده تا فاز ۲.۶» که در کامنت‌های خود کد ذکر شده.
- ~۱۵ نقطه خواندن مستقیم از آرایه‌ی `invoices` و ~۱۰ نقطه از `customers` باقی مانده‌اند. برخی از این‌ها (مثل `buildInvMonthList`, `getMonthCount`) از قبل الگوی fallback دارند: اول `InvoiceRepo` را امتحان می‌کنند، در خطا به آرایه برمی‌گردند. برخی دیگر (مثل `renderCustomerViewCard`, فیلتر اصلی لیست فاکتورها در خط ۴۶۸۴) هنوز کاملاً sync و مستقیم روی آرایه کار می‌کنند.

هدف نهایی: حذف کامل آرایه‌ی حافظه + IndexedDB، و SQLite به‌عنوان تنها منبع داده.

---

## فاز ۰ — تثبیت وضعیت فعلی و ابزار اندازه‌گیری (پیش‌نیاز، ~نیم روز) ✅ انجام شد

هدف: قبل از هر تغییری، یک خط پایه (baseline) قابل اندازه‌گیری داشته باشیم که بشود در هر فاز بعدی با آن مقایسه کرد.

- [x] لیست کامل و شماره‌خط‌دار همه‌ی نقاط `invoices.*` و `customers.*` مستقیم را در یک فایل چک‌لیست (`migration-checklist.md`) ثبت کن — هرکدام با وضعیت: `دارد fallback به Repo` / `فقط آرایه (باید تبدیل شود)`.
- [x] همه‌ی ۳۸ فراخوانی `idb*` را هم در همان چک‌لیست فهرست کن، با ستون «آیا نوشتن موازی است یا خواندن مستقل». *(یافته: شمارش دقیق ۲۳ فراخوانی واقعی بود، نه ۳۸ — جزئیات در `migration-checklist.md`)*
- [x] تست‌های موجود در `tests/` را یک‌بار روی وضعیت فعلی اجرا کن و نتیجه را ثبت کن (باید سبز باشند؛ این معیار «قبل از شروع» است). *(همه‌ی ۲۴ تست، شامل هر دو معیار پذیرش رسمی روی ۱M رکورد، سبز بودند)*
- [x] یک اسکریپت کوچک بساز که بعد از migration بررسی کند تعداد رکوردهای IndexedDB == تعداد رکوردهای SQLite (برای invoices و customers جداگانه). این ابزار در فازهای بعد بارها استفاده می‌شود. *(`verify-migration-counts.mjs` — باید داخل خودِ اپ/WebView اجرا شود، نه Node خالص، چون به IndexedDB و اتصال واقعی SQLite Capacitor نیاز دارد)*

**خروجی فاز:** `migration-checklist.md` + گزارش baseline تست‌ها + `verify-migration-counts.mjs`. هیچ کد تغییر نکرد.

---

## فاز ۱ — تأیید و سخت‌کردن مهاجرت خودکار (بحرانی‌ترین فاز، ~۱ روز) ✅ انجام شد

چون migration از قبل در startup صدا زده می‌شود، تمرکز این فاز روی **اعتبارسنجی** آن است، نه سیم‌کشی از صفر.

- [x] `migrateFromIndexedDB.js` را بخوان و تأیید کن: idempotent است؟ (اجرای دوباره باید بی‌خطر `skipped:true` برگرداند، نه رکورد تکراری بسازد). *(idempotent است — هم در سطح پرچم `settings_kv.migrated_v1` و هم در سطح insert چون `bulkInsert` در `CustomerRepo`/`InvoiceRepo` از `ON CONFLICT DO UPDATE` استفاده می‌کند، نه INSERT خام. تأیید با `tests/phase1-migration-resilience-test.mjs`.)*
- [x] تست کن با دیتای واقعی/بزرگ (نه فقط چند رکورد نمونه) — از `tests/customer-search-1m-test.mjs` و `tests/perf-test-1m.mjs` که ظاهراً برای همین منظور وجود دارند استفاده یا الگوبرداری کن. *(هر دو سبز؛ علاوه بر آن‌ها، `phase1-migration-resilience-test.mjs` روی ۱۲۰۰ مشتری/۲۶۰۰ فاکتور شبیه‌سازی‌شده migrate را با شمارش واقعی جدول تأیید کرد.)*
- [x] رفتار خطا را بررسی کن: اگر migration وسط کار (مثلاً بعد از ۵۰٪ رکوردها) با خطا متوقف شود، آیا دفعه‌ی بعد به‌درستی از سر گرفته می‌شود یا داده‌ی ناقص/دوتایی می‌ماند؟ *(بررسی شد: چون خواندن مشتری/فاکتور با `Promise.all` انجام می‌شود، خطای خواندن باعث توقف کامل قبل از هر insert می‌شود، نه insert نصفه؛ اجرای بعدی با منبع سالم کامل و بدون رکورد دوتایی موفق می‌شود.)*
- [x] یک حالت شکست عمدی (corrupt IndexedDB record، رکورد با فیلد گمشده) بساز و تست کن migration کرش نمی‌کند و مسیر fallback فعلی (آرایه‌ی حافظه) هنوز کار می‌کند. *(⚠️ باگ واقعی پیدا شد: قبلاً یک رکورد بدون فیلد الزامی (مثلاً فاکتور بدون customerId) باعث throw کل `migrate()` می‌شد — روی دستگاه واقعی یعنی migration برای همیشه شکست می‌خورد، بی‌صدا. **رفع شد**: رکوردهای بدون id/customerId معتبر (فاکتور) یا id/name معتبر (مشتری) حالا قبل از insert فیلتر می‌شوند و تعدادشان در `skippedInvalidCustomers`/`skippedInvalidInvoices` گزارش می‌شود، به‌جای کرش کل عملیات.)*
- [x] تأیید کن `result.skipped` به‌درستی در run بعدی true می‌شود (یعنی migration را دوباره روی داده‌ی از قبل مهاجرت‌شده اجرا نمی‌کند). *(⚠️ باگ واقعی پیدا شد: `migrateFromIndexedDB.js` هرگز فیلد boolean `skipped` برنمی‌گرداند — فقط `status:'ok'|'skipped'|'error'`. کد مصرف‌کننده در `html8.html` (`bootstrapSQLitePhase2Step1`) قبلاً `if(result.skipped)` چک می‌کرد که همیشه `undefined`/false بود؛ یعنی حتی در `status:'error'` پیام «مهاجرت با موفقیت انجام شد» چاپ می‌شد. **رفع شد** — الان `result.status` چک می‌شود و هر سه حالت جدا لاگ می‌شوند.)*
- [x] یک flag/log قابل مشاهده برای کاربر یا حداقل در کنسول اضافه کن که وضعیت نهایی migration (موفق/رد‌شده/خطا) به‌وضوح مشخص باشد — الان فقط `console.log`/`console.debug` است و در محیط production دیده نمی‌شود. *(اضافه شد: `window.__sqliteMigrationStatus` (شامل حالت `'unavailable'` وقتی SQLite اصلاً در دسترس نیست) + لاگ‌های رنگی متمایز برای هر سه حالت در کنسول، به‌علاوه‌ی هشدار جداگانه اگر رکورد نامعتبری رد شده باشد.)*

**یافته‌ی جانبی (مستند شده، رفع‌نشده — کم‌ریسک):** فراخوانی `migrate(callback)` در `html8.html` یک تابع progress-callback پاس می‌دهد که `migrate()` اصلاً نمی‌پذیرد (امضای واقعی فقط `{readInvoicesSource, readCustomersSource, force}` است) — بی‌خطر است (فقط یک قابلیت لاگ پیشرفت که هیچ‌وقت واقعاً کار نمی‌کرده) ولی باید در فاز بعد یا هر بازنویسی دیگر این تابع رفع/حذف شود.

**معیار خروج از فاز:** روی یک دیتاست واقعیِ کپی‌شده از کاربر (یا شبیه‌سازی بزرگ)، migration یک‌بار درست اجرا می‌شود، رکورد گم نمی‌شود، و اجرای دوم بدون تغییر رد می‌شود (skip). ✅ تأیید شد.

**هیچ‌چیزی حذف نشد.** (تغییرات این فاز فقط در `migrateFromIndexedDB.js` — سخت‌کردن در برابر رکورد ناقص/id تکراری — و در بلاک مصرف‌کننده‌ی `html8.html` بود؛ هیچ مسیر IndexedDB یا آرایه‌ی حافظه لمس نشد.)

---

## فاز ۲ — تبدیل خواندن‌های باقی‌مانده‌ی `invoices[]` (~۱ تا ۱٫۵ روز) ✅ انجام شد (گروه الف و گروه ب هر دو کامل)

هر مورد باید جداگانه بررسی و migrate شود؛ اینجا اولویت‌بندی بر اساس ریسک:

### گروه الف — از قبل الگوی fallback دارند (ریسک کم، فقط باید fallback حذف شود) ✅ انجام شد
خط‌های ۴۱۹۰-۴۲۴۵ تقریباً (`buildInvMonthList`, تابع شمارش ماه/تاریخ): این‌ها همین حالا اول `InvoiceRepo` را امتحان می‌کنند و در خطا به آرایه برمی‌گردند.
- [x] بعد از اطمینان از فاز ۱، این‌ها را از «fallback با catch ساکت» به «صرفاً async از Repo، بدون fallback به آرایه» تبدیل کن. *(هر دو تابع — `fetchInvMonthList` خط ۴۱۹۵ و `fetchInvMonthCount` خط ۴۲۳۰ — بازنویسی شدند: دیگر به `invoices` دست نمی‌زنند؛ در نبود Repo یا خطا به‌ترتیب `[]`/`0` برمی‌گردانند و با `console.error` بلند لاگ می‌کنند، نه `console.debug` ساکت قبلی.)*
- [x] چون این توابع از قبل async هستند، زنجیره‌ی `await` در فراخوانی‌کننده‌ها احتمالاً از قبل درست است — فقط دوباره چک شود. *(بررسی شد: تنها فراخوان `fetchInvMonthList` خودِ `buildInvMonthList` است که آن‌هم هیچ‌جای UI صدا زده نمی‌شود — طبق کامنت قبلی کد؛ `fetchInvMonthCount` در `renderInvMonthSwitcher` خط ۴۲۷۰ از قبل با `await` درست فراخوانی می‌شد. هیچ زنجیره‌ی جدیدی نیاز به تغییر نداشت.)*
- [x] خط ۴۲۰۸ (`invoices.length`/`invoices[invoices.length-1]` داخل `buildInvMonthList`) **عمداً دست‌نخورده ماند** — طبق چک‌لیست فاز ۰، این فقط امضای کش برای invalidation است، نه منبع داده، و در دامنه‌ی گروه الف نبود.
- [x] تست‌های مرتبط اجرا شدند تا از عدم رگرسیون اطمینان حاصل شود: `run-integration-tests.mjs`، `phase1-migration-resilience-test.mjs`، تمام `invoice-*-test.mjs`، `backup-ndjson-test.mjs`، `lazy-load-cache-connect-test.mjs`، `stats-worker-test.mjs` — همگی سبز (۰ شکست).

### گروه ب — کاملاً sync، مستقیم روی آرایه (ریسک بالا — دومینوی async) ✅ انجام شد (هر ۶ مورد تمام شدند)

اولویت به ترتیب اهمیت در UI:

1. **فیلتر اصلی لیست فاکتورها** (خط ~۴۶۸۴، `baseList=invoices.slice()...`) ✅ یافته: از قبل انجام شده بود
   - [x] این تابع را async کن و از `InvoiceRepo.getPage`/کوئری فیلتر معادل بخواند. *(بررسی این جلسه: `renderInvoicesList` از قبل `async` است و رندر کارت‌ها از طریق `createVirtualList`+`fetchInvoicesPage` مستقیماً `InvoiceRepo.getPage`/`getPageByCursor` را صدا می‌زند — این کار ظاهراً در یک تکرار قبلی انجام شده بی‌آن‌که چک‌باکس این نقشه‌راه به‌روزرسانی شود. `baseList=invoices.slice()` هنوز sync است ولی طبق کامنت خودِ کد (خط ۴۶۸۶) عمداً فقط fallback شمارش وضعیت/بدهکاران است، نه منبع رندر کارت‌ها — این خارج از دامنه‌ی این مورد است.)*
   - [x] همه‌ی فراخوانی‌کننده‌ها (renderInvoicesList و مشابه) را برای `await` بررسی کن. *(بررسی شد: تنها فراخوان مستقیم `_invoicesVirtualList.setTotal` با `await` است؛ `fetchItems` خودِ VirtualList هم به‌صورت async فراخوانی می‌شود.)*

2. **مودال فاکتورهای مشتری** (خط ۳۵۳۸، `invoices.filter(inv=>inv.customerId===...)`) ✅ یافته: از قبل انجام شده بود
   - [x] به کوئری `InvoiceRepo` بر اساس `customerId` وصل شود. *(بررسی این جلسه: `fetchCustomerInvoicesPage`، دقیقاً هم‌الگو با مورد ۱، از قبل `InvoiceRepo.getPage({customerId,...})` را صدا می‌زند؛ `fullList=invoices.filter(...)` در `renderCustInvoicesModal` هم مثل مورد ۱ فقط fallback/چک‌خالی‌بودن مستند‌شده است، نه منبع رندر.)*

3. **کارت نمایش مشتری — شمارش فاکتورها** (خط ۳۳۹۷، `renderCustomerViewCard`) ✅ انجام شد (این جلسه)
   - [x] چون این تابع داخل یک map روی لیست مشتریان صدا زده می‌شود، تبدیل ساده به async می‌تواند N+1 کوئری بسازد — شمارش فاکتور per-customer به یک کوئری تجمیعی (aggregate) جدا در `renderCustomersList` تبدیل شد، دقیقاً طبق پیشنهاد خودِ این ردیف نقشه‌راه.
     - متد جدید `InvoiceRepo.getCountsByCustomerIds(customerIds)` اضافه شد (`InvoiceRepo.js`): یک `SELECT customerId, COUNT(*) ... WHERE customerId IN (...) GROUP BY customerId` روی فقط idهای درخواستی — همیشه ۱ کوئری، نه N.
     - `renderCustomersList` قبل از رندر کارت‌ها، این نگاشت را برای customerIdهای فقط همان صفحه (نه کل دیتابیس) می‌گیرد و به `renderCustomerViewCard(c, count)` پاس می‌دهد.
     - `renderCustomerViewCard` امضای جدید `(c, invoiceCount)` دارد؛ اگر `invoiceCount` پاس داده نشود (فراخوانی قدیمی/جای دیگر)، دقیقاً همان رفتار sync قبلی (`invoices.filter`) را انجام می‌دهد — بدون شکستن فراخوان‌های دیگر.
     - اگر `InvoiceRepo` در دسترس نباشد یا کوئری خطا بدهد، `fetchCustomerInvoiceCounts` بی‌صدا `null` برمی‌گرداند و کارت به همان fallback sync قبلی برمی‌گردد — همان سیاست «هر دو سیستم هم‌زمان زنده» بقیه‌ی این فاز.
     - تست جدید `tests/invoice-counts-by-customer-test.mjs` (۸ assertion، همه سبز): شمارش درست چند مشتری، غیبت مشتری بدون فاکتور از نگاشت (نه صفر صریح)، فیلتر شدن به فقط idهای درخواستی، ورودی خالی/null-safe.
     - کپی Node-test (`tests/_node_test_copies/InvoiceRepo.js`) هم‌گام‌سازی شد؛ کل مجموعه تست‌های موجود (`run-integration-tests`, `customer-search-wire-test`, `customer-fts-search-test`, `invoice-getpage-cap-test`, `invoice-getpage-cursor-test`, `lazy-load-cache-connect-test`, `invoice-cursor-routing-test`, `stats-worker-test`, `backup-ndjson-test`) دوباره اجرا و همگی سبز ماندند (۰ رگرسیون).

4. **به‌روزرسانی نام مشتری روی فاکتورهای مرتبط** (خط ۳۵۷۱، `invoices.forEach(...)`) ✅ انجام شد (این جلسه)
   - [x] این یک عملیات نوشتن گروهی است (نه فقط خواندن) — باید به یک متد batch-update در `InvoiceRepo` (مثل `updateCustomerNameForInvoices(customerId, newName)`) تبدیل شود، نه لوپ روی آرایه + `dbSaveInvoice` تکی. *(اضافه شد: `InvoiceRepo.updateCustomerNameForInvoices(customerId, newName)` — یک `db.withTransaction` واحد که هر دو جدول `invoices` و `invoice_items` را `UPDATE` می‌کند.)*
     - **یافته‌ی جانبی حین این مورد:** `customerName` نه‌فقط روی `invoices` بلکه روی `invoice_items` هم denormalize شده بود (برای کوئری‌های تب آمار بدون JOIN، طبق کامنت `schema.js`). اگر متد جدید فقط `invoices` را به‌روز می‌کرد، بعد از تغییر نام مشتری تب آمار همچنان نام قدیمی را برای فاکتورهای قبلی نشان می‌داد. رفع شد با یک تراکنش واحد که هر دو جدول را هم‌زمان `UPDATE` می‌کند.
     - سمت `html8.html`: `saveCustomerEdit` دیگر لوپ+`dbSaveInvoice` تکی صدا نمی‌زند؛ آرایه‌ی حافظه با یک `idbBulkPut('invoices',affected)` (به‌جای N بار `idbPut`) و SQLite با یک فراخوانی `InvoiceRepo.updateCustomerNameForInvoices(id,name)` به‌روز می‌شود. اگر `InvoiceRepo`/متد در دسترس نباشد، بی‌صدا catch می‌شود و IndexedDB (که همیشه هم‌زمان نوشته می‌شود) تنها منبع باقی می‌ماند — همان سیاست «هر دو سیستم زنده».
     - تست جدید `tests/update-customer-name-for-invoices-test.mjs` (۵ assertion، همه سبز): آپدیت گروهی درست، هم‌زمانی با `invoice_items`، عدم تأثیر روی مشتری دیگر، `customerId` بدون فاکتور بدون خطا، `customerId` تهی/null بدون کوئری.
     - کپی Node-test (`tests/_node_test_copies/InvoiceRepo.js`) هم‌گام‌سازی شد؛ کل مجموعه تست‌های موجود (`run-integration-tests`, `invoice-counts-by-customer-test`, `invoice-getpage-cap-test`, `invoice-getpage-cursor-test`, `invoice-cursor-routing-test`, `customer-search-wire-test`, `customer-fts-search-test`, `lazy-load-cache-connect-test`, `stats-worker-test`, `backup-ndjson-test`, `phase1-migration-resilience-test`, `write-integrity-test`) دوباره اجرا و همگی سبز ماندند (۰ رگرسیون).

5. **حذف فاکتور از آرایه** (خط ۵۲۶۶) ✅ انجام شد (این جلسه)
   - [x] `invoices=invoices.filter(...)` باید حذف شود؛ منبع حقیقت مستقیماً از `InvoiceRepo.remove`/`deleteWithStockRestore` می‌آید و UI باید بعد از حذف دوباره از Repo بخواند، نه آرایه‌ی محلی را دستکاری کند. *(`dbDeleteInvoiceRecordWithStock` از fire-and-forget به `async` تبدیل شد (نتیجه‌ی هر دو مسیر `deleteWithStockRestore`/`remove` حالا await می‌شود). `deleteInvoice` دیگر `invoices=invoices.filter(...)` را به‌عنوان مکانیزم حذف صدا نمی‌زند؛ به‌جایش `await dbDeleteInvoiceRecordWithStock(...)` می‌کند و سپس رندر مجدد را صدا می‌زند — رندرها (`renderInvoicesList`/`renderCustInvoicesModal`) طبق موارد ۱ و ۲ همین گروه از `InvoiceRepo.getPage` می‌خوانند، نه از آرایه. **باگ واقعی پیدا شد**: `window.__invoiceCache` (کش LRU مستقل `resolveInvoiceById`) هیچ‌وقت بعد از حذف invalidate نمی‌شد — فقط آرایه‌ی حافظه فیلتر می‌شد؛ یعنی روی یک اپ با بارگذاری پس‌زمینه‌ی ناتمام، کلیک بعدی روی همان فاکتور می‌توانست رکورد حذف‌شده را از کش برگرداند. رفع شد با `window.__invoiceCache.delete(id)` کنار `invoices.splice(idx,1)` (به‌جای بازساخت کل آرایه با `filter`) — هر دو صرفاً invalidation کش هستند، نه مسیر حذف.)*

6. **دراپ‌داون فیلتر مشتری در لیست فاکتورها** (خط ۴۴۵۲-۴۴۵۶، `renderInvoicesFilterOptions`) ✅ انجام شد (این جلسه)
   - [x] `[...new Set(invoices.map(i=>i.customerId))]` + `invoices.find(...)` (fallback نام) به یک متد جدید `InvoiceRepo.getDistinctCustomers()` تبدیل شد — یک کوئری واحد `SELECT customerId, customerName FROM invoices GROUP BY customerId` (همان الگوی بدون-تابع-تجمیعی‌روی-customerName که در `getStatsLeaders` هم استفاده شده)، نه N کوئری per-customer. متد داخل `InvoiceRepo.js` (کنار `getCountsByCustomerIds`) اضافه شد و در کپی تست Node (`tests/_node_test_copies/InvoiceRepo.js`) هم‌گام‌سازی شد.
   - [x] `renderInvoicesFilterOptions` به async تبدیل شد. *(بررسی فراخوان‌کنندگان: همه‌ی ۶ نقطه‌ی صدازدن — `renderAll`، `switchTab`، بعد از ثبت فاکتور جدید، بعد از ویرایش مشتری، سوییچر ماه، و مرجع `Invoices.renderFilters` — از قبل بدون `await` صدا می‌زنند، دقیقاً هم‌الگو با `renderInvoicesList` که خودش هم async است ولی همه‌جا fire-and-forget فراخوانی می‌شود؛ چون تنها اثر جانبی این تابع نوشتن `innerHTML` یک `<select>` است (نه یک مقدار بازگشتی که فراخوان‌کننده لازم داشته باشد)، این الگوی جاافتاده‌ی خودِ کدبیس همچنان درست است — نیازی به تغییر هیچ‌کدام از فراخوان‌کنندگان نبود.)*
   - [x] در نبود `window.__InvoiceRepo`/متد یا خطای کوئری، دراپ‌داون فقط «همه مشتریان» را نشان می‌دهد (نه throw) — دقیقاً هم‌قرارداد `fetchInvMonthList`/`fetchInvMonthCount` در گروه الف همین فاز.
   - [x] تست جدید `tests/invoice-distinct-customers-test.mjs` (۴ assertion، همه سبز): چند فاکتور با customerId تکراری → دقیقاً یک ردیف per-customer، نام مشتری درست، جدول خالی → `[]` بدون throw.
   - [x] مجموعه‌ی تست‌های مرتبط دوباره اجرا شدند تا از عدم رگرسیون اطمینان حاصل شود: `invoice-distinct-customers-test`, `invoice-counts-by-customer-test`, `invoice-getpage-cap-test`, `invoice-getpage-cursor-test`, `invoice-cursor-routing-test`, `invoice-cache-test`, `invoice-items-denormalize-test`, `resolve-invoice-by-id-test`, `customer-search-wire-test`, `customer-fts-search-test`, `lazy-load-cache-connect-test`, `stats-worker-test`, `backup-ndjson-test`, `phase1-migration-resilience-test`, `write-integrity-test`, `update-customer-name-for-invoices-test`, `run-integration-tests` — همگی سبز (۰ رگرسیون). *(`nextInvoiceId` محاسبه‌شده از `invoices.reduce` در بازیابی بکاپ، خط ۶۱۰۸، در دامنه‌ی این مورد نبود — بخشی از فاز ۴ بکاپ است.)*

**فاز ۲ / گروه ب کامل شد** — هر ۶ مورد اولویت‌بندی‌شده انجام شدند.

- [x] بعد از هر مورد، تست دستی + اجرای `tests/invoice-*` مرتبط. *(برای هر ۶ مورد گروه ب انجام شد.)*
- [x] برای هر تابعی که از sync به async تبدیل می‌شود، **همه‌ی فراخوانی‌کنندگانش** (نه فقط مستقیم، بلکه زنجیره‌ی کامل تا event handler اصلی مثل `onclick`) باید بررسی و await بگیرند. پیشنهاد: قبل از تبدیل هر تابع، با `grep` همه‌ی جاهایی که آن تابع صدا زده می‌شود را لیست کن. *(برای هر ۶ مورد انجام شد؛ آخرین مورد — `renderInvoicesFilterOptions` — طبق بررسی بالا.)*

---

## فاز ۳ — تبدیل خواندن‌های باقی‌مانده‌ی `customers[]` (~نیم تا ۱ روز)

مشابه فاز ۲ ولی حجم کمتر:

- [ ] `getCustomerById`, `findCustomerByName` (خط ۷۵۸-۷۵۹): توابع بسیار پرکاربردند (احتمالاً در جاهای زیادی صدا زده می‌شوند) — تبدیل این دو به async بیشترین اثر دومینویی را دارد؛ باید اول همه‌ی فراخوانی‌کنندگان لیست شوند.
- [ ] `addCustomer` — چک تکراری‌بودن نام (خط ۳۳۰۹) و افزودن به آرایه (۳۳۱۵): باید بررسی یکتایی نام به یک کوئری SQLite (`WHERE name = ?`) تبدیل شود.
- [ ] `removeCustomer` (خط ۳۳۲۵) — مشابه حذف فاکتور، آرایه دستکاری نشود.
- [ ] `renderCustomersList` — فیلتر fallback (خط ۳۳۶۶) از قبل الگوی Repo/fallback دارد (`fetchCustomersPage`)؛ فقط باید fallback حذف شود بعد از اطمینان.
- [ ] datalist مشتریان (خط ۳۵۸۰، `customers.map(...)`) — برای خودتکمیلی فرم؛ می‌تواند از یک کوئری سبک (فقط نام‌ها) تغذیه شود.

---

## فاز ۴ — `loadState` / `reloadInvoicesLazy` / بکاپ (~۱ روز)

- [ ] `loadState` (خط ۱۱۳۷): بازنویسی تا صفحه‌ی اول از `InvoiceRepo.getPage`/`CustomerRepo.getPage` بخواند، نه `idbGetAllLimited`.
- [ ] `reloadInvoicesLazy` (خط ۱۲۰۹): مسیر بارگذاری پس‌زمینه هم باید از Repo بیاید.
- [ ] `doBackup` (خط ۵۸۴۷) و `doBackupLegacyFromMemory` (۵۸۹۸): بکاپ باید مستقیماً از SQLite (صفحه‌بندی‌شده، طبق کامنت خودِ کد) بخواند، نه IndexedDB.
- [ ] `doRestore`/`doRestoreNdjson`/`doRestoreLegacyJson` (۵۹۸۸-۶۱۲۹): این‌ها هم‌اکنون `idbClear`+`idbBulkPut` را صدا می‌زنند (خط ۶۰۱۸، ۶۰۳۲، ۶۰۴۰، ۶۱۱۲-۶۱۱۳)؛ باید مستقیماً به نوشتن گروهی SQLite تبدیل شوند. **این یکی از حساس‌ترین بخش‌هاست چون کاربر مستقیماً از این مسیر بازیابی داده انجام می‌دهد** — تست بازیابی از یک بکاپ واقعی قبل و بعد از تغییر الزامی است.
- [ ] `clearAllData` (خط ۶۱۳۰): پاک‌سازی هم باید هر دو مسیر (فعلاً IndexedDB) و SQLite را هدف بگیرد؛ در نهایت فقط SQLite.

**تست الزامی این فاز:** یک بکاپ گرفته‌شده با نسخه‌ی *فعلی* (قبل از این فاز) باید با موفقیت در نسخه‌ی *جدید* (بعد از این فاز) بازیابی شود — سازگاری فرمت بکاپ نباید بشکند.

---

## فاز ۵ — حذف idb helperها (~نیم روز، فقط بعد از اطمینان کامل)

- [ ] هر ۳۸ فراخوانی idb* را طبق چک‌لیست فاز ۰ یکی‌یکی حذف کن (نوشتن موازی در `dbSaveInvoice`/`dbSaveCustomer`/`dbDeleteInvoiceRecord`/... خط ۱۰۰۲-۱۰۸۱).
- [ ] بعد از حذف فراخوانی‌ها، خودِ توابع `idbPut/idbGet/idbBulkPut/idbDelete/idbClear/idbGetAll/idbGetAllLimited` (خط ۸۷۸-۹۴۸) را حذف کن.
- [ ] یک دوره‌ی مشاهده (staging یا حداقل تست دستی طولانی‌تر) قبل از حذف نهایی توصیه می‌شود — چون این نقطه‌ی بازگشت‌ناپذیر است (دیگر fallback به IndexedDB وجود نخواهد داشت).
- [ ] فقط بعد از این فاز، پاک کردن دیتابیس IndexedDB خودش (نه فقط کد helper) به‌عنوان یک migration نهایی (مثلاً حذف دیتابیس با `indexedDB.deleteDatabase`) در نظر گرفته شود — و این هم باید پشت یک شرط «migration تأییدشده» باشد.

---

## فاز ۶ — تست رگرسیون کامل + مستندسازی (~نیم روز)

- [ ] تمام تست‌های `tests/` دوباره اجرا شوند (به‌خصوص تست‌های موازی/perf که در پیام قبلی اشاره شد: `perf-test-1m.mjs`, `customer-search-1m-test.mjs`, `write-integrity-test.mjs`, `vacuum-churn-test.mjs`).
- [ ] `roadmap-infinite-records-v4_28.md` موجود در پروژه به‌روزرسانی شود تا وضعیت نهایی را منعکس کند.
- [ ] کامنت‌های کد که به «سیاست هر دو سیستم زنده تا فاز ۲.۶» اشاره می‌کنند حذف/به‌روز شوند تا کد به‌خودی‌خود گمراه‌کننده نباشد.

---

## خلاصه‌ی ریسک در هر فاز

| فاز | ریسک اصلی | برگشت‌پذیر؟ | وضعیت |
|---|---|---|---|
| ۰ | هیچ (فقط مستندسازی) | بله | ✅ انجام شد |
| ۱ | migration ناقص روی داده‌ی واقعی کشف نشود | بله (چیزی حذف نمی‌شود) | ✅ انجام شد — ۲ باگ واقعی پیدا و رفع شد (رکورد ناقص کرش می‌کرد؛ `result.skipped` هیچ‌وقت true نمی‌شد) |
| ۲ | فراموشی `await` در زنجیره‌ی فراخوانی → باگ خاموش | نسبتاً (کد قابل revert است) | ✅ انجام شد — هر ۶ مورد گروه ب + هر دو مورد گروه الف، بدون رگرسیون |
| ۳ | همان ریسک فاز ۲، روی `customers[]` | نسبتاً (کد قابل revert است) | – |
| ۴ | خرابی بکاپ/بازیابی → از دست رفتن داده‌ی کاربر در همان لحظه | باید قبل از merge تست شود | – |
| ۵ | حذف idb helperها بدون اطمینان کافی → دیگر مسیر برگشت نیست | **خیر — نقطه‌ی بی‌بازگشت** | – |
| ۶ | چیزی از قلم بیفتد که تست پوشش نمی‌دهد | - | – |

**تخمین کل:** ۴ تا ۶ روز کار متمرکز (نزدیک به تخمین اولیه‌ی خودت)، با این تفاوت که فاز ۱ اکنون «تأیید» است نه «سیم‌کشی از صفر» — چون آن بخش از قبل انجام شده بود.
