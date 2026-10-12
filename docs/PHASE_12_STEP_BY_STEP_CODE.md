# Phase 12: Progress v2 + topic review — Hướng dẫn từng bước

Mục tiêu Phase 12:

1. **Quiz accuracy** trên Progress = correct / attempted từ `QuizItem`
2. **Time reviewing** = tổng `reviewedSeconds` trên `QuestionAnswer` (Room **v7**)
3. **AI insights** rule-based (không gọi Gemini): top subject + accuracy improved
4. **Topic review suggest** — card Home + Progress → mở Practice (`QuizActivity`)

**Điều kiện trước khi làm:** Phase 0–11 đã xong (QuizSession/QuizItem, Answer review XP, Room **v6**).

**Không làm ở Phase này:** Unlock avatar/theme/badges mở rộng/leaderboard (P13), notifications (P14), gọi Gemini tóm tắt insights.

**Lưu ý DB:** bump Room **v6 → v7** + `fallbackToDestructiveMigration(true)` → data cũ trên máy dev **bị xóa**. Bình thường cho giai đoạn này.

**Constants quan trọng (phải giữ đúng):**

| Constant | Value | Ý nghĩa |
|----------|-------|---------|
| `MIN_PRACTICE_QA` | `2` | Cần ≥2 QA cùng subject mới hiện/mở Review Practice |
| `MAX_SESSION_SECONDS` | `1800` | Cap 30 phút / lần flush time trên Answer |
| `WINDOW_MS` | `30 * DAY_MS` | Cửa sổ 30 ngày so sánh accuracy |
| Accuracy improved | `delta > 0.05` | Recent acc − older acc > 5% và mỗi cửa sổ ≥2 attempts |

**Quy tắc review time:** `AnswerActivity` đo thời gian foreground (`onResume` → `onPause`/`onDestroy`), cộng vào `reviewedSeconds` qua `addReviewedSeconds`.

**Insights:**
- Insight 1: `You frequently ask questions about {topSubject}.` nếu có ≥1 QA.
- Insight 2: `Your accuracy in {subject} has improved this month.` nếu 1 subject thỏa so sánh 2 cửa sổ 30 ngày.

**Topic review CTA:** subject có nhiều QA nhất + count ≥ 2 → tap → `QuizActivity` (EXTRA_SUBJECT).

---

## Tổng quan các file sẽ chạm

| File | Loại | Việc |
|------|------|------|
| `QuestionAnswer.kt` | Sửa | Thêm `reviewedSeconds` |
| `QuestionAnswerDao.kt` | Sửa | `addReviewedSeconds`, `sumReviewedSecondsByUserId`, `getTopSubjectByUserId` |
| `QuizItemDao.kt` | Sửa | Count theo subject + theo khoảng thời gian |
| `AppDatabase.kt` | Sửa | `version = 7` |
| `AskFragment.kt` | Sửa | `qa.reviewedSeconds = 0` khi insert |
| `AnswerActivity.kt` | Sửa | Track + flush review seconds |
| `strings_progress.xml` | Sửa | Metrics / insights / review strings |
| `strings_home.xml` | Sửa | Review suggest strings |
| `fragment_progress.xml` | Sửa | Metrics + insights + review card |
| `fragment_home.xml` | Sửa | Review suggest card |
| `ProgressFragment.kt` | Sửa | Load accuracy / time / insights / review |
| `HomeFragment.kt` | Sửa | Review suggest → Practice |

**Không đụng:** `QuizActivity`, GeminiClient, History, Manifest (không Activity mới).

---

## Step 1) Entity `QuestionAnswer` — thêm `reviewedSeconds`

**Giải thích:**  
Mỗi lần user ở màn Answer, số giây xem được cộng dồn vào field này. Progress hiển thị `SUM(reviewedSeconds)`.

