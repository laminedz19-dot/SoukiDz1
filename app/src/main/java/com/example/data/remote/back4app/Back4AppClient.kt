package com.example.data.remote.back4app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.example.BuildConfig
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ListingEntity
import com.example.data.local.OrderEntity
import com.example.data.local.PlatformSettingsEntity
import com.example.data.local.ReportEntity
import com.example.data.local.TopUpRequestEntity
import com.example.data.local.UserEntity
import com.example.data.local.WalletEntity
import com.example.data.local.WalletTransactionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import com.parse.ParseUser
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Full-scale Back4App / Parse REST API Client for SoukiDz.
 * Connects the entire application to the cloud database without external SDK dependencies.
 */
class Back4AppClient {

    companion object {
        private const val TAG = "Back4AppClient"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val JPEG_MEDIA_TYPE = "image/jpeg".toMediaType()
    }

    private val applicationId = BuildConfig.BACK4APP_APPLICATION_ID.trim()
    private val clientKey = BuildConfig.BACK4APP_CLIENT_KEY.trim()
    private val baseUrl = BuildConfig.BACK4APP_SERVER_URL.trim()
        .ifBlank { "https://parseapi.back4app.com/" }
        .let { if (it.endsWith("/")) it else "$it/" }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()

    private fun newRequestBuilder(endpoint: String): Request.Builder {
        check(applicationId.isNotBlank()) { "لم تتم تهيئة معرف تطبيق Back4App لهذا الإصدار." }
        val url = if (endpoint.startsWith("http")) endpoint else "$baseUrl$endpoint"
        val builder = Request.Builder()
            .url(url)
            .addHeader("X-Parse-Application-Id", applicationId)
        if (clientKey.isNotBlank()) builder.addHeader("X-Parse-Client-Key", clientKey)
        ParseUser.getCurrentUser()?.getSessionToken()?.takeIf { it.isNotBlank() }?.let {
            builder.addHeader("X-Parse-Session-Token", it)
        }
        return builder
    }

    // ==========================================
    // 1. FILE STORAGE (Receipts, Ads, Avatars)
    // ==========================================
    suspend fun uploadReceiptImage(context: Context, uri: Uri, customName: String = "receipt"): Result<String> =
        uploadImage(context, uri, customName)

    suspend fun uploadListingImage(context: Context, uri: Uri, customName: String = "listing"): Result<String> =
        uploadImage(context, uri, customName)

    suspend fun uploadImage(context: Context, uri: Uri, customName: String = "file"): Result<String> = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(Exception("تعذر فتح ملف الصورة المحدد"))

            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            if (originalBitmap == null) {
                return@withContext Result.failure(Exception("ملف الصورة تالف أو غير صالح"))
            }

            val maxDim = 1200
            val w = originalBitmap.width
            val h = originalBitmap.height
            val scale = if (w > h) {
                if (w > maxDim) maxDim.toFloat() / w else 1f
            } else {
                if (h > maxDim) maxDim.toFloat() / h else 1f
            }

            val finalBitmap = if (scale < 1f) {
                Bitmap.createScaledBitmap(originalBitmap, (w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1), true)
            } else {
                originalBitmap
            }

