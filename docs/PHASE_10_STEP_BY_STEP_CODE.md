# Phase 10: Image question submission — Hướng dẫn từng bước


Mục tiêu Phase 10:

1. **Camera / Gallery** — chọn hoặc chụp ảnh bài tập trên `AskFragment`
2. **Preview + xóa** — xem ảnh trước khi gửi, bỏ chọn được
3. **Gemini multimodal** — gửi text và/hoặc ảnh (base64 inline) trong cùng request
4. **Lưu `imagePath`** — copy ảnh vào app storage, lưu Room, hiện thumbnail trên Answer

**Điều kiện trước khi làm:** Phase 0–9 đã xong (Ask text, Answer extras, Room v4).

**Không làm ở Phase này:** Quiz (P11), Progress v2 (P12), gamification (P13).

**Lưu ý DB:** bump Room **v4 → v5** + `fallbackToDestructiveMigration(true)` → data QA cũ trên máy dev **bị xóa**. Bình thường cho giai đoạn này.

**minSdk = 33** → chỉ cần `READ_MEDIA_IMAGES` (không legacy storage).

---

## Tổng quan các file sẽ chạm

| File | Loại | Việc |
|------|------|------|
| `QuestionAnswer.kt` | Sửa | Thêm property `imagePath` |
| `AppDatabase.kt` | Sửa | `version = 5` |
| `GeminiClient.kt` | Sửa | Overload `ask(..., imageBase64, mimeType)` multimodal |
| `AndroidManifest.xml` | Sửa | CAMERA, READ_MEDIA_IMAGES, FileProvider |
| `res/xml/file_paths.xml` | **Mới** | Paths cho FileProvider (camera cache + qa_images) |
| `strings.xml` | Sửa | Chuỗi Camera / Gallery / permission / default question |
| `fragment_ask.xml` | Sửa | Preview + nút Camera / Gallery |
| `AskFragment.kt` | Sửa | Permission, pick/capture, encode, submit |
| `activity_answer.xml` | Sửa | Block thumbnail ảnh |
| `AnswerActivity.kt` | Sửa | Load `imagePath` → ImageView |

---

## Step 1) Entity — thêm `imagePath`

**Giải thích:**  
Cần đường dẫn file ảnh trong app storage để Answer/History sau này mở lại được, không phụ thuộc URI gallery tạm thời. Property kiểu `String?` (nullable) vì câu hỏi text-only không có ảnh; Room map thành cột `TEXT` cho phép `NULL`.

**File:** `app/src/main/java/com/aistudy/aistudy/database/QuestionAnswer.kt`

**Thêm property ngay sau `var reviewedAt: Long = 0`:**

```kotlin
    var imagePath: String? = null
```

| Property | Ý nghĩa |
|-------|---------|
| `imagePath` | Absolute path trong `filesDir/qa_images/…`; `null` = hỏi text-only |

**Kiểm tra nhanh:** Build lại, entity compile OK; chưa bump version thì Room chưa có cột mới trên máy.

---

## Step 2) Bump `AppDatabase` → version 5

**Giải thích:**  
Đổi schema entity bắt buộc tăng `version`. Project dùng `fallbackToDestructiveMigration(true)` → dev: xóa DB cũ, tạo lại.

**File:** `app/src/main/java/com/aistudy/aistudy/database/AppDatabase.kt`

**Tìm:**

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

**Đổi thành:**

```kotlin
@Database(
    entities = [
        User::class,
        Profile::class,
        QuestionAnswer::class
    ],
    version = 5,
    exportSchema = false
)
```

**Kiểm tra nhanh:** Chạy app lần đầu sau đổi → login lại; QA cũ mất (destructive).

> **Note Phase 11:** plan cũ ghi Quiz = v5. Sau Phase 10, Quiz bump tiếp **v6**.

---

## Step 3) `GeminiClient` — multimodal