**File:** `app/src/main/java/com/aistudy/aistudy/database/QuestionAnswer.kt` (**thay toàn bộ**)

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
    var imagePath: String? = null
    var reviewedSeconds: Int = 0
}
```

| Field mới | Kiểu | Ý nghĩa |
|-----------|------|---------|
| `reviewedSeconds` | `Int` (`var`, mặc định `0`) | Tổng giây đã xem Answer của QA này |

**Kiểm tra nhanh:** Property mới nằm ngay sau `imagePath`, khai báo `var reviewedSeconds: Int = 0` (kiểu non-null nên Room tạo cột `INTEGER NOT NULL`); dù đã có giá trị mặc định `0`, vẫn gán rõ khi insert ở Step 5.

---

## Step 2) `QuestionAnswerDao`

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
}
```

**Methods mới:**

| Method | Việc |
|--------|------|
| `addReviewedSeconds` | `reviewedSeconds = reviewedSeconds + :delta` |
| `sumReviewedSecondsByUserId` | Tổng time reviewing toàn user |
| `getTopSubjectByUserId` | Subject có nhiều QA nhất; kiểu trả về `String?` (`null` nếu chưa hỏi) |

**Kiểm tra nhanh:** `COALESCE(SUM(...), 0)` tránh null khi chưa có row, nhờ vậy `sumReviewedSecondsByUserId` trả về `Int` non-null. Các DAO method là `fun` trong `interface`, không có thân hàm; KSP sinh `QuestionAnswerDao_Impl` khi build.

---

## Step 3) `QuizItemDao` — accuracy theo subject / cửa sổ thời gian

**File:** `app/src/main/java/com/aistudy/aistudy/database/QuizItemDao.kt` (**thay toàn bộ**)

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
}
```

| Method | Dùng cho |
|--------|----------|
| `countAttempted/CorrectByUserId` | Accuracy tổng trên Progress (đã có từ P11) |
| `...ByUserIdAndSubject` | Count theo môn (sẵn dùng) |
| `...ByUserIdSubjectAndRange` | So sánh accuracy 2 cửa sổ 30 ngày (insight) |

**Kiểm tra nhanh:** Range filter trên `quiz_sessions.createdAt`, không phải trên item. SQL dùng tên cột `isCorrect`; trong Kotlin, property tương ứng của `QuizItem` là `correct` (map bằng `@ColumnInfo(name = "isCorrect")`), nên query giữ nguyên `isCorrect`.

---

## Step 4) `AppDatabase` version **7**

**File:** `app/src/main/java/com/aistudy/aistudy/database/AppDatabase.kt` — đổi `version = 7` (giữ nguyên entities và `companion object` chứa `getInstance`).

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
    version = 7,
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

**Kiểm tra nhanh:** `version = 7`; `fallbackToDestructiveMigration(true)` vẫn còn; `instance` vẫn là `@Volatile private var` trong `companion object`.

---

## Step 5) `AskFragment` — init `reviewedSeconds = 0`

Mở `app/src/main/java/com/aistudy/aistudy/ask/AskFragment.kt`. Trong chỗ tạo `val qa = QuestionAnswer()` trước `val qaId = qaDao.insert(qa)`, thêm một dòng sau `qa.imagePath = imagePath`:

```kotlin
                qa.reviewedAt = 0
                qa.imagePath = imagePath
                qa.reviewedSeconds = 0
