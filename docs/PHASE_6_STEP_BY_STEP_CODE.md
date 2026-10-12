# Phase 6: Home v2 — Hướng dẫn từng bước


Mục tiêu Phase 6: trên màn Home (sau phần XP + CTA Ask của Phase 5) thêm:

1. **Recent question** — câu hỏi mới nhất từ Room  
2. **Subject stats** — đếm số câu theo 4 môn (math, science, code, history)  
3. **Tap recent** → mở `AnswerActivity`

**Điều kiện trước khi làm:** Phase 3–5 đã xong (`QuestionAnswerDao.getLatestByUserId`, `countBySubject`, Home v1 đang chạy).

**Không làm ở Phase này:** danh sách History đầy đủ, Progress chart → để Phase 7.

---

## Tổng quan các file sẽ chạm

| File | Loại | Việc |
|------|------|------|
| `strings_home.xml` | Sửa | Thêm chữ cho recent + subjects |
| `bg_home_card.xml` | **Mới** | Nền card (recent + ô môn) |
| `bg_subject_icon.xml` | **Mới** | Nền vuông bo góc cho icon môn |
| `fragment_home.xml` | Sửa | Chèn section Recent + lưới 4 môn **sau** nút Ask |
| `HomeFragment.kt` | Sửa | Đọc QA từ DAO, bind UI, mở Answer / tab History |

**Không đụng:** `QuestionAnswerDao` (đã có sẵn từ Phase 3), `AnswerActivity`, schema Room.

---

## Step 1) Thêm string Home v2 vào `strings_home.xml`

**Giải thích:**  
Phase 5 chỉ có greeting / XP / CTA. Phase 6 cần thêm nhãn section và format đếm câu. Giữ toàn bộ chữ của Home trong `strings_home.xml` để dễ tìm.

**File:** `app/src/main/res/values/strings_home.xml`

**Tìm cuối file (trước `</resources>`):**
```xml
    <string name="home_ask_cta">Ask a question</string>
</resources>
```

**Đổi thành:**
```xml
    <string name="home_ask_cta">Ask a question</string>
    <string name="home_recent_title">Recent question</string>
    <string name="home_view_all">View all</string>
    <string name="home_subjects_title">Subjects</string>
    <string name="home_subject_count">%d questions</string>
    <string name="home_subject_math_icon">∑</string>
    <string name="home_subject_science_icon">🔬</string>
    <string name="home_subject_code_icon">{ }</string>
    <string name="home_subject_history_icon">⌛</string>
</resources>
```

| Key | Ý nghĩa |
|-----|---------|
| `home_recent_title` | Tiêu đề section câu hỏi gần đây |
| `home_view_all` | Link chuyển tab History |
| `home_subjects_title` | Tiêu đề lưới môn học |
| `home_subject_count` | Ví dụ `2 questions` — `%d` = số đếm từ DAO |
| `home_subject_*_icon` | Ký hiệu ngắn trong ô icon (giống web `SUBJECTS.emoji`) |

**Kiểm tra nhanh:** Sync Gradle / Rebuild — không lỗi `R.string.home_recent_title`.

---

## Step 2) Tạo drawable nền card

**Giải thích:** Web dùng `rounded-2xl bg-card ring`. Android dùng shape XML: màu `auth_card`, bo góc 16dp, viền mỏng.

**Tạo file mới:** `app/src/main/res/drawable/bg_home_card.xml`

**Dán toàn bộ:**
```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="@color/auth_card" />
    <corners android:radius="16dp" />
    <stroke
        android:width="1dp"
        android:color="@color/auth_shell_ring" />
</shape>
```

---

## Step 3) Tạo drawable nền icon môn

**Giải thích:** Ô nhỏ vuông bo góc màu brand nhạt — đặt ký hiệu ∑ / 🔬 / … bên trong.

**Tạo file mới:** `app/src/main/res/drawable/bg_subject_icon.xml`

**Dán toàn bộ:**
```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="@color/auth_brand_soft" />
    <corners android:radius="8dp" />
</shape>
```

---

## Step 4) Mở rộng layout `fragment_home.xml`

**Giải thích:**  
Chèn UI **ngay sau nút** `btn_ask`, **trước** khi đóng `LinearLayout` gốc và `ScrollView`.  
Cấu trúc:

```
[Phase 5: level / streak / XP / greeting / Ask]
        ↓
[section_recent]  ← ẩn (gone) nếu chưa có câu hỏi
        ↓
[Subjects title]
[row: Math | Science]
[row: Code | History]
```

**File:** `app/src/main/res/layout/fragment_home.xml`

