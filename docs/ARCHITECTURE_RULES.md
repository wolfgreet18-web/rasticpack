# قوانین معماری — رستیک پک (اندروید)

> این سند خلاصه‌ی مرجعِ سریع «قانون وابستگی یک‌طرفه» است که در نقشه‌ی معماری
> (`نقشه-معماری-رستیک-پک-5.md`، بخش ۱) به‌طور کامل توضیح داده شده. هر مرحله‌ی بعدی
> باید به همین قوانین پایبند بماند؛ اگر شک داشتید، این فایل را قبل از افزودن هر
> فایل جدید مرور کنید.

## دیاگرام لایه‌ها

```
presentation (ui)  →  domain  ←  data
                         ↑
                       core (زیرساخت مشترک، بدون وابستگی به لایه‌های دیگر)
```

## قانون اصلی (اجباری، در تمام مراحل چک می‌شود)

**`presentation → domain ← data`** — یعنی هم `presentation` (پکیج `ui/`) و هم `data`
به `domain` وابسته‌اند، اما **`domain` به هیچ‌کدام وابسته نیست**.

- `ui/` هرگز مستقیماً از `data.*` (Entity، DAO، Repository پیاده‌سازی‌شده، یا
  `androidx.room`) import نمی‌کند — فقط از `domain.usecase` و `domain.model`.
- `domain/` هرگز از `data.*` یا `ui.*` import نمی‌کند — فقط از `domain.model`،
  `domain.repository` (اینترفیس خودش)، و `core.*`.
- `data/` می‌تواند از `domain.model` و `domain.repository` import کند (چون این
  لایه همان اینترفیس‌های domain را پیاده‌سازی می‌کند)، اما هرگز از `ui.*`.

## استثنای `core`

پکیج `core/` (شامل `core/result/`, `core/di/`, `core/dispatcher/`) زیرساخت خنثی
است — نه یک لایه‌ی جدید در جهت وابستگی، بلکه چیزی که **همه‌ی لایه‌ها (از جمله
`domain`) مجازند از آن استفاده کنند**، چون خودش هیچ منطق دامنه یا داده‌ای ندارد
(فقط `RasticResult`/`RasticError`، ماژول‌های Hilt، و `DispatcherProvider`).
استفاده از `core` نقض قانون یک‌طرفه‌بودن بالا محسوب نمی‌شود.

## هر پکیج دقیقاً چه چیزی نگه می‌دارد

| پکیج | محتوا | مجاز به import از |
|---|---|---|
| `domain/model/` | `data class` خالص کاتلین (بدون `androidx.room`) | فقط `domain.model` دیگر |
| `domain/repository/` | اینترفیس Repository (فقط قرارداد) | `domain.model`, `core.*` |
| `domain/usecase/` | یک عملیات = یک کلاس، `@Inject constructor`، برگرداندن `RasticResult<T>` | `domain.model`, `domain.repository`, `core.*` |
| `data/db/entity/` | Room Entity | فقط این لایه اجازه‌ی `androidx.room` دارد |
| `data/db/dao/` | DAO + `Flow` | `androidx.room` |
| `data/repository/` | پیاده‌سازی واقعی: `XRepositoryImpl : domain.repository.XRepository` | `domain.model`, `domain.repository`, `data.db.*` |
| `ui/*` | Screen (Composable) + ViewModel | `domain.usecase`, `domain.model`, `core.*` — **هرگز `data.*`** |

## چرا این قانون مهم است

- **تست‌پذیری:** چون `domain` به `androidx.room`/اندروید وابسته نیست، هر UseCase را
  می‌توان در Unit Test خالص JVM (بدون امولاتور) تست کرد — دقیقاً چیزی که فاز الف و
  فاز و (تست خودکار) نقشه به آن نیاز دارند.
- **جایگزین‌پذیری:** اگر روزی منبع داده عوض شد (مثلاً افزودن سینک با سرور)، فقط
  `data/repository/` تغییر می‌کند؛ `domain` و `ui` دست‌نخورده می‌مانند چون فقط با
  اینترفیس کار می‌کردند، نه پیاده‌سازی.
- **خوانایی:** با نگاه به import های بالای هر فایل بلافاصله معلوم است این فایل در
  کدام لایه است و چه چیزی حق دیدن دارد — بدون نیاز به مستندات جداگانه برای هر فایل.

## وضعیت فعلی این قانون در پروژه (به‌روزرسانی خودکار در هر مرحله)

- فاز صفر (مراحل ۰.۱–۰.۴): زیرساخت `core/` کامل شد؛ لایه‌ی `domain/` هنوز خالی
  است (فقط اسکلت پوشه — همین مرحله).
- از مرحله ۲ به بعد: هر Entity/Repository/ViewModel به‌ترتیب طبق این قانون به
  `domain/` منتقل می‌شود؛ تا آن زمان `ui/*ViewModel.kt` هنوز مستقیماً از
  `data.repo.*` (نسخه‌ی فعلی، پیش از انتقال) استفاده می‌کند — این یک نقض موقت
  و شناخته‌شده است که با تکمیل فاز الف رفع می‌شود، نه یک اشتباه.
