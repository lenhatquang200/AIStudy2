# Phase 13: Gamification đầy đủ — Hướng dẫn từng bước

Mục tiêu Phase 13:

1. **`XpRules`** — 1 chỗ duy nhất quản XP + level + streak + weekly XP
2. **Level → unlock** avatar (4) + theme (3), lưu `Profile.avatarId` / `Profile.themeId`
3. **Badges mở rộng** 6 cái: Starter, 5 stars, 3-day streak, Quiz 100%, Subject mastery, Scholar 1k
4. **Leaderboard local** theo XP tuần — entity `Friend` + `FriendSeeder` (Room **v8**)
5. **Theme apply** đổi màu background của `MainActivity` + Progress

**Điều kiện trước khi làm:** Phase 0–12 đã xong (QuizSession/QuizItem, `reviewedSeconds`, Progress v2, Room **v7**).

**Không làm ở Phase này:** Notifications / WorkManager (P14), sync cloud leaderboard, avatar bằng drawable ảnh thật (dùng emoji), Settings screen riêng (unlock nằm trong Progress).

**Lưu ý DB:** bump Room **v7 → v8** + `fallbackToDestructiveMigration(true)` → data cũ trên máy dev **bị xóa**. Bình thường cho giai đoạn này.

**Quy ước quan trọng của doc này:** một số query DAO chỉ dùng ở Phase 14 (notification) nhưng **được thêm sẵn ngay Phase 13** (`getOldestUnreviewedStarred`, `countUnreviewedStarredBefore`, `countCreatedSince`, `countCompletedSince`, `findSubjectWithRepeatedMistakes`). Làm vậy để không phải sửa DAO 2 lần và Phase 14 **không đụng** `database/`.

**Constants quan trọng (phải giữ đúng):**

| Constant | Value | Ý nghĩa |
|----------|-------|---------|
| `XpRules.ASK` | `50` | XP mỗi câu hỏi |
| `XpRules.REVIEW` | `10` | XP review answer (1 lần/ngày/QA) |
| `XpRules.QUIZ_CORRECT` | `10` | XP mỗi câu quiz đúng |
| `XpRules.QUIZ_COMPLETE_BONUS` | `20` | Bonus hoàn thành quiz |
| `XpRules.LEVEL_XP` | `intArrayOf(0, 100, 250, 500, 1000, 2000)` | Ngưỡng level 1→6 |
| `MASTERY_MIN` | `5` | Badge Subject mastery: 1 môn ≥5 QA |
| Badge 5 stars | `starred >= 5` | |
| Badge streak | `streak >= 3` | |
| Badge Quiz 100% | `countPerfectByUserId >= 1` | |
| Badge Scholar 1k | `xp >= 1000` | |
| `isNearNextLevel` | `remain in 1..50` | Dùng ở P14 motivational |

**Unlock catalog:**

| Loại | Id | Level cần | Hiển thị |
|------|----|-----------|----------|
| Avatar | `fox` | 1 | 🦊 |
| Avatar | `owl` | 2 | 🦉 |
| Avatar | `cat` | 3 | 🐱 |
| Avatar | `rocket` | 4 | 🚀 |
| Theme | `default` | 1 | `theme_default_bg` `#F9FAFC` |
| Theme | `night` | 2 | `theme_night_bg` `#1C1F28` |
| Theme | `pastel` | 3 | `theme_pastel_bg` `#F3E8FF` |

**Friend seed (mỗi user 1 lần):**

| Name | weeklyXp |
|------|----------|
| Alex | 80 |
| Sam | 120 |
| Jordan | 45 |
| Riley | 200 |

**Quy tắc streak:** `XpRules.recordDailyActivity` gọi khi **Ask** và khi **hoàn thành Quiz** — `lastActiveDay` = hôm nay → bỏ qua; = hôm qua → `streak + 1`; khác → `streak = 1`.

**Quy tắc weekly XP:** `weekStartMs` = 00:00 thứ Hai tuần hiện tại. `ensureWeek` phát hiện tuần mới → `weeklyXp = 0`.

---

## Tổng quan các file sẽ chạm

| File | Loại | Việc |
|------|------|------|
| `xp/XpRules.kt` | **Mới** | XP / level / weekly / streak |
| `xp/UnlockCatalog.kt` | **Mới** | Avatar + theme + level yêu cầu |
| `xp/FriendSeeder.kt` | **Mới** | Seed 4 rival |
| `database/Friend.kt` | **Mới** | Entity leaderboard |
| `database/FriendDao.kt` | **Mới** | count + list theo owner |
| `database/Profile.kt` | Sửa | `avatarId`, `themeId`, `weeklyXp`, `weekStartMs`, `lastActiveDay` |
| `database/ProfileDefaults.kt` | Sửa | Default avatar/theme/week |
| `database/AppDatabase.kt` | Sửa | `version = 8` + `Friend` + `friendDao()` |
| `database/QuestionAnswerDao.kt` | Sửa | `countStarredByUserId` + 3 query cho P14 |
| `database/QuizSessionDao.kt` | Sửa | `countPerfectByUserId`, `countCompletedSince` |
| `database/QuizItemDao.kt` | Sửa | `findSubjectWithRepeatedMistakes` (P14) |
| `res/values/colors.xml` | Sửa | 3 màu theme |
| `res/drawable/bg_unlock_chip.xml` | **Mới** | Chip chưa chọn |
| `res/drawable/bg_unlock_chip_selected.xml` | **Mới** | Chip đang chọn |
| `res/drawable/bg_leaderboard_row.xml` | **Mới** | Row leaderboard |
| `res/layout/item_leaderboard_row.xml` | **Mới** | Rank / name / XP |
| `res/values/strings_progress.xml` | Sửa | Badge mới + avatar/theme/leaderboard |
| `res/layout/fragment_progress.xml` | Sửa | Avatar + theme + leaderboard + 6 badge |
| `progress/ProgressFragment.kt` | Sửa | Bind unlock / leaderboard / badge / theme |
| `ask/AskFragment.kt` | Sửa | `XpRules.addXp` + `recordDailyActivity` |
| `answer/AnswerActivity.kt` | Sửa | `XpRules.addXp(REVIEW)` |
| `quiz/QuizActivity.kt` | Sửa | XP quiz theo `XpRules` |
| `home/HomeFragment.kt` | Sửa | Level/XP theo `XpRules` + theme bg |
| `MainActivity.kt` | Sửa | Seed friend + apply theme bg |

**Không đụng:** `GeminiClient`, `History`, Manifest, `build.gradle.kts` (WorkManager để P14).

---

## Step 1) `xp/XpRules.kt` — nguồn duy nhất của XP

**Giải thích:**  
Tất cả chỗ cộng XP đều đi qua `addXp` (cộng cả `xp` và `weeklyXp`, tự reset tuần). `levelFromXp` dò `LEVEL_XP` từ thấp lên cao. `recordDailyActivity` lo streak. `isNearNextLevel` / `streakAtRisk` / `todayKey` phục vụ Phase 14.

Chi tiết Kotlin trong file:

| Cú pháp | Ý nghĩa |
|---------|---------|
| `object XpRules` | Singleton, gọi thẳng `XpRules.addXp(...)` mà không cần tạo instance, không cần constructor private |
| `const val ASK = 50` | Hằng số compile-time, dùng được trong `when` và annotation |
| `val LEVEL_XP = intArrayOf(...)` | Mảng `IntArray` không thể là `const`, nên khai báo `val` |
| `LEVEL_XP.indices` / `LEVEL_XP.size` | Dải index `0..size-1` và độ dài mảng |
| `minOf` / `maxOf` | Lấy min / max của 2 số |
| `roundToInt()` | Làm tròn `Float` sang `Int` (cần `import kotlin.math.roundToInt`) |
| `if (...) 6 else ...` | `if` là biểu thức, trả về giá trị trực tiếp |
| `cal.timeInMillis` / `cal.time` | Đọc thời gian dạng property |
| `profile: Profile?` + `if (profile == null) return` | Tham số nullable; sau check null, Kotlin smart cast sang `Profile` |
| `today == profile.lastActiveDay` | `==` so sánh nội dung chuỗi và an toàn khi vế phải `null` |
| `remain in 1..50` | Còn từ 1 đến 50 XP |

**File:** `app/src/main/java/com/aistudy/aistudy/xp/XpRules.kt` (**mới**)

```kotlin
package com.aistudy.aistudy.xp

import com.aistudy.aistudy.database.Profile
import com.aistudy.aistudy.database.ProfileDao
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

object XpRules {

    const val ASK = 50
    const val REVIEW = 10
    const val QUIZ_CORRECT = 10
    const val QUIZ_COMPLETE_BONUS = 20

    val LEVEL_XP = intArrayOf(0, 100, 250, 500, 1000, 2000)

    fun levelFromXp(xp: Int): Int {
        var level = 1
        for (i in LEVEL_XP.indices) {
            if (xp >= LEVEL_XP[i]) {
                level = i + 1
            }
        }
        return level
    }

    fun nextLevelThreshold(xp: Int): Int {
        val level = levelFromXp(xp)
        if (level >= LEVEL_XP.size) {
            return LEVEL_XP[LEVEL_XP.size - 1]
        }
        return LEVEL_XP[level]
    }

    fun progressPercentToNext(xp: Int): Int {
        val level = levelFromXp(xp)
        if (level >= LEVEL_XP.size) {
            return 100
        }
        val start = LEVEL_XP[level - 1]
        val end = LEVEL_XP[level]
        val span = end - start
        if (span <= 0) {
            return 100
        }
        return minOf(100, ((xp - start) * 100f / span).roundToInt())
    }

    fun xpToNextLevel(xp: Int): Int {
        val next = nextLevelThreshold(xp)
        val remain = next - xp
        return maxOf(0, remain)
    }

    fun currentWeekStartMs(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val daysFromMonday = if (dow == Calendar.SUNDAY) 6 else dow - Calendar.MONDAY
        cal.add(Calendar.DAY_OF_MONTH, -daysFromMonday)
        return cal.timeInMillis
    }

    fun ensureWeek(profile: Profile?) {
        if (profile == null) {
            return
        }
        val weekStart = currentWeekStartMs()
        if (profile.weekStartMs != weekStart) {
            profile.weekStartMs = weekStart
            profile.weeklyXp = 0
        }
    }

    fun addXp(profile: Profile?, profileDao: ProfileDao?, amount: Int) {
        if (profile == null || profileDao == null || amount <= 0) {
            return
        }
        ensureWeek(profile)
        profile.xp += amount
        profile.weeklyXp += amount
        profileDao.update(profile)
    }

    fun recordDailyActivity(profile: Profile?, profileDao: ProfileDao?) {
        if (profile == null || profileDao == null) {
            return
        }
        val today = dayKey(0)
        if (today == profile.lastActiveDay) {
            return
        }
        val yesterday = dayKey(-1)
        if (yesterday == profile.lastActiveDay) {
            profile.streak = profile.streak + 1
        } else {
            profile.streak = 1
        }
        profile.lastActiveDay = today
        profileDao.update(profile)
    }

    fun isNearNextLevel(xp: Int): Boolean {
        val remain = xpToNextLevel(xp)
        return remain in 1..50
    }

    fun streakAtRisk(profile: Profile?): Boolean {
        if (profile == null || profile.streak <= 0) {
            return false
        }
        val today = dayKey(0)
        return profile.lastActiveDay != null &&
            today != profile.lastActiveDay &&
            dayKey(-1) == profile.lastActiveDay
    }

    private fun dayKey(dayOffset: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_MONTH, dayOffset)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }

    fun todayKey(): String {
        return dayKey(0)
    }
}
```

**Kiểm tra nhanh:**

| Input | `levelFromXp` | `progressPercentToNext` |
|-------|---------------|-------------------------|
| `0` | 1 | 0 |
| `50` | 1 | 50 |
| `100` | 2 | 0 |
| `2000` | 6 | 100 |

---

## Step 2) `xp/UnlockCatalog.kt` — avatar + theme theo level

**Giải thích:**  
Level cần = **index + 1** trong mảng → `fox` Lv1 … `rocket` Lv4; `default` Lv1 … `pastel` Lv3. `normalize*` chống null / id lạ trong DB.

Chi tiết Kotlin trong file:

| Cú pháp | Ý nghĩa |
|---------|---------|
| `object UnlockCatalog` + `const val` | Singleton chứa hằng số id avatar / theme |
| `val AVATARS = arrayOf(...)` | `Array<String>`, thứ tự phần tử quyết định level yêu cầu |
| `AVATARS.indexOf(avatarId)` | Trả về `-1` khi không tìm thấy (kể cả khi `avatarId` là `null`) → `requiredLevel*` trả `-1` |
| `when (avatarId) { AVATAR_OWL -> ... else -> ... }` | So khớp theo giá trị, nhánh `else` là mặc định (`🦊`, `Default`, `theme_default_bg`) |
| `avatarId.isNullOrEmpty()` | Gộp check `null` và chuỗi rỗng |
| `AVATARS.firstOrNull { it == avatarId } ?: AVATAR_FOX` | Tìm id hợp lệ, không có thì dùng giá trị mặc định qua toán tử Elvis `?:` |

