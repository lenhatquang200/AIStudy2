# Phase 11: AI Practice / Quiz — Hướng dẫn từng bước


Mục tiêu Phase 11:

1. **QuizSession + QuizItem** — lưu phiên practice và từng câu (Room **v6**)
2. **Gemini generate quiz** — lấy N câu QA gần đây cùng môn → 3–5 items (`mcq` / `short` / `blank`)
3. **QuizActivity** — làm từng câu, instant feedback (đúng/sai + explanation)
4. **XP quiz** — hoàn thành: `profile.xp += score * 10` (score = số câu đúng)
5. **Entry points** — Answer Practice, Home (nút + tap subject), History (chip môn)

**Điều kiện trước khi làm:** Phase 0–10 đã xong (Ask text/ảnh, Answer extras, Room **v5**).

**Không làm ở Phase này:** Progress accuracy UI (P12), badges/leaderboard (P13), notifications (P14).

**Lưu ý DB:** bump Room **v5 → v6** + `fallbackToDestructiveMigration(true)` → data cũ trên máy dev **bị xóa** khi schema mới. Bình thường cho giai đoạn này.

**Constants quan trọng (phải giữ đúng, khai báo `private const val` trong `companion object` của `QuizActivity`):**

| Constant | Value | Ý nghĩa |
|----------|-------|---------|
| `MIN_SOURCE_QA` | `2` | Cần ≥2 QA cùng subject mới Practice |
| `RECENT_LIMIT` | `5` | Số QA gần nhất gửi Gemini |
| `TARGET_ITEMS` | `4` | Số câu quiz mục tiêu (trong khoảng 3–5 của plan) |
| `XP_PER_CORRECT` | `10` | XP = số đúng × 10 |

---

## Tổng quan các file sẽ chạm

| File | Loại | Việc |
|------|------|------|
| `QuizSession.kt` | **Mới** | Entity phiên quiz |
| `QuizItem.kt` | **Mới** | Entity từng câu |
| `QuizSessionDao.kt` | **Mới** | insert/update/get |
| `QuizItemDao.kt` | **Mới** | insert/update/get + count cho P12 |
| `AppDatabase.kt` | Sửa | `version = 6`, đăng ký entity + DAO |
| `QuestionAnswerDao.kt` | Sửa | `getRecentByUserIdAndSubject` |
| `strings_quiz.xml` | **Mới** | Toàn bộ chuỗi quiz |
| `activity_quiz.xml` | **Mới** | Loading / question / result |
| `QuizActivity.kt` | **Mới** | Generate + play + XP |
| `AndroidManifest.xml` | Sửa | Đăng ký `QuizActivity` |
| `activity_answer.xml` | Sửa | Nút Practice |
| `AnswerActivity.kt` | Sửa | Mở `QuizActivity` theo subject |
| `fragment_home.xml` | Sửa | `btn_practice_recent` |
| `HomeFragment.kt` | Sửa | Practice recent + tap subject card |
| `fragment_history.xml` | Sửa | `btn_practice` + xmlns app |
| `HistoryFragment.kt` | Sửa | Practice khi filter theo môn |

**Không đụng:** `GeminiClient.kt` (dùng lại `ask(apiKey, prompt)`), AskFragment, ProgressFragment (UI accuracy để P12).

---

## Step 1) Entity `QuizSession`

**Giải thích:**  
Một lần bấm Practice = 1 session: môn, nguồn QA (JSON ids), điểm, tổng câu, đã hoàn thành chưa. Entity viết theo cùng kiểu các entity khác: `class` thường với các `var` có giá trị mặc định (Room cần constructor không tham số và setter cho từng cột). Cột text dùng `String?`, cột số dùng `Long`/`Int` mặc định `0`.

**File:** `app/src/main/java/com/aistudy/aistudy/database/QuizSession.kt` (**mới** — copy nguyên file)

```kotlin
package com.aistudy.aistudy.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quiz_sessions")
class QuizSession {

    @PrimaryKey(autoGenerate = true)
    var id: Long = 0

    var userId: Long = 0
    var subject: String? = null
    var sourceQaIds: String? = null
    var createdAt: Long = 0
    var score: Int = 0
    var total: Int = 0
    var completed: Boolean = false
}
```

| Property | Ý nghĩa |
|-------|---------|
| `userId` | User đang login |
| `subject` | `math` / `science` / `code` / `history` / `language` / … |
| `sourceQaIds` | JSON array id QA nguồn, vd. `[12,11,9]` |
| `createdAt` | epoch ms |
| `score` | Số câu đúng (cập nhật lúc finish) |
| `total` | Tổng câu trong session |
| `completed` | `true` khi xem result |

**Kiểm tra nhanh:** Build lại, file compile; chưa bump version thì Room chưa tạo bảng.

---

## Step 2) Entity `QuizItem`

**Giải thích:**  
Từng câu trong session. Kết quả đúng/sai dùng `Boolean?` (nullable) — `null` = chưa trả lời (Progress P12 đếm attempted).

**Tại sao property tên `correct` chứ không phải `isCorrect`:** Kotlin xử lý property bắt đầu bằng `is` đặc biệt — `var isCorrect` sinh getter `isCorrect()` và setter `setCorrect(...)`. Room KSP không ghép được setter `setCorrect(...)` với field `isCorrect` nên báo lỗi không tìm thấy setter cho field này khi build. Vì vậy đặt tên property là `correct` và gắn `@ColumnInfo(name = "isCorrect")`: tên cột SQL trong bảng `quiz_items` vẫn là `isCorrect`, nên mọi câu `@Query` (vd. `isCorrect IS NOT NULL`, `isCorrect = 1`) giữ nguyên; code Kotlin đọc/ghi qua `item.correct`.

**File:** `app/src/main/java/com/aistudy/aistudy/database/QuizItem.kt` (**mới** — copy nguyên file)

```kotlin
package com.aistudy.aistudy.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quiz_items")
class QuizItem {

    @PrimaryKey(autoGenerate = true)
    var id: Long = 0

    var sessionId: Long = 0
    var type: String? = null
    var prompt: String? = null
    var optionsJson: String? = null
    var correctAnswer: String? = null
    var explanation: String? = null
    var userAnswer: String? = null

    @ColumnInfo(name = "isCorrect")
    var correct: Boolean? = null
}
```

