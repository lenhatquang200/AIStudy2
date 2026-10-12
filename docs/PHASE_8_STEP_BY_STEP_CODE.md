# Phase 8: Polish — Logout + Hash password — Hướng dẫn từng bước


Mục tiêu Phase 8:

1. **Logout** trên tab Progress — xóa `user_id` trong `SharedPreferences`, về `LoginActivity`, không back lại Main
2. **UX nhỏ** — hiện email user cạnh nút Log out (giống web `progress.tsx`)
3. **Hash password** — đăng ký lưu SHA-256; đăng nhập so khớp hash (vẫn chấp nhận mật khẩu plain cũ)

**Điều kiện trước khi làm:** Phase 0–7 đã xong (Auth, ProgressFragment UI đầy đủ).

**Không làm ở Phase này:** đổi schema Room, đổi màu theme toàn app, thêm màn Settings riêng.

---

## Tổng quan các file sẽ chạm

| File | Loại | Việc |
|------|------|------|
| `strings_progress.xml` | Sửa | Thêm chữ `Log out` |
| `bg_logout_pill.xml` | **Mới** | Nền nút Log out (pill xám nhạt) |
| `fragment_progress.xml` | Sửa | Header: title + email + nút Logout |
| `ProgressFragment.kt` | Sửa | Bind email, `logout()` clear session |
| `PasswordHasher.kt` | **Mới** | SHA-256 + `matches()` |
| `RegisterActivity.kt` | Sửa | Lưu `PasswordHasher.hash(password)` |
| `LoginActivity.kt` | Sửa | Đổi so sánh `!=` → `PasswordHasher.matches` |

**Không đụng:** Room entities/DAO, Home / History / Ask, Gemini.

---

## Step 1) String nút Log out

**Giải thích:**  
Chữ UI không hard-code trong layout hay file Kotlin — để trong `strings_*.xml` cho dễ sửa và khớp convention Phase 5–7.

**File:** `app/src/main/res/values/strings_progress.xml`

**Tìm đầu file (sau `progress_title`):**
```xml
    <string name="progress_title">Learning progress</string>
    <string name="progress_xp_label">Total XP</string>
```

**Đổi thành:**
```xml
    <string name="progress_title">Learning progress</string>
    <string name="progress_logout">Log out</string>
    <string name="progress_xp_label">Total XP</string>
```

| Key | Ý nghĩa |
|-----|---------|
| `progress_logout` | Nhãn nút đăng xuất trên header Progress |

**Kiểm tra nhanh:** Build Resources — không lỗi thiếu `@string/progress_logout`.

---

## Step 2) Drawable nền nút Log out

**Giải thích:**  
Web dùng pill `rounded-full` nền secondary. Android dùng ripple + shape bo `999dp` để bấm có sóng, màu khớp `auth_card`.

**Tạo file mới:** `app/src/main/res/drawable/bg_logout_pill.xml`

**Dán toàn bộ:**
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

**Kiểm tra nhanh:** Preview drawable — pill xám, bo tròn.

---

## Step 3) Đổi header Progress — title + email + Log out

**Giải thích:**  
Giống `progress.tsx`: trái = tiêu đề + email; phải = nút Đăng xuất. Email lấy từ `User` theo `user_id` session.

**File:** `app/src/main/res/layout/fragment_progress.xml`

**Tìm khối title cũ (đầu `LinearLayout` trong `ScrollView`):**
```xml
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/progress_title"
            android:textColor="@color/auth_foreground"
            android:textSize="24sp"
            android:textStyle="bold" />

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="24dp"
            android:background="@drawable/bg_progress_xp_card"
```

**Thay bằng:**
```xml
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
```

| Id | Việc |
|----|------|
| `tv_user_email` | Hiện email tài khoản đang đăng nhập |
| `btn_logout` | Bấm → clear session + mở Login |

Title chưa có id ở Phase 8; `@+id/tv_progress_title` được thêm ở Phase 13.

**Kiểm tra nhanh:** Preview layout — header 2 cột, pill Log out bên phải.

---

## Step 4) Wire Logout + bind email trong `ProgressFragment`

**Giải thích:**  
Session chỉ là `SharedPreferences` key `user_id` (`LoginActivity.PREFS_AUTH` / `KEY_USER_ID` — hai `const val` trong `companion object` của `LoginActivity`). Logout = `remove` key đó rồi mở `LoginActivity` với `Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK` để stack Main/Onboarding không còn — nút Back không quay lại app đã login.

