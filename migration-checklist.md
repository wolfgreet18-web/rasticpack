# چک‌لیست فاز ۰ — خط پایه (Baseline)

> تولید شده در تاریخ تحلیل روی `html8.html` (۷۱۵۵ خط) داخل `html34.zip`.
> این فایل فقط مستندسازی است — هیچ کدی تغییر نکرده.
>
> **به‌روزرسانی (فاز ۲ / گروه الف):** دو ردیف اول جدول زیر (۴۲۰۴ و ۴۲۴۲-۴۲۴۳ سابق) پیاده‌سازی و
> از این چک‌لیست حذف/خط‌خورده شدند — جزئیات در `sqlite-migration-roadmap.md`، بخش فاز ۲.
>
> **به‌روزرسانی (فاز ۵ — این جلسه):** بخش ۳ (فراخوانی‌های `idb*`) کامل بازنویسی شد — هر ۲۴
> فراخوانی واقعی (نه فقط ۲۳ شمارش اولیه‌ی فاز ۰) حذف شدند و کل لایه‌ی helper از `html8.html`
> پاک شد. جزئیات کامل در `sqlite-migration-roadmap.md`، بخش فاز ۵.

---

## ۱) نقاط خواندن/نوشتن مستقیم آرایه‌ی `invoices[]`

