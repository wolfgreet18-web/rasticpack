# ویجت کپی شماره کارت (Capacitor + Native Android Widget)

این پروژه یه اپ Capacitor هست که یه **ویجت واقعی صفحه اصلی اندروید** بهش اضافه شده.
با لمس ویجت، شماره کارتی که براش انتخاب کردی کپی می‌شه. می‌تونی چند تا ویجت
مختلف اضافه کنی که هرکدوم به یه کارت متفاوت وصل باشن.

چون ساخت APK نیاز به Android SDK و Gradle داره که من به‌صورت مستقیم بهش دسترسی
ندارم، پروژه طوری چیده شده که **GitHub خودش با یک push، APK رو می‌سازه** (با
GitHub Actions) و تو فقط دانلودش می‌کنی. نیازی به نصب Android Studio نیست.

---

## مرحله ۱: آماده‌سازی یک‌بار روی کامپیوتر (یا Termux گوشی)

باید یک‌بار Node.js داشته باشی (روی یک کامپیوتر، یا اپ Termux روی خود گوشی).

```bash
cd card-widget-app
npm install
npx cap init "کپی شماره کارت" com.example.cardwidget --web-dir=www
npx cap add android
```

> اگه خواستی packageId رو عوض کنی (پیشنهاد می‌شه یه چیز یکتا مثل
> `ir.yourname.cardwidget` بذاری)، باید همون اسم رو همه‌جا (capacitor.config.json،
> پوشه‌بندی جاوا، و فایل‌های native) جایگزین `com.example.cardwidget` کنی.

## مرحله ۲: اضافه‌کردن فایل‌های Native

بعد از `cap add android` یه پوشه `android/` ساخته می‌شه. حالا فایل‌های داخل
`native-android-files/` رو این‌طوری کپی کن:

```
native-android-files/java/com/example/cardwidget/*.kt
  → android/app/src/main/java/com/example/cardwidget/

native-android-files/res/layout/*.xml
  → android/app/src/main/res/layout/

native-android-files/res/xml/*.xml
  → android/app/src/main/res/xml/

native-android-files/res/drawable/*.xml
  → android/app/src/main/res/drawable/
```

سپس فایل `android/app/src/main/AndroidManifest.xml` رو باز کن و محتوای
`native-android-files/AndroidManifest-additions.xml` رو (طبق توضیح داخلش) قبل
از بسته‌شدن تگ `</application>` اضافه کن.

## مرحله ۳: آپلود به گیت‌هاب

```bash
git init
git add .
git commit -m "widget app"
git branch -M main
git remote add origin https://github.com/USERNAME/REPO.git
git push -u origin main
```

فایل `.github/workflows/build.yml` از قبل توی پروژه هست؛ به محض push، تب
**Actions** توی گیت‌هاب شروع به ساخت APK می‌کنه (چند دقیقه طول می‌کشه).

## مرحله ۴: دانلود و نصب APK

1. توی ریپوی گیت‌هاب برو به تب **Actions**
2. آخرین ران رو باز کن، پایین صفحه بخش **Artifacts** رو ببین
3. فایل `app-debug` رو دانلود کن (یه zip هست، داخلش `app-debug.apk`)
4. APK رو به گوشی هانر X8 منتقل کن (تلگرام، گوگل درایو، یا کابل)
5. توی تنظیمات گوشی، نصب از منابع ناشناس رو برای همون اپ (مثلاً فایل منیجر یا
   تلگرام) فعال کن، بعد روی APK بزن نصبش کن

## مرحله ۵: اضافه‌کردن ویجت به صفحه اصلی

1. روی صفحه اصلی گوشی، جای خالی رو لمس نگه‌دار
2. گزینه «ویجت‌ها» رو باز کن
3. اپ «کپی شماره کارت» رو پیدا کن و ویجتش رو به صفحه بکش
4. یه پنجره باز می‌شه — یا یه کارت ذخیره‌شده رو انتخاب کن، یا با دکمه «افزودن
   کارت جدید» یه کارت تازه (عنوان + شماره) اضافه کن
5. تمام! از این به بعد با یه لمس روی اون ویجت، شماره همون کارت کپی می‌شه

برای هر کارت دیگه، همین مراحل ویجت اضافه‌کردن رو تکرار کن و کارت دیگه‌ای رو
انتخاب کن. برای حذف یه کارت از لیست ذخیره‌شده‌ها، توی صفحه انتخاب کارت،
روی اسمش نگه‌دار.

---

## نکته امنیتی

شماره کارت‌ها به‌صورت متن ساده (بدون رمزنگاری) روی گوشی خودت، داخل حافظه
اختصاصی اپ ذخیره می‌شن. برای استفاده شخصی روی گوشی خودت مشکلی نداره، ولی
اگه گوشیت روت باشه یا بخوای امنیت بیشتری بخوای، بگو تا رمزنگاری
(EncryptedSharedPreferences) رو هم اضافه کنم.
