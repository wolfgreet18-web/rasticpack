/**
 * sqlite-entry.js — نقطه‌ی ورود باندل SQLite برای WebView.
 *
 * چرا این فایل لازم شد (رفع باگ):
 * قبلاً index.html مستقیم داخل یک <script type="module"> با import() پویا و
 * specifier غیرمسیری ('@capacitor-community/sqlite') این پلاگین را لود
 * می‌کرد. این کار در Node.js/باندلرها کار می‌کند، ولی در یک WebView خام
 * (بدون هیچ باندلر یا import map) اصلاً قابل resolve نیست — مرورگر معنی
 * "@capacitor-community/sqlite" را به‌عنوان یک آدرس نمی‌فهمد، پس import
 * همیشه throw می‌کرد، بی‌صدا در catch قورت می‌شد، و window.__CustomerRepo/
 * __InvoiceRepo هیچ‌وقت ست نمی‌شدند — یعنی کل اپ همیشه از fallback حافظه
 * استفاده می‌کرد، نه واقعاً از SQLite.
 *
 * راه‌حل: این فایل با import استاتیک (نه پویا) نوشته شده تا esbuild موقع
 * build (که در package.json اضافه شده) بتواند '@capacitor-community/sqlite'
 * را از node_modules واقعاً resolve و در یک فایل خروجی واحد باندل کند.
 * نتیجه در www/vendor/sqlite-bundle.js نوشته می‌شود و index.html آن را با
 * یک <script> معمولی (نه type="module"، نه import پویا) لود می‌کند.
 */
import { migrate } from './db/migrateFromIndexedDB.js';
import { InvoiceRepo } from './db/InvoiceRepo.js';
import { CustomerRepo } from './db/CustomerRepo.js';

(async function bootstrapSQLite(){
  const TAG='[SQLite bootstrap]';
  try{
    window.__InvoiceRepo = InvoiceRepo;
    window.__CustomerRepo = CustomerRepo;

    console.log(TAG,'شروع مهاجرت (در صورت نیاز)...');
    const result = await migrate();
    window.__sqliteMigrationStatus = result;

    if(result.status==='skipped'){
      console.log('%c'+TAG+' مهاجرت قبلاً انجام شده — کاری انجام نشد.','color:#888',result);
    }else if(result.status==='ok'){
      console.log('%c'+TAG+' ✅ مهاجرت با موفقیت انجام شد.','color:green;font-weight:bold',result);
      if(result.skippedInvalidCustomers||result.skippedInvalidInvoices){
        console.warn(TAG,`⚠️ ${result.skippedInvalidCustomers||0} مشتری و ${result.skippedInvalidInvoices||0} فاکتور به‌خاطر فیلد الزامی گمشده رد شدند (migrate شدند ولی از منبع اصلی حذف نشدند — قابل بررسی دستی).`);
      }
    }else{
      console.error('%c'+TAG+' ❌ مهاجرت ناموفق بود — دوباره در اجرای بعدی تلاش می‌شود.','color:red;font-weight:bold',result);
    }

    /* اگر تب فاکتورها همین الان باز است و قبلاً (قبل از آماده شدن SQLite)
       با fallback حافظه رندر شده بود، یک‌بار دیگر از InvoiceRepo بکش. */
    if(typeof window.__onSQLiteReady==='function') window.__onSQLiteReady();
  }catch(e){
    /* مهم: این کچ عمداً بی‌سروصداست. اگر فایل در مرورگر معمولی (بدون
       Capacitor واقعی روی دستگاه) باز شده، پلاگین native SQLite اصلاً وجود
       ندارد و این خطا طبیعی است — رفتار فعلی اپ (آرایه‌ی حافظه) باید دقیقاً
       مثل قبل کار کند. پرچم قابل‌مشاهده هم ست می‌شود تا با
       window.__sqliteMigrationStatus قابل بررسی باشد. */
    window.__sqliteMigrationStatus = { status:'unavailable', message: e?.message };
    console.debug(TAG,'SQLite در این محیط در دسترس نیست یا مهاجرت رد شد — اپ با آرایه‌ی حافظه‌ی فعلی ادامه می‌دهد.',e?.message);
  }
})();
