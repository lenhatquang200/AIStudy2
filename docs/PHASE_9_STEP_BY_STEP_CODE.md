# Phase 9: Answer enrichment + metadata — Hướng dẫn từng bước


Mục tiêu Phase 9:

1. **Difficulty** — AI trả `easy|medium|hard`, lưu Room, hiện badge trên Answer
2. **Languages** — subject `language` hiện trên Home / History / Progress (Ask đã có sẵn)
3. **Answer extras** — Simplify / Alternative / Concepts / Mistakes / Follow-ups (Gemini on-demand → cache `extrasJson`)
4. **XP review** — mở Answer lần đầu trong ngày (hoặc bấm Mark reviewed) → `reviewedAt` + **+10 XP**

**Điều kiện trước khi làm:** Phase 0–8 đã xong (QA Room v3, Ask/Answer, Home/History/Progress).

**Không làm ở Phase này:** ảnh (P10), quiz (P11), object `XpRules` (P13).

**Lưu ý DB:** bump Room **v3 → v4** + `fallbackToDestructiveMigration(true)` → data QA cũ trên máy dev **bị xóa** khi cài lại schema. Bình thường cho giai đoạn này.

---

## Tổng quan các file sẽ chạm

| File | Loại | Việc |
|------|------|------|
| `QuestionAnswer.kt` | Sửa | Thêm `difficulty`, `extrasJson`, `reviewedAt` |
| `QuestionAnswerDao.kt` | Sửa | `@Update`, `updateExtras`, `updateReviewedAt` |
| `AppDatabase.kt` | Sửa | `version = 4` |
| `AskFragment.kt` | Sửa | Prompt + parse + lưu `difficulty` |
| `strings.xml` | Sửa | Difficulty, actions, extras, review |
| `bg_difficulty_badge.xml` | **Mới** | Pill badge độ khó |
| `bg_answer_action.xml` | **Mới** | Nút action extras (idle) |
| `bg_answer_action_active.xml` | **Mới** | Nút action extras (active) |
| `activity_answer.xml` | Sửa | Badge + actions + panel + Mark reviewed |
| `AnswerActivity.kt` | Sửa | Load extras Gemini, cache, XP review |
| `strings_home.xml` | Sửa | Icon Language |
| `fragment_home.xml` | Sửa | Card Language |
| `HomeFragment.kt` | Sửa | Bind count language |
| `fragment_history.xml` | Sửa | Chip Language |
| `HistoryFragment.kt` | Sửa | Filter language |
| `fragment_progress.xml` | Sửa | Row Language |
| `ProgressFragment.kt` | Sửa | Count + bar language |

**Không đụng:** `GeminiClient.kt` (vẫn dùng `GeminiClient.ask(apiKey, prompt)`), Auth, Onboarding.

---

## Step 1) Mở rộng entity `QuestionAnswer`

**Giải thích:**  
Room entity = bản ghi 1 câu hỏi/đáp. Phase 9 cần 3 cột mới: độ khó, JSON tip học thêm, thời điểm đã review (phục vụ XP + Progress v2 sau).

Mỗi cột là một property `var` có giá trị mặc định: `String?` cho cột nullable (`difficulty`, `extrasJson`), `Long = 0` cho `reviewedAt` (`0` = chưa review).

**File:** `app/src/main/java/com/aistudy/aistudy/database/QuestionAnswer.kt`

**Thay toàn bộ file bằng:**

```kotlin
package com.aistudy.aistudy.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "question_answers")
class QuestionAnswer {

    @PrimaryKey(autoGenerate = true)
    var id: Long = 0

    var userId: Long = 0
    var subject: String? = null
    var question: String? = null
    var steps: String? = null
    var finalAnswer: String? = null
    var starred: Boolean = false
    var createdAt: Long = 0
    var difficulty: String? = null
    var extrasJson: String? = null
    var reviewedAt: Long = 0
}
```

| Property | Ý nghĩa |
|----------|---------|
| `difficulty` | `"easy"` / `"medium"` / `"hard"` (string từ AI) |
| `extrasJson` | JSON nullable — toàn bộ tip extras; `null` = chưa generate |
| `reviewedAt` | millis; `0` = chưa review |

Phase 10 thêm `imagePath`, Phase 12 thêm `reviewedSeconds` vào cuối class này.

**Kiểm tra nhanh:** Build compile `QuestionAnswer` — chưa bump version thì Room chưa recreate table.

---

## Step 2) DAO — update extras / reviewed

**Giải thích:**  
Insert đã có. Cần `@Update` và `@Query` UPDATE để ghi extras sau khi Gemini trả về, và ghi `reviewedAt` khi user review. `extrasJson` nhận `String?` để khớp property nullable của entity.

**File:** `app/src/main/java/com/aistudy/aistudy/database/QuestionAnswerDao.kt`

**Thêm import (sau `import androidx.room.Query`):**

```kotlin
import androidx.room.Update
```

**Thêm ngay sau `fun insert(qa: QuestionAnswer): Long`:**

```kotlin

    @Update
    fun update(qa: QuestionAnswer)
```

**Thêm ngay sau `fun toggleStar(id: Long)`:**