**Giải thích:**  
API Gemini `generateContent` nhận nhiều `parts`: `text` + `inline_data` (mime + base64). Giữ hàm `ask(apiKey, prompt)` cũ để Answer extras (Phase 9) không đổi call site. `GeminiClient` là `object` nên gọi trực tiếp `GeminiClient.ask(...)`. Tham số ảnh khai báo `String?` để truyền `null` khi hỏi text-only; `isNullOrEmpty()` kiểm tra cả `null` lẫn chuỗi rỗng. `conn.outputStream.use { }` tự đóng stream kể cả khi có exception.

**File:** `app/src/main/java/com/aistudy/aistudy/ask/GeminiClient.kt`

**Đổi thành (toàn bộ file):**

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
        return ask(apiKey, prompt, null, null)
    }

    fun ask(apiKey: String, prompt: String, imageBase64: String?, mimeType: String?): String {
        val hasImage = !imageBase64.isNullOrEmpty()
        Log.d(TAG, "request start model=$MODEL promptLength=${prompt.length} hasImage=$hasImage")
        val urlString = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=$apiKey"
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 30000
        conn.readTimeout = 90000

        val parts = JSONArray()

        val textPart = JSONObject()
        textPart.put("text", prompt)
        parts.put(textPart)

        if (hasImage) {
            val mime = if (mimeType.isNullOrEmpty()) "image/jpeg" else mimeType
            val inlineData = JSONObject()
            inlineData.put("mime_type", mime)
            inlineData.put("data", imageBase64)
            val imagePart = JSONObject()
            imagePart.put("inline_data", inlineData)
            parts.put(imagePart)
        }

        val content = JSONObject()
        content.put("parts", parts)

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

| Điểm | Chi tiết |
|------|----------|
| `ask(apiKey, prompt)` | Gọi overload với `null` image — Answer extras vẫn dùng được |
| `inline_data` | REST Gemini dùng snake_case `mime_type` + `data` |
| `code in 200..299` | Kiểm tra mã HTTP thành công bằng range khi chọn stream đọc |
| Read timeout | 90s — ảnh thường lâu hơn text |

**Kiểm tra nhanh:** Ask text-only vẫn chạy (image `null` → body chỉ có text part).

---

## Step 4) Permissions + FileProvider

**Giải thích:**  
Camera Intent cần URI qua `FileProvider` (không dùng `file://`). Gallery trên API 33+ cần `READ_MEDIA_IMAGES`.

### 4a) Manifest

**File:** `app/src/main/AndroidManifest.xml`

**Thêm ngay dưới `INTERNET`:**

```xml
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />

    <uses-feature
        android:name="android.hardware.camera"
        android:required="false" />
```

**Thêm trong `<application>` (sau các Activity):**

```xml
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>
```

### 4b) File paths XML (**mới**)

**File:** `app/src/main/res/xml/file_paths.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path
        name="camera_cache"
        path="camera/" />
    <files-path
        name="qa_images"
        path="qa_images/" />
</paths>
```

| Path | Dùng cho |
|------|----------|
| `cacheDir/camera/` | File tạm khi `TakePicture` |
| `filesDir/qa_images/` | Ảnh lưu bền để Answer xem lại |

**Kiểm tra nhanh:** Cài app → Settings → App permissions thấy Camera + Photos.

---

## Step 5) Strings

**Giải thích:**  
UI tiếng Anh như các phase trước; prompt mặc định khi image-only không có text.

**File:** `app/src/main/res/values/strings.xml`

**Thêm sau `ask_suggestion_4`:**

```xml
    <string name="ask_camera">Camera</string>
    <string name="ask_gallery">Gallery</string>
    <string name="ask_remove_image">Remove</string>
    <string name="ask_image_preview">Selected question image</string>
    <string name="ask_empty_both">Enter a question or attach an image.</string>
    <string name="ask_image_default_question">Please solve the problem shown in this image.</string>
    <string name="ask_permission_camera">Camera permission is required to take a photo.</string>
    <string name="ask_permission_gallery">Photo access is required to pick an image.</string>
    <string name="ask_image_error">Could not load the selected image.</string>
    <string name="answer_image_label">Attached image</string>
```

---

## Step 6) Layout Ask — preview + Camera / Gallery

**Giải thích:**  
User cần thấy ảnh trước khi gửi và bỏ chọn. Nút send nằm cùng hàng Camera/Gallery cho gọn.

**File:** `app/src/main/res/layout/fragment_ask.xml`

**Thay khối sau `edit_question` (nút Send cũ đứng một mình) bằng:**

```xml
            <LinearLayout
                android:id="@+id/layout_image_preview"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="12dp"
                android:orientation="vertical"
                android:visibility="gone">

                <ImageView
                    android:id="@+id/img_preview"
                    android:layout_width="match_parent"
                    android:layout_height="180dp"
                    android:adjustViewBounds="true"
                    android:contentDescription="@string/ask_image_preview"
                    android:scaleType="centerCrop" />

                <com.google.android.material.button.MaterialButton
                    android:id="@+id/btn_remove_image"
                    style="@style/Widget.Material3.Button.TextButton"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_gravity="end"
                    android:text="@string/ask_remove_image"
                    android:textColor="@color/auth_muted_foreground" />
            </LinearLayout>

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="12dp"
                android:gravity="center_vertical"
                android:orientation="horizontal">

                <com.google.android.material.button.MaterialButton
                    android:id="@+id/btn_camera"
                    style="@style/Widget.Material3.Button.OutlinedButton"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_marginEnd="8dp"
                    android:layout_weight="1"
                    android:text="@string/ask_camera"
                    android:textColor="@color/auth_foreground"
                    app:strokeColor="@color/auth_input_ring" />

                <com.google.android.material.button.MaterialButton
                    android:id="@+id/btn_gallery"
                    style="@style/Widget.Material3.Button.OutlinedButton"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_marginEnd="8dp"
                    android:layout_weight="1"
                    android:text="@string/ask_gallery"
                    android:textColor="@color/auth_foreground"
                    app:strokeColor="@color/auth_input_ring" />

                <com.google.android.material.button.MaterialButton
                    android:id="@+id/btn_send"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/ask_send"
                    app:backgroundTint="@color/auth_brand"
                    app:cornerRadius="24dp" />
            </LinearLayout>
```

(Giữ `ProgressBar` ngay dưới như cũ.)

**ids mới:** `layout_image_preview`, `img_preview`, `btn_remove_image`, `btn_camera`, `btn_gallery`.

---

## Step 7) `AskFragment` — logic ảnh

**Giải thích:**  
Dùng `ActivityResultContracts` (không dùng `startActivityForResult` đã deprecated). Flow:

1. Xin permission → Camera (`TakePicture`) hoặc Gallery (`GetContent`)
2. Gallery → copy ngay vào `filesDir/qa_images/`
3. Camera → file cache → lúc Submit mới copy vào `qa_images`
4. Submit: text-only **hoặc** text+image **hoặc** image-only (caption mặc định)
5. Encode Base64 → `GeminiClient.ask(...)` → insert QA kèm `imagePath`

**File:** `app/src/main/java/com/aistudy/aistudy/ask/AskFragment.kt`

### 7a) Import cần thêm

Chèn các dòng sau vào khối import, giữ thứ tự alphabet như các import đang có (Kotlin không có dấu `;` cuối dòng):

```kotlin
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
```

`java.io.InputStream` không cần import: `openInputStream(uri)` trả về `InputStream?` và kiểu được suy luận tự động.

### 7b) Properties mới (cùng các property cũ)

**Giải thích:**  
View dùng `private lateinit var` (gán trong `onViewCreated`). Trạng thái ảnh có thể chưa có nên dùng `var ... : Type? = null`. Launcher gán trong `onCreate` trước khi dùng nên khai báo `lateinit`.

Thêm `btnCamera`, `btnGallery` ngay sau `btnSend`:

```kotlin
    private lateinit var btnCamera: MaterialButton
    private lateinit var btnGallery: MaterialButton
```

Thêm `layoutImagePreview`, `imgPreview` ngay sau `progressLoading`:

```kotlin
    private lateinit var layoutImagePreview: LinearLayout
    private lateinit var imgPreview: ImageView
```

Thêm trạng thái ảnh và launcher ngay sau `private var executor: ExecutorService? = null`:

```kotlin

    private var selectedImageFile: File? = null
    private var selectedMimeType: String? = null
    private var cameraUri: Uri? = null
    private var cameraFile: File? = null

    private lateinit var requestCameraPermission: ActivityResultLauncher<String>
    private lateinit var requestGalleryPermission: ActivityResultLauncher<String>
    private lateinit var takePicture: ActivityResultLauncher<Uri>
    private lateinit var pickImage: ActivityResultLauncher<String>
```

### 7c) Đăng ký launchers trong `onCreate` (không phải `onViewCreated`)

**Giải thích:** `registerForActivityResult` phải gọi trước khi Fragment ở trạng thái CREATED; đặt trong `onCreate`. Callback viết bằng trailing lambda. Trong callback camera, copy `cameraFile` sang biến local `file` để Kotlin smart-cast từ `File?` sang `File`. Trong callback gallery, `return@registerForActivityResult` thoát khỏi lambda khi user hủy chọn (`uri == null`).

Thêm hàm sau ngay dưới `onCreateView`:

```kotlin
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestCameraPermission = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                launchCamera()
            } else {
                Toast.makeText(requireContext(), R.string.ask_permission_camera, Toast.LENGTH_SHORT).show()
            }
        }

        requestGalleryPermission = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                pickImage.launch("image/*")
            } else {
                Toast.makeText(requireContext(), R.string.ask_permission_gallery, Toast.LENGTH_SHORT).show()
            }
        }

        takePicture = registerForActivityResult(
            ActivityResultContracts.TakePicture()
        ) { success ->
            val file = cameraFile
            if (success && file != null && file.exists()) {
                setSelectedImage(file, "image/jpeg")
            } else {
                Toast.makeText(requireContext(), R.string.ask_image_error, Toast.LENGTH_SHORT).show()
            }
        }

        pickImage = registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri == null) return@registerForActivityResult
            try {
                val copied = copyUriToQaImages(uri)
                var mime = requireContext().contentResolver.getType(uri)
                if (mime == null || !mime.startsWith("image/")) {
                    mime = "image/jpeg"
                }
                setSelectedImage(copied, mime)
            } catch (e: Exception) {
                Log.e(TAG, "gallery copy failed", e)
                Toast.makeText(requireContext(), R.string.ask_image_error, Toast.LENGTH_SHORT).show()
            }
        }
    }
```

### 7d) Bind UI + click trong `onViewCreated`

**Thay toàn bộ `onViewCreated` bằng:**

```kotlin
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        editQuestion = view.findViewById(R.id.edit_question)
        btnSend = view.findViewById(R.id.btn_send)
        btnCamera = view.findViewById(R.id.btn_camera)
        btnGallery = view.findViewById(R.id.btn_gallery)
        progressLoading = view.findViewById(R.id.progress_loading)
        layoutImagePreview = view.findViewById(R.id.layout_image_preview)
        imgPreview = view.findViewById(R.id.img_preview)
        executor = Executors.newSingleThreadExecutor()

        btnSend.setOnClickListener { submitQuestion() }
        btnCamera.setOnClickListener { ensureCameraPermission() }
        btnGallery.setOnClickListener { ensureGalleryPermission() }
        view.findViewById<View>(R.id.btn_remove_image).setOnClickListener { clearSelectedImage() }

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
```

`findViewById<View>(...)` cần ghi rõ kiểu generic vì kết quả không gán vào biến có kiểu khai báo sẵn.

### 7e) Helpers permission / camera / copy / preview

**Giải thích:**  
`FileInputStream(...).use { }` / `FileOutputStream(...).use { }` tự đóng stream kể cả khi có exception. Vòng đọc dùng `var n = input.read(buf)` rồi đọc lại cuối vòng lặp, vì Kotlin không cho gán trong biểu thức `while`.

Thêm các hàm sau ngay sau `onViewCreated`:

```kotlin
    private fun ensureCameraPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            launchCamera()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun ensureGalleryPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_MEDIA_IMAGES)
            == PackageManager.PERMISSION_GRANTED
        ) {
            pickImage.launch("image/*")
        } else {
            requestGalleryPermission.launch(Manifest.permission.READ_MEDIA_IMAGES)
        }
    }

    private fun launchCamera() {
        try {
            val dir = File(requireContext().cacheDir, "camera")
            if (!dir.exists() && !dir.mkdirs()) {
                throw Exception("cannot create camera cache")
            }
            val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
            cameraFile = file
            val uri = FileProvider.getUriForFile(
                requireContext(),
                requireContext().packageName + ".fileprovider",
                file
            )
            cameraUri = uri
            takePicture.launch(uri)
        } catch (e: Exception) {
            Log.e(TAG, "launch camera failed", e)
            Toast.makeText(requireContext(), R.string.ask_image_error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyUriToQaImages(uri: Uri): File {
        val dir = File(requireContext().filesDir, "qa_images")
        if (!dir.exists() && !dir.mkdirs()) {
            throw Exception("cannot create qa_images")
        }
        val out = File(dir, "qa_${System.currentTimeMillis()}.jpg")
        val input = requireContext().contentResolver.openInputStream(uri)
        input.use { ins ->
            FileOutputStream(out).use { fos ->
                if (ins == null) throw Exception("null input stream")
                val buf = ByteArray(8192)
                var n = ins.read(buf)
                while (n >= 0) {
                    fos.write(buf, 0, n)
                    n = ins.read(buf)
                }
            }
        }
        return out
    }

    private fun persistCameraToQaImages(source: File): File {
        val dir = File(requireContext().filesDir, "qa_images")
        if (!dir.exists() && !dir.mkdirs()) {
            throw Exception("cannot create qa_images")
        }
        val out = File(dir, "qa_${System.currentTimeMillis()}.jpg")
        FileInputStream(source).use { input ->
            FileOutputStream(out).use { fos ->
                val buf = ByteArray(8192)
                var n = input.read(buf)
                while (n >= 0) {
                    fos.write(buf, 0, n)
                    n = input.read(buf)
                }
            }
        }
        return out
    }

    private fun setSelectedImage(file: File, mimeType: String) {
        selectedImageFile = file
        selectedMimeType = mimeType
        imgPreview.setImageBitmap(BitmapFactory.decodeFile(file.absolutePath))
        layoutImagePreview.visibility = View.VISIBLE
    }

    private fun clearSelectedImage() {
        selectedImageFile = null
        selectedMimeType = null
        imgPreview.setImageDrawable(null)
        layoutImagePreview.visibility = View.GONE
    }
```

**Hàm encode Base64** không dùng state của Fragment nên đặt trong `companion object`. Thêm vào `companion object` ngay sau khai báo `SUBJECTS`:

```kotlin

        private fun encodeFileBase64(file: File): String {
            FileInputStream(file).use { input ->
                ByteArrayOutputStream().use { out ->
                    val buf = ByteArray(8192)
                    var n = input.read(buf)
                    while (n >= 0) {
                        out.write(buf, 0, n)
                        n = input.read(buf)
                    }
                    return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                }
            }
        }
```

### 7f) Đổi `submitQuestion` — cho phép image-only + gọi multimodal

**Đầu hàm — validation (đổi `val question` thành `var question` vì có thể gán caption mặc định):**

```kotlin
        var question = editQuestion.text.toString().trim()
        val hasImage = selectedImageFile?.exists() == true
        if (TextUtils.isEmpty(question) && !hasImage) {
            Toast.makeText(requireContext(), R.string.ask_empty_both, Toast.LENGTH_SHORT).show()
            return
        }
        if (TextUtils.isEmpty(question)) {
            question = getString(R.string.ask_image_default_question)
        }
```

`selectedImageFile?.exists() == true` trả `false` khi chưa chọn ảnh (safe call trả `null`, so sánh với `true` ra `false`).

**Log bắt đầu submit — thay bằng bản có `hasImage`:**

```kotlin
        Log.d(
            TAG, "submit start questionLength=${question.length}" +
                " hasImage=$hasImage" +
                " apiKey=${maskKey(BuildConfig.GEMINI_API_KEY)}"
        )
```

**Trước `executor?.execute` — chụp giá trị vào biến local:**

```kotlin
        val questionFinal = question
        val imageFile = selectedImageFile
        val mimeType = selectedMimeType
        val useImage = hasImage
```

Lambda chạy trên background thread, trong lúc đó user có thể bấm Remove làm `selectedImageFile` đổi. Copy sang `val` giữ đúng giá trị tại thời điểm bấm Send và cho phép smart-cast `imageFile` sau khi kiểm tra `!= null`.

**Trong `executor`, thay dòng gọi Gemini cũ bằng:**

```kotlin
                var imageBase64: String? = null
                var imagePath: String? = null
                var mime = mimeType

                if (useImage && imageFile != null) {
                    val parent = imageFile.parentFile
                    val persisted = if (parent != null && "qa_images" == parent.name) {
                        imageFile
                    } else {
                        persistCameraToQaImages(imageFile)
                    }
                    imagePath = persisted.absolutePath
                    val encoded = encodeFileBase64(persisted)
                    imageBase64 = encoded
                    if (mime.isNullOrEmpty()) {
                        mime = "image/jpeg"
                    }
                    Log.d(TAG, "image ready path=$imagePath base64Length=${encoded.length}")
                }

                Log.d(TAG, "calling Gemini...")
                val raw = GeminiClient.ask(BuildConfig.GEMINI_API_KEY, prompt, imageBase64, mime)
```

**Khi tạo QA — đổi `qa.question` và thêm `qa.imagePath` ngay sau `qa.reviewedAt = 0`:**

```kotlin
                qa.question = questionFinal
```

```kotlin
                qa.imagePath = imagePath
```

**Sau `val qaId = qaDao.insert(qa)` — đổi log thành:**

```kotlin
                Log.d(TAG, "saved qaId=$qaId imagePath=$imagePath")
```

**Sau insert thành công (trong `requireActivity().runOnUiThread { }`, ngay sau `setLoading(false)`) — clear form:**

```kotlin
                    clearSelectedImage()
                    editQuestion.setText("")
```

### 7g) Prompt — nhắc đọc ảnh

**Trong `buildPrompt`, tìm:**

```kotlin
            "Also rate difficulty as easy, medium, or hard for this student.\n\n" +
```

**Đổi thành:**

```kotlin
            "Also rate difficulty as easy, medium, or hard for this student.\n" +
            "If an image is attached, read the problem from the image carefully.\n\n" +
```

### 7h) `setLoading` — disable Camera/Gallery khi đang gửi

**Thay toàn bộ `setLoading` bằng:**

```kotlin
    private fun setLoading(loading: Boolean) {
        btnSend.isEnabled = !loading
        btnCamera.isEnabled = !loading
        btnGallery.isEnabled = !loading
        btnSend.setText(if (loading) R.string.ask_sending else R.string.ask_send)
        progressLoading.visibility = if (loading) View.VISIBLE else View.GONE
    }
```

**Kiểm tra nhanh:** Build lại. Pick gallery → preview hiện; Remove → ẩn; Submit không text + không ảnh → toast `ask_empty_both`.

---

## Step 8) Answer — hiện thumbnail

**Giải thích:**  
Nếu QA có `imagePath` và file còn tồn tại → hiện block ảnh dưới câu hỏi.

### 8a) Layout

**File:** `app/src/main/res/layout/activity_answer.xml`

**Sau `tv_question`, trước label Steps — chèn:**

```xml
            <LinearLayout
                android:id="@+id/layout_answer_image"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginBottom="24dp"
                android:orientation="vertical"
                android:visibility="gone">

                <TextView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginBottom="8dp"
                    android:text="@string/answer_image_label"
                    android:textAllCaps="true"
                    android:textColor="@color/auth_muted_foreground"
                    android:textSize="10sp"
                    android:textStyle="bold" />

                <ImageView
                    android:id="@+id/img_answer"
                    android:layout_width="match_parent"
                    android:layout_height="200dp"
                    android:adjustViewBounds="true"
                    android:contentDescription="@string/answer_image_label"
                    android:scaleType="centerCrop" />
            </LinearLayout>
```

Đổi `layout_marginBottom` của `tv_question` từ `24dp` thành `16dp` để khoảng cách tới block ảnh gọn hơn.

### 8b) Activity

**File:** `app/src/main/java/com/aistudy/aistudy/answer/AnswerActivity.kt`

**Import thêm (chèn đúng thứ tự alphabet):**

```kotlin
import android.graphics.BitmapFactory
import android.widget.ImageView
import java.io.File
```

**Sau `tvDifficulty.text = difficultyLabel(item.difficulty)` — thêm một dòng trống rồi:**

```kotlin
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
```

`item` là QA local (non-null) đã load từ `dao.getById(qaId)`. Copy `item.imagePath` sang `val imagePath` để sau `isNullOrEmpty()` Kotlin smart-cast thành `String` khi tạo `File(imagePath)`.

**Kiểm tra nhanh:** Mở Answer từ History cho QA có ảnh → thấy thumbnail; QA text-only → block ẩn.

---

## Step 9) Test tay (checklist)

### Checklist Camera / Gallery

1. Ask → **Gallery** → cho phép Photos → chọn ảnh → preview hiện.
2. Bấm **Remove** → preview ẩn.
3. Ask → **Camera** → cho phép Camera → chụp → preview hiện.
4. Từ chối permission → toast tương ứng, không crash.

### Checklist Submit modes

1. **Text-only** (không ảnh) → Answer như Phase 9, không thumbnail.
2. **Text + image** → AI trả steps đúng nội dung ảnh/text; Answer có thumbnail.
3. **Image-only** (ô trống) → hỏi mặc định *Please solve the problem…*; Answer vẫn có thumbnail + steps.

### Checklist Persist

1. Sau Ask ảnh → mở lại Answer từ History → thumbnail vẫn còn.
2. Logcat `AskFragment`: `saved qaId=… imagePath=…/qa_images/qa_….jpg`.
3. Logcat `GeminiClient`: `hasImage=true`.

### Lỗi thường gặp

| Triệu chứng | Nguyên nhân hay gặp | Cách xử lý |
|-------------|---------------------|------------|
| Crash FileProvider | Thiếu `file_paths.xml` / provider | Step 4 |
| Camera không mở | Chưa xin CAMERA / FileProvider authority sai | Step 4 + 7e |
| Gallery empty / fail | Chưa `READ_MEDIA_IMAGES` | Step 4a |
| `Unresolved reference: FileProvider` / `ActivityResultContracts` | Thiếu import | Step 7a |
| `lateinit property takePicture has not been initialized` | Launcher chưa đăng ký trong `onCreate` | Step 7c |
| `IllegalStateException` khi `registerForActivityResult` | Đăng ký trong `onViewCreated` hoặc click listener | Chuyển vào `onCreate` (Step 7c) |
| `Val cannot be reassigned` ở `question` | Còn khai báo `val question` | Đổi thành `var question` (Step 7f) |
| Gemini lỗi HTTP | Body quá lớn / model không hỗ trợ vision | Giảm độ phân giải; check model |
| Answer không thumbnail | `imagePath` null hoặc file bị xóa | Step 7f persist |
| Schema crash | Quên bump version 5 | Step 2 |
| Data biến mất | Destructive migration | Đúng kỳ vọng Phase 10 |

---

## Mapping với plan / FR

| FR / Plan | Android Phase 10 |
|-----------|------------------|
| Image upload questions | Camera + Gallery trên Ask |
| Gemini multimodal | `inline_data` base64 trong `GeminiClient` |
| Xem lại ảnh | `qa.imagePath` + thumbnail Answer |

---

## Checklist hoàn thành Phase 10

- [ ] `QuestionAnswer.imagePath`
- [ ] `AppDatabase` version **5**
- [ ] `GeminiClient.ask(apiKey, prompt, imageBase64, mimeType)`
- [ ] Manifest: CAMERA + READ_MEDIA_IMAGES + FileProvider
- [ ] `file_paths.xml`
- [ ] Ask: preview, Camera, Gallery, Remove, 3 chế độ submit
- [ ] Answer: hiện thumbnail khi có ảnh
- [ ] Test tay theo Step 9

**Tiếp theo:** Phase 11 — AI Practice/Quiz (`AppDatabase` **v6**) — xem `docs/PHASE_11_STEP_BY_STEP_CODE.md`.
