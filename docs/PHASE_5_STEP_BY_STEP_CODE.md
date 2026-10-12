# Phase 5: Home v1 — Hướng dẫn từng bước


Mục tiêu Phase 5: màn Home hiển thị `fullName`, level badge, XP bar, streak, và nút CTA chuyển sang tab Ask.

**Điều kiện trước khi làm:** Phase 3–4 đã xong (có `Profile.xp`, đã hỏi AI được ít nhất 1 câu để test XP tăng).

**Không làm ở Phase này:** recent question, subject stats → để Phase 6.

---

## Tổng quan các file sẽ chạm

| File | Loại | Việc |
|------|------|------|
| `UserDao.kt` | Sửa | Thêm `getById` để lấy `fullName` |
| `colors.xml` | Sửa | Thêm màu streak + track XP |
| `strings_home.xml` | **Mới** | Chuỗi chữ cho Home |
| `bg_level_badge.xml` | **Mới** | Nền tròn badge cấp |
| `bg_streak_pill.xml` | **Mới** | Nền pill streak |
| `bg_xp_progress.xml` | **Mới** | Thanh XP (track + fill) |
| `fragment_home.xml` | **Thay toàn bộ** | Layout dashboard Home |
| `HomeFragment.kt` | **Thay toàn bộ** | Đọc DB + bind UI + CTA Ask |

---

## Step 1) Thêm `getById` vào `UserDao`

**Giải thích:** Home cần tên người dùng (`fullName`) từ bảng `users`. Hiện `UserDao` chỉ có `findByEmail` (dùng lúc login). Thêm `getById` để lấy user theo `user_id` đang lưu trong `SharedPreferences`. Kiểu trả về là `User?` vì Room trả `null` khi không có dòng nào khớp.

**File:** `app/src/main/java/com/aistudy/aistudy/database/UserDao.kt`

**Tìm cuối interface (trước dấu `}`):**
```kotlin
    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    fun findByEmail(email: String): User?
}
```

**Đổi thành:**
```kotlin
    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    fun findByEmail(email: String): User?

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    fun getById(id: Long): User?
}
```

**Kiểm tra nhanh:** Build project — KSP sinh lại `UserDao_Impl` (trong `app/build/generated/ksp/debug/kotlin/...`) có hàm `getById`.

---

## Step 2) Thêm màu streak / XP vào `colors.xml`

**Giải thích:** Web dùng màu `streak` (cam) cho chuỗi ngày học. Thanh XP cần màu track xám nhạt. Palette auth (xanh brand) vẫn dùng lại cho badge + nút CTA.

**File:** `app/src/main/res/values/colors.xml`

**Chèn trước dòng `</resources>` (cách khối màu `auth_*` một dòng trống):**
```xml
    <color name="streak">#E67E22</color>
    <color name="streak_soft">#FDF0E4</color>
    <color name="xp_track">#E8EAED</color>
```

---

## Step 3) Tạo file chuỗi `strings_home.xml`

**Giải thích:** Tách string Home ra file riêng (giống `strings_auth.xml`, `strings_onboarding.xml`) để dễ tìm và không làm `strings.xml` phình to. `%1$s`, `%d` là placeholder — Kotlin điền tên / số vào lúc chạy qua `getString(id, ...)`.

**Tạo file mới:** `app/src/main/res/values/strings_home.xml`

**Dán toàn bộ:**
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
</resources>
```

| Key | Ý nghĩa |
|-----|---------|
| `home_level_label` | Nhãn nhỏ phía trên badge |
| `home_scholar` | Chữ cố định cạnh badge (giống web “Học giả”) |
| `home_streak_days` | `0 days`, `3 days`… |
| `home_xp_progress` | Ví dụ `50 / 1200 XP` |
| `home_xp_percent` | Ví dụ `4%` |
| `home_greeting` | `Hi, Nam` |
| `home_ask_cta` | Text nút chuyển sang Ask |

---

## Step 4) Tạo drawable nền badge cấp

**Giải thích:** `drawable` XML vẽ hình nền không cần file PNG. Badge level là hình tròn màu brand nhạt.

**Tạo file mới:** `app/src/main/res/drawable/bg_level_badge.xml`

**Dán toàn bộ:**
```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="oval">
    <solid android:color="@color/auth_brand_soft" />
    <stroke
        android:width="1dp"
        android:color="@color/auth_brand_ring" />