**File:** `app/src/main/java/com/aistudy/aistudy/xp/UnlockCatalog.kt` (**mới**)

```kotlin
package com.aistudy.aistudy.xp

import com.aistudy.aistudy.R

object UnlockCatalog {

    const val AVATAR_FOX = "fox"
    const val AVATAR_OWL = "owl"
    const val AVATAR_CAT = "cat"
    const val AVATAR_ROCKET = "rocket"

    val AVATARS = arrayOf(
        AVATAR_FOX, AVATAR_OWL, AVATAR_CAT, AVATAR_ROCKET
    )

    const val THEME_DEFAULT = "default"
    const val THEME_NIGHT = "night"
    const val THEME_PASTEL = "pastel"

    val THEMES = arrayOf(
        THEME_DEFAULT, THEME_NIGHT, THEME_PASTEL
    )

    fun isAvatarUnlocked(avatarId: String?, level: Int): Boolean {
        val required = requiredLevelForAvatar(avatarId)
        return required > 0 && level >= required
    }

    fun isThemeUnlocked(themeId: String?, level: Int): Boolean {
        val required = requiredLevelForTheme(themeId)
        return required > 0 && level >= required
    }

    fun requiredLevelForAvatar(avatarId: String?): Int {
        val index = AVATARS.indexOf(avatarId)
        return if (index >= 0) index + 1 else -1
    }

    fun requiredLevelForTheme(themeId: String?): Int {
        val index = THEMES.indexOf(themeId)
        return if (index >= 0) index + 1 else -1
    }

    fun emojiForAvatar(avatarId: String?): String {
        return when (avatarId) {
            AVATAR_OWL -> "🦉"
            AVATAR_CAT -> "🐱"
            AVATAR_ROCKET -> "🚀"
            else -> "🦊"
        }
    }

    fun labelForTheme(themeId: String?): String {
        return when (themeId) {
            THEME_NIGHT -> "Night"
            THEME_PASTEL -> "Pastel"
            else -> "Default"
        }
    }

    fun backgroundColorRes(themeId: String?): Int {
        return when (themeId) {
            THEME_NIGHT -> R.color.theme_night_bg
            THEME_PASTEL -> R.color.theme_pastel_bg
            else -> R.color.theme_default_bg
        }
    }

    fun normalizeAvatarId(avatarId: String?): String {
        if (avatarId.isNullOrEmpty()) {
            return AVATAR_FOX
        }
        return AVATARS.firstOrNull { it == avatarId } ?: AVATAR_FOX
    }

    fun normalizeThemeId(themeId: String?): String {
        if (themeId.isNullOrEmpty()) {
            return THEME_DEFAULT
        }
        return THEMES.firstOrNull { it == themeId } ?: THEME_DEFAULT
    }
}
```

**Kiểm tra nhanh:** `backgroundColorRes` cần 3 color ở Step 9 — làm Step 9 trước khi build nếu IDE báo `Unresolved reference: theme_night_bg` (hoặc tương tự) trên `R.color`.

---

## Step 3) Entity `Profile` — 5 field gamification

**File:** `app/src/main/java/com/aistudy/aistudy/database/Profile.kt` (**thay toàn bộ**)

```kotlin
package com.aistudy.aistudy.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
class Profile {

    @PrimaryKey
    var userId: Long = 0

    var onboarded: Boolean = false
    var level: String? = null
    var subjects: String? = null
    var style: String? = null
    var xp: Int = 0
    var streak: Int = 0
    var avatarId: String? = null
    var themeId: String? = null
    var weeklyXp: Int = 0
    var weekStartMs: Long = 0
    var lastActiveDay: String? = null
}
```

| Field mới | Kiểu | Ý nghĩa |
|-----------|------|---------|
| `avatarId` | `String?` | `fox` / `owl` / `cat` / `rocket` |
| `themeId` | `String?` | `default` / `night` / `pastel` |
| `weeklyXp` | `Int` | XP trong tuần hiện tại |
| `weekStartMs` | `Long` | 00:00 thứ Hai của tuần đang tính |
| `lastActiveDay` | `String?` | `yyyy-MM-dd` lần cuối có hoạt động |

Mỗi field là `var` có giá trị mặc định để Room KSP tạo được entity qua constructor rỗng rồi gán từng property. Field `String?` cho phép `null` (Room tạo cột nullable); `Int` / `Long` không null, mặc định `0`.

**Lưu ý:** `level` (`String?`) là **trình độ học** từ onboarding (`junior`/`high`/`uni`) — **không phải** level XP. Level XP tính runtime bằng `XpRules.levelFromXp`.

---

## Step 4) `ProfileDefaults` — default cho profile mới

**File:** `app/src/main/java/com/aistudy/aistudy/database/ProfileDefaults.kt` (**thay toàn bộ**)

`ProfileDefaults` là `object` chứa các `const val`; `createDefault` gán thêm `avatarId`, `themeId`, `weeklyXp`, `weekStartMs`, `lastActiveDay`. Chuỗi `"$SUBJECT_MATH,$SUBJECT_SCIENCE"` dùng string template, kết quả là `math,science`.

```kotlin
package com.aistudy.aistudy.database

import com.aistudy.aistudy.xp.UnlockCatalog
import com.aistudy.aistudy.xp.XpRules

object ProfileDefaults {

    const val LEVEL_JUNIOR = "junior"
    const val LEVEL_HIGH = "high"
    const val LEVEL_UNI = "uni"

    const val STYLE_SHORT = "short"
    const val STYLE_STEPWISE = "stepwise"
    const val STYLE_DETAILED = "detailed"

    const val SUBJECT_MATH = "math"
    const val SUBJECT_SCIENCE = "science"
    const val SUBJECT_CODE = "code"
    const val SUBJECT_HISTORY = "history"
    const val SUBJECT_LANGUAGE = "language"
    const val SUBJECT_OTHER = "other"

    fun createDefault(userId: Long): Profile {
        val profile = Profile()
        profile.userId = userId
        profile.onboarded = false
        profile.level = LEVEL_HIGH
        profile.subjects = "$SUBJECT_MATH,$SUBJECT_SCIENCE"
        profile.style = STYLE_STEPWISE
        profile.xp = 0
        profile.streak = 0
        profile.avatarId = UnlockCatalog.AVATAR_FOX
        profile.themeId = UnlockCatalog.THEME_DEFAULT
        profile.weeklyXp = 0
        profile.weekStartMs = XpRules.currentWeekStartMs()
        profile.lastActiveDay = null
        return profile
    }

    fun ensureExists(profileDao: ProfileDao, userId: Long) {
        if (profileDao.getByUserId(userId) == null) {
            profileDao.insert(createDefault(userId))
        }
    }
}
```

---

## Step 5) Entity + Dao `Friend`

**Giải thích:**  
Leaderboard **local**: mỗi user có danh sách rival riêng (`ownerUserId`), sort `weeklyXp DESC`.

**File:** `app/src/main/java/com/aistudy/aistudy/database/Friend.kt` (**mới**)

```kotlin
package com.aistudy.aistudy.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "friends")
class Friend {

    @PrimaryKey(autoGenerate = true)
    var id: Long = 0

    var ownerUserId: Long = 0
    var displayName: String? = null
    var weeklyXp: Int = 0
}
```

**File:** `app/src/main/java/com/aistudy/aistudy/database/FriendDao.kt` (**mới**)

DAO là `interface` với các `fun`; Room KSP sinh `FriendDao_Impl` lúc build. `getByOwner` trả `List<Friend>` (không null, rỗng khi chưa có dữ liệu).

```kotlin
package com.aistudy.aistudy.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface FriendDao {

    @Insert
    fun insert(friend: Friend): Long

    @Query("SELECT COUNT(*) FROM friends WHERE ownerUserId = :ownerUserId")
    fun countByOwner(ownerUserId: Long): Int

    @Query("SELECT * FROM friends WHERE ownerUserId = :ownerUserId ORDER BY weeklyXp DESC")
    fun getByOwner(ownerUserId: Long): List<Friend>
}
```

---

## Step 6) `xp/FriendSeeder.kt` — seed 4 rival

**Giải thích:**  
Idempotent: `countByOwner > 0` → thoát. Gọi ở `MainActivity` và ở `ProgressFragment` (an toàn khi login user mới). Tham số `friendDao: FriendDao?` nhận được cả giá trị nullable; sau check `friendDao == null` Kotlin smart cast sang `FriendDao` nên gọi thẳng `friendDao.countByOwner(...)`.

**File:** `app/src/main/java/com/aistudy/aistudy/xp/FriendSeeder.kt` (**mới**)

```kotlin
package com.aistudy.aistudy.xp

import com.aistudy.aistudy.database.Friend
import com.aistudy.aistudy.database.FriendDao

object FriendSeeder {

    fun ensureSeeded(friendDao: FriendDao?, ownerUserId: Long) {
        if (friendDao == null || ownerUserId < 0) {
            return
        }
        if (friendDao.countByOwner(ownerUserId) > 0) {
            return
        }
        insert(friendDao, ownerUserId, "Alex", 80)
        insert(friendDao, ownerUserId, "Sam", 120)
        insert(friendDao, ownerUserId, "Jordan", 45)
        insert(friendDao, ownerUserId, "Riley", 200)
    }

    private fun insert(dao: FriendDao, ownerUserId: Long, name: String, weeklyXp: Int) {
        val friend = Friend()
        friend.ownerUserId = ownerUserId
        friend.displayName = name
        friend.weeklyXp = weeklyXp
        dao.insert(friend)
    }
}
```

**Kiểm tra nhanh:** user mới có 4 friend; user XP tuần 0 → đứng hạng 5.

---

## Step 7) `AppDatabase` — v8 + `Friend`

**File:** `app/src/main/java/com/aistudy/aistudy/database/AppDatabase.kt` (**thay toàn bộ**)

So với v7: thêm `Friend::class` vào `entities`, đổi `version = 8`, thêm `abstract fun friendDao(): FriendDao`. Phần `companion object` (`@Volatile` + `synchronized`) giữ nguyên.

```kotlin
package com.aistudy.aistudy.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        User::class,
        Profile::class,
        QuestionAnswer::class,
        QuizSession::class,
        QuizItem::class,
        Friend::class
    ],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao

    abstract fun profileDao(): ProfileDao

    abstract fun questionAnswerDao(): QuestionAnswerDao

    abstract fun quizSessionDao(): QuizSessionDao

    abstract fun quizItemDao(): QuizItemDao

    abstract fun friendDao(): FriendDao

    companion object {

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "aistudy.db"
                )
                    .allowMainThreadQueries()
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
```

**Kiểm tra nhanh:** Quên `version = 8` → crash `Room cannot verify the data integrity`.

---

## Step 8) DAO — count cho badge + query chuẩn bị cho Phase 14

**Giải thích:**  
`countStarredByUserId` cho badge **5 stars**, `countPerfectByUserId` cho badge **Quiz 100%**. 3 query cuối của `QuestionAnswerDao`, `countCompletedSince` và `findSubjectWithRepeatedMistakes` **chưa dùng ở P13** — thêm sẵn cho Phase 14 để Phase 14 không phải sửa `database/`.

Query trả 1 dòng dùng kiểu nullable (`QuestionAnswer?`, `String?`) vì có thể không có kết quả; query `COUNT(*)` trả `Int`; danh sách trả `List<...>`.

**File:** `app/src/main/java/com/aistudy/aistudy/database/QuestionAnswerDao.kt` (**thay toàn bộ**)