| خط | کد | وضعیت |
|---|---|---|
| 1101 | `let inv=invoices.find(i=>i.id===id);` | فقط آرایه (باید تبدیل شود) — گلوگاه `resolveInvoiceById` |
| 1112 | `invoices.push(normalized);` | فقط آرایه — نتیجه‌ی resolve را کش می‌کند |
| 1222 | `invoices.slice(-STARTUP_PAGE_SIZE)` (داخل `.then`) | فقط آرایه — پر کردن `InvoiceCache` بعد از لود پس‌زمینه |
| 2458 | `invoices.push(newInv);` | فقط آرایه — بعد از ایجاد فاکتور جدید |
| ~~3397~~ | ~~`invoices.filter(inv=>inv.customerId===c.id)`~~ | ✅ **[فاز ۲ گروه ب مورد ۳] حذف شد** — `renderCustomerViewCard` حالا `invoiceCount` را از `renderCustomersList` می‌گیرد (که یک‌جا با `InvoiceRepo.getCountsByCustomerIds` برای کل صفحه‌ی جاری پر می‌شود)؛ `invoices.filter` فقط fallback وقتی `invoiceCount` پاس داده نشود |
| 3538 | `invoices.filter(inv=>inv.customerId===custInvModalCustomerId)` | **دارد fallback به Repo** (یافته‌ی فاز ۲ گروه ب مورد ۲) — `renderCustInvoicesModal` از `fetchCustomerInvoicesPage`→`InvoiceRepo.getPage({customerId})` تغذیه می‌شود؛ این خط فقط fallback/چک طول است |
| ~~3571~~ (اکنون ~3597) | ~~`invoices.forEach(inv=>{...dbSaveInvoice(inv);})`~~ | ✅ **[فاز ۲ گروه ب مورد ۴] حذف شد** — `saveCustomerEdit` حالا با `idbBulkPut` (یک نوشتن گروهی به‌جای N بار `idbPut`) و `InvoiceRepo.updateCustomerNameForInvoices(customerId,name)` (یک `UPDATE` گروهی تراکنشی روی `invoices`+`invoice_items`، به‌جای N بار `InvoiceRepo.save` تکی) کار می‌کند؛ آرایه‌ی حافظه هنوز مستقیماً ویرایش می‌شود (`affected.forEach(inv=>{inv.customerName=name;})`) چون بازنمایی خودِ حافظه هنوز منبع UI است |
| 3732 | `invoices.forEach(...)` | فقط آرایه — تجمیع آمار |
| 3774-3795 | حلقه‌ی `for` روی `invoices.length` / `invoices[i]` (×۲ بلاک) | فقط آرایه — آمار/جمع‌بندی سنگین |
| 3945 | `if(!invoices.length)` | فقط آرایه — چک خالی‌بودن قبل از رندر آمار |
| 3978 | `invoices.forEach(inv=>{...})` | فقط آرایه — حلقه‌ی آماری دوم |
| ~~4204~~ | ~~`invoices.map(inv=>inv.date)`~~ | ✅ **[فاز ۲ گروه الف] حذف شد** — `fetchInvMonthList` (اکنون خط ۴۱۹۵-۴۲۰۸) دیگر fallback ندارد، فقط `InvoiceRepo.getAllDates`؛ در نبود Repo `[]` برمی‌گرداند |
| 4211 (جابجا شده از ۴۲۰۸) | `invoices.length` / `invoices[invoices.length-1]` | فقط آرایه — امضای کش (`buildInvMonthList`)، صرفاً برای invalidation، نه منبع داده — **عمداً دست‌نخورده، خارج از دامنه‌ی گروه الف** |
| ~~4242-4243~~ | ~~`invoices.filter(...).length` (×۲)~~ | ✅ **[فاز ۲ گروه الف] حذف شد** — `fetchInvMonthCount` (اکنون خط ۴۲۳۳-۴۲۴۹) دیگر fallback ندارد، فقط `InvoiceRepo.getMonthCount`؛ در نبود Repo `0` برمی‌گرداند |
| ~~4452~~ | ~~`invoices.map(i=>i.customerId)`~~ | ✅ **[فاز ۲ گروه ب مورد ۶] حذف شد** — `renderInvoicesFilterOptions` حالا async است و از `InvoiceRepo.getDistinctCustomers()` (یک کوئری `GROUP BY customerId` واحد) می‌خواند؛ در نبود Repo/خطا لیست مشتریان فیلتر خالی می‌ماند («همه مشتریان» تنها گزینه) |
| ~~4456~~ | ~~`invoices.find(i=>i.customerId===id)`~~ | ✅ **[فاز ۲ گروه ب مورد ۶] حذف شد** — fallback نام مشتری حالا از همان نتیجه‌ی `getDistinctCustomers()` (Map محلی `nameById`) می‌آید، نه از آرایه‌ی حافظه |
| 4693 (جابجا شده) | `let baseList=invoices.slice();` | **دارد fallback به Repo** (یافته‌ی فاز ۲ گروه ب مورد ۱) — رندر کارت‌ها از `fetchInvoicesPage`→`InvoiceRepo.getPage`/`getPageByCursor` می‌آید؛ این خط فقط fallback شمارش وضعیت/بدهکاران است، طبق کامنت خودِ کد |
| ~~5266~~ (اکنون ۵۳۱۴/۵۳۳۷) | ~~`invoices=invoices.filter(i=>i.id!==id);`~~ | ✅ **[فاز ۲ گروه ب مورد ۵] حذف شد** — `deleteInvoice` حالا `await dbDeleteInvoiceRecordWithStock(...)` می‌کند (که خودش async شد) تا حذف واقعی در Repo تمام شود قبل از رندر مجدد؛ رندرهای بعدی (`renderInvoicesList`/`renderCustInvoicesModal`) طبق موارد ۱ و ۲ همین گروه از `InvoiceRepo.getPage` می‌خوانند، نه آرایه. `invoices.splice`/`window.__invoiceCache.delete` فقط برای invalidation کشِ `resolveInvoiceById` نگه داشته شدند (باگ واقعی پیدا شد: `window.__invoiceCache` قبلاً هرگز اینجا پاک نمی‌شد) |
| 6093 | `invoices=Array.isArray(data.invoices)?...` | فقط آرایه — بازگردانی کامل آرایه از بکاپ (بخشی از فاز ۴) |
| 6108 | `invoices.reduce((x,i)=>Math.max(x,i.id),0)` | فقط آرایه — محاسبه‌ی `nextInvoiceId` بعد از restore (بخشی از فاز ۴) |
| 6127 | `invoices.length` | فقط آرایه — فقط برای پیام موفقیت بکاپ، بی‌خطر |

**جمع‌بندی (به‌روزرسانی فاز ۲ گروه ب، موارد ۱-۶ — همه‌ی گروه ب تمام شد):** ۲ مورد fallback‌دار قبلی (گروه الف) + ۲ مورد دیگر که در جلسه‌ی قبل کشف شد از قبل fallback‌دار بوده‌اند (خط ۳۵۳۸ و ۴۶۹۳/مودال و فیلتر اصلی) + ۴ مورد که به‌صورت کوئری/نوشتن گروهی حل شدند (خط ۳۳۹۷ با کوئری تجمیعی، خط ۳۵۷۱ با `UPDATE` گروهی، خط ۵۲۶۶ با حذف async+invalidation کش، خط ۴۴۵۲/۴۴۵۶ با `getDistinctCustomers`) = ۸ مورد جمع‌بندی‌شده؛ موارد باقی‌مانده‌ی فقط-آرایه همگی خارج از دامنه‌ی فاز ۲ گروه ب‌اند (فاز ۳/۴).

