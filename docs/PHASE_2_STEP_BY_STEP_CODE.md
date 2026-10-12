# Phase 2: Profile + Onboarding 3 bước — Hướng dẫn từng bước

Mục tiêu Phase 2: mỗi user có một bản ghi `Profile` trong Room. Lần đầu vào app, user đi qua màn **Onboarding** 3 bước (cấp học → môn học → kiểu giải thích); kết quả lưu vào `Profile`, sau đó mới vào `MainActivity`.

**Điều kiện trước khi làm:** Phase 1 xong (Auth với `User`, `UserDao`, `AppDatabase` version 1, `LoginActivity`, `RegisterActivity`; `MainActivity` + BottomNav + 4 Fragment placeholder).

**Không làm ở Phase này:** hỏi AI, XP thực tế → Phase 3–4. Hai field `xp`, `streak` được tạo sẵn trong `Profile` với giá trị `0`.

---

## Tổng quan các file sẽ chạm

| File | Loại | Việc |
|------|------|------|
| `database/Profile.kt` | **Mới** | Entity bảng `profiles` |
| `database/ProfileDao.kt` | **Mới** | insert / update / getByUserId |
| `database/ProfileDefaults.kt` | **Mới** | Hằng số level/style/subject + tạo profile mặc định |
| `database/AppDatabase.kt` | **Thay toàn bộ** | Thêm `Profile`, `version = 2`, `profileDao()` |
| `auth/LoginActivity.kt` | Sửa | `ProfileDefaults.ensureExists` sau khi login |
| `auth/RegisterActivity.kt` | Sửa | `ProfileDefaults.ensureExists` sau khi register |
| `values/strings_onboarding.xml` | **Mới** | Chuỗi chữ Onboarding |
| `drawable/bg_onboarding_choice.xml` | **Mới** | Nền lựa chọn level/style (chưa chọn) |
| `drawable/bg_onboarding_choice_selected.xml` | **Mới** | Nền lựa chọn level/style (đã chọn) |
| `drawable/bg_onboarding_subject.xml` | **Mới** | Nền ô môn học (chưa chọn) |
| `drawable/bg_onboarding_subject_selected.xml` | **Mới** | Nền ô môn học (đã chọn) |
| `layout/activity_onboarding.xml` | **Mới** | Layout 3 bước |
| `onboarding/OnboardingActivity.kt` | **Mới** | Logic chọn + lưu Profile |
| `AndroidManifest.xml` | Sửa | Khai báo `OnboardingActivity` |
| `MainActivity.kt` | **Thay toàn bộ** | Chưa onboarded → chuyển Onboarding |

Các resource dùng lại từ Auth (không sửa): `colors.xml` (`auth_brand`, `auth_card`, `auth_foreground`, `auth_muted_foreground`, `auth_primary_foreground`, `auth_surface`, `auth_brand_soft`, `auth_brand_ring`, `auth_input_ring`, `white`), `dimens.xml` (`auth_screen_padding_horizontal`, `auth_screen_padding_vertical`, `auth_title_margin_top`, `auth_form_margin_top`, `auth_field_spacing`, `auth_input_height`, `auth_input_radius`), `drawable/bg_auth_button.xml`.

---

## Step 1) Entity `Profile`

**Giải thích:**
- `@Entity(tableName = "profiles")` → Room tạo bảng `profiles`.
- `userId` là `@PrimaryKey` **không** tự tăng: mỗi user đúng một profile, khóa trùng `users.id`.
- Mọi property là `var` có giá trị mặc định để Room tạo object bằng constructor rỗng rồi gán từng cột.
- `String?` cho các cột text vì SQLite cho phép `NULL`.
- `subjects` lưu nhiều môn trong một chuỗi, ngăn cách dấu phẩy: `"math,science"`.

**Tạo file mới:** `app/src/main/java/com/aistudy/aistudy/database/Profile.kt`

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
}
```

| Property | Kiểu | Ví dụ | Ý nghĩa |
|----------|------|-------|---------|
| `userId` | `Long` | `1` | PK, trùng `users.id` |
| `onboarded` | `Boolean` | `false` | `false` = phải làm onboarding |
| `level` | `String?` | `"high"` | `junior`, `high`, `uni` |
| `subjects` | `String?` | `"math,science"` | Danh sách môn, cách nhau dấu phẩy |
| `style` | `String?` | `"stepwise"` | `short`, `stepwise`, `detailed` |
| `xp` | `Int` | `0` | Dùng từ Phase 4 |
| `streak` | `Int` | `0` | Dùng từ Phase 5 |

---

## Step 2) `ProfileDao`

**Giải thích:**
- `OnConflictStrategy.IGNORE`: nếu đã có profile cùng `userId` thì bỏ qua, không ghi đè dữ liệu onboarding.
- `@Update` cập nhật theo khóa chính `userId`.
- `getByUserId` trả `Profile?` vì user chưa có profile thì Room trả `null`.

**Tạo file mới:** `app/src/main/java/com/aistudy/aistudy/database/ProfileDao.kt`

```kotlin
package com.aistudy.aistudy.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface ProfileDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(profile: Profile): Long

    @Update
    fun update(profile: Profile)

    @Query("SELECT * FROM profiles WHERE userId = :userId LIMIT 1")
    fun getByUserId(userId: Long): Profile?
}
```

---

## Step 3) `ProfileDefaults`

**Giải thích:**
- `object` = singleton Kotlin, gọi trực tiếp `ProfileDefaults.createDefault(...)` không cần tạo instance.
- `const val` là hằng số biên dịch; dùng các hằng này thay vì gõ chuỗi `"high"`, `"math"` rải rác trong code.
- `createDefault` tạo profile mặc định: `onboarded = false`, cấp `high`, môn `math,science`, kiểu `stepwise`.
- `ensureExists` chỉ insert khi chưa có profile → gọi nhiều lần vẫn an toàn.

**Tạo file mới:** `app/src/main/java/com/aistudy/aistudy/database/ProfileDefaults.kt`

```kotlin
package com.aistudy.aistudy.database

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
        return profile
    }

    fun ensureExists(profileDao: ProfileDao, userId: Long) {
        if (profileDao.getByUserId(userId) == null) {
            profileDao.insert(createDefault(userId))
        }
    }
}
```

| Hằng số | Giá trị | Chữ hiển thị (strings) |
|---------|---------|------------------------|
| `LEVEL_JUNIOR` | `junior` | Middle school |
| `LEVEL_HIGH` | `high` | High school |
| `LEVEL_UNI` | `uni` | University |
| `STYLE_SHORT` | `short` | Concise |
| `STYLE_STEPWISE` | `stepwise` | Step by step |
| `STYLE_DETAILED` | `detailed` | Detailed |
| `SUBJECT_MATH` | `math` | Math |
| `SUBJECT_SCIENCE` | `science` | Science |
| `SUBJECT_CODE` | `code` | Programming |
| `SUBJECT_HISTORY` | `history` | History |
| `SUBJECT_LANGUAGE` | `language` | Language |
| `SUBJECT_OTHER` | `other` | Other |

---

## Step 4) `AppDatabase` version 2

**Giải thích:**
- Thêm `Profile::class` vào `entities` và hàm `profileDao()`.
- Schema thay đổi nên tăng `version` từ `1` lên `2`.
- `fallbackToDestructiveMigration(true)`: khi version tăng mà không có migration, Room xóa và tạo lại toàn bộ bảng (chấp nhận được ở giai đoạn phát triển; tài khoản cũ bị xóa, cần đăng ký lại).
- `companion object` + `@Volatile` + `synchronized` đảm bảo chỉ tạo một instance database cho toàn app.

**File:** `app/src/main/java/com/aistudy/aistudy/database/AppDatabase.kt`
**Thay toàn bộ nội dung bằng:**

```kotlin
package com.aistudy.aistudy.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        User::class,
        Profile::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao

    abstract fun profileDao(): ProfileDao

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