```kotlin
package com.aistudy.aistudy.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface QuestionAnswerDao {

    @Insert
    fun insert(qa: QuestionAnswer): Long

    @Update
    fun update(qa: QuestionAnswer)

    @Query("SELECT * FROM question_answers WHERE userId = :userId ORDER BY createdAt DESC")
    fun getAllByUserId(userId: Long): List<QuestionAnswer>

    @Query("SELECT * FROM question_answers WHERE userId = :userId ORDER BY createdAt DESC LIMIT 1")
    fun getLatestByUserId(userId: Long): QuestionAnswer?

    @Query("SELECT * FROM question_answers WHERE id = :id LIMIT 1")
    fun getById(id: Long): QuestionAnswer?

    @Query("UPDATE question_answers SET starred = NOT starred WHERE id = :id")
    fun toggleStar(id: Long)

    @Query("UPDATE question_answers SET extrasJson = :extrasJson WHERE id = :id")
    fun updateExtras(id: Long, extrasJson: String?)

    @Query("UPDATE question_answers SET reviewedAt = :reviewedAt WHERE id = :id")
    fun updateReviewedAt(id: Long, reviewedAt: Long)

    @Query("UPDATE question_answers SET reviewedSeconds = reviewedSeconds + :delta WHERE id = :id")
    fun addReviewedSeconds(id: Long, delta: Int)

    @Query("SELECT COALESCE(SUM(reviewedSeconds), 0) FROM question_answers WHERE userId = :userId")
    fun sumReviewedSecondsByUserId(userId: Long): Int

    @Query("SELECT subject FROM question_answers WHERE userId = :userId GROUP BY subject ORDER BY COUNT(*) DESC LIMIT 1")
    fun getTopSubjectByUserId(userId: Long): String?

    @Query("SELECT COUNT(*) FROM question_answers WHERE userId = :userId AND subject = :subject")
    fun countBySubject(userId: Long, subject: String): Int

    @Query("SELECT * FROM question_answers WHERE userId = :userId AND subject = :subject ORDER BY createdAt DESC LIMIT :limit")
    fun getRecentByUserIdAndSubject(userId: Long, subject: String, limit: Int): List<QuestionAnswer>

    @Query("SELECT COUNT(*) FROM question_answers WHERE userId = :userId AND starred = 1")
    fun countStarredByUserId(userId: Long): Int

    @Query("SELECT COUNT(*) FROM question_answers WHERE userId = :userId AND createdAt >= :fromMs")
    fun countCreatedSince(userId: Long, fromMs: Long): Int

    @Query("SELECT * FROM question_answers WHERE userId = :userId AND starred = 1 AND reviewedAt = 0 AND createdAt <= :beforeMs ORDER BY createdAt ASC LIMIT 1")
    fun getOldestUnreviewedStarred(userId: Long, beforeMs: Long): QuestionAnswer?

    @Query("SELECT COUNT(*) FROM question_answers WHERE userId = :userId AND starred = 1 AND reviewedAt = 0 AND createdAt <= :beforeMs")
    fun countUnreviewedStarredBefore(userId: Long, beforeMs: Long): Int
}
```

**File:** `app/src/main/java/com/aistudy/aistudy/database/QuizSessionDao.kt` (**thay toàn bộ**)

```kotlin
package com.aistudy.aistudy.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface QuizSessionDao {

    @Insert
    fun insert(session: QuizSession): Long

    @Update
    fun update(session: QuizSession)

    @Query("SELECT * FROM quiz_sessions WHERE id = :id LIMIT 1")
    fun getById(id: Long): QuizSession?

    @Query("SELECT * FROM quiz_sessions WHERE userId = :userId ORDER BY createdAt DESC")
    fun getAllByUserId(userId: Long): List<QuizSession>

    @Query("SELECT COUNT(*) FROM quiz_sessions WHERE userId = :userId AND completed = 1 AND score = total AND total > 0")
    fun countPerfectByUserId(userId: Long): Int

    @Query("SELECT COUNT(*) FROM quiz_sessions WHERE userId = :userId AND completed = 1 AND createdAt >= :fromMs")
    fun countCompletedSince(userId: Long, fromMs: Long): Int
}
```

**File:** `app/src/main/java/com/aistudy/aistudy/database/QuizItemDao.kt` (**thay toàn bộ**)

Các query dùng tên cột SQL `isCorrect` (property Kotlin `QuizItem.correct` được map vào cột này bằng `@ColumnInfo(name = "isCorrect")`), nên chuỗi SQL giữ nguyên `isCorrect`.

```kotlin
package com.aistudy.aistudy.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface QuizItemDao {

    @Insert
    fun insert(item: QuizItem): Long

    @Update
    fun update(item: QuizItem)

    @Query("SELECT * FROM quiz_items WHERE sessionId = :sessionId ORDER BY id ASC")
    fun getBySessionId(sessionId: Long): List<QuizItem>

    @Query("SELECT COUNT(*) FROM quiz_items WHERE sessionId IN (SELECT id FROM quiz_sessions WHERE userId = :userId) AND isCorrect IS NOT NULL")
    fun countAttemptedByUserId(userId: Long): Int

    @Query("SELECT COUNT(*) FROM quiz_items WHERE sessionId IN (SELECT id FROM quiz_sessions WHERE userId = :userId) AND isCorrect = 1")
    fun countCorrectByUserId(userId: Long): Int

    @Query("SELECT COUNT(*) FROM quiz_items WHERE sessionId IN (SELECT id FROM quiz_sessions WHERE userId = :userId AND subject = :subject) AND isCorrect IS NOT NULL")
    fun countAttemptedByUserIdAndSubject(userId: Long, subject: String): Int

    @Query("SELECT COUNT(*) FROM quiz_items WHERE sessionId IN (SELECT id FROM quiz_sessions WHERE userId = :userId AND subject = :subject) AND isCorrect = 1")
    fun countCorrectByUserIdAndSubject(userId: Long, subject: String): Int

    @Query("SELECT COUNT(*) FROM quiz_items WHERE sessionId IN (SELECT id FROM quiz_sessions WHERE userId = :userId AND subject = :subject AND createdAt >= :fromMs AND createdAt < :toMs) AND isCorrect IS NOT NULL")
    fun countAttemptedByUserIdSubjectAndRange(userId: Long, subject: String, fromMs: Long, toMs: Long): Int

    @Query("SELECT COUNT(*) FROM quiz_items WHERE sessionId IN (SELECT id FROM quiz_sessions WHERE userId = :userId AND subject = :subject AND createdAt >= :fromMs AND createdAt < :toMs) AND isCorrect = 1")
    fun countCorrectByUserIdSubjectAndRange(userId: Long, subject: String, fromMs: Long, toMs: Long): Int

    @Query("SELECT qs.subject FROM quiz_items qi INNER JOIN quiz_sessions qs ON qi.sessionId = qs.id WHERE qs.userId = :userId AND qi.isCorrect = 0 AND qs.createdAt >= :fromMs GROUP BY qs.subject HAVING COUNT(*) >= 2 LIMIT 1")
    fun findSubjectWithRepeatedMistakes(userId: Long, fromMs: Long): String?
}
```

| Query | Dùng ở |
|-------|--------|
| `countStarredByUserId` | P13 badge 5 stars + stat Stars |
| `countPerfectByUserId` | P13 badge Quiz 100% |
| `countCreatedSince` | P14 summary |
| `getOldestUnreviewedStarred` / `countUnreviewedStarredBefore` | P14 bookmark reminder |
| `countCompletedSince` | P14 (số quiz trong kỳ) |
| `findSubjectWithRepeatedMistakes` | P14 repeated mistakes |

---

## Step 9) `colors.xml` — 3 màu theme

**File:** `app/src/main/res/values/colors.xml` (**thay toàn bộ**)

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="black">#FF000000</color>
    <color name="white">#FFFFFFFF</color>

    <color name="auth_background">#F9FAFC</color>
    <color name="auth_surface">#F9FAFC</color>
    <color name="auth_shell_outer">#F4F4F5</color>
    <color name="auth_foreground">#1C1F28</color>
    <color name="auth_card">#F2F4F7</color>
    <color name="auth_muted_foreground">#6E7583</color>
    <color name="auth_brand">#3B9E74</color>
    <color name="auth_brand_soft">#1A3B9E74</color>
    <color name="auth_brand_ring">#4D3B9E74</color>
    <color name="auth_primary_foreground">#F8FDFA</color>
    <color name="auth_input_ring">#0D000000</color>
    <color name="auth_input_ring_focus">#663B9E74</color>
    <color name="auth_shell_ring">#0D000000</color>

    <color name="streak">#E67E22</color>
    <color name="streak_soft">#FDF0E4</color>
    <color name="xp_track">#E8EAED</color>

    <color name="theme_default_bg">#F9FAFC</color>
    <color name="theme_night_bg">#1C1F28</color>
    <color name="theme_pastel_bg">#F3E8FF</color>
</resources>
```

---

## Step 10) Drawables cho chip unlock + row leaderboard

**File:** `app/src/main/res/drawable/bg_unlock_chip.xml` (**mới**)

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="@color/auth_card" />
    <corners android:radius="12dp" />
    <stroke
        android:width="1dp"
        android:color="@color/auth_input_ring" />
</shape>
```

**File:** `app/src/main/res/drawable/bg_unlock_chip_selected.xml` (**mới**)

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="@color/auth_brand_soft" />
    <corners android:radius="12dp" />
    <stroke
        android:width="2dp"
        android:color="@color/auth_brand" />
</shape>
```

**File:** `app/src/main/res/drawable/bg_leaderboard_row.xml` (**mới**)

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="@color/auth_surface" />
    <corners android:radius="12dp" />
    <stroke
        android:width="1dp"
        android:color="@color/auth_input_ring" />
</shape>
```

---

## Step 11) `item_leaderboard_row.xml`

**Giải thích:** Inflate trong code (`ProgressFragment.bindLeaderboard`), không dùng RecyclerView vì tối đa 5 row.

**File:** `app/src/main/res/layout/item_leaderboard_row.xml` (**mới**)

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginTop="8dp"
    android:background="@drawable/bg_leaderboard_row"
    android:gravity="center_vertical"
    android:orientation="horizontal"
    android:paddingHorizontal="14dp"
    android:paddingVertical="12dp">

    <TextView
        android:id="@+id/tv_rank"
        android:layout_width="28dp"
        android:layout_height="wrap_content"
        android:gravity="center"
        android:textColor="@color/auth_muted_foreground"
        android:textSize="14sp"
        android:textStyle="bold" />

    <TextView
        android:id="@+id/tv_name"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_marginStart="8dp"
        android:layout_weight="1"
        android:ellipsize="end"
        android:maxLines="1"
        android:textColor="@color/auth_foreground"
        android:textSize="14sp"
        android:textStyle="bold" />

    <TextView
        android:id="@+id/tv_weekly_xp"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:textColor="@color/auth_brand"
        android:textSize="13sp"
        android:textStyle="bold" />
</LinearLayout>
```

---

## Step 12) `strings_progress.xml`

**Giải thích:**  
Badge cũ `progress_badge_week` / `progress_badge_math` (P7–P12) bị thay bằng `stars` / `streak` / `perfect` / `mastery`. Thêm strings avatar/theme/leaderboard/unlock. `progress_force_notify` **chỉ dùng ở Phase 14** — khai báo sẵn ở đây.

**File:** `app/src/main/res/values/strings_progress.xml` (**thay toàn bộ**)

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="progress_title">Learning progress</string>
    <string name="progress_logout">Log out</string>
    <string name="progress_xp_label">Total XP</string>
    <string name="progress_level_scholar">Level %1$d • Scholar</string>
    <string name="progress_xp_percent">%d%%</string>
    <string name="progress_next_level">to next level</string>
    <string name="progress_stat_streak">Streak</string>
    <string name="progress_stat_questions">Questions</string>
    <string name="progress_stat_stars">Stars</string>
    <string name="progress_streak_value">%dd</string>
    <string name="progress_subjects_title">Subject breakdown</string>
    <string name="progress_subjects_empty">No data yet — ask your first question!</string>
    <string name="progress_badges_title">Badges</string>
    <string name="progress_badge_starter">Starter</string>
    <string name="progress_badge_stars">5 stars</string>
    <string name="progress_badge_streak">3-day streak</string>
    <string name="progress_badge_perfect">Quiz 100%%</string>
    <string name="progress_badge_mastery">Subject mastery</string>
    <string name="progress_badge_scholar">Scholar 1k</string>
    <string name="progress_metrics_title">Learning metrics</string>
    <string name="progress_quiz_accuracy_label">Quiz accuracy</string>
    <string name="progress_quiz_accuracy_value">%1$d%% · %2$d/%3$d</string>
    <string name="progress_quiz_accuracy_empty">No quiz attempts yet</string>
    <string name="progress_review_time_label">Time reviewing</string>
    <string name="progress_review_time_seconds">%d sec</string>
    <string name="progress_review_time_minutes">%d min</string>
    <string name="progress_review_time_hours">%1$dh %2$dm</string>
    <string name="progress_insights_title">AI insights</string>
    <string name="progress_insight_top_subject">You frequently ask questions about %1$s.</string>
    <string name="progress_insight_accuracy_improved">Your accuracy in %1$s has improved this month.</string>
    <string name="progress_insights_empty">Ask questions and complete quizzes to unlock insights.</string>
    <string name="progress_review_title">Suggested review</string>
    <string name="progress_review_subtitle">Review: %1$s</string>
    <string name="progress_review_cta">Practice now</string>
    <string name="progress_avatar_title">Avatar</string>
    <string name="progress_theme_title">Theme</string>
    <string name="progress_leaderboard_title">Weekly leaderboard</string>
    <string name="progress_leaderboard_you">You</string>
    <string name="progress_leaderboard_xp">%d XP</string>
    <string name="progress_unlock_locked">Lv %d</string>
    <string name="progress_unlock_toast">Reach level %1$d to unlock</string>
    <string name="progress_theme_applied">Theme applied</string>
    <string name="progress_force_notify">Checking learning reminders…</string>
</resources>
```