            val baos = ByteArrayOutputStream()
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, 75, baos)
            val bytes = baos.toByteArray()

            val cleanName = customName.replace("[^a-zA-Z0-9]".toRegex(), "_") + ".jpg"
            val requestBody = bytes.toRequestBody(JPEG_MEDIA_TYPE)

            val request = newRequestBuilder("files/$cleanName")
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()
            val respBody = response.body?.string().orEmpty()

            if (response.isSuccessful) {
                val json = JSONObject(respBody)
                val fileUrl = json.optString("url")
                if (fileUrl.isNotBlank()) {
                    Log.i(TAG, "Uploaded image to Back4App: $fileUrl")
                    Result.success(fileUrl)
                } else {
                    Result.failure(Exception("استجابة غير صحيحة من الخادم: $respBody"))
                }
            } else {
                Result.failure(Exception("خطأ في خادم رفع الملفات: $respBody"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading image to Back4App: ${e.message}", e)
            Result.failure(e)
        }
    }

    // ==========================================
    // GENERIC UPSERT HELPER (Avoids duplicate records)
    // ==========================================
    private suspend fun upsertObject(className: String, idKey: String, idValue: String, data: JSONObject): Result<String> = withContext(Dispatchers.IO) {
        try {
            data.put(idKey, idValue)
            val encodedWhere = URLEncoder.encode(JSONObject().put(idKey, idValue).toString(), "UTF-8")
            val findReq = newRequestBuilder("classes/$className?where=$encodedWhere&limit=1").get().build()
            val findResp = httpClient.newCall(findReq).execute()
            val findBody = findResp.body?.string().orEmpty()

            val existingObjectId = if (findResp.isSuccessful) {
                val results = JSONObject(findBody).optJSONArray("results")
                if (results != null && results.length() > 0) {
                    results.getJSONObject(0).optString("objectId")
                } else null
            } else null

            if (!existingObjectId.isNullOrBlank()) {
                // Update
                val updateReq = newRequestBuilder("classes/$className/$existingObjectId")
                    .put(data.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()
                val updateResp = httpClient.newCall(updateReq).execute()
                if (updateResp.isSuccessful) Result.success(existingObjectId)
                else Result.failure(Exception("فشل التحديث في Back4App: ${updateResp.body?.string()}"))
            } else {
                // Insert
                val insertReq = newRequestBuilder("classes/$className")
                    .post(data.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()
                val insertResp = httpClient.newCall(insertReq).execute()
                val insertBody = insertResp.body?.string().orEmpty()
                if (insertResp.isSuccessful) {
                    val objId = JSONObject(insertBody).optString("objectId")
                    Result.success(objId)
                } else {
                    Result.failure(Exception("فشل الإنشاء في Back4App: $insertBody"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==========================================
    // 2. TOP UP REQUESTS
    // ==========================================
    suspend fun submitTopUpRequest(req: TopUpRequestEntity): Result<String> = withContext(Dispatchers.IO) {
        try {
            val user = ParseUser.getCurrentUser()
                ?: return@withContext Result.failure(SecurityException("سجّل الدخول قبل إرسال طلب الشحن."))
            if (user.objectId != req.userId) {
                return@withContext Result.failure(SecurityException("هوية حساب Parse لا تطابق صاحب طلب الشحن."))
            }
            if (req.amountDzd < 200) return@withContext Result.failure(IllegalArgumentException("الحد الأدنى للشحن هو 200 دج."))
            val json = JSONObject().apply {
                put("requestId", req.id)
                put("user", JSONObject().put("__type", "Pointer").put("className", "_User").put("objectId", user.objectId))
                put("userId", user.objectId)
                put("userName", req.userName)
                put("userPhone", req.userPhone)
                put("amountDzd", req.amountDzd)
                put("provider", req.provider)
                put("reference", req.reference)
                put("receiptImageUri", req.receiptImageUri)
                put("status", "PENDING")
                put("adminNote", "")
                put("createdAtMs", req.createdAt)
                put("reviewedAtMs", 0L)
            }
            val request = newRequestBuilder("classes/TopUpRequest")
                .post(json.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) Result.success(JSONObject(body).optString("objectId", req.id))
                else Result.failure(Exception("تعذر حفظ طلب الشحن في Back4App: $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTopUpRequests(): Result<List<TopUpRequestEntity>> = fetchTopUpRequests(null)

    suspend fun getUserTopUpRequests(userId: String): Result<List<TopUpRequestEntity>> =
        fetchTopUpRequests(userId.takeIf { it.isNotBlank() && it != "user_me" })

    private suspend fun fetchTopUpRequests(userId: String?): Result<List<TopUpRequestEntity>> = withContext(Dispatchers.IO) {
        try {
            val where = if (userId == null) null else JSONObject().put(
                "user", JSONObject().put("__type", "Pointer").put("className", "_User").put("objectId", userId)
            )
            val query = where?.let { "?where=${URLEncoder.encode(it.toString(), "UTF-8")}&order=-createdAt&limit=200" }
                ?: "?order=-createdAt&limit=200"
            val request = newRequestBuilder("classes/TopUpRequest$query").get().build()
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) return@withContext Result.failure(Exception("تعذر تحميل طلبات الشحن: $body"))
                val results = JSONObject(body).optJSONArray("results") ?: JSONArray()
                val list = buildList {
                    for (i in 0 until results.length()) {
                        val item = results.optJSONObject(i) ?: continue
                        val pointer = item.optJSONObject("user")
                        add(TopUpRequestEntity(
                            id = item.optString("requestId").ifBlank { item.optString("objectId") },
                            userId = pointer?.optString("objectId").orEmpty().ifBlank { item.optString("userId") },
                            userName = item.optString("userName"),
                            userPhone = item.optString("userPhone"),
                            amountDzd = item.optInt("amountDzd", 0),
                            provider = item.optString("provider"),
                            reference = item.optString("reference"),
                            receiptImageUri = item.optString("receiptImageUri"),
                            status = item.optString("status", "PENDING"),
                            adminNote = item.optString("adminNote"),
                            createdAt = item.optLong("createdAtMs", System.currentTimeMillis()),
                            reviewedAt = item.optLong("reviewedAtMs", 0L)
                        ))
                    }
                }
                Result.success(list)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateTopUpStatus(requestId: String, newStatus: String, adminNote: String): Result<Unit> = withContext(Dispatchers.IO) {
        when (newStatus) {
            "APPROVED" -> callTopUpCloudFunction("approveTopUpRequest", requestId, adminNote).map { }
            "REJECTED" -> callTopUpCloudFunction("rejectTopUpRequest", requestId, adminNote).map { }
            else -> Result.failure(IllegalArgumentException("حالة قرار الشحن غير صالحة."))
        }
    }

    private suspend fun callTopUpCloudFunction(name: String, requestId: String, adminNote: String): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            if (ParseUser.getCurrentUser() == null) return@withContext Result.failure(SecurityException("يجب تسجيل دخول المشرف."))
            val params = JSONObject().put("requestId", requestId).put("adminNote", adminNote)
            val request = newRequestBuilder("functions/$name")
                .post(params.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) Result.success(JSONObject(body).optJSONObject("result") ?: JSONObject())
                else Result.failure(Exception("فشلت مراجعة طلب الشحن: $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==========================================
    // 3. LISTINGS (Ads Marketplace)
    // ==========================================
    suspend fun saveListing(l: ListingEntity): Result<String> {
        val json = JSONObject().apply {
            put("id", l.id)
            put("userId", l.userId)
            put("userName", l.userName)
            put("userPhone", l.userPhone)
            put("isPhoneVisible", l.isPhoneVisible)
            put("title", l.title)
            put("description", l.description)
            put("categoryId", l.categoryId)
            put("categoryNameAr", l.categoryNameAr)
            put("subcategory", l.subcategory)
            put("priceDzd", l.priceDzd)
            put("isNegotiable", l.isNegotiable)
            put("condition", l.condition)
            put("wilayaCode", l.wilayaCode)
            put("wilayaName", l.wilayaName)
            put("commune", l.commune)
            put("imagesJson", l.imagesJson)
            put("videoUrl", l.videoUrl)
            put("status", l.status)
            put("rejectionReason", l.rejectionReason)
            put("packageType", l.packageType)
            put("publishingFeeDzd", l.publishingFeeDzd)
            put("isPaid", l.isPaid)
            put("isFeatured", l.isFeatured)
            put("isUrgent", l.isUrgent)
            put("viewsCount", l.viewsCount)
            put("createdAt", l.createdAt)
            put("expiresAt", l.expiresAt)
        }
        return upsertObject("Listing", "id", l.id, json)
    }

    suspend fun getListings(): Result<List<ListingEntity>> = withContext(Dispatchers.IO) {
        try {
            val req = newRequestBuilder("classes/Listing?order=-createdAt&limit=500").get().build()
            val resp = httpClient.newCall(req).execute()
            val body = resp.body?.string().orEmpty()
            if (resp.isSuccessful) {
                val results = JSONObject(body).optJSONArray("results") ?: JSONArray()
                val list = mutableListOf<ListingEntity>()
                for (i in 0 until results.length()) {
                    val item = results.getJSONObject(i)
                    list.add(ListingEntity(
                        id = item.optString("id").ifBlank { item.optString("objectId") },
                        userId = item.optString("userId"),
                        userName = item.optString("userName"),
                        userPhone = item.optString("userPhone"),
                        isPhoneVisible = item.optBoolean("isPhoneVisible", true),
                        title = item.optString("title"),
                        description = item.optString("description"),
                        categoryId = item.optString("categoryId"),
                        categoryNameAr = item.optString("categoryNameAr"),
                        subcategory = item.optString("subcategory"),
                        priceDzd = item.optLong("priceDzd", 0L),
                        isNegotiable = item.optBoolean("isNegotiable", false),
                        condition = item.optString("condition", "NEW"),
                        wilayaCode = item.optInt("wilayaCode", 16),
                        wilayaName = item.optString("wilayaName", "الجزائر"),
                        commune = item.optString("commune", ""),
                        imagesJson = item.optString("imagesJson", ""),
                        videoUrl = item.optString("videoUrl", ""),
                        status = item.optString("status", "PUBLISHED"),
                        rejectionReason = item.optString("rejectionReason", ""),
                        packageType = item.optString("packageType", "STANDARD"),
                        publishingFeeDzd = item.optInt("publishingFeeDzd", 0),
                        isPaid = item.optBoolean("isPaid", true),
                        isFeatured = item.optBoolean("isFeatured", false),
                        isUrgent = item.optBoolean("isUrgent", false),
                        viewsCount = item.optInt("viewsCount", 0),
                        createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                        expiresAt = item.optLong("expiresAt", System.currentTimeMillis() + 30L * 86400000L)
                    ))
                }
                Result.success(list)
            } else {
                Result.failure(Exception("فشل جلب الإعلانات من Back4App: $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateListingStatus(listingId: String, status: String, reason: String = ""): Result<Unit> {
        val json = JSONObject().apply {
            put("status", status)
            put("rejectionReason", reason)
        }
        return upsertObject("Listing", "id", listingId, json).map { }
    }

    // ==========================================
    // 4. PLATFORM SETTINGS (CCP, BaridiMob, Info)
    // ==========================================
    suspend fun savePlatformSettings(s: PlatformSettingsEntity): Result<String> {
        val json = JSONObject().apply {
            put("id", "global")
            put("standardAdFeeDzd", s.standardAdFeeDzd)
            put("featuredAdFeeDzd", s.featuredAdFeeDzd)
            put("urgentAdFeeDzd", s.urgentAdFeeDzd)
            put("adDurationDays", s.adDurationDays)
            put("autoPublishAfterPayment", s.autoPublishAfterPayment)
            put("isFreePromoActive", s.isFreePromoActive)
            put("officialRip", s.officialRip)
            put("officialKey", s.officialKey)
            put("officialAccountHolder", s.officialAccountHolder)
            put("officialProviderName", s.officialProviderName)
            put("officialInstructions", s.officialInstructions)
        }
        return upsertObject("PlatformSettings", "id", "global", json)
    }

    suspend fun getPlatformSettings(): Result<PlatformSettingsEntity?> = withContext(Dispatchers.IO) {
        try {
            val req = newRequestBuilder("classes/PlatformSettings?limit=1").get().build()
            val resp = httpClient.newCall(req).execute()
            val body = resp.body?.string().orEmpty()
            if (resp.isSuccessful) {
                val results = JSONObject(body).optJSONArray("results")
                if (results != null && results.length() > 0) {
                    val item = results.getJSONObject(0)
                    val settings = PlatformSettingsEntity(
                        id = "global",
                        standardAdFeeDzd = item.optInt("standardAdFeeDzd", 400),
                        featuredAdFeeDzd = item.optInt("featuredAdFeeDzd", 600),
                        urgentAdFeeDzd = item.optInt("urgentAdFeeDzd", 1000),
                        adDurationDays = item.optInt("adDurationDays", 30),
                        autoPublishAfterPayment = item.optBoolean("autoPublishAfterPayment", true),
                        isFreePromoActive = item.optBoolean("isFreePromoActive", false),
                        officialRip = item.optString("officialRip", "007999990008761821"),
                        officialKey = item.optString("officialKey", "94"),
                        officialAccountHolder = item.optString("officialAccountHolder", "سوقي DZ - الحساب المعتمد"),
                        officialProviderName = item.optString("officialProviderName", "بريدي موب / CCP"),
                        officialInstructions = item.optString("officialInstructions", "يرجى تحويل المبلغ بدقة، ثم أخذ لقطة شاشة للوصل وإرفاقها مع كتابة رقم العملية.")
                    )
                    Result.success(settings)
                } else {
                    Result.success(null)
                }
            } else {
                Result.failure(Exception("خطأ في جلب إعدادات المنصة: $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==========================================
    // 5. USERS & WALLETS
    // ==========================================
    suspend fun saveUser(u: UserEntity): Result<String> {
        val json = JSONObject().apply {
            put("id", u.id)
            put("phone", u.phone)
            put("email", u.email)
            put("name", u.name)
            put("avatarUrl", u.avatarUrl)
            put("wilaya", u.wilaya)
            put("commune", u.commune)
            put("bio", u.bio)
            put("sellerRating", u.sellerRating)
            put("reviewsCount", u.reviewsCount)
            put("adsCount", u.adsCount)
            put("createdAt", u.createdAt)
            put("isVerified", u.isVerified)
            put("verificationRequested", u.verificationRequested)
            put("isBanned", u.isBanned)
            put("role", u.role)
        }
        return upsertObject("AppUser", "id", u.id, json)
    }

    suspend fun getAllUsers(): Result<List<UserEntity>> = withContext(Dispatchers.IO) {
        try {
            val req = newRequestBuilder("classes/AppUser?limit=500").get().build()
            val resp = httpClient.newCall(req).execute()
            val body = resp.body?.string().orEmpty()
            if (resp.isSuccessful) {
                val results = JSONObject(body).optJSONArray("results") ?: JSONArray()
                val list = mutableListOf<UserEntity>()
                for (i in 0 until results.length()) {
                    val item = results.getJSONObject(i)
                    list.add(UserEntity(
                        id = item.optString("id").ifBlank { item.optString("objectId") },
                        phone = item.optString("phone"),
                        email = item.optString("email"),
                        name = item.optString("name"),
                        avatarUrl = item.optString("avatarUrl"),
                        wilaya = item.optString("wilaya"),
                        commune = item.optString("commune"),
                        bio = item.optString("bio"),
                        sellerRating = item.optDouble("sellerRating", 5.0),
                        reviewsCount = item.optInt("reviewsCount", 0),
                        adsCount = item.optInt("adsCount", 0),
                        createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                        isVerified = item.optBoolean("isVerified", false),
                        verificationRequested = item.optBoolean("verificationRequested", false),
                        isBanned = item.optBoolean("isBanned", false),
                        role = item.optString("role", "USER")
                    ))
                }
                Result.success(list)
            } else {
                Result.failure(Exception("خطأ في جلب المستخدمين: $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateUserBan(userId: String, isBanned: Boolean): Result<Unit> {
        return upsertObject("AppUser", "id", userId, JSONObject().put("isBanned", isBanned)).map { }
    }

    suspend fun updateUserVerification(userId: String, isVerified: Boolean): Result<Unit> {
        return upsertObject("AppUser", "id", userId, JSONObject().put("isVerified", isVerified)).map { }
    }

    // --- Wallets ---
    suspend fun saveWallet(w: WalletEntity): Result<String> {
        val json = JSONObject().apply {
            put("user", JSONObject().put("__type", "Pointer").put("className", "_User").put("objectId", w.userId))
            put("userId", w.userId)
            put("balanceDzd", w.balanceDzd)
            put("updatedAt", w.updatedAt)
            put("updatedAtMs", w.updatedAt)
        }
        return upsertObject("Wallet", "userId", w.userId, json)
    }

    suspend fun getWallet(userId: String): Result<WalletEntity?> = withContext(Dispatchers.IO) {
        try {
            val userPointer = JSONObject().put("__type", "Pointer").put("className", "_User").put("objectId", userId)
            val where = JSONObject().put("\$or", JSONArray()
                .put(JSONObject().put("user", userPointer))
                .put(JSONObject().put("userId", userId)))
            val encodedWhere = URLEncoder.encode(where.toString(), "UTF-8")
            val req = newRequestBuilder("classes/Wallet?where=$encodedWhere&limit=1").get().build()
            val resp = httpClient.newCall(req).execute()
            val body = resp.body?.string().orEmpty()
            if (resp.isSuccessful) {
                val results = JSONObject(body).optJSONArray("results")
                if (results != null && results.length() > 0) {
                    val item = results.getJSONObject(0)
                    Result.success(WalletEntity(
                        userId = userId,
                        balanceDzd = item.optInt("balanceDzd", 0),
                        updatedAt = item.optLong("updatedAtMs", item.optLong("updatedAt", System.currentTimeMillis()))
                    ))
                } else {
                    Result.success(null)
                }
            } else {
                Result.failure(Exception("تعذر جلب المحفظة: $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==========================================
    // 6. ORDERS MANAGEMENT
    // ==========================================
    suspend fun saveOrder(o: OrderEntity): Result<String> {
        val json = JSONObject().apply {
            put("id", o.id)
            put("orderNumber", o.orderNumber)
            put("listingId", o.listingId)
            put("listingTitle", o.listingTitle)
            put("listingImageUrl", o.listingImageUrl)
            put("sellerId", o.sellerId)
            put("sellerName", o.sellerName)
            put("sellerPhone", o.sellerPhone)
            put("buyerId", o.buyerId)
            put("buyerName", o.buyerName)
            put("buyerPhone", o.buyerPhone)
            put("buyerWilaya", o.buyerWilaya)
            put("buyerCommune", o.buyerCommune)
            put("buyerAddress", o.buyerAddress)
            put("quantity", o.quantity)
            put("unitPriceDzd", o.unitPriceDzd)
            put("deliveryFeeDzd", o.deliveryFeeDzd)
            put("totalAmountDzd", o.totalAmountDzd)
            put("paymentMethod", o.paymentMethod)
            put("isPaid", o.isPaid)
            put("status", o.status)
            put("trackingNumber", o.trackingNumber)
            put("buyerNotes", o.buyerNotes)
            put("statusNote", o.statusNote)
            put("createdAt", o.createdAt)
            put("updatedAt", o.updatedAt)
            put("deliveredAt", o.deliveredAt)
            put("cancelledAt", o.cancelledAt)
        }
        return upsertObject("MarketOrder", "id", o.id, json)
    }

    suspend fun getAllOrders(): Result<List<OrderEntity>> = withContext(Dispatchers.IO) {
        try {
            val req = newRequestBuilder("classes/MarketOrder?order=-createdAt&limit=500").get().build()
            val resp = httpClient.newCall(req).execute()
            val body = resp.body?.string().orEmpty()
            if (resp.isSuccessful) {
                val results = JSONObject(body).optJSONArray("results") ?: JSONArray()
                val list = mutableListOf<OrderEntity>()
                for (i in 0 until results.length()) {
                    val item = results.getJSONObject(i)
                    list.add(OrderEntity(
                        id = item.optString("id").ifBlank { item.optString("objectId") },
                        orderNumber = item.optString("orderNumber"),
                        listingId = item.optString("listingId"),
                        listingTitle = item.optString("listingTitle"),
                        listingImageUrl = item.optString("listingImageUrl"),
                        sellerId = item.optString("sellerId"),
                        sellerName = item.optString("sellerName"),
                        sellerPhone = item.optString("sellerPhone"),
                        buyerId = item.optString("buyerId"),
                        buyerName = item.optString("buyerName"),
                        buyerPhone = item.optString("buyerPhone"),
                        buyerWilaya = item.optString("buyerWilaya"),
                        buyerCommune = item.optString("buyerCommune"),
                        buyerAddress = item.optString("buyerAddress"),
                        quantity = item.optInt("quantity", 1),
                        unitPriceDzd = item.optInt("unitPriceDzd", 0),
                        deliveryFeeDzd = item.optInt("deliveryFeeDzd", 0),
                        totalAmountDzd = item.optInt("totalAmountDzd", 0),
                        paymentMethod = item.optString("paymentMethod", "COD"),
                        isPaid = item.optBoolean("isPaid", false),
                        status = item.optString("status", "PENDING"),
                        trackingNumber = item.optString("trackingNumber", ""),
                        buyerNotes = item.optString("buyerNotes", ""),
                        statusNote = item.optString("statusNote", ""),
                        createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = item.optLong("updatedAt", System.currentTimeMillis()),
                        deliveredAt = item.optLong("deliveredAt", 0L),
                        cancelledAt = item.optLong("cancelledAt", 0L)
                    ))
                }
                Result.success(list)
            } else {
                Result.failure(Exception("خطأ في جلب الطلبيات: $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateOrderStatus(orderId: String, status: String, statusNote: String, trackingNumber: String?): Result<Unit> {
        val json = JSONObject().apply {
            put("status", status)
            put("statusNote", statusNote)
            if (trackingNumber != null) put("trackingNumber", trackingNumber)
            put("updatedAt", System.currentTimeMillis())
        }
        return upsertObject("MarketOrder", "id", orderId, json).map { }
    }

    // ==========================================
    // 7. CHAT MESSAGES
    // ==========================================
    suspend fun saveChatMessage(m: ChatMessageEntity): Result<String> {
        val json = JSONObject().apply {
            put("id", m.id)
            put("listingId", m.listingId)
            put("senderId", m.senderId)
            put("receiverId", m.receiverId)
            put("content", m.content)
            put("timestamp", m.timestamp)
            put("isOffer", m.isOffer)
            put("offerAmountDzd", m.offerAmountDzd)
            put("offerStatus", m.offerStatus)
        }
        return upsertObject("ChatMessage", "id", m.id, json)
    }

    suspend fun getChatMessages(listingId: String, user1: String, user2: String): Result<List<ChatMessageEntity>> = withContext(Dispatchers.IO) {
        try {
            val req = newRequestBuilder("classes/ChatMessage?order=timestamp&limit=500").get().build()
            val resp = httpClient.newCall(req).execute()
            val body = resp.body?.string().orEmpty()
            if (resp.isSuccessful) {
                val results = JSONObject(body).optJSONArray("results") ?: JSONArray()
                val list = mutableListOf<ChatMessageEntity>()
                for (i in 0 until results.length()) {
                    val item = results.getJSONObject(i)
                    val sId = item.optString("senderId")
                    val rId = item.optString("receiverId")
                    val lId = item.optString("listingId")
                    if (lId == listingId && ((sId == user1 && rId == user2) || (sId == user2 && rId == user1))) {
                        list.add(ChatMessageEntity(
                            id = item.optString("id").ifBlank { item.optString("objectId") },
                            listingId = lId,
                            senderId = sId,
                            receiverId = rId,
                            content = item.optString("content"),
                            timestamp = item.optLong("timestamp", System.currentTimeMillis()),
                            isOffer = item.optBoolean("isOffer", false),
                            offerAmountDzd = item.optLong("offerAmountDzd", 0L),
                            offerStatus = item.optString("offerStatus", "NONE")
                        ))
                    }
                }
                Result.success(list)
            } else {
                Result.failure(Exception("خطأ في جلب الرسائل: $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==========================================
    // 8. REPORTS
    // ==========================================
    suspend fun saveReport(r: ReportEntity): Result<String> {
        val json = JSONObject().apply {
            put("id", r.id)
            put("reporterId", r.reporterId)
            put("reportedListingId", r.reportedListingId)
            put("reportedUserId", r.reportedUserId)
            put("reason", r.reason)
            put("comment", r.comment)
            put("timestamp", r.timestamp)
            put("status", r.status)
        }
        return upsertObject("Report", "id", r.id, json)
    }

    suspend fun getAllReports(): Result<List<ReportEntity>> = withContext(Dispatchers.IO) {
        try {
            val req = newRequestBuilder("classes/Report?order=-timestamp&limit=200").get().build()
            val resp = httpClient.newCall(req).execute()
            val body = resp.body?.string().orEmpty()
            if (resp.isSuccessful) {
                val results = JSONObject(body).optJSONArray("results") ?: JSONArray()
                val list = mutableListOf<ReportEntity>()
                for (i in 0 until results.length()) {
                    val item = results.getJSONObject(i)
                    list.add(ReportEntity(
                        id = item.optString("id").ifBlank { item.optString("objectId") },
                        reporterId = item.optString("reporterId"),
                        reportedListingId = item.optString("reportedListingId"),
                        reportedUserId = item.optString("reportedUserId"),
                        reason = item.optString("reason"),
                        comment = item.optString("comment"),
                        timestamp = item.optLong("timestamp", System.currentTimeMillis()),
                        status = item.optString("status", "PENDING")
                    ))
                }
                Result.success(list)
            } else {
                Result.failure(Exception("خطأ في جلب البلاغات: $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
