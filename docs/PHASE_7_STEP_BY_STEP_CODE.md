# Phase 7: History + Progress — Hướng dẫn từng bước


Mục tiêu Phase 7:

1. **HistoryFragment** — danh sách câu hỏi đã hỏi (`RecyclerView`), lọc môn / ★, tìm kiếm, tap → `AnswerActivity`, bấm ★ → `toggleStar`
2. **ProgressFragment** — Tổng XP, 3 ô thống kê (streak / số câu / số ★), phân bố môn, huy hiệu

**Điều kiện trước khi làm:** Phase 3–6 đã xong (`QuestionAnswerDao.getAllByUserId`, `toggleStar`, `countBySubject`, Home v2 đang chạy).

**Không làm ở Phase này:** nút Logout → để Phase 8.

---

## Tổng quan các file sẽ chạm

| File | Loại | Việc |
|------|------|------|
| `gradle/libs.versions.toml` | Sửa | Thêm dependency `recyclerview` |
| `app/build.gradle.kts` | Sửa | `implementation(libs.recyclerview)` |
| `strings_history.xml` | **Mới** | Chữ màn History |
| `strings_progress.xml` | **Mới** | Chữ màn Progress |
| `bg_history_chip.xml` | **Mới** | Nền chip lọc (chưa chọn) |
| `bg_history_chip_active.xml` | **Mới** | Nền chip lọc (đã chọn) |
| `bg_progress_xp_card.xml` | **Mới** | Card tối “Total XP” |
| `bg_progress_xp_bar.xml` | **Mới** | Thanh XP trên card tối |
| `bg_progress_subject_bar.xml` | **Mới** | Thanh % theo môn |
| `bg_badge_earned.xml` / `bg_badge_locked.xml` | **Mới** | Nền huy hiệu |
| `item_history.xml` | **Mới** | 1 dòng trong danh sách |
| `fragment_history.xml` | **Thay** | Thôi placeholder → UI đủ |
| `fragment_progress.xml` | **Thay** | Thôi placeholder → UI đủ |
| `HistoryAdapter.kt` | **Mới** | Adapter `RecyclerView` |
| `HistoryFragment.kt` | **Thay** | Load QA, lọc, mở Answer, star |
| `ProgressFragment.kt` | **Thay** | Bind XP + stats + badges |

**Không đụng:** `QuestionAnswerDao` (đã đủ từ Phase 3), schema Room, Logout.

---

## Step 1) Thêm dependency RecyclerView

**Giải thích:**  
List dài không viết bằng nhiều `TextView` cố định — dùng `RecyclerView` tái sử dụng ô `item_history`. Material không luôn kéo sẵn đủ; khai báo rõ trong catalog Gradle. Ở thời điểm này catalog đã có `ksp` (Phase 0) và `room`, `fragment`; `work` chưa có (thêm ở Phase 14).

**File:** `gradle/libs.versions.toml`

**Trong `[versions]`, tìm:**
```toml
fragment = "1.8.9"
ksp = "2.3.12"
```

**Đổi thành:**
```toml
fragment = "1.8.9"
recyclerview = "1.4.0"
ksp = "2.3.12"
```

**Trong `[libraries]`, tìm dòng cuối của khối:**
```toml
fragment = { group = "androidx.fragment", name = "fragment", version.ref = "fragment" }
```

**Đổi thành:**
```toml
fragment = { group = "androidx.fragment", name = "fragment", version.ref = "fragment" }
recyclerview = { group = "androidx.recyclerview", name = "recyclerview", version.ref = "recyclerview" }
```

**File:** `app/build.gradle.kts`

**Trong `dependencies { }`, tìm:**
```kotlin
    implementation(libs.fragment)
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
```

**Đổi thành:**
```kotlin
    implementation(libs.fragment)
    implementation(libs.recyclerview)
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
```

**Kiểm tra nhanh:** Sync Gradle — không lỗi `Unresolved reference: recyclerview` ở `libs.recyclerview`.

---

## Step 2) String History

**Giải thích:** Giống Phase 6 tách `strings_home.xml` — chữ History nằm riêng để dễ tìm.

**Tạo file mới:** `app/src/main/res/values/strings_history.xml`

**Dán toàn bộ:**
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="history_title">Library</string>
    <string name="history_search_hint">Search questions…</string>
    <string name="history_chip_all">All</string>
    <string name="history_chip_starred">★ Saved</string>
    <string name="history_empty">No questions yet. Ask AI to get started!</string>
    <string name="history_just_now">Just now</string>
    <string name="history_hours_ago">%d h ago</string>
    <string name="history_days_ago">%d d ago</string>
    <string name="history_star">★</string>
