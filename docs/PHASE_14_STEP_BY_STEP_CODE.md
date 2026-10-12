# Phase 14: Notifications (WorkManager) — Hướng dẫn từng bước

Mục tiêu Phase 14:

1. **WorkManager** `work-runtime 2.11.2` + channel `learning`
2. **4 loại nhắc học**: bookmark chưa review, repeated mistakes, motivational, daily/weekly summary
3. **Periodic daily 20:00** + **one-time force** (long-press title Progress) để test
4. **Deep link** notification → `MainActivity` (`EXTRA_OPEN_TAB` / `EXTRA_QA_ID` → mở `AnswerActivity`)
5. **Chống spam**: mỗi loại tối đa **1 lần/ngày** (`SharedPreferences notify_state`)

**Điều kiện trước khi làm:** Phase 0–13 đã xong (Room **v8**, `XpRules`, `Friend`, và các DAO query cho notification **đã thêm ở Phase 13 Step 8**).

**Không làm ở Phase này:** FCM / push server, Room bump version (**giữ v8**), gọi Gemini trong Worker, notification setting screen.

**Constants quan trọng (phải giữ đúng):**

| Constant | Value | Ý nghĩa |
|----------|-------|---------|
| `CHANNEL_LEARNING` | `"learning"` | Channel id |
| `ID_BOOKMARK` | `13001` | Notification id |
| `ID_MISTAKES` | `13002` | |
| `ID_MOTIVATIONAL` | `13003` | |
| `ID_SUMMARY` | `13004` | |
| `UNIQUE_DAILY` | `"aistudy_daily_learning"` | Unique periodic work |
| `UNIQUE_FORCE` | `"aistudy_force_learning"` | Unique one-time work |
| Giờ chạy | `20` (8pm) | `millisUntilHour(20)` |
| Chu kỳ | `1 DAY` | `PeriodicWorkRequest` |
| `HOURS_48_MS` | `48h` | Ngưỡng bookmark chưa review |
| `DAYS_7_MS` | `7 ngày` | Cửa sổ mistakes + weekly summary |
| Mistakes threshold | `COUNT(*) >= 2` | Sai ≥2 item cùng subject / 7 ngày |
| Near next level | `xpToNextLevel <= 50` | `XpRules.isNearNextLevel` |
| Prefs | `notify_state` / `last_day_<type>` | Chống gửi lặp trong ngày |

Trong Kotlin, các hằng số trên là `const val`: của `NotificationHelper` / `NotificationScheduler` nằm trực tiếp trong `object`, của `DailyLearningWorker` nằm trong `companion object` (`private const val`).

**Trigger tổng hợp:**

| Loại | Điều kiện | Deep link |
|------|-----------|-----------|
| Review bookmarked | có QA `starred = 1 AND reviewedAt = 0 AND createdAt <= now-48h` | `TAB_HISTORY` + `qaId` → mở Answer |
| Repeated mistakes | 1 subject có ≥2 `quiz_items.isCorrect = 0` trong 7 ngày | `TAB_PROGRESS` |
| Motivational | `streakAtRisk` (hôm qua có hoạt động, hôm nay chưa) **hoặc** còn ≤50 XP lên level | `TAB_HOME` |
| Daily / weekly summary | mỗi ngày; **Chủ nhật** → bản weekly (cửa sổ 7 ngày) | `TAB_PROGRESS` |

**Ưu tiên nội dung motivational:** streak-at-risk **trước**, nếu không thì near-level.

---

## Tổng quan các file sẽ chạm

| File | Loại | Việc |
|------|------|------|
| `gradle/libs.versions.toml` | Sửa | `work = "2.11.2"` + `work-runtime` |
| `app/build.gradle.kts` | Sửa | `implementation(libs.work.runtime)` |
| `AndroidManifest.xml` | Sửa | `POST_NOTIFICATIONS` + `MainActivity` `singleTop` |
| `res/values/strings_notify.xml` | **Mới** | Toàn bộ text notification |
| `notify/NotificationHelper.kt` | **Mới** | Channel + build + PendingIntent |
| `notify/NotificationScheduler.kt` | **Mới** | Periodic 20:00 + force one-time |
| `notify/DailyLearningWorker.kt` | **Mới** | 4 rule + chống lặp trong ngày |
| `MainActivity.kt` | Sửa | Xin permission, schedule work, handle deep link |
| `progress/ProgressFragment.kt` | Sửa | Long-press title → `enqueueNow` |

**Không đụng:** `database/` (DAO đã đủ từ Phase 13), `AppDatabase` (giữ **v8**), Gemini, layout Progress (`tv_progress_title` đã có id từ Phase 13).

---

## Step 1) Dependency WorkManager