```kotlin

    @Query("UPDATE question_answers SET extrasJson = :extrasJson WHERE id = :id")
    fun updateExtras(id: Long, extrasJson: String?)

    @Query("UPDATE question_answers SET reviewedAt = :reviewedAt WHERE id = :id")
    fun updateReviewedAt(id: Long, reviewedAt: Long)
```

**Kiểm tra nhanh:** Build — KSP sinh lại `QuestionAnswerDao_Impl`, không báo lỗi tên tham số (`:extrasJson`, `:reviewedAt`, `:id` phải trùng tên tham số hàm).

---

## Step 3) Bump `AppDatabase` → version 4

**Giải thích:**  
Đổi schema entity bắt buộc tăng `version`. Project đã có `.fallbackToDestructiveMigration(true)` → dev: xóa DB cũ, tạo lại. Không viết Migration SQL thủ công ở phase này.

**File:** `app/src/main/java/com/aistudy/aistudy/database/AppDatabase.kt`

**Tìm:**

```kotlin
@Database(
    entities = [
        User::class,
        Profile::class,
        QuestionAnswer::class
    ],
    version = 3,
    exportSchema = false
)
```

**Đổi thành:**

```kotlin
@Database(
    entities = [
        User::class,
        Profile::class,
        QuestionAnswer::class
    ],
    version = 4,
    exportSchema = false
)
```

**Kiểm tra nhanh:** Chạy app lần đầu sau đổi → login lại / data QA cũ có thể mất (destructive).

---

## Step 4) Ask — prompt + parse `difficulty`

**Giải thích:**  
Ask đã classify `language`. Phase 9 thêm field `difficulty` trong JSON schema Gemini và lưu xuống Room khi insert QA. `difficulty` khai báo `var` vì có thể bị gán lại thành `"medium"` khi AI trả giá trị lạ.

**File:** `app/src/main/java/com/aistudy/aistudy/ask/AskFragment.kt`

### 4a) Parse sau khi đọc `subject`

**Tìm khối:**

```kotlin
                var subject = json.optString("subject", "other")
                if (!isValidSubject(subject)) {
                    Log.w(TAG, "invalid subject from AI: $subject, fallback to other")
                    subject = "other"
                }

                val stepsArray = json.getJSONArray("steps")
```

**Đổi thành:**

```kotlin
                var subject = json.optString("subject", "other")
                if (!isValidSubject(subject)) {
                    Log.w(TAG, "invalid subject from AI: $subject, fallback to other")
                    subject = "other"
                }

                var difficulty = json.optString("difficulty", "medium")
                if (!isValidDifficulty(difficulty)) {
                    difficulty = "medium"
                }

                val stepsArray = json.getJSONArray("steps")
```

**Thay toàn bộ lời gọi `Log.d(...)` in `"parsed subject=..."` (ngay sau dòng `val finalAnswer = json.getString("finalAnswer")`) bằng:**

```kotlin
                Log.d(
                    TAG, "parsed subject=$subject difficulty=$difficulty" +
                        " steps=${stepsArray.length()}" +
                        " finalAnswerLength=${finalAnswer.length}"
                )
```

**Tìm dòng gán thời điểm tạo QA:**

```kotlin
                qa.createdAt = System.currentTimeMillis()
```

**Đổi thành:**

```kotlin
                qa.createdAt = System.currentTimeMillis()
                qa.difficulty = difficulty
                qa.extrasJson = null
                qa.reviewedAt = 0
```

### 4b) Prompt JSON schema

**Trong `buildPrompt`, thay biểu thức `return` bằng:**

```kotlin
        return "You are AI Study Mentor, a study tutor in English for a $levelText.\n" +
            styleText + "\n" +
            "Always answer in English. Put math formulas in the \"expr\" field of the matching step.\n" +
            "Classify the subject correctly (math/science/code/history/language/other).\n" +
            "Also rate difficulty as easy, medium, or hard for this student.\n\n" +
            "Return ONLY valid JSON (no markdown) with this structure:\n" +
            "{\n" +
            "  \"subject\": \"math|science|code|history|language|other\",\n" +
            "  \"difficulty\": \"easy|medium|hard\",\n" +
            "  \"steps\": [{\"title\": \"optional\", \"body\": \"required\", \"expr\": \"optional\"}],\n" +
            "  \"finalAnswer\": \"string\"\n" +
            "}\n\n" +
            "Question: " + question
```

### 4c) Helper validate difficulty

**Thêm ngay sau hàm `isValidSubject`:**

```kotlin

    private fun isValidDifficulty(difficulty: String): Boolean {
        return difficulty == "easy" ||
            difficulty == "medium" ||
            difficulty == "hard"
    }
```

**Kiểm tra nhanh:** Hỏi 1 câu → Logcat thấy `difficulty=easy|medium|hard`; mở Database Inspector thấy cột mới.

---

## Step 5) Strings Answer (difficulty / extras / review)

**Giải thích:**  
UI tiếng Anh như auth. Không hard-code chữ trong Kotlin/XML.

**File:** `app/src/main/res/values/strings.xml`

**Chèn sau `answer_not_found`:**

