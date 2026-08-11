# چک‌لیست فاز ۰ — خط پایه (Baseline)

> تولید شده در تاریخ تحلیل روی `html8.html` (۷۱۵۵ خط) داخل `html34.zip`.
> این فایل فقط مستندسازی است — هیچ کدی تغییر نکرده.
>
> **به‌روزرسانی (فاز ۲ / گروه الف):** دو ردیف اول جدول زیر (۴۲۰۴ و ۴۲۴۲-۴۲۴۳ سابق) پیاده‌سازی و
> از این چک‌لیست حذف/خط‌خورده شدند — جزئیات در `sqlite-migration-roadmap.md`، بخش فاز ۲.

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

| خط | کد | وضعیت |
|---|---|---|
| 758 | `getCustomerById = id => customers.find(...)` | فقط آرایه — **پرکاربردترین تابع**، بدون fallback |
| 759 | `findCustomerByName = name => customers.find(...)` | فقط آرایه — بدون fallback |
| 1231-1233 | `if(!customers.length){...customers.push(sampleCust);}` | فقط آرایه — seed نمونه، ریسک پایین |
| 3309 | `customers.find(c=>c.name...===name...)` | فقط آرایه — چک یکتایی نام قبل از افزودن |
| 3315 | `customers.push(newCust);` | فقط آرایه — افزودن مشتری جدید |
| 3325 | `customers=customers.filter(c=>c.id!==id);` | فقط آرایه — حذف مشتری |
| 3362 | `if(!customers.length)` | فقط آرایه — چک خالی قبل از رندر لیست |
| 3366 | `customers.filter(...)` / بدون q: خودِ `customers` | **دارد fallback به Repo** — بخشی از `renderCustomersList`، جلوترش `fetchCustomersPage` را امتحان می‌کند |
| 3557 | `customers.find(x=>x.id===id)` | فقط آرایه — شروع ویرایش مشتری |
| 3560 | `customers.find(x=>x.id!==id&&x.name...)` | فقط آرایه — چک یکتایی نام هنگام ویرایش |
| 3580 | `customers.map(c=>...)` | فقط آرایه — پر کردن datalist خودتکمیلی |
| 6107 | `customers.reduce((x,i)=>Math.max(x,i.id),0)` | فقط آرایه — محاسبه‌ی `nextCustId` بعد از restore (فاز ۴) |
| 6127 | `customers.length` | فقط آرایه — فقط پیام موفقیت، بی‌خطر |

**جمع‌بندی:** ۱ مورد fallback‌دار، ~۱۱ مورد فقط-آرایه — کمی بیشتر از برآورد «~۱۰» نقشه‌راه اما در همان محدوده.

---

## ۳) فراخوانی‌های `idb*` — ⚠️ یافته‌ی مهم فاز ۰

نقشه‌راه رقم **۳۸ فراخوانی** را ذکر کرده بود. شمارش دقیق این بررسی (با حذف ۶ تعریف تابع در خطوط ۸۷۸-۹۴۸ و ۲ اشاره‌ی صرفاً متنی داخل کامنت در خطوط ۹۵۱ و ۱۱۶۸ که هیچ‌کدام فراخوانی واقعی نیستند) عدد **۲۳ فراخوانی واقعی** را نشان می‌دهد. این مغایرت باید همین‌جا ثبت شود تا فازهای بعدی (به‌خصوص فاز ۵) روی رقم درست کار کنند؛ شاید نسخه‌ای که رقم ۳۸ از آن گرفته شده جدیدتر/قدیمی‌تر از این zip بوده.

| خط | فراخوانی | نوع | نوشتن موازی یا خواندن مستقل؟ |
|---|---|---|---|
| 1004 | `idbPut('invoices',inv)` | نوشتن | **موازی** — داخل `dbSaveInvoice` (کنار `InvoiceRepo.save`) |
| 1011 | `idbDelete('invoices',id)` | حذف | **موازی** — داخل `dbDeleteInvoiceRecord` (کنار `InvoiceRepo.remove`) |
| 1019 | `idbPut('customers',cust)` | نوشتن | **موازی** — داخل `dbSaveCustomer` |
| 1025 | `idbDelete('customers',id)` | حذف | **موازی** — داخل `dbDeleteCustomerRecord` |
| 1057 | `idbPut('invoices',inv)` | نوشتن | **موازی** — داخل `dbSaveInvoiceWithStock` (کنار `insertWithStockUpdate`) |
| 1071 | `idbDelete('invoices',id)` | حذف | **موازی** — داخل `dbDeleteInvoiceRecordWithStock` |
| 1176 | `idbGetAll('customers')` | خواندن | **مستقل** — داخل `loadState`، منبع اصلی startup |
| 1178 | `idbBulkPut('customers',legacyCustomers)` | نوشتن گروهی | **مستقل** (one-time seed از `window.storage` قدیمی، نه موازی با SQLite) |
| 1183 | `idbGetAllLimited('invoices',STARTUP_PAGE_SIZE)` | خواندن | **مستقل** — صفحه‌ی اول startup |
| 1185 | `idbBulkPut('invoices',legacyInvoices)` | نوشتن گروهی | **مستقل** (one-time seed مشابه بالا) |
| 1218 | `idbGetAll('invoices').then(...)` | خواندن | **مستقل** — بارگذاری کامل پس‌زمینه (`reloadInvoicesLazy`) |
| 6018 ×۲ | `idbClear('invoices')` / `idbClear('customers')` | حذف کامل | **مستقل** — شروع `doRestore` (JSON legacy) |
| 6032 | `idbBulkPut('customers',batch)` | نوشتن گروهی | **مستقل** — `doRestore` |
| 6040 | `idbBulkPut('invoices',normalized)` | نوشتن گروهی | **مستقل** — `doRestore` |
| 6070 | `customers=await idbGetAll('customers')` | خواندن | **مستقل** — بازخوانی بعد از `doRestore` |
| 6071 | `idbGetAllLimited('invoices',STARTUP_PAGE_SIZE)` | خواندن | **مستقل** — بازخوانی بعد از `doRestore` |
| 6112 ×۲ | `idbClear('invoices')` / `idbClear('customers')` | حذف کامل | **مستقل** — `doRestoreNdjson` یا مسیر مشابه |
| 6113 ×۲ | `idbBulkPut('invoices',invoices)` / `idbBulkPut('customers',customers)` | نوشتن گروهی | **مستقل** |
| 6137 ×۲ | `idbClear('invoices')` / `idbClear('customers')` | حذف کامل | **مستقل** — `clearAllData` |

توجه: هیچ فراخوانی idb برای store «inventory» پیدا نشد — مطابق هشدار خودِ کد (خط ۱۰۴۵) که می‌گوید جدول SQL inventory اصلاً seed نمی‌شود.

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

هیچ کدی در `html8.html` یا `rasticpack-capacitor/` تغییر نکرد.