**Giải thích:**  
Project dùng **KSP** cho Room: `libs.versions.toml` có version `ksp = "2.3.12"` và plugin `ksp` (`com.google.devtools.ksp`), `app/build.gradle.kts` apply `alias(libs.plugins.ksp)` và khai báo `ksp(libs.room.compiler)`. AGP 9.2.1 có sẵn Kotlin built-in nên không cần plugin `org.jetbrains.kotlin.android`. Phase này chỉ thêm `work = "2.11.2"`, library `work-runtime` và `implementation(libs.work.runtime)`.

Artifact `work-runtime` dùng trực tiếp từ Kotlin: class `Worker` được kế thừa bằng `class DailyLearningWorker(...) : Worker(context, params)`, request tạo bằng `PeriodicWorkRequest.Builder(DailyLearningWorker::class.java, 1, TimeUnit.DAYS)` và `OneTimeWorkRequest.Builder(DailyLearningWorker::class.java)`. Không cần `work-runtime-ktx` vì Worker không dùng coroutine.

**File:** `gradle/libs.versions.toml` (**thay toàn bộ**)

```toml
[versions]
agp = "9.2.1"
junit = "4.13.2"
junitVersion = "1.3.0"
espressoCore = "3.7.0"
appcompat = "1.7.1"
material = "1.14.0"
activityKtx = "1.13.0"
constraintlayout = "2.2.1"
room = "2.7.2"
fragment = "1.8.9"
recyclerview = "1.4.0"
work = "2.11.2"
ksp = "2.3.12"

[libraries]
junit = { group = "junit", name = "junit", version.ref = "junit" }
ext-junit = { group = "androidx.test.ext", name = "junit", version.ref = "junitVersion" }
espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espressoCore" }
appcompat = { group = "androidx.appcompat", name = "appcompat", version.ref = "appcompat" }
material = { group = "com.google.android.material", name = "material", version.ref = "material" }
activity-ktx = { group = "androidx.activity", name = "activity-ktx", version.ref = "activityKtx" }
constraintlayout = { group = "androidx.constraintlayout", name = "constraintlayout", version.ref = "constraintlayout" }
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
fragment = { group = "androidx.fragment", name = "fragment", version.ref = "fragment" }
recyclerview = { group = "androidx.recyclerview", name = "recyclerview", version.ref = "recyclerview" }
work-runtime = { group = "androidx.work", name = "work-runtime", version.ref = "work" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }

```

**File:** `app/build.gradle.kts` (**thay toàn bộ**)

```kotlin
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}
val geminiApiKey = localProperties.getProperty("GEMINI_API_KEY", "")

android {
    namespace = "com.aistudy.aistudy"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.aistudy.aistudy"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation(libs.fragment)
    implementation(libs.recyclerview)
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    implementation(libs.work.runtime)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
```

**Kiểm tra nhanh:** Sync Gradle → `androidx.work:work-runtime:2.11.2` xuất hiện trong External Libraries. `minSdk = 33` nên **không cần** guard version cho `NotificationChannel`.

---

## Step 2) `AndroidManifest.xml` — permission + `singleTop`

**Giải thích:**  
`POST_NOTIFICATIONS` bắt buộc từ API 33. `launchMode="singleTop"` để tap notification khi app đang mở thì vào `onNewIntent` thay vì tạo Activity mới (tránh mở trùng Answer).

**File:** `app/src/main/AndroidManifest.xml` (**thay toàn bộ**)

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <uses-feature
        android:name="android.hardware.camera"
        android:required="false" />

    <application
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.AIStudy">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTop">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />

                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
        <activity
            android:name=".auth.LoginActivity"
            android:exported="false" />
        <activity
            android:name=".auth.RegisterActivity"
            android:exported="false" />
        <activity
            android:name=".onboarding.OnboardingActivity"
            android:exported="false" />
        <activity
            android:name=".answer.AnswerActivity"
            android:exported="false" />
        <activity
            android:name=".quiz.QuizActivity"
            android:exported="false" />

        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>
    </application>

</manifest>
```

**Lưu ý:** Không cần khai báo `WorkManagerInitializer` — WorkManager tự init bằng `androidx.startup`.

---

## Step 3) `strings_notify.xml`

**File:** `app/src/main/res/values/strings_notify.xml` (**mới**)

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="notify_channel_name">Learning reminders</string>
    <string name="notify_channel_desc">Bookmarks, mistakes, motivation, and weekly summary</string>
    <string name="notify_bookmark_title">Review a bookmarked answer</string>
    <string name="notify_bookmark_body">You starred an answer more than 48 hours ago. Tap to review it.</string>
    <string name="notify_mistakes_title">Repeated mistakes</string>
    <string name="notify_mistakes_body">You missed several %1$s quiz items recently. Practice that subject.</string>
    <string name="notify_motivational_title">Keep going</string>
    <string name="notify_motivational_streak">Your %1$d-day streak is at risk — ask or review today!</string>
    <string name="notify_motivational_level">Only %1$d XP left to reach level %2$d.</string>
    <string name="notify_summary_daily_title">Daily learning summary</string>
    <string name="notify_summary_weekly_title">Weekly learning summary</string>
    <string name="notify_summary_body">Questions: %1$d · Quiz accuracy: %2$d%% · Weekly XP: %3$d</string>
</resources>
```