| Property | Ý nghĩa |
|-------|---------|
| `type` | `mcq` \| `short` \| `blank` |
| `prompt` | Nội dung câu hỏi |
| `optionsJson` | JSON array string (mcq); `[]` nếu short/blank |
| `correctAnswer` | Đáp án đúng (mcq: phải khớp **exact** 1 option) |
| `explanation` | Giải thích instant feedback |
| `userAnswer` | Câu trả lời user (`null` nếu chưa) |
| `correct` (cột `isCorrect`) | `true`/`false`/`null` |

**Kiểm tra nhanh:** `type` chỉ 3 giá trị trên; normalize trong `QuizActivity`.

---

## Step 3) `QuizSessionDao`

**Giải thích:**  
DAO là `interface` với các `fun`. Query một dòng trả `QuizSession?` (có thể không tìm thấy), query danh sách trả `List<QuizSession>`. `@Insert` trả `Long` là id vừa sinh.

**File:** `app/src/main/java/com/aistudy/aistudy/database/QuizSessionDao.kt` (**mới**)

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
}
```

---

## Step 4) `QuizItemDao`

**Giải thích:**  
`countAttemptedByUserId` / `countCorrectByUserId` chuẩn bị cho Phase 12 (accuracy). Phase 11 chưa hiện trên Progress UI. Query dùng tên cột `isCorrect` (tên SQL từ `@ColumnInfo`), không phải tên property `correct`.

**File:** `app/src/main/java/com/aistudy/aistudy/database/QuizItemDao.kt` (**mới**)

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
}
```

---

## Step 5) Bump `AppDatabase` → version 6

**Giải thích:**  
Thêm 2 entity + 2 abstract DAO. Version **5 → 6**. Có `fallbackToDestructiveMigration(true)` → mở app lần đầu sau bump: DB tạo lại, login lại. Danh sách entity viết dạng array literal `[X::class, ...]`; `getInstance` nằm trong `companion object` với `@Volatile` + `synchronized` để chỉ tạo một instance.

**File:** `app/src/main/java/com/aistudy/aistudy/database/AppDatabase.kt`

**Đổi thành toàn bộ file:**

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
        QuizItem::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao

    abstract fun profileDao(): ProfileDao

    abstract fun questionAnswerDao(): QuestionAnswerDao

    abstract fun quizSessionDao(): QuizSessionDao

    abstract fun quizItemDao(): QuizItemDao

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

**Kiểm tra nhanh:** Build lại (KSP sinh `AppDatabase_Impl`, `QuizSessionDao_Impl`, `QuizItemDao_Impl`) → Run app → schema mới; QA cũ mất (destructive).

---

## Step 6) `QuestionAnswerDao` — recent theo subject

**Giải thích:**  
Quiz cần N câu cùng môn để làm source cho Gemini.

**File:** `app/src/main/java/com/aistudy/aistudy/database/QuestionAnswerDao.kt`

**Thêm hàm sau `countBySubject`:**

```kotlin

    @Query("SELECT * FROM question_answers WHERE userId = :userId AND subject = :subject ORDER BY createdAt DESC LIMIT :limit")
    fun getRecentByUserIdAndSubject(userId: Long, subject: String, limit: Int): List<QuestionAnswer>
```

**File đầy đủ sau Step 6:**

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

    @Query("SELECT COUNT(*) FROM question_answers WHERE userId = :userId AND subject = :subject")
    fun countBySubject(userId: Long, subject: String): Int

    @Query("SELECT * FROM question_answers WHERE userId = :userId AND subject = :subject ORDER BY createdAt DESC LIMIT :limit")
    fun getRecentByUserIdAndSubject(userId: Long, subject: String, limit: Int): List<QuestionAnswer>
}
```

---

## Step 7) Strings quiz

**File:** `app/src/main/res/values/strings_quiz.xml` (**mới**)

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="quiz_title">Practice Quiz</string>
    <string name="quiz_practice">Practice</string>
    <string name="quiz_practice_on">Practice on %1$s</string>
    <string name="quiz_back">Back</string>
    <string name="quiz_loading">Generating practice questions…</string>
    <string name="quiz_need_more">Ask at least 2 questions in this subject before practice.</string>
    <string name="quiz_no_api_key">GEMINI_API_KEY not set in local.properties</string>
    <string name="quiz_error">Could not generate quiz. Try again.</string>
    <string name="quiz_progress">Question %1$d of %2$d</string>
    <string name="quiz_type_mcq">Multiple choice</string>
    <string name="quiz_type_short">Short answer</string>
    <string name="quiz_type_blank">Fill in the blank</string>
    <string name="quiz_hint_answer">Your answer</string>
    <string name="quiz_check">Check answer</string>
    <string name="quiz_next">Next</string>
    <string name="quiz_finish">See results</string>
    <string name="quiz_correct">Correct!</string>
    <string name="quiz_incorrect">Incorrect</string>
    <string name="quiz_explanation_label">Explanation</string>
    <string name="quiz_correct_answer_label">Correct answer: %1$s</string>
    <string name="quiz_result_title">Quiz complete</string>
    <string name="quiz_result_score">Score: %1$d / %2$d</string>
    <string name="quiz_result_xp">+%1$d XP</string>
    <string name="quiz_done">Done</string>
    <string name="quiz_select_option">Select an option first.</string>
    <string name="quiz_enter_answer">Enter your answer first.</string>
</resources>
```

Android merge mọi `values/strings*.xml` — không cần sửa `strings.xml`.

---

## Step 8) Layout `activity_quiz.xml`

**Giải thích:**  
3 state trong 1 Activity: `layout_loading`, `layout_quiz`, `layout_result`. MCQ dùng `RadioGroup` (add RadioButton runtime). Short/blank dùng `EditText`.