**Kiểm tra nhanh:** `Quiz 100%%` phải escape `%%`, không thì aapt lỗi format.

---

## Step 13) `fragment_progress.xml`

**Giải thích những chỗ mới so với P12:**

| Thay đổi | Chi tiết |
|----------|----------|
| Title có id | `@+id/tv_progress_title` (P14 dùng long-press) |
| Avatar section | Title `progress_avatar_title` + `@+id/row_avatars` (rỗng, code fill) |
| Theme section | Title `progress_theme_title` + `@+id/row_themes` |
| Leaderboard section | Title `progress_leaderboard_title` + `@+id/layout_leaderboard` |
| Badges | 2 hàng × 3: `badge_starter` / `badge_stars` / `badge_streak` — `badge_perfect` / `badge_mastery` / `badge_scholar` |

**File:** `app/src/main/res/layout/fragment_progress.xml` (**thay toàn bộ**)

```xml
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/auth_surface"
    android:clipToPadding="false"
    android:fillViewport="true">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:paddingHorizontal="@dimen/auth_screen_padding_horizontal"
        android:paddingTop="28dp"
        android:paddingBottom="24dp">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:gravity="top"
            android:orientation="horizontal">

            <LinearLayout
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:orientation="vertical">

                <TextView
                    android:id="@+id/tv_progress_title"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/progress_title"
                    android:textColor="@color/auth_foreground"
                    android:textSize="24sp"
                    android:textStyle="bold" />

                <TextView
                    android:id="@+id/tv_user_email"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="4dp"
                    android:ellipsize="end"
                    android:maxLines="1"
                    android:textColor="@color/auth_muted_foreground"
                    android:textSize="13sp" />
            </LinearLayout>

            <TextView
                android:id="@+id/btn_logout"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginStart="12dp"
                android:background="@drawable/bg_logout_pill"
                android:paddingHorizontal="12dp"
                android:paddingVertical="8dp"
                android:text="@string/progress_logout"
                android:textColor="@color/auth_muted_foreground"
                android:textSize="12sp"
                android:textStyle="bold" />
        </LinearLayout>

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="24dp"
            android:background="@drawable/bg_progress_xp_card"
            android:orientation="vertical"
            android:padding="20dp">

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/progress_xp_label"
                android:textAllCaps="true"
                android:textColor="#99FFFFFF"
                android:textSize="10sp"
                android:textStyle="bold" />

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="12dp"
                android:gravity="bottom"
                android:orientation="horizontal">

                <LinearLayout
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:orientation="vertical">

                    <TextView
                        android:id="@+id/tv_xp_total"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:textColor="@color/white"
                        android:textSize="36sp"
                        android:textStyle="bold" />

                    <TextView
                        android:id="@+id/tv_level"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="4dp"
                        android:textColor="#99FFFFFF"
                        android:textSize="12sp" />
                </LinearLayout>

                <LinearLayout
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:gravity="end"
                    android:orientation="vertical">

                    <TextView
                        android:id="@+id/tv_xp_percent"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:textColor="@color/auth_brand"
                        android:textSize="20sp"
                        android:textStyle="bold" />

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="2dp"
                        android:text="@string/progress_next_level"
                        android:textColor="#99FFFFFF"
                        android:textSize="12sp" />
                </LinearLayout>
            </LinearLayout>

            <ProgressBar
                android:id="@+id/progress_xp"
                style="?android:attr/progressBarStyleHorizontal"
                android:layout_width="match_parent"
                android:layout_height="6dp"
                android:layout_marginTop="16dp"
                android:max="100"
                android:progressDrawable="@drawable/bg_progress_xp_bar" />
        </LinearLayout>

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:orientation="horizontal">

            <LinearLayout
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:background="@drawable/bg_home_card"
                android:orientation="vertical"
                android:padding="16dp">

                <TextView
                    android:id="@+id/tv_stat_streak"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:textColor="@color/auth_foreground"
                    android:textSize="18sp"
                    android:textStyle="bold" />

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="4dp"
                    android:text="@string/progress_stat_streak"
                    android:textColor="@color/auth_muted_foreground"
                    android:textSize="11sp" />
            </LinearLayout>

            <LinearLayout
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_marginStart="12dp"
                android:layout_weight="1"
                android:background="@drawable/bg_home_card"
                android:orientation="vertical"
                android:padding="16dp">

                <TextView
                    android:id="@+id/tv_stat_questions"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:textColor="@color/auth_foreground"
                    android:textSize="18sp"
                    android:textStyle="bold" />

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="4dp"
                    android:text="@string/progress_stat_questions"
                    android:textColor="@color/auth_muted_foreground"
                    android:textSize="11sp" />
            </LinearLayout>

            <LinearLayout
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_marginStart="12dp"
                android:layout_weight="1"
                android:background="@drawable/bg_home_card"
                android:orientation="vertical"
                android:padding="16dp">

                <TextView
                    android:id="@+id/tv_stat_stars"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:textColor="@color/auth_foreground"
                    android:textSize="18sp"
                    android:textStyle="bold" />

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="4dp"
                    android:text="@string/progress_stat_stars"
                    android:textColor="@color/auth_muted_foreground"
                    android:textSize="11sp" />
            </LinearLayout>
        </LinearLayout>

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="24dp"
            android:background="@drawable/bg_home_card"
            android:orientation="vertical"
            android:padding="20dp">

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/progress_metrics_title"
                android:textAllCaps="true"
                android:textColor="@color/auth_muted_foreground"
                android:textSize="11sp"
                android:textStyle="bold" />

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="16dp"
                android:orientation="horizontal">

                <LinearLayout
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:orientation="vertical">

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@string/progress_quiz_accuracy_label"
                        android:textColor="@color/auth_muted_foreground"
                        android:textSize="11sp" />

                    <TextView
                        android:id="@+id/tv_quiz_accuracy"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="6dp"
                        android:textColor="@color/auth_foreground"
                        android:textSize="16sp"
                        android:textStyle="bold" />
                </LinearLayout>

                <LinearLayout
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_marginStart="12dp"
                    android:layout_weight="1"
                    android:orientation="vertical">

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@string/progress_review_time_label"
                        android:textColor="@color/auth_muted_foreground"
                        android:textSize="11sp" />

                    <TextView
                        android:id="@+id/tv_review_time"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="6dp"
                        android:textColor="@color/auth_foreground"
                        android:textSize="16sp"
                        android:textStyle="bold" />
                </LinearLayout>
            </LinearLayout>
        </LinearLayout>

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:background="@drawable/bg_home_card"
            android:orientation="vertical"
            android:padding="20dp">

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/progress_insights_title"
                android:textAllCaps="true"
                android:textColor="@color/auth_muted_foreground"
                android:textSize="11sp"
                android:textStyle="bold" />

            <TextView
                android:id="@+id/tv_insight_1"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="12dp"
                android:textColor="@color/auth_foreground"
                android:textSize="14sp"
                android:visibility="gone" />

            <TextView
                android:id="@+id/tv_insight_2"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="8dp"
                android:textColor="@color/auth_foreground"
                android:textSize="14sp"
                android:visibility="gone" />

            <TextView
                android:id="@+id/tv_insights_empty"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="12dp"
                android:text="@string/progress_insights_empty"
                android:textColor="@color/auth_muted_foreground"
                android:textSize="14sp"
                android:visibility="gone" />
        </LinearLayout>

        <LinearLayout
            android:id="@+id/card_review_suggest"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:background="@drawable/bg_home_card"
            android:clickable="true"
            android:focusable="true"
            android:orientation="vertical"
            android:padding="20dp"
            android:visibility="gone">

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/progress_review_title"
                android:textAllCaps="true"
                android:textColor="@color/auth_muted_foreground"
                android:textSize="11sp"
                android:textStyle="bold" />

            <TextView
                android:id="@+id/tv_review_subtitle"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="10dp"
                android:textColor="@color/auth_foreground"
                android:textSize="16sp"
                android:textStyle="bold" />

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="8dp"
                android:text="@string/progress_review_cta"
                android:textColor="@color/auth_brand"
                android:textSize="13sp"
                android:textStyle="bold" />
        </LinearLayout>

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="24dp"
            android:background="@drawable/bg_home_card"
            android:orientation="vertical"
            android:padding="20dp">

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/progress_subjects_title"
                android:textAllCaps="true"
                android:textColor="@color/auth_muted_foreground"
                android:textSize="11sp"
                android:textStyle="bold" />

            <LinearLayout
                android:id="@+id/layout_subjects"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="12dp"
                android:orientation="vertical">

                <LinearLayout
                    android:id="@+id/row_math"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginBottom="12dp"
                    android:orientation="vertical"
                    android:visibility="gone">

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="horizontal">

                        <TextView
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1"
                            android:text="@string/subject_math"
                            android:textColor="@color/auth_foreground"
                            android:textSize="12sp"
                            android:textStyle="bold" />

                        <TextView
                            android:id="@+id/tv_count_math"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:textColor="@color/auth_muted_foreground"
                            android:textSize="12sp" />
                    </LinearLayout>

                    <ProgressBar
                        android:id="@+id/bar_math"
                        style="?android:attr/progressBarStyleHorizontal"
                        android:layout_width="match_parent"
                        android:layout_height="6dp"
                        android:layout_marginTop="6dp"
                        android:max="100"
                        android:progressDrawable="@drawable/bg_progress_subject_bar" />
                </LinearLayout>

                <LinearLayout
                    android:id="@+id/row_science"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginBottom="12dp"
                    android:orientation="vertical"
                    android:visibility="gone">

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="horizontal">

                        <TextView
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1"
                            android:text="@string/subject_science"
                            android:textColor="@color/auth_foreground"
                            android:textSize="12sp"
                            android:textStyle="bold" />

                        <TextView
                            android:id="@+id/tv_count_science"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:textColor="@color/auth_muted_foreground"
                            android:textSize="12sp" />
                    </LinearLayout>

                    <ProgressBar
                        android:id="@+id/bar_science"
                        style="?android:attr/progressBarStyleHorizontal"
                        android:layout_width="match_parent"
                        android:layout_height="6dp"
                        android:layout_marginTop="6dp"
                        android:max="100"
                        android:progressDrawable="@drawable/bg_progress_subject_bar" />
                </LinearLayout>

                <LinearLayout
                    android:id="@+id/row_code"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginBottom="12dp"
                    android:orientation="vertical"
                    android:visibility="gone">

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="horizontal">

                        <TextView
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1"
                            android:text="@string/subject_code"
                            android:textColor="@color/auth_foreground"
                            android:textSize="12sp"
                            android:textStyle="bold" />

                        <TextView
                            android:id="@+id/tv_count_code"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:textColor="@color/auth_muted_foreground"
                            android:textSize="12sp" />
                    </LinearLayout>

                    <ProgressBar
                        android:id="@+id/bar_code"
                        style="?android:attr/progressBarStyleHorizontal"
                        android:layout_width="match_parent"
                        android:layout_height="6dp"
                        android:layout_marginTop="6dp"
                        android:max="100"
                        android:progressDrawable="@drawable/bg_progress_subject_bar" />
                </LinearLayout>

                <LinearLayout
                    android:id="@+id/row_history"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginBottom="12dp"
                    android:orientation="vertical"
                    android:visibility="gone">

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="horizontal">

                        <TextView
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1"
                            android:text="@string/subject_history"
                            android:textColor="@color/auth_foreground"
                            android:textSize="12sp"
                            android:textStyle="bold" />

                        <TextView
                            android:id="@+id/tv_count_history"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:textColor="@color/auth_muted_foreground"
                            android:textSize="12sp" />
                    </LinearLayout>

                    <ProgressBar
                        android:id="@+id/bar_history"
                        style="?android:attr/progressBarStyleHorizontal"
                        android:layout_width="match_parent"
                        android:layout_height="6dp"
                        android:layout_marginTop="6dp"
                        android:max="100"
                        android:progressDrawable="@drawable/bg_progress_subject_bar" />
                </LinearLayout>

                <LinearLayout
                    android:id="@+id/row_language"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    android:visibility="gone">

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="horizontal">

                        <TextView
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1"
                            android:text="@string/subject_language"
                            android:textColor="@color/auth_foreground"
                            android:textSize="12sp"
                            android:textStyle="bold" />

                        <TextView
                            android:id="@+id/tv_count_language"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:textColor="@color/auth_muted_foreground"
                            android:textSize="12sp" />
                    </LinearLayout>

                    <ProgressBar
                        android:id="@+id/bar_language"
                        style="?android:attr/progressBarStyleHorizontal"
                        android:layout_width="match_parent"
                        android:layout_height="6dp"
                        android:layout_marginTop="6dp"
                        android:max="100"
                        android:progressDrawable="@drawable/bg_progress_subject_bar" />
                </LinearLayout>
            </LinearLayout>

            <TextView
                android:id="@+id/tv_subjects_empty"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="4dp"
                android:text="@string/progress_subjects_empty"
                android:textColor="@color/auth_muted_foreground"
                android:textSize="14sp"
                android:visibility="gone" />
        </LinearLayout>

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="24dp"
            android:text="@string/progress_avatar_title"
            android:textAllCaps="true"
            android:textColor="@color/auth_muted_foreground"
            android:textSize="11sp"
            android:textStyle="bold" />

        <LinearLayout
            android:id="@+id/row_avatars"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="12dp"
            android:orientation="horizontal" />

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="24dp"
            android:text="@string/progress_theme_title"
            android:textAllCaps="true"
            android:textColor="@color/auth_muted_foreground"
            android:textSize="11sp"
            android:textStyle="bold" />

        <LinearLayout
            android:id="@+id/row_themes"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="12dp"
            android:orientation="horizontal" />

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="24dp"
            android:text="@string/progress_leaderboard_title"
            android:textAllCaps="true"
            android:textColor="@color/auth_muted_foreground"
            android:textSize="11sp"
            android:textStyle="bold" />

        <LinearLayout
            android:id="@+id/layout_leaderboard"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="4dp"
            android:orientation="vertical" />

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="24dp"
            android:text="@string/progress_badges_title"
            android:textAllCaps="true"
            android:textColor="@color/auth_muted_foreground"
            android:textSize="11sp"
            android:textStyle="bold" />

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="12dp"
            android:orientation="horizontal">

            <LinearLayout
                android:id="@+id/badge_starter"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:background="@drawable/bg_badge_locked"
                android:gravity="center"
                android:orientation="vertical"
                android:padding="10dp">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="🏅"
                    android:textSize="18sp" />

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="6dp"
                    android:gravity="center"
                    android:text="@string/progress_badge_starter"
                    android:textColor="@color/auth_foreground"
                    android:textSize="10sp"
                    android:textStyle="bold" />
            </LinearLayout>

            <LinearLayout
                android:id="@+id/badge_stars"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_marginStart="8dp"
                android:layout_weight="1"
                android:background="@drawable/bg_badge_locked"
                android:gravity="center"
                android:orientation="vertical"
                android:padding="10dp">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="⭐"
                    android:textSize="18sp" />

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="6dp"
                    android:gravity="center"
                    android:text="@string/progress_badge_stars"
                    android:textColor="@color/auth_foreground"
                    android:textSize="10sp"
                    android:textStyle="bold" />
            </LinearLayout>

            <LinearLayout
                android:id="@+id/badge_streak"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_marginStart="8dp"
                android:layout_weight="1"
                android:background="@drawable/bg_badge_locked"
                android:gravity="center"
                android:orientation="vertical"
                android:padding="10dp">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="🔥"
                    android:textSize="18sp" />

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="6dp"
                    android:gravity="center"
                    android:text="@string/progress_badge_streak"
                    android:textColor="@color/auth_foreground"
                    android:textSize="10sp"
                    android:textStyle="bold" />
            </LinearLayout>
        </LinearLayout>

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:orientation="horizontal">

            <LinearLayout
                android:id="@+id/badge_perfect"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:background="@drawable/bg_badge_locked"
                android:gravity="center"
                android:orientation="vertical"
                android:padding="10dp">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="💯"
                    android:textSize="18sp" />

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="6dp"
                    android:gravity="center"
                    android:text="@string/progress_badge_perfect"
                    android:textColor="@color/auth_foreground"
                    android:textSize="10sp"
                    android:textStyle="bold" />
            </LinearLayout>

            <LinearLayout
                android:id="@+id/badge_mastery"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_marginStart="8dp"
                android:layout_weight="1"
                android:background="@drawable/bg_badge_locked"
                android:gravity="center"
                android:orientation="vertical"
                android:padding="10dp">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="📚"
                    android:textSize="18sp" />

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="6dp"
                    android:gravity="center"
                    android:text="@string/progress_badge_mastery"
                    android:textColor="@color/auth_foreground"
                    android:textSize="10sp"
                    android:textStyle="bold" />
            </LinearLayout>

            <LinearLayout
                android:id="@+id/badge_scholar"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_marginStart="8dp"
                android:layout_weight="1"
                android:background="@drawable/bg_badge_locked"
                android:gravity="center"
                android:orientation="vertical"
                android:padding="10dp">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="🎓"
                    android:textSize="18sp" />

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="6dp"
                    android:gravity="center"
                    android:text="@string/progress_badge_scholar"
                    android:textColor="@color/auth_foreground"
                    android:textSize="10sp"
                    android:textStyle="bold" />
            </LinearLayout>
        </LinearLayout>
    </LinearLayout>
</ScrollView>
```