```

**Không đổi** phần còn lại của `AskFragment`.

**Kiểm tra nhanh:** Grep `reviewedSeconds` trong `AskFragment.kt` thấy `= 0`.

---

## Step 6) `AnswerActivity` — đo thời gian review

**Thay đổi chính:**
1. Property `private var reviewSessionStartMs: Long = 0` + hằng `private const val MAX_SESSION_SECONDS = 1800` trong `companion object` (cùng chỗ với `EXTRA_QA_ID`, `XP_REVIEW`, các `KEY_*` và hàm `isSameDay`)
2. Cuối `onCreate`: `reviewSessionStartMs = System.currentTimeMillis()`
3. `override fun onResume()` / `override fun onPause()` / `flushReviewSeconds()`
4. `override fun onDestroy()`: flush rồi `executor?.shutdown()`

`qa` và `qaDao` khai báo nullable (`QuestionAnswer?`, `QuestionAnswerDao?`) vì `onPause`/`onDestroy` vẫn chạy khi `onCreate` đã `finish()` sớm (không tìm thấy QA). Trong `flushReviewSeconds`, copy sang biến local `item`/`dao` rồi kiểm tra `null` để Kotlin smart cast.

**File:** `app/src/main/java/com/aistudy/aistudy/answer/AnswerActivity.kt` (**thay toàn bộ** — khớp code đã chạy)

```kotlin
package com.aistudy.aistudy.answer

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
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
import com.aistudy.aistudy.quiz.QuizActivity
import com.google.android.material.button.MaterialButton
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
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

        private const val MAX_SESSION_SECONDS = 1800

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
    private var reviewSessionStartMs: Long = 0

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
        val btnPractice = findViewById<MaterialButton>(R.id.btn_practice)

        btnBack.setOnClickListener { finish() }
        tvSubject.text = subjectLabel(item.subject)
        tvQuestion.text = item.question
        tvFinalAnswer.text = item.finalAnswer
        tvDifficulty.text = difficultyLabel(item.difficulty)
        btnPractice.text = getString(R.string.quiz_practice_on, subjectLabel(item.subject))
        btnPractice.setOnClickListener {
            val intent = Intent(this, QuizActivity::class.java)
            intent.putExtra(QuizActivity.EXTRA_SUBJECT, item.subject)
            startActivity(intent)
        }

        val layoutAnswerImage = findViewById<LinearLayout>(R.id.layout_answer_image)
        val imgAnswer = findViewById<ImageView>(R.id.img_answer)
        val imagePath = item.imagePath
        if (!imagePath.isNullOrEmpty()) {
            val imageFile = File(imagePath)
            if (imageFile.exists()) {
                imgAnswer.setImageBitmap(BitmapFactory.decodeFile(imageFile.absolutePath))
                layoutAnswerImage.visibility = View.VISIBLE
            }
        }

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
        reviewSessionStartMs = System.currentTimeMillis()
    }

    override fun onResume() {
        super.onResume()
        reviewSessionStartMs = System.currentTimeMillis()
    }

    override fun onPause() {
        flushReviewSeconds()
        super.onPause()
    }

    private fun flushReviewSeconds() {
        val item = qa
        val dao = qaDao
        if (item == null || dao == null || reviewSessionStartMs <= 0) {
            return
        }
        val elapsedMs = System.currentTimeMillis() - reviewSessionStartMs
        reviewSessionStartMs = 0
        var delta = (elapsedMs / 1000L).toInt()
        if (delta <= 0) {
            return
        }
        if (delta > MAX_SESSION_SECONDS) {
            delta = MAX_SESSION_SECONDS
        }
        item.reviewedSeconds += delta
        dao.addReviewedSeconds(item.id, delta)
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
        flushReviewSeconds()
        executor?.shutdown()
        super.onDestroy()
    }
}
```

**Hành vi flush:**
- `onPause` và `onDestroy` đều gọi `flushReviewSeconds`
- Flush lần đầu set `reviewSessionStartMs = 0` → lần sau no-op (không double-count)
- `delta` cap 1800 giây / session flush
- `delta` tính bằng `(elapsedMs / 1000L).toInt()`; khai báo `var delta` vì có thể bị gán lại khi vượt `MAX_SESSION_SECONDS`
- XP review vẫn cộng trực tiếp `profile.xp += XP_REVIEW` rồi `profileDao.update(profile)` (Phase 13 thay bằng `XpRules.addXp`)

**Kiểm tra nhanh:** Mở Answer ≥2 giây → back → Progress “Time reviewing” tăng.

---

## Step 7) Strings Progress

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
    <string name="progress_badge_week">7-day streak</string>
    <string name="progress_badge_math">Algebra basics</string>
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
</resources>
```

