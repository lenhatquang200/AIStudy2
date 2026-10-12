# Phase 1: Auth (Room) + Main shell — BottomNavigation + 4 Fragment — Hướng dẫn từng bước

Mục tiêu Phase 1:
- Đăng ký / đăng nhập bằng email + mật khẩu, lưu tài khoản vào bảng `users` của Room, lưu phiên đăng nhập (`user_id`) vào `SharedPreferences`.
- Sau khi đăng nhập, `MainActivity` hiển thị thanh điều hướng dưới (BottomNavigationView) gồm 4 tab **Home / Ask / History / Progress**; mỗi tab thay một `Fragment` vào vùng nội dung phía trên.

**Điều kiện trước khi làm:** Phase 0 xong — Gradle đã cấu hình Kotlin + plugin KSP (`docs/PHASE_0_KOTLIN_SETUP.md`).

**Không làm ở Phase này:** Profile + Onboarding → Phase 2; nội dung thật của từng tab → Phase 3–7; băm mật khẩu → Phase 8 (Phase 1 lưu mật khẩu dạng chuỗi gốc).

---

## Tổng quan các file sẽ chạm

| File | Loại | Việc |
|------|------|------|
| `gradle/libs.versions.toml` | **Thay toàn bộ** | Khai báo appcompat, material, activity-ktx, constraintlayout, room, fragment |
| `app/build.gradle.kts` | **Thay toàn bộ** | Khai báo dependencies |
| `database/User.kt` | **Mới** | Entity bảng `users` |
| `database/UserDao.kt` | **Mới** | insert / findByEmail |
| `database/AppDatabase.kt` | **Mới** | Room database `version = 1` |
| `res/values/colors.xml` | **Thay toàn bộ** | Bảng màu `auth_*` |
| `res/values/dimens.xml` | **Mới** | Kích thước màn Auth |
| `res/values/strings.xml` | **Thay toàn bộ** | `app_name`, `hello_blank_fragment` |
| `res/values/strings_auth.xml` | **Mới** | Chuỗi chữ màn Auth |
| `res/values/themes.xml` | Kiểm tra | Theme Material3 NoActionBar |
| `res/drawable/bg_auth_shell_panel.xml` | **Mới** | Nền màn Auth |
| `res/drawable/bg_auth_brand_icon.xml` | **Mới** | Nền ô logo |
| `res/drawable/bg_auth_input.xml` | **Mới** | Nền ô nhập |
| `res/drawable/bg_auth_button.xml` | **Mới** | Nền nút chính |
| `res/drawable/ic_sparkles.xml` | **Mới** | Icon logo |
| `res/layout/auth_activity_login.xml` | **Mới** | Layout Đăng nhập |
| `res/layout/auth_activity_register.xml` | **Mới** | Layout Đăng ký |
| `auth/LoginActivity.kt` | **Mới** | Logic Đăng nhập + lưu session |
| `auth/RegisterActivity.kt` | **Mới** | Logic Đăng ký + lưu session |
| `res/menu/menu_bottom_nav.xml` | **Mới** | 4 item menu cho BottomNav |
| `res/layout/activity_main.xml` | **Thay toàn bộ** | `FragmentContainerView` + `BottomNavigationView` |
| `res/layout/fragment_home.xml` | **Mới** | Placeholder tab Home |
| `res/layout/fragment_ask.xml` | **Mới** | Placeholder tab Ask |
| `res/layout/fragment_history.xml` | **Mới** | Placeholder tab History |
| `res/layout/fragment_progress.xml` | **Mới** | Placeholder tab Progress |
| `home/HomeFragment.kt` | **Mới** | Fragment inflate `fragment_home` |
| `ask/AskFragment.kt` | **Mới** | Fragment inflate `fragment_ask` |
| `history/HistoryFragment.kt` | **Mới** | Fragment inflate `fragment_history` |
| `progress/ProgressFragment.kt` | **Mới** | Fragment inflate `fragment_progress` |
| `MainActivity.kt` | **Thay toàn bộ** | Kiểm tra session + gắn BottomNav |
| `AndroidManifest.xml` | **Thay toàn bộ** | `MainActivity` là launcher, khai báo Login/Register |

Cấu trúc package sau Phase 1:

```
app/src/main/java/com/aistudy/aistudy/
├── MainActivity.kt
├── ask/AskFragment.kt
├── auth/LoginActivity.kt
├── auth/RegisterActivity.kt
├── database/AppDatabase.kt
├── database/User.kt
├── database/UserDao.kt
├── history/HistoryFragment.kt
├── home/HomeFragment.kt
└── progress/ProgressFragment.kt
```

---

## Step 1) Khai báo thư viện trong `libs.versions.toml`

**Giải thích:**
- `appcompat`: `AppCompatActivity`.
- `material`: `MaterialButton`, `BottomNavigationView`, theme Material3.
- `activity-ktx`: hàm `enableEdgeToEdge()`.
- `constraintlayout`: `ConstraintLayout` cho `activity_main.xml`.
- `room-runtime` + `room-compiler`: database SQLite; `room-compiler` chạy qua KSP để sinh code DAO.
- `fragment`: `Fragment`, `FragmentContainerView`.
- Các phase sau thêm `recyclerview` (Phase 7) và `work` (Phase 14).

**File:** `gradle/libs.versions.toml`
**Thay toàn bộ nội dung bằng:**

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

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

---

## Step 2) Khai báo dependencies trong `app/build.gradle.kts`

**Giải thích:** `implementation(...)` đưa thư viện vào app; `ksp(libs.room.compiler)` chỉ chạy lúc build để sinh `AppDatabase_Impl`, `UserDao_Impl`. Phase 3–4 bổ sung phần đọc `GEMINI_API_KEY` từ `local.properties`.

**File:** `app/build.gradle.kts`
**Thay toàn bộ nội dung bằng:**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
}

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
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
```

**Kiểm tra nhanh:** **File → Sync Project with Gradle Files** — không lỗi `Unresolved reference` ở các dòng `libs.*`.

---

## Step 3) Entity `User`

**Giải thích:**
- `@Entity(tableName = "users")` → Room tạo bảng `users`.
- `indices = [Index(value = ["email"], unique = true)]` → cột `email` không được trùng; insert email trùng ném `SQLiteConstraintException`.
- `@PrimaryKey(autoGenerate = true)` + `id: Long = 0` → Room tự sinh id khi insert.
- Mọi property là `var` có giá trị mặc định để Room tạo object bằng constructor rỗng rồi gán từng cột; cột text khai báo `String?` vì SQLite cho phép `NULL`.

**Tạo package:** chuột phải `com.aistudy.aistudy` → **New → Package** → `database`.

**Tạo file mới:** `app/src/main/java/com/aistudy/aistudy/database/User.kt`

```kotlin
package com.aistudy.aistudy.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "users",
    indices = [Index(value = ["email"], unique = true)]
)
class User {