---

## Step 14) `ProgressFragment` — unlock + leaderboard + badge + theme

**Giải thích:**

| Hàm | Việc |
|-----|------|
| `bindAvatars` / `bindThemes` | Tạo chip động: locked → text `Lv N` + alpha `0.45` |
| `onAvatarClicked` / `onThemeClicked` | Chưa unlock → Toast; unlock → lưu `Profile` + re-bind |
| `applyThemeBackground` | Set background của root Progress |
| `bindLeaderboard` | User + friends → `sortByDescending { it.weeklyXp }` → inflate row |
| `setBadge` | `bg_badge_earned` / `bg_badge_locked` + alpha |

Level dùng cho unlock là `currentLevel = XpRules.levelFromXp(xp)`.

Chi tiết Kotlin trong file:

| Cú pháp | Ý nghĩa |
|---------|---------|
| `private lateinit var rowAvatars: LinearLayout` | View gán trong `onViewCreated` bằng `findViewById`; truy cập trước khi gán sẽ crash `UninitializedPropertyAccessException` |
| `private var profileDao: ProfileDao? = null` | DAO / profile có thể chưa có khi fragment chưa load, nên để nullable |
| `val profile = currentProfile` rồi check `profile == null` | Copy property `var` sang biến local `val` để Kotlin smart cast được (smart cast không áp dụng trực tiếp cho `var` của class) |
| `UnlockCatalog.AVATARS.forEachIndexed { i, avatarId -> ... }` | Lặp có index; `i > 0` quyết định chip có margin trái |
| `chip.setOnClickListener { onAvatarClicked(avatarId) }` | Lambda xử lý click |
| `lp.marginStart = dp(8)` / `chip.alpha = 0.45f` | Gán dạng property |
| `activity?.findViewById<View>(R.id.main)?.setBackgroundResource(...)` | Safe call: không làm gì khi fragment đã tách khỏi activity |
| `rows.sortByDescending { it.weeklyXp }` | Sort tại chỗ theo XP tuần giảm dần |
| `"${getString(R.string.progress_leaderboard_you)} (${row.name})"` | String template, kết quả `You (Full Name)` |
| `maxOf(countMath, countScience, countCode, countHistory, countLanguage)` | `maxOf` nhận nhiều tham số, lấy số QA lớn nhất của 1 môn |
| `private class LeaderboardRow(val name: String?, val weeklyXp: Int, val isSelf: Boolean)` | Class nhỏ với property khai báo ngay trong constructor |

**File:** `app/src/main/java/com/aistudy/aistudy/progress/ProgressFragment.kt` (**thay toàn bộ**)

> Bản Phase 13 dưới đây **chưa có** long-press notify (`import com.aistudy.aistudy.notify.NotificationScheduler` + listener `setOnLongClickListener` trên `tv_progress_title`) — phần đó thêm ở **Phase 14 Step 8**, ngay sau dòng `btnLogout.setOnClickListener { logout() }`.

