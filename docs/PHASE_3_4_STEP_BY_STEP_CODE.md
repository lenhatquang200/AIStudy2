# Phase 3-4: Chèn Code Vào Đâu (Snippet Guide)

Tài liệu này chỉ ghi **đoạn code cần thêm/sửa** và **vị trí chèn**.  
Không lặp lại full file khi chỉ sửa vài dòng.

---

## Step 1) Tạo `QuestionAnswer` entity (file mới)

Giải thích: Đây là model Room cho dữ liệu hỏi-đáp. Mỗi lần user hỏi AI sẽ tạo 1 dòng mới trong bảng này để dùng lại cho Answer/History/Progress.

Trong Kotlin, entity là `class` với các property `var` có giá trị mặc định: Room (KSP) cần constructor không tham số và setter cho từng cột. Cột số dùng `Long`/`Boolean` với mặc định `0`/`false`; cột chuỗi dùng `String?` với mặc định `null` vì cột TEXT trong SQLite có thể rỗng.

**Tạo file mới:** `app/src/main/java/com/aistudy/aistudy/database/QuestionAnswer.kt`  
**Dán toàn bộ đoạn sau:**

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
}
```

---

## Step 2) Tạo `QuestionAnswerDao` (file mới)

Giải thích: DAO là nơi định nghĩa các query chính cho phase hiện tại và phase sau: lưu câu hỏi, lấy theo user, lấy theo id, toggle sao, đếm theo môn.

DAO là `interface` với các hàm `fun`. Query trả về 1 dòng khai báo kiểu `QuestionAnswer?` (có thể không tìm thấy → `null`), query trả nhiều dòng khai báo `List<QuestionAnswer>`. KSP sinh class `QuestionAnswerDao_Impl` khi build.

**Tạo file mới:** `app/src/main/java/com/aistudy/aistudy/database/QuestionAnswerDao.kt`  
**Dán toàn bộ đoạn sau:**

```kotlin
package com.aistudy.aistudy.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface QuestionAnswerDao {

    @Insert
    fun insert(qa: QuestionAnswer): Long

    @Query("SELECT * FROM question_answers WHERE userId = :userId ORDER BY createdAt DESC")
    fun getAllByUserId(userId: Long): List<QuestionAnswer>

    @Query("SELECT * FROM question_answers WHERE userId = :userId ORDER BY createdAt DESC LIMIT 1")
    fun getLatestByUserId(userId: Long): QuestionAnswer?

    @Query("SELECT * FROM question_answers WHERE id = :id LIMIT 1")
    fun getById(id: Long): QuestionAnswer?

    @Query("UPDATE question_answers SET starred = NOT starred WHERE id = :id")
    fun toggleStar(id: Long)

    @Query("SELECT COUNT(*) FROM question_answers WHERE userId = :userId AND subject = :subject")
    fun countBySubject(userId: Long, subject: String): Int
}
```

---

## Step 3) Sửa `AppDatabase` (thêm entity + DAO + version)

Giải thích: Room chỉ nhận bảng mới khi entity được khai báo trong `@Database` và version được tăng. Nếu không tăng version, app vẫn chạy schema cũ và không có bảng `question_answers`.

Trong Kotlin, danh sách entity là mảng `[...]` với tham chiếu class dạng `QuestionAnswer::class`; DAO khai báo bằng `abstract fun`.

**File:** `app/src/main/java/com/aistudy/aistudy/database/AppDatabase.kt`

### 3.1 Sửa khối `@Database(...)`
**Tìm:**
```kotlin
@Database(
    entities = [
        User::class,
        Profile::class
    ],
    version = 2,
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
    version = 3,
    exportSchema = false
)
```

### 3.2 Thêm abstract DAO
**Chèn ngay dưới `abstract fun profileDao(): ProfileDao` (cách 1 dòng trống):**
```kotlin
    abstract fun questionAnswerDao(): QuestionAnswerDao