**File:** `app/src/main/java/com/aistudy/aistudy/progress/ProgressFragment.kt`

### 4a. Import thêm

**Thêm vào khối import (bỏ qua dòng nào đã có):**
```kotlin
import android.content.Context
import android.content.Intent
```

Kiểu `User` không cần import: `val user = db.userDao().getById(userId)` tự suy ra kiểu `User?`.

### 4b. Khai báo property mới (cùng chỗ property UI)

**Thêm ngay trước `private lateinit var tvXpTotal: TextView`:**
```kotlin
    private lateinit var tvUserEmail: TextView
    private lateinit var btnLogout: TextView
```

`lateinit` cho phép khai báo view non-null rồi gán trong `onViewCreated`; truy cập trước khi gán sẽ ném `UninitializedPropertyAccessException`.

### 4c. Trong `onViewCreated` — findViewById + click

**Đầu `onViewCreated`, ngay sau `super.onViewCreated(view, savedInstanceState)`, thêm:**
```kotlin
        tvUserEmail = view.findViewById(R.id.tv_user_email)
        btnLogout = view.findViewById(R.id.btn_logout)
```

**Cuối `onViewCreated` (sau các dòng gán `badge...`), thêm:**
```kotlin
        btnLogout.setOnClickListener { logout() }
```

### 4d. Hàm `logout()` — thêm trước `loadProgress()`

```kotlin
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
```

| Kotlin | Ý nghĩa |
|--------|---------|
| `context == null` | Fragment đã detach → bỏ qua |
| `LoginActivity::class.java` | Class reference cho `Intent` |
| `intent.flags = A or B` | Gán property `flags`, `or` là phép OR bit của `Int` |

### 4e. Trong `loadProgress()` — hiện email

**Sau khi đã có `userId` hợp lệ và dòng `val db = AppDatabase.getInstance(requireContext())`, thêm trước khi đọc Profile:**
```kotlin
        val user = db.userDao().getById(userId)
        tvUserEmail.text = user?.email ?: ""
```

`getById` trả `User?`; `user?.email` trả `null` nếu `user` null hoặc `email` null, `?: ""` thay bằng chuỗi rỗng.

`UserDao.getById` đã có từ Phase 0 — không cần thêm DAO.

**Kiểm tra nhanh:** Mở tab Progress → thấy email; bấm Log out → màn Sign in; Back không về Main.

---

## Step 5) Tạo `PasswordHasher` (SHA-256)

**Giải thích:**  
Plain text trong SQLite dễ lộ nếu ai đọc DB device. Hash một chiều: lưu hex SHA-256; lúc login hash lại input rồi so.  
`matches()` còn chấp nhận **plain cũ** (`plainPassword == stored`) để account đăng ký trước Phase 8 vẫn login được — không bắt Clear Data.

`PasswordHasher` là `object` (singleton Kotlin) nên gọi thẳng `PasswordHasher.hash(...)` như hàm tĩnh, không cần constructor private. `joinToString("") { "%02x".format(it) }` đổi từng byte thành 2 ký tự hex (byte âm được format như giá trị không dấu). `==` giữa hai `String` trong Kotlin so sánh nội dung.

**Tạo file mới:** `app/src/main/java/com/aistudy/aistudy/auth/PasswordHasher.kt`

**Dán toàn bộ:**
```kotlin
package com.aistudy.aistudy.auth

import java.security.MessageDigest

object PasswordHasher {

    fun hash(plainPassword: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(plainPassword.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun matches(plainPassword: String, stored: String?): Boolean {
        if (stored == null) {
            return false
        }
        if (plainPassword == stored) {
            return true
        }
        return hash(plainPassword) == stored
    }
}
```

| Hàm | Việc |
|-----|------|
| `hash` | Plain → chuỗi hex 64 ký tự |
| `matches` | Đúng nếu plain **hoặc** hash khớp `stored`; `stored` kiểu `String?` vì cột `password` nullable |

**Kiểm tra nhanh:** Compile package `auth` — object tồn tại, chỉ dùng JDK (`java.security.MessageDigest`), không cần import Android.

> Ghi chú: SHA-256 không salt đủ cho app local; production thường dùng bcrypt/argon2 + salt. Không cần thêm library ở Phase 8.

---

## Step 6) Register lưu hash

**Giải thích:**  
Từ giờ account mới không còn lưu mật khẩu thô trong cột `users.password`.

**File:** `app/src/main/java/com/aistudy/aistudy/auth/RegisterActivity.kt`