| Thay đổi | Trước | Sau |
|----------|-------|-----|
| `entities` | `[User::class]` | `[User::class, Profile::class]` |
| `version` | `1` | `2` |
| DAO | `userDao()` | thêm `profileDao()` |

---

## Step 5) `LoginActivity` — tạo profile sau khi login

**Giải thích:** Tài khoản tạo trước Phase 2 chưa có profile. Gọi `ensureExists` ngay sau khi lưu session để mọi user đăng nhập đều có profile.

**File:** `app/src/main/java/com/aistudy/aistudy/auth/LoginActivity.kt`

**Thêm import (theo thứ tự alphabet, sau `import com.aistudy.aistudy.database.AppDatabase`):**

```kotlin
import com.aistudy.aistudy.database.ProfileDefaults
```

**Tìm:**

```kotlin
        getSharedPreferences(PREFS_AUTH, MODE_PRIVATE)
            .edit()
            .putLong(KEY_USER_ID, user.id)
            .apply()

        startActivity(Intent(this, MainActivity::class.java))
```

**Đổi thành:**

```kotlin
        getSharedPreferences(PREFS_AUTH, MODE_PRIVATE)
            .edit()
            .putLong(KEY_USER_ID, user.id)
            .apply()

        ProfileDefaults.ensureExists(AppDatabase.getInstance(this).profileDao(), user.id)

        startActivity(Intent(this, MainActivity::class.java))
```

**File `LoginActivity.kt` đầy đủ sau Phase 2:**

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
import com.aistudy.aistudy.database.ProfileDefaults

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

        ProfileDefaults.ensureExists(AppDatabase.getInstance(this).profileDao(), user.id)

        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
```

---

## Step 6) `RegisterActivity` — tạo profile sau khi register

**File:** `app/src/main/java/com/aistudy/aistudy/auth/RegisterActivity.kt`

**Thêm import (sau `import com.aistudy.aistudy.database.AppDatabase`):**

```kotlin
import com.aistudy.aistudy.database.ProfileDefaults
```

**Tìm:**

```kotlin
                .putLong(LoginActivity.KEY_USER_ID, userId)
                .apply()

            Toast.makeText(this, R.string.auth_success_register, Toast.LENGTH_SHORT).show()
```

**Đổi thành:**

```kotlin
                .putLong(LoginActivity.KEY_USER_ID, userId)
                .apply()

            ProfileDefaults.ensureExists(AppDatabase.getInstance(this).profileDao(), userId)

            Toast.makeText(this, R.string.auth_success_register, Toast.LENGTH_SHORT).show()
```

**File `RegisterActivity.kt` đầy đủ sau Phase 2:**

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
import com.aistudy.aistudy.database.ProfileDefaults
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

            ProfileDefaults.ensureExists(AppDatabase.getInstance(this).profileDao(), userId)

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

## Step 7) Chuỗi `strings_onboarding.xml`

**Tạo file mới:** `app/src/main/res/values/strings_onboarding.xml`

**Dán toàn bộ:**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="onboarding_step_level_title">What level are you studying at?</string>
    <string name="onboarding_step_level_subtitle">We will adjust answer difficulty for you.</string>

    <string name="onboarding_step_subjects_title">Subjects you care about</string>
    <string name="onboarding_step_subjects_subtitle">Pick at least one — you can change later.</string>

    <string name="onboarding_step_style_title">How do you like explanations?</string>
    <string name="onboarding_step_style_subtitle">AI will answer in the style you choose.</string>

    <string name="onboarding_level_junior">Middle school</string>
    <string name="onboarding_level_junior_hint">Grades 6 – 9</string>
    <string name="onboarding_level_high">High school</string>
    <string name="onboarding_level_high_hint">Grades 10 – 12</string>
    <string name="onboarding_level_uni">University</string>
    <string name="onboarding_level_uni_hint">College student</string>

    <string name="onboarding_subject_math">Math</string>
    <string name="onboarding_subject_science">Science</string>
    <string name="onboarding_subject_code">Programming</string>
    <string name="onboarding_subject_history">History</string>
    <string name="onboarding_subject_language">Language</string>
    <string name="onboarding_subject_other">Other</string>

    <string name="onboarding_style_short">Concise</string>
    <string name="onboarding_style_short_hint">Quick answer, fewer words</string>
    <string name="onboarding_style_stepwise">Step by step</string>
    <string name="onboarding_style_stepwise_hint">Clear steps, one idea each</string>
    <string name="onboarding_style_detailed">Detailed</string>
    <string name="onboarding_style_detailed_hint">In-depth with examples</string>

    <string name="onboarding_back">Back</string>
    <string name="onboarding_continue">Continue</string>
    <string name="onboarding_finish">Start learning</string>
    <string name="onboarding_pick_subject">Pick at least one subject.</string>
</resources>
```

