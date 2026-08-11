# چک‌لیست دقیق و خط‌به‌خط رخدادهای `invoices[]` / `customers[]` در `html8.html`

این فایل خروجی **فاز ۷** نقشه‌راه ۲ (`sqlite-migration-roadmap.md`) است — پیش‌نیاز بدون تغییر کد،
پیش از شروع حذف واقعی fallbackها (فاز ۸ به بعد). هر رخداد شناسه‌ی `invoices` یا `customers`
در `html8.html` با شماره‌خط ثبت شده — مجموع ۸۰ رخداد `invoices` + ۴۴ رخداد `customers` = ۱۲۴،
دقیقاً برابر با شمارش خودکار `grep -o` (تأییدشده توسط اسکریپت `memory-array-audit.mjs`).

> **یادداشت بعد از فاز ۸:** این جدول عمداً به‌عنوان *baseline فاز ۷* (پیش از هر حذفی) دست‌نخورده
> نگه داشته شده — برای این‌که بشود «قبل/بعد» را مقایسه کرد. ردیف‌های خط ۳۷۸۴ (`computeStatsDataFallback`)،
> ۳۹۹۸ (`renderStatsBarChart`)، ۴۰۳۱ (`computeStatsLeadersFallback`)، ۴۷۷۱ (`baseList` — فقط مصرف
> «fallback شمارش وضعیت»‌اش)، و ۶۰۳۸ (`doBackupLegacyFromMemory`، هر دو ستون `invoices`/`customers`)
> در فاز ۸ برطرف شدند؛ جزئیات دقیق هر کدام در `sqlite-migration-roadmap.md` (فاز ۸، تیک‌خورده) ثبت
> شده، نه اینجا. شماره‌خط‌های بقیه‌ی جدول ممکن است بعد از فاز ۸ کمی جابه‌جا شده باشند (چون کد حذف شد)؛
> برای شماره‌خط دقیق فعلی، `grep -n` تازه بزن.

## دسته‌بندی‌ها

| کد | دسته | توضیح |
|----|------|-------|
| A | نوشتن‌آینه‌ای بعد از SQLite | نوشتن محلی روی آرایه بلافاصله کنار/بعد از نوشتن واقعی در Repo (SQLite)، فقط برای نمایش فوری |
| B | fallback ناامن به داده‌ی احتمالاً قدیمی/ناقص | مسیری که در نبود/خطای SQLite (یا در کد legacy) مستقیم از آرایه‌ی حافظه می‌خواند/می‌نویسد؛ آرایه ممکن است لِیزی/ناقص/قدیمی باشد |
| C | کش لِیزی که از SQLite پر می‌شود | آرایه به‌عنوان یک کش خواندن استفاده می‌شود که خودش کامل از SQLite (مستقیم یا صفحه‌بندی‌شده) پر شده؛ نه یک کپی مستقل از منبع حقیقت |
| D | متغیر/کلید محلی بی‌ربط با همین اسم | پارامتر تابع، کلید JSON ورودی/خروجی، یا هر شناسه‌ی دیگری که فقط هم‌نام آرایه‌ی سراسری است ولی به آن اشاره ندارد |
| E | رشته‌ی بی‌ربط (شناسه‌ی تب/DOM/جدول) | لیترال رشته‌ای مثل نام تب، id عنصر HTML، یا نام جدول/store — نه خودِ آرایه |
| F | کامنت/مستندات | فقط در کامنت — بدون هیچ اثر روی رفتار زمان‌اجرا |
| G | تعریف/بازنشانی متغیر سراسری | خط `let`/بازنشانی که خودِ متغیر سراسری را تعریف یا صفر می‌کند |

## جدول کامل (114 ردیف، مجموع 124 رخداد)