    @PrimaryKey(autoGenerate = true)
    var id: Long = 0

    var fullName: String? = null
    var email: String? = null
    var password: String? = null
}
```

| Property | Kiểu | Ví dụ | Ý nghĩa |
|----------|------|-------|---------|
| `id` | `Long` | `1` | Khóa chính tự tăng |
| `fullName` | `String?` | `"Nguyen Van A"` | Họ tên hiển thị |
| `email` | `String?` | `"a@school.edu"` | Unique, lưu chữ thường |
| `password` | `String?` | `"123456"` | Mật khẩu (Phase 8 đổi sang dạng băm) |

---

## Step 4) `UserDao`

**Giải thích:**
- `interface` gắn `@Dao`; KSP sinh class `UserDao_Impl` hiện thực các hàm.
- `@Insert(onConflict = OnConflictStrategy.ABORT)`: email trùng → ném exception, không ghi đè tài khoản cũ. Hàm trả `Long` là `id` vừa sinh.
- `findByEmail` trả `User?` vì không có dòng nào khớp thì Room trả `null`.

**Tạo file mới:** `app/src/main/java/com/aistudy/aistudy/database/UserDao.kt`

```kotlin
package com.aistudy.aistudy.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface UserDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(user: User): Long

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    fun findByEmail(email: String): User?
}
```

---

## Step 5) `AppDatabase` version 1

**Giải thích:**
- `@Database(entities = [User::class], version = 1)`: database chỉ có bảng `users`. Phase sau thêm entity thì tăng `version`.
- `exportSchema = false`: không xuất file JSON schema.
- `companion object` + `@Volatile` + `synchronized` → cả app dùng chung một instance; `instance ?: synchronized(this) { instance ?: ... }` tránh tạo 2 instance khi 2 luồng gọi cùng lúc.
- `allowMainThreadQueries()`: cho phép query trên main thread (dữ liệu nhỏ, code gọn).
- `fallbackToDestructiveMigration(true)`: khi `version` tăng mà không có migration, Room xóa và tạo lại toàn bộ bảng.
- File database trên máy: `aistudy.db`.

**Tạo file mới:** `app/src/main/java/com/aistudy/aistudy/database/AppDatabase.kt`

```kotlin
package com.aistudy.aistudy.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        User::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao

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

**Kiểm tra nhanh:** **Build → Make Project** — thư mục `app/build/generated/ksp/debug/kotlin/com/aistudy/aistudy/database/` có `AppDatabase_Impl.kt` và `UserDao_Impl.kt`.

---

## Step 6) Màu `colors.xml`

**Giải thích:** Bảng màu `auth_*` dùng cho màn Auth và được dùng lại ở Onboarding, Home, Ask… Phase 5 thêm màu streak/XP, Phase 13 thêm màu theme.

**File:** `app/src/main/res/values/colors.xml`
**Thay toàn bộ nội dung bằng:**

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
</resources>
```

| Màu | Giá trị | Dùng cho |
|-----|---------|----------|
| `auth_shell_outer` | `#F4F4F5` | Nền ngoài cùng màn Auth |
| `auth_surface` | `#F9FAFC` | Nền panel nội dung |
| `auth_foreground` | `#1C1F28` | Chữ chính |
| `auth_muted_foreground` | `#6E7583` | Chữ phụ, label, hint |
| `auth_card` | `#F2F4F7` | Nền ô nhập |
| `auth_brand` | `#3B9E74` | Màu thương hiệu (nút, icon) |
| `auth_brand_soft` | `#1A3B9E74` | Brand trong suốt 10% (nền logo) |
| `auth_brand_ring` | `#4D3B9E74` | Viền brand trong suốt 30% |
| `auth_primary_foreground` | `#F8FDFA` | Chữ trên nút brand |
| `auth_input_ring` | `#0D000000` | Viền ô nhập |

---

## Step 7) Kích thước `dimens.xml`

**Tạo file mới:** `app/src/main/res/values/dimens.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <dimen name="auth_screen_padding_horizontal">24dp</dimen>
    <dimen name="auth_screen_padding_vertical">40dp</dimen>
    <dimen name="auth_brand_icon_size">40dp</dimen>
    <dimen name="auth_brand_icon_radius">16dp</dimen>
    <dimen name="auth_header_gap">8dp</dimen>
    <dimen name="auth_header_margin_bottom">40dp</dimen>
    <dimen name="auth_title_margin_top">8dp</dimen>
    <dimen name="auth_form_margin_top">32dp</dimen>
    <dimen name="auth_field_spacing">16dp</dimen>
    <dimen name="auth_label_input_spacing">6dp</dimen>
    <dimen name="auth_input_height">44dp</dimen>
    <dimen name="auth_input_radius">12dp</dimen>
    <dimen name="auth_input_padding_horizontal">14dp</dimen>
    <dimen name="auth_toggle_margin_top">20dp</dimen>
</resources>
```

---

## Step 8) Chuỗi `strings.xml` và `strings_auth.xml`

**Giải thích:** `strings.xml` giữ `app_name` (tên app trên launcher) và `hello_blank_fragment`; Phase 3–4 thêm các key `ask_*` ngay sau `hello_blank_fragment`. Chuỗi của màn Auth tách riêng vào `strings_auth.xml` để dễ tìm.

**File:** `app/src/main/res/values/strings.xml`
**Thay toàn bộ nội dung bằng:**

```xml
<resources>
    <string name="app_name">AIStudy</string>
    <string name="hello_blank_fragment">Hello blank fragment</string>
</resources>
```