**Kiểm tra nhanh:** `%2$d%%` — dấu `%` literal phải escape `%%`.

---

## Step 4) `notify/NotificationHelper.kt`

**Giải thích:**  
`NotificationHelper` là `object` (singleton); gọi từ nơi khác bằng `NotificationHelper.show(...)`. Hằng số là `const val` nên dùng được ở mọi nơi như compile-time constant.

`ensureChannel` idempotent (gọi lại chỉ update). `context.getSystemService(NotificationManager::class.java)` trả về kiểu nullable nên dùng `?.createNotificationChannel(channel)`. `channel.description = ...` và `intent.flags = ...` là cú pháp property; cờ được ghép bằng toán tử bit `or`. `title` / `body` là `String?` vì `NotificationCompat.Builder` chấp nhận null.

`show` build `PendingIntent` với `requestCode = notificationId` để 4 loại không ghi đè extras của nhau; `FLAG_IMMUTABLE` bắt buộc từ API 31.

**File:** `app/src/main/java/com/aistudy/aistudy/notify/NotificationHelper.kt` (**mới**)

```kotlin
package com.aistudy.aistudy.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.aistudy.aistudy.MainActivity
import com.aistudy.aistudy.R

object NotificationHelper {

    const val CHANNEL_LEARNING = "learning"

    const val ID_BOOKMARK = 13001
    const val ID_MISTAKES = 13002
    const val ID_MOTIVATIONAL = 13003
    const val ID_SUMMARY = 13004

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_LEARNING,
            context.getString(R.string.notify_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        channel.description = context.getString(R.string.notify_channel_desc)
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    fun show(
        context: Context,
        notificationId: Int,
        title: String?,
        body: String?,
        openTab: String?,
        qaId: Long
    ) {
        ensureChannel(context)

        val intent = Intent(context, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        if (openTab != null) {
            intent.putExtra(MainActivity.EXTRA_OPEN_TAB, openTab)
        }
        if (qaId > 0) {
            intent.putExtra(MainActivity.EXTRA_QA_ID, qaId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_LEARNING)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }
}
```

---

## Step 5) `notify/NotificationScheduler.kt`

**Giải thích:**  
`NotificationScheduler` cũng là `object`. `schedule` gọi mỗi lần mở `MainActivity` nhưng dùng `ExistingPeriodicWorkPolicy.KEEP` → không reset lịch. Worker được truyền bằng `DailyLearningWorker::class.java`. `enqueueNow` **clear** `notify_state` để bỏ cờ "đã gửi hôm nay" → test lại được ngay. `millisUntilHour` là `private fun` trả về `Long`, đọc `target.timeInMillis`.

**File:** `app/src/main/java/com/aistudy/aistudy/notify/NotificationScheduler.kt` (**mới**)

```kotlin
package com.aistudy.aistudy.notify

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

object NotificationScheduler {

    const val UNIQUE_DAILY = "aistudy_daily_learning"
    const val UNIQUE_FORCE = "aistudy_force_learning"

    fun schedule(context: Context) {
        NotificationHelper.ensureChannel(context)

        val delayMs = millisUntilHour(20)
        val daily = PeriodicWorkRequest.Builder(
            DailyLearningWorker::class.java,
            1,
            TimeUnit.DAYS
        )
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_DAILY,
            ExistingPeriodicWorkPolicy.KEEP,
            daily
        )
    }

    fun enqueueNow(context: Context) {
        NotificationHelper.ensureChannel(context)
        context.getSharedPreferences("notify_state", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
        val once = OneTimeWorkRequest.Builder(DailyLearningWorker::class.java).build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_FORCE,
            ExistingWorkPolicy.REPLACE,
            once
        )
    }

    private fun millisUntilHour(hourOfDay: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance()
        target.set(Calendar.HOUR_OF_DAY, hourOfDay)
        target.set(Calendar.MINUTE, 0)
        target.set(Calendar.SECOND, 0)
        target.set(Calendar.MILLISECOND, 0)
        if (!target.after(now)) {
            target.add(Calendar.DAY_OF_MONTH, 1)
        }
        return target.timeInMillis - now.timeInMillis
    }
}
```

**Kiểm tra nhanh:** Đã qua 20:00 → `millisUntilHour(20)` trả về khoảng cách tới 20:00 **ngày mai**.

---

## Step 6) `notify/DailyLearningWorker.kt` — 4 rule

**Giải thích:**