```xml
    <string name="answer_difficulty_easy">Easy</string>
    <string name="answer_difficulty_medium">Medium</string>
    <string name="answer_difficulty_hard">Hard</string>
    <string name="answer_action_simplify">Simplify</string>
    <string name="answer_action_alternative">Alternative</string>
    <string name="answer_action_concepts">Concepts</string>
    <string name="answer_action_mistakes">Mistakes</string>
    <string name="answer_action_followups">Follow-ups</string>
    <string name="answer_extras_loading">Generating learning tips…</string>
    <string name="answer_extras_error">Could not generate tips. Try again.</string>
    <string name="answer_extras_empty">Tap an action above to get learning support.</string>
    <string name="answer_mark_reviewed">Mark reviewed (+10 XP)</string>
    <string name="answer_reviewed">Reviewed ✓</string>
    <string name="answer_review_xp_toast">+10 XP for reviewing</string>
```

**Kiểm tra nhanh:** Sync resources — không lỗi thiếu `@string/answer_*`.

---

## Step 6) Drawable badge + action pills

**Giải thích:**  
Badge độ khó dùng nền brand soft. Action chip giống History chip (idle / active).

### 6a) `bg_difficulty_badge.xml` (**mới**)

**Path:** `app/src/main/res/drawable/bg_difficulty_badge.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="@color/auth_brand_soft" />
    <corners android:radius="999dp" />
</shape>
```

### 6b) `bg_answer_action.xml` (**mới**)

**Path:** `app/src/main/res/drawable/bg_answer_action.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<ripple xmlns:android="http://schemas.android.com/apk/res/android"
    android:color="#1A000000">
    <item>
        <shape android:shape="rectangle">
            <solid android:color="@color/auth_card" />
            <corners android:radius="999dp" />
            <stroke
                android:width="1dp"
                android:color="@color/auth_shell_ring" />
        </shape>
    </item>
</ripple>
```

### 6c) `bg_answer_action_active.xml` (**mới**)

**Path:** `app/src/main/res/drawable/bg_answer_action_active.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="@color/auth_brand" />
    <corners android:radius="999dp" />
</shape>
```

**Kiểm tra nhanh:** Preview 3 drawable.

---

## Step 7) Layout `activity_answer.xml`

**Giải thích:**  
Header thêm badge difficulty bên phải subject. Dưới final answer: hàng action + panel extras + nút Mark reviewed.

**File:** `app/src/main/res/layout/activity_answer.xml`

### 7a) Header — thêm `tv_difficulty` sau `tv_subject`

Trong header `LinearLayout` (có `btn_back` + `tv_subject`), **sau** `tv_subject` thêm:

```xml
        <TextView
            android:id="@+id/tv_difficulty"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:background="@drawable/bg_difficulty_badge"
            android:paddingHorizontal="10dp"
            android:paddingVertical="4dp"
            android:textColor="@color/auth_brand"
            android:textSize="11sp"
            android:textStyle="bold" />
```

### 7b) Sau khối Final answer — actions + extras + review

**Trước khi đóng** `LinearLayout` trong `ScrollView`, **sau** card final answer, thêm:

```xml
            <HorizontalScrollView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:scrollbars="none">

                <LinearLayout
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:orientation="horizontal">

                    <TextView
                        android:id="@+id/btn_simplify"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:background="@drawable/bg_answer_action"
                        android:paddingHorizontal="14dp"
                        android:paddingVertical="8dp"
                        android:text="@string/answer_action_simplify"
                        android:textColor="@color/auth_foreground"
                        android:textSize="12sp"
                        android:textStyle="bold" />

                    <TextView
                        android:id="@+id/btn_alternative"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_marginStart="8dp"
                        android:background="@drawable/bg_answer_action"
                        android:paddingHorizontal="14dp"
                        android:paddingVertical="8dp"
                        android:text="@string/answer_action_alternative"
                        android:textColor="@color/auth_foreground"
                        android:textSize="12sp"
                        android:textStyle="bold" />

                    <TextView
                        android:id="@+id/btn_concepts"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_marginStart="8dp"
                        android:background="@drawable/bg_answer_action"
                        android:paddingHorizontal="14dp"
                        android:paddingVertical="8dp"
                        android:text="@string/answer_action_concepts"
                        android:textColor="@color/auth_foreground"
                        android:textSize="12sp"
                        android:textStyle="bold" />

                    <TextView
                        android:id="@+id/btn_mistakes"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_marginStart="8dp"
                        android:background="@drawable/bg_answer_action"
                        android:paddingHorizontal="14dp"
                        android:paddingVertical="8dp"
                        android:text="@string/answer_action_mistakes"
                        android:textColor="@color/auth_foreground"
                        android:textSize="12sp"
                        android:textStyle="bold" />

                    <TextView
                        android:id="@+id/btn_followups"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_marginStart="8dp"
                        android:background="@drawable/bg_answer_action"
                        android:paddingHorizontal="14dp"
                        android:paddingVertical="8dp"
                        android:text="@string/answer_action_followups"
                        android:textColor="@color/auth_foreground"
                        android:textSize="12sp"
                        android:textStyle="bold" />
                </LinearLayout>
            </HorizontalScrollView>

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="12dp"
                android:background="@color/auth_card"
                android:orientation="vertical"
                android:padding="16dp">

                <ProgressBar
                    android:id="@+id/progress_extras"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_gravity="center_horizontal"
                    android:visibility="gone" />

                <TextView
                    android:id="@+id/tv_extras"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:text="@string/answer_extras_empty"
                    android:textColor="@color/auth_foreground"
                    android:textSize="14sp" />
            </LinearLayout>

            <com.google.android.material.button.MaterialButton
                android:id="@+id/btn_mark_reviewed"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="16dp"
                android:text="@string/answer_mark_reviewed"
                app:backgroundTint="@color/auth_brand"
                app:cornerRadius="12dp" />
```