---

## ۲) نقاط خواندن/نوشتن مستقیم آرایه‌ی `customers[]`

> **به‌روزرسانی (فاز ۳ — این جلسه):** ۴ ردیف باقی‌مانده (۳۳۰۹، ۳۳۱۵-بخش چک، ۳۳۲۵، ۳۳۶۶، ۳۵۸۰) پیاده‌سازی/رفع fallback شدند — جزئیات در `sqlite-migration-roadmap.md`، فاز ۳. `getCustomerById`/`findCustomerByName` (۷۵۸-۷۵۹) از قبل (جلسه‌ی پیشین) بدون fallback به Repo وصل شده بودند.

| خط | کد | وضعیت |
|---|---|---|
| ~~758~~ | ~~`getCustomerById = id => customers.find(...)`~~ | ✅ **[فاز ۳] حذف شد** — پل به `CustomerRepo.getById`، بدون fallback به آرایه؛ در نبود Repo/خطا `null` برمی‌گرداند و `console.error` می‌زند |
| ~~759~~ | ~~`findCustomerByName = name => customers.find(...)`~~ | ✅ **[فاز ۳] حذف شد** — پل به `CustomerRepo.findByExactName`، بدون fallback؛ در `addCustomer` هم برای چک یکتایی نام استفاده می‌شود |
| 1231-1233 | `if(!customers.length){...customers.push(sampleCust);}` | فقط آرایه — seed نمونه، ریسک پایین، خارج از دامنه‌ی فاز ۳ |
| ~~3309 (اکنون ۳۳۷۳ چک تکراری)~~ | ~~`customers.find(c=>c.name...===name...)`~~ | ✅ **[فاز ۳ — این جلسه] حذف شد** — `addCustomer` حالا `await findCustomerByName(name)` می‌کند (کوئری واقعی SQLite با `COLLATE NOCASE`)، نه جست‌وجوی روی آرایه‌ی محلی که می‌توانست ناقص/قدیمی باشد |
| 3315 (اکنون ~۳۳۸۰) | `customers.push(newCust);` | فقط آرایه — عمداً دست‌نخورده ماند؛ طبق سیاست «هر دو سیستم زنده»، این فقط کش خواندن محلی است، `dbSaveCustomer` همان لحظه به `CustomerRepo.save` هم می‌نویسد |
| ~~3325 (اکنون ۳۴۰۰ removeCustomer)~~ | ~~`customers=customers.filter(c=>c.id!==id);`~~ | ✅ **[فاز ۳ — این جلسه] حذف شد** — `removeCustomer` حالا `await dbDeleteCustomerRecord(id)` می‌کند (که خودش async شد و `CustomerRepo.remove` را await می‌کند) تا حذف واقعی در Repo تمام شود قبل از رندر مجدد؛ هم‌الگو با `deleteInvoice`/فاز ۲ گروه ب #۵. `customers.splice` فقط برای invalidation کش محلی نگه داشته شد |
| 3362 (اکنون ~۳۴۴۹) | `if(!customers.length)` | فقط آرایه — چک خالی‌بودن اولیه قبل از هر کوئری Repo؛ عمداً دست‌نخورده، خارج از دامنه‌ی موارد این فاز |
| ~~3366~~ | ~~`customers.filter(...)` / بدون q: خودِ `customers` (fallback `fetchCustomersPage`)~~ | ✅ **[فاز ۳ — این جلسه] fallback حذف شد** — `fetchCustomersPage` دیگر پارامتر `fullList` نمی‌گیرد؛ در نبود Repo/خطا `{items:[],total:0}` برمی‌گرداند و `console.error` می‌زند، هم‌الگو با `fetchInvMonthList`/`fetchInvMonthCount` (فاز ۲ گروه الف) |
| 3557 | `customers.find(x=>x.id===id)` | فقط آرایه — شروع ویرایش مشتری (`updateCustomer`)، خارج از دامنه‌ی ۴ موردِ این فاز |
| 3560 | `customers.find(x=>x.id!==id&&x.name...)` | فقط آرایه — چک یکتایی نام هنگام ویرایش، خارج از دامنه‌ی ۴ موردِ این فاز (فقط `addCustomer` در چک‌لیست بود، نه `updateCustomer`) |
| ~~3580 (اکنون ۳۷۰۳)~~ | ~~`customers.map(c=>...)`~~ | ✅ **[فاز ۳ — این جلسه] حذف شد** — `renderCustomerDatalist` حالا از `CustomerRepo.getAllNames()` (متد جدید) می‌خواند، بدون fallback به آرایه |
| 6107 | `customers.reduce((x,i)=>Math.max(x,i.id),0)` | فقط آرایه — محاسبه‌ی `nextCustId` بعد از restore (فاز ۴) |
| 6127 | `customers.length` | فقط آرایه — فقط پیام موفقیت، بی‌خطر |