</shape>
```

---

## Step 5) Tạo drawable pill streak

**Giải thích:** Pill = hình chữ nhật bo góc rất lớn (`999dp`) để nhìn như viên thuốc. Viền + chữ màu `streak`.

**Tạo file mới:** `app/src/main/res/drawable/bg_streak_pill.xml`

**Dán toàn bộ:**
```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="@color/streak_soft" />
    <corners android:radius="999dp" />
    <stroke
        android:width="1dp"
        android:color="@color/streak" />
</shape>
```

---

## Step 6) Tạo drawable thanh XP

**Giải thích:** `ProgressBar` kiểu ngang dùng `layer-list`: lớp `background` (xám) + lớp `progress` (xanh brand) bị `clip` theo % progress.

**Tạo file mới:** `app/src/main/res/drawable/bg_xp_progress.xml`

**Dán toàn bộ:**
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

---

## Step 7) Thay layout `fragment_home.xml`

**Giải thích:** Placeholder cũ chỉ có chữ “Home” giữa màn hình. Layout mới có 3 khối: (1) header level + streak, (2) XP label + ProgressBar, (3) greeting + CTA Ask. Dùng `ScrollView` để Phase 6 thêm recent/stats mà không bị chật.

**File:** `app/src/main/res/layout/fragment_home.xml`  
**Xóa hết nội dung cũ, dán toàn bộ:**

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
    </LinearLayout>
</ScrollView>
```

**Các `@+id` quan trọng (Kotlin gọi `findViewById`):**

| id | Widget | Dữ liệu bind |
|----|--------|--------------|
| `tv_level_badge` | TextView | Số cấp = `maxOf(1, xp / 100)` |
| `tv_streak` | TextView | `profile.streak` |
| `tv_xp_label` | TextView | `xp / 1200 XP` |
| `tv_xp_percent` | TextView | % thanh XP |
| `progress_xp` | ProgressBar | 0–100 |
| `tv_greeting` | TextView | `Hi, {fullName}` |
| `btn_ask` | MaterialButton | Click → tab Ask |

---

## Step 8) Viết logic `HomeFragment.kt`

**Giải thích:**
1. `onCreateView` — inflate layout (giống các Fragment khác).
2. `onViewCreated` — gán các view `lateinit var` bằng `findViewById` + gắn click CTA bằng lambda `setOnClickListener { ... }`.
3. CTA gán `selectedItemId = R.id.nav_ask` cho `bottom_nav` — **không** start Activity mới; BottomNav sẵn có sẽ đổi sang `AskFragment`.
4. `onResume` gọi `loadDashboard()` — mỗi lần quay lại tab Home (sau khi hỏi AI +XP) số liệu được refresh.
5. Công thức giống web (`index.tsx`): `XP_MAX = 1200` (khai báo `private const val` trong `companion object`), cấp = `maxOf(1, xp / 100)`, `%` = `minOf(100, ((xp * 100f) / XP_MAX).roundToInt())`.
6. `user` và `profile` có kiểu nullable (`User?`, `Profile?`); đọc giá trị bằng `?.` và dùng `?:` để lấy mặc định (`""`, `0`) khi `null`.

**File:** `app/src/main/java/com/aistudy/aistudy/home/HomeFragment.kt`  
**Thay toàn bộ nội dung bằng:**

```kotlin
package com.aistudy.aistudy.home

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.aistudy.aistudy.MainActivity
import com.aistudy.aistudy.R
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

        btnAsk.setOnClickListener {
            val activity = activity
            if (activity is MainActivity) {
                activity.findViewById<BottomNavigationView>(R.id.bottom_nav)?.selectedItemId = R.id.nav_ask
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
    }
}
```

**Luồng dữ liệu:**

```
SharedPreferences (user_id)
        │
        ├── UserDao.getById ────────► fullName ──► tv_greeting
        │
        └── ProfileDao.getByUserId ─► xp, streak
                                      │
                                      ├── level badge
                                      ├── streak pill
                                      └── XP bar + %
```