---

## Step 8) 4 drawable cho lựa chọn

**Giải thích:**
- Lựa chọn **level / style** dùng `shape` thường: chưa chọn nền `auth_card` viền `auth_input_ring`; đã chọn nền `auth_brand_soft` viền `auth_brand_ring`.
- Ô **môn học** dùng `ripple` để có hiệu ứng gợn sóng khi bấm; item `@android:id/mask` giới hạn vùng ripple theo góc bo.
- Kotlin đổi nền bằng `setBackgroundResource(...)` mỗi khi trạng thái chọn thay đổi.

**Tạo file mới:** `app/src/main/res/drawable/bg_onboarding_choice.xml`

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

**Tạo file mới:** `app/src/main/res/drawable/bg_onboarding_choice_selected.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="@color/auth_brand_soft" />
    <corners android:radius="@dimen/auth_input_radius" />
    <stroke
        android:width="1dp"
        android:color="@color/auth_brand_ring" />
</shape>
```

**Tạo file mới:** `app/src/main/res/drawable/bg_onboarding_subject.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<ripple xmlns:android="http://schemas.android.com/apk/res/android"
    android:color="#1A000000">
    <item android:id="@android:id/mask">
        <shape android:shape="rectangle">
            <solid android:color="@color/white" />
            <corners android:radius="@dimen/auth_input_radius" />
        </shape>
    </item>
    <item>
        <shape android:shape="rectangle">
            <solid android:color="@color/auth_card" />
            <corners android:radius="@dimen/auth_input_radius" />
            <stroke
                android:width="1dp"
                android:color="@color/auth_input_ring" />
        </shape>
    </item>
</ripple>
```

**Tạo file mới:** `app/src/main/res/drawable/bg_onboarding_subject_selected.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<ripple xmlns:android="http://schemas.android.com/apk/res/android"
    android:color="#1A000000">
    <item android:id="@android:id/mask">
        <shape android:shape="rectangle">
            <solid android:color="@color/white" />
            <corners android:radius="@dimen/auth_input_radius" />
        </shape>
    </item>
    <item>
        <shape android:shape="rectangle">
            <solid android:color="@color/auth_brand_soft" />
            <corners android:radius="@dimen/auth_input_radius" />
            <stroke
                android:width="1dp"
                android:color="@color/auth_brand_ring" />
        </shape>
    </item>
</ripple>
```

Nút **Back / Continue** dùng lại `bg_auth_button.xml` có sẵn từ Auth:

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

---

## Step 9) Layout `activity_onboarding.xml`

**Giải thích cấu trúc:**
- `ScrollView` → `LinearLayout` dọc.
- Thanh tiến trình 3 đoạn: `progress_0`, `progress_1`, `progress_2` (View cao 4dp); Kotlin tô màu `auth_brand` cho các bước đã qua.
- `tv_step_title`, `tv_step_subtitle`: tiêu đề + mô tả đổi theo bước.
- 3 khối nội dung, mỗi lần chỉ một khối `VISIBLE`:
  - `step_level`: 3 lựa chọn `choice_level_junior`, `choice_level_high`, `choice_level_uni`.
  - `step_subjects`: 6 ô môn học trong 3 hàng × 2 cột (`choice_subject_*`).
  - `step_style`: 3 lựa chọn `choice_style_short`, `choice_style_stepwise`, `choice_style_detailed`.
- Hàng nút dưới cùng: `btn_back` (ẩn ở bước 1) + `btn_next`.

**Tạo file mới:** `app/src/main/res/layout/activity_onboarding.xml`