**Tìm đoạn nút Ask hiện tại:**
```xml
        <com.google.android.material.button.MaterialButton
            android:id="@+id/btn_ask"
            ...
            app:cornerRadius="12dp" />
    </LinearLayout>
</ScrollView>
```

**Thay bằng (giữ nguyên nút Ask, thêm phần dưới, rồi mới đóng LinearLayout + ScrollView):**

```xml
        <com.google.android.material.button.MaterialButton
            android:id="@+id/btn_ask"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="20dp"
            android:text="@string/home_ask_cta"
            app:backgroundTint="@color/auth_brand"
            app:cornerRadius="12dp" />

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
    </LinearLayout>
</ScrollView>
```

**ID cần nhớ (Kotlin gọi `findViewById`):**

| ID | Vai trò |
|----|---------|
| `section_recent` | Container; `GONE` khi chưa có QA |
| `tv_view_all` | Chuyển tab History |
| `card_recent` | Bấm → `AnswerActivity` |
| `tv_recent_question` | Nội dung câu hỏi (tối đa 2 dòng) |
| `tv_recent_subject` | Nhãn môn (Math, Science…) |
| `tv_count_math` … `tv_count_history` | Số câu theo môn |

**Ghi chú lưới 2 cột:** dùng 2 `LinearLayout` ngang, mỗi ô `layout_weight="1"` — cấu trúc đơn giản hơn `GridLayout` và dễ thêm hàng mới.

---

## Step 5) Cập nhật `HomeFragment.kt`

**Giải thích luồng:**

```
SharedPreferences (user_id)
        │
        ├── User + Profile          → giữ nguyên Phase 5 (greeting, XP…)
        │
        └── QuestionAnswerDao
                ├── getLatestByUserId  → section_recent / AnswerActivity
                └── countBySubject ×4  → tv_count_*
```

**File:** `app/src/main/java/com/aistudy/aistudy/home/HomeFragment.kt`

### 5a) Import thêm

Trong khối `import`, thêm 3 dòng dưới đây vào đúng vị trí theo thứ tự chữ cái (`Intent` sau `android.content.Context`, `LinearLayout` trước `android.widget.ProgressBar`, `AnswerActivity` sau `com.aistudy.aistudy.R`):
```kotlin
import android.content.Intent
import android.widget.LinearLayout
import com.aistudy.aistudy.answer.AnswerActivity
```

Không cần import `QuestionAnswer` / `QuestionAnswerDao`: biến `latest` và `qaDao` là `val` cục bộ, Kotlin tự suy ra kiểu.

### 5b) Khai báo property UI mới (sau `btnAsk`)

**Tìm:**
```kotlin
    private lateinit var btnAsk: MaterialButton
```

**Đổi thành:**
```kotlin
    private lateinit var btnAsk: MaterialButton

    private lateinit var sectionRecent: LinearLayout
    private lateinit var tvViewAll: TextView
    private lateinit var cardRecent: LinearLayout
    private lateinit var tvRecentQuestion: TextView
    private lateinit var tvRecentSubject: TextView

    private lateinit var tvCountMath: TextView
    private lateinit var tvCountScience: TextView
    private lateinit var tvCountCode: TextView
    private lateinit var tvCountHistory: TextView

    private var latestQaId = -1L
```

`latestQaId`: khi user bấm card recent, biết mở Answer với `id` nào. Giá trị khởi tạo `-1L` để Kotlin suy ra kiểu `Long` (khớp kiểu `QuestionAnswer.id`).

### 5c) Trong `onViewCreated` — bind view + click

**Tìm đoạn CTA của Phase 5:**
```kotlin
        btnAsk = view.findViewById(R.id.btn_ask)

        btnAsk.setOnClickListener {
            val activity = activity
            if (activity is MainActivity) {
                activity.findViewById<BottomNavigationView>(R.id.bottom_nav)?.selectedItemId = R.id.nav_ask
            }
        }
    }
```

**Đổi thành:**
```kotlin
        btnAsk = view.findViewById(R.id.btn_ask)

        sectionRecent = view.findViewById(R.id.section_recent)
        tvViewAll = view.findViewById(R.id.tv_view_all)
        cardRecent = view.findViewById(R.id.card_recent)
        tvRecentQuestion = view.findViewById(R.id.tv_recent_question)
        tvRecentSubject = view.findViewById(R.id.tv_recent_subject)

        tvCountMath = view.findViewById(R.id.tv_count_math)
        tvCountScience = view.findViewById(R.id.tv_count_science)
        tvCountCode = view.findViewById(R.id.tv_count_code)
        tvCountHistory = view.findViewById(R.id.tv_count_history)

        btnAsk.setOnClickListener { selectTab(R.id.nav_ask) }
        tvViewAll.setOnClickListener { selectTab(R.id.nav_history) }
        cardRecent.setOnClickListener {
            if (latestQaId != -1L) {
                val intent = Intent(requireContext(), AnswerActivity::class.java)
                intent.putExtra(AnswerActivity.EXTRA_QA_ID, latestQaId)
                startActivity(intent)
            }
        }
    }
```