**Tìm:**
```kotlin
        user.password = password
```

**Đổi thành:**
```kotlin
        user.password = PasswordHasher.hash(password)
```

Cùng package `com.aistudy.aistudy.auth` → không cần import.

**Kiểm tra nhanh:** Đăng ký user mới → mở App Inspection / Database Inspector: cột `password` là hex dài, không còn chữ mật khẩu gốc.

---

## Step 7) Login dùng `PasswordHasher.matches`

**Giải thích:**  
Trước đây so `password != user.password`. Sau hash, phải so qua `matches` (cover cả user cũ plain + user mới hash). Sau khối `if`, Kotlin smart cast `user` thành non-null nên `user.id` dùng trực tiếp được.

**File:** `app/src/main/java/com/aistudy/aistudy/auth/LoginActivity.kt`

**Tìm:**
```kotlin
        if (user == null || password != user.password) {
            Toast.makeText(this, R.string.auth_error_invalid_credentials, Toast.LENGTH_SHORT).show()
            return
        }
```

**Đổi thành:**
```kotlin
        if (user == null || !PasswordHasher.matches(password, user.password)) {
            Toast.makeText(this, R.string.auth_error_invalid_credentials, Toast.LENGTH_SHORT).show()
            return
        }
```

**Kiểm tra nhanh:**  
- User đăng ký **sau** Step 6 → login OK.  
- User còn plain trong DB → login vẫn OK nhờ nhánh `plainPassword == stored` trong `matches`.  
- Sai mật khẩu → toast credentials.

---

## Step 8) Build & test tay

**Giải thích:** Không migration Room — chỉ session + password lưu dạng khác.

### Checklist Logout

1. Login → tab **Progress** → thấy email dưới title, pill **Log out** bên phải.
2. Bấm **Log out** → màn Sign in.
3. Nút Back hệ thống → **không** quay lại Main / Progress (stack đã `CLEAR_TASK`).
4. Mở lại app (cold start) → vẫn Sign in (`LoginActivity` không thấy `user_id`).
5. Login lại cùng account → vào Home bình thường; data Room (QA, XP) còn nguyên (logout **không** xóa DB).

### Checklist Hash password

1. Đăng ký account mới → trong DB `password` là hex ~64 ký tự.
2. Login đúng mật khẩu → vào app.
3. Login sai mật khẩu → lỗi credentials.
4. (Nếu còn) account cũ plain text → vẫn login được.

### Lỗi thường gặp

| Triệu chứng | Nguyên nhân hay gặp | Cách xử lý |
|-------------|---------------------|------------|
| `Unresolved reference: tv_user_email` | Layout chưa sync / id sai | Check `fragment_progress.xml` id, Sync Project |
| `UninitializedPropertyAccessException: lateinit property tvUserEmail has not been initialized` | Quên `findViewById` trong `onViewCreated` | Step 4c |
| `Unresolved reference: PasswordHasher` | File sai package / sai tên | Đặt `PasswordHasher.kt` trong `com.aistudy.aistudy.auth` |
| Log out rồi Back vẫn vào Main | Thiếu `FLAG_ACTIVITY_CLEAR_TASK` | So Intent flags Step 4d |
| Login fail sau register | Vẫn so `password != user.password` | Đã đổi `LoginActivity` Step 7? |
| Email trống | `userId` sai / `getById` trả `null` | Confirm session trước khi vào Main |

---

## Mapping với web (tham khảo)

| Web | Android Phase 8 |
|-----|-----------------|
| `progress.tsx` header + `signOut()` | `btn_logout` + `logout()` |
| `user?.email` dưới title | `tv_user_email` + `UserDao.getById` |
| `supabase.auth.signOut()` | `SharedPreferences.remove(KEY_USER_ID)` |
| (không có hash local) | `PasswordHasher` SHA-256 local Room |

---

## Checklist hoàn thành Phase 8

- [ ] `progress_logout` trong `strings_progress.xml`
- [ ] `bg_logout_pill.xml`
- [ ] Header Progress: `tv_user_email` + `btn_logout`
- [ ] `ProgressFragment.logout()` clear prefs + `CLEAR_TASK` → Login
- [ ] `PasswordHasher.kt`
- [ ] Register lưu hash; Login dùng `matches`
- [ ] Test: Log out sạch session; register/login hash; data Room còn sau logout

**Tiếp theo:** Phase 9 — difficulty, subject Language, answer extras, XP review.