**Dán toàn bộ:**

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
        android:paddingTop="@dimen/auth_screen_padding_vertical"
        android:paddingBottom="@dimen/auth_screen_padding_vertical">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="32dp"
            android:orientation="horizontal">

            <View
                android:id="@+id/progress_0"
                android:layout_width="0dp"
                android:layout_height="4dp"
                android:layout_marginEnd="6dp"
                android:layout_weight="1"
                android:background="@color/auth_brand" />

            <View
                android:id="@+id/progress_1"
                android:layout_width="0dp"
                android:layout_height="4dp"
                android:layout_marginEnd="6dp"
                android:layout_weight="1"
                android:background="@color/auth_card" />

            <View
                android:id="@+id/progress_2"
                android:layout_width="0dp"
                android:layout_height="4dp"
                android:layout_weight="1"
                android:background="@color/auth_card" />
        </LinearLayout>

        <TextView
            android:id="@+id/tv_step_title"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="@string/onboarding_step_level_title"
            android:textColor="@color/auth_foreground"
            android:textSize="24sp"
            android:textStyle="bold" />

        <TextView
            android:id="@+id/tv_step_subtitle"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="@dimen/auth_title_margin_top"
            android:text="@string/onboarding_step_level_subtitle"
            android:textColor="@color/auth_muted_foreground"
            android:textSize="14sp" />

        <LinearLayout
            android:id="@+id/step_level"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="@dimen/auth_form_margin_top"
            android:orientation="vertical">

            <LinearLayout
                android:id="@+id/choice_level_junior"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginBottom="@dimen/auth_field_spacing"
                android:background="@drawable/bg_onboarding_choice"
                android:clickable="true"
                android:focusable="true"
                android:orientation="vertical"
                android:padding="16dp">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/onboarding_level_junior"
                    android:textColor="@color/auth_foreground"
                    android:textSize="14sp"
                    android:textStyle="bold" />

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="4dp"
                    android:text="@string/onboarding_level_junior_hint"
                    android:textColor="@color/auth_muted_foreground"
                    android:textSize="12sp" />
            </LinearLayout>

            <LinearLayout
                android:id="@+id/choice_level_high"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginBottom="@dimen/auth_field_spacing"
                android:background="@drawable/bg_onboarding_choice_selected"
                android:clickable="true"
                android:focusable="true"
                android:orientation="vertical"
                android:padding="16dp">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/onboarding_level_high"
                    android:textColor="@color/auth_foreground"
                    android:textSize="14sp"
                    android:textStyle="bold" />

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="4dp"
                    android:text="@string/onboarding_level_high_hint"
                    android:textColor="@color/auth_muted_foreground"
                    android:textSize="12sp" />
            </LinearLayout>

            <LinearLayout
                android:id="@+id/choice_level_uni"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:background="@drawable/bg_onboarding_choice"
                android:clickable="true"
                android:focusable="true"
                android:orientation="vertical"
                android:padding="16dp">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/onboarding_level_uni"
                    android:textColor="@color/auth_foreground"
                    android:textSize="14sp"
                    android:textStyle="bold" />

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="4dp"
                    android:text="@string/onboarding_level_uni_hint"
                    android:textColor="@color/auth_muted_foreground"
                    android:textSize="12sp" />
            </LinearLayout>
        </LinearLayout>

        <LinearLayout
            android:id="@+id/step_subjects"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="@dimen/auth_form_margin_top"
            android:orientation="vertical"
            android:visibility="gone">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal">

                <TextView
                    android:id="@+id/choice_subject_math"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_marginEnd="8dp"
                    android:layout_marginBottom="8dp"
                    android:layout_weight="1"
                    android:background="@drawable/bg_onboarding_subject_selected"
                    android:clickable="true"
                    android:focusable="true"
                    android:gravity="center"
                    android:padding="16dp"
                    android:text="@string/onboarding_subject_math"
                    android:textColor="@color/auth_foreground"
                    android:textSize="14sp"
                    android:textStyle="bold" />

                <TextView
                    android:id="@+id/choice_subject_science"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_marginBottom="8dp"
                    android:layout_weight="1"
                    android:background="@drawable/bg_onboarding_subject_selected"
                    android:clickable="true"
                    android:focusable="true"
                    android:gravity="center"
                    android:padding="16dp"
                    android:text="@string/onboarding_subject_science"
                    android:textColor="@color/auth_foreground"
                    android:textSize="14sp"
                    android:textStyle="bold" />
            </LinearLayout>

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal">

                <TextView
                    android:id="@+id/choice_subject_code"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_marginEnd="8dp"
                    android:layout_marginBottom="8dp"
                    android:layout_weight="1"
                    android:background="@drawable/bg_onboarding_subject"
                    android:clickable="true"
                    android:focusable="true"
                    android:gravity="center"
                    android:padding="16dp"
                    android:text="@string/onboarding_subject_code"
                    android:textColor="@color/auth_foreground"
                    android:textSize="14sp"
                    android:textStyle="bold" />

                <TextView
                    android:id="@+id/choice_subject_history"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_marginBottom="8dp"
                    android:layout_weight="1"
                    android:background="@drawable/bg_onboarding_subject"
                    android:clickable="true"
                    android:focusable="true"
                    android:gravity="center"
                    android:padding="16dp"
                    android:text="@string/onboarding_subject_history"
                    android:textColor="@color/auth_foreground"
                    android:textSize="14sp"
                    android:textStyle="bold" />
            </LinearLayout>

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal">

                <TextView
                    android:id="@+id/choice_subject_language"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_marginEnd="8dp"
                    android:layout_weight="1"
                    android:background="@drawable/bg_onboarding_subject"
                    android:clickable="true"
                    android:focusable="true"
                    android:gravity="center"
                    android:padding="16dp"
                    android:text="@string/onboarding_subject_language"
                    android:textColor="@color/auth_foreground"
                    android:textSize="14sp"
                    android:textStyle="bold" />

                <TextView
                    android:id="@+id/choice_subject_other"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:background="@drawable/bg_onboarding_subject"
                    android:clickable="true"
                    android:focusable="true"
                    android:gravity="center"
                    android:padding="16dp"
                    android:text="@string/onboarding_subject_other"
                    android:textColor="@color/auth_foreground"
                    android:textSize="14sp"
                    android:textStyle="bold" />
            </LinearLayout>
        </LinearLayout>

        <LinearLayout
            android:id="@+id/step_style"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="@dimen/auth_form_margin_top"
            android:orientation="vertical"
            android:visibility="gone">

            <LinearLayout
                android:id="@+id/choice_style_short"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginBottom="@dimen/auth_field_spacing"
                android:background="@drawable/bg_onboarding_choice"
                android:clickable="true"
                android:focusable="true"
                android:orientation="vertical"
                android:padding="16dp">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/onboarding_style_short"
                    android:textColor="@color/auth_foreground"
                    android:textSize="14sp"
                    android:textStyle="bold" />

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="4dp"
                    android:text="@string/onboarding_style_short_hint"
                    android:textColor="@color/auth_muted_foreground"
                    android:textSize="12sp" />
            </LinearLayout>

            <LinearLayout
                android:id="@+id/choice_style_stepwise"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginBottom="@dimen/auth_field_spacing"
                android:background="@drawable/bg_onboarding_choice_selected"
                android:clickable="true"
                android:focusable="true"
                android:orientation="vertical"
                android:padding="16dp">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/onboarding_style_stepwise"
                    android:textColor="@color/auth_foreground"
                    android:textSize="14sp"
                    android:textStyle="bold" />

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="4dp"
                    android:text="@string/onboarding_style_stepwise_hint"
                    android:textColor="@color/auth_muted_foreground"
                    android:textSize="12sp" />
            </LinearLayout>

            <LinearLayout
                android:id="@+id/choice_style_detailed"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:background="@drawable/bg_onboarding_choice"
                android:clickable="true"
                android:focusable="true"
                android:orientation="vertical"
                android:padding="16dp">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/onboarding_style_detailed"
                    android:textColor="@color/auth_foreground"
                    android:textSize="14sp"
                    android:textStyle="bold" />

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="4dp"
                    android:text="@string/onboarding_style_detailed_hint"
                    android:textColor="@color/auth_muted_foreground"
                    android:textSize="12sp" />
            </LinearLayout>
        </LinearLayout>

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="32dp"
            android:orientation="horizontal">

            <com.google.android.material.button.MaterialButton
                android:id="@+id/btn_back"
                android:layout_width="wrap_content"
                android:layout_height="@dimen/auth_input_height"
                android:layout_marginEnd="12dp"
                android:backgroundTint="@color/auth_card"
                android:insetTop="0dp"
                android:insetBottom="0dp"
                android:text="@string/onboarding_back"
                android:textAllCaps="false"
                android:textColor="@color/auth_foreground"
                android:visibility="gone"
                app:cornerRadius="@dimen/auth_input_radius" />

            <com.google.android.material.button.MaterialButton
                android:id="@+id/btn_next"
                android:layout_width="0dp"
                android:layout_height="@dimen/auth_input_height"
                android:layout_weight="1"
                android:background="@drawable/bg_auth_button"
                android:insetTop="0dp"
                android:insetBottom="0dp"
                android:text="@string/onboarding_continue"
                android:textAllCaps="false"
                android:textColor="@color/auth_primary_foreground"
                app:backgroundTint="@null"
                app:cornerRadius="@dimen/auth_input_radius" />
        </LinearLayout>
    </LinearLayout>