**Lưu ý:** lambda CTA Ask cũ (khối `if (activity is MainActivity)`) thay bằng gọi `selectTab` — gom code chung cho cả nút Ask và link View all. `AnswerActivity.EXTRA_QA_ID` là `const val` trong `companion object` của `AnswerActivity`.

### 5d) Helper `selectTab`

Thêm hàm mới ở cuối class (trước dấu `}` đóng class):
```kotlin
    private fun selectTab(menuItemId: Int) {
        val activity = activity
        if (activity is MainActivity) {
            activity.findViewById<BottomNavigationView>(R.id.bottom_nav)?.selectedItemId = menuItemId
        }
    }
```

### 5e) Helper `subjectLabel`

Giống `AnswerActivity` — map id môn (`"math"`) → chữ hiển thị (`Math`) bằng biểu thức `when`. Tham số `String?` vì `QuestionAnswer.subject` nullable; nhánh `else` bắt cả `null`. Thêm ngay sau `selectTab`:
```kotlin
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
```

### 5f) Mở rộng `loadDashboard`

**Tìm:**
```kotlin
        val profile = db.profileDao().getByUserId(userId)
```

**Đổi thành:**
```kotlin
        val profile = db.profileDao().getByUserId(userId)
        val qaDao = db.questionAnswerDao()
```

**Tìm dòng cuối của `loadDashboard`:**
```kotlin
        tvGreeting.text = getString(R.string.home_greeting, fullName)
    }
```

**Đổi thành:**
```kotlin
        tvGreeting.text = getString(R.string.home_greeting, fullName)

        val latest = qaDao.getLatestByUserId(userId)
        if (latest != null) {
            latestQaId = latest.id
            sectionRecent.visibility = View.VISIBLE
            tvRecentQuestion.text = latest.question
            tvRecentSubject.text = subjectLabel(latest.subject)
        } else {
            latestQaId = -1L
            sectionRecent.visibility = View.GONE
        }

        tvCountMath.text = getString(R.string.home_subject_count, qaDao.countBySubject(userId, "math"))
        tvCountScience.text = getString(R.string.home_subject_count, qaDao.countBySubject(userId, "science"))
        tvCountCode.text = getString(R.string.home_subject_count, qaDao.countBySubject(userId, "code"))
        tvCountHistory.text = getString(R.string.home_subject_count, qaDao.countBySubject(userId, "history"))
    }
```

`getLatestByUserId` trả `QuestionAnswer?`; sau `if (latest != null)` Kotlin smart cast nên đọc `latest.id`, `latest.question` trực tiếp.

### 5g) File Kotlin hoàn chỉnh (tham chiếu)

Sau Phase 6, `HomeFragment.kt` trông như sau (dùng để đối chiếu, không nhầm với bản Phase 5):

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
import androidx.fragment.app.Fragment
import com.aistudy.aistudy.MainActivity
import com.aistudy.aistudy.R
import com.aistudy.aistudy.answer.AnswerActivity
import com.aistudy.aistudy.auth.LoginActivity
import com.aistudy.aistudy.database.AppDatabase
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import kotlin.math.roundToInt

class HomeFragment : Fragment() {

    companion object {
        private const val XP_MAX = 1200
    }

    private lateinit var tvLevelBadge: TextView
    private lateinit var tvStreak: TextView
    private lateinit var tvXpLabel: TextView
    private lateinit var tvXpPercent: TextView
    private lateinit var progressXp: ProgressBar
    private lateinit var tvGreeting: TextView
    private lateinit var btnAsk: MaterialButton

    private lateinit var sectionRecent: LinearLayout
    private lateinit var tvViewAll: TextView
    private lateinit var cardRecent: LinearLayout
    private lateinit var tvRecentQuestion: TextView
    private lateinit var tvRecentSubject: TextView

    private lateinit var tvCountMath: TextView
    private lateinit var tvCountScience: TextView
    private lateinit var tvCountCode: TextView
    private lateinit var tvCountHistory: TextView

    private var latestQaId = -1L

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

        sectionRecent = view.findViewById(R.id.section_recent)
        tvViewAll = view.findViewById(R.id.tv_view_all)
        cardRecent = view.findViewById(R.id.card_recent)
        tvRecentQuestion = view.findViewById(R.id.tv_recent_question)
        tvRecentSubject = view.findViewById(R.id.tv_recent_subject)