</resources>
```

| Key | Ý nghĩa |
|-----|---------|
| `history_title` | Tiêu đề tab |
| `history_search_hint` | Placeholder ô tìm |
| `history_chip_*` | Chip lọc |
| `history_empty` | Khi chưa có câu / lọc ra rỗng |
| `history_*_ago` | Thời gian tương đối dưới mỗi card |

Nhãn môn (`Math`, `Science`…) dùng sẵn `subject_*` trong `strings.xml`.

---

## Step 3) String Progress

**Tạo file mới:** `app/src/main/res/values/strings_progress.xml`

**Dán toàn bộ:**
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="progress_title">Learning progress</string>
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
</resources>
```

| Key | Ý nghĩa |
|-----|---------|
| `progress_level_scholar` | Ví dụ `Level 2 • Scholar` — `%1$d` = level |
| `progress_streak_value` | Ví dụ `3d` |
| `progress_badge_*` | 4 huy hiệu (logic trong `ProgressFragment.kt`) |

---

## Step 4) Drawable chip History

**Giải thích:** Chip lọc giống web `rounded-full`. Inactive = card + viền; active = nền brand.

**Tạo:** `app/src/main/res/drawable/bg_history_chip.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="@color/auth_card" />
    <corners android:radius="999dp" />
    <stroke
        android:width="1dp"
        android:color="@color/auth_shell_ring" />
</shape>
```

**Tạo:** `app/src/main/res/drawable/bg_history_chip_active.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="@color/auth_brand" />
    <corners android:radius="999dp" />
</shape>
```

---

## Step 5) Drawable Progress (card XP + thanh + badge)

**Tạo:** `bg_progress_xp_card.xml` — nền tối giống web `bg-foreground`
```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="@color/auth_foreground" />
    <corners android:radius="16dp" />
</shape>
```

**Tạo:** `bg_progress_xp_bar.xml` — track trắng mờ + fill brand (dùng trên card tối)
```xml
<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:id="@android:id/background">
        <shape android:shape="rectangle">
            <corners android:radius="999dp" />
            <solid android:color="#26FFFFFF" />
        </shape>
    </item>
    <item android:id="@android:id/progress">
        <clip>
            <shape android:shape="rectangle">
                <corners android:radius="999dp" />
                <solid android:color="@color/auth_brand" />
            </shape>
        </clip>
    </item>
</layer-list>
```

**Tạo:** `bg_progress_subject_bar.xml` — track `xp_track` + fill brand
```xml
<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:id="@android:id/background">
        <shape android:shape="rectangle">
            <corners android:radius="999dp" />
            <solid android:color="@color/xp_track" />
        </shape>
    </item>
    <item android:id="@android:id/progress">
        <clip>
            <shape android:shape="rectangle">
                <corners android:radius="999dp" />
                <solid android:color="@color/auth_brand" />
            </shape>
        </clip>
    </item>
</layer-list>
```

**Tạo:** `bg_badge_earned.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="@color/auth_brand_soft" />
    <corners android:radius="12dp" />
    <stroke
        android:width="1dp"
        android:color="@color/auth_brand_ring" />
</shape>
```

**Tạo:** `bg_badge_locked.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="@color/auth_card" />
    <corners android:radius="12dp" />
    <stroke
        android:width="1dp"
        android:color="@color/auth_shell_ring" />
</shape>
```

Card item History tái dùng `@drawable/bg_home_card` (đã có từ Phase 6).

---

## Step 6) Layout 1 dòng History — `item_history.xml`

**Giải thích:** Mỗi ô RecyclerView inflate layout này. Cấu trúc: câu hỏi + ★ | môn + thời gian.

**Tạo file mới:** `app/src/main/res/layout/item_history.xml`

**Dán toàn bộ:**
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginBottom="12dp"
    android:background="@drawable/bg_home_card"
    android:orientation="vertical"
    android:padding="16dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:gravity="top"
        android:orientation="horizontal">

        <TextView
            android:id="@+id/tv_question"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:ellipsize="end"
            android:maxLines="2"
            android:textColor="@color/auth_foreground"
            android:textSize="14sp"
            android:textStyle="bold" />

        <TextView
            android:id="@+id/tv_star"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="12dp"
            android:padding="4dp"
            android:text="@string/history_star"
            android:textSize="16sp" />
    </LinearLayout>

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp"
        android:orientation="horizontal">

        <TextView
            android:id="@+id/tv_subject"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:textColor="@color/auth_brand"
            android:textSize="11sp"
            android:textStyle="bold" />

        <TextView
            android:id="@+id/tv_time"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textColor="@color/auth_muted_foreground"
            android:textSize="11sp" />
    </LinearLayout>