</ScrollView>
```

### Bảng ánh xạ id XML ↔ property Kotlin

| id | Widget | Property Kotlin |
|----|--------|-----------------|
| `progress_0` / `progress_1` / `progress_2` | View | `progress0` / `progress1` / `progress2` |
| `tv_step_title` | TextView | `tvStepTitle` |
| `tv_step_subtitle` | TextView | `tvStepSubtitle` |
| `step_level` / `step_subjects` / `step_style` | LinearLayout | `stepLevel` / `stepSubjects` / `stepStyle` |
| `choice_level_junior` / `_high` / `_uni` | LinearLayout | `choiceLevelJunior` / `choiceLevelHigh` / `choiceLevelUni` |
| `choice_subject_math` … `choice_subject_other` | TextView | `choiceSubjectMath` … `choiceSubjectOther` |
| `choice_style_short` / `_stepwise` / `_detailed` | LinearLayout | `choiceStyleShort` / `choiceStyleStepwise` / `choiceStyleDetailed` |
| `btn_back` / `btn_next` | MaterialButton | `btnBack` / `btnNext` |

---

## Step 10) `OnboardingActivity`

**Giải thích:**
1. **`companion object`** chứa hằng số bước `STEP_LEVEL = 0`, `STEP_SUBJECTS = 1`, `STEP_STYLE = 2` và hàm `joinSubjects` (nối `Set<String>` thành `"math,science"` bằng `joinToString(",")`).
2. **`lateinit var`** cho view và `profileDao`, `profile`: khai báo trước, gán trong `onCreate` / `bindViews()`. Truy cập trước khi gán sẽ ném `UninitializedPropertyAccessException`.
3. **`onCreate`:**
   - Lấy `userId` từ `SharedPreferences`; không có session → `finish()`.
   - `ensureExists` rồi đọc profile; `!!` khẳng định không null vì vừa đảm bảo tồn tại.
   - Profile đã `onboarded` → vào thẳng Main.
   - Nạp lựa chọn cũ từ profile: `profile.subjects.orEmpty().split(",")` (chuỗi `null` thành rỗng).
   - `bindViews()` → gắn listener cho 3 nhóm lựa chọn → `showStep(STEP_LEVEL)`.
4. **Chọn level/style:** lưu vào `selectedLevel` / `selectedStyle` rồi `refresh...Ui()` tô lại nền cho cả nhóm (chỉ một lựa chọn sáng).
5. **Chọn môn:** `toggleSubject` thêm/bỏ khỏi `selectedSubjects` (`HashSet`), cho phép chọn nhiều môn; `subject in selectedSubjects` là cách viết Kotlin của `contains`.
6. **`showStep`:** ẩn/hiện 3 khối theo bước, tô màu thanh tiến trình, ẩn `btnBack` ở bước đầu, đổi tiêu đề và chữ nút (`Continue` / `Start learning`) bằng `when`.
7. **`onNext`:** bước môn học mà chưa chọn môn nào → Toast; chưa tới bước cuối → sang bước kế; bước cuối → ghi `level`, `style`, `subjects`, `onboarded = true` vào profile, `profileDao.update(profile)`, rồi `goToMain()`.
8. **`goToMain`:** `FLAG_ACTIVITY_CLEAR_TOP or FLAG_ACTIVITY_SINGLE_TOP` (ghép cờ bằng toán tử bit `or`) rồi `finish()` để nút Back không quay lại Onboarding.

**Tạo package:** `com.aistudy.aistudy.onboarding`

**Tạo file mới:** `app/src/main/java/com/aistudy/aistudy/onboarding/OnboardingActivity.kt`

```kotlin
package com.aistudy.aistudy.onboarding

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.aistudy.aistudy.MainActivity
import com.aistudy.aistudy.R
import com.aistudy.aistudy.auth.LoginActivity
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.Profile
import com.aistudy.aistudy.database.ProfileDao
import com.aistudy.aistudy.database.ProfileDefaults
import com.google.android.material.button.MaterialButton

class OnboardingActivity : AppCompatActivity() {

    companion object {
        private const val STEP_LEVEL = 0
        private const val STEP_SUBJECTS = 1
        private const val STEP_STYLE = 2

        private fun joinSubjects(subjects: Set<String>): String {
            return subjects.joinToString(",")
        }
    }

    private lateinit var profileDao: ProfileDao
    private var userId: Long = 0
    private lateinit var profile: Profile
    private var currentStep = STEP_LEVEL

