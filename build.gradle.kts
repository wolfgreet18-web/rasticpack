// فایل تنظیمات سطح پروژه — فقط پلاگین‌ها را اعلام می‌کند (apply=false یعنی هنوز فعال نشده،
// هر ماژول (مثل app) خودش تصمیم می‌گیرد کدام پلاگین را واقعاً استفاده کند)
plugins {
    id("com.android.application") version "8.5.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}