```

Giữ nguyên `companion object` chứa `getInstance(context)` (`@Volatile` + `synchronized`, `allowMainThreadQueries()`, `fallbackToDestructiveMigration(true)`).

---

## Step 4) Sửa `build.gradle.kts` để đọc `GEMINI_API_KEY`

Giải thích: Mục tiêu là truyền API key vào `BuildConfig` để code Kotlin đọc trực tiếp bằng `BuildConfig.GEMINI_API_KEY`, tránh hardcode key trong source.

**File:** `app/build.gradle.kts`

File này đã có sẵn từ Phase 0-2 phần setup Kotlin: plugin `alias(libs.plugins.ksp)` trong `plugins { }` và `ksp(libs.room.compiler)` trong `dependencies { }`. Không đổi các dòng đó; chỉ thêm 4 đoạn dưới đây.

### 4.1 Thêm import ở đầu file
```kotlin
import java.util.Properties
```

### 4.2 Thêm block đọc `local.properties` (sau `plugins { ... }`, trước `android { ... }`)
```kotlin
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}
val geminiApiKey = localProperties.getProperty("GEMINI_API_KEY", "")
```

### 4.3 Trong `defaultConfig { ... }` thêm (ngay sau `testInstrumentationRunner`):
```kotlin
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")
```

### 4.4 Trong `android { ... }` thêm (ngay sau khối `defaultConfig { ... }`):
```kotlin
    buildFeatures {
        buildConfig = true
    }
```

Sau khi sửa: **Sync Project with Gradle Files**. Nếu thiếu `buildFeatures.buildConfig = true`, Kotlin báo `Unresolved reference: BuildConfig`.

---

## Step 5) Sửa `local.properties` (máy local)

Giải thích: Đây là nơi giữ bí mật local. Key đặt ở đây để build đọc được nhưng không đẩy lên git.

**File:** `local.properties`  
**Thêm dòng cuối file:**

```properties
GEMINI_API_KEY=YOUR_REAL_GEMINI_API_KEY
```

---

## Step 6) Sửa `AndroidManifest.xml`

Giải thích: App cần `INTERNET` để gọi Gemini REST API; và cần đăng ký `AnswerActivity` để có thể mở màn hình kết quả sau khi lưu DB.

**File:** `app/src/main/AndroidManifest.xml`

### 6.1 Thêm quyền internet
**Chèn ngay dưới `<manifest ...>`:**
```xml
<uses-permission android:name="android.permission.INTERNET" />
```

### 6.2 Đăng ký `AnswerActivity`
**Chèn trong `<application> ... </application>`, sau activity `.onboarding.OnboardingActivity`:**
```xml
<activity
    android:name=".answer.AnswerActivity"
    android:exported="false" />
```

---

## Step 7) Tạo `GeminiClient` (file mới)

Giải thích: Tách lớp gọi mạng riêng giúp `AskFragment` chỉ xử lý UI + flow. `GeminiClient` chịu trách nhiệm request/response và parse text từ cấu trúc JSON của Gemini.

`GeminiClient` là `object` (singleton): gọi trực tiếp `GeminiClient.ask(apiKey, prompt)` không cần tạo instance. Hằng số dùng `private const val`. Hàm `ask` chạy blocking nên chỉ được gọi trong background thread (Executor). `conn.outputStream.use { }` tự đóng stream kể cả khi có exception. Lỗi HTTP ném `Exception` để `AskFragment` bắt trong `try/catch`.

**Tạo file mới:** `app/src/main/java/com/aistudy/aistudy/ask/GeminiClient.kt`  
**Dán toàn bộ đoạn sau:**

```kotlin
package com.aistudy.aistudy.ask

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

object GeminiClient {

    private const val TAG = "GeminiClient"
    private const val MODEL = "gemini-3.5-flash"