| خط | شناسه | تعداد در این خط | تابع/بخش | دسته | توضیح |
|----|-------|------------------|----------|------|-------|
| 24 | `invoices` | 1 | HTML — دکمه‌ی تب | E — رشته‌ی بی‌ربط (شناسه‌ی تب/DOM/جدول) | رشته‌ی نام تب در onclick="switchTab('invoices',...)" |
| 25 | `customers` | 1 | HTML — دکمه‌ی تب | E — رشته‌ی بی‌ربط (شناسه‌ی تب/DOM/جدول) | رشته‌ی نام تب در onclick="switchTab('customers',...)" |
| 71 | `invoices` | 1 | HTML — <section id="panel-invoices"> | E — رشته‌ی بی‌ربط (شناسه‌ی تب/DOM/جدول) | شناسه‌ی DOM پنل |
| 104 | `invoices` | 1 | HTML — <div id="invoices-list"> | E — رشته‌ی بی‌ربط (شناسه‌ی تب/DOM/جدول) | شناسه‌ی DOM لیست |
| 156 | `customers` | 1 | HTML — <section id="panel-customers"> | E — رشته‌ی بی‌ربط (شناسه‌ی تب/DOM/جدول) | شناسه‌ی DOM پنل |
| 605 | `customers` | 1 | تعریف STATE سراسری | G — تعریف/بازنشانی متغیر سراسری (state) | let ..., customers=[], ... |
| 605 | `invoices` | 1 | تعریف STATE سراسری | G — تعریف/بازنشانی متغیر سراسری (state) | let ..., invoices=[], ... |
| 759 | `customers` | 1 | کامنت بالای بخش customers | F — کامنت/مستندات (بدون اثر روی رفتار کد) |  |
| 1008 | `invoices` | 1 | کامنت بالای resolveInvoiceById | F — کامنت/مستندات (بدون اثر روی رفتار کد) |  |
| 1010 | `invoices` | 1 | کامنت بالای resolveInvoiceById | F — کامنت/مستندات (بدون اثر روی رفتار کد) |  |
| 1020 | `invoices` | 1 | کامنت بالای resolveInvoiceById | F — کامنت/مستندات (بدون اثر روی رفتار کد) |  |
| 1023 | `invoices` | 1 | resolveInvoiceById | C — کش لِیزی که از SQLite پر می‌شود | مرحله‌ی اول زنجیره: خواندن از آرایه‌ی حافظه (که خودش از SQLite پر می‌شود) |
| 1034 | `invoices` | 1 | resolveInvoiceById | C — کش لِیزی که از SQLite پر می‌شود | push رکورد تازه‌کوئری‌شده به آرایه، برای کش کلیک‌های بعدی |
| 1061 | `customers` | 1 | کامنت — حذف idbGetAll در فاز قبلی | F — کامنت/مستندات (بدون اثر روی رفتار کد) |  |
| 1099 | `customers` | 1 | loadState — destructure از window.storage قدیمی | B — fallback ناامن به داده‌ی احتمالاً قدیمی | همان — customers:cu |
| 1099 | `invoices` | 1 | loadState — destructure از window.storage قدیمی | B — fallback ناامن به داده‌ی احتمالاً قدیمی | دادهٔ legacy پیش از SQLite؛ فقط برای مسیر مهاجرت یک‌باره خوانده می‌شود |
| 1115 | `customers` | 1 | کامنت | F — کامنت/مستندات (بدون اثر روی رفتار کد) |  |
| 1115 | `invoices` | 1 | کامنت | F — کامنت/مستندات (بدون اثر روی رفتار کد) |  |
| 1138 | `customers` | 1 | loadState | C — کش لِیزی که از SQLite پر می‌شود | customers=repoCustomers — پر شدن از fetchAllCustomersForStartup (SQLite) |
| 1152 | `customers` | 1 | loadState — catch(SQLite ناموفق) | B — fallback ناامن به داده‌ی احتمالاً قدیمی | fallback صریح به legacyCustomers |
| 1152 | `invoices` | 1 | loadState — catch(SQLite ناموفق) | B — fallback ناامن به داده‌ی احتمالاً قدیمی | fallback صریح به legacyInvoices وقتی SQLite در دسترس نیست |
| 1163 | `invoices` | 1 | کامنت بالای reloadInvoicesLazy | F — کامنت/مستندات (بدون اثر روی رفتار کد) |  |
| 1165 | `invoices` | 1 | کامنت بالای reloadInvoicesLazy | F — کامنت/مستندات (بدون اثر روی رفتار کد) |  |
| 1170 | `invoices` | 1 | reloadInvoicesLazy | C — کش لِیزی که از SQLite پر می‌شود | invoices=firstPage.map(...) — صفحه‌ی اول از SQLite |
| 1175 | `invoices` | 1 | reloadInvoicesLazy | C — کش لِیزی که از SQLite پر می‌شود | پر کردن InvoiceCache از همان آرایه‌ی لِیزی |
| 1180 | `invoices` | 1 | reloadInvoicesLazy (پس‌زمینه) | C — کش لِیزی که از SQLite پر می‌شود | invoices=all.map(...) — کل داده از SQLite (forEachBatch) |
| 1182 | `invoices` | 1 | reloadInvoicesLazy (پس‌زمینه) | C — کش لِیزی که از SQLite پر می‌شود | setMany روی بخش پایانی آرایه‌ی کامل‌شده |
| 1190 | `invoices` | 1 | کامنت | F — کامنت/مستندات (بدون اثر روی رفتار کد) |  |
| 1207 | `customers` | 1 | seedSampleDataIfEmpty | C — کش لِیزی که از SQLite پر می‌شود | چک طول آرایه‌ی کامل (نه لِیزی) که در loadState از CustomerRepo پر شده |
| 1209 | `customers` | 1 | seedSampleDataIfEmpty | A — نوشتن‌آینه‌ای بعد از SQLite | push محلی بلافاصله قبل از dbSaveCustomer |
| 1272 | `customers` | 1 | کامنت | F — کامنت/مستندات (بدون اثر روی رفتار کد) |  |
| 1272 | `invoices` | 1 | کامنت | F — کامنت/مستندات (بدون اثر روی رفتار کد) |  |
| 1510 | `invoices` | 1 | switchTab | E — رشته‌ی بی‌ربط (شناسه‌ی تب/DOM/جدول) | if(name==='invoices') — مقایسه‌ی رشته‌ی نام تب |
| 1512 | `customers` | 1 | switchTab | E — رشته‌ی بی‌ربط (شناسه‌ی تب/DOM/جدول) | if(name==='customers') — مقایسه‌ی رشته‌ی نام تب |
| 2434 | `invoices` | 1 | ثبت فاکتور Calc2 | A — نوشتن‌آینه‌ای بعد از SQLite | push محلی بلافاصله قبل از dbSaveInvoiceWithStock |
| 2442 | `invoices` | 1 | ثبت فاکتور Calc2 | E — رشته‌ی بی‌ربط (شناسه‌ی تب/DOM/جدول) | سلکتور DOM با رشته‌ی onclick شامل نام تب |
| 2443 | `invoices` | 1 | ثبت فاکتور Calc2 | E — رشته‌ی بی‌ربط (شناسه‌ی تب/DOM/جدول) | switchTab('invoices',invBtn) |
| 3282 | `customers` | 1 | کامنت بالای addCustomer | F — کامنت/مستندات (بدون اثر روی رفتار کد) |  |
| 3298 | `customers` | 1 | addCustomer | A — نوشتن‌آینه‌ای بعد از SQLite | push محلی بلافاصله قبل از dbSaveCustomer |
| 3306 | `customers` | 2 | کامنت بالای removeCustomer | F — کامنت/مستندات (بدون اثر روی رفتار کد) |  |
| 3313 | `customers` | 1 | کامنت بالای removeCustomer | F — کامنت/مستندات (بدون اثر روی رفتار کد) |  |
| 3318 | `customers` | 1 | removeCustomer | C — کش لِیزی که از SQLite پر می‌شود | findIndex فقط برای پاک‌سازی کش خواندنِ محلی، بعد از await حذف واقعی در Repo |
| 3319 | `customers` | 1 | removeCustomer | C — کش لِیزی که از SQLite پر می‌شود | splice — پاک‌سازی همان کش |
| 3359 | `customers` | 1 | renderCustomersList | C — کش لِیزی که از SQLite پر می‌شود | چک خالی‌بودن آرایه‌ی کامل (پر شده در loadState از CustomerRepo) |
| 3417 | `invoices` | 1 | renderCustomerViewCard | B — fallback ناامن به داده‌ی احتمالاً قدیمی | fallback sync وقتی invoiceCount از renderCustomersList پاس داده نشده |
| 3559 | `invoices` | 1 | renderCustInvoicesModal | B — fallback ناامن به داده‌ی احتمالاً قدیمی | fullList از فیلتر آرایه‌ی حافظه ساخته می‌شود، نه کوئری مستقیم |
| 3578 | `customers` | 1 | saveCustomerEdit | C — کش لِیزی که از SQLite پر می‌شود | find رکورد برای ویرایش از آرایه‌ی کامل |
| 3581 | `customers` | 1 | saveCustomerEdit | B — fallback ناامن به داده‌ی احتمالاً قدیمی | چک تکراری‌بودن نام هنوز روی آرایه‌ی حافظه (بر خلاف addCustomer که به findCustomerByName منتقل شد) |
| 3597 | `invoices` | 1 | saveCustomerEdit | A — نوشتن‌آینه‌ای بعد از SQLite | فیلتر و به‌روزرسانی محلی customerName، سپس UPDATE گروهی در SQLite |
| 3613 | `customers` | 1 | کامنت بالای renderCustomerDatalist | F — کامنت/مستندات (بدون اثر روی رفتار کد) |  |
| 3778 | `invoices` | 1 | کامنت بالای computeStatsDataFallback | F — کامنت/مستندات (بدون اثر روی رفتار کد) |  |
| 3784 | `invoices` | 1 | computeStatsDataFallback | B — fallback ناامن به داده‌ی احتمالاً قدیمی | fallback نهایی زنجیره‌ی آمار — حلقه‌ی JS روی کل آرایه |
| 3800 | `invoices` | 1 | کامنت بالای STATS_WORKER_SRC | F — کامنت/مستندات (بدون اثر روی رفتار کد) |  |
| 3805 | `invoices` | 1 | کامنت بالای STATS_WORKER_SRC | F — کامنت/مستندات (بدون اثر روی رفتار کد) |  |
| 3809 | `invoices` | 1 | کامنت بالای STATS_WORKER_SRC | F — کامنت/مستندات (بدون اثر روی رفتار کد) |  |
| 3819 | `invoices` | 2 | STATS_WORKER_SRC (کد داخل Web Worker) | D — متغیر/کلید محلی بی‌ربط با همین اسم | پارامتر محلی self.onmessage، از postMessage می‌آید نه آرایه‌ی سراسری صفحه |
| 3826 | `invoices` | 1 | STATS_WORKER_SRC | D — متغیر/کلید محلی بی‌ربط با همین اسم | همان پارامتر محلی Worker |
| 3827 | `invoices` | 1 | STATS_WORKER_SRC | D — متغیر/کلید محلی بی‌ربط با همین اسم | همان پارامتر محلی Worker |
| 3846 | `invoices` | 1 | STATS_WORKER_SRC | D — متغیر/کلید محلی بی‌ربط با همین اسم | همان پارامتر محلی Worker |
| 3847 | `invoices` | 1 | STATS_WORKER_SRC | D — متغیر/کلید محلی بی‌ربط با همین اسم | همان پارامتر محلی Worker |
| 3942 | `invoices` | 1 | computeStatsDataViaWorker | B — fallback ناامن به داده‌ی احتمالاً قدیمی | ارسال کل آرایه‌ی حافظه به Worker به‌عنوان لایه‌ی دوم fallback |
| 3945 | `invoices` | 1 | computeStatsLeadersViaWorker | B — fallback ناامن به داده‌ی احتمالاً قدیمی | همان — ارسال آرایه به Worker |
| 3998 | `invoices` | 1 | renderStatsBarChart | B — fallback ناامن به داده‌ی احتمالاً قدیمی | چک خالی‌بودن روی آرایه‌ی حافظه برای تصمیم رسم نمودار |
| 4024 | `invoices` | 1 | کامنت بالای computeStatsLeadersFallback | F — کامنت/مستندات (بدون اثر روی رفتار کد) |  |
| 4031 | `invoices` | 1 | computeStatsLeadersFallback | B — fallback ناامن به داده‌ی احتمالاً قدیمی | fallback نهایی زنجیره‌ی آمار — حلقه‌ی JS |
| 4246 | `invoices` | 1 | کامنت بالای fetchInvMonthList | F — کامنت/مستندات (بدون اثر روی رفتار کد) |  |
| 4250 | `invoices` | 1 | کامنت بالای fetchInvMonthList | F — کامنت/مستندات (بدون اثر روی رفتار کد) |  |
| 4267 | `invoices` | 3 | buildInvMonthList | C — کش لِیزی که از SQLite پر می‌شود | ساخت امضای کش تغییر از طول/آخرین‌آیتمِ آرایه‌ی لِیزی |
| 4290 | `invoices` | 1 | کامنت بالای fetchInvMonthCount | F — کامنت/مستندات (بدون اثر روی رفتار کد) |  |
| 4513 | `invoices` | 2 | کامنت بالای بخش fetchInv* (فاز ۲ گروه ب) | F — کامنت/مستندات (بدون اثر روی رفتار کد) |  |
| 4514 | `invoices` | 1 | کامنت بالای بخش fetchInv* (فاز ۲ گروه ب) | F — کامنت/مستندات (بدون اثر روی رفتار کد) |  |
| 4771 | `invoices` | 1 | renderInvoicesList | B — fallback ناامن به داده‌ی احتمالاً قدیمی | baseList=invoices.slice() — هدف صریح فاز ۸ برای حذف (fallback شمارش وضعیت) |
| 4789 | `invoices` | 1 | renderInvoicesList | E — رشته‌ی بی‌ربط (شناسه‌ی تب/DOM/جدول) | $('invoices-list') — شناسه‌ی DOM |
| 4830 | `invoices` | 1 | window.__onSQLiteReady | E — رشته‌ی بی‌ربط (شناسه‌ی تب/DOM/جدول) | $('panel-invoices') — شناسه‌ی DOM |
| 5082 | `customers` | 1 | کامنت بالای renderInvoiceViewCard | F — کامنت/مستندات (بدون اثر روی رفتار کد) |  |
| 5361 | `invoices` | 2 | کامنت بالای deleteInvoice | F — کامنت/مستندات (بدون اثر روی رفتار کد) |  |
| 5370 | `invoices` | 1 | کامنت بالای deleteInvoice | F — کامنت/مستندات (بدون اثر روی رفتار کد) |  |
| 5375 | `invoices` | 1 | deleteInvoice | C — کش لِیزی که از SQLite پر می‌شود | findIndex فقط برای پاک‌سازی کش، بعد از await حذف واقعی در Repo |
| 5376 | `invoices` | 1 | deleteInvoice | C — کش لِیزی که از SQLite پر می‌شود | splice — پاک‌سازی همان کش |
| 5896 | `customers` | 1 | کامنت بالای بخش بکاپ/ریستور | F — کامنت/مستندات (بدون اثر روی رفتار کد) |  |
| 5896 | `invoices` | 1 | کامنت بالای بخش بکاپ/ریستور | F — کامنت/مستندات (بدون اثر روی رفتار کد) |  |
| 5898 | `invoices` | 1 | کامنت بالای بخش بکاپ/ریستور | F — کامنت/مستندات (بدون اثر روی رفتار کد) |  |
| 5938 | `customers` | 1 | repoForEachBatch | E — رشته‌ی بی‌ربط (شناسه‌ی تب/DOM/جدول) | storeName==='customers' — مقایسه‌ی رشته |
| 5938 | `invoices` | 1 | repoForEachBatch | E — رشته‌ی بی‌ربط (شناسه‌ی تب/DOM/جدول) | storeName==='invoices' — مقایسه‌ی رشته |
| 5965 | `customers` | 1 | buildBackupParts | E — رشته‌ی بی‌ربط (شناسه‌ی تب/DOM/جدول) | readBatches('customers', ...) — رشته‌ی نام جدول |
| 5969 | `invoices` | 1 | buildBackupParts | E — رشته‌ی بی‌ربط (شناسه‌ی تب/DOM/جدول) | readBatches('invoices', ...) — رشته‌ی نام جدول |
| 6038 | `customers` | 1 | doBackupLegacyFromMemory (مسیر legacy، هدف حذف فاز ۸) | B — fallback ناامن به داده‌ی احتمالاً قدیمی | همان |
| 6038 | `invoices` | 1 | doBackupLegacyFromMemory (مسیر legacy، هدف حذف فاز ۸) | B — fallback ناامن به داده‌ی احتمالاً قدیمی | JSON.stringify مستقیم آرایه‌ی حافظه |
| 6150 | `invoices` | 1 | کامنت بالای doRestoreNdjson | F — کامنت/مستندات (بدون اثر روی رفتار کد) |  |
| 6169 | `customers` | 1 | کامنت داخل doRestoreNdjson | F — کامنت/مستندات (بدون اثر روی رفتار کد) |  |
| 6169 | `invoices` | 1 | کامنت داخل doRestoreNdjson | F — کامنت/مستندات (بدون اثر روی رفتار کد) |  |
| 6210 | `customers` | 1 | کامنت داخل doRestoreNdjson | F — کامنت/مستندات (بدون اثر روی رفتار کد) |  |
| 6211 | `invoices` | 1 | کامنت داخل doRestoreNdjson | F — کامنت/مستندات (بدون اثر روی رفتار کد) |  |
| 6213 | `customers` | 1 | doRestoreNdjson | C — کش لِیزی که از SQLite پر می‌شود | customers=await fetchAllCustomersForStartup() — پر شدن کامل از SQLite بعد از ریستور |
| 6228 | `customers` | 1 | doRestoreLegacyJson — اعتبارسنجی ورودی | D — متغیر/کلید محلی بی‌ربط با همین اسم | data.customers — همان |
| 6228 | `invoices` | 1 | doRestoreLegacyJson — اعتبارسنجی ورودی | D — متغیر/کلید محلی بی‌ربط با همین اسم | data.invoices — کلید JSON فایل بکاپ ورودی، نه آرایه‌ی سراسری |
| 6235 | `customers` | 3 | doRestoreLegacyJson (مسیر legacy، هدف تبدیل فاز ۱۰) | B — fallback ناامن به داده‌ی احتمالاً قدیمی | customers=Array.isArray(data.customers)?data.customers:[] — کل فایل در آرایه‌ی سراسری |
| 6236 | `invoices` | 3 | doRestoreLegacyJson (مسیر legacy، هدف تبدیل فاز ۱۰) | B — fallback ناامن به داده‌ی احتمالاً قدیمی | invoices=Array.isArray(data.invoices)?data.invoices.map(...):[] — کل فایل در آرایه‌ی سراسری |
| 6250 | `customers` | 1 | doRestoreLegacyJson | B — fallback ناامن به داده‌ی احتمالاً قدیمی | nextCustId از customers.reduce — وابسته به همان آرایه‌ی legacy |
| 6251 | `invoices` | 1 | doRestoreLegacyJson | B — fallback ناامن به داده‌ی احتمالاً قدیمی | nextInvoiceId از invoices.reduce — همان |
| 6254 | `customers` | 1 | کامنت داخل doRestoreLegacyJson | F — کامنت/مستندات (بدون اثر روی رفتار کد) |  |
| 6255 | `invoices` | 1 | کامنت داخل doRestoreLegacyJson | F — کامنت/مستندات (بدون اثر روی رفتار کد) |  |
| 6261 | `customers` | 1 | doRestoreLegacyJson | B — fallback ناامن به داده‌ی احتمالاً قدیمی | bulkInsert(customers) — نوشتن از آرایه‌ی legacy در SQLite |
| 6262 | `invoices` | 1 | doRestoreLegacyJson | B — fallback ناامن به داده‌ی احتمالاً قدیمی | bulkInsert(invoices) — همان |
| 6271 | `customers` | 1 | doRestoreLegacyJson — پیام موفقیت | B — fallback ناامن به داده‌ی احتمالاً قدیمی | customers.length — همان |
| 6271 | `invoices` | 1 | doRestoreLegacyJson — پیام موفقیت | B — fallback ناامن به داده‌ی احتمالاً قدیمی | invoices.length در پیام، وابسته به همان آرایه‌ی legacy |
| 6278 | `customers` | 1 | clearAllData | G — تعریف/بازنشانی متغیر سراسری (state) | customers=[] — همان |
| 6278 | `invoices` | 1 | clearAllData | G — تعریف/بازنشانی متغیر سراسری (state) | invoices=[] — هدف تغییر فاز ۱۰ (باید از این خط حذف شود) |
| 6282 | `customers` | 1 | کامنت داخل clearAllData | F — کامنت/مستندات (بدون اثر روی رفتار کد) |  |
| 6282 | `invoices` | 1 | کامنت داخل clearAllData | F — کامنت/مستندات (بدون اثر روی رفتار کد) |  |
| 6299 | `customers` | 1 | سوایپ تب‌ها — tabOrder | E — رشته‌ی بی‌ربط (شناسه‌ی تب/DOM/جدول) | رشته‌ی 'customers' در آرایه‌ی نام تب‌ها |
| 6299 | `invoices` | 1 | سوایپ تب‌ها — tabOrder | E — رشته‌ی بی‌ربط (شناسه‌ی تب/DOM/جدول) | رشته‌ی 'invoices' در آرایه‌ی نام تب‌ها |
| 6423 | `customers` | 1 | کامنت بلاک bootstrap SQLite | F — کامنت/مستندات (بدون اثر روی رفتار کد) |  |
| 6423 | `invoices` | 1 | کامنت بلاک bootstrap SQLite | F — کامنت/مستندات (بدون اثر روی رفتار کد) |  |