**Tạo file mới:** `app/src/main/res/values/strings_auth.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="auth_app_title">AI Study Mentor</string>
    <string name="auth_app_subtitle">Learn with AI anytime</string>

    <string name="auth_login_title">Sign in</string>
    <string name="auth_login_subtitle">Continue your learning journey.</string>
    <string name="auth_login_submit">Sign in</string>
    <string name="auth_login_toggle">Don\'t have an account? Sign up</string>

    <string name="auth_register_title">Create account</string>
    <string name="auth_register_subtitle">Start saving question history and XP to your account.</string>
    <string name="auth_register_submit">Sign up</string>
    <string name="auth_register_toggle">Already have an account? Sign in</string>

    <string name="auth_label_name">Full name</string>
    <string name="auth_label_email">Email</string>
    <string name="auth_label_password">Password</string>
    <string name="auth_hint_name">Your full name</string>
    <string name="auth_hint_email">you@school.edu</string>
    <string name="auth_hint_password">At least 6 characters</string>
    <string name="auth_submit_loading">Processing…</string>

    <string name="auth_error_empty_fields">Please fill in all fields.</string>
    <string name="auth_error_name_required">Please enter your full name.</string>
    <string name="auth_error_invalid_email">Please enter a valid email.</string>
    <string name="auth_error_password_short">Password must be at least 6 characters.</string>
    <string name="auth_error_email_taken">This email is already registered.</string>
    <string name="auth_error_invalid_credentials">Invalid email or password.</string>
    <string name="auth_error_unknown">Something went wrong. Please try again.</string>
    <string name="auth_success_register">Account created successfully.</string>
</resources>
```

**Lưu ý:** Dấu nháy đơn trong chuỗi XML phải viết `\'` (ví dụ `Don\'t`), nếu không build báo lỗi `Apostrophe not preceded by \`.

---

## Step 9) Kiểm tra theme `themes.xml`

**Giải thích:** `MaterialButton` và `BottomNavigationView` cần theme thuộc họ `Theme.Material3` (hoặc `Theme.MaterialComponents`). `NoActionBar` bỏ thanh tiêu đề mặc định vì các màn tự vẽ header.

**File:** `app/src/main/res/values/themes.xml` — nội dung cần có:

```xml
<resources xmlns:tools="http://schemas.android.com/tools">
    <style name="Base.Theme.AIStudy" parent="Theme.Material3.DayNight.NoActionBar">
    </style>

    <style name="Theme.AIStudy" parent="Base.Theme.AIStudy" />
</resources>
```

**File:** `app/src/main/res/values-night/themes.xml` — nội dung cần có:

```xml
<resources xmlns:tools="http://schemas.android.com/tools">
    <style name="Base.Theme.AIStudy" parent="Theme.Material3.DayNight.NoActionBar">
    </style>
</resources>
```

---

## Step 10) Drawable cho màn Auth

**Giải thích:**
- `shape` vẽ hình chữ nhật có màu nền (`solid`), bo góc (`corners`), viền (`stroke`).
- `layer-list` xếp chồng nhiều lớp drawable; ở đây một lớp nền.
- `ripple` tạo hiệu ứng gợn sóng khi bấm, lớp `item` bên trong là nền nút.
- `vector` là icon dạng path, co giãn không vỡ hình.

**Tạo file mới:** `app/src/main/res/drawable/bg_auth_shell_panel.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item>
        <shape android:shape="rectangle">
            <solid android:color="@color/auth_surface" />
        </shape>
    </item>
</layer-list>
```

**Tạo file mới:** `app/src/main/res/drawable/bg_auth_brand_icon.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="@color/auth_brand_soft" />
    <corners android:radius="@dimen/auth_brand_icon_radius" />
    <stroke
        android:width="1dp"
        android:color="@color/auth_brand_ring" />
</shape>
```

**Tạo file mới:** `app/src/main/res/drawable/bg_auth_input.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="@color/auth_card" />
    <corners android:radius="@dimen/auth_input_radius" />
    <stroke
        android:width="1dp"
        android:color="@color/auth_input_ring" />
</shape>
```

**Tạo file mới:** `app/src/main/res/drawable/bg_auth_button.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<ripple xmlns:android="http://schemas.android.com/apk/res/android"
    android:color="#33FFFFFF">
    <item>
        <shape android:shape="rectangle">
            <solid android:color="@color/auth_brand" />
            <corners android:radius="@dimen/auth_input_radius" />
            <stroke
                android:width="1dp"
                android:color="@color/auth_brand_ring" />
        </shape>
    </item>
</ripple>
```

**Tạo file mới:** `app/src/main/res/drawable/ic_sparkles.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="20dp"
    android:height="20dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="@color/auth_brand"
        android:pathData="M9.937,15.5A2,2 0,0 0,8.5 14.063l-6.135,-1.582a0.5,0.5 0,0 1,0 -0.962L8.5,9.936A2,2 0,0 0,9.937 8.5l1.582,-6.135a0.5,0.5 0,0 1,0.962 0L14.063,8.5A2,2 0,0 0,15.5 9.937l6.135,1.581a0.5,0.5 0,0 1,0 0.964L15.5,14.063a2,2 0,0 0,-1.437 1.437l-1.582,6.135a0.5,0.5 0,0 1,-0.962 0z" />