| Hàm | Rule |
|-----|------|
| `maybeBookmark` | `getOldestUnreviewedStarred(userId, now - 48h) ?: return` → notify + `qa.id` deep link |
| `maybeMistakes` | `findSubjectWithRepeatedMistakes(userId, now - 7d)` không `isNullOrEmpty()` → notify (subject capitalize) |
| `maybeMotivational` | `streakAtRisk` **hoặc** `isNearNextLevel` |
| `maybeSummary` | Chủ nhật → weekly (7 ngày), còn lại → daily (từ 00:00 hôm nay) |

Các điểm Kotlin trong file:

- Constructor chính `class DailyLearningWorker(context: Context, params: WorkerParameters) : Worker(context, params)` truyền thẳng `context`, `params` lên lớp cha `Worker`; tham số khai báo non-null nên không cần annotation.
- `override fun doWork(): Result`, `applicationContext` là property.
- `userId` kiểu `Long` nên so sánh với `-1L`.
- `getByUserId(userId) ?: return Result.success()` gộp bước lấy profile và kiểm tra null bằng Elvis `?:`; sau dòng này `profile` là `Profile` non-null.
- `val body = if (...) ... else ...` và `val fromMs = if (sunday) ... else { ...; start.timeInMillis }` dùng `if` như biểu thức, không cần khai báo biến rồi gán sau.
- `roundToInt()` (import `kotlin.math.roundToInt`) làm tròn số, `==` so sánh nội dung chuỗi, `"summary_$kind"` là string template.
- `PREFS`, `KEY_DAY`, `HOURS_48_MS`, `DAYS_7_MS` và hàm `capitalize` nằm trong `companion object`; `capitalize` dùng `uppercase(Locale.US)`.

`alreadySentToday` / `markSentToday` dùng `notify_state` + `XpRules.todayKey()`. Worker chạy trên background thread của WorkManager, DAO có `allowMainThreadQueries` nên gọi trực tiếp OK.

**File:** `app/src/main/java/com/aistudy/aistudy/notify/DailyLearningWorker.kt` (**mới**)