**جمع‌بندی (به‌روزرسانی فاز ۳):** ۶ مورد رفع‌شده (۲ مورد `getCustomerById`/`findCustomerByName` از جلسه‌ی پیشین + ۴ مورد `addCustomer`/`removeCustomer`/`fetchCustomersPage`/`renderCustomerDatalist` از این جلسه) + ۱ مورد seed نمونه (۱۲۳۱-۱۲۳۳) و ۳ مورد خارج‌از‌دامنه (چک خالی‌بودن اولیه، `updateCustomer`×۲) که در فاز ۳ نقشه‌راه اصلاً درخواست نشده بودند + ۲ مورد بکاپ (فاز ۴). فاز ۳ (طبق نقشه‌راه) **کامل شد**.

---

## ۳) فراخوانی‌های `idb*` — ✅ [فاز ۵ — این جلسه] همه حذف شدند

نقشه‌راه رقم **۳۸ فراخوانی** را ذکر کرده بود. شمارش دقیق فاز ۰ (با حذف ۶ تعریف تابع در خطوط ۸۷۸-۹۴۸ و ۲ اشاره‌ی صرفاً متنی داخل کامنت که هیچ‌کدام فراخوانی واقعی نبودند) عدد **۲۳ فراخوانی واقعی** را نشان داده بود. بازبینی فاز ۵ یک نقطه‌ی چهارم اضافه‌تر هم پیدا کرد (جمعاً **۲۴** — نگاه کن به یادداشت پایین جدول)؛ همه‌ی این ۲۴ فراخوانی در این جلسه حذف شدند و لایه‌ی helper (idbOpen/idbStore/idbPut/idbDelete/idbGetAll/idbGetAllLimited/idbClear/idbBulkPut/idbForEachBatch + `IDB_NAME`/`IDB_VERSION`/`_idbInstance`) کامل از `html8.html` پاک شد.