        tvCountMath = view.findViewById(R.id.tv_count_math)
        tvCountScience = view.findViewById(R.id.tv_count_science)
        tvCountCode = view.findViewById(R.id.tv_count_code)
        tvCountHistory = view.findViewById(R.id.tv_count_history)

        btnAsk.setOnClickListener { selectTab(R.id.nav_ask) }
        tvViewAll.setOnClickListener { selectTab(R.id.nav_history) }
        cardRecent.setOnClickListener {
            if (latestQaId != -1L) {
                val intent = Intent(requireContext(), AnswerActivity::class.java)
                intent.putExtra(AnswerActivity.EXTRA_QA_ID, latestQaId)
                startActivity(intent)
            }
        }
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
            sectionRecent.visibility = View.VISIBLE
            tvRecentQuestion.text = latest.question
            tvRecentSubject.text = subjectLabel(latest.subject)
        } else {
            latestQaId = -1L
            sectionRecent.visibility = View.GONE
        }

        tvCountMath.text = getString(R.string.home_subject_count, qaDao.countBySubject(userId, "math"))
        tvCountScience.text = getString(R.string.home_subject_count, qaDao.countBySubject(userId, "science"))
        tvCountCode.text = getString(R.string.home_subject_count, qaDao.countBySubject(userId, "code"))
        tvCountHistory.text = getString(R.string.home_subject_count, qaDao.countBySubject(userId, "history"))
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

---

## Step 6) Build & test tay

**Giải thích:** Không migration Room — chỉ UI + query đã có.

### Checklist test

1. User **chưa hỏi câu nào** → Home vẫn thấy XP/CTA; **không** thấy block “Recent question”; 4 ô môn hiện `0 questions`.
2. Hỏi AI **1 câu môn Math** → quay Home → Recent hiện đúng câu; Math = `1 questions`; các môn khác `0`.
3. Hỏi thêm **Science** rồi **Code** → Recent = câu **mới nhất**; đếm từng môn tăng đúng.
4. **Bấm card Recent** → mở `AnswerActivity` đúng câu đó.
5. **Bấm View all** → BottomNav nhảy tab History (dù History vẫn placeholder cũng được).
6. Quay lại Home từ Answer → `onResume` refresh: Recent / count cập nhật nếu vừa hỏi thêm.

### Lỗi thường gặp

| Triệu chứng | Nguyên nhân hay gặp | Cách xử lý |
|-------------|---------------------|------------|
| Recent luôn ẩn | Chưa insert QA / sai `userId` | Hỏi AI thành công ở Ask; kiểm tra Room có row |
| Count luôn 0 | `subject` trong DB khác `"math"`… | Xem Log Ask — subject AI trả; phải khớp chuỗi DAO |
| Crash khi bấm Recent | Thiếu `EXTRA_QA_ID` / intent sai | So `AnswerActivity.EXTRA_QA_ID` |
| `Unresolved reference 'tv_count_…'` | Layout chưa sync | Rebuild; khớp id XML |
| `Unresolved reference 'Intent'` / `'AnswerActivity'` | Thiếu import ở 5a | Thêm import |
| `UninitializedPropertyAccessException: lateinit property sectionRecent has not been initialized` | Thiếu dòng `findViewById` ở 5c | Bổ sung dòng gán view |
| View all không đổi tab | Sai id menu | So `menu_bottom_nav.xml` → `nav_history` |

---

## Mapping với web (tham khảo)

| Web `index.tsx` | Android Phase 6 |
|-----------------|-----------------|
| `history[0]` recent | `qaDao.getLatestByUserId` |
| Link `/answer/$id` | `AnswerActivity` + `EXTRA_QA_ID` |
| Link `/history` “Xem tất cả” | `selectTab(R.id.nav_history)` |
| `SUBJECTS.slice(0, 4)` + count | 4 ô Math/Science/Code/History + `countBySubject` |
| Star trên recent | **Không** (để Phase 7 History) |

---

## Checklist hoàn thành Phase 6

- [ ] `strings_home.xml` có recent / subjects / count / icon
- [ ] `bg_home_card.xml` + `bg_subject_icon.xml` tạo xong
- [ ] `fragment_home.xml` có `section_recent` + 4 ô môn sau nút Ask
- [ ] `HomeFragment` load latest QA + count, tap mở Answer
- [ ] Test: hỏi 2–3 môn khác nhau → Recent + stats đúng

**Tiếp theo:** Phase 7 — `HistoryFragment` + `ProgressFragment` (RecyclerView).
