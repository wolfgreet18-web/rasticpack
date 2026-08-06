// ══ فایل سطح پروژه (root) ══
// طبق مرحله ۰.۱ نقشه‌ی معماری: افزودن پلاگین Hilt در اینجا با apply false —
// پلاگین واقعی در app/build.gradle.kts اعمال می‌شود. نسخه‌ها با AGP/Kotlin
// فعلی پروژه (build.gradle.kts ماژول app) هماهنگ انتخاب شده‌اند.
plugins {
    id("com.android.application") version "8.5.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false
    id("com.google.dagger.hilt.android") version "2.51.1" apply false
}