    fun ask(apiKey: String, prompt: String): String {
        Log.d(TAG, "request start model=$MODEL promptLength=${prompt.length}")
        val urlString = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=$apiKey"
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 30000
        conn.readTimeout = 60000

        val part = JSONObject()
        part.put("text", prompt)

        val content = JSONObject()
        content.put("parts", JSONArray().put(part))

        val body = JSONObject()
        body.put("contents", JSONArray().put(content))

        val bytes = body.toString().toByteArray(StandardCharsets.UTF_8)
        Log.d(TAG, "request body bytes=${bytes.size}")
        conn.outputStream.use { os ->
            os.write(bytes)
        }

        val code = conn.responseCode
        Log.d(TAG, "response httpCode=$code")
        val reader = if (code in 200..299) {
            BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8))
        } else {
            BufferedReader(InputStreamReader(conn.errorStream, StandardCharsets.UTF_8))
        }

        val sb = StringBuilder()
        var line = reader.readLine()
        while (line != null) {
            sb.append(line)
            line = reader.readLine()
        }
        reader.close()

        val responseBody = sb.toString()
        if (code < 200 || code >= 300) {
            Log.e(TAG, "request failed httpCode=$code body=${truncate(responseBody, 500)}")
            throw Exception("HTTP $code: $responseBody")
        }

        Log.d(TAG, "response body length=${responseBody.length}")
        val json = JSONObject(responseBody)
        val text = json.getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
        Log.d(TAG, "parsed text length=${text.length} preview=${truncate(text, 200)}")
        return text
    }

    private fun truncate(s: String?, max: Int): String {
        if (s == null) return "null"
        if (s.length <= max) return s
        return s.substring(0, max) + "..."
    }
}
```

---

## Step 8) Sửa `fragment_ask.xml` (replace toàn bộ layout)

Giải thích: Layout này tạo đủ phần cần cho flow hỏi AI: ô nhập câu hỏi, nút gửi, trạng thái loading, và các câu gợi ý để thao tác nhanh.

**File:** `app/src/main/res/layout/fragment_ask.xml`  
**Thay toàn bộ nội dung file bằng:**

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
        android:paddingTop="24dp"
        android:paddingBottom="24dp">

        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="20dp"
            android:text="@string/ask_title"
            android:textColor="@color/auth_foreground"
            android:textSize="20sp"
            android:textStyle="bold" />

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:background="@color/auth_card"
            android:orientation="vertical"
            android:padding="16dp">

            <EditText
                android:id="@+id/edit_question"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:background="@android:color/transparent"
                android:gravity="top"
                android:hint="@string/ask_hint"
                android:inputType="textMultiLine"
                android:minLines="4"
                android:textColor="@color/auth_foreground"
                android:textColorHint="@color/auth_muted_foreground"
                android:textSize="14sp" />

            <com.google.android.material.button.MaterialButton
                android:id="@+id/btn_send"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_gravity="end"
                android:layout_marginTop="12dp"
                android:text="@string/ask_send"
                app:backgroundTint="@color/auth_brand"
                app:cornerRadius="24dp" />

            <ProgressBar
                android:id="@+id/progress_loading"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_gravity="center"
                android:layout_marginTop="8dp"
                android:visibility="gone" />
        </LinearLayout>

        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="24dp"
            android:layout_marginBottom="12dp"
            android:text="@string/ask_suggestions_title"
            android:textAllCaps="true"
            android:textColor="@color/auth_muted_foreground"
            android:textSize="11sp"
            android:textStyle="bold" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btn_suggestion_1"
            style="@style/Widget.Material3.Button.OutlinedButton"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="8dp"
            android:gravity="start|center_vertical"
            android:text="@string/ask_suggestion_1"
            android:textAllCaps="false"
            android:textColor="@color/auth_foreground"
            app:strokeColor="@color/auth_input_ring" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btn_suggestion_2"
            style="@style/Widget.Material3.Button.OutlinedButton"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="8dp"
            android:gravity="start|center_vertical"
            android:text="@string/ask_suggestion_2"
            android:textAllCaps="false"
            android:textColor="@color/auth_foreground"
            app:strokeColor="@color/auth_input_ring" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btn_suggestion_3"
            style="@style/Widget.Material3.Button.OutlinedButton"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="8dp"
            android:gravity="start|center_vertical"
            android:text="@string/ask_suggestion_3"
            android:textAllCaps="false"
            android:textColor="@color/auth_foreground"
            app:strokeColor="@color/auth_input_ring" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btn_suggestion_4"
            style="@style/Widget.Material3.Button.OutlinedButton"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:gravity="start|center_vertical"
            android:text="@string/ask_suggestion_4"
            android:textAllCaps="false"
            android:textColor="@color/auth_foreground"
            app:strokeColor="@color/auth_input_ring" />
    </LinearLayout>
</ScrollView>
```

---

## Step 9) Sửa `AskFragment.kt`

Giải thích: Đây là core logic của phase 4. Trình tự xử lý: validate input -> build prompt từ profile -> gọi Gemini ở background thread -> parse JSON -> lưu Room -> cộng XP -> mở `AnswerActivity`.

**File:** `app/src/main/java/com/aistudy/aistudy/ask/AskFragment.kt`