</vector>
```

---

## Step 11) Layout Đăng nhập `auth_activity_login.xml`

**Giải thích:**
- `FrameLayout` ngoài cùng tô `auth_shell_outer`; `ScrollView` + `fillViewport="true"` cho form cuộn được khi bàn phím mở.
- Header: ô logo (`bg_auth_brand_icon` + `ic_sparkles`) và 2 dòng tên app.
- Form: label + `EditText` cho email (`edit_email`) và mật khẩu (`edit_password`), nút `btn_submit`.
- `MaterialButton` dùng nền riêng nên đặt `app:backgroundTint="@null"` (không thì Material tô đè màu theme), `insetTop` / `insetBottom = 0dp` để nút cao đúng `auth_input_height`.
- `btn_toggle_mode`: dòng chữ chuyển sang màn Đăng ký.

**Tạo file mới:** `app/src/main/res/layout/auth_activity_login.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/auth_shell_outer">

    <ScrollView
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:background="@drawable/bg_auth_shell_panel"
        android:clipToPadding="false"
        android:fillViewport="true">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:paddingHorizontal="@dimen/auth_screen_padding_horizontal"
            android:paddingTop="@dimen/auth_screen_padding_vertical"
            android:paddingBottom="@dimen/auth_screen_padding_vertical">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginBottom="@dimen/auth_header_margin_bottom"
                android:gravity="center_vertical"
                android:orientation="horizontal">

                <FrameLayout
                    android:layout_width="@dimen/auth_brand_icon_size"
                    android:layout_height="@dimen/auth_brand_icon_size"
                    android:background="@drawable/bg_auth_brand_icon">

                    <ImageView
                        android:layout_width="20dp"
                        android:layout_height="20dp"
                        android:layout_gravity="center"
                        android:contentDescription="@null"
                        android:importantForAccessibility="no"
                        android:src="@drawable/ic_sparkles" />
                </FrameLayout>

                <LinearLayout
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_marginStart="@dimen/auth_header_gap"
                    android:layout_weight="1"
                    android:orientation="vertical">

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@string/auth_app_title"
                        android:textColor="@color/auth_foreground"
                        android:textSize="14sp"
                        android:textStyle="bold" />

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@string/auth_app_subtitle"
                        android:textColor="@color/auth_muted_foreground"
                        android:textSize="11sp" />
                </LinearLayout>
            </LinearLayout>

            <TextView
                android:id="@+id/tv_auth_title"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="@string/auth_login_title"
                android:textColor="@color/auth_foreground"
                android:textSize="24sp"
                android:textStyle="bold" />

            <TextView
                android:id="@+id/tv_auth_subtitle"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="@dimen/auth_title_margin_top"
                android:text="@string/auth_login_subtitle"
                android:textColor="@color/auth_muted_foreground"
                android:textSize="14sp" />

            <LinearLayout
                android:id="@+id/layout_auth_form"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="@dimen/auth_form_margin_top"
                android:orientation="vertical">

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical">

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@string/auth_label_email"
                        android:textColor="@color/auth_muted_foreground"
                        android:textSize="12sp"
                        android:textStyle="bold" />

                    <EditText
                        android:id="@+id/edit_email"
                        android:layout_width="match_parent"
                        android:layout_height="@dimen/auth_input_height"
                        android:layout_marginTop="@dimen/auth_label_input_spacing"
                        android:autofillHints="emailAddress"
                        android:background="@drawable/bg_auth_input"
                        android:hint="@string/auth_hint_email"
                        android:importantForAutofill="yes"
                        android:inputType="textEmailAddress"
                        android:paddingHorizontal="@dimen/auth_input_padding_horizontal"
                        android:textColor="@color/auth_foreground"
                        android:textColorHint="@color/auth_muted_foreground"
                        android:textSize="14sp" />
                </LinearLayout>

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="@dimen/auth_field_spacing"
                    android:orientation="vertical">

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@string/auth_label_password"
                        android:textColor="@color/auth_muted_foreground"
                        android:textSize="12sp"
                        android:textStyle="bold" />

                    <EditText
                        android:id="@+id/edit_password"
                        android:layout_width="match_parent"
                        android:layout_height="@dimen/auth_input_height"
                        android:layout_marginTop="@dimen/auth_label_input_spacing"
                        android:autofillHints="password"
                        android:background="@drawable/bg_auth_input"
                        android:hint="@string/auth_hint_password"
                        android:importantForAutofill="yes"
                        android:inputType="textPassword"
                        android:paddingHorizontal="@dimen/auth_input_padding_horizontal"
                        android:textColor="@color/auth_foreground"
                        android:textColorHint="@color/auth_muted_foreground"
                        android:textSize="14sp" />
                </LinearLayout>

                <com.google.android.material.button.MaterialButton
                    android:id="@+id/btn_submit"
                    android:layout_width="match_parent"
                    android:layout_height="@dimen/auth_input_height"
                    android:layout_marginTop="@dimen/auth_field_spacing"
                    android:background="@drawable/bg_auth_button"
                    android:insetTop="0dp"
                    android:insetBottom="0dp"
                    android:stateListAnimator="@null"
                    android:text="@string/auth_login_submit"
                    android:textAllCaps="false"
                    android:textColor="@color/auth_primary_foreground"
                    android:textSize="14sp"
                    android:textStyle="bold"
                    app:backgroundTint="@null"
                    app:cornerRadius="@dimen/auth_input_radius"
                    app:rippleColor="#33FFFFFF"
                    tools:ignore="UnusedAttribute" />
            </LinearLayout>

            <TextView
                android:id="@+id/btn_toggle_mode"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="@dimen/auth_toggle_margin_top"
                android:gravity="center"
                android:text="@string/auth_login_toggle"
                android:textColor="@color/auth_muted_foreground"
                android:textSize="12sp" />
        </LinearLayout>
    </ScrollView>