| خط (قبل از فاز ۵) | فراخوانی | نوع | جایگزین بعد از فاز ۵ |
|---|---|---|---|
| 1004 | `idbPut('invoices',inv)` | نوشتن | ✅ حذف — `dbSaveInvoice` فقط `InvoiceRepo.save` را می‌نویسد؛ در نبود Repo با `console.error` گزارش می‌شود، نه سقوط بی‌صدا |
| 1011 | `idbDelete('invoices',id)` | حذف | ✅ حذف — `dbDeleteInvoiceRecord` فقط `InvoiceRepo.remove` |
| 1019 | `idbPut('customers',cust)` | نوشتن | ✅ حذف — `dbSaveCustomer` فقط `CustomerRepo.save` |
| 1025 | `idbDelete('customers',id)` | حذف | ✅ حذف — `dbDeleteCustomerRecord` فقط `CustomerRepo.remove` |
| 1057 | `idbPut('invoices',inv)` | نوشتن | ✅ حذف — `dbSaveInvoiceWithStock` فقط `InvoiceRepo.insertWithStockUpdate`/`save` |
| 1071 | `idbDelete('invoices',id)` | حذف | ✅ حذف — `dbDeleteInvoiceRecordWithStock` فقط `InvoiceRepo.deleteWithStockRestore`/`remove` |
| 1176 | `idbGetAll('customers')` | خواندن | ✅ حذف — `fetchAllCustomersForStartup` در نبود `CustomerRepo.forEachBatch` آرایه‌ی خالی برمی‌گرداند (نه دیگر یک منبع جایگزین) |
| 1178 | `idbBulkPut('customers',legacyCustomers)` | نوشتن گروهی | ✅ حذف — مهاجرت یک‌باره‌ی `window.storage` قدیمی حالا مستقیم `CustomerRepo.bulkInsert` است |
| 1183 | `idbGetAllLimited('invoices',STARTUP_PAGE_SIZE)` | خواندن | ✅ حذف — `fetchFirstInvoicePageForStartup` در نبود `InvoiceRepo.getPage` آرایه‌ی خالی برمی‌گرداند |
| 1185 | `idbBulkPut('invoices',legacyInvoices)` | نوشتن گروهی | ✅ حذف — مهاجرت یک‌باره حالا مستقیم `InvoiceRepo.bulkInsert` است |
| 1218 | `idbGetAll('invoices').then(...)` | خواندن | ✅ حذف — `fetchAllInvoicesForBackgroundLoad` در نبود `InvoiceRepo.forEachBatch` آرایه‌ی خالی برمی‌گرداند |
| (فاز ۲) | `idbBulkPut('invoices',affected)` در `saveCustomerEdit` | نوشتن گروهی | ✅ حذف — نقطه‌ی چهارمی که فاز ۰ به‌صورت مجزا نشمرده بود (چون در فاز ۲ اضافه شده بود، بعد از تولید چک‌لیست فاز ۰)؛ این جلسه هم پیدا و هم حذف شد. فقط `InvoiceRepo.updateCustomerNameForInvoices` باقی ماند |
| 5934 (شماره‌ی بعد از فاز ۲-۴) | `idbForEachBatch(...)` در `repoForEachBatch` (fallback نبود Repo) | خواندن | ✅ حذف — در نبود Repo، `onBatch` صدا زده نمی‌شود و `console.error` گزارش می‌کند (بکاپ آن store خالی می‌ماند، نه یک بکاپ ناقص بی‌صدا) |
| 5940 | `idbForEachBatch(...)` در `repoForEachBatch` (catch خطای Repo) | خواندن | ✅ حذف — همان بالا |
| 6018 ×۲ | `idbClear('invoices')` / `idbClear('customers')` | حذف کامل | ✅ حذف — `doRestoreLegacyJson` فقط `InvoiceRepo.clearAll`/`CustomerRepo.clearAll` |
| 6032 | `idbBulkPut('customers',batch)` | نوشتن گروهی | ✅ حذف — فقط `CustomerRepo.bulkInsert` |
| 6040 | `idbBulkPut('invoices',normalized)` | نوشتن گروهی | ✅ حذف — فقط `InvoiceRepo.bulkInsert` |
| 6112 ×۲ | `idbClear('invoices')` / `idbClear('customers')` | حذف کامل | ✅ حذف — `doRestoreNdjson` فقط `InvoiceRepo.clearAll`/`CustomerRepo.clearAll` |
| 6113 ×۲ | `idbBulkPut('invoices',invoices)` / `idbBulkPut('customers',customers)` | نوشتن گروهی | ✅ حذف — فقط `InvoiceRepo.bulkInsert`/`CustomerRepo.bulkInsert` (batch به batch، نه یک‌جا) |
| 6137 ×۲ | `idbClear('invoices')` / `idbClear('customers')` | حذف کامل | ✅ حذف — `clearAllData` فقط `InvoiceRepo.clearAll`/`CustomerRepo.clearAll` |

**جمع نهایی:** ۲۴ فراخوانی واقعی (۲۳ شمارش فاز ۰ + ۱ مورد `saveCustomerEdit` که بعد از فاز ۰ در فاز ۲ اضافه شده بود) — همگی در فاز ۵ حذف شدند. توجه: هیچ فراخوانی idb برای store «inventory» وجود نداشت (مطابق هشدار خودِ کد که جدول SQL inventory اصلاً seed نمی‌شود) — این هنوز هم صادق است، فاز ۵ چیزی در این مورد تغییر نداد.

**تأیید نهایی فاز ۵:** `grep -n "idbPut\|idbGet\|idbDelete\|idbClear\|idbBulkPut\|idbForEachBatch\|idbOpen\|idbStore(" html8.html` فقط کامنت‌های مستندسازیِ خودِ این حذف را برمی‌گرداند، هیچ فراخوانی یا تعریف زنده‌ای باقی نمانده.

---

## ۴) نتیجه‌ی اجرای تست‌های موجود (baseline — قبل از هر تغییری)

محیط: Node v22.22.2 (`--experimental-sqlite` در دسترس، `node:sqlite` واقعی نه mock).