Các điểm Kotlin trong file:

| Chỗ | Ý nghĩa |
|-----|---------|
| `companion object { private const val TAG ... }` | Hằng số và helper dùng chung (`maskKey`, `truncate`) |
| `private lateinit var editQuestion: EditText` | View gán trong `onViewCreated` bằng `findViewById`; dùng trước khi gán sẽ crash `UninitializedPropertyAccessException` |
| `private var executor: ExecutorService? = null` | Nullable → gọi `executor?.execute { }` và `executor?.shutdown()` |
| `view.findViewById<View>(R.id.btn_suggestion_1)` | Chỉ định kiểu generic khi không gán vào biến có kiểu |
| `setOnClickListener { submitQuestion() }` | Lambda xử lý click |
| `return@execute` | Thoát khỏi lambda của `execute`, không thoát khỏi `submitQuestion` |
| `if (loading) R.string.ask_sending else R.string.ask_send` | `if` là biểu thức, trả giá trị trực tiếp |
| `when (level) { "junior" -> ... else -> ... }` | So sánh chuỗi trong `buildPrompt` |
| `AnswerActivity::class.java` | Tham chiếu class cho `Intent` |
| `SUBJECTS.contains(subject)` | Kiểm tra subject hợp lệ |

> Cách nhanh và đúng nhất cho phase này: **replace toàn bộ class `AskFragment`** bằng đoạn bên dưới.