```kotlin
package com.aistudy.aistudy.notify

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.aistudy.aistudy.MainActivity
import com.aistudy.aistudy.R
import com.aistudy.aistudy.auth.LoginActivity
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.Profile
import com.aistudy.aistudy.database.QuestionAnswerDao
import com.aistudy.aistudy.database.QuizItemDao
import com.aistudy.aistudy.xp.XpRules
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

class DailyLearningWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val context = applicationContext
        val userId = context.getSharedPreferences(LoginActivity.PREFS_AUTH, Context.MODE_PRIVATE)
            .getLong(LoginActivity.KEY_USER_ID, -1)
        if (userId == -1L) {
            return Result.success()
        }

        val db = AppDatabase.getInstance(context)
        val profile = db.profileDao().getByUserId(userId) ?: return Result.success()

        val qaDao = db.questionAnswerDao()
        val quizItemDao = db.quizItemDao()

        maybeBookmark(context, qaDao, userId)
        maybeMistakes(context, quizItemDao, userId)
        maybeMotivational(context, profile, userId)
        maybeSummary(context, qaDao, quizItemDao, profile, userId)

        return Result.success()
    }

    private fun maybeBookmark(context: Context, qaDao: QuestionAnswerDao, userId: Long) {
        if (alreadySentToday(context, "bookmark")) {
            return
        }
        val beforeMs = System.currentTimeMillis() - HOURS_48_MS
        val qa = qaDao.getOldestUnreviewedStarred(userId, beforeMs) ?: return
        NotificationHelper.show(
            context,
            NotificationHelper.ID_BOOKMARK,
            context.getString(R.string.notify_bookmark_title),
            context.getString(R.string.notify_bookmark_body),
            MainActivity.TAB_HISTORY,
            qa.id
        )
        markSentToday(context, "bookmark")
    }

    private fun maybeMistakes(context: Context, quizItemDao: QuizItemDao, userId: Long) {
        if (alreadySentToday(context, "mistakes")) {
            return
        }
        val fromMs = System.currentTimeMillis() - DAYS_7_MS
        val subject = quizItemDao.findSubjectWithRepeatedMistakes(userId, fromMs)
        if (subject.isNullOrEmpty()) {
            return
        }
        NotificationHelper.show(
            context,
            NotificationHelper.ID_MISTAKES,
            context.getString(R.string.notify_mistakes_title),
            context.getString(R.string.notify_mistakes_body, capitalize(subject)),
            MainActivity.TAB_PROGRESS,
            -1
        )
        markSentToday(context, "mistakes")
    }

    private fun maybeMotivational(context: Context, profile: Profile, userId: Long) {
        if (alreadySentToday(context, "motivational")) {
            return
        }
        XpRules.ensureWeek(profile)
        val nearLevel = XpRules.isNearNextLevel(profile.xp)
        val streakRisk = XpRules.streakAtRisk(profile)
        if (!nearLevel && !streakRisk) {
            return
        }
        val body = if (streakRisk) {
            context.getString(R.string.notify_motivational_streak, profile.streak)
        } else {
            context.getString(
                R.string.notify_motivational_level,
                XpRules.xpToNextLevel(profile.xp),
                XpRules.levelFromXp(profile.xp) + 1
            )
        }
        NotificationHelper.show(
            context,
            NotificationHelper.ID_MOTIVATIONAL,
            context.getString(R.string.notify_motivational_title),
            body,
            MainActivity.TAB_HOME,
            -1
        )
        markSentToday(context, "motivational")
    }

    private fun maybeSummary(
        context: Context,
        qaDao: QuestionAnswerDao,
        quizItemDao: QuizItemDao,
        profile: Profile,
        userId: Long
    ) {
        val cal = Calendar.getInstance()
        val sunday = cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
        val kind = if (sunday) "weekly" else "daily"
        if (alreadySentToday(context, "summary_$kind")) {
            return
        }

        val fromMs = if (sunday) {
            System.currentTimeMillis() - DAYS_7_MS
        } else {
            val start = Calendar.getInstance()
            start.set(Calendar.HOUR_OF_DAY, 0)
            start.set(Calendar.MINUTE, 0)
            start.set(Calendar.SECOND, 0)
            start.set(Calendar.MILLISECOND, 0)
            start.timeInMillis
        }

        val questions = qaDao.countCreatedSince(userId, fromMs)
        val attempted = quizItemDao.countAttemptedByUserId(userId)
        val correct = quizItemDao.countCorrectByUserId(userId)
        val accuracy = if (attempted > 0) ((correct * 100f) / attempted).roundToInt() else 0
        XpRules.ensureWeek(profile)

        val title = if (sunday) {
            context.getString(R.string.notify_summary_weekly_title)
        } else {
            context.getString(R.string.notify_summary_daily_title)
        }
        val body = context.getString(
            R.string.notify_summary_body,
            questions,
            accuracy,
            profile.weeklyXp
        )

        NotificationHelper.show(
            context,
            NotificationHelper.ID_SUMMARY,
            title,
            body,
            MainActivity.TAB_PROGRESS,
            -1
        )
        markSentToday(context, "summary_$kind")
    }

    private fun alreadySentToday(context: Context, type: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = XpRules.todayKey()
        return today == prefs.getString(KEY_DAY + type, "")
    }

    private fun markSentToday(context: Context, type: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DAY + type, XpRules.todayKey())
            .apply()
    }

    companion object {
        private const val PREFS = "notify_state"
        private const val KEY_DAY = "last_day_"
        private const val HOURS_48_MS = 48L * 60L * 60L * 1000L
        private const val DAYS_7_MS = 7L * 24L * 60L * 60L * 1000L

        private fun capitalize(subject: String?): String {
            if (subject.isNullOrEmpty()) {
                return ""
            }
            return subject.substring(0, 1).uppercase(Locale.US) + subject.substring(1)
        }
    }
}
```

**Kiểm tra nhanh:** Summary **luôn** gửi (kể cả 0 câu hỏi); 3 loại còn lại chỉ gửi khi thỏa điều kiện.

---

## Step 7) `MainActivity` — permission + schedule + deep link

**Giải thích:**

| Phần | Việc |
|------|------|
| `companion object` với `EXTRA_OPEN_TAB` / `EXTRA_QA_ID` + `TAB_*` | Hằng số `const val` dùng chung với `NotificationHelper` và `DailyLearningWorker` (gọi `MainActivity.TAB_HISTORY` ...) |
| `notificationPermissionLauncher` | `registerForActivityResult(ActivityResultContracts.RequestPermission()) { }` — lambda rỗng vì không cần xử lý kết quả |
| `handleOpenIntent(intent: Intent?)` | `qaId > 0` → mở `AnswerActivity` + chọn tab History; ngược lại gán `bottomNav.selectedItemId = when (intent.getStringExtra(EXTRA_OPEN_TAB)) { ... }` |
| `override fun onNewIntent(intent: Intent)` | `singleTop` → `setIntent(intent)` rồi handle lại |
| `NotificationScheduler.schedule(this)` | Cuối `onCreate` |

`bottomNav` là `private lateinit var` vì chỉ được gán sau `setContentView`. Listener bottom nav dùng `when (item.itemId)`, nhánh `else -> return@setOnItemSelectedListener false` thoát khỏi lambda với giá trị `false`. `bottomNav.selectedItemId = ...` là cú pháp property.

`handleOpenIntent` chỉ chạy khi `savedInstanceState == null` để rotate không mở lại Answer.

**File:** `app/src/main/java/com/aistudy/aistudy/MainActivity.kt` (**thay toàn bộ**)