**File:** `app/src/main/res/layout/activity_quiz.xml` (**mới** — copy nguyên)

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/auth_surface">

    <LinearLayout
        android:id="@+id/layout_loading"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:gravity="center"
        android:orientation="vertical"
        android:padding="24dp">

        <ProgressBar
            android:layout_width="wrap_content"
            android:layout_height="wrap_content" />

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:text="@string/quiz_loading"
            android:textColor="@color/auth_foreground"
            android:textSize="14sp" />
    </LinearLayout>

    <LinearLayout
        android:id="@+id/layout_quiz"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical"
        android:visibility="gone">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:gravity="center_vertical"
            android:orientation="horizontal"
            android:paddingHorizontal="16dp"
            android:paddingVertical="16dp">

            <ImageButton
                android:id="@+id/btn_back"
                android:layout_width="40dp"
                android:layout_height="40dp"
                android:background="@color/auth_card"
                android:contentDescription="@string/quiz_back"
                android:src="@android:drawable/ic_menu_revert"
                app:tint="@color/auth_foreground" />

            <TextView
                android:id="@+id/tv_quiz_title"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:gravity="center"
                android:text="@string/quiz_title"
                android:textColor="@color/auth_brand"
                android:textSize="12sp"
                android:textStyle="bold" />

            <TextView
                android:id="@+id/tv_progress"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:textColor="@color/auth_muted_foreground"
                android:textSize="12sp"
                android:textStyle="bold" />
        </LinearLayout>

        <View
            android:layout_width="match_parent"
            android:layout_height="1dp"
            android:background="@color/auth_input_ring" />

        <ScrollView
            android:layout_width="match_parent"
            android:layout_height="0dp"
            android:layout_weight="1"
            android:clipToPadding="false"
            android:fillViewport="true">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:paddingHorizontal="@dimen/auth_screen_padding_horizontal"
                android:paddingTop="20dp"
                android:paddingBottom="24dp">

                <TextView
                    android:id="@+id/tv_item_type"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:background="@drawable/bg_difficulty_badge"
                    android:paddingHorizontal="10dp"
                    android:paddingVertical="4dp"
                    android:textColor="@color/auth_brand"
                    android:textSize="11sp"
                    android:textStyle="bold" />

                <TextView
                    android:id="@+id/tv_prompt"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="16dp"
                    android:textColor="@color/auth_foreground"
                    android:textSize="16sp"
                    android:textStyle="bold" />

                <RadioGroup
                    android:id="@+id/group_options"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="16dp"
                    android:visibility="gone" />

                <EditText
                    android:id="@+id/et_answer"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="16dp"
                    android:background="@drawable/bg_auth_input"
                    android:hint="@string/quiz_hint_answer"
                    android:inputType="textMultiLine"
                    android:minHeight="56dp"
                    android:padding="14dp"
                    android:textColor="@color/auth_foreground"
                    android:textColorHint="@color/auth_muted_foreground"
                    android:textSize="14sp"
                    android:visibility="gone" />

                <LinearLayout
                    android:id="@+id/layout_feedback"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="20dp"
                    android:background="@color/auth_card"
                    android:orientation="vertical"
                    android:padding="16dp"
                    android:visibility="gone">

                    <TextView
                        android:id="@+id/tv_feedback_result"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:textSize="14sp"
                        android:textStyle="bold" />

                    <TextView
                        android:id="@+id/tv_feedback_correct"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="8dp"
                        android:textColor="@color/auth_muted_foreground"
                        android:textSize="13sp"
                        android:visibility="gone" />

                    <TextView
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="12dp"
                        android:text="@string/quiz_explanation_label"
                        android:textAllCaps="true"
                        android:textColor="@color/auth_muted_foreground"
                        android:textSize="10sp"
                        android:textStyle="bold" />

                    <TextView
                        android:id="@+id/tv_explanation"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="6dp"
                        android:textColor="@color/auth_foreground"
                        android:textSize="14sp" />
                </LinearLayout>
            </LinearLayout>
        </ScrollView>

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btn_primary"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginHorizontal="@dimen/auth_screen_padding_horizontal"
            android:layout_marginBottom="24dp"
            android:text="@string/quiz_check"
            app:backgroundTint="@color/auth_brand"
            app:cornerRadius="12dp" />
    </LinearLayout>

    <LinearLayout
        android:id="@+id/layout_result"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:gravity="center"
        android:orientation="vertical"
        android:padding="24dp"
        android:visibility="gone">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/quiz_result_title"
            android:textColor="@color/auth_foreground"
            android:textSize="22sp"
            android:textStyle="bold" />

        <TextView
            android:id="@+id/tv_result_score"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="12dp"
            android:textColor="@color/auth_foreground"
            android:textSize="18sp"
            android:textStyle="bold" />

        <TextView
            android:id="@+id/tv_result_xp"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:textColor="@color/auth_brand"
            android:textSize="16sp"
            android:textStyle="bold" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btn_done"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="28dp"
            android:text="@string/quiz_done"
            app:backgroundTint="@color/auth_brand"
            app:cornerRadius="12dp" />
    </LinearLayout>