```kotlin
package com.aistudy.aistudy.ask

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.aistudy.aistudy.BuildConfig
import com.aistudy.aistudy.R
import com.aistudy.aistudy.answer.AnswerActivity
import com.aistudy.aistudy.auth.LoginActivity
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.QuestionAnswer
import com.google.android.material.button.MaterialButton
import org.json.JSONObject
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AskFragment : Fragment() {

    companion object {
        private const val TAG = "AskFragment"
        private val SUBJECTS = arrayOf("math", "science", "code", "history", "language", "other")
        private const val XP_PER_QUESTION = 50

        private fun maskKey(key: String?): String {
            if (key == null || key.length < 8) return "(invalid)"
            return key.substring(0, 4) + "..." + key.substring(key.length - 4)
        }

        private fun truncate(s: String?, max: Int): String {
            if (s == null) return "null"
            if (s.length <= max) return s
            return s.substring(0, max) + "..."
        }
    }

    private lateinit var editQuestion: EditText
    private lateinit var btnSend: MaterialButton
    private lateinit var progressLoading: ProgressBar
    private var executor: ExecutorService? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_ask, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        editQuestion = view.findViewById(R.id.edit_question)
        btnSend = view.findViewById(R.id.btn_send)
        progressLoading = view.findViewById(R.id.progress_loading)
        executor = Executors.newSingleThreadExecutor()

        btnSend.setOnClickListener { submitQuestion() }

        view.findViewById<View>(R.id.btn_suggestion_1).setOnClickListener {
            editQuestion.setText(getString(R.string.ask_suggestion_1))
        }
        view.findViewById<View>(R.id.btn_suggestion_2).setOnClickListener {
            editQuestion.setText(getString(R.string.ask_suggestion_2))
        }
        view.findViewById<View>(R.id.btn_suggestion_3).setOnClickListener {
            editQuestion.setText(getString(R.string.ask_suggestion_3))
        }
        view.findViewById<View>(R.id.btn_suggestion_4).setOnClickListener {
            editQuestion.setText(getString(R.string.ask_suggestion_4))
        }
    }

    private fun submitQuestion() {
        val question = editQuestion.text.toString().trim()
        if (TextUtils.isEmpty(question)) {
            Toast.makeText(requireContext(), R.string.ask_empty, Toast.LENGTH_SHORT).show()
            return
        }
        if (TextUtils.isEmpty(BuildConfig.GEMINI_API_KEY) ||
            "YOUR_KEY_HERE" == BuildConfig.GEMINI_API_KEY
        ) {
            Log.e(TAG, "submit blocked: api key missing or placeholder")
            Toast.makeText(requireContext(), R.string.ask_no_api_key, Toast.LENGTH_LONG).show()
            return
        }

        Log.d(
            TAG, "submit start questionLength=${question.length}" +
                " apiKey=${maskKey(BuildConfig.GEMINI_API_KEY)}"
        )
        setLoading(true)

        val prefs = requireContext().getSharedPreferences(LoginActivity.PREFS_AUTH, 0)
        val userId = prefs.getLong(LoginActivity.KEY_USER_ID, -1)

        val db = AppDatabase.getInstance(requireContext())
        val profileDao = db.profileDao()
        val qaDao = db.questionAnswerDao()
        val profile = profileDao.getByUserId(userId)

        val level = if (profile != null) profile.level else "high"
        val style = if (profile != null) profile.style else "stepwise"
        val prompt = buildPrompt(question, level, style)
        Log.d(TAG, "profile level=$level style=$style userId=$userId")

        executor?.execute {
            try {
                Log.d(TAG, "calling Gemini...")
                val raw = GeminiClient.ask(BuildConfig.GEMINI_API_KEY, prompt)
                if (raw.isEmpty()) {
                    Log.e(TAG, "Gemini returned empty text")
                    showError(getString(R.string.ask_error))
                    return@execute
                }
                Log.d(TAG, "raw response preview=${truncate(raw, 300)}")

                val jsonText = extractJson(raw)
                Log.d(TAG, "extracted json preview=${truncate(jsonText, 300)}")
                val json = JSONObject(jsonText)

                var subject = json.optString("subject", "other")
                if (!isValidSubject(subject)) {
                    Log.w(TAG, "invalid subject from AI: $subject, fallback to other")
                    subject = "other"
                }

                val stepsArray = json.getJSONArray("steps")
                val finalAnswer = json.getString("finalAnswer")
                Log.d(
                    TAG, "parsed subject=$subject" +
                        " steps=${stepsArray.length()}" +
                        " finalAnswerLength=${finalAnswer.length}"
                )

                val qa = QuestionAnswer()
                qa.userId = userId
                qa.subject = subject
                qa.question = question
                qa.steps = stepsArray.toString()
                qa.finalAnswer = finalAnswer
                qa.starred = false
                qa.createdAt = System.currentTimeMillis()

                val qaId = qaDao.insert(qa)
                Log.d(TAG, "saved qaId=$qaId")

                if (profile != null) {
                    profile.xp += XP_PER_QUESTION
                    profileDao.update(profile)
                    Log.d(TAG, "xp updated to ${profile.xp}")
                }

                requireActivity().runOnUiThread {
                    Log.d(TAG, "opening AnswerActivity qaId=$qaId")
                    setLoading(false)
                    val intent = Intent(requireContext(), AnswerActivity::class.java)
                    intent.putExtra(AnswerActivity.EXTRA_QA_ID, qaId)
                    startActivity(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "submit failed", e)
                showError(getString(R.string.ask_error))
            }
        }
    }

    private fun showError(message: String) {
        if (!isAdded) return
        requireActivity().runOnUiThread {
            setLoading(false)
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        }
    }

    private fun setLoading(loading: Boolean) {
        btnSend.isEnabled = !loading
        btnSend.setText(if (loading) R.string.ask_sending else R.string.ask_send)
        progressLoading.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun buildPrompt(question: String, level: String?, style: String?): String {
        val levelText = when (level) {
            "junior" -> "middle school student (grades 6-9)"
            "uni" -> "university student"
            else -> "high school student (grades 10-12)"
        }

        val styleText = when (style) {
            "short" -> "Answer very briefly — go straight to the answer."
            "detailed" -> "Explain in detail with examples."
            else -> "Explain step by step, one idea per step."
        }

        return "You are AI Study Mentor, a study tutor in English for a $levelText.\n" +
            styleText + "\n" +
            "Always answer in English. Put math formulas in the \"expr\" field of the matching step.\n" +
            "Classify the subject correctly (math/science/code/history/language/other).\n\n" +
            "Return ONLY valid JSON (no markdown) with this structure:\n" +
            "{\n" +
            "  \"subject\": \"math|science|code|history|language|other\",\n" +
            "  \"steps\": [{\"title\": \"optional\", \"body\": \"required\", \"expr\": \"optional\"}],\n" +
            "  \"finalAnswer\": \"string\"\n" +
            "}\n\n" +
            "Question: " + question
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

    private fun isValidSubject(subject: String): Boolean {
        return SUBJECTS.contains(subject)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        executor?.shutdown()
    }
}
```

---

## Step 10) Tạo `activity_answer.xml` (file mới)