```kotlin
package com.aistudy.aistudy

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.aistudy.aistudy.answer.AnswerActivity
import com.aistudy.aistudy.ask.AskFragment
import com.aistudy.aistudy.auth.LoginActivity
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.ProfileDefaults
import com.aistudy.aistudy.history.HistoryFragment
import com.aistudy.aistudy.home.HomeFragment
import com.aistudy.aistudy.notify.NotificationScheduler
import com.aistudy.aistudy.onboarding.OnboardingActivity
import com.aistudy.aistudy.progress.ProgressFragment
import com.aistudy.aistudy.xp.FriendSeeder
import com.aistudy.aistudy.xp.UnlockCatalog
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_OPEN_TAB = "open_tab"
        const val EXTRA_QA_ID = "qa_id"
        const val TAB_HOME = "home"
        const val TAB_ASK = "ask"
        const val TAB_HISTORY = "history"
        const val TAB_PROGRESS = "progress"
    }

    private lateinit var bottomNav: BottomNavigationView
    private val notificationPermissionLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val userId = getSharedPreferences(LoginActivity.PREFS_AUTH, MODE_PRIVATE)
            .getLong(LoginActivity.KEY_USER_ID, -1)
        if (userId == -1L) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        ProfileDefaults.ensureExists(AppDatabase.getInstance(this).profileDao(), userId)
        val profile = AppDatabase.getInstance(this).profileDao().getByUserId(userId)
        if (profile != null && !profile.onboarded) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        FriendSeeder.ensureSeeded(AppDatabase.getInstance(this).friendDao(), userId)

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        if (profile != null) {
            findViewById<View>(R.id.main).setBackgroundResource(
                UnlockCatalog.backgroundColorRes(UnlockCatalog.normalizeThemeId(profile.themeId))
            )
        }

        bottomNav = findViewById(R.id.bottom_nav)
        bottomNav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_home -> HomeFragment()
                R.id.nav_ask -> AskFragment()
                R.id.nav_history -> HistoryFragment()
                R.id.nav_progress -> ProgressFragment()
                else -> return@setOnItemSelectedListener false
            }
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit()
            true
        }

        if (savedInstanceState == null) {
            handleOpenIntent(intent)
        }

        requestNotificationPermissionIfNeeded()
        NotificationScheduler.schedule(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOpenIntent(intent)
    }

    private fun handleOpenIntent(intent: Intent?) {
        if (intent == null) {
            bottomNav.selectedItemId = R.id.nav_home
            return
        }
        val qaId = intent.getLongExtra(EXTRA_QA_ID, -1)
        if (qaId > 0) {
            val answer = Intent(this, AnswerActivity::class.java)
            answer.putExtra(AnswerActivity.EXTRA_QA_ID, qaId)
            startActivity(answer)
            bottomNav.selectedItemId = R.id.nav_history
            return
        }
        bottomNav.selectedItemId = when (intent.getStringExtra(EXTRA_OPEN_TAB)) {
            TAB_ASK -> R.id.nav_ask
            TAB_HISTORY -> R.id.nav_history
            TAB_PROGRESS -> R.id.nav_progress
            else -> R.id.nav_home
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < 33) {
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
```

**Kiểm tra nhanh:** `registerForActivityResult` phải khai báo ở **property** của class (`private val notificationPermissionLauncher = ...` như trên), gọi trong `onCreate` sau `super` sẽ crash `LifecycleOwners must call register before they are STARTED`.

---

## Step 8) `ProgressFragment` — long-press title để force notification

**Giải thích:**  
Không cần ADB: long-press tiêu đề Progress → clear `notify_state` + chạy Worker ngay. Lambda của `setOnLongClickListener` trả về `true` ở dòng cuối (không có `return`). `findViewById<View>(...)` cần tham số kiểu vì không có biến nhận kiểu.

**File:** `app/src/main/java/com/aistudy/aistudy/progress/ProgressFragment.kt`

**8.1 — thêm import:**

```kotlin
import com.aistudy.aistudy.notify.NotificationScheduler
```

**8.2 — trong `onViewCreated`, thêm ngay sau `btnLogout.setOnClickListener { logout() }`:**

```kotlin
        view.findViewById<View>(R.id.tv_progress_title).setOnLongClickListener {
            NotificationScheduler.enqueueNow(requireContext())
            Toast.makeText(requireContext(), R.string.progress_force_notify, Toast.LENGTH_SHORT).show()
            true
        }
```

Sau khi sửa, block listener trong `onViewCreated` phải đúng như sau:

```kotlin
        btnLogout.setOnClickListener { logout() }
        view.findViewById<View>(R.id.tv_progress_title).setOnLongClickListener {
            NotificationScheduler.enqueueNow(requireContext())
            Toast.makeText(requireContext(), R.string.progress_force_notify, Toast.LENGTH_SHORT).show()
            true
        }
        cardReviewSuggest.setOnClickListener {
            reviewSubject?.let { openPractice(it) }
        }
```