```kotlin
package com.aistudy.aistudy.progress

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.aistudy.aistudy.R
import com.aistudy.aistudy.auth.LoginActivity
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.FriendDao
import com.aistudy.aistudy.database.Profile
import com.aistudy.aistudy.database.ProfileDao
import com.aistudy.aistudy.database.QuestionAnswerDao
import com.aistudy.aistudy.database.QuizItemDao
import com.aistudy.aistudy.quiz.QuizActivity
import com.aistudy.aistudy.xp.FriendSeeder
import com.aistudy.aistudy.xp.UnlockCatalog
import com.aistudy.aistudy.xp.XpRules
import kotlin.math.roundToInt

class ProgressFragment : Fragment() {

    companion object {
        private const val MIN_PRACTICE_QA = 2
        private const val MASTERY_MIN = 5
        private const val DAY_MS = 24L * 60L * 60L * 1000L
        private const val WINDOW_MS = 30L * DAY_MS
        private val SUBJECTS = arrayOf(
            "math", "science", "code", "history", "language"
        )
    }

    private lateinit var tvUserEmail: TextView
    private lateinit var btnLogout: TextView
    private lateinit var tvXpTotal: TextView
    private lateinit var tvLevel: TextView
    private lateinit var tvXpPercent: TextView
    private lateinit var progressXp: ProgressBar
    private lateinit var tvStatStreak: TextView
    private lateinit var tvStatQuestions: TextView
    private lateinit var tvStatStars: TextView

    private lateinit var tvQuizAccuracy: TextView
    private lateinit var tvReviewTime: TextView
    private lateinit var tvInsight1: TextView
    private lateinit var tvInsight2: TextView
    private lateinit var tvInsightsEmpty: TextView
    private lateinit var cardReviewSuggest: LinearLayout
    private lateinit var tvReviewSubtitle: TextView

    private lateinit var rowAvatars: LinearLayout
    private lateinit var rowThemes: LinearLayout
    private lateinit var layoutLeaderboard: LinearLayout

    private lateinit var rowMath: LinearLayout
    private lateinit var rowScience: LinearLayout
    private lateinit var rowCode: LinearLayout
    private lateinit var rowHistory: LinearLayout
    private lateinit var rowLanguage: LinearLayout
    private lateinit var tvCountMath: TextView
    private lateinit var tvCountScience: TextView
    private lateinit var tvCountCode: TextView
    private lateinit var tvCountHistory: TextView
    private lateinit var tvCountLanguage: TextView
    private lateinit var barMath: ProgressBar
    private lateinit var barScience: ProgressBar
    private lateinit var barCode: ProgressBar
    private lateinit var barHistory: ProgressBar
    private lateinit var barLanguage: ProgressBar
    private lateinit var tvSubjectsEmpty: TextView

    private lateinit var badgeStarter: LinearLayout
    private lateinit var badgeStars: LinearLayout
    private lateinit var badgeStreak: LinearLayout
    private lateinit var badgePerfect: LinearLayout
    private lateinit var badgeMastery: LinearLayout
    private lateinit var badgeScholar: LinearLayout

    private var qaDao: QuestionAnswerDao? = null
    private var profileDao: ProfileDao? = null
    private var friendDao: FriendDao? = null
    private var currentUserId = -1L
    private var reviewSubject: String? = null
    private var currentProfile: Profile? = null
    private var currentLevel = 1

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_progress, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tvUserEmail = view.findViewById(R.id.tv_user_email)
        btnLogout = view.findViewById(R.id.btn_logout)
        tvXpTotal = view.findViewById(R.id.tv_xp_total)
        tvLevel = view.findViewById(R.id.tv_level)
        tvXpPercent = view.findViewById(R.id.tv_xp_percent)
        progressXp = view.findViewById(R.id.progress_xp)
        tvStatStreak = view.findViewById(R.id.tv_stat_streak)
        tvStatQuestions = view.findViewById(R.id.tv_stat_questions)
        tvStatStars = view.findViewById(R.id.tv_stat_stars)

        tvQuizAccuracy = view.findViewById(R.id.tv_quiz_accuracy)
        tvReviewTime = view.findViewById(R.id.tv_review_time)
        tvInsight1 = view.findViewById(R.id.tv_insight_1)
        tvInsight2 = view.findViewById(R.id.tv_insight_2)
        tvInsightsEmpty = view.findViewById(R.id.tv_insights_empty)
        cardReviewSuggest = view.findViewById(R.id.card_review_suggest)
        tvReviewSubtitle = view.findViewById(R.id.tv_review_subtitle)

        rowAvatars = view.findViewById(R.id.row_avatars)
        rowThemes = view.findViewById(R.id.row_themes)
        layoutLeaderboard = view.findViewById(R.id.layout_leaderboard)

        rowMath = view.findViewById(R.id.row_math)
        rowScience = view.findViewById(R.id.row_science)
        rowCode = view.findViewById(R.id.row_code)
        rowHistory = view.findViewById(R.id.row_history)
        rowLanguage = view.findViewById(R.id.row_language)
        tvCountMath = view.findViewById(R.id.tv_count_math)
        tvCountScience = view.findViewById(R.id.tv_count_science)
        tvCountCode = view.findViewById(R.id.tv_count_code)
        tvCountHistory = view.findViewById(R.id.tv_count_history)
        tvCountLanguage = view.findViewById(R.id.tv_count_language)
        barMath = view.findViewById(R.id.bar_math)
        barScience = view.findViewById(R.id.bar_science)
        barCode = view.findViewById(R.id.bar_code)
        barHistory = view.findViewById(R.id.bar_history)
        barLanguage = view.findViewById(R.id.bar_language)
        tvSubjectsEmpty = view.findViewById(R.id.tv_subjects_empty)

        badgeStarter = view.findViewById(R.id.badge_starter)
        badgeStars = view.findViewById(R.id.badge_stars)
        badgeStreak = view.findViewById(R.id.badge_streak)
        badgePerfect = view.findViewById(R.id.badge_perfect)
        badgeMastery = view.findViewById(R.id.badge_mastery)
        badgeScholar = view.findViewById(R.id.badge_scholar)

        btnLogout.setOnClickListener { logout() }
        cardReviewSuggest.setOnClickListener {
            reviewSubject?.let { openPractice(it) }
        }
    }

    override fun onResume() {
        super.onResume()
        loadProgress()
    }

    private fun logout() {
        if (context == null) {
            return
        }
        requireContext()
            .getSharedPreferences(LoginActivity.PREFS_AUTH, Context.MODE_PRIVATE)
            .edit()
            .remove(LoginActivity.KEY_USER_ID)
            .apply()

        val intent = Intent(requireContext(), LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }

    private fun loadProgress() {
        if (context == null) {
            return
        }

        val userId = requireContext()
            .getSharedPreferences(LoginActivity.PREFS_AUTH, Context.MODE_PRIVATE)
            .getLong(LoginActivity.KEY_USER_ID, -1L)
        if (userId == -1L) {
            return
        }

        val db = AppDatabase.getInstance(requireContext())
        val user = db.userDao().getById(userId)
        tvUserEmail.text = user?.email ?: ""

        val profileDao = db.profileDao()
        this.profileDao = profileDao
        val profile = profileDao.getByUserId(userId)
        if (profile != null) {
            XpRules.ensureWeek(profile)
            if (profile.avatarId.isNullOrEmpty()) {
                profile.avatarId = UnlockCatalog.AVATAR_FOX
            }
            if (profile.themeId.isNullOrEmpty()) {
                profile.themeId = UnlockCatalog.THEME_DEFAULT
            }
            profileDao.update(profile)
        }
        currentProfile = profile
        val qaDao = db.questionAnswerDao()
        this.qaDao = qaDao
        val friendDao = db.friendDao()
        this.friendDao = friendDao
        val quizItemDao = db.quizItemDao()
        val sessionDao = db.quizSessionDao()
        currentUserId = userId
        FriendSeeder.ensureSeeded(friendDao, userId)

        val all = qaDao.getAllByUserId(userId)

        val xp = profile?.xp ?: 0
        val streak = profile?.streak ?: 0
        currentLevel = XpRules.levelFromXp(xp)
        val xpPct = XpRules.progressPercentToNext(xp)
        val totalQuestions = all.size
        val starredCount = qaDao.countStarredByUserId(userId)

        tvXpTotal.text = xp.toString()
        tvLevel.text = getString(R.string.progress_level_scholar, currentLevel)
        tvXpPercent.text = getString(R.string.progress_xp_percent, xpPct)
        progressXp.progress = xpPct
        tvStatStreak.text = getString(R.string.progress_streak_value, streak)
        tvStatQuestions.text = totalQuestions.toString()
        tvStatStars.text = starredCount.toString()

        val attempted = quizItemDao.countAttemptedByUserId(userId)
        val correct = quizItemDao.countCorrectByUserId(userId)
        if (attempted > 0) {
            val accuracyPct = ((correct * 100f) / attempted).roundToInt()
            tvQuizAccuracy.text = getString(
                R.string.progress_quiz_accuracy_value, accuracyPct, correct, attempted
            )
        } else {
            tvQuizAccuracy.setText(R.string.progress_quiz_accuracy_empty)
        }

        val reviewSeconds = qaDao.sumReviewedSecondsByUserId(userId)
        tvReviewTime.text = formatReviewTime(reviewSeconds)

        bindInsights(qaDao, quizItemDao, userId)
        bindReviewSuggest(qaDao, userId)
        bindAvatars()
        bindThemes()
        bindLeaderboard(user?.fullName)

        val countMath = qaDao.countBySubject(userId, "math")
        val countScience = qaDao.countBySubject(userId, "science")
        val countCode = qaDao.countBySubject(userId, "code")
        val countHistory = qaDao.countBySubject(userId, "history")
        val countLanguage = qaDao.countBySubject(userId, "language")
        val subjectTotal = countMath + countScience + countCode + countHistory + countLanguage
        val barDenom = if (subjectTotal > 0) subjectTotal else 1

        bindSubjectRow(rowMath, tvCountMath, barMath, countMath, barDenom)
        bindSubjectRow(rowScience, tvCountScience, barScience, countScience, barDenom)
        bindSubjectRow(rowCode, tvCountCode, barCode, countCode, barDenom)
        bindSubjectRow(rowHistory, tvCountHistory, barHistory, countHistory, barDenom)
        bindSubjectRow(rowLanguage, tvCountLanguage, barLanguage, countLanguage, barDenom)
        tvSubjectsEmpty.visibility = if (subjectTotal == 0) View.VISIBLE else View.GONE

        val maxSubjectCount = maxOf(countMath, countScience, countCode, countHistory, countLanguage)
        val perfectCount = sessionDao.countPerfectByUserId(userId)

        setBadge(badgeStarter, totalQuestions >= 1)
        setBadge(badgeStars, starredCount >= 5)
        setBadge(badgeStreak, streak >= 3)
        setBadge(badgePerfect, perfectCount >= 1)
        setBadge(badgeMastery, maxSubjectCount >= MASTERY_MIN)
        setBadge(badgeScholar, xp >= 1000)

        applyThemeBackground()
    }

    private fun bindAvatars() {
        rowAvatars.removeAllViews()
        val profile = currentProfile
        if (context == null || profile == null) {
            return
        }
        val selected = UnlockCatalog.normalizeAvatarId(profile.avatarId)
        UnlockCatalog.AVATARS.forEachIndexed { i, avatarId ->
            val unlocked = UnlockCatalog.isAvatarUnlocked(avatarId, currentLevel)
            val isSelected = avatarId == selected
            val chip = buildChip(
                UnlockCatalog.emojiForAvatar(avatarId),
                unlocked,
                isSelected,
                UnlockCatalog.requiredLevelForAvatar(avatarId),
                i > 0
            )
            chip.setOnClickListener { onAvatarClicked(avatarId) }
            rowAvatars.addView(chip)
        }
    }

    private fun bindThemes() {
        rowThemes.removeAllViews()
        val profile = currentProfile
        if (context == null || profile == null) {
            return
        }
        val selected = UnlockCatalog.normalizeThemeId(profile.themeId)
        UnlockCatalog.THEMES.forEachIndexed { i, themeId ->
            val unlocked = UnlockCatalog.isThemeUnlocked(themeId, currentLevel)
            val isSelected = themeId == selected
            val chip = buildChip(
                UnlockCatalog.labelForTheme(themeId),
                unlocked,
                isSelected,
                UnlockCatalog.requiredLevelForTheme(themeId),
                i > 0
            )
            chip.setOnClickListener { onThemeClicked(themeId) }
            rowThemes.addView(chip)
        }
    }

    private fun buildChip(
        label: String,
        unlocked: Boolean,
        selected: Boolean,
        requiredLevel: Int,
        withStartMargin: Boolean
    ): TextView {
        val chip = TextView(requireContext())
        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        if (withStartMargin) {
            lp.marginStart = dp(8)
        }
        chip.layoutParams = lp
        chip.gravity = Gravity.CENTER
        chip.setPadding(dp(10), dp(12), dp(10), dp(12))
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        if (!unlocked) {
            chip.text = getString(R.string.progress_unlock_locked, requiredLevel)
            chip.alpha = 0.45f
            chip.setBackgroundResource(R.drawable.bg_unlock_chip)
            chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.auth_muted_foreground))
        } else {
            chip.text = label
            chip.alpha = 1f
            chip.setBackgroundResource(
                if (selected) R.drawable.bg_unlock_chip_selected else R.drawable.bg_unlock_chip
            )
            chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.auth_foreground))
        }
        return chip
    }

    private fun onAvatarClicked(avatarId: String) {
        val profile = currentProfile
        val dao = profileDao
        if (profile == null || dao == null) {
            return
        }
        if (!UnlockCatalog.isAvatarUnlocked(avatarId, currentLevel)) {
            Toast.makeText(
                requireContext(),
                getString(
                    R.string.progress_unlock_toast,
                    UnlockCatalog.requiredLevelForAvatar(avatarId)
                ),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        profile.avatarId = avatarId
        dao.update(profile)
        bindAvatars()
    }

    private fun onThemeClicked(themeId: String) {
        val profile = currentProfile
        val dao = profileDao
        if (profile == null || dao == null) {
            return
        }
        if (!UnlockCatalog.isThemeUnlocked(themeId, currentLevel)) {
            Toast.makeText(
                requireContext(),
                getString(
                    R.string.progress_unlock_toast,
                    UnlockCatalog.requiredLevelForTheme(themeId)
                ),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        profile.themeId = themeId
        dao.update(profile)
        bindThemes()
        applyThemeBackground()
        activity?.findViewById<View>(R.id.main)?.setBackgroundResource(UnlockCatalog.backgroundColorRes(themeId))
        Toast.makeText(requireContext(), R.string.progress_theme_applied, Toast.LENGTH_SHORT).show()
    }

    private fun applyThemeBackground() {
        val root = view
        val profile = currentProfile
        if (root == null || profile == null) {
            return
        }
        val themeId = UnlockCatalog.normalizeThemeId(profile.themeId)
        root.setBackgroundResource(UnlockCatalog.backgroundColorRes(themeId))
    }

    private fun bindLeaderboard(fullName: String?) {
        layoutLeaderboard.removeAllViews()
        val profile = currentProfile
        val friendDao = friendDao
        if (context == null || profile == null || friendDao == null) {
            return
        }
        XpRules.ensureWeek(profile)
        profileDao?.update(profile)

        val rows = mutableListOf<LeaderboardRow>()
        val youName = if (!fullName.isNullOrEmpty()) fullName else getString(R.string.progress_leaderboard_you)
        rows.add(LeaderboardRow(youName, profile.weeklyXp, true))

        for (friend in friendDao.getByOwner(currentUserId)) {
            rows.add(LeaderboardRow(friend.displayName, friend.weeklyXp, false))
        }
        rows.sortByDescending { it.weeklyXp }

        val inflater = LayoutInflater.from(requireContext())
        rows.forEachIndexed { i, row ->
            val item = inflater.inflate(R.layout.item_leaderboard_row, layoutLeaderboard, false)
            val tvRank = item.findViewById<TextView>(R.id.tv_rank)
            val tvName = item.findViewById<TextView>(R.id.tv_name)
            val tvWeeklyXp = item.findViewById<TextView>(R.id.tv_weekly_xp)
            tvRank.text = (i + 1).toString()
            tvName.text = if (row.isSelf) {
                "${getString(R.string.progress_leaderboard_you)} (${row.name})"
            } else {
                row.name
            }
            tvWeeklyXp.text = getString(R.string.progress_leaderboard_xp, row.weeklyXp)
            if (row.isSelf) {
                tvName.setTextColor(ContextCompat.getColor(requireContext(), R.color.auth_brand))
            }
            layoutLeaderboard.addView(item)
        }
    }

    private fun bindInsights(qaDao: QuestionAnswerDao, quizItemDao: QuizItemDao, userId: Long) {
        tvInsight1.visibility = View.GONE
        tvInsight2.visibility = View.GONE

        var shown = 0
        val topSubject = qaDao.getTopSubjectByUserId(userId)
        if (topSubject != null && qaDao.countBySubject(userId, topSubject) >= 1) {
            tvInsight1.text = getString(
                R.string.progress_insight_top_subject, subjectLabel(topSubject)
            )
            tvInsight1.visibility = View.VISIBLE
            shown++
        }

        val improvedSubject = findImprovedSubject(quizItemDao, userId)
        if (improvedSubject != null) {
            val target = if (shown == 0) tvInsight1 else tvInsight2
            target.text = getString(
                R.string.progress_insight_accuracy_improved, subjectLabel(improvedSubject)
            )
            target.visibility = View.VISIBLE
            shown++
        }

        tvInsightsEmpty.visibility = if (shown == 0) View.VISIBLE else View.GONE
    }

    private fun findImprovedSubject(quizItemDao: QuizItemDao, userId: Long): String? {
        val now = System.currentTimeMillis()
        val recentFrom = now - WINDOW_MS
        val olderFrom = now - (2L * WINDOW_MS)

        var bestSubject: String? = null
        var bestDelta = 0f

        for (subject in SUBJECTS) {
            val recentAttempted = quizItemDao.countAttemptedByUserIdSubjectAndRange(
                userId, subject, recentFrom, now
            )
            val olderAttempted = quizItemDao.countAttemptedByUserIdSubjectAndRange(
                userId, subject, olderFrom, recentFrom
            )
            if (recentAttempted < 2 || olderAttempted < 2) {
                continue
            }
            val recentCorrect = quizItemDao.countCorrectByUserIdSubjectAndRange(
                userId, subject, recentFrom, now
            )
            val olderCorrect = quizItemDao.countCorrectByUserIdSubjectAndRange(
                userId, subject, olderFrom, recentFrom
            )
            val recentAcc = recentCorrect * 1f / recentAttempted
            val olderAcc = olderCorrect * 1f / olderAttempted
            val delta = recentAcc - olderAcc
            if (delta > 0.05f && delta > bestDelta) {
                bestDelta = delta
                bestSubject = subject
            }
        }
        return bestSubject
    }

    private fun bindReviewSuggest(qaDao: QuestionAnswerDao, userId: Long) {
        val topSubject = qaDao.getTopSubjectByUserId(userId)
        if (topSubject == null || qaDao.countBySubject(userId, topSubject) < MIN_PRACTICE_QA) {
            reviewSubject = null
            cardReviewSuggest.visibility = View.GONE
            return
        }
        reviewSubject = topSubject
        tvReviewSubtitle.text = getString(R.string.progress_review_subtitle, subjectLabel(topSubject))
        cardReviewSuggest.visibility = View.VISIBLE
    }

    private fun openPractice(subject: String) {
        val dao = qaDao
        if (context == null || dao == null || currentUserId == -1L) {
            return
        }
        val count = dao.countBySubject(currentUserId, subject)
        if (count < MIN_PRACTICE_QA) {
            Toast.makeText(requireContext(), R.string.quiz_need_more, Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(requireContext(), QuizActivity::class.java)
        intent.putExtra(QuizActivity.EXTRA_SUBJECT, subject)
        startActivity(intent)
    }

    private fun formatReviewTime(totalSeconds: Int): String {
        if (totalSeconds < 60) {
            return getString(R.string.progress_review_time_seconds, totalSeconds)
        }
        if (totalSeconds < 3600) {
            return getString(R.string.progress_review_time_minutes, totalSeconds / 60)
        }
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        return getString(R.string.progress_review_time_hours, hours, minutes)
    }

    private fun bindSubjectRow(
        row: LinearLayout,
        tvCount: TextView,
        bar: ProgressBar,
        count: Int,
        denom: Int
    ) {
        if (count <= 0) {
            row.visibility = View.GONE
            return
        }
        row.visibility = View.VISIBLE
        tvCount.text = count.toString()
        bar.progress = ((count * 100f) / denom).roundToInt()
    }

    private fun setBadge(badge: LinearLayout, earned: Boolean) {
        badge.setBackgroundResource(if (earned) R.drawable.bg_badge_earned else R.drawable.bg_badge_locked)
        badge.alpha = if (earned) 1f else 0.5f
    }

    private fun subjectLabel(subject: String?): String {
        return when (subject) {
            "math" -> getString(R.string.subject_math)
            "science" -> getString(R.string.subject_science)
            "code" -> getString(R.string.subject_code)
            "history" -> getString(R.string.subject_history)
            "language" -> getString(R.string.subject_language)
            else -> getString(R.string.subject_other)
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).roundToInt()
    }

    private class LeaderboardRow(
        val name: String?,
        val weeklyXp: Int,
        val isSelf: Boolean
    )
}
```