**Lưu ý:**
- `val activity = activity` rồi `if (activity is MainActivity)` — smart cast sang `MainActivity` và tránh crash nếu Fragment không nằm trong Main. `findViewById<BottomNavigationView>(...)?.` bỏ qua khi không tìm thấy view.
- Gán `selectedItemId` kích hoạt listener đã viết sẵn trong `MainActivity` → replace `AskFragment`.
- `onResume` quan trọng: nếu chỉ load trong `onViewCreated`, XP không cập nhật khi user hỏi xong rồi quay Home.
- Các view khai báo `lateinit var` nên phải gán trong `onViewCreated` trước khi `loadDashboard()` chạy ở `onResume`.
- `XP_MAX` là `Int`, `xp * 100f` là `Float`; `roundToInt()` (import `kotlin.math.roundToInt`) làm tròn về `Int` cho `ProgressBar`.

---

## Step 9) Build & test tay

**Giải thích:** Không cần DB migration mới (không đổi schema Room). Chỉ thêm query + UI.

### Checklist test

1. **Login** bằng tài khoản đã register (có `fullName`).
2. Mở tab **Home** → thấy `Hi, {tên}`, badge cấp ≥ 1, `0 days` (hoặc streak hiện có), `0 / 1200 XP` nếu chưa hỏi.
3. Bấm **Ask a question** → BottomNav nhảy sang tab Ask (highlight đúng icon Ask).
4. Hỏi AI 1 câu thành công (+50 XP ở Phase 4) → quay lại Home → XP = `50 / 1200 XP`, % ≈ 4%, badge vẫn cấp 1 (`50/100`).
5. Hỏi thêm đến khi XP ≥ 100 → badge hiện cấp 2.

### Lỗi thường gặp

| Triệu chứng | Nguyên nhân hay gặp | Cách xử lý |
|-------------|---------------------|------------|
| `Unresolved reference 'tv_...'` ở `R.id` | Layout chưa sync / typo id | Build → Rebuild Project; so khớp id XML với Kotlin |
| `Unresolved reference 'getById'` | Chưa thêm hàm vào `UserDao` hoặc KSP chưa chạy lại | Làm Step 1; Rebuild Project |
| `UninitializedPropertyAccessException: lateinit property tvGreeting has not been initialized` | Thiếu dòng `findViewById` cho view đó trong `onViewCreated` | Bổ sung dòng gán view |
| Greeting trống `Hi, ` | User null hoặc `fullName` rỗng | Kiểm tra register có nhập tên; query `getById` đúng `userId` |
| CTA không đổi tab | Sai id `bottom_nav` / `nav_ask` | So với `activity_main.xml` và `menu_bottom_nav.xml` |
| XP không tăng trên Home | Quên `onResume` / chưa cộng XP ở Ask | Confirm Phase 4 cộng `profile.xp`; đảm bảo có `onResume` |
| ProgressBar không tô màu | Thiếu `progressDrawable` | Kiểm tra `bg_xp_progress.xml` + `@android:id/progress` |

---

## Mapping với web (tham khảo)

| Web `index.tsx` | Android Phase 5 |
|-----------------|-----------------|
| `xpMax = 1200` | `private const val XP_MAX = 1200` |
| `Math.max(1, Math.floor(xp / 100))` | `maxOf(1, xp / 100)` |
| Streak pill | `tv_streak` + `bg_streak_pill` |
| XP bar | `progress_xp` |
| Navigate `/ask` | `selectedItemId = R.id.nav_ask` |
| `fullName` | Thêm ở Android (web header không hiện tên; plan Phase 5 yêu cầu) |
| Recent + subject stats | **Chưa** — Phase 6 |

---

## Checklist hoàn thành Phase 5

- [ ] `UserDao.getById` đã thêm
- [ ] `colors.xml` có `streak`, `streak_soft`, `xp_track`
- [ ] `strings_home.xml` tạo xong
- [ ] 3 drawable: `bg_level_badge`, `bg_streak_pill`, `bg_xp_progress`
- [ ] `fragment_home.xml` không còn placeholder “Home”
- [ ] `HomeFragment` load User + Profile, CTA Ask hoạt động
- [ ] Test: tên đúng, XP đổi sau khi hỏi AI, nút chuyển tab Ask

**Tiếp theo:** Phase 6 — Home v2 (recent question + subject stats từ `QuestionAnswer`).