Giải thích: Màn hình kết quả cần hiển thị: subject, question, danh sách steps và final answer. `layout_steps` là container để thêm step động theo dữ liệu thực tế.

**File:** `app/src/main/res/layout/activity_answer.xml`  
**Dán toàn bộ đoạn sau:**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/auth_surface"
    android:orientation="vertical">

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
            android:contentDescription="@string/answer_back"
            android:src="@android:drawable/ic_menu_revert"
            app:tint="@color/auth_foreground" />

        <TextView
            android:id="@+id/tv_subject"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:gravity="center"
            android:textColor="@color/auth_brand"
            android:textSize="12sp"
            android:textStyle="bold" />
    </LinearLayout>

    <View
        android:layout_width="match_parent"
        android:layout_height="1dp"
        android:background="@color/auth_input_ring" />

    <ScrollView
        android:layout_width="match_parent"
        android:layout_height="match_parent"
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
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="@string/answer_question_label"
                android:textAllCaps="true"
                android:textColor="@color/auth_muted_foreground"
                android:textSize="10sp"
                android:textStyle="bold" />

            <TextView
                android:id="@+id/tv_question"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="8dp"
                android:layout_marginBottom="24dp"
                android:textColor="@color/auth_foreground"
                android:textSize="16sp"
                android:textStyle="bold" />

            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginBottom="16dp"
                android:text="@string/answer_steps_label"
                android:textAllCaps="true"
                android:textColor="@color/auth_muted_foreground"
                android:textSize="10sp"
                android:textStyle="bold" />

            <LinearLayout
                android:id="@+id/layout_steps"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginBottom="20dp"
                android:background="@color/auth_card"
                android:orientation="vertical"
                android:padding="16dp" />

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginBottom="20dp"
                android:background="@color/auth_brand_soft"
                android:orientation="vertical"
                android:padding="16dp">

                <TextView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:text="@string/answer_final_label"
                    android:textAllCaps="true"
                    android:textColor="@color/auth_brand"
                    android:textSize="10sp"
                    android:textStyle="bold" />

                <TextView
                    android:id="@+id/tv_final_answer"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="8dp"
                    android:textColor="@color/auth_foreground"
                    android:textSize="14sp"
                    android:textStyle="bold" />
            </LinearLayout>
        </LinearLayout>
    </ScrollView>
</LinearLayout>
```

---

## Step 11) Tạo `AnswerActivity` (file mới)

Giải thích: Activity này đọc `qaId` từ intent, query lại Room theo id, sau đó render nội dung đã lưu. Cách này đảm bảo dữ liệu hiển thị luôn lấy từ DB local.

Các điểm Kotlin trong file:

| Chỗ | Ý nghĩa |
|-----|---------|
| `companion object { const val EXTRA_QA_ID = "qa_id" }` | Hằng số public, gọi từ nơi khác bằng `AnswerActivity.EXTRA_QA_ID` |
| `enableEdgeToEdge()` | Extension của `androidx.activity`, bật hiển thị edge-to-edge |
| `intent.getLongExtra(EXTRA_QA_ID, -1)` + `qaId == -1L` | Kết quả là `Long` nên so sánh với literal `-1L` |
| `val item = dao.getById(qaId)` + `if (item == null) { ...; return }` | `getById` trả `QuestionAnswer?`; sau khi kiểm tra `null`, Kotlin smart cast `item` thành non-null |
| `findViewById<TextView>(R.id.tv_subject)` | Chỉ định kiểu view khi khai báo bằng `val` |
| `tvSubject.text = ...`, `textSize = 14f`, `layoutParams = ...` | Đọc/gán dạng property |
| `(24 * density).toInt()` | Ép kiểu `Float` → `Int` |
| `for (i in 0 until steps.length())` | Vòng lặp chỉ số |
| `when (subject) { "math" -> ... else -> ... }` | Map subject → label |

**File:** `app/src/main/java/com/aistudy/aistudy/answer/AnswerActivity.kt`  
**Dán toàn bộ đoạn sau:**

```kotlin
package com.aistudy.aistudy.answer

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.aistudy.aistudy.R
import com.aistudy.aistudy.database.AppDatabase
import org.json.JSONArray
import org.json.JSONObject

class AnswerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_QA_ID = "qa_id"
    }

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
        val item = dao.getById(qaId)
        if (item == null) {
            Toast.makeText(this, R.string.answer_not_found, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val btnBack = findViewById<ImageButton>(R.id.btn_back)
        val tvSubject = findViewById<TextView>(R.id.tv_subject)
        val tvQuestion = findViewById<TextView>(R.id.tv_question)
        val layoutSteps = findViewById<LinearLayout>(R.id.layout_steps)
        val tvFinalAnswer = findViewById<TextView>(R.id.tv_final_answer)

        btnBack.setOnClickListener { finish() }
        tvSubject.text = subjectLabel(item.subject)
        tvQuestion.text = item.question
        tvFinalAnswer.text = item.finalAnswer

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
}
```

---

## Step 12) Sửa `strings.xml` (append keys mới)

Giải thích: Tất cả text được tách vào `strings.xml` để UI nhất quán và dễ đổi ngôn ngữ, đồng thời Kotlin/XML chỉ tham chiếu `R.string.*`.

**File:** `app/src/main/res/values/strings.xml`

**Thêm các key sau vào trong `<resources> ... </resources>` (sau `hello_blank_fragment`):**

```xml
<string name="ask_title">Ask AI Mentor</string>
<string name="ask_hint">Type your question (Math, Science, Code, History, Language…)</string>
<string name="ask_send">Send</string>
<string name="ask_sending">AI is thinking…</string>
<string name="ask_empty">Please enter a question.</string>
<string name="ask_no_api_key">GEMINI_API_KEY not set in local.properties</string>
<string name="ask_error">Could not get AI response. Try again.</string>
<string name="ask_suggestions_title">Suggestions</string>
<string name="ask_suggestion_1">Solve quadratic: x² - 5x + 6 = 0</string>
<string name="ask_suggestion_2">How is a eukaryotic cell different from a prokaryotic cell?</string>
<string name="ask_suggestion_3">Difference between let and const in JavaScript?</string>
<string name="ask_suggestion_4">Causes of the August Revolution 1945?</string>

<string name="answer_back">Back</string>
<string name="answer_question_label">Question</string>
<string name="answer_steps_label">Step-by-step solution</string>
<string name="answer_final_label">Final answer</string>
<string name="answer_not_found">Question not found.</string>

<string name="subject_math">Math</string>
<string name="subject_science">Science</string>
<string name="subject_code">Code</string>
<string name="subject_history">History</string>
<string name="subject_language">Language</string>
<string name="subject_other">Other</string>
```

---

## Step 13) Build kiểm tra

Giải thích: Bước này xác nhận toàn bộ code phase 3-4 đã compile thành công sau khi thêm DB, network và UI flow. KSP chạy trong lúc build và sinh lại `AppDatabase_Impl`, `QuestionAnswerDao_Impl`.

```powershell
.\gradlew.bat assembleDebug
```

Nếu build pass là phase 3-4 đủ code.

### Lỗi build thường gặp

| Lỗi | Nguyên nhân | Cách sửa |
|-----|-------------|----------|
| `Unresolved reference: BuildConfig` | Thiếu `buildFeatures { buildConfig = true }` hoặc chưa Sync | Thêm Step 4.4 → Sync Gradle → Build lại |
| `Unresolved reference: questionAnswerDao` | Chưa thêm `abstract fun questionAnswerDao()` vào `AppDatabase` | Làm lại Step 3.2 → Build lại |
| `[ksp] ... Cannot find setter for field` | Entity khai báo `val` thay vì `var` | Đổi mọi property của `QuestionAnswer` sang `var` |
| `[ksp] ... There is a problem with the query` | Sai tên cột/bảng trong `@Query` | So lại tên bảng `question_answers` và tên property trong entity |
| `Only safe (?.) or non-null asserted (!!.) calls are allowed on a nullable receiver of type 'QuestionAnswer?'` | Dùng kết quả `getById` mà chưa kiểm tra `null` | Kiểm tra `if (item == null) { ...; return }` trước khi dùng |
| `UninitializedPropertyAccessException: lateinit property editQuestion has not been initialized` | Dùng view trước khi `findViewById` trong `onViewCreated` | Gán tất cả `lateinit` view ở đầu `onViewCreated` |
| `NetworkOnMainThreadException` | Gọi `GeminiClient.ask` trên main thread | Gọi bên trong `executor?.execute { }` |