**Kiểm tra nhanh:** Level 1 → chỉ `fox` + `Default` sáng; 3 chip/avatar còn lại hiện `Lv 2` / `Lv 3` / `Lv 4`.

---

## Step 15) `AskFragment` — XP qua `XpRules`

**Thêm import** vào `app/src/main/java/com/aistudy/aistudy/ask/AskFragment.kt` (ngay sau `import com.aistudy.aistudy.database.QuestionAnswer`):

```kotlin
import com.aistudy.aistudy.xp.XpRules
```

**Thay block cộng XP** (ngay sau `qaDao.insert(qa)`, bên trong lambda chạy trên executor):

```kotlin
                val qaId = qaDao.insert(qa)
                Log.d(TAG, "saved qaId=$qaId imagePath=$imagePath")

                if (profile != null) {
                    XpRules.recordDailyActivity(profile, profileDao)
                    XpRules.addXp(profile, profileDao, XpRules.ASK)
                    Log.d(TAG, "xp updated to ${profile.xp}")
                }
```

`if (profile != null)` giúp Kotlin smart cast `profile` từ `Profile?` sang `Profile` bên trong block, nên truy cập `profile.xp` không cần `?.`.

**Kiểm tra nhanh:** Không còn dòng `profile.xp += 50` / `profileDao.update(profile)` thủ công trong `AskFragment`.

---

## Step 16) `AnswerActivity` — XP review qua `XpRules`

**Thêm import** vào `app/src/main/java/com/aistudy/aistudy/answer/AnswerActivity.kt` (ngay sau `import com.aistudy.aistudy.quiz.QuizActivity`):

```kotlin
import com.aistudy.aistudy.xp.XpRules
```

**Xóa hằng số cũ trong `companion object`:** XP review giờ lấy từ `XpRules.REVIEW`.

**Tìm:**

```kotlin
        const val EXTRA_QA_ID = "qa_id"

        private const val XP_REVIEW = 10
        private const val KEY_SIMPLIFY = "simplify"
```

**Đổi thành:**

```kotlin
        const val EXTRA_QA_ID = "qa_id"

        private const val KEY_SIMPLIFY = "simplify"
```

**Tìm block trong `markReviewed()`:**

```kotlin
        if (firstReview) {
            val profile = profileDao.getByUserId(item.userId)
            if (profile != null) {
                profile.xp += XP_REVIEW
                profileDao.update(profile)
                Toast.makeText(this, R.string.answer_review_xp_toast, Toast.LENGTH_SHORT).show()
            }
        }
        refreshReviewButton()
```

**Đổi thành:**

```kotlin
        if (firstReview) {
            val profile = profileDao.getByUserId(item.userId)
            if (profile != null) {
                XpRules.addXp(profile, profileDao, XpRules.REVIEW)
                Toast.makeText(this, R.string.answer_review_xp_toast, Toast.LENGTH_SHORT).show()
            }
        }
        refreshReviewButton()
```

**Lưu ý:** review **không** gọi `recordDailyActivity` — streak chỉ tính khi Ask hoặc hoàn thành Quiz.

---

## Step 17) `QuizActivity.finishQuiz` — XP + streak

**Thêm import** vào `app/src/main/java/com/aistudy/aistudy/quiz/QuizActivity.kt` (ngay sau `import com.aistudy.aistudy.database.QuizSessionDao`):

```kotlin
import com.aistudy.aistudy.xp.XpRules
```

**Thay toàn bộ hàm `finishQuiz()`:**

```kotlin
    private fun finishQuiz() {
        val score = items.count { it.correct == true }
        val xpGain = score * XpRules.QUIZ_CORRECT + XpRules.QUIZ_COMPLETE_BONUS
        session.score = score
        session.total = items.size
        session.completed = true
        sessionDao.update(session)

        val profile = profileDao.getByUserId(userId)
        if (profile != null) {
            XpRules.recordDailyActivity(profile, profileDao)
            XpRules.addXp(profile, profileDao, xpGain)
        }

        layoutQuiz.visibility = View.GONE
        layoutLoading.visibility = View.GONE
        layoutResult.visibility = View.VISIBLE
        tvResultScore.text = getString(R.string.quiz_result_score, score, items.size)
        tvResultXp.text = getString(R.string.quiz_result_xp, xpGain)
    }
```

`items.count { it.correct == true }` đếm số câu đúng: `QuizItem.correct` có kiểu `Boolean?` (câu chưa chấm là `null`), so sánh `== true` loại cả `false` lẫn `null`. Property Kotlin tên `correct` được map vào cột SQL `isCorrect` bằng `@ColumnInfo(name = "isCorrect")`.

| Điểm | XP |
|------|-----|
| 0/4 | 20 |
| 3/4 | 50 |
| 4/4 | 60 |

---

## Step 18) `HomeFragment` — level/XP theo `XpRules` + theme bg

**Giải thích:**  
Bỏ `xp / 100` và `XP_MAX` cố định của P5; dùng `levelFromXp`, `progressPercentToNext`, `nextLevelThreshold`. Đồng thời apply theme cho `R.id.main` qua `activity?.findViewById<View>(R.id.main)?.setBackgroundResource(...)` trong `loadDashboard()`. `profile?.xp ?: 0` lấy XP khi có profile, ngược lại trả `0`.

**File:** `app/src/main/java/com/aistudy/aistudy/home/HomeFragment.kt` (**thay toàn bộ**)