Card final answer giữ `android:layout_marginBottom="20dp"` để cách hàng actions. Root `LinearLayout` phải khai báo `xmlns:app="http://schemas.android.com/apk/res-auto"` (đã có sẵn cho `app:tint` của `btn_back`).

**Kiểm tra nhanh:** Preview layout — thấy badge, 5 chips, panel, nút review.

---

## Step 8) Logic `AnswerActivity` — extras + XP review

**Giải thích:**  
- Badge đọc `item.difficulty`.  
- Tap action: nếu `extrasJson` đã có → hiện field tương ứng; chưa có → gọi Gemini **một lần** lấy cả 5 field → lưu Room → render.  
- Mở Answer lần đầu trong ngày (hoặc bấm Mark reviewed) → `reviewedAt = now`, `profile.xp += XP_REVIEW` (10).

**File:** `app/src/main/java/com/aistudy/aistudy/answer/AnswerActivity.kt`

Điểm chính:

| Thành phần | Việc |
|------------|------|
| `companion object` | `EXTRA_QA_ID` (public `const val`), `XP_REVIEW = 10`, các key JSON, hàm `isSameDay` |
| Keys JSON | `simplify`, `alternative`, `concepts`, `mistakes`, `followUps` |
| `qa` / `qaDao` / `executor` | Property nullable (`?`); hàm dùng `val item = qa ?: return` để lấy bản non-null |
| View extras | `private lateinit var` + `findViewById` trong `onCreate` |
| `maybeAutoReview()` | Gọi `markReviewed()` nếu chưa review **hôm nay** |
| `markReviewed()` | Update `reviewedAt` + XP nếu lần đầu trong ngày |
| `onExtraAction(key)` | Cache hit → show; miss → `loadExtras` |
| `loadExtras` | `GeminiClient.ask` + `updateExtras` trên `Executor`, render qua `runOnUiThread` |
| `buildExtrasPrompt` | Prompt chỉ JSON extras (string template `${...}`) |
| `formatExtra` | String hoặc `JSONArray` (kiểm tra bằng `is`, smart cast) → bullet list |

**Schema extras Gemini trả về:**

```json
{
  "simplify": "...",
  "alternative": "...",
  "concepts": ["...", "..."],
  "mistakes": ["...", "..."],
  "followUps": ["...", "..."]
}
```

**Thay toàn bộ file bằng (bản Phase 9 — chưa có ảnh, ảnh thêm ở Phase 10; nút Practice thêm ở Phase 11; đo thời gian review thêm ở Phase 12; `XpRules` thay `XP_REVIEW` ở Phase 13):**