</FrameLayout>
```

| id | View | Dùng trong Kotlin |
|----|------|-------------------|
| `edit_email` | `EditText` | Đọc email |
| `edit_password` | `EditText` | Đọc mật khẩu |
| `btn_submit` | `MaterialButton` | Gọi `login()` |
| `btn_toggle_mode` | `TextView` | Mở `RegisterActivity` |

---

## Step 12) Layout Đăng ký `auth_activity_register.xml`

**Giải thích:** Cấu trúc giống màn Đăng nhập, thêm ô **Full name** (`edit_full_name`, `inputType="textPersonName|textCapWords"` tự viết hoa chữ đầu mỗi từ) ở đầu form; ô mật khẩu dùng `autofillHints="newPassword"`.

**Tạo file mới:** `app/src/main/res/layout/auth_activity_register.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/auth_shell_outer">

    <ScrollView
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:background="@drawable/bg_auth_shell_panel"
        android:clipToPadding="false"
        android:fillViewport="true">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:paddingHorizontal="@dimen/auth_screen_padding_horizontal"
            android:paddingTop="@dimen/auth_screen_padding_vertical"
            android:paddingBottom="@dimen/auth_screen_padding_vertical">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginBottom="@dimen/auth_header_margin_bottom"
                android:gravity="center_vertical"
                android:orientation="horizontal">

                <FrameLayout
                    android:layout_width="@dimen/auth_brand_icon_size"
                    android:layout_height="@dimen/auth_brand_icon_size"
                    android:background="@drawable/bg_auth_brand_icon">

                    <ImageView
                        android:layout_width="20dp"
                        android:layout_height="20dp"
                        android:layout_gravity="center"
                        android:contentDescription="@null"
                        android:importantForAccessibility="no"
                        android:src="@drawable/ic_sparkles" />
                </FrameLayout>

                <LinearLayout
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_marginStart="@dimen/auth_header_gap"
                    android:layout_weight="1"
                    android:orientation="vertical">

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@string/auth_app_title"
                        android:textColor="@color/auth_foreground"
                        android:textSize="14sp"
                        android:textStyle="bold" />

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@string/auth_app_subtitle"
                        android:textColor="@color/auth_muted_foreground"
                        android:textSize="11sp" />
                </LinearLayout>
            </LinearLayout>

            <TextView
                android:id="@+id/tv_auth_title"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="@string/auth_register_title"
                android:textColor="@color/auth_foreground"
                android:textSize="24sp"
                android:textStyle="bold" />

            <TextView
                android:id="@+id/tv_auth_subtitle"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="@dimen/auth_title_margin_top"
                android:text="@string/auth_register_subtitle"
                android:textColor="@color/auth_muted_foreground"
                android:textSize="14sp" />

            <LinearLayout
                android:id="@+id/layout_auth_form"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="@dimen/auth_form_margin_top"
                android:orientation="vertical">

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical">

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@string/auth_label_name"
                        android:textColor="@color/auth_muted_foreground"
                        android:textSize="12sp"
                        android:textStyle="bold" />

                    <EditText
                        android:id="@+id/edit_full_name"
                        android:layout_width="match_parent"
                        android:layout_height="@dimen/auth_input_height"
                        android:layout_marginTop="@dimen/auth_label_input_spacing"
                        android:autofillHints="personName"
                        android:background="@drawable/bg_auth_input"
                        android:hint="@string/auth_hint_name"
                        android:importantForAutofill="yes"
                        android:inputType="textPersonName|textCapWords"
                        android:paddingHorizontal="@dimen/auth_input_padding_horizontal"
                        android:textColor="@color/auth_foreground"
                        android:textColorHint="@color/auth_muted_foreground"
                        android:textSize="14sp" />
                </LinearLayout>

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="@dimen/auth_field_spacing"
                    android:orientation="vertical">

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@string/auth_label_email"
                        android:textColor="@color/auth_muted_foreground"
                        android:textSize="12sp"
                        android:textStyle="bold" />

                    <EditText
                        android:id="@+id/edit_email"
                        android:layout_width="match_parent"
                        android:layout_height="@dimen/auth_input_height"
                        android:layout_marginTop="@dimen/auth_label_input_spacing"
                        android:autofillHints="emailAddress"
                        android:background="@drawable/bg_auth_input"
                        android:hint="@string/auth_hint_email"
                        android:importantForAutofill="yes"
                        android:inputType="textEmailAddress"
                        android:paddingHorizontal="@dimen/auth_input_padding_horizontal"
                        android:textColor="@color/auth_foreground"
                        android:textColorHint="@color/auth_muted_foreground"
                        android:textSize="14sp" />
                </LinearLayout>

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="@dimen/auth_field_spacing"
                    android:orientation="vertical">

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@string/auth_label_password"
                        android:textColor="@color/auth_muted_foreground"
                        android:textSize="12sp"
                        android:textStyle="bold" />

                    <EditText
                        android:id="@+id/edit_password"
                        android:layout_width="match_parent"
                        android:layout_height="@dimen/auth_input_height"
                        android:layout_marginTop="@dimen/auth_label_input_spacing"
                        android:autofillHints="newPassword"
                        android:background="@drawable/bg_auth_input"
                        android:hint="@string/auth_hint_password"
                        android:importantForAutofill="yes"
                        android:inputType="textPassword"
                        android:paddingHorizontal="@dimen/auth_input_padding_horizontal"
                        android:textColor="@color/auth_foreground"
                        android:textColorHint="@color/auth_muted_foreground"
                        android:textSize="14sp" />
                </LinearLayout>

                <com.google.android.material.button.MaterialButton
                    android:id="@+id/btn_submit"
                    android:layout_width="match_parent"
                    android:layout_height="@dimen/auth_input_height"
                    android:layout_marginTop="@dimen/auth_field_spacing"
                    android:background="@drawable/bg_auth_button"
                    android:insetTop="0dp"
                    android:insetBottom="0dp"
                    android:stateListAnimator="@null"
                    android:text="@string/auth_register_submit"
                    android:textAllCaps="false"
                    android:textColor="@color/auth_primary_foreground"
                    android:textSize="14sp"
                    android:textStyle="bold"
                    app:backgroundTint="@null"
                    app:cornerRadius="@dimen/auth_input_radius"
                    app:rippleColor="#33FFFFFF"
                    tools:ignore="UnusedAttribute" />
            </LinearLayout>

            <TextView
                android:id="@+id/btn_toggle_mode"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="@dimen/auth_toggle_margin_top"
                android:gravity="center"
                android:text="@string/auth_register_toggle"
                android:textColor="@color/auth_muted_foreground"
                android:textSize="12sp" />
        </LinearLayout>
    </ScrollView>
</FrameLayout>
```

---

## Step 13) `LoginActivity`

**Giải thích:**
1. **Hằng số session:** `PREFS_AUTH = "auth"` (tên file `SharedPreferences`), `KEY_USER_ID = "user_id"`. Đặt trong `companion object` để nơi khác gọi `LoginActivity.PREFS_AUTH`.
2. **Đã đăng nhập:** `user_id` khác `-1` → mở thẳng `MainActivity`, `finish()` và `return` trước `setContentView`.
3. **Insets:** listener trên `android.R.id.content` cộng padding bằng kích thước status bar / navigation bar.
4. **Kiểm tra dữ liệu nhập:** rỗng → `auth_error_empty_fields`; sai định dạng email (`Patterns.EMAIL_ADDRESS`) → `auth_error_invalid_email`; mật khẩu dưới 6 ký tự → `auth_error_password_short`. Email được `trim()` + `lowercase()` để so khớp đúng với lúc đăng ký.
5. **Xác thực:** `findByEmail` trả `null` hoặc mật khẩu khác → `auth_error_invalid_credentials`. Sau `user == null ||`, Kotlin smart cast `user` thành non-null nên đọc được `user.password`, `user.id`.
6. **Lưu session:** `putLong(KEY_USER_ID, user.id)` + `apply()` (ghi bất đồng bộ), rồi mở `MainActivity` và `finish()` để nút Back không quay lại Login.

**Tạo package:** chuột phải `com.aistudy.aistudy` → **New → Package** → `auth`.

**Tạo file mới:** `app/src/main/java/com/aistudy/aistudy/auth/LoginActivity.kt`

```kotlin
package com.aistudy.aistudy.auth

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.aistudy.aistudy.MainActivity
import com.aistudy.aistudy.R
import com.aistudy.aistudy.database.AppDatabase