    private var selectedLevel: String? = ProfileDefaults.LEVEL_HIGH
    private var selectedStyle: String? = ProfileDefaults.STYLE_STEPWISE
    private val selectedSubjects = hashSetOf(
        ProfileDefaults.SUBJECT_MATH,
        ProfileDefaults.SUBJECT_SCIENCE
    )

    private lateinit var progress0: View
    private lateinit var progress1: View
    private lateinit var progress2: View
    private lateinit var tvStepTitle: TextView
    private lateinit var tvStepSubtitle: TextView
    private lateinit var stepLevel: LinearLayout
    private lateinit var stepSubjects: LinearLayout
    private lateinit var stepStyle: LinearLayout
    private lateinit var btnBack: MaterialButton
    private lateinit var btnNext: MaterialButton

    private lateinit var choiceLevelJunior: LinearLayout
    private lateinit var choiceLevelHigh: LinearLayout
    private lateinit var choiceLevelUni: LinearLayout

    private lateinit var choiceSubjectMath: TextView
    private lateinit var choiceSubjectScience: TextView
    private lateinit var choiceSubjectCode: TextView
    private lateinit var choiceSubjectHistory: TextView
    private lateinit var choiceSubjectLanguage: TextView
    private lateinit var choiceSubjectOther: TextView