---

## Step 8) Strings Home (thêm 3 key review)

**File:** `app/src/main/res/values/strings_home.xml` (**thay toàn bộ**)

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="home_level_label">Current level</string>
    <string name="home_scholar">Scholar</string>
    <string name="home_streak_days">%d days</string>
    <string name="home_xp_progress">%1$d / %2$d XP</string>
    <string name="home_xp_percent">%d%%</string>
    <string name="home_greeting">Hi, %1$s</string>
    <string name="home_subtitle">What should we learn today?</string>
    <string name="home_ask_cta">Ask a question</string>
    <string name="home_recent_title">Recent question</string>
    <string name="home_view_all">View all</string>
    <string name="home_subjects_title">Subjects</string>
    <string name="home_subject_count">%d questions</string>
    <string name="home_subject_math_icon">∑</string>
    <string name="home_subject_science_icon">🔬</string>
    <string name="home_subject_code_icon">{ }</string>
    <string name="home_subject_history_icon">⌛</string>
    <string name="home_subject_language_icon">あ</string>
    <string name="home_review_title">Suggested review</string>
    <string name="home_review_subtitle">Review: %1$s</string>
    <string name="home_review_cta">Practice now</string>
</resources>
```

---

## Step 9) Layout `fragment_progress.xml`

Chèn **3 khối** sau hàng stats (streak/questions/stars), **trước** Subject breakdown:
1. Learning metrics (accuracy + time)
2. AI insights
3. Suggested review card

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
                android:padding="12dp">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="🏅"
                    android:textSize="20sp" />

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
                android:id="@+id/badge_week"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_marginStart="8dp"
                android:layout_weight="1"
                android:background="@drawable/bg_badge_locked"
                android:gravity="center"
                android:orientation="vertical"
                android:padding="12dp">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="🏅"
                    android:textSize="20sp" />

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="6dp"
                    android:gravity="center"
                    android:text="@string/progress_badge_week"
                    android:textColor="@color/auth_foreground"
                    android:textSize="10sp"
                    android:textStyle="bold" />
            </LinearLayout>

            <LinearLayout
                android:id="@+id/badge_math"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_marginStart="8dp"
                android:layout_weight="1"
                android:background="@drawable/bg_badge_locked"
                android:gravity="center"
                android:orientation="vertical"
                android:padding="12dp">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="🏅"
                    android:textSize="20sp" />

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="6dp"
                    android:gravity="center"
                    android:text="@string/progress_badge_math"
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
                android:padding="12dp">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="🏅"
                    android:textSize="20sp" />

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

**IDs mới bắt buộc:** `tv_quiz_accuracy`, `tv_review_time`, `tv_insight_1`, `tv_insight_2`, `tv_insights_empty`, `card_review_suggest`, `tv_review_subtitle`.

---

## Step 10) Layout `fragment_home.xml`

Chèn card `card_review_suggest` ngay **sau** `btn_practice_recent`, **trước** `section_recent`.

**File:** `app/src/main/res/layout/fragment_home.xml` (**thay toàn bộ**)

```xml
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
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
            android:gravity="center_vertical"
            android:orientation="horizontal">

            <LinearLayout
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:orientation="vertical">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/home_level_label"
                    android:textAllCaps="true"
                    android:textColor="@color/auth_muted_foreground"
                    android:textSize="10sp"
                    android:textStyle="bold" />

                <LinearLayout
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="6dp"
                    android:gravity="center_vertical"
                    android:orientation="horizontal">

                    <TextView
                        android:id="@+id/tv_level_badge"
                        android:layout_width="32dp"
                        android:layout_height="32dp"
                        android:background="@drawable/bg_level_badge"
                        android:gravity="center"
                        android:textColor="@color/auth_brand"
                        android:textSize="14sp"
                        android:textStyle="bold" />

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_marginStart="8dp"
                        android:text="@string/home_scholar"
                        android:textColor="@color/auth_foreground"
                        android:textSize="16sp"
                        android:textStyle="bold" />
                </LinearLayout>
            </LinearLayout>

            <TextView
                android:id="@+id/tv_streak"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:background="@drawable/bg_streak_pill"
                android:paddingHorizontal="10dp"
                android:paddingVertical="6dp"
                android:textColor="@color/streak"
                android:textSize="12sp"
                android:textStyle="bold" />
        </LinearLayout>

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:orientation="horizontal">

            <TextView
                android:id="@+id/tv_xp_label"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:textColor="@color/auth_muted_foreground"
                android:textSize="12sp"
                android:textStyle="bold" />

            <TextView
                android:id="@+id/tv_xp_percent"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:textColor="@color/auth_muted_foreground"
                android:textSize="12sp"
                android:textStyle="bold" />
        </LinearLayout>

        <ProgressBar
            android:id="@+id/progress_xp"
            style="?android:attr/progressBarStyleHorizontal"
            android:layout_width="match_parent"
            android:layout_height="6dp"
            android:layout_marginTop="8dp"
            android:max="100"
            android:progressDrawable="@drawable/bg_xp_progress" />

        <TextView
            android:id="@+id/tv_greeting"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="32dp"
            android:textColor="@color/auth_foreground"
            android:textSize="24sp"
            android:textStyle="bold" />

        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:text="@string/home_subtitle"
            android:textColor="@color/auth_muted_foreground"
            android:textSize="14sp" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btn_ask"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="20dp"
            android:text="@string/home_ask_cta"
            app:backgroundTint="@color/auth_brand"
            app:cornerRadius="12dp" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btn_practice_recent"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="12dp"
            android:text="@string/quiz_practice"
            android:visibility="gone"
            app:backgroundTint="@color/auth_brand"
            app:cornerRadius="12dp" />

        <LinearLayout
            android:id="@+id/card_review_suggest"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="20dp"
            android:background="@drawable/bg_home_card"
            android:clickable="true"
            android:focusable="true"
            android:orientation="vertical"
            android:padding="16dp"
            android:visibility="gone">

            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="@string/home_review_title"
                android:textAllCaps="true"
                android:textColor="@color/auth_muted_foreground"
                android:textSize="11sp"
                android:textStyle="bold" />

            <TextView
                android:id="@+id/tv_review_subtitle"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="8dp"
                android:textColor="@color/auth_foreground"
                android:textSize="16sp"
                android:textStyle="bold" />

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="8dp"
                android:text="@string/home_review_cta"
                android:textColor="@color/auth_brand"
                android:textSize="13sp"
                android:textStyle="bold" />
        </LinearLayout>

        <LinearLayout
            android:id="@+id/section_recent"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="32dp"
            android:orientation="vertical"
            android:visibility="gone">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:gravity="center_vertical"
                android:orientation="horizontal">

                <TextView
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="@string/home_recent_title"
                    android:textAllCaps="true"
                    android:textColor="@color/auth_muted_foreground"
                    android:textSize="11sp"
                    android:textStyle="bold" />

                <TextView
                    android:id="@+id/tv_view_all"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/home_view_all"
                    android:textColor="@color/auth_brand"
                    android:textSize="12sp"
                    android:textStyle="bold" />
            </LinearLayout>

            <LinearLayout
                android:id="@+id/card_recent"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="12dp"
                android:background="@drawable/bg_home_card"
                android:clickable="true"
                android:focusable="true"
                android:orientation="vertical"
                android:padding="16dp">

                <TextView
                    android:id="@+id/tv_recent_question"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:ellipsize="end"
                    android:maxLines="2"
                    android:textColor="@color/auth_foreground"
                    android:textSize="14sp"
                    android:textStyle="bold" />

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="12dp"
                    android:gravity="center_vertical"
                    android:orientation="horizontal">

                    <TextView
                        android:id="@+id/tv_recent_subject"
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_weight="1"
                        android:textColor="@color/auth_brand"
                        android:textSize="12sp"
                        android:textStyle="bold" />

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="›"
                        android:textColor="@color/auth_muted_foreground"
                        android:textSize="18sp" />
                </LinearLayout>
            </LinearLayout>
        </LinearLayout>

        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="32dp"
            android:text="@string/home_subjects_title"
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
                android:id="@+id/card_subject_math"
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
                    android:text="@string/home_subject_math_icon"
                    android:textColor="@color/auth_brand"
                    android:textSize="14sp"
                    android:textStyle="bold" />

                <TextView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="12dp"
                    android:text="@string/subject_math"
                    android:textColor="@color/auth_foreground"
                    android:textSize="14sp"
                    android:textStyle="bold" />

                <TextView
                    android:id="@+id/tv_count_math"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="2dp"
                    android:textColor="@color/auth_muted_foreground"
                    android:textSize="11sp" />
            </LinearLayout>

            <LinearLayout
                android:id="@+id/card_subject_science"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_marginStart="6dp"
                android:layout_weight="1"
                android:background="@drawable/bg_home_card"
                android:orientation="vertical"
                android:padding="16dp">

                <TextView
                    android:layout_width="36dp"
                    android:layout_height="36dp"
                    android:background="@drawable/bg_subject_icon"
                    android:gravity="center"
                    android:text="@string/home_subject_science_icon"
                    android:textColor="@color/auth_brand"
                    android:textSize="14sp"
                    android:textStyle="bold" />

                <TextView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="12dp"
                    android:text="@string/subject_science"
                    android:textColor="@color/auth_foreground"
                    android:textSize="14sp"
                    android:textStyle="bold" />

                <TextView
                    android:id="@+id/tv_count_science"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="2dp"
                    android:textColor="@color/auth_muted_foreground"
                    android:textSize="11sp" />
            </LinearLayout>
        </LinearLayout>

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="12dp"
            android:orientation="horizontal">

            <LinearLayout
                android:id="@+id/card_subject_code"
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
                    android:text="@string/home_subject_code_icon"
                    android:textColor="@color/auth_brand"
                    android:textSize="14sp"
                    android:textStyle="bold" />

                <TextView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="12dp"
                    android:text="@string/subject_code"
                    android:textColor="@color/auth_foreground"
                    android:textSize="14sp"
                    android:textStyle="bold" />

                <TextView
                    android:id="@+id/tv_count_code"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="2dp"
                    android:textColor="@color/auth_muted_foreground"
                    android:textSize="11sp" />
            </LinearLayout>

            <LinearLayout
                android:id="@+id/card_subject_history"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_marginStart="6dp"
                android:layout_weight="1"
                android:background="@drawable/bg_home_card"
                android:orientation="vertical"
                android:padding="16dp">

                <TextView
                    android:layout_width="36dp"
                    android:layout_height="36dp"
                    android:background="@drawable/bg_subject_icon"
                    android:gravity="center"
                    android:text="@string/home_subject_history_icon"
                    android:textColor="@color/auth_brand"
                    android:textSize="14sp"
                    android:textStyle="bold" />

                <TextView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="12dp"
                    android:text="@string/subject_history"
                    android:textColor="@color/auth_foreground"
                    android:textSize="14sp"
                    android:textStyle="bold" />

                <TextView
                    android:id="@+id/tv_count_history"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="2dp"
                    android:textColor="@color/auth_muted_foreground"
                    android:textSize="11sp" />
            </LinearLayout>
        </LinearLayout>

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
    </LinearLayout>