```kotlin
package com.aistudy.aistudy.answer

import android.graphics.Typeface
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
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
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.ProfileDao
import com.aistudy.aistudy.database.QuestionAnswer
import com.aistudy.aistudy.database.QuestionAnswerDao
import com.google.android.material.button.MaterialButton
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AnswerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_QA_ID = "qa_id"

        private const val XP_REVIEW = 10
        private const val KEY_SIMPLIFY = "simplify"
        private const val KEY_ALTERNATIVE = "alternative"
        private const val KEY_CONCEPTS = "concepts"
        private const val KEY_MISTAKES = "mistakes"
        private const val KEY_FOLLOWUPS = "followUps"

        private fun isSameDay(a: Long, b: Long): Boolean {
            val ca = Calendar.getInstance()
            ca.timeInMillis = a
            val cb = Calendar.getInstance()
            cb.timeInMillis = b
            return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
                ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
        }
    }

    private var qaDao: QuestionAnswerDao? = null
    private lateinit var profileDao: ProfileDao
    private var qa: QuestionAnswer? = null
    private var executor: ExecutorService? = null

    private lateinit var tvDifficulty: TextView
    private lateinit var btnSimplify: TextView
    private lateinit var btnAlternative: TextView
    private lateinit var btnConcepts: TextView
    private lateinit var btnMistakes: TextView
    private lateinit var btnFollowups: TextView
    private lateinit var tvExtras: TextView
    private lateinit var progressExtras: ProgressBar
    private lateinit var btnMarkReviewed: MaterialButton

    private var activeExtraKey: String? = null
    private var extrasLoading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_answer)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val qaId = intent.getLongExtra(EXTRA_QA_ID, -1)
        if (qaId == -1L) {
            Toast.makeText(this, R.string.answer_not_found, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val db = AppDatabase.getInstance(this)
        val dao = db.questionAnswerDao()
        qaDao = dao
        profileDao = db.profileDao()
        val item = dao.getById(qaId)
        qa = item
        if (item == null) {
            Toast.makeText(this, R.string.answer_not_found, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        executor = Executors.newSingleThreadExecutor()

        val btnBack = findViewById<ImageButton>(R.id.btn_back)
        val tvSubject = findViewById<TextView>(R.id.tv_subject)
        val tvQuestion = findViewById<TextView>(R.id.tv_question)
        val layoutSteps = findViewById<LinearLayout>(R.id.layout_steps)
        val tvFinalAnswer = findViewById<TextView>(R.id.tv_final_answer)
        tvDifficulty = findViewById(R.id.tv_difficulty)
        btnSimplify = findViewById(R.id.btn_simplify)
        btnAlternative = findViewById(R.id.btn_alternative)
        btnConcepts = findViewById(R.id.btn_concepts)
        btnMistakes = findViewById(R.id.btn_mistakes)
        btnFollowups = findViewById(R.id.btn_followups)
        tvExtras = findViewById(R.id.tv_extras)
        progressExtras = findViewById(R.id.progress_extras)
        btnMarkReviewed = findViewById(R.id.btn_mark_reviewed)

        btnBack.setOnClickListener { finish() }
        tvSubject.text = subjectLabel(item.subject)
        tvQuestion.text = item.question
        tvFinalAnswer.text = item.finalAnswer
        tvDifficulty.text = difficultyLabel(item.difficulty)

        try {
            val steps = JSONArray(item.steps)
            for (i in 0 until steps.length()) {
                val step = steps.getJSONObject(i)
                addStepView(layoutSteps, i + 1, step)
            }
        } catch (e: Exception) {
            val error = TextView(this)
            error.text = item.steps
            error.setTextColor(getColor(R.color.auth_foreground))
            layoutSteps.addView(error)
        }

        btnSimplify.setOnClickListener { onExtraAction(KEY_SIMPLIFY) }
        btnAlternative.setOnClickListener { onExtraAction(KEY_ALTERNATIVE) }
        btnConcepts.setOnClickListener { onExtraAction(KEY_CONCEPTS) }
        btnMistakes.setOnClickListener { onExtraAction(KEY_MISTAKES) }
        btnFollowups.setOnClickListener { onExtraAction(KEY_FOLLOWUPS) }
        btnMarkReviewed.setOnClickListener { markReviewed() }

        refreshReviewButton()
        maybeAutoReview()
    }

    private fun maybeAutoReview() {
        val item = qa ?: return
        val now = System.currentTimeMillis()
        if (item.reviewedAt != 0L && isSameDay(item.reviewedAt, now)) {
            return
        }
        markReviewed()
    }

    private fun markReviewed() {
        val item = qa ?: return
        val now = System.currentTimeMillis()
        if (item.reviewedAt != 0L && isSameDay(item.reviewedAt, now)) {
            refreshReviewButton()
            return
        }

        val firstReview = item.reviewedAt == 0L || !isSameDay(item.reviewedAt, now)
        item.reviewedAt = now
        qaDao?.updateReviewedAt(item.id, now)

        if (firstReview) {
            val profile = profileDao.getByUserId(item.userId)
            if (profile != null) {
                profile.xp += XP_REVIEW
                profileDao.update(profile)
                Toast.makeText(this, R.string.answer_review_xp_toast, Toast.LENGTH_SHORT).show()
            }
        }
        refreshReviewButton()
    }

    private fun refreshReviewButton() {
        val item = qa ?: return
        val now = System.currentTimeMillis()
        val doneToday = item.reviewedAt != 0L && isSameDay(item.reviewedAt, now)
        btnMarkReviewed.isEnabled = !doneToday
        btnMarkReviewed.setText(if (doneToday) R.string.answer_reviewed else R.string.answer_mark_reviewed)
    }

    private fun onExtraAction(key: String) {
        if (extrasLoading) {
            return
        }
        activeExtraKey = key
        styleExtraButtons()
        val cached = readExtraField(key)
        if (!cached.isNullOrEmpty()) {
            tvExtras.text = cached
            return
        }
        loadExtras(key)
    }

    private fun loadExtras(focusKey: String) {
        if (TextUtils.isEmpty(BuildConfig.GEMINI_API_KEY) ||
            "YOUR_KEY_HERE" == BuildConfig.GEMINI_API_KEY
        ) {
            Toast.makeText(this, R.string.ask_no_api_key, Toast.LENGTH_LONG).show()
            return
        }
        val item = qa ?: return

        extrasLoading = true
        progressExtras.visibility = View.VISIBLE
        tvExtras.setText(R.string.answer_extras_loading)

        val prompt = buildExtrasPrompt(item)
        executor?.execute {
            try {
                val raw = GeminiClient.ask(BuildConfig.GEMINI_API_KEY, prompt)
                val jsonText = extractJson(raw)
                val extras = JSONObject(jsonText)
                val payload = extras.toString()
                item.extrasJson = payload
                qaDao?.updateExtras(item.id, payload)

                runOnUiThread {
                    extrasLoading = false
                    progressExtras.visibility = View.GONE
                    val text = formatExtra(extras, focusKey)
                    if (text.isNullOrEmpty()) {
                        tvExtras.setText(R.string.answer_extras_error)
                    } else {
                        tvExtras.text = text
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    extrasLoading = false
                    progressExtras.visibility = View.GONE
                    tvExtras.setText(R.string.answer_extras_error)
                    Toast.makeText(this, R.string.answer_extras_error, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun readExtraField(key: String): String? {
        val extrasJson = qa?.extrasJson
        if (extrasJson.isNullOrEmpty()) {
            return null
        }
        return try {
            formatExtra(JSONObject(extrasJson), key)
        } catch (e: Exception) {
            null
        }
    }

    private fun formatExtra(extras: JSONObject, key: String): String? {
        return try {
            val value = extras.opt(key)
            if (value == null || JSONObject.NULL == value) {
                return null
            }
            if (value is JSONArray) {
                val sb = StringBuilder()
                for (i in 0 until value.length()) {
                    if (i > 0) {
                        sb.append("\n\n")
                    }
                    sb.append("• ").append(value.getString(i))
                }
                return sb.toString()
            }
            value.toString()
        } catch (e: Exception) {
            null
        }
    }

    private fun buildExtrasPrompt(item: QuestionAnswer): String {
        return "You are AI Study Mentor. Given this solved question, return ONLY valid JSON " +
            "(no markdown) with learning support fields:\n" +
            "{\n" +
            "  \"simplify\": \"simpler explanation in English\",\n" +
            "  \"alternative\": \"another way to solve in English\",\n" +
            "  \"concepts\": [\"key concept 1\", \"key concept 2\"],\n" +
            "  \"mistakes\": [\"common mistake 1\", \"common mistake 2\"],\n" +
            "  \"followUps\": [\"follow-up question 1\", \"follow-up question 2\"]\n" +
            "}\n\n" +
            "Subject: ${item.subject}\n" +
            "Difficulty: ${item.difficulty ?: "medium"}\n" +
            "Question: ${item.question}\n" +
            "Final answer: ${item.finalAnswer}\n" +
            "Steps JSON: ${item.steps}"
    }

    private fun styleExtraButtons() {
        styleAction(btnSimplify, KEY_SIMPLIFY == activeExtraKey)
        styleAction(btnAlternative, KEY_ALTERNATIVE == activeExtraKey)
        styleAction(btnConcepts, KEY_CONCEPTS == activeExtraKey)
        styleAction(btnMistakes, KEY_MISTAKES == activeExtraKey)
        styleAction(btnFollowups, KEY_FOLLOWUPS == activeExtraKey)
    }

    private fun styleAction(btn: TextView, active: Boolean) {
        if (active) {
            btn.setBackgroundResource(R.drawable.bg_answer_action_active)
            btn.setTextColor(ContextCompat.getColor(this, R.color.auth_primary_foreground))
        } else {
            btn.setBackgroundResource(R.drawable.bg_answer_action)
            btn.setTextColor(ContextCompat.getColor(this, R.color.auth_foreground))
        }
    }

    private fun addStepView(parent: LinearLayout, number: Int, step: JSONObject) {
        val density = resources.displayMetrics.density
        val stepNumber = TextView(this)
        stepNumber.text = number.toString()
        stepNumber.gravity = Gravity.CENTER
        stepNumber.setTextColor(getColor(R.color.auth_primary_foreground))
        stepNumber.setBackgroundColor(getColor(R.color.auth_brand))
        val size = (24 * density).toInt()
        val numberParams = LinearLayout.LayoutParams(size, size)
        numberParams.setMargins(0, 0, (12 * density).toInt(), 0)
        stepNumber.layoutParams = numberParams

        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        val contentParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        contentParams.bottomMargin = (16 * density).toInt()
        content.layoutParams = contentParams

        val title = step.optString("title", "")
        if (title.isNotEmpty()) {
            val tvTitle = TextView(this)
            tvTitle.text = title
            tvTitle.setTextColor(getColor(R.color.auth_foreground))
            tvTitle.textSize = 14f
            tvTitle.setTypeface(null, Typeface.BOLD)
            content.addView(tvTitle)
        }

        val body = step.optString("body", "")
        val tvBody = TextView(this)
        tvBody.text = body
        tvBody.setTextColor(getColor(R.color.auth_foreground))
        tvBody.textSize = 14f
        content.addView(tvBody)

        val expr = step.optString("expr", "")
        if (expr.isNotEmpty()) {
            val tvExpr = TextView(this)
            tvExpr.text = expr
            tvExpr.setTextColor(getColor(R.color.auth_foreground))
            tvExpr.textSize = 14f
            tvExpr.setTypeface(Typeface.MONOSPACE, Typeface.NORMAL)
            tvExpr.setPadding(12, 8, 12, 8)
            tvExpr.setBackgroundColor(getColor(R.color.auth_surface))
            val exprParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            exprParams.topMargin = (8 * density).toInt()
            tvExpr.layoutParams = exprParams
            content.addView(tvExpr)
        }

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.addView(stepNumber)
        row.addView(content)
        parent.addView(row)
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

    private fun difficultyLabel(difficulty: String?): String {
        return when (difficulty) {
            "easy" -> getString(R.string.answer_difficulty_easy)
            "hard" -> getString(R.string.answer_difficulty_hard)
            else -> getString(R.string.answer_difficulty_medium)
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
        executor?.shutdown()
        super.onDestroy()
    }
}
```