class LoginActivity : AppCompatActivity() {

    companion object {
        const val PREFS_AUTH = "auth"
        const val KEY_USER_ID = "user_id"
    }

    private lateinit var editEmail: EditText
    private lateinit var editPassword: EditText
    private lateinit var btnSubmit: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences(PREFS_AUTH, MODE_PRIVATE)
        if (prefs.getLong(KEY_USER_ID, -1) != -1L) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        enableEdgeToEdge()
        setContentView(R.layout.auth_activity_login)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        editEmail = findViewById(R.id.edit_email)
        editPassword = findViewById(R.id.edit_password)
        btnSubmit = findViewById(R.id.btn_submit)

        findViewById<View>(R.id.btn_toggle_mode).setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        btnSubmit.setOnClickListener { login() }
    }

    private fun login() {
        val email = editEmail.text.toString().trim().lowercase()
        val password = editPassword.text.toString()
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, R.string.auth_error_empty_fields, Toast.LENGTH_SHORT).show()
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, R.string.auth_error_invalid_email, Toast.LENGTH_SHORT).show()
            return
        }
        if (password.length < 6) {
            Toast.makeText(this, R.string.auth_error_password_short, Toast.LENGTH_SHORT).show()
            return
        }
        val userDao = AppDatabase.getInstance(this).userDao()
        val user = userDao.findByEmail(email)

        if (user == null || password != user.password) {
            Toast.makeText(this, R.string.auth_error_invalid_credentials, Toast.LENGTH_SHORT).show()
            return
        }

        getSharedPreferences(PREFS_AUTH, MODE_PRIVATE)
            .edit()
            .putLong(KEY_USER_ID, user.id)
            .apply()

        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
```

---

## Step 14) `RegisterActivity`

**Giải thích:**
1. `btn_toggle_mode` gọi `finish()` → quay lại `LoginActivity` đang nằm dưới stack.
2. Kiểm tra: họ tên rỗng → `auth_error_name_required`; các rule email / mật khẩu giống Login.
3. `findByEmail(email) != null` → email đã tồn tại → `auth_error_email_taken`.
4. Tạo `User()`, gán `fullName`, `email`, `password`, gọi `userDao.insert(user)` lấy `userId`.
5. `try / catch (e: SQLiteConstraintException)`: phòng trường hợp email bị chèn trùng giữa lúc kiểm tra và lúc insert (index unique chặn lại).
6. Đăng ký xong lưu luôn session (`LoginActivity.PREFS_AUTH`, `LoginActivity.KEY_USER_ID`) rồi vào `MainActivity`.

**Tạo file mới:** `app/src/main/java/com/aistudy/aistudy/auth/RegisterActivity.kt`

```kotlin
package com.aistudy.aistudy.auth

import android.content.Intent
import android.database.sqlite.SQLiteConstraintException
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.aistudy.aistudy.MainActivity
import com.aistudy.aistudy.R
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.User

class RegisterActivity : AppCompatActivity() {

    private lateinit var editFullName: EditText
    private lateinit var editEmail: EditText
    private lateinit var editPassword: EditText
    private lateinit var btnSubmit: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.auth_activity_register)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        editFullName = findViewById(R.id.edit_full_name)
        editEmail = findViewById(R.id.edit_email)
        editPassword = findViewById(R.id.edit_password)
        btnSubmit = findViewById(R.id.btn_submit)

        findViewById<View>(R.id.btn_toggle_mode).setOnClickListener { finish() }

        btnSubmit.setOnClickListener { register() }
    }

    private fun register() {
        val fullName = editFullName.text.toString().trim()
        val email = editEmail.text.toString().trim().lowercase()
        val password = editPassword.text.toString()

        if (fullName.isEmpty()) {
            Toast.makeText(this, R.string.auth_error_name_required, Toast.LENGTH_SHORT).show()
            return
        }
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, R.string.auth_error_empty_fields, Toast.LENGTH_SHORT).show()
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, R.string.auth_error_invalid_email, Toast.LENGTH_SHORT).show()
            return
        }
        if (password.length < 6) {
            Toast.makeText(this, R.string.auth_error_password_short, Toast.LENGTH_SHORT).show()
            return
        }

        val userDao = AppDatabase.getInstance(this).userDao()
        if (userDao.findByEmail(email) != null) {
            Toast.makeText(this, R.string.auth_error_email_taken, Toast.LENGTH_SHORT).show()
            return
        }

        val user = User()
        user.fullName = fullName
        user.email = email
        user.password = password

        try {
            val userId = userDao.insert(user)
            getSharedPreferences(LoginActivity.PREFS_AUTH, MODE_PRIVATE)
                .edit()
                .putLong(LoginActivity.KEY_USER_ID, userId)
                .apply()

            Toast.makeText(this, R.string.auth_success_register, Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        } catch (e: SQLiteConstraintException) {
            Toast.makeText(this, R.string.auth_error_email_taken, Toast.LENGTH_SHORT).show()
        }
    }
}
```

---

## Step 15) Tạo menu `menu_bottom_nav.xml`

**Giải thích:** `BottomNavigationView` đọc danh sách tab từ một file menu. Mỗi `<item>` có `android:id` (Kotlin dùng để biết tab nào được bấm), `android:icon` và `android:title`. Icon dùng sẵn từ `@android:drawable/...` nên không cần tạo file ảnh.

**Tạo thư mục:** chuột phải `res` → **New → Android Resource Directory** → Resource type: `menu`.

**Tạo file mới:** `app/src/main/res/menu/menu_bottom_nav.xml`

**Dán toàn bộ:**

```xml
<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android">
    <item
        android:id="@+id/nav_home"
        android:icon="@android:drawable/ic_menu_view"
        android:title="Home" />
    <item
        android:id="@+id/nav_ask"
        android:icon="@android:drawable/ic_menu_edit"
        android:title="Ask" />
    <item
        android:id="@+id/nav_history"
        android:icon="@android:drawable/ic_menu_recent_history"
        android:title="History" />
    <item
        android:id="@+id/nav_progress"
        android:icon="@android:drawable/ic_menu_sort_by_size"
        android:title="Progress" />