```kotlin
package com.aistudy.aistudy.home

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.aistudy.aistudy.MainActivity
import com.aistudy.aistudy.R
import com.aistudy.aistudy.answer.AnswerActivity
import com.aistudy.aistudy.auth.LoginActivity
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.QuestionAnswerDao
import com.aistudy.aistudy.quiz.QuizActivity
import com.aistudy.aistudy.xp.UnlockCatalog
import com.aistudy.aistudy.xp.XpRules
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton

class HomeFragment : Fragment() {

    companion object {
        private const val MIN_PRACTICE_QA = 2
    }

    private lateinit var tvLevelBadge: TextView
    private lateinit var tvStreak: TextView
    private lateinit var tvXpLabel: TextView
    private lateinit var tvXpPercent: TextView
    private lateinit var progressXp: ProgressBar
    private lateinit var tvGreeting: TextView
    private lateinit var btnAsk: MaterialButton
    private lateinit var btnPracticeRecent: MaterialButton

    private lateinit var cardReviewSuggest: LinearLayout
    private lateinit var tvReviewSubtitle: TextView

    private lateinit var sectionRecent: LinearLayout
    private lateinit var tvViewAll: TextView
    private lateinit var cardRecent: LinearLayout
    private lateinit var tvRecentQuestion: TextView
    private lateinit var tvRecentSubject: TextView

    private lateinit var tvCountMath: TextView
    private lateinit var tvCountScience: TextView
    private lateinit var tvCountCode: TextView
    private lateinit var tvCountHistory: TextView
    private lateinit var tvCountLanguage: TextView

    private var latestQaId = -1L
    private var latestSubject: String? = null
    private var reviewSubject: String? = null
    private var currentUserId = -1L
    private var qaDao: QuestionAnswerDao? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tvLevelBadge = view.findViewById(R.id.tv_level_badge)
        tvStreak = view.findViewById(R.id.tv_streak)
        tvXpLabel = view.findViewById(R.id.tv_xp_label)
        tvXpPercent = view.findViewById(R.id.tv_xp_percent)
        progressXp = view.findViewById(R.id.progress_xp)
        tvGreeting = view.findViewById(R.id.tv_greeting)
        btnAsk = view.findViewById(R.id.btn_ask)
        btnPracticeRecent = view.findViewById(R.id.btn_practice_recent)

        cardReviewSuggest = view.findViewById(R.id.card_review_suggest)
        tvReviewSubtitle = view.findViewById(R.id.tv_review_subtitle)

        sectionRecent = view.findViewById(R.id.section_recent)
        tvViewAll = view.findViewById(R.id.tv_view_all)
        cardRecent = view.findViewById(R.id.card_recent)
        tvRecentQuestion = view.findViewById(R.id.tv_recent_question)
        tvRecentSubject = view.findViewById(R.id.tv_recent_subject)

        tvCountMath = view.findViewById(R.id.tv_count_math)
        tvCountScience = view.findViewById(R.id.tv_count_science)
        tvCountCode = view.findViewById(R.id.tv_count_code)
        tvCountHistory = view.findViewById(R.id.tv_count_history)
        tvCountLanguage = view.findViewById(R.id.tv_count_language)

        btnAsk.setOnClickListener { selectTab(R.id.nav_ask) }
        btnPracticeRecent.setOnClickListener {
            latestSubject?.let { openPractice(it) }
        }
        cardReviewSuggest.setOnClickListener {
            reviewSubject?.let { openPractice(it) }
        }
        tvViewAll.setOnClickListener { selectTab(R.id.nav_history) }
        cardRecent.setOnClickListener {
            if (latestQaId != -1L) {
                val intent = Intent(requireContext(), AnswerActivity::class.java)
                intent.putExtra(AnswerActivity.EXTRA_QA_ID, latestQaId)
                startActivity(intent)
            }
        }

        view.findViewById<View>(R.id.card_subject_math).setOnClickListener { openPractice("math") }
        view.findViewById<View>(R.id.card_subject_science).setOnClickListener { openPractice("science") }
        view.findViewById<View>(R.id.card_subject_code).setOnClickListener { openPractice("code") }
        view.findViewById<View>(R.id.card_subject_history).setOnClickListener { openPractice("history") }
        view.findViewById<View>(R.id.card_subject_language).setOnClickListener { openPractice("language") }
    }

    override fun onResume() {
        super.onResume()
        loadDashboard()
    }

    private fun loadDashboard() {
        if (context == null) {
            return
        }

        val userId = requireContext()
            .getSharedPreferences(LoginActivity.PREFS_AUTH, Context.MODE_PRIVATE)
            .getLong(LoginActivity.KEY_USER_ID, -1L)
        if (userId == -1L) {
            return
        }

        val db = AppDatabase.getInstance(requireContext())
        val user = db.userDao().getById(userId)
        val profile = db.profileDao().getByUserId(userId)
        val qaDao = db.questionAnswerDao()
        this.qaDao = qaDao
        currentUserId = userId

        val fullName = user?.fullName ?: ""
        val xp = profile?.xp ?: 0
        val streak = profile?.streak ?: 0
        val level = XpRules.levelFromXp(xp)
        val xpPct = XpRules.progressPercentToNext(xp)
        val nextXp = XpRules.nextLevelThreshold(xp)

        tvLevelBadge.text = level.toString()
        tvStreak.text = getString(R.string.home_streak_days, streak)
        tvXpLabel.text = getString(R.string.home_xp_progress, xp, nextXp)
        tvXpPercent.text = getString(R.string.home_xp_percent, xpPct)
        progressXp.progress = xpPct
        tvGreeting.text = getString(R.string.home_greeting, fullName)

        if (profile != null) {
            activity?.findViewById<View>(R.id.main)?.setBackgroundResource(
                UnlockCatalog.backgroundColorRes(UnlockCatalog.normalizeThemeId(profile.themeId))
            )
        }

        val latest = qaDao.getLatestByUserId(userId)
        if (latest != null) {
            latestQaId = latest.id
            latestSubject = latest.subject
            sectionRecent.visibility = View.VISIBLE
            tvRecentQuestion.text = latest.question
            tvRecentSubject.text = subjectLabel(latest.subject)
            val subjectCount = latest.subject?.let { qaDao.countBySubject(userId, it) } ?: 0
            if (subjectCount >= MIN_PRACTICE_QA) {
                btnPracticeRecent.visibility = View.VISIBLE
                btnPracticeRecent.text = getString(R.string.quiz_practice_on, subjectLabel(latest.subject))
            } else {
                btnPracticeRecent.visibility = View.GONE
            }
        } else {
            latestQaId = -1L
            latestSubject = null
            sectionRecent.visibility = View.GONE
            btnPracticeRecent.visibility = View.GONE
        }

        val topSubject = qaDao.getTopSubjectByUserId(userId)
        if (topSubject != null && qaDao.countBySubject(userId, topSubject) >= MIN_PRACTICE_QA) {
            reviewSubject = topSubject
            tvReviewSubtitle.text = getString(R.string.home_review_subtitle, subjectLabel(topSubject))
            cardReviewSuggest.visibility = View.VISIBLE
        } else {
            reviewSubject = null
            cardReviewSuggest.visibility = View.GONE
        }

        tvCountMath.text = getString(R.string.home_subject_count, qaDao.countBySubject(userId, "math"))
        tvCountScience.text = getString(R.string.home_subject_count, qaDao.countBySubject(userId, "science"))
        tvCountCode.text = getString(R.string.home_subject_count, qaDao.countBySubject(userId, "code"))
        tvCountHistory.text = getString(R.string.home_subject_count, qaDao.countBySubject(userId, "history"))
        tvCountLanguage.text = getString(R.string.home_subject_count, qaDao.countBySubject(userId, "language"))
    }

    private fun openPractice(subject: String) {
        val dao = qaDao
        if (context == null || dao == null || currentUserId == -1L) {
            return
        }
        val count = dao.countBySubject(currentUserId, subject)
        if (count < MIN_PRACTICE_QA) {
            Toast.makeText(requireContext(), R.string.quiz_need_more, Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(requireContext(), QuizActivity::class.java)
        intent.putExtra(QuizActivity.EXTRA_SUBJECT, subject)
        startActivity(intent)
    }

    private fun selectTab(menuItemId: Int) {
        val activity = activity
        if (activity is MainActivity) {
            activity.findViewById<BottomNavigationView>(R.id.bottom_nav)?.selectedItemId = menuItemId
        }
    }

    private fun subjectLabel(subject: String?): String {
        return when (subject) {
            "math" -> getString(R.string.subject_math)
            "science" -> getString(R.string.subject_science)
            "code" -> getString(R.string.subject_code)
            "history" -> getString(R.string.subject_history)
            "language" -> getString(R.string.subject_language)
            else -> getString(R.string.subject_other)
        }
    }
}
```

**Kiểm tra nhanh:** XP 50 → badge level `1`, label `50 / 100 XP`, bar 50%.

---

## Step 19) `MainActivity` — seed friend + apply theme

**Giải thích:** Chỉ 3 chỉnh sửa nhỏ ở Phase 13 (phần WorkManager/notification làm ở Phase 14).

**File:** `app/src/main/java/com/aistudy/aistudy/MainActivity.kt`

**19.1 — thêm import** (giữ thứ tự alphabet; `android.view.View` chỉ thêm nếu file chưa có):

```kotlin
import android.view.View
import com.aistudy.aistudy.xp.FriendSeeder
import com.aistudy.aistudy.xp.UnlockCatalog
```

**19.2 — ngay sau khối check `onboarded` (khối `if (profile != null && !profile.onboarded) { ... return }`), thêm seed friend:**

```kotlin
        FriendSeeder.ensureSeeded(AppDatabase.getInstance(this).friendDao(), userId)
```

**19.3 — ngay sau lambda `ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets -> ... }`, apply theme background:**

```kotlin
        if (profile != null) {
            findViewById<View>(R.id.main).setBackgroundResource(
                UnlockCatalog.backgroundColorRes(UnlockCatalog.normalizeThemeId(profile.themeId))
            )
        }
```

`findViewById<View>(...)` cần chỉ rõ kiểu generic vì kết quả không được gán vào biến có kiểu khai báo sẵn; thiếu `<View>` sẽ báo lỗi `Not enough information to infer type variable T`. `profile` ở đây là `val profile` đã đọc từ `profileDao().getByUserId(userId)` trong khối check `onboarded`.

**Kiểm tra nhanh:** Đổi theme ở Progress → back Home → nền vẫn giữ theme mới (Home cũng set lại trong `loadDashboard`).

---

## Step 20) Test tay (checklist)

### Chuẩn bị

1. Rebuild sau bump **v8** (data cũ bị xóa) → login + onboarding.
2. Ask 2 câu Math (XP 100) → level 2.
3. Làm 1 quiz Math tới hết → XP tăng `score*10 + 20`.

### Checklist XP / level

1. Home badge level đổi `1 → 2` khi XP ≥ 100.
2. Progress `Level N • Scholar` khớp Home.
3. `%` Progress = `progressPercentToNext` (reset về 0 ngay khi vừa lên level).
4. Ask +50, Answer Mark reviewed +10, quiz `score*10 + 20`.

### Checklist unlock

1. Level 1: chỉ 🦊 + `Default` sáng; còn lại mờ + hiện `Lv 2/3/4`.
2. Tap chip mờ → Toast `Reach level N to unlock`.
3. Level 2: tap 🦉 → chip viền xanh (selected), reload Progress vẫn giữ.
4. Tap theme `Night` (Lv2) → Toast `Theme applied` + nền tối ngay lập tức + Home cũng tối.

### Checklist leaderboard

1. Progress → **Weekly leaderboard** 5 row: Riley 200, Sam 120, Alex 80, Jordan 45 + You.
2. You XP tuần = tổng XP kiếm trong tuần này (không phải tổng XP).
3. Tên You màu brand + format `You (Full Name)`.
4. Kiếm > 200 XP tuần → You lên hạng 1.

### Checklist badges

| Badge | Cách bật |
|-------|----------|
| Starter | Ask ≥1 câu |
| 5 stars | Star ≥5 QA trong History/Answer |
| 3-day streak | `streak >= 3` (sửa DB nhanh để test) |
| Quiz 100% | Hoàn thành 1 quiz đúng hết |
| Subject mastery | 1 môn ≥5 QA |
| Scholar 1k | XP ≥ 1000 |

### Checklist streak

1. Ask lần đầu trong ngày → `streak = 1`, `lastActiveDay = today`.
2. Ask thêm cùng ngày → streak **không** tăng.
3. Sửa `lastActiveDay` thành hôm qua rồi Ask → streak +1.

### Lỗi thường gặp

| Triệu chứng | Nguyên nhân hay gặp | Cách xử lý |
|-------------|---------------------|------------|
| Crash schema | Quên `version = 8` hoặc quên `Friend::class` trong `entities` | Step 7 |
| `Unresolved reference: friendDao` | Quên `abstract fun friendDao(): FriendDao` | Step 7 |
| KSP lỗi `[ksp] ... Friend` khi build | Entity thiếu giá trị mặc định cho `var` hoặc thiếu `@PrimaryKey` | Step 5 |
| `Unresolved reference: XpRules` / `UnlockCatalog` | Thiếu `import com.aistudy.aistudy.xp.XpRules` / `UnlockCatalog` | Step 15–19 |
| `UninitializedPropertyAccessException: lateinit property rowAvatars has not been initialized` | Quên `findViewById` cho view mới trong `onViewCreated` | Step 14 |
| `Not enough information to infer type variable T` ở `findViewById` | Thiếu `<View>` khi gọi thẳng `findViewById(R.id.main).setBackgroundResource(...)` | Step 19.3 |
| Leaderboard trống | Chưa `FriendSeeder.ensureSeeded` | Step 6 + 19.2 |
| Chip avatar không hiện | `row_avatars` sai id / quên `bindAvatars()` | Step 13 + 14 |
| Weekly XP không reset | Quên `ensureWeek` trước khi cộng | `XpRules.addXp` đã gọi sẵn |
| Theme không đổi ở Home | Quên block ở `loadDashboard` | Step 18 |
| aapt lỗi format string | `Quiz 100%` thiếu escape `%%` | Step 12 |
| Data biến mất | Destructive migration v8 | Đúng kỳ vọng Phase 13 |

---

## Mapping với plan / FR

| FR / Plan | Android Phase 13 |
|-----------|------------------|
| XP sources chuẩn hóa 1 chỗ | `XpRules.ASK/REVIEW/QUIZ_CORRECT/QUIZ_COMPLETE_BONUS` |
| Level từ XP thresholds | `LEVEL_XP = intArrayOf(0, 100, 250, 500, 1000, 2000)` |
| Unlock avatar + theme | `UnlockCatalog` + `Profile.avatarId/themeId` |
| Badges mở rộng | 6 badge trên Progress |
| Leaderboard local theo XP tuần | `Friend` + `weeklyXp` + `weekStartMs` reset thứ Hai |
| Không sync cloud | Toàn bộ trong Room |

---

## Checklist hoàn thành Phase 13

- [ ] `xp/XpRules.kt`
- [ ] `xp/UnlockCatalog.kt`
- [ ] `xp/FriendSeeder.kt`
- [ ] `Friend` + `FriendDao`
- [ ] `Profile` 5 field mới + `ProfileDefaults`
- [ ] `AppDatabase` version **8** + `friendDao()`
- [ ] DAO: `countStarredByUserId`, `countPerfectByUserId` (+ query cho P14)
- [ ] `colors.xml` 3 theme + 3 drawable + `item_leaderboard_row.xml`
- [ ] `strings_progress.xml` + `fragment_progress.xml`
- [ ] `ProgressFragment` unlock / leaderboard / badge / theme
- [ ] Ask / Answer / Quiz dùng `XpRules`
- [ ] `HomeFragment` + `MainActivity`
- [ ] Test tay theo Step 20
- [ ] Compile: `./gradlew :app:compileDebugKotlin` OK (KSP sinh lại `AppDatabase_Impl` + `FriendDao_Impl`)

**Tiếp theo:** Phase 14 — Notifications (WorkManager: bookmark, repeated mistakes, motivational, daily/weekly summary + deep link).