</LinearLayout>
```

| ID | Vai trò |
|----|---------|
| `tv_question` | Nội dung câu hỏi |
| `tv_star` | Bấm để `toggleStar` (không mở Answer) |
| `tv_subject` | Nhãn môn |
| `tv_time` | “Just now” / “2 h ago” |

---

## Step 7) Layout `fragment_history.xml` (thay toàn bộ)

**Giải thích:** Placeholder chữ “History” giữa màn → title + search + chip + `RecyclerView` + empty.

**File:** `app/src/main/res/layout/fragment_history.xml` — **xóa hết**, dựng layout mới theo cấu trúc dưới đây.

Cấu trúc tóm tắt:

```
LinearLayout (vertical)
 ├── tv title "Library"
 ├── EditText et_search
 ├── HorizontalScrollView → chips (All / Math / Science / Code / History / ★ Saved)
 └── FrameLayout
      ├── RecyclerView rv_history
      └── TextView tv_empty
```

**ID cần nhớ:**

| ID | Vai trò |
|----|---------|
| `et_search` | Lọc theo chữ trong `question` |
| `chip_all` … `chip_starred` | Đổi `subjectFilter` / `starredOnly` |
| `rv_history` | Danh sách |
| `tv_empty` | Hiện khi filtered rỗng |

Bản XML cuối cùng nằm ở `app/src/main/res/layout/fragment_history.xml` trong project; bản đó đã có thêm chip Language (Phase 9) và nút `btn_practice` + `xmlns:app` (Phase 11). Ở Phase 7 bỏ qua hai phần đó.

---

## Step 8) `HistoryAdapter.kt` (mới)

**Giải thích luồng:**

```
List<QuestionAnswer>
        │
        ▼
HistoryAdapter.bind → item_history
        │
        ├── click item  → Listener.onItemClick → AnswerActivity
        └── click ★     → Listener.onStarClick → qaDao.toggleStar
```

**Các điểm Kotlin trong file:**
- Constructor chính khai báo luôn 3 property `private val` (`listener`, `labelProvider`, `timeProvider`).
- `SubjectLabelProvider` và `RelativeTimeProvider` là `fun interface` (interface một hàm) nên Fragment truyền được function reference `::subjectLabel`, `::relativeTime` hoặc lambda.
- `items` là `mutableListOf<QuestionAnswer>()`; `submitList` nhận `List<QuestionAnswer>?` để chấp nhận `null`.
- `Holder` gán view ngay khi khai báo property (`val tvQuestion: TextView = itemView.findViewById(...)`).
- `getItemCount()` viết dạng biểu thức `= items.size`.

**Tạo file mới:** `app/src/main/java/com/aistudy/aistudy/history/HistoryAdapter.kt`

```kotlin
package com.aistudy.aistudy.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.aistudy.aistudy.R
import com.aistudy.aistudy.database.QuestionAnswer

class HistoryAdapter(
    private val listener: Listener,
    private val labelProvider: SubjectLabelProvider,
    private val timeProvider: RelativeTimeProvider
) : RecyclerView.Adapter<HistoryAdapter.Holder>() {

    interface Listener {
        fun onItemClick(qa: QuestionAnswer)

        fun onStarClick(qa: QuestionAnswer)
    }

    private val items = mutableListOf<QuestionAnswer>()

    fun interface SubjectLabelProvider {
        fun label(subject: String?): String
    }

    fun interface RelativeTimeProvider {
        fun format(createdAt: Long): String
    }

    fun submitList(list: List<QuestionAnswer>?) {
        items.clear()
        if (list != null) {
            items.addAll(list)
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val qa = items[position]
        holder.tvQuestion.text = qa.question
        holder.tvSubject.text = labelProvider.label(qa.subject)
        holder.tvTime.text = timeProvider.format(qa.createdAt)
        val starColor = if (qa.starred) {
            ContextCompat.getColor(holder.itemView.context, R.color.streak)
        } else {
            ContextCompat.getColor(holder.itemView.context, R.color.auth_muted_foreground)
        }
        holder.tvStar.setTextColor(starColor)
        holder.itemView.setOnClickListener { listener.onItemClick(qa) }
        holder.tvStar.setOnClickListener { listener.onStarClick(qa) }
    }

    override fun getItemCount(): Int = items.size

    class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvQuestion: TextView = itemView.findViewById(R.id.tv_question)
        val tvStar: TextView = itemView.findViewById(R.id.tv_star)
        val tvSubject: TextView = itemView.findViewById(R.id.tv_subject)
        val tvTime: TextView = itemView.findViewById(R.id.tv_time)
    }
}
```

---

## Step 9) `HistoryFragment.kt` (thay logic)

**Giải thích luồng:**

```
onResume
   └── loadHistory()
          ├── SharedPreferences → userId
          ├── qaDao.getAllByUserId → allItems
          └── applyFilter() → adapter.submitList