    private lateinit var choiceStyleShort: LinearLayout
    private lateinit var choiceStyleStepwise: LinearLayout
    private lateinit var choiceStyleDetailed: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_onboarding)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        userId = getSharedPreferences(LoginActivity.PREFS_AUTH, MODE_PRIVATE)
            .getLong(LoginActivity.KEY_USER_ID, -1)
        if (userId == -1L) {
            finish()
            return
        }

        profileDao = AppDatabase.getInstance(this).profileDao()
        ProfileDefaults.ensureExists(profileDao, userId)
        profile = profileDao.getByUserId(userId)!!

        if (profile.onboarded) {
            goToMain()
            return
        }

        selectedLevel = profile.level
        selectedStyle = profile.style
        selectedSubjects.clear()
        for (subject in profile.subjects.orEmpty().split(",")) {
            if (subject.isNotEmpty()) {
                selectedSubjects.add(subject)
            }
        }

        bindViews()
        setupLevelChoices()
        setupSubjectChoices()
        setupStyleChoices()
        showStep(STEP_LEVEL)

        btnBack.setOnClickListener {
            if (currentStep > STEP_LEVEL) {
                showStep(currentStep - 1)
            }
        }

        btnNext.setOnClickListener { onNext() }
    }

    private fun bindViews() {
        progress0 = findViewById(R.id.progress_0)
        progress1 = findViewById(R.id.progress_1)
        progress2 = findViewById(R.id.progress_2)
        tvStepTitle = findViewById(R.id.tv_step_title)
        tvStepSubtitle = findViewById(R.id.tv_step_subtitle)
        stepLevel = findViewById(R.id.step_level)
        stepSubjects = findViewById(R.id.step_subjects)
        stepStyle = findViewById(R.id.step_style)
        btnBack = findViewById(R.id.btn_back)
        btnNext = findViewById(R.id.btn_next)

        choiceLevelJunior = findViewById(R.id.choice_level_junior)
        choiceLevelHigh = findViewById(R.id.choice_level_high)
        choiceLevelUni = findViewById(R.id.choice_level_uni)

        choiceSubjectMath = findViewById(R.id.choice_subject_math)
        choiceSubjectScience = findViewById(R.id.choice_subject_science)
        choiceSubjectCode = findViewById(R.id.choice_subject_code)
        choiceSubjectHistory = findViewById(R.id.choice_subject_history)
        choiceSubjectLanguage = findViewById(R.id.choice_subject_language)
        choiceSubjectOther = findViewById(R.id.choice_subject_other)

        choiceStyleShort = findViewById(R.id.choice_style_short)
        choiceStyleStepwise = findViewById(R.id.choice_style_stepwise)
        choiceStyleDetailed = findViewById(R.id.choice_style_detailed)
    }

    private fun setupLevelChoices() {
        choiceLevelJunior.setOnClickListener { selectLevel(ProfileDefaults.LEVEL_JUNIOR) }
        choiceLevelHigh.setOnClickListener { selectLevel(ProfileDefaults.LEVEL_HIGH) }
        choiceLevelUni.setOnClickListener { selectLevel(ProfileDefaults.LEVEL_UNI) }
        refreshLevelUi()
    }

    private fun selectLevel(level: String) {
        selectedLevel = level
        refreshLevelUi()
    }

    private fun refreshLevelUi() {
        setChoiceSelected(choiceLevelJunior, selectedLevel == ProfileDefaults.LEVEL_JUNIOR)
        setChoiceSelected(choiceLevelHigh, selectedLevel == ProfileDefaults.LEVEL_HIGH)
        setChoiceSelected(choiceLevelUni, selectedLevel == ProfileDefaults.LEVEL_UNI)
    }

    private fun setupSubjectChoices() {
        choiceSubjectMath.setOnClickListener { toggleSubject(ProfileDefaults.SUBJECT_MATH, choiceSubjectMath) }
        choiceSubjectScience.setOnClickListener { toggleSubject(ProfileDefaults.SUBJECT_SCIENCE, choiceSubjectScience) }
        choiceSubjectCode.setOnClickListener { toggleSubject(ProfileDefaults.SUBJECT_CODE, choiceSubjectCode) }
        choiceSubjectHistory.setOnClickListener { toggleSubject(ProfileDefaults.SUBJECT_HISTORY, choiceSubjectHistory) }
        choiceSubjectLanguage.setOnClickListener { toggleSubject(ProfileDefaults.SUBJECT_LANGUAGE, choiceSubjectLanguage) }
        choiceSubjectOther.setOnClickListener { toggleSubject(ProfileDefaults.SUBJECT_OTHER, choiceSubjectOther) }
        refreshSubjectUi()
    }

    private fun toggleSubject(subject: String, view: TextView) {
        if (subject in selectedSubjects) {
            selectedSubjects.remove(subject)
        } else {
            selectedSubjects.add(subject)
        }
        refreshSubjectUi()
    }

    private fun refreshSubjectUi() {
        setSubjectSelected(choiceSubjectMath, ProfileDefaults.SUBJECT_MATH in selectedSubjects)
        setSubjectSelected(choiceSubjectScience, ProfileDefaults.SUBJECT_SCIENCE in selectedSubjects)
        setSubjectSelected(choiceSubjectCode, ProfileDefaults.SUBJECT_CODE in selectedSubjects)
        setSubjectSelected(choiceSubjectHistory, ProfileDefaults.SUBJECT_HISTORY in selectedSubjects)
        setSubjectSelected(choiceSubjectLanguage, ProfileDefaults.SUBJECT_LANGUAGE in selectedSubjects)
        setSubjectSelected(choiceSubjectOther, ProfileDefaults.SUBJECT_OTHER in selectedSubjects)
    }

    private fun setupStyleChoices() {
        choiceStyleShort.setOnClickListener { selectStyle(ProfileDefaults.STYLE_SHORT) }
        choiceStyleStepwise.setOnClickListener { selectStyle(ProfileDefaults.STYLE_STEPWISE) }
        choiceStyleDetailed.setOnClickListener { selectStyle(ProfileDefaults.STYLE_DETAILED) }
        refreshStyleUi()
    }

    private fun selectStyle(style: String) {
        selectedStyle = style
        refreshStyleUi()
    }

    private fun refreshStyleUi() {
        setChoiceSelected(choiceStyleShort, selectedStyle == ProfileDefaults.STYLE_SHORT)
        setChoiceSelected(choiceStyleStepwise, selectedStyle == ProfileDefaults.STYLE_STEPWISE)
        setChoiceSelected(choiceStyleDetailed, selectedStyle == ProfileDefaults.STYLE_DETAILED)
    }

    private fun setChoiceSelected(view: LinearLayout, selected: Boolean) {
        view.setBackgroundResource(
            if (selected) R.drawable.bg_onboarding_choice_selected
            else R.drawable.bg_onboarding_choice
        )
    }

    private fun setSubjectSelected(view: TextView, selected: Boolean) {
        view.setBackgroundResource(
            if (selected) R.drawable.bg_onboarding_subject_selected
            else R.drawable.bg_onboarding_subject
        )
    }

    private fun showStep(step: Int) {
        currentStep = step

        stepLevel.visibility = if (step == STEP_LEVEL) View.VISIBLE else View.GONE
        stepSubjects.visibility = if (step == STEP_SUBJECTS) View.VISIBLE else View.GONE
        stepStyle.visibility = if (step == STEP_STYLE) View.VISIBLE else View.GONE

        val brand = ContextCompat.getColor(this, R.color.auth_brand)
        val muted = ContextCompat.getColor(this, R.color.auth_card)

        progress0.setBackgroundColor(if (step >= STEP_LEVEL) brand else muted)
        progress1.setBackgroundColor(if (step >= STEP_SUBJECTS) brand else muted)
        progress2.setBackgroundColor(if (step >= STEP_STYLE) brand else muted)

        btnBack.visibility = if (step > STEP_LEVEL) View.VISIBLE else View.GONE

        when (step) {
            STEP_LEVEL -> {
                tvStepTitle.setText(R.string.onboarding_step_level_title)
                tvStepSubtitle.setText(R.string.onboarding_step_level_subtitle)
                btnNext.setText(R.string.onboarding_continue)
            }
            STEP_SUBJECTS -> {
                tvStepTitle.setText(R.string.onboarding_step_subjects_title)
                tvStepSubtitle.setText(R.string.onboarding_step_subjects_subtitle)
                btnNext.setText(R.string.onboarding_continue)
            }
            else -> {
                tvStepTitle.setText(R.string.onboarding_step_style_title)
                tvStepSubtitle.setText(R.string.onboarding_step_style_subtitle)
                btnNext.setText(R.string.onboarding_finish)
            }
        }
    }

    private fun onNext() {
        if (currentStep == STEP_SUBJECTS && selectedSubjects.isEmpty()) {
            Toast.makeText(this, R.string.onboarding_pick_subject, Toast.LENGTH_SHORT).show()
            return
        }

        if (currentStep < STEP_STYLE) {
            showStep(currentStep + 1)
            return
        }

        profile.level = selectedLevel
        profile.style = selectedStyle
        profile.subjects = joinSubjects(selectedSubjects)
        profile.onboarded = true
        profileDao.update(profile)

        goToMain()
    }

    private fun goToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finish()
    }
}
```

### Danh sách hàm

| Hàm | Việc |
|-----|------|
| `onCreate` | Kiểm tra session/profile, nạp lựa chọn cũ, khởi tạo UI |
| `bindViews` | `findViewById` toàn bộ view |
| `setupLevelChoices` / `selectLevel` / `refreshLevelUi` | Nhóm cấp học (chọn 1) |
| `setupSubjectChoices` / `toggleSubject` / `refreshSubjectUi` | Nhóm môn học (chọn nhiều) |
| `setupStyleChoices` / `selectStyle` / `refreshStyleUi` | Nhóm kiểu giải thích (chọn 1) |
| `setChoiceSelected` / `setSubjectSelected` | Đổi drawable nền theo trạng thái |
| `showStep` | Hiển thị bước, thanh tiến trình, chữ nút |
| `onNext` | Validate, chuyển bước, lưu profile |
| `goToMain` | Mở `MainActivity`, đóng Onboarding |

---

## Step 11) Khai báo `OnboardingActivity` trong Manifest

**File:** `app/src/main/AndroidManifest.xml`

**Tìm:**

```xml
        <activity
            android:name=".auth.RegisterActivity"
            android:exported="false" />
    </application>
```

**Đổi thành:**

```xml
        <activity
            android:name=".auth.RegisterActivity"
            android:exported="false" />
        <activity
            android:name=".onboarding.OnboardingActivity"
            android:exported="false" />
    </application>