</ScrollView>
```

---

## Step 11) `ProgressFragment.kt`

**File:** `app/src/main/java/com/aistudy/aistudy/progress/ProgressFragment.kt` (**thay toàn bộ**)

Hằng số (`XP_MAX`, `MIN_PRACTICE_QA`, `DAY_MS`, `WINDOW_MS`, `SUBJECTS`) nằm trong `companion object` dạng `private const val` (riêng mảng `SUBJECTS` là `private val` vì `arrayOf` không phải hằng compile-time). View khai báo `private lateinit var` và gán bằng `findViewById` trong `onViewCreated`.

```kotlin
package com.aistudy.aistudy.progress

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
import com.aistudy.aistudy.R
import com.aistudy.aistudy.auth.LoginActivity
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.QuestionAnswerDao
import com.aistudy.aistudy.database.QuizItemDao
import com.aistudy.aistudy.quiz.QuizActivity
import kotlin.math.roundToInt

class ProgressFragment : Fragment() {

    companion object {
        private const val XP_MAX = 1200
        private const val MIN_PRACTICE_QA = 2
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
    private lateinit var badgeWeek: LinearLayout
    private lateinit var badgeMath: LinearLayout
    private lateinit var badgeScholar: LinearLayout

    private var qaDao: QuestionAnswerDao? = null
    private var currentUserId = -1L
    private var reviewSubject: String? = null

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
        badgeWeek = view.findViewById(R.id.badge_week)
        badgeMath = view.findViewById(R.id.badge_math)
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

        val profile = db.profileDao().getByUserId(userId)
        val qaDao = db.questionAnswerDao()
        this.qaDao = qaDao
        val quizItemDao = db.quizItemDao()
        currentUserId = userId

        val all = qaDao.getAllByUserId(userId)

        val xp = profile?.xp ?: 0
        val streak = profile?.streak ?: 0
        val level = maxOf(1, xp / 100)
        val xpPct = minOf(100, ((xp * 100f) / XP_MAX).roundToInt())
        val totalQuestions = all.size
        val starredCount = all.count { it.starred }

        tvXpTotal.text = xp.toString()
        tvLevel.text = getString(R.string.progress_level_scholar, level)
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

        setBadge(badgeStarter, totalQuestions >= 1)
        setBadge(badgeWeek, streak >= 7)
        setBadge(badgeMath, countMath >= 1)
        setBadge(badgeScholar, xp >= 1000)
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
}
```

**Logic cần nhớ:**
- Level / XP: `maxOf(1, xp / 100)` và `minOf(100, ((xp * 100f) / XP_MAX).roundToInt())` (import `kotlin.math.roundToInt`)
- Stars: `all.count { it.starred }`
- Accuracy: `((correct * 100f) / attempted).roundToInt()` khi `attempted > 0`
- Insight improved: mỗi subject cần ≥2 attempts ở cả recent 30d và older 30d; `delta > 0.05f`
- Review card: top subject + count ≥ `MIN_PRACTICE_QA` → `QuizActivity`; listener dùng `reviewSubject?.let { openPractice(it) }` nên không mở khi `reviewSubject` là `null`

---

## Step 12) `HomeFragment.kt`

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
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import kotlin.math.roundToInt

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
        val level = maxOf(1, xp / 100)
        val xpPct = minOf(100, ((xp * 100f) / XP_MAX).roundToInt())

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

**Hành vi:** Nếu top subject có ≥2 QA → hiện “Review: {Subject}” → tap Practice. Level / XP ở Phase 12 vẫn theo công thức cố định `maxOf(1, xp / 100)` và `XP_MAX = 1200` (Phase 13 thay bằng `XpRules`). `selectTab` kiểm tra `activity is MainActivity` rồi đặt `selectedItemId` cho `BottomNavigationView`.

---

## Step 13) Test tay (checklist)

### Chuẩn bị

1. Rebuild app sau bump v7 (data cũ bị xóa) → login + onboard.
2. Ask **≥2 câu Math** và **≥2 câu Science** (hoặc 2 môn bất kỳ).
3. Làm **2 quiz** (2 môn khác nhau), trả lời vài câu đúng/sai.
4. Mở ít nhất 1 Answer và ở lại ≥ vài giây rồi Back.

### Checklist Progress metrics

1. Tab Progress → **Quiz accuracy** dạng `NN% · correct/attempted` (không empty).
2. **Time reviewing** > 0 sec/min sau khi xem Answer.
3. Subject breakdown vẫn đúng như P7/P9.

### Checklist Insights

1. Có QA → thấy “You frequently ask questions about {Subject}.”
2. Insight “improved this month” chỉ hiện khi đủ data 2 cửa sổ (có thể empty lúc mới test — bình thường).
3. Không có QA/quiz → “Ask questions and complete quizzes…”.

### Checklist Topic review

1. Home: card **Suggested review** hiện “Review: {TopSubject}”.
2. Tap → vào Quiz loading của môn đó (≥2 QA).
3. Progress: cùng card “Practice now” → Quiz tương tự.
4. Nếu subject top < 2 QA → card ẩn.

### Checklist Persist

1. `question_answers.reviewedSeconds` tăng sau xem Answer.
2. Accuracy khớp `QuizItemDao.countCorrect / countAttempted`.

### Lỗi thường gặp

| Triệu chứng | Nguyên nhân hay gặp | Cách xử lý |
|-------------|---------------------|------------|
| Schema crash | Quên `version = 7` | Step 4 |
| Accuracy empty sau quiz | Quiz chưa Check / `QuizItem.correct` (cột `isCorrect`) vẫn `null` | Làm quiz tới khi trả lời |
| Time luôn 0 | Quên flush / quên field | Step 1 + 6 |
| Review card không hiện | Top subject < 2 QA | Ask thêm cùng môn |
| Double time tăng quá nhiều | Không zero `reviewSessionStartMs` sau flush | Dùng đúng `flushReviewSeconds` Step 6 |
| Data biến mất | Destructive migration v7 | Đúng kỳ vọng Phase 12 |
| `Unresolved reference: reviewedSeconds` / `addReviewedSeconds` | Chưa thêm property vào `QuestionAnswer.kt` hoặc method vào `QuestionAnswerDao.kt` | Step 1 + 2, rồi Build lại |
| KSP báo lỗi `There is a problem with the query` / `no such column` | Gõ sai tên cột trong `@Query` (ví dụ `reviewedSecond`, `correct` thay vì `isCorrect`) | So lại từng query ở Step 2 + 3 |
| `UninitializedPropertyAccessException: lateinit property tvQuizAccuracy has not been initialized` | Thiếu dòng `findViewById` trong `onViewCreated` hoặc id trong layout sai | Đối chiếu id ở Step 9/10 với Step 11/12 |
| `ClassCastException` khi gán `cardReviewSuggest` | Id `card_review_suggest` trong layout không phải `LinearLayout` | Giữ đúng layout Step 9/10 |
| `Type mismatch: inferred type is String? but String was expected` | Truyền `latest.subject` (`String?`) thẳng vào `countBySubject` | Dùng `latest.subject?.let { qaDao.countBySubject(userId, it) } ?: 0` như Step 12 |

---

## Mapping với plan / FR

| FR / Plan | Android Phase 12 |
|-----------|------------------|
| Quiz accuracy | `correct/attempted` trên Progress |
| Time reviewing | `reviewedSeconds` cộng dồn từ Answer |
| AI insights | Rule-based top subject + accuracy delta |
| Suggest review topic | Card Home/Progress → Practice |
| Optional Gemini daily | **Không làm** (đủ FR wording bằng rule-based) |

---

## Checklist hoàn thành Phase 12

- [ ] `QuestionAnswer.reviewedSeconds` + Dao helpers
- [ ] `QuizItemDao` range/subject counts
- [ ] `AppDatabase` version **7**
- [ ] Ask insert `reviewedSeconds = 0`
- [ ] AnswerActivity flush review seconds
- [ ] strings Progress + Home
- [ ] Layout Progress metrics/insights/review
- [ ] Layout Home review card
- [ ] ProgressFragment + HomeFragment logic
- [ ] Test tay theo Step 13
- [ ] Compile: `./gradlew :app:compileDebugKotlin` OK

**Tiếp theo:** Phase 13 — Gamification (XP rules, avatar/theme, badges, leaderboard local).