Chips / Search
   └── đổi subjectFilter / starredOnly / query → applyFilter()

★ click
   └── qaDao.toggleStar(id) → loadHistory() lại
```

**File:** `app/src/main/java/com/aistudy/aistudy/history/HistoryFragment.kt`

### 9a) Điểm chính cần nhớ khi viết

1. Khai báo view bằng `private lateinit var` (`etSearch`, `chipAll` … `chipStarred`, `rvHistory`, `tvEmpty`) và `private lateinit var adapter: HistoryAdapter`; DAO giữ trong `private var qaDao: QuestionAnswerDao? = null`; dữ liệu gốc trong `private val allItems = mutableListOf<QuestionAnswer>()`; trạng thái lọc `subjectFilter = "all"`, `starredOnly = false`, `query = ""`.
2. `onViewCreated`: bind view, tạo adapter, gán `rvHistory.layoutManager = LinearLayoutManager(requireContext())` + `rvHistory.adapter = adapter`, gắn click chip bằng lambda và `etSearch.addTextChangedListener(object : TextWatcher { ... })`.
3. `onResume`: gọi `loadHistory()` (quay từ Answer / Ask vẫn refresh).
4. `applyFilter`: lọc in-memory trên `allItems` (không thêm query Room mới).
5. `styleChip`: active → `bg_history_chip_active` + chữ `auth_primary_foreground`; inactive → `bg_history_chip` + chữ `auth_foreground`.
6. `relativeTime`: giống web — &lt;1h → Just now; &lt;24h → `Nh h ago`; else `Nd d ago`.

### 9b) Các đoạn Kotlin quan trọng

Bản `HistoryFragment.kt` trong project là trạng thái cuối (đã có chip Language của Phase 9 và nút Practice của Phase 11). Ở Phase 7 dùng các đoạn sau.

Tạo adapter trong `onViewCreated` — listener viết bằng `object : HistoryAdapter.Listener { ... }`, hai provider truyền bằng function reference. Trong `onStarClick`, `val dao = qaDao ?: return` thoát sớm khi DAO chưa sẵn sàng:

```kotlin
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
```

Filter — `qa.question?.lowercase()?.contains(...) != true` loại cả câu hỏi `null` lẫn câu không chứa từ khóa:

```kotlin
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
    }
```

Thời gian tương đối:

```kotlin
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
```

---

## Step 10) Layout `fragment_progress.xml` (thay toàn bộ)

**Giải thích:** Placeholder “Progress” → ScrollView với 4 khối: XP card → 3 stats → subject bars → badges.

Cấu trúc:

```
ScrollView
 └── LinearLayout
      ├── title
      ├── card XP tối (tv_xp_total, tv_level, tv_xp_percent, progress_xp)
      ├── hàng 3 ô (streak / questions / stars)
      ├── card Subject breakdown (row_math… + tv_subjects_empty)
      └── Badges (badge_starter / week / math / scholar)
```

**ID chính:**

| ID | Vai trò |
|----|---------|
| `tv_xp_total` | Số XP từ `Profile.xp` |
| `progress_xp` | `minOf(100, ((xp * 100f) / XP_MAX).roundToInt())` — cùng công thức Home |
| `tv_stat_*` | Streak / số QA / số starred |
| `row_*` + `bar_*` | Ẩn nếu count = 0; thanh % = count / tổng 4 môn |
| `badge_*` | Đổi nền earned/locked + alpha |

Bản XML cuối cùng nằm ở `app/src/main/res/layout/fragment_progress.xml`; các Phase 8, 9, 12, 13 thay đổi tiếp file này.

---

## Step 11) `ProgressFragment.kt` (thay logic)

**Giải thích luồng:**

```
onResume → loadProgress()
              ├── Profile → xp, streak, level
              ├── getAllByUserId → size, starredCount
              ├── countBySubject ×4 → thanh phân bố
              └── badges:
                    starter  : totalQuestions >= 1
                    week     : streak >= 7
                    math     : countMath >= 1
                    scholar  : xp >= 1000