```

**Manifest đầy đủ sau Phase 2:**

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
        <activity
            android:name=".onboarding.OnboardingActivity"
            android:exported="false" />
    </application>

</manifest>
```

---

## Step 12) `MainActivity` — chuyển Onboarding khi chưa hoàn tất

**Giải thích:** Sau bước kiểm tra session, đảm bảo profile tồn tại rồi đọc `profile.onboarded`. Nếu `false` → mở `OnboardingActivity`, `finish()` và `return` trước khi inflate layout Main.

**File:** `app/src/main/java/com/aistudy/aistudy/MainActivity.kt`

**Thêm import:**

```kotlin
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.ProfileDefaults
import com.aistudy.aistudy.onboarding.OnboardingActivity
```

**Tìm:**

```kotlin
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        enableEdgeToEdge()
```

**Đổi thành:**

```kotlin
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        ProfileDefaults.ensureExists(AppDatabase.getInstance(this).profileDao(), userId)
        val profile = AppDatabase.getInstance(this).profileDao().getByUserId(userId)
        if (profile != null && !profile.onboarded) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        enableEdgeToEdge()
```

**File `MainActivity.kt` đầy đủ sau Phase 2:**

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
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.ProfileDefaults
import com.aistudy.aistudy.history.HistoryFragment
import com.aistudy.aistudy.home.HomeFragment
import com.aistudy.aistudy.onboarding.OnboardingActivity
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

        ProfileDefaults.ensureExists(AppDatabase.getInstance(this).profileDao(), userId)
        val profile = AppDatabase.getInstance(this).profileDao().getByUserId(userId)
        if (profile != null && !profile.onboarded) {
            startActivity(Intent(this, OnboardingActivity::class.java))
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

---

## Luồng hoạt động

```
RegisterActivity
  userDao.insert(user) → putLong("user_id")
  ProfileDefaults.ensureExists → Profile(onboarded = false)
  → MainActivity

LoginActivity
  findByEmail + so mật khẩu → putLong("user_id")
  ProfileDefaults.ensureExists
  → MainActivity

MainActivity
  user_id == -1                     → LoginActivity
  profile != null && !onboarded     → OnboardingActivity
  còn lại                           → BottomNav + HomeFragment

OnboardingActivity
  Bước 1 level → Bước 2 subjects (≥ 1 môn) → Bước 3 style
  profile.onboarded = true, profileDao.update
  → MainActivity (CLEAR_TOP | SINGLE_TOP)
```

---

## Schema SQLite sau Phase 2

**Bảng `users`:** `id` (PK auto), `fullName`, `email` (unique), `password`.

**Bảng `profiles`:**

| Cột | Kiểu SQLite | Ví dụ |
|-----|-------------|-------|
| `userId` | INTEGER PK | `1` |
| `onboarded` | INTEGER (0/1) | `1` |
| `level` | TEXT | `uni` |
| `subjects` | TEXT | `math,code` |
| `style` | TEXT | `detailed` |
| `xp` | INTEGER | `0` |
| `streak` | INTEGER | `0` |

Kiểm tra bằng **App Inspection → Database Inspector** → `aistudy.db` → `profiles`.

---

## Step 13) Build & test tay

1. **Build → Rebuild Project** (KSP sinh lại `AppDatabase_Impl` với bảng `profiles`).
2. Gỡ app cũ (version DB tăng nên dữ liệu cũ bị xóa), chạy lại.
3. **User mới:** Register → vào Onboarding bước 1, thanh tiến trình sáng 1 đoạn, không có nút Back.
4. Chọn **University** → Continue → bước 2, nút Back xuất hiện.
5. **Validate môn:** bỏ chọn hết môn → Continue → Toast `Pick at least one subject.`
6. Chọn **Math + Programming** → Continue → bước 3, nút đổi thành `Start learning`.
7. Bấm **Back** → quay lại bước 2, các môn đã chọn vẫn giữ.
8. Chọn **Detailed** → Start learning → vào Main tab Home.
9. Database Inspector: `onboarded = 1`, `level = uni`, `subjects` chứa `math` và `code`, `style = detailed`.
10. Đóng app, mở lại → vào thẳng Main, không qua Onboarding.

### Lỗi thường gặp

| Triệu chứng | Nguyên nhân hay gặp | Cách xử lý |
|-------------|---------------------|------------|
| Crash `Room cannot verify the data integrity` | Quên tăng `version = 2` | Step 4 |
| `Unresolved reference: profileDao` | Chưa thêm `abstract fun profileDao()` | Step 4 |
| `[ksp] ... There is no column / table profiles` | Chưa thêm `Profile::class` vào `entities` | Step 4, Rebuild |
| Crash `ActivityNotFoundException: OnboardingActivity` | Chưa khai báo trong Manifest | Step 11 |
| `UninitializedPropertyAccessException: lateinit property ... has not been initialized` | Dùng view trước `bindViews()` | Gọi `bindViews()` trước `setup...Choices()` |
| Onboarding hiện lại sau khi hoàn tất | Quên `profileDao.update(profile)` hoặc `onboarded = true` | Step 10, `onNext` |
| Back từ Main quay về Onboarding | Thiếu `finish()` trong `goToMain` | Step 10 |

---

## Checklist hoàn thành Phase 2

- [ ] `Profile`, `ProfileDao`, `ProfileDefaults` tạo xong
- [ ] `AppDatabase` có `Profile::class`, `version = 2`, `profileDao()`
- [ ] Login + Register gọi `ProfileDefaults.ensureExists`
- [ ] `strings_onboarding.xml` + 4 drawable onboarding
- [ ] `activity_onboarding.xml` + `OnboardingActivity`
- [ ] Manifest khai báo `.onboarding.OnboardingActivity`
- [ ] `MainActivity` chuyển Onboarding khi `onboarded = false`
- [ ] Test: user mới đi đủ 3 bước, dữ liệu lưu đúng, mở lại không hiện Onboarding

**Tiếp theo:** Phase 3–4 — `QuestionAnswer` + Gemini (`docs/PHASE_3_4_STEP_BY_STEP_CODE.md`).