Một số điểm Kotlin trong file:

| Kotlin | Ý nghĩa |
|--------|---------|
| `val item = qa ?: return` | Thoát hàm nếu `qa` null, sau đó dùng `item` non-null |
| `qaDao?.updateReviewedAt(...)` | Chỉ gọi khi `qaDao` khác null |
| `value is JSONArray` | Kiểm tra kiểu; trong nhánh `if`, `value` được smart cast thành `JSONArray` |
| `item.difficulty ?: "medium"` | Giá trị mặc định khi `difficulty` null |
| `executor?.execute { ... }` | Lambda chạy trên thread nền; cập nhật UI trong `runOnUiThread { ... }` |
| `when (subject) { ... }` | Thay chuỗi `if` so sánh string |

**Kiểm tra nhanh:**  
1. Mở Answer → toast `+10 XP`, nút thành Reviewed.  
2. Mở lại cùng ngày → không toast thêm.  
3. Bấm Concepts → loading → nội dung; mở lại Answer → Concepts không gọi AI lần 2.

---

## Step 9) Home — card Languages

**Giải thích:**  
Ask đã lưu `language`; Home chỉ đếm 4 môn → thiếu Languages. Thêm hàng 3: card Language + spacer.

### 9a) String icon

**File:** `app/src/main/res/values/strings_home.xml`