## جمع‌بندی به تفکیک دسته

| دسته | تعداد رخداد (invoices + customers) |
|------|-------------------------------------|
| A — نوشتن‌آینه‌ای بعد از SQLite | 4 |
| B — fallback ناامن به داده‌ی احتمالاً قدیمی | 27 |
| C — کش لِیزی که از SQLite پر می‌شود | 18 |
| D — متغیر/کلید محلی بی‌ربط با همین اسم | 8 |
| E — رشته‌ی بی‌ربط (شناسه‌ی تب/DOM/جدول) | 17 |
| F — کامنت/مستندات (بدون اثر روی رفتار کد) | 46 |
| G — تعریف/بازنشانی متغیر سراسری (state) | 4 |

## نکات مهم برای فازهای بعدی

- **دسته B (فعلاً ۲۹ رخداد)** هدف اصلی فازهای ۸–۱۰ است — این‌ها جایی هستند که کد صراحتاً به آرایه‌ی
  حافظه (که می‌تواند لِیزی/قدیمی/ناقص باشد) سقوط می‌کند، به‌جای خطای واضح یا کوئری مستقیم.
- **دسته C (۱۴ رخداد)** طبق تعریف نقشه‌راه، این‌ها *باقی می‌مانند* — کش لِیزی که از SQLite پر می‌شود
  (مثل `InvoiceCache`) هدف حذف این نقشه‌راه نیست؛ فقط باید مطمئن شویم واقعاً «کش» هستند نه «fallback».
- **دسته A (۵ رخداد)** طبق فاز ۹ حذف می‌شوند: بعد از نوشتن موفق در Repo، فقط UI دوباره از Repo رندر شود.
- **دسته D/E/F/G** خارج از دامنه‌ی حذف‌اند (رشته/کامنت/پارامتر بی‌ربط یا خودِ تعریف متغیر) — اما G
  (خط ۶۰۵ و ۶۲۷۸) دقیقاً همان دو نقطه‌ای‌اند که فاز ۱۰ و ۱۱ باید تغییرشان بدهند.
- یافته‌ی جدید غیرمنتظره حین این ممیزی: **خط ۳۵۸۱ (`saveCustomerEdit`)** هنوز چک تکراری‌بودن نام را
  مستقیم روی آرایه‌ی حافظه (`customers.find`) انجام می‌دهد، برخلاف `addCustomer` که در فاز ۳ به
  `findCustomerByName` (پل به `CustomerRepo.findByExactName`) منتقل شد. این یک ناسازگاری واقعی است که
  در فاز ۸ یا ۹ باید یا اصلاح شود یا صراحتاً در ریسک‌های نقشه‌راه ثبت شود.