| فایل تست | نتیجه |
|---|---|
| run-integration-tests.mjs | ✅ 30/30 assertion |
| invoice-cache-test.mjs | ✅ 17/17 |
| single-write-cost-test.mjs | ✅ (بدون assertion شکست‌خورده) |
| startup-lazy-load-test.mjs | ✅ 4/4 |
| backup-ndjson-test.mjs | ✅ 27/27 |
| invoice-items-denormalize-test.mjs | ✅ 18/18 |
| ram-usage-test.mjs | ✅ 4/4 |
| perf-test.mjs | ✅ (همه PASS) |
| invoice-cursor-routing-test.mjs | ✅ 14/14 |
| invoice-getpage-cursor-test.mjs | ✅ 11/11 |
| keyset-pagination-perf-test.mjs | ✅ |
| vacuum-churn-test.mjs | ✅ (بدون خطا) |
| stats-aggregation-test.mjs | ✅ 19/19 |
| db-file-size-test.mjs | ✅ |
| stats-worker-test.mjs | ✅ 18/18 |
| write-integrity-test.mjs | ✅ 16/16 |
| resolve-invoice-by-id-test.mjs | ✅ 12/12 |
| lazy-load-cache-connect-test.mjs | ✅ 10/10 |
| customer-fts-search-test.mjs | ✅ 8/8 |
| backup-ram-usage-test.mjs | ✅ 8/8 |
| customer-search-wire-test.mjs | ✅ 12/12 |
| stats-leaders-test.mjs | ✅ 19/19 |
| invoice-getpage-cap-test.mjs | ✅ 8/8 |
| perf-test-1m.mjs (معیار پذیرش رسمی فاز ۱) | ✅ همه زیر ۵۰ms |
| customer-search-1m-test.mjs (معیار پذیرش رسمی) | ✅ همه زیر ۳۰ms |

**اجرا نشد (عمداً، خارج از دامنه‌ی فاز ۰):**
- `seed-chunk.node-only.mjs`, `stress-test-phase7-query.node-only.mjs`, `fileDbConnection.node-only.mjs`, `testConnection.node-only.mjs` — این‌ها ابزارک‌های کمکی فاز ۷ هستند، نیاز به آرگومان مسیر فایل دیسک و چند اجرای جدا دارند؛ خودشان "تست" مستقل با معیار pass/fail نیستند.

**نتیجه:** خط پایه ۱۰۰٪ سبز است. هیچ تست شکست‌خورده‌ای قبل از فاز ۱ وجود ندارد.

---

## ۵) نکات جانبی کشف‌شده (خارج از چک‌لیست اصلی ولی مهم برای فازهای بعد)

- **مسیر import مشکوک:** بلاک `bootstrapSQLitePhase2Step1` (خط ۶۲۹۳) با `import('../src/db/migrateFromIndexedDB.js')` نسبت به مکان خودِ `html8.html` مسیر می‌دهد. در این zip، `html8.html` در ریشه است ولی `src/db/` زیر `rasticpack-capacitor/src/db/` قرار دارد — یعنی مسیر نسبی `../src/db/...` با ساختار فعلی zip مطابقت ندارد و فقط در چیدمان واقعی پروژه‌ی Capacitor (که کامنت خودِ کد هم به آن اشاره می‌کند: «www/» یک پوشه پایین‌تر) درست کار می‌کند. این را باید در فاز ۱ روی چیدمان واقعی پروژه (نه این zip) تأیید کرد.

---

## خروجی فاز ۰

- [x] لیست خط‌شماره‌دار `invoices.*` / `customers.*`
- [x] لیست idb* با ستون نوشتن‌موازی/خواندن‌مستقل + **تصحیح رقم ۳۸→۲۳**
- [x] اجرای کامل تست‌ها روی وضعیت فعلی — همه سبز
- [ ] اسکریپت شمارش‌تطبیقی IndexedDB↔SQLite — در `verify-migration-counts.mjs` ساخته شد (فایل جدا، چون باید داخل محیط اپ/مرورگر اجرا شود، نه Node خالص)

هیچ کدی در `html8.html` یا `rasticpack-capacitor/` در فاز ۰ تغییر نکرد (این فایل فقط مستندسازی خط پایه بود).

---

## به‌روزرسانی نهایی (بعد از فاز ۵)

بخش ۳ این چک‌لیست (فراخوانی‌های `idb*`) که در فاز ۰ فقط «مستندسازی» بود، در فاز ۵ به‌طور کامل
عملیاتی شد: هر ۲۴ فراخوانی واقعی حذف و لایه‌ی IndexedDB از کد پاک شد — نگاه کن به بخش ۳ بالا
(ستون «جایگزین بعد از فاز ۵») و `sqlite-migration-roadmap.md` برای جزئیات کامل. بخش‌های ۱ و ۲
(نقاط `invoices[]`/`customers[]`) هم در فازهای ۲ و ۳ به همین شکل عملیاتی شدند.