</menu>
```

| id | Tab | Fragment |
|----|-----|----------|
| `nav_home` | Home | `HomeFragment` |
| `nav_ask` | Ask | `AskFragment` |
| `nav_history` | History | `HistoryFragment` |
| `nav_progress` | Progress | `ProgressFragment` |

---

## Step 16) Thay layout `activity_main.xml`

**Giải thích:**
- Root là `ConstraintLayout` có `android:id="@+id/main"`. Id này dùng cho `ViewCompat.setOnApplyWindowInsetsListener` (padding tránh status bar / navigation bar) và về sau (Phase 13) để đổi màu nền theo theme.
- `FragmentContainerView` (`fragment_container`) chiếm toàn bộ phần trên: `layout_height="0dp"` + ràng buộc `Top_toTopOf="parent"` và `Bottom_toTopOf="@id/bottom_nav"`.
- `BottomNavigationView` (`bottom_nav`) dính đáy màn hình, `app:menu="@menu/menu_bottom_nav"`.

**File:** `app/src/main/res/layout/activity_main.xml`
**Xóa hết nội dung cũ, dán toàn bộ:**

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:id="@+id/main"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    tools:context=".MainActivity">

    <androidx.fragment.app.FragmentContainerView
        android:id="@+id/fragment_container"
        android:layout_width="0dp"
        android:layout_height="0dp"
        app:layout_constraintBottom_toTopOf="@id/bottom_nav"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent" />

    <com.google.android.material.bottomnavigation.BottomNavigationView
        android:id="@+id/bottom_nav"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:menu="@menu/menu_bottom_nav" />

</androidx.constraintlayout.widget.ConstraintLayout>
```

---

## Step 17) Tạo 4 layout placeholder

**Giải thích:** Mỗi tab tạm thời chỉ hiện tên tab ở giữa màn hình để kiểm tra điều hướng. Các phase sau thay toàn bộ nội dung file (Ask ở Phase 3–4, Home ở Phase 5, History + Progress ở Phase 7).

**Tạo file mới:** `app/src/main/res/layout/fragment_home.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:text="Home"
        android:textSize="24sp" />

</FrameLayout>
```

**Tạo file mới:** `app/src/main/res/layout/fragment_ask.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:text="Ask"
        android:textSize="24sp" />

</FrameLayout>
```

**Tạo file mới:** `app/src/main/res/layout/fragment_history.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:text="History"
        android:textSize="24sp" />

</FrameLayout>
```

**Tạo file mới:** `app/src/main/res/layout/fragment_progress.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:text="Progress"
        android:textSize="24sp" />

</FrameLayout>
```

---

## Step 18) Tạo 4 Fragment Kotlin

**Giải thích:**
- Mỗi Fragment kế thừa `androidx.fragment.app.Fragment` và override `onCreateView` để inflate layout tương ứng.
- Tham số `container: ViewGroup?` có dấu `?` vì Android có thể truyền `null`; `attachToRoot = false` vì `FragmentManager` tự gắn view vào container.
- Mỗi Fragment nằm trong package riêng (`home`, `ask`, `history`, `progress`) để các phase sau thêm Adapter, helper… vào đúng chỗ.

**Tạo package:** chuột phải `com.aistudy.aistudy` → **New → Package** → `home`, lặp lại cho `ask`, `history`, `progress`.

**Tạo file mới:** `app/src/main/java/com/aistudy/aistudy/home/HomeFragment.kt`

```kotlin
package com.aistudy.aistudy.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.aistudy.aistudy.R

class HomeFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }
}
```

**Tạo file mới:** `app/src/main/java/com/aistudy/aistudy/ask/AskFragment.kt`

```kotlin
package com.aistudy.aistudy.ask

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.aistudy.aistudy.R

class AskFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_ask, container, false)
    }
}
```

**Tạo file mới:** `app/src/main/java/com/aistudy/aistudy/history/HistoryFragment.kt`

```kotlin
package com.aistudy.aistudy.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.aistudy.aistudy.R

class HistoryFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_history, container, false)
    }
}
```

**Tạo file mới:** `app/src/main/java/com/aistudy/aistudy/progress/ProgressFragment.kt`

```kotlin
package com.aistudy.aistudy.progress

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.aistudy.aistudy.R

class ProgressFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_progress, container, false)
    }
}
```

---

## Step 19) Viết `MainActivity.kt`

**Giải thích:**
1. **Kiểm tra session:** đọc `user_id` trong `SharedPreferences` (`LoginActivity.PREFS_AUTH`, `LoginActivity.KEY_USER_ID`). Giá trị `-1L` nghĩa là chưa đăng nhập → mở `LoginActivity`, gọi `finish()` và `return` để không inflate layout.
2. **Edge-to-edge:** `enableEdgeToEdge()` cho nội dung vẽ dưới status bar; listener insets cộng padding bằng kích thước system bars vào view `main`.
3. **BottomNav:** `setOnItemSelectedListener` nhận `item`, dùng `when (item.itemId)` chọn Fragment. Nhánh `else -> return@setOnItemSelectedListener false` thoát khỏi lambda (không thoát khỏi `onCreate`) và báo không xử lý item. Lambda trả `true` ở dòng cuối để BottomNav highlight tab được chọn.
4. **`replace(R.id.fragment_container, fragment)`** thay Fragment đang hiển thị bằng Fragment mới.
5. **`savedInstanceState == null`** chỉ đúng lần đầu mở Activity; khi xoay màn hình `FragmentManager` tự khôi phục Fragment nên không chọn lại tab. Gán `selectedItemId` sẽ kích hoạt listener ở bước 3 → hiển thị Home.

**File:** `app/src/main/java/com/aistudy/aistudy/MainActivity.kt`
**Thay toàn bộ nội dung bằng:**

```kotlin
package com.aistudy.aistudy

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.aistudy.aistudy.ask.AskFragment
import com.aistudy.aistudy.auth.LoginActivity
import com.aistudy.aistudy.history.HistoryFragment
import com.aistudy.aistudy.home.HomeFragment
import com.aistudy.aistudy.progress.ProgressFragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val userId = getSharedPreferences(LoginActivity.PREFS_AUTH, MODE_PRIVATE)
            .getLong(LoginActivity.KEY_USER_ID, -1)
        if (userId == -1L) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val bottomNav: BottomNavigationView = findViewById(R.id.bottom_nav)
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
            bottomNav.selectedItemId = R.id.nav_home
        }
    }
}
```