**Kiểm tra nhanh:** `Toast`, `View` và `R.string.progress_force_notify` đã có sẵn từ Phase 13 (Step 12) — không cần thêm string hay import khác.

---

## Step 9) DAO — không sửa gì ở Phase 14

Các query notification **đã thêm ở Phase 13 Step 8**. Chỉ verify tồn tại:

| Query | File | Dùng ở |
|-------|------|--------|
| `getOldestUnreviewedStarred(userId, beforeMs): QuestionAnswer?` | `QuestionAnswerDao` | bookmark |
| `countUnreviewedStarredBefore(userId, beforeMs)` | `QuestionAnswerDao` | đếm bookmark quá hạn |
| `countCreatedSince(userId, fromMs)` | `QuestionAnswerDao` | summary |
| `findSubjectWithRepeatedMistakes(userId, fromMs): String?` | `QuizItemDao` | mistakes |
| `countAttemptedByUserId` / `countCorrectByUserId` | `QuizItemDao` | accuracy trong summary |
| `countCompletedSince(userId, fromMs)` | `QuizSessionDao` | số quiz trong kỳ |

Hai query trả về một dòng có kiểu nullable (`?`) nên Worker xử lý bằng `?: return` và `isNullOrEmpty()`.

**`AppDatabase` giữ `version = 8`** — Phase 14 không đổi schema.

---

## Step 10) Test tay (checklist)

### Chuẩn bị

1. Build + run → hộp thoại xin quyền **Notifications** → **Allow**.
2. Ask ≥1 câu, star 1 answer, làm 1 quiz sai ≥2 câu cùng môn.
3. Vào tab **Progress**.

### Force chạy Worker

1. **Long-press** tiêu đề “Learning progress”.
2. Thấy Toast `Checking learning reminders…`.
3. Sau 1–3 giây → notification xuất hiện (ít nhất bản **summary**).

### Checklist từng loại

| Loại | Cách tạo điều kiện | Kỳ vọng |
|------|--------------------|---------|
| Bookmark | Sửa `question_answers.createdAt` của 1 QA đã star về `now - 3 ngày`, `reviewedAt = 0` | Notification “Review a bookmarked answer”; tap → mở đúng Answer đó |
| Mistakes | Làm quiz 1 môn, sai ≥2 câu | “Repeated mistakes” + tên môn capitalize; tap → tab Progress |
| Motivational | Đặt `xp = 60` (còn 40 XP tới 100) hoặc `lastActiveDay` = hôm qua + `streak = 3` | “Keep going” với 1 trong 2 nội dung; streak-at-risk ưu tiên |
| Summary | Không cần chuẩn bị | “Daily learning summary” (Chủ nhật → “Weekly…”) với 3 số liệu |

### Checklist chống spam

1. Long-press lần 2 **ngay sau đó** → vẫn ra notification vì `enqueueNow` clear `notify_state`.
2. Chờ Worker periodic (hoặc không clear prefs) → cùng loại **không** gửi lại trong ngày.

### Checklist deep link

1. Notification bookmark → tap → `AnswerActivity` đúng `qaId`, back về tab History.
2. Notification mistakes / summary → tap → tab **Progress**.
3. Notification motivational → tap → tab **Home**.
4. App đang mở khi tap → không tạo `MainActivity` thứ 2 (nhờ `singleTop`).

### Checklist periodic

1. `adb shell dumpsys jobscheduler | findstr aistudy` → thấy job của WorkManager.
2. Kill app rồi mở lại → `schedule` không nhân bản job (`KEEP`).

### Lỗi thường gặp