</FrameLayout>
```

---

## Step 9) `QuizActivity` — generate + play + XP

**Giải thích:**  
1. Intent `EXTRA_SUBJECT`  
2. Load ≥2 QA recent cùng subject  
3. Call Gemini → parse JSON `items`  
4. Insert `QuizSession` + `QuizItem`  
5. Từng câu: Check → feedback → Next  
6. Finish: `completed = true`, `xp += score * 10`, hiện result  

**Các điểm Kotlin trong file:**

| Điểm | Chi tiết |
|------|----------|
| `companion object` | `const val EXTRA_SUBJECT` public để Answer/Home/History dùng `QuizActivity.EXTRA_SUBJECT`; các hằng còn lại `private const val` |
| `lateinit var` | DAO, executor, `subject`, `session` và mọi view được gán sau `onCreate` / sau khi generate |
| `subjectExtra.isNullOrEmpty()` | Kiểm tra extra; sau đó gán vào `subject` (kiểu `String` non-null) |
| `minOf(arr.length(), TARGET_ITEMS)` | Giới hạn số câu tối đa |
| `options?.toString() ?: "[]"` | Safe call + Elvis khi Gemini không trả `options` |
| `item.correct` | Property map cột `isCorrect` (Step 2) |
| `items.count { it.correct == true }` | Đếm câu đúng; `null` (chưa trả lời) không tính |
| `::executor.isInitialized` | Trong `onDestroy`, tránh lỗi khi Activity `finish()` sớm trước khi tạo executor |

**File:** `app/src/main/java/com/aistudy/aistudy/quiz/QuizActivity.kt` (**mới** — copy nguyên)

```kotlin
package com.aistudy.aistudy.quiz

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.aistudy.aistudy.BuildConfig
import com.aistudy.aistudy.R
import com.aistudy.aistudy.ask.GeminiClient
import com.aistudy.aistudy.auth.LoginActivity
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.ProfileDao
import com.aistudy.aistudy.database.QuestionAnswer
import com.aistudy.aistudy.database.QuestionAnswerDao
import com.aistudy.aistudy.database.QuizItem
import com.aistudy.aistudy.database.QuizItemDao
import com.aistudy.aistudy.database.QuizSession
import com.aistudy.aistudy.database.QuizSessionDao
import com.google.android.material.button.MaterialButton
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class QuizActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SUBJECT = "subject"

        private const val MIN_SOURCE_QA = 2
        private const val RECENT_LIMIT = 5
        private const val TARGET_ITEMS = 4
        private const val XP_PER_CORRECT = 10
    }

    private lateinit var qaDao: QuestionAnswerDao
    private lateinit var sessionDao: QuizSessionDao
    private lateinit var itemDao: QuizItemDao
    private lateinit var profileDao: ProfileDao
    private lateinit var executor: ExecutorService

    private var userId: Long = -1
    private lateinit var subject: String
    private lateinit var session: QuizSession
    private val items = mutableListOf<QuizItem>()
    private var currentIndex = 0
    private var answered = false

    private lateinit var layoutLoading: LinearLayout
    private lateinit var layoutQuiz: LinearLayout
    private lateinit var layoutResult: LinearLayout
    private lateinit var tvQuizTitle: TextView
    private lateinit var tvProgress: TextView
    private lateinit var tvItemType: TextView
    private lateinit var tvPrompt: TextView
    private lateinit var groupOptions: RadioGroup
    private lateinit var etAnswer: EditText
    private lateinit var layoutFeedback: LinearLayout
    private lateinit var tvFeedbackResult: TextView
    private lateinit var tvFeedbackCorrect: TextView
    private lateinit var tvExplanation: TextView
    private lateinit var btnPrimary: MaterialButton
    private lateinit var tvResultScore: TextView
    private lateinit var tvResultXp: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_quiz)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val subjectExtra = intent.getStringExtra(EXTRA_SUBJECT)
        if (subjectExtra.isNullOrEmpty()) {
            Toast.makeText(this, R.string.quiz_error, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        subject = subjectExtra

        userId = getSharedPreferences(LoginActivity.PREFS_AUTH, MODE_PRIVATE)
            .getLong(LoginActivity.KEY_USER_ID, -1)
        if (userId == -1L) {
            finish()
            return
        }

        val db = AppDatabase.getInstance(this)
        qaDao = db.questionAnswerDao()
        sessionDao = db.quizSessionDao()
        itemDao = db.quizItemDao()
        profileDao = db.profileDao()
        executor = Executors.newSingleThreadExecutor()

        layoutLoading = findViewById(R.id.layout_loading)
        layoutQuiz = findViewById(R.id.layout_quiz)
        layoutResult = findViewById(R.id.layout_result)
        tvQuizTitle = findViewById(R.id.tv_quiz_title)
        tvProgress = findViewById(R.id.tv_progress)
        tvItemType = findViewById(R.id.tv_item_type)
        tvPrompt = findViewById(R.id.tv_prompt)
        groupOptions = findViewById(R.id.group_options)
        etAnswer = findViewById(R.id.et_answer)
        layoutFeedback = findViewById(R.id.layout_feedback)
        tvFeedbackResult = findViewById(R.id.tv_feedback_result)
        tvFeedbackCorrect = findViewById(R.id.tv_feedback_correct)
        tvExplanation = findViewById(R.id.tv_explanation)
        btnPrimary = findViewById(R.id.btn_primary)
        tvResultScore = findViewById(R.id.tv_result_score)
        tvResultXp = findViewById(R.id.tv_result_xp)
        val btnBack = findViewById<ImageButton>(R.id.btn_back)
        val btnDone = findViewById<MaterialButton>(R.id.btn_done)

        tvQuizTitle.text = getString(R.string.quiz_practice_on, subjectLabel(subject))
        btnBack.setOnClickListener { finish() }
        btnDone.setOnClickListener { finish() }
        btnPrimary.setOnClickListener { onPrimaryClick() }

        startGenerate()
    }

    private fun startGenerate() {
        val sources = qaDao.getRecentByUserIdAndSubject(userId, subject, RECENT_LIMIT)
        if (sources.size < MIN_SOURCE_QA) {
            Toast.makeText(this, R.string.quiz_need_more, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        if (BuildConfig.GEMINI_API_KEY.isEmpty() || BuildConfig.GEMINI_API_KEY == "YOUR_KEY_HERE") {
            Toast.makeText(this, R.string.quiz_no_api_key, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        showLoading()
        val prompt = buildQuizPrompt(sources)
        val sourceIds = JSONArray()
        for (qa in sources) {
            sourceIds.put(qa.id)
        }

        executor.execute {
            try {
                val raw = GeminiClient.ask(BuildConfig.GEMINI_API_KEY, prompt)
                val jsonText = extractJson(raw)
                val root = JSONObject(jsonText)
                val arr = root.getJSONArray("items")
                if (arr.length() == 0) {
                    throw Exception("empty items")
                }

                val newSession = QuizSession()
                newSession.userId = userId
                newSession.subject = subject
                newSession.sourceQaIds = sourceIds.toString()
                newSession.createdAt = System.currentTimeMillis()
                newSession.score = 0
                newSession.total = 0
                newSession.completed = false
                val sessionId = sessionDao.insert(newSession)
                newSession.id = sessionId

                val generated = mutableListOf<QuizItem>()
                val limit = minOf(arr.length(), TARGET_ITEMS)
                for (i in 0 until limit) {
                    val obj = arr.getJSONObject(i)
                    val item = QuizItem()
                    item.sessionId = sessionId
                    item.type = normalizeType(obj.optString("type", "mcq"))
                    item.prompt = obj.optString("prompt", "").trim()
                    item.correctAnswer = obj.optString("correctAnswer", "").trim()
                    item.explanation = obj.optString("explanation", "").trim()
                    val options = obj.optJSONArray("options")
                    item.optionsJson = options?.toString() ?: "[]"
                    item.userAnswer = null
                    item.correct = null
                    if (item.prompt.isNullOrEmpty() || item.correctAnswer.isNullOrEmpty()) {
                        continue
                    }
                    item.id = itemDao.insert(item)
                    generated.add(item)
                }

                if (generated.isEmpty()) {
                    throw Exception("no valid items")
                }

                newSession.total = generated.size
                sessionDao.update(newSession)

                runOnUiThread {
                    session = newSession
                    items.clear()
                    items.addAll(generated)
                    currentIndex = 0
                    answered = false
                    showQuiz()
                    bindCurrentItem()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, R.string.quiz_error, Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }

    private fun onPrimaryClick() {
        if (!answered) {
            checkAnswer()
        } else if (currentIndex < items.size - 1) {
            currentIndex++
            answered = false
            bindCurrentItem()
        } else {
            finishQuiz()
        }
    }

    private fun checkAnswer() {
        if (items.isEmpty() || currentIndex >= items.size) {
            return
        }
        val item = items[currentIndex]
        val userAnswer: String
        if (item.type == "mcq") {
            val checkedId = groupOptions.checkedRadioButtonId
            if (checkedId == -1) {
                Toast.makeText(this, R.string.quiz_select_option, Toast.LENGTH_SHORT).show()
                return
            }
            val selected = findViewById<RadioButton>(checkedId)
            userAnswer = selected.text.toString().trim()
        } else {
            userAnswer = etAnswer.text?.toString()?.trim() ?: ""
            if (userAnswer.isEmpty()) {
                Toast.makeText(this, R.string.quiz_enter_answer, Toast.LENGTH_SHORT).show()
                return
            }
        }

        val correct = normalizeAnswer(userAnswer) == normalizeAnswer(item.correctAnswer)
        item.userAnswer = userAnswer
        item.correct = correct
        itemDao.update(item)

        answered = true
        layoutFeedback.visibility = View.VISIBLE
        if (correct) {
            tvFeedbackResult.setText(R.string.quiz_correct)
            tvFeedbackResult.setTextColor(ContextCompat.getColor(this, R.color.auth_brand))
            tvFeedbackCorrect.visibility = View.GONE
        } else {
            tvFeedbackResult.setText(R.string.quiz_incorrect)
            tvFeedbackResult.setTextColor(ContextCompat.getColor(this, R.color.streak))
            tvFeedbackCorrect.visibility = View.VISIBLE
            tvFeedbackCorrect.text = getString(R.string.quiz_correct_answer_label, item.correctAnswer)
        }
        tvExplanation.text = if (item.explanation.isNullOrEmpty()) "—" else item.explanation

        setInputsEnabled(false)
        if (currentIndex < items.size - 1) {
            btnPrimary.setText(R.string.quiz_next)
        } else {
            btnPrimary.setText(R.string.quiz_finish)
        }
    }

    private fun finishQuiz() {
        val score = items.count { it.correct == true }
        val xpGain = score * XP_PER_CORRECT
        session.score = score
        session.total = items.size
        session.completed = true
        sessionDao.update(session)

        val profile = profileDao.getByUserId(userId)
        if (profile != null && xpGain > 0) {
            profile.xp += xpGain
            profileDao.update(profile)
        }

        layoutQuiz.visibility = View.GONE
        layoutLoading.visibility = View.GONE
        layoutResult.visibility = View.VISIBLE
        tvResultScore.text = getString(R.string.quiz_result_score, score, items.size)
        tvResultXp.text = getString(R.string.quiz_result_xp, xpGain)
    }

    private fun bindCurrentItem() {
        val item = items[currentIndex]
        tvProgress.text = getString(R.string.quiz_progress, currentIndex + 1, items.size)
        tvItemType.text = typeLabel(item.type)
        tvPrompt.text = item.prompt
        layoutFeedback.visibility = View.GONE
        btnPrimary.setText(R.string.quiz_check)
        setInputsEnabled(true)

        groupOptions.removeAllViews()
        groupOptions.clearCheck()
        etAnswer.setText("")

        if (item.type == "mcq") {
            groupOptions.visibility = View.VISIBLE
            etAnswer.visibility = View.GONE
            try {
                val options = JSONArray(item.optionsJson ?: "[]")
                for (i in 0 until options.length()) {
                    val option = options.getString(i)
                    val rb = RadioButton(this)
                    rb.id = View.generateViewId()
                    rb.text = option
                    rb.setTextColor(ContextCompat.getColor(this, R.color.auth_foreground))
                    rb.setPadding(8, 16, 8, 16)
                    groupOptions.addView(rb)
                }
            } catch (ignored: Exception) {
            }
        } else {
            groupOptions.visibility = View.GONE
            etAnswer.visibility = View.VISIBLE
        }
    }

    private fun setInputsEnabled(enabled: Boolean) {
        for (i in 0 until groupOptions.childCount) {
            groupOptions.getChildAt(i).isEnabled = enabled
        }
        etAnswer.isEnabled = enabled
    }

    private fun showLoading() {
        layoutLoading.visibility = View.VISIBLE
        layoutQuiz.visibility = View.GONE
        layoutResult.visibility = View.GONE
    }

    private fun showQuiz() {
        layoutLoading.visibility = View.GONE
        layoutQuiz.visibility = View.VISIBLE
        layoutResult.visibility = View.GONE
    }

    private fun buildQuizPrompt(sources: List<QuestionAnswer>): String {
        val sb = StringBuilder()
        sb.append("You are AI Study Mentor. Create a practice quiz from the student's recent questions.\n")
            .append("Return ONLY valid JSON (no markdown):\n")
            .append("{\n")
            .append("  \"items\": [\n")
            .append("    {\n")
            .append("      \"type\": \"mcq\",\n")
            .append("      \"prompt\": \"question text\",\n")
            .append("      \"options\": [\"A\", \"B\", \"C\", \"D\"],\n")
            .append("      \"correctAnswer\": \"exact matching option text\",\n")
            .append("      \"explanation\": \"why this is correct\"\n")
            .append("    },\n")
            .append("    {\n")
            .append("      \"type\": \"short\",\n")
            .append("      \"prompt\": \"question text\",\n")
            .append("      \"options\": [],\n")
            .append("      \"correctAnswer\": \"expected short answer\",\n")
            .append("      \"explanation\": \"brief explanation\"\n")
            .append("    },\n")
            .append("    {\n")
            .append("      \"type\": \"blank\",\n")
            .append("      \"prompt\": \"Fill in the blank: ...\",\n")
            .append("      \"options\": [],\n")
            .append("      \"correctAnswer\": \"word or phrase\",\n")
            .append("      \"explanation\": \"brief explanation\"\n")
            .append("    }\n")
            .append("  ]\n")
            .append("}\n\n")
            .append("Rules:\n")
            .append("- Create exactly ").append(TARGET_ITEMS).append(" items\n")
            .append("- Mix types: include at least one mcq, one short, one blank if possible\n")
            .append("- For mcq: options must have exactly 4 distinct choices; correctAnswer must equal one option exactly\n")
            .append("- Subject: ").append(subject).append("\n")
            .append("- Questions must be based on the source material below (similar concepts, not identical copies)\n")
            .append("- English only\n\n")
            .append("Source questions:\n")
        for ((i, qa) in sources.withIndex()) {
            sb.append(i + 1).append(". Q: ").append(qa.question)
                .append("\n   Final: ").append(qa.finalAnswer)
                .append("\n   Difficulty: ").append(qa.difficulty ?: "medium")
                .append("\n")
        }
        return sb.toString()
    }

    private fun normalizeType(type: String): String {
        return when (type) {
            "short", "blank", "mcq" -> type
            else -> "mcq"
        }
    }

    private fun normalizeAnswer(value: String?): String {
        if (value == null) {
            return ""
        }
        return value.trim().replace(Regex("\\s+"), " ").lowercase()
    }

    private fun typeLabel(type: String?): String {
        return when (type) {
            "short" -> getString(R.string.quiz_type_short)
            "blank" -> getString(R.string.quiz_type_blank)
            else -> getString(R.string.quiz_type_mcq)
        }
    }

    private fun subjectLabel(value: String?): String {
        return when (value) {
            "math" -> getString(R.string.subject_math)
            "science" -> getString(R.string.subject_science)
            "code" -> getString(R.string.subject_code)
            "history" -> getString(R.string.subject_history)
            "language" -> getString(R.string.subject_language)
            else -> getString(R.string.subject_other)
        }
    }

    private fun extractJson(raw: String): String {
        val text = raw.trim()
        if (text.startsWith("```")) {
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            if (start >= 0 && end > start) {
                return text.substring(start, end + 1)
            }
        }
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1)
        }
        return text
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::executor.isInitialized) {
            executor.shutdown()
        }
    }
}
```

**Kiểm tra nhanh:** Intent thiếu subject → finish + toast; ít hơn 2 QA → toast `quiz_need_more`; thiếu API key → toast; Gemini lỗi → toast `quiz_error`.

---

## Step 10) Đăng ký Manifest

**File:** `app/src/main/AndroidManifest.xml`

**Thêm activity ngay sau `AnswerActivity`:**

```xml
        <activity
            android:name=".quiz.QuizActivity"
            android:exported="false" />
```

**Kiểm tra nhanh:** Launch từ code không crash ActivityNotFoundException.

---

## Step 11) Entry — Answer “Practice”

### 11a) Layout

**File:** `app/src/main/res/layout/activity_answer.xml`

**Trước `btn_mark_reviewed`, thêm nút Practice; đổi `layout_marginTop` của Mark reviewed thành `12dp`:**

```xml
            <com.google.android.material.button.MaterialButton
                android:id="@+id/btn_practice"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="16dp"
                android:text="@string/quiz_practice"
                app:backgroundTint="@color/auth_brand"
                app:cornerRadius="12dp" />

            <com.google.android.material.button.MaterialButton
                android:id="@+id/btn_mark_reviewed"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="12dp"
                android:text="@string/answer_mark_reviewed"
                app:backgroundTint="@color/auth_brand"
                app:cornerRadius="12dp" />
```

### 11b) Activity

**File:** `app/src/main/java/com/aistudy/aistudy/answer/AnswerActivity.kt`

**Import thêm (chèn đúng thứ tự alphabet):**

```kotlin
import android.content.Intent
import com.aistudy.aistudy.quiz.QuizActivity
```

**Sau `btnMarkReviewed = findViewById(R.id.btn_mark_reviewed)`, thêm:**

```kotlin
        val btnPractice = findViewById<MaterialButton>(R.id.btn_practice)
```

**Sau `tvDifficulty.text = difficultyLabel(item.difficulty)`, thêm:**

```kotlin
        btnPractice.text = getString(R.string.quiz_practice_on, subjectLabel(item.subject))
        btnPractice.setOnClickListener {
            val intent = Intent(this, QuizActivity::class.java)
            intent.putExtra(QuizActivity.EXTRA_SUBJECT, item.subject)
            startActivity(intent)
        }
```

`QuizActivity::class.java` là cách lấy `Class` cho `Intent`; `QuizActivity.EXTRA_SUBJECT` đọc trực tiếp từ `companion object`.

**Kiểm tra nhanh:** Mở Answer → nút “Practice on Math” (theo subject) → vào QuizActivity.

---

## Step 12) Entry — Home

### 12a) Layout

**File:** `app/src/main/res/layout/fragment_home.xml`

**Ngay sau `btn_ask`, thêm:**

```xml
        <com.google.android.material.button.MaterialButton
            android:id="@+id/btn_practice_recent"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="12dp"
            android:text="@string/quiz_practice"
            android:visibility="gone"
            app:backgroundTint="@color/auth_brand"
            app:cornerRadius="12dp" />
```

### 12b) Fragment — file đầy đủ sau Phase 11

**File:** `app/src/main/java/com/aistudy/aistudy/home/HomeFragment.kt`

**Giải thích:**  
`qaDao` lưu thành property `QuestionAnswerDao?` để `openPractice` dùng lại; trong `loadDashboard` dùng biến local `qaDao` (non-null) rồi gán `this.qaDao = qaDao`. `latestSubject?.let { openPractice(it) }` chỉ mở quiz khi đã có subject. `latest.subject?.let { qaDao.countBySubject(userId, it) } ?: 0` xử lý subject `null`. `maxOf` / `minOf` giới hạn level và phần trăm XP. `selectTab` dùng `is MainActivity` để kiểm tra kiểu Activity.

Thay toàn bộ bằng:

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
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton

class HomeFragment : Fragment() {

    companion object {
        private const val XP_MAX = 1200
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
        val level = maxOf(1, xp / 100)
        val xpPct = minOf(100, Math.round((xp * 100f) / XP_MAX))

        tvLevelBadge.text = level.toString()
        tvStreak.text = getString(R.string.home_streak_days, streak)
        tvXpLabel.text = getString(R.string.home_xp_progress, xp, XP_MAX)
        tvXpPercent.text = getString(R.string.home_xp_percent, xpPct)
        progressXp.progress = xpPct
        tvGreeting.text = getString(R.string.home_greeting, fullName)

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

**Hành vi:**
- `btn_practice_recent` hiện khi latest subject có ≥2 QA
- Tap card subject (`card_subject_math` …) → `openPractice(subject)`; ít hơn 2 QA → toast

---

## Step 13) Entry — History

### 13a) Layout

**File:** `app/src/main/res/layout/fragment_history.xml`

1. Thêm `xmlns:app` trên root `LinearLayout`:

```xml
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
```

2. Sau `HorizontalScrollView` (chips), trước `FrameLayout` list, thêm:

```xml
    <com.google.android.material.button.MaterialButton
        android:id="@+id/btn_practice"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="12dp"
        android:text="@string/quiz_practice"
        android:visibility="gone"
        app:backgroundTint="@color/auth_brand"
        app:cornerRadius="12dp" />
```

### 13b) Fragment — file đầy đủ sau Phase 11

**File:** `app/src/main/java/com/aistudy/aistudy/history/HistoryFragment.kt`

**Giải thích:**  
Listener của adapter viết bằng `object : HistoryAdapter.Listener { ... }` (object expression). Hai provider nhãn môn / thời gian truyền bằng function reference `::subjectLabel`, `::relativeTime`. Trong click listener của `btnPractice`, `return@setOnClickListener` thoát khỏi lambda. So sánh chuỗi dùng `==` / `!=` trực tiếp.

Thay toàn bộ bằng:

```kotlin
package com.aistudy.aistudy.history

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aistudy.aistudy.R
import com.aistudy.aistudy.answer.AnswerActivity
import com.aistudy.aistudy.auth.LoginActivity
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.QuestionAnswer
import com.aistudy.aistudy.database.QuestionAnswerDao
import com.aistudy.aistudy.quiz.QuizActivity
import com.google.android.material.button.MaterialButton

class HistoryFragment : Fragment() {

    companion object {
        private const val MIN_PRACTICE_QA = 2
    }

    private lateinit var etSearch: EditText
    private lateinit var chipAll: TextView
    private lateinit var chipMath: TextView
    private lateinit var chipScience: TextView
    private lateinit var chipCode: TextView
    private lateinit var chipHistory: TextView
    private lateinit var chipLanguage: TextView
    private lateinit var chipStarred: TextView
    private lateinit var btnPractice: MaterialButton
    private lateinit var rvHistory: RecyclerView
    private lateinit var tvEmpty: TextView

    private lateinit var adapter: HistoryAdapter
    private var qaDao: QuestionAnswerDao? = null
    private var userId = -1L

    private val allItems = mutableListOf<QuestionAnswer>()
    private var subjectFilter = "all"
    private var starredOnly = false
    private var query = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        etSearch = view.findViewById(R.id.et_search)
        chipAll = view.findViewById(R.id.chip_all)
        chipMath = view.findViewById(R.id.chip_math)
        chipScience = view.findViewById(R.id.chip_science)
        chipCode = view.findViewById(R.id.chip_code)
        chipHistory = view.findViewById(R.id.chip_history)
        chipLanguage = view.findViewById(R.id.chip_language)
        chipStarred = view.findViewById(R.id.chip_starred)
        btnPractice = view.findViewById(R.id.btn_practice)
        rvHistory = view.findViewById(R.id.rv_history)
        tvEmpty = view.findViewById(R.id.tv_empty)

        adapter = HistoryAdapter(
            object : HistoryAdapter.Listener {
                override fun onItemClick(qa: QuestionAnswer) {
                    val intent = Intent(requireContext(), AnswerActivity::class.java)
                    intent.putExtra(AnswerActivity.EXTRA_QA_ID, qa.id)
                    startActivity(intent)
                }

                override fun onStarClick(qa: QuestionAnswer) {
                    val dao = qaDao ?: return
                    dao.toggleStar(qa.id)
                    loadHistory()
                }
            },
            ::subjectLabel,
            ::relativeTime
        )
        rvHistory.layoutManager = LinearLayoutManager(requireContext())
        rvHistory.adapter = adapter

        chipAll.setOnClickListener {
            subjectFilter = "all"
            starredOnly = false
            refreshChips()
            applyFilter()
        }
        chipMath.setOnClickListener {
            subjectFilter = "math"
            starredOnly = false
            refreshChips()
            applyFilter()
        }
        chipScience.setOnClickListener {
            subjectFilter = "science"
            starredOnly = false
            refreshChips()
            applyFilter()
        }
        chipCode.setOnClickListener {
            subjectFilter = "code"
            starredOnly = false
            refreshChips()
            applyFilter()
        }
        chipHistory.setOnClickListener {
            subjectFilter = "history"
            starredOnly = false
            refreshChips()
            applyFilter()
        }
        chipLanguage.setOnClickListener {
            subjectFilter = "language"
            starredOnly = false
            refreshChips()
            applyFilter()
        }
        chipStarred.setOnClickListener {
            subjectFilter = "all"
            starredOnly = true
            refreshChips()
            applyFilter()
        }
        btnPractice.setOnClickListener {
            if (subjectFilter == "all" || starredOnly) {
                return@setOnClickListener
            }
            val dao = qaDao
            if (dao == null || userId == -1L) {
                return@setOnClickListener
            }
            val count = dao.countBySubject(userId, subjectFilter)
            if (count < MIN_PRACTICE_QA) {
                Toast.makeText(requireContext(), R.string.quiz_need_more, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val intent = Intent(requireContext(), QuizActivity::class.java)
            intent.putExtra(QuizActivity.EXTRA_SUBJECT, subjectFilter)
            startActivity(intent)
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                query = s?.toString()?.trim() ?: ""
                applyFilter()
            }

            override fun afterTextChanged(s: Editable?) {
            }
        })

        refreshChips()
    }

    override fun onResume() {
        super.onResume()
        loadHistory()
    }

    private fun loadHistory() {
        if (context == null) {
            return
        }

        userId = requireContext()
            .getSharedPreferences(LoginActivity.PREFS_AUTH, Context.MODE_PRIVATE)
            .getLong(LoginActivity.KEY_USER_ID, -1L)
        if (userId == -1L) {
            return
        }

        val dao = AppDatabase.getInstance(requireContext()).questionAnswerDao()
        qaDao = dao
        allItems.clear()
        allItems.addAll(dao.getAllByUserId(userId))
        applyFilter()
    }

    private fun applyFilter() {
        val filtered = mutableListOf<QuestionAnswer>()
        for (qa in allItems) {
            if (subjectFilter != "all" && subjectFilter != qa.subject) {
                continue
            }
            if (starredOnly && !qa.starred) {
                continue
            }
            if (query.isNotEmpty() && qa.question?.lowercase()?.contains(query.lowercase()) != true) {
                continue
            }
            filtered.add(qa)
        }
        adapter.submitList(filtered)
        val empty = filtered.isEmpty()
        tvEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        rvHistory.visibility = if (empty) View.GONE else View.VISIBLE
        refreshPracticeButton()
    }

    private fun refreshPracticeButton() {
        val show = !starredOnly && subjectFilter != "all"
        if (!show) {
            btnPractice.visibility = View.GONE
            return
        }
        btnPractice.visibility = View.VISIBLE
        btnPractice.text = getString(R.string.quiz_practice_on, subjectLabel(subjectFilter))
    }

    private fun refreshChips() {
        styleChip(chipAll, subjectFilter == "all" && !starredOnly)
        styleChip(chipMath, subjectFilter == "math" && !starredOnly)
        styleChip(chipScience, subjectFilter == "science" && !starredOnly)
        styleChip(chipCode, subjectFilter == "code" && !starredOnly)
        styleChip(chipHistory, subjectFilter == "history" && !starredOnly)
        styleChip(chipLanguage, subjectFilter == "language" && !starredOnly)
        styleChip(chipStarred, starredOnly)
    }

    private fun styleChip(chip: TextView, active: Boolean) {
        if (active) {
            chip.setBackgroundResource(R.drawable.bg_history_chip_active)
            chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.auth_primary_foreground))
        } else {
            chip.setBackgroundResource(R.drawable.bg_history_chip)
            chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.auth_foreground))
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

    private fun relativeTime(createdAt: Long): String {
        val diff = System.currentTimeMillis() - createdAt
        val hours = diff / 3_600_000L
        if (hours < 1) {
            return getString(R.string.history_just_now)
        }
        if (hours < 24) {
            return getString(R.string.history_hours_ago, hours.toInt())
        }
        return getString(R.string.history_days_ago, (hours / 24).toInt())
    }
}
```

**Hành vi:** Chip môn (không phải All/Starred) → hiện “Practice on {Subject}”; bấm → Quiz; ít hơn 2 QA → toast.

---

## Step 14) Test tay (checklist)

### Chuẩn bị

1. `local.properties` có `GEMINI_API_KEY=...` hợp lệ.
2. Cài lại / mở app sau bump v6 → login + onboard nếu cần.
3. Ask **≥2 câu cùng môn** (vd. Math) — text hoặc ảnh đều OK.

### Checklist Entry

1. **Answer** → “Practice on Math” → loading → quiz.
2. **Home** → nút Practice recent hiện (khi ≥2 QA môn latest) → vào quiz.
3. **Home** → tap card Math (≥2) → quiz; tap môn ít hơn 2 → toast `quiz_need_more`.
4. **History** → chip Math → nút Practice → quiz; chip All → nút ẩn.

### Checklist Quiz play

1. Loading “Generating practice questions…” rồi hiện câu 1/`total`.
2. **MCQ:** chọn option → Check → Correct/Incorrect + explanation; Next.
3. **Short/Blank:** gõ đáp án → Check; so khớp không phân biệt hoa thường / khoảng trắng thừa.
4. Câu cuối → “See results” → Score `X / Y` và `+(X*10) XP`.
5. Done → về màn trước; Home XP tăng đúng.

### Checklist Persist (DB)

1. Device Explorer / App Inspection: bảng `quiz_sessions` có row `completed=1`, `score`, `total`, `subject`.
2. `quiz_items` có đủ câu, cột `userAnswer` / `isCorrect` đã cập nhật (cột `isCorrect` là property `correct` trong Kotlin).
3. `profiles.xp` tăng = số đúng × 10.

### Lỗi thường gặp

| Triệu chứng | Nguyên nhân hay gặp | Cách xử lý |
|-------------|---------------------|------------|
| Toast need more | ít hơn 2 QA subject | Ask thêm cùng môn |
| Toast no API key | Thiếu `GEMINI_API_KEY` | `local.properties` + rebuild |
| Toast quiz error | Gemini JSON lỗi / network | Logcat; thử lại; check model |
| ActivityNotFound | Quên Manifest | Step 10 |
| Schema crash | Quên version 6 / entity | Step 5 |
| KSP báo không tìm thấy setter cho field `isCorrect` | Property Kotlin đặt tên `isCorrect` | Đổi thành `correct` + `@ColumnInfo(name = "isCorrect")` (Step 2) |
| `Unresolved reference: isCorrect` trong `QuizActivity` | Code còn dùng tên cũ | Dùng `item.correct` |
| `Unresolved reference: getRecentByUserIdAndSubject` | Chưa thêm query | Step 6 |
| `Unresolved reference: quizSessionDao` / `quizItemDao` | Chưa khai báo abstract fun trong `AppDatabase` | Step 5 |
| `lateinit property session has not been initialized` | `layout_quiz` hiện trước khi generate xong | Chỉ gọi `showQuiz()` trong `runOnUiThread` sau khi gán `session = newSession` (Step 9) |
| Data biến mất | Destructive migration | Đúng kỳ vọng Phase 11 |
| MCQ luôn sai | `correctAnswer` ≠ text option | Prompt yêu cầu exact match (đã có trong code) |
| Practice button History không hiện | Đang All/Starred | Chọn chip môn |

---

## Mapping với plan / FR

| FR / Plan | Android Phase 11 |
|-----------|------------------|
| AI Practice / Quiz | Gemini từ lịch sử QA → `QuizItem` mix type |
| Instant feedback | Check → đúng/sai + explanation |
| XP hoàn thành quiz | `xp += score * 10` |
| Entry Answer / Home / History | Step 11–13 |
| Data cho Progress accuracy | `QuizItemDao` count methods (UI ở P12) |

---

## Checklist hoàn thành Phase 11

- [ ] `QuizSession` + `QuizItem` entities (`correct` map cột `isCorrect`)
- [ ] `QuizSessionDao` + `QuizItemDao`
- [ ] `AppDatabase` version **6** + DAO accessors
- [ ] `getRecentByUserIdAndSubject`
- [ ] `strings_quiz.xml` + `activity_quiz.xml`
- [ ] `QuizActivity` generate / play / XP
- [ ] Manifest đăng ký `QuizActivity`
- [ ] Answer / Home / History entry points
- [ ] Test tay theo Step 14
- [ ] Compile: `./gradlew :app:compileDebugKotlin` OK

**Tiếp theo:** Phase 12 — Progress v2 (quiz accuracy, time reviewing, AI insights, topic review).