**Thêm ngay sau dòng `home_subject_history_icon`:**

```xml
    <string name="home_subject_language_icon">あ</string>
```

### 9b) Layout sau row Code|History

**File:** `app/src/main/res/layout/fragment_home.xml` — thêm row thứ 3 ngay sau row chứa `card_subject_code` / `card_subject_history`:

```xml
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="12dp"
            android:orientation="horizontal">

            <LinearLayout
                android:id="@+id/card_subject_language"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_marginEnd="6dp"
                android:layout_weight="1"
                android:background="@drawable/bg_home_card"
                android:orientation="vertical"
                android:padding="16dp">

                <TextView
                    android:layout_width="36dp"
                    android:layout_height="36dp"
                    android:background="@drawable/bg_subject_icon"
                    android:gravity="center"
                    android:text="@string/home_subject_language_icon"
                    android:textColor="@color/auth_brand"
                    android:textSize="14sp"
                    android:textStyle="bold" />

                <TextView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="12dp"
                    android:text="@string/subject_language"
                    android:textColor="@color/auth_foreground"
                    android:textSize="14sp"
                    android:textStyle="bold" />

                <TextView
                    android:id="@+id/tv_count_language"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="2dp"
                    android:textColor="@color/auth_muted_foreground"
                    android:textSize="11sp" />
            </LinearLayout>

            <View
                android:layout_width="0dp"
                android:layout_height="1dp"
                android:layout_marginStart="6dp"
                android:layout_weight="1" />
        </LinearLayout>
```

### 9c) `HomeFragment.kt`

**Thêm property (ngay sau `private lateinit var tvCountHistory: TextView`):**

```kotlin
    private lateinit var tvCountLanguage: TextView
```

**Bind trong `onViewCreated` (ngay sau dòng bind `tvCountHistory`):**

```kotlin
        tvCountLanguage = view.findViewById(R.id.tv_count_language)
```

**Trong `loadDashboard`, ngay sau dòng set count History:**

```kotlin
        tvCountLanguage.text = getString(R.string.home_subject_count, qaDao.countBySubject(userId, "language"))
```

**Kiểm tra nhanh:** Hỏi câu Language → Home hiện count Languages.

---

## Step 10) History — chip Language

**Giải thích:**  
Filter chip giống Math/Science. Chèn **trước** chip Starred.

### 10a) `fragment_history.xml`

**Chèn ngay trước `TextView` có `android:id="@+id/chip_starred"`:**

```xml
            <TextView
                android:id="@+id/chip_language"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginStart="8dp"
                android:background="@drawable/bg_history_chip"
                android:paddingHorizontal="14dp"
                android:paddingVertical="6dp"
                android:text="@string/subject_language"
                android:textColor="@color/auth_foreground"
                android:textSize="12sp"
                android:textStyle="bold" />
```

### 10b) `HistoryFragment.kt`

**Thêm property (ngay sau `private lateinit var chipHistory: TextView`):**

```kotlin
    private lateinit var chipLanguage: TextView
```

**Bind trong `onViewCreated` (ngay sau dòng bind `chipHistory`):**

```kotlin
        chipLanguage = view.findViewById(R.id.chip_language)
```

**Thêm click listener (ngay sau listener của `chipHistory`):**

```kotlin
        chipLanguage.setOnClickListener {
            subjectFilter = "language"
            starredOnly = false
            refreshChips()
            applyFilter()
        }
```

**Trong `refreshChips`, ngay sau dòng của `chipHistory`:**

```kotlin
        styleChip(chipLanguage, subjectFilter == "language" && !starredOnly)
```

**Kiểm tra nhanh:** Chip Language lọc đúng QA `subject=language`.

---

## Step 11) Progress — row Language

**Giải thích:**  
Breakdown % môn phải gồm Language, không thì tổng bar lệch với total questions.