| Triệu chứng | Nguyên nhân hay gặp | Cách xử lý |
|-------------|---------------------|------------|
| Không có notification nào | Chưa cấp quyền POST_NOTIFICATIONS | Settings → App → Notifications → Allow |
| `Unresolved reference: work` / `Unresolved reference: Worker` | Quên `work-runtime` trong `libs.versions.toml` hoặc `implementation(libs.work.runtime)` | Step 1, Sync Gradle |
| `ClassNotFoundException: DailyLearningWorker` | Sai package hoặc đổi tên class sau khi đã enqueue | Kiểm tra `package com.aistudy.aistudy.notify`, gỡ app cài lại |
| `Unresolved reference: TAB_HISTORY` / `EXTRA_OPEN_TAB` | Hằng số khai báo ngoài `companion object` của `MainActivity` | Step 7 |
| `Type mismatch: inferred type is Profile? but Profile was expected` | Bỏ `?: return Result.success()` sau `getByUserId` | Step 6 |
| `Only safe (?.) or non-null asserted (!!.) calls are allowed` ở `getSystemService` | Gọi `.createNotificationChannel` trực tiếp trên kết quả nullable | Step 4 (dùng `?.`) |
| `lateinit property bottomNav has not been initialized` | Gọi `handleOpenIntent` trước `bottomNav = findViewById(...)` | Step 7 (giữ đúng thứ tự) |
| Crash `register before STARTED` | Gọi `registerForActivityResult` trong `onCreate` | Step 7 (khai báo ở property) |
| `ksp` / `Plugin [id: 'com.google.devtools.ksp'] was not found` | Thiếu plugin `ksp` trong `libs.versions.toml` hoặc `alias(libs.plugins.ksp) apply false` ở root | Step 1 + phần Hoàn tất lộ trình |
| Tap notification mở app trắng | Thiếu `launchMode="singleTop"` / thiếu `onNewIntent` | Step 2 + 7 |
| Notification sau ghi đè extras notification trước | Dùng cùng `requestCode` cho PendingIntent | Step 4 (dùng `notificationId`) |
| aapt lỗi format | `%2$d%%` thiếu escape | Step 3 |
| Long-press không phản ứng | `tv_progress_title` chưa có id (layout Phase 13) | Phase 13 Step 13 |
| Mistakes không bao giờ bắn | Quiz chưa có item `isCorrect = 0` trong 7 ngày (property Kotlin `correct` map cột `isCorrect`) | Làm quiz sai thêm |

---

## Mapping với plan / FR

| FR / Plan | Android Phase 14 |
|-----------|------------------|
| `NotificationHelper` + channel `learning` | `NotificationHelper.CHANNEL_LEARNING` |
| WorkManager periodic (daily) + one-time | `NotificationScheduler.schedule` / `enqueueNow` |
| Review bookmarked — ★ chưa `reviewedAt` trong 48h | `maybeBookmark` + `getOldestUnreviewedStarred` |
| Repeated mistakes — cùng subject sai ≥2 | `maybeMistakes` + `findSubjectWithRepeatedMistakes` |
| Motivational — streak sắp mất / XP gần level | `maybeMotivational` + `streakAtRisk` / `isNearNextLevel` |
| Daily/weekly summary 20:00 hoặc Sunday | `maybeSummary` + `millisUntilHour(20)` |
| Deep link → History / Progress / Answer theo `qaId` | `MainActivity.handleOpenIntent` |
| Không push FCM | Chỉ WorkManager local |

---

## Checklist hoàn thành Phase 14

- [ ] `work = "2.11.2"` + `work-runtime` trong `libs.versions.toml`
- [ ] `implementation(libs.work.runtime)` trong `app/build.gradle.kts` (cạnh `ksp(libs.room.compiler)`)
- [ ] `POST_NOTIFICATIONS` + `MainActivity` `singleTop`
- [ ] `strings_notify.xml`
- [ ] `notify/NotificationHelper.kt`
- [ ] `notify/NotificationScheduler.kt`
- [ ] `notify/DailyLearningWorker.kt` (4 rule + chống lặp)
- [ ] `MainActivity.kt` permission + schedule + deep link
- [ ] `ProgressFragment.kt` long-press force notify
- [ ] `AppDatabase` vẫn **v8** (không đổi schema)
- [ ] Test tay theo Step 10
- [ ] Compile: `./gradlew :app:compileDebugKotlin` OK

---

## Hoàn tất lộ trình

**Phase 0–14 đã xong — toàn bộ Functional Requirements trong plan `aistudy_next_steps` đã cover:**

| FR | Phase |
|----|-------|
| Auth + onboarding + profile | 0–2 |
| Ask text → Gemini → Answer + XP | 3–5 |
| Home dashboard + History | 5–7 |
| Difficulty + Languages + answer extras + XP review | 9 |
| Image upload questions | 10 |
| AI Practice/Quiz + instant feedback + XP | 11 |
| Progress: accuracy, time reviewing, AI insights, topic review | 12 |
| Gamification: level unlock avatar/theme, badges, leaderboard tuần | 13 |
| Notifications: bookmark / mistakes / motivational / summary + deep link | 14 |

Cấu hình Gradle cuối cùng của project Kotlin: ngoài `gradle/libs.versions.toml` và `app/build.gradle.kts` ở Step 1, file `build.gradle.kts` ở thư mục gốc khai báo `alias(libs.plugins.ksp) apply false` bên cạnh plugin Android application để module `app` apply được KSP cho Room.

**File:** `build.gradle.kts` (thư mục gốc, nội dung hiện tại)

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.ksp) apply false
}
```

**Không còn phase tiếp theo trong plan.** Việc mở rộng sau này (nếu muốn): sync cloud leaderboard, Gemini tóm tắt insights hằng ngày, avatar bằng ảnh thật, notification settings cho user tự bật/tắt từng loại.