```

**Không** thêm Logout ở đây (Phase 8).

**File:** `app/src/main/java/com/aistudy/aistudy/progress/ProgressFragment.kt`

Mẫu số của thanh môn: `val barDenom = if (subjectTotal > 0) subjectTotal else 1` để tránh chia cho 0. Đoạn bind môn + badge (cần `import kotlin.math.roundToInt`):

```kotlin
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
```

`XP_MAX = 1200` khai báo `private const val` trong `companion object` — khớp `HomeFragment`.

---

## Step 12) Build & test tay

**Giải thích:** Không migration Room — chỉ UI + DAO sẵn có.

### Checklist History

1. Chưa hỏi câu nào → empty “No questions yet…”.
2. Hỏi 2–3 câu khác môn → list hiện, mới nhất trên cùng (`ORDER BY createdAt DESC`).
3. Chip **Math** → chỉ câu math; **★ Saved** → chỉ starred; **All** → hết.
4. Gõ search → lọc theo chữ trong câu hỏi.
5. Bấm card → `AnswerActivity` đúng `qa_id`.
6. Bấm ★ → màu streak; bấm lại → muted; Progress Stars tăng/giảm.
7. Home **View all** → tab History vẫn nhảy đúng (Phase 6).

### Checklist Progress

1. XP / % khớp Home (cùng user).
2. Questions = số QA; Stars = số `starred`.
3. Chỉ môn có count &gt; 0 mới hiện row; empty text khi tổng = 0.
4. Badge Starter sáng sau câu đầu; Math sau khi có math; Scholar khi XP ≥ 1000; Week khi streak ≥ 7.

### Lỗi thường gặp

| Triệu chứng | Nguyên nhân hay gặp | Cách xử lý |
|-------------|---------------------|------------|
| `Unresolved reference 'recyclerview'` / `'RecyclerView'` | Chưa sync `libs.recyclerview` | Sync Gradle; check `build.gradle.kts` |
| `UninitializedPropertyAccessException: lateinit property adapter has not been initialized` | `applyFilter()` chạy trước khi tạo adapter | Tạo adapter trong `onViewCreated` trước khi gọi `refreshChips()` / filter |
| `Type mismatch` khi truyền `::subjectLabel` | Chữ ký hàm không khớp `fun interface` (`(String?) -> String`, `(Long) -> String`) | Khai báo `subjectLabel(subject: String?)`, `relativeTime(createdAt: Long)` |
| List trống dù đã hỏi | Sai `userId` / chưa `onResume` load | Confirm session; hỏi lại Ask |
| ★ không đổi | Chưa gọi `toggleStar` / không reload | So `onStarClick` → `loadHistory()` |
| Bấm ★ mở Answer | Click bubble lên parent | Star có listener riêng trên `tv_star` |
| Progress luôn empty subjects | Subject DB khác `"math"`… | Khớp chuỗi Ask / DAO |
| Badge Scholar không mở | XP &lt; 1000 | Hỏi thêm để cộng XP |

---

## Mapping với web (tham khảo)

| Web | Android Phase 7 |
|-----|-----------------|
| `history.tsx` list + Link answer | `HistoryAdapter` + `AnswerActivity` |
| Search + chip subject + ★ | `et_search` + chips + `starredOnly` |
| `toggleStar` | `QuestionAnswerDao.toggleStar` |
| `progress.tsx` Total XP | Card tối + `Profile.xp` |
| Stat Chuỗi / Câu hỏi / Sao | 3 ô `tv_stat_*` |
| Phân bố môn | `countBySubject` + `ProgressBar` |
| Huy hiệu | 4 badge Starter / Week / Math / Scholar |
| Nút Đăng xuất | **Không** (Phase 8) |

---

## Checklist hoàn thành Phase 7

- [ ] `recyclerview` trong `libs.versions.toml` + `app/build.gradle.kts`
- [ ] `strings_history.xml` + `strings_progress.xml`
- [ ] Drawable chip / XP card / bars / badge
- [ ] `item_history.xml` + `fragment_history.xml` + `fragment_progress.xml`
- [ ] `HistoryAdapter` + `HistoryFragment` (list, filter, star, Answer)
- [ ] `ProgressFragment` (XP, stats, subjects, badges)
- [ ] Test: list đúng → star → Progress cập nhật

**Tiếp theo:** Phase 8 — Logout + UX nhỏ → [`PHASE_8_STEP_BY_STEP_CODE.md`](PHASE_8_STEP_BY_STEP_CODE.md).