### 11a) `fragment_progress.xml` — sau `row_history`

`row_history` giữ `layout_marginBottom="12dp"`. Chèn ngay **sau** khối `row_history` (row Language là row cuối nên không cần margin bottom):

```xml
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
```

### 11b) `ProgressFragment.kt`

**Thêm property — mỗi dòng đặt ngay sau dòng History tương ứng:**

Sau `private lateinit var rowHistory: LinearLayout`:

```kotlin
    private lateinit var rowLanguage: LinearLayout
```

Sau `private lateinit var tvCountHistory: TextView`:

```kotlin
    private lateinit var tvCountLanguage: TextView
```

Sau `private lateinit var barHistory: ProgressBar`:

```kotlin
    private lateinit var barLanguage: ProgressBar
```

**Bind trong `onViewCreated` — mỗi dòng đặt ngay sau dòng bind History tương ứng:**

Sau `rowHistory = view.findViewById(R.id.row_history)`:

```kotlin
        rowLanguage = view.findViewById(R.id.row_language)
```

Sau `tvCountHistory = view.findViewById(R.id.tv_count_history)`:

```kotlin
        tvCountLanguage = view.findViewById(R.id.tv_count_language)
```

Sau `barHistory = view.findViewById(R.id.bar_history)`:

```kotlin
        barLanguage = view.findViewById(R.id.bar_language)
```

**Trong `loadProgress` — thay khối đếm môn / `subjectTotal` / `bindSubjectRow` cũ bằng:**

```kotlin
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
```

Khác bản cũ chỉ ở dòng `countLanguage`, phần cộng `+ countLanguage` trong `subjectTotal` và dòng `bindSubjectRow(rowLanguage, ...)`.

**Kiểm tra nhanh:** Có QA language → Progress hiện hàng Language + bar > 0.

---

## Step 12) Build & test tay

### Checklist Difficulty + Ask

1. Uninstall app cũ (hoặc clear data) vì DB v4 destructive.
2. Register/Login + Onboarding.
3. Ask 1 câu Math → Answer header có badge Easy/Medium/Hard.
4. Ask câu về grammar/vocab → subject Language.

### Checklist Extras

1. Bấm **Concepts** → loading → danh sách concepts.
2. Bấm **Simplify** → hiện ngay (không gọi lại AI nếu đã cache).
3. Thoát Answer → mở lại từ History → Concepts vẫn có, không loading dài.

### Checklist XP review

1. Mở Answer mới → toast `+10 XP`, Progress/Home XP tăng 10.
2. Mở lại cùng Answer cùng ngày → không +10 lần nữa; nút **Reviewed ✓**.
3. (Tuỳ chọn) Đổi ngày thiết bị / chờ qua ngày → mở lại → +10 lần nữa (rule “lần đầu trong ngày”).

### Checklist Languages UI

1. Home: card Language + count.
2. History: chip Language filter.
3. Progress: row Language trong breakdown.

### Lỗi thường gặp

| Triệu chứng | Nguyên nhân hay gặp | Cách xử lý |
|-------------|---------------------|------------|
| Crash `Room cannot verify the data integrity` | Quên bump version 4 | Step 3 |
| KSP lỗi `Cannot find method parameters for :extrasJson` | Tên tham số hàm DAO khác tên trong `@Query` | Step 2 |
| `Unresolved reference: tv_difficulty` | Layout chưa sync / thiếu id | Step 7a, Sync Project |
| `UninitializedPropertyAccessException: lateinit property tvDifficulty has not been initialized` | Quên `findViewById` trong `onCreate` | Step 8 |
| `Unresolved reference: difficulty` trên `qa` | Chưa thêm property vào `QuestionAnswer` | Step 1 |
| Extras luôn error | API key / JSON parse | Logcat `GeminiClient`; check `extractJson` |
| XP không tăng | `userId` sai / `getByUserId` trả `null` | Confirm session + profile sau onboarding |
| Language không hiện Home | Quên bind count | Step 9c |
| Data biến mất sau update | Destructive migration | Đúng kỳ vọng Phase 9 dev |

---

## Mapping với plan / FR

| FR / Plan | Android Phase 9 |
|-----------|-----------------|
| Difficulty metadata | `qa.difficulty` + badge |
| Languages subject | Home/History/Progress + Ask sẵn |
| Answer Improvement (simplify/alt/concepts/mistakes/follow-ups) | Actions + `extrasJson` cache |
| XP khi review answer | `reviewedAt` + +10 / ngày / câu |

---

## Checklist hoàn thành Phase 9

- [ ] `QuestionAnswer.kt`: `difficulty`, `extrasJson`, `reviewedAt`
- [ ] `QuestionAnswerDao.kt`: `update` / `updateExtras` / `updateReviewedAt`
- [ ] `AppDatabase.kt` version **4**
- [ ] Ask prompt + parse difficulty
- [ ] Answer: badge + 5 actions + cache extras
- [ ] Answer: review XP +10 (auto lần đầu ngày / nút)
- [ ] Home card + History chip + Progress row **Language**
- [ ] Test tay theo Step 12

**Tiếp theo:** Phase 10 — Ask bằng ảnh (camera/gallery → Gemini multimodal).