**Luồng mở app:**

```
App launch → MainActivity
    │
    ├── user_id == -1 ──► LoginActivity ──(login OK)──► MainActivity
    │                          │
    │                          └── "Sign up" ──► RegisterActivity ──(register OK)──► MainActivity
    │
    └── user_id hợp lệ ──► activity_main
                             │
                             └── bottomNav.selectedItemId = nav_home ──► HomeFragment
```

---

## Step 20) `AndroidManifest.xml`

**Giải thích:** `MainActivity` là màn hình khởi động (`MAIN` + `LAUNCHER`); `LoginActivity`, `RegisterActivity` chỉ mở nội bộ nên `android:exported="false"`. Mọi Activity phải khai báo ở đây, thiếu thì mở sẽ crash `ActivityNotFoundException`.

**File:** `app/src/main/AndroidManifest.xml`
**Thay toàn bộ nội dung bằng:**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

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
            android:exported="true">
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
    </application>

</manifest>
```

---

## Step 21) Build & test tay

1. **File → Sync Project with Gradle Files**, sau đó **Build → Rebuild Project**.
2. Gỡ app cũ trên máy ảo (xóa dữ liệu session + database), chạy lại → màn **Sign in** xuất hiện.
3. Bấm **Sign in** khi để trống → Toast `Please fill in all fields.`
4. Bấm **Don't have an account? Sign up** → màn **Create account**.
5. Đăng ký với email sai định dạng → `Please enter a valid email.`; mật khẩu 3 ký tự → `Password must be at least 6 characters.`
6. Đăng ký hợp lệ → Toast `Account created successfully.` → vào `MainActivity`, tab **Home** được chọn, giữa màn hình hiện chữ `Home`.
7. Bấm lần lượt **Ask / History / Progress** → chữ giữa màn hình đổi đúng tên tab, icon tab được highlight.
8. Xoay màn hình ở tab History → vẫn ở History (không nhảy về Home).
9. Đóng app, mở lại → vào thẳng `MainActivity` (session còn).
10. **App Inspection → Database Inspector** → database `aistudy.db` → bảng `users` có dòng vừa đăng ký.
11. Chưa có nút Logout (Phase 8), nên để test đăng nhập: **View → Tool Windows → Device Explorer** → xóa file `/data/data/com.aistudy.aistudy/shared_prefs/auth.xml`, đóng hẳn app rồi mở lại → màn **Sign in**.
12. Đăng nhập sai mật khẩu → `Invalid email or password.`; đúng → vào `MainActivity`.
13. Ở màn Sign in bấm **Sign up**, đăng ký lại email cũ → `This email is already registered.`

### Lỗi thường gặp

| Triệu chứng | Nguyên nhân hay gặp | Cách xử lý |
|-------------|---------------------|------------|
| `Unresolved reference: room` / `material` ở `libs.*` | Chưa sync hoặc sai tên trong `libs.versions.toml` | So Step 1, Sync Gradle |
| `AppDatabase_Impl does not exist` lúc chạy | Thiếu `ksp(libs.room.compiler)` | Kiểm tra Step 2, Rebuild |
| `[ksp] Cannot find setter for field` | Property entity khai báo `val` | Đổi thành `var` có giá trị mặc định |
| Crash `Cannot access database on the main thread` | Thiếu `allowMainThreadQueries()` | Kiểm tra Step 5 |
| Crash `Error inflating class MaterialButton` | Theme không thuộc họ Material | Kiểm tra Step 9 |
| Nút Sign in hiện màu tím mặc định | Thiếu `app:backgroundTint="@null"` | So lại XML Step 11 / 12 |
| `Apostrophe not preceded by \` | Viết `Don't` thay vì `Don\'t` trong strings | Sửa `strings_auth.xml` |
| Crash `ActivityNotFoundException` | Chưa khai báo Activity trong manifest | Kiểm tra Step 20 |
| Đăng nhập báo `Invalid email or password.` dù đúng | Email lưu khác hoa/thường | Cả Login và Register đều `trim().lowercase()` |
| `Unresolved reference: nav_home` | Menu chưa tạo / sai thư mục `res/menu` | Kiểm tra Step 15, Rebuild |
| `Unresolved reference: HomeFragment` | Sai package hoặc thiếu import | So `package` đầu file với thư mục |
| Crash `IllegalArgumentException: No view found for id fragment_container` | Sai id trong `activity_main.xml` | So khớp `@+id/fragment_container` |
| Nội dung bị status bar che | Thiếu listener insets hoặc sai id `main` | Kiểm tra Step 19 mục 2 |
| Bấm tab không đổi nội dung | Lambda trả `false` / quên `.commit()` | Dòng cuối lambda phải là `true` |
| Mở app lặp lại màn Login | `LoginActivity` không lưu `user_id` | Kiểm tra `putLong(KEY_USER_ID, user.id)` + `apply()` |

---

## Checklist hoàn thành Phase 1

- [ ] `libs.versions.toml` + `app/build.gradle.kts` có appcompat, material, activity-ktx, constraintlayout, fragment, room (`ksp(libs.room.compiler)`)
- [ ] `User`, `UserDao`, `AppDatabase` (`version = 1`) build ra `AppDatabase_Impl`
- [ ] `colors.xml`, `dimens.xml`, `strings.xml`, `strings_auth.xml`, 5 drawable Auth
- [ ] `auth_activity_login.xml`, `auth_activity_register.xml`
- [ ] Đăng ký tạo dòng mới trong bảng `users`, email trùng bị chặn
- [ ] Đăng nhập đúng → vào `MainActivity`; sai → Toast lỗi
- [ ] `menu_bottom_nav.xml` có 4 item `nav_home`, `nav_ask`, `nav_history`, `nav_progress`
- [ ] `activity_main.xml` có `main`, `fragment_container`, `bottom_nav`
- [ ] 4 layout placeholder + 4 Fragment Kotlin
- [ ] `MainActivity` chuyển về Login khi chưa có session
- [ ] Chuyển tab đúng Fragment, xoay màn hình giữ tab

**Tiếp theo:** Phase 2 — Profile + Onboarding (`docs/PHASE_2_STEP_BY_STEP_CODE.md`).
