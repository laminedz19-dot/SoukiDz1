package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.example.data.local.AppDatabase
import com.example.data.local.ChatMessageEntity
import com.example.data.local.FavoriteEntity
import com.example.data.local.ListingEntity
import com.example.data.local.PaymentOrderEntity
import com.example.data.local.PlatformSettingsEntity
import com.example.data.local.ReportEntity
import com.example.data.local.ReviewEntity
import com.example.data.local.UserEntity
import com.example.data.local.WalletEntity
import com.example.data.local.WalletTransactionEntity
import com.example.data.local.TopUpRequestEntity
import com.example.data.remote.auth.FirebaseAuthService
import com.example.data.remote.firestore.FirestorePayment
import com.example.data.remote.firestore.FirestoreService
import com.example.data.remote.firestore.FirestoreTopUpRequest
import com.example.data.remote.firestore.FirestoreWallet
import com.example.data.remote.storage.FirebaseStorageService
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.util.UUID

class MarketplaceRepository(
    private val db: AppDatabase,
    val firestoreService: FirestoreService = FirestoreService(),
    val authService: FirebaseAuthService = FirebaseAuthService(),
    val storageService: FirebaseStorageService = FirebaseStorageService()
) {

    // Listings
    fun getPublishedListings(): Flow<List<ListingEntity>> = db.listingDao().getAllPublishedListings()
    fun getAllListingsAdmin(): Flow<List<ListingEntity>> = db.listingDao().getAllListingsForAdmin()
    fun getUserListings(userId: String): Flow<List<ListingEntity>> = db.listingDao().getUserListings(userId)
    fun getListingById(id: String): Flow<ListingEntity?> = db.listingDao().getListingById(id)
    suspend fun getListingDirect(id: String): ListingEntity? = db.listingDao().getListingByIdDirect(id)

    suspend fun syncListingsFromFirestore() {
        val result = firestoreService.getPublishedListings()
        if (result.isSuccess) {
            val remoteListings = result.getOrNull().orEmpty()
            if (remoteListings.isNotEmpty()) {
                val entities = remoteListings.map { it.toListingEntity() }
                db.listingDao().insertListings(entities)
            }
        }
    }

    suspend fun saveListing(listing: ListingEntity) {
        db.listingDao().insertListing(listing)
        try {
            firestoreService.saveListing(listing)
        } catch (_: Exception) {}
    }

    suspend fun updateListingStatus(id: String, status: String, rejectionReason: String = "") {
        db.listingDao().updateListingStatus(id, status, rejectionReason)
        try {
            firestoreService.updateListingStatus(id, status, rejectionReason)
        } catch (_: Exception) {}
    }

    suspend fun deleteListing(id: String) {
        val listing = db.listingDao().getListingByIdDirect(id)
        db.listingDao().deleteListing(id)
        if (listing != null) {
            try {
                storageService.deleteAllListingImages(listing.userId, listing.id)
            } catch (_: Exception) {}
        }
    }

    suspend fun markListingAsSold(listingId: String, userId: String): Boolean =
        db.listingDao().updateStatusForOwner(listingId, userId, "SOLD") == 1

    suspend fun deleteUserData(userId: String) {
        db.withTransaction {
            db.listingDao().deleteListingsForUser(userId)
            db.walletDao().deleteTransactions(userId)
            db.walletDao().deleteWallet(userId)
            db.favoriteDao().deleteForUser(userId)
            db.userDao().deleteUser(userId)
        }
    }

    // Payment & Publishing workflow
    suspend fun processAdPayment(
        listingId: String,
        userId: String,
        packageType: String, // "STANDARD", "FEATURED", "URGENT"
        paymentMethod: String // "WALLET", "EDAHABIA", "CIB", "BARIDIMOB"
    ): Result<PaymentOrderEntity> {
        if (paymentMethod != "WALLET") {
            return Result.failure(Exception("وسيلة الدفع غير مفعّلة حتى يتم ربط مزود دفع موثوق."))
        }
        val settings = db.settingsDao().getSettingsDirect() ?: PlatformSettingsEntity()
        val fee = when (packageType) {
            "FEATURED" -> settings.featuredAdFeeDzd
            "URGENT" -> settings.urgentAdFeeDzd
            else -> settings.standardAdFeeDzd
        }

        val now = System.currentTimeMillis()
        val paymentId = "PAY_" + UUID.randomUUID().toString().take(8).uppercase()
        return db.withTransaction {
            val listing = db.listingDao().getListingByIdDirect(listingId)
                ?: return@withTransaction Result.failure(Exception("الإعلان غير موجود."))
            if (listing.userId != userId) {
                return@withTransaction Result.failure(Exception("لا تملك هذا الإعلان."))
            }
            if (db.walletDao().debitIfSufficient(userId, fee, now) != 1) {
                val balance = db.walletDao().getWalletDirect(userId)?.balanceDzd ?: 0
                return@withTransaction Result.failure(Exception("رصيد المحفظة غير كافٍ. الرصيد الحالي: $balance دج والمطلوب: $fee دج"))
            }
            db.walletDao().insertTransaction(
                WalletTransactionEntity(
                    id = "TX_" + UUID.randomUUID().toString().take(8),
                    userId = userId,
                    type = "AD_PAYMENT",
                    amount = -fee,
                    description = "دفع رسوم نشر إعلان ($packageType)",
                    referenceId = paymentId,
                    timestamp = now
                )
            )
            val order = PaymentOrderEntity(
                paymentId = paymentId,
                userId = userId,
                listingId = listingId,
                amount = fee,
                currency = "DZD",
                status = "SUCCESS",
                provider = "WALLET",
                transactionReference = paymentId,
                createdAt = now,
                completedAt = now
            )
            db.paymentDao().insertPayment(order)
            db.listingDao().insertListing(listing.copy(
                status = if (settings.autoPublishAfterPayment) "PUBLISHED" else "UNDER_REVIEW",
                isPaid = true,
                publishingFeeDzd = fee,
                packageType = packageType,
                isFeatured = packageType != "STANDARD",
                isUrgent = packageType == "URGENT"
            ))
            Result.success(order)
        }
    }

    // Wallet Operations
    fun getWallet(userId: String): Flow<WalletEntity?> = db.walletDao().getWallet(userId)
    fun getWalletTransactions(userId: String): Flow<List<WalletTransactionEntity>> = db.walletDao().getTransactions(userId)

    fun getUserTopUpRequests(userId: String): Flow<List<TopUpRequestEntity>> =
        db.topUpRequestDao().getUserRequests(userId)

    /**
     * Listens directly to Firestore topUpRequests for the current user in real-time.
     * Caches received items into Room so offline mode remains functional.
     * Emits Result.failure if Firestore listener fails, preserving accurate error states.
     */
    fun getUserTopUpRequestsFromFirestore(userId: String): Flow<Result<List<TopUpRequestEntity>>> {
        return firestoreService.getUserTopUpRequestsFlow(userId).map { result ->
            if (result.isSuccess) {
                val firestoreList = result.getOrNull().orEmpty()
                val entities = firestoreList.map { it.toTopUpRequestEntity() }
                try {
                    for (entity in entities) {
                        val localExisting = db.topUpRequestDao().getRequestById(entity.id)
                        val wasNotApproved = localExisting == null || localExisting.status != "APPROVED"
                        if (entity.status == "APPROVED" && wasNotApproved) {
                            // Idempotent credit in local Room wallet for the approved top-up request
                            val wallet = db.walletDao().getWalletDirect(userId)
                            if (wallet == null) {
                                db.walletDao().insertOrUpdateWallet(
                                    WalletEntity(userId = userId, balanceDzd = 0, updatedAt = System.currentTimeMillis())
                                )
                            }
                            db.walletDao().creditWallet(userId, entity.amountDzd, System.currentTimeMillis())
                            db.walletDao().insertTransaction(
                                WalletTransactionEntity(
                                    id = "topup_${entity.id}",
                                    userId = userId,
                                    type = "TOPUP",
                                    amount = entity.amountDzd,
                                    description = "شحن رصيد معتمد (${entity.provider})",
                                    referenceId = entity.id,
                                    timestamp = if (entity.reviewedAt > 0) entity.reviewedAt else System.currentTimeMillis()
                                )
                            )
                        }
                        db.topUpRequestDao().insertRequest(entity)
                    }
                } catch (e: Exception) {
                    android.util.Log.w("MarketplaceRepository", "Failed to cache user top-up requests in Room: ${e.message}")
                }
                Result.success(entities)
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("خطأ غير معروف أثناء مزامنة طلبات الشحن"))
            }
        }
    }

    fun getAllTopUpRequests(): Flow<List<TopUpRequestEntity>> =
        db.topUpRequestDao().getAllRequests()

    /**
     * Submits a top-up request strictly adhering to security invariants:
     * 1. Current user must be authenticated with Firebase Auth (uid != null, != "user_me").
     * 2. Allowed payment providers: BARIDIMOB, CCP, EDAHABIA, CIB.
     * 3. Minimum amount is 200 DZD.
     * 4. Must provide at least a transfer reference or receipt image.
     * 5. If receipt image is present, upload first to Firebase Storage at topUpReceipts/{uid}/{requestId}/receipt.{ext}.
     * 6. Write request to Firestore at topUpRequests/{requestId}.
     * 7. If Firestore fails, roll back and delete the uploaded Storage receipt.
     * 8. Update Room cache ONLY after Firestore succeeds.
     */
    suspend fun submitTopUpRequest(
        context: Context,
        userId: String = "",
        amount: Int,
        provider: String,
        reference: String,
        receiptImageUriString: String
    ): Result<String> = withContext(Dispatchers.IO) {
        // 1. Validate amount
        if (amount < 200) {
            return@withContext Result.failure(IllegalArgumentException("الحد الأدنى لشحن الرصيد هو 200 دج."))
        }

        // 2. Validate provider
        val allowedProviders = setOf("BARIDIMOB", "CCP", "EDAHABIA", "CIB")
        if (provider !in allowedProviders) {
            return@withContext Result.failure(IllegalArgumentException("مزود الدفع المحدد غير مدعوم ($provider). المزودون المسموح بهم فقط: BARIDIMOB, CCP, EDAHABIA, CIB."))
        }

        // 3. Validate that at least reference or receipt image is provided
        val hasReference = reference.isNotBlank()
        val hasReceipt = receiptImageUriString.isNotBlank()
        if (!hasReference && !hasReceipt) {
            return@withContext Result.failure(IllegalArgumentException("يجب إرفاق صورة وصل التحويل أو إدخال رقم مرجع العملية على الأقل."))
        }

        // 4. Resolve authenticated user or anonymous user
        var currentFirebaseUser = authService.currentUser
        if (currentFirebaseUser == null) {
            try {
                val anonResult = withTimeoutOrNull(6000L) {
                    authService.signInAnonymously()
                }
                if (anonResult?.isSuccess == true) {
                    currentFirebaseUser = anonResult.getOrNull()
                }
            } catch (t: Throwable) {
                android.util.Log.w("MarketplaceRepository", "Anonymous auth attempt: ${t.message}")
            }
        }

        if (currentFirebaseUser == null) {
            return@withContext Result.failure(
                IllegalStateException("يجب تسجيل الدخول أو إنشاء حساب أولاً لتقديم طلب شحن الرصيد لربطه بمحفظتك الرقمية.")
            )
        }

        val uid = currentFirebaseUser.uid

        // 5. Generate requestId before uploading image
        val requestId = "req_" + UUID.randomUUID().toString().replace("-", "").take(16)

        // 6. Handle receipt image upload if present
        var storedReceiptPath = ""
        if (hasReceipt) {
            val localUri = Uri.parse(receiptImageUriString)
            try {
                val uploadResult = withTimeoutOrNull(25000L) {
                    storageService.uploadTopUpReceipt(
                        context = context,
                        userId = uid,
                        requestId = requestId,
                        imageUri = localUri
                    )
                }
                if (uploadResult?.isSuccess == true) {
                    storedReceiptPath = uploadResult.getOrNull().orEmpty()
                } else {
                    val err = uploadResult?.exceptionOrNull()?.message ?: "مهلة الرفع انتهت"
                    android.util.Log.w("MarketplaceRepository", "Storage upload warning: $err")
                }
            } catch (t: Throwable) {
                android.util.Log.w("MarketplaceRepository", "Storage upload exception: ${t.message}")
            }

            if (storedReceiptPath.isBlank() && !hasReference) {
                return@withContext Result.failure(
                    IllegalStateException("تعذر رفع صورة الوصل إلى السحابة. يُرجى إدخال رقم مرجع العملية أو التحقق من الاتصال بالإنترنت.")
                )
            }
        }

        // 7. Extract user profile info for admin display
        val localUser = db.userDao().getUserByIdDirect(uid)
            ?: (if (userId.isNotBlank()) db.userDao().getUserByIdDirect(userId) else null)
            ?: db.userDao().getUserByIdDirect("user_me")
        val userName = localUser?.name?.ifBlank { null }
            ?: currentFirebaseUser.displayName?.ifBlank { null }
            ?: currentFirebaseUser.email?.substringBefore("@")
            ?: "مستخدم سوقي"
        val userPhone = localUser?.phone?.ifBlank { null }
            ?: currentFirebaseUser.phoneNumber
            ?: ""

        // 8. Sync with Firestore FIRST: verify cloud storage before confirming to user
        val firestoreReq = FirestoreTopUpRequest(
            id = requestId,
            userId = uid,
            userName = userName,
            userPhone = userPhone,
            amountDzd = amount,
            provider = provider,
            reference = reference.trim(),
            receiptImageUri = storedReceiptPath,
            status = "PENDING",
            adminNote = "",
            createdAt = null,
            reviewedAt = null
        )

        val firestoreResult = withTimeoutOrNull(15000L) {
            firestoreService.submitTopUpRequest(firestoreReq)
        }

        if (firestoreResult == null || firestoreResult.isFailure) {
            val err = firestoreResult?.exceptionOrNull()?.message ?: "انتهت مهلة الاتصال بالخادم"
            android.util.Log.e("MarketplaceRepository", "Firestore write failed: $err")
            return@withContext Result.failure(
                IllegalStateException("تعذر تسجيل طلب الشحن في الخادم السحابي: $err")
            )
        }

        // 9. Save locally in Room after cloud confirmation
        val now = System.currentTimeMillis()
        val cachedEntity = TopUpRequestEntity(
            id = requestId,
            userId = uid,
            userName = userName,
            userPhone = userPhone,
            amountDzd = amount,
            provider = provider,
            reference = reference.trim(),
            receiptImageUri = storedReceiptPath.ifBlank { receiptImageUriString },
            status = "PENDING",
            adminNote = "",
            createdAt = now,
            reviewedAt = 0L
        )

        try {
            db.topUpRequestDao().insertRequest(cachedEntity)
            if (userId.isNotBlank() && userId != uid) {
                db.topUpRequestDao().insertRequest(cachedEntity.copy(userId = userId))
            }
        } catch (dbErr: Exception) {
            android.util.Log.e("MarketplaceRepository", "Failed to cache request locally: ${dbErr.message}")
        }

        Result.success("تم إرسال طلب الشحن ووصل الدفع بنجاح إلى الإدارة! ستتم مراجعته واعتماد الرصيد قريباً.")
    }

    /**
     * Compresses a local image Uri into a compact JPEG Base64 data URI (data:image/jpeg;base64,...).
     * Keeps the file size small (<150KB) so it safely fits within Firestore's 1MB document limit
     * without needing Firebase Cloud Storage or a Blaze billing plan.
     */
    private fun compressImageToBase64DataUri(context: Context, uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (originalBitmap == null) return null

            // Scale down to max 800x800 for optimal readability and tiny storage footprint
            val maxDimension = 800
            val width = originalBitmap.width
            val height = originalBitmap.height
            val scale = if (width > height) {
                if (width > maxDimension) maxDimension.toFloat() / width else 1f
            } else {
                if (height > maxDimension) maxDimension.toFloat() / height else 1f
            }

            val scaledBitmap = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    originalBitmap,
                    (width * scale).toInt().coerceAtLeast(1),
                    (height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                originalBitmap
            }

            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
            val byteArray = outputStream.toByteArray()
            val base64 = Base64.encodeToString(byteArray, Base64.NO_WRAP)
            "data:image/jpeg;base64,$base64"
        } catch (e: Exception) {
            android.util.Log.e("MarketplaceRepository", "Error compressing receipt image: ${e.message}", e)
            null
        }
    }

    suspend fun approveTopUpRequest(requestId: String, adminNote: String = "تم التحقق من الوصل بنجاح"): Result<String> {
        val request = db.topUpRequestDao().getRequestById(requestId)
            ?: return Result.failure(Exception("طلب الشحن غير موجود."))

        if (request.status == "APPROVED") {
            return Result.failure(Exception("هذا الطلب تمت الموافقة عليه مسبقاً."))
        }

        val now = System.currentTimeMillis()
        val updated = db.walletDao().creditWallet(request.userId, request.amountDzd, now)
        if (updated == 0) {
            db.walletDao().insertOrUpdateWallet(
                WalletEntity(userId = request.userId, balanceDzd = request.amountDzd, updatedAt = now)
            )
        }

        val tx = WalletTransactionEntity(
            id = "tx_" + UUID.randomUUID().toString().replace("-", "").take(10),
            userId = request.userId,
            type = "TOPUP",
            amount = request.amountDzd,
            description = "شحن رصيد يدوي - وصل تحويل ${request.provider} (مرجع: ${request.reference.ifBlank { request.id }})",
            referenceId = request.id,
            timestamp = now
        )
        db.walletDao().insertTransaction(tx)
        db.topUpRequestDao().updateStatus(request.id, "APPROVED", adminNote, now)

        return Result.success("تمت الموافقة بنجاح وشحن ${request.amountDzd} دج إلى محفظة ${request.userName} ✓")
    }

    suspend fun rejectTopUpRequest(requestId: String, reason: String): Result<String> {
        val request = db.topUpRequestDao().getRequestById(requestId)
            ?: return Result.failure(Exception("طلب الشحن غير موجود."))

        val now = System.currentTimeMillis()
        val note = reason.ifBlank { "الوصل غير مطابق أو غير واضح" }
        db.topUpRequestDao().updateStatus(request.id, "REJECTED", note, now)
        return Result.success("تم رفض طلب الشحن.")
    }

    suspend fun topUpWallet(userId: String, amount: Int, paymentProvider: String, txReference: String? = null): Result<String> {
        return Result.failure(Exception("يرجى إرسال وصل التحويل للمراجعة اليدوية عبر شحن المحفظة."))
    }

    /**
     * Reads the specified user's balance directly from Firestore.
     * Falls back to local Room database if Firestore is unreachable or offline.
     */
    suspend fun getCurrentUserBalance(userId: String): Result<Int> {
        val firestoreResult = firestoreService.getCurrentUserBalance(userId)
        if (firestoreResult.isSuccess) {
            val balance = firestoreResult.getOrDefault(0)
            try {
                val now = System.currentTimeMillis()
                db.walletDao().insertOrUpdateWallet(WalletEntity(userId = userId, balanceDzd = balance, updatedAt = now))
            } catch (e: Exception) {
                android.util.Log.w("MarketplaceRepository", "Failed to cache Firestore balance locally: ${e.message}")
            }
            return firestoreResult
        }
        val localWallet = db.walletDao().getWalletDirect(userId)
        return Result.success(localWallet?.balanceDzd ?: 0)
    }

    /**
     * Realtime flow for observing the user's wallet document from Firestore.
     */
    fun getUserWalletFromFirestore(userId: String): Flow<Result<FirestoreWallet?>> {
        return firestoreService.getUserWalletFlow(userId)
    }

    // Users
    fun getUser(id: String): Flow<UserEntity?> = db.userDao().getUserById(id)
    suspend fun getUserDirect(id: String): UserEntity? = db.userDao().getUserByIdDirect(id)
    suspend fun findUserByPhoneOrEmail(input: String): UserEntity? = db.userDao().getUserByPhoneOrEmail(input)
    fun getAllUsers(): Flow<List<UserEntity>> = db.userDao().getAllUsers()
    suspend fun getAllUsersDirect(): List<UserEntity> = db.userDao().getAllUsersDirect()
    suspend fun saveUser(user: UserEntity) {
        // Always persist locally first. Cloud sync must not leave the registration UI
        // spinning forever when Firebase is unavailable or its rules reject the write.
        db.userDao().insertUser(user)
        try {
            kotlinx.coroutines.withTimeoutOrNull(5_000L) {
                firestoreService.saveUser(user)
            }
        } catch (_: Exception) {
            // The local profile remains available; the next sync can retry the cloud write.
        }
    }

    suspend fun createEmptyWallet(userId: String) {
        db.walletDao().insertOrUpdateWallet(
            WalletEntity(userId = userId, balanceDzd = 0, updatedAt = System.currentTimeMillis())
        )
    }

    suspend fun updateUser(user: UserEntity) {
        db.userDao().updateUser(user)
        try {
            firestoreService.saveUser(user)
        } catch (_: Exception) {}
    }
    suspend fun updateBanStatus(userId: String, banned: Boolean) = db.userDao().updateBanStatus(userId, banned)
    suspend fun updateVerification(userId: String, verified: Boolean) = db.userDao().updateVerificationStatus(userId, verified)
    suspend fun requestVerification(userId: String) = db.userDao().requestVerification(userId)
    suspend fun deleteUser(userId: String) = db.userDao().deleteUser(userId)

    // Chat & Offers
    fun getChatMessages(listingId: String, user1: String, user2: String): Flow<List<ChatMessageEntity>> =
        db.chatDao().getMessages(listingId, user1, user2)

    fun getUserConversations(userId: String): Flow<List<ChatMessageEntity>> =
        db.chatDao().getUserConversations(userId)

    suspend fun sendMessage(
        listingId: String,
        senderId: String,
        receiverId: String,
        content: String,
        isOffer: Boolean = false,
        offerAmount: Long = 0
    ) {
        val msg = ChatMessageEntity(
            id = "MSG_" + UUID.randomUUID().toString(),
            listingId = listingId,
            senderId = senderId,
            receiverId = receiverId,
            content = content,
            timestamp = System.currentTimeMillis(),
            isOffer = isOffer,
            offerAmountDzd = offerAmount,
            offerStatus = if (isOffer) "PENDING" else "NONE"
        )
        db.chatDao().insertMessage(msg)
    }

    suspend fun updateOfferStatus(messageId: String, status: String) {
        db.chatDao().updateOfferStatus(messageId, status)
    }

    // Reviews
    fun getReviews(sellerId: String): Flow<List<ReviewEntity>> = db.reviewDao().getReviewsForSeller(sellerId)

    suspend fun addReview(sellerId: String, buyerId: String, buyerName: String, listingId: String, rating: Int, comment: String): Result<String> {
        if (sellerId == buyerId) {
            return Result.failure(Exception("لا يمكنك تقييم نفسك."))
        }
        val count = db.reviewDao().hasReviewed(sellerId, buyerId, listingId)
        if (count > 0) {
            return Result.failure(Exception("لقد قمت بتقييم هذه الصفقة مسبقاً."))
        }
        val review = ReviewEntity(
            id = "REV_" + UUID.randomUUID().toString().take(8),
            sellerId = sellerId,
            buyerId = buyerId,
            buyerName = buyerName,
            listingId = listingId,
            rating = rating,
            comment = comment,
            timestamp = System.currentTimeMillis()
        )
        db.reviewDao().insertReview(review)
        return Result.success("تم إضافة تقييمك بنجاح")
    }

    // Reports
    fun getAllReports(): Flow<List<ReportEntity>> = db.reportDao().getAllReports()
    suspend fun submitReport(reporterId: String, listingId: String, userId: String, reason: String, comment: String) {
        val report = ReportEntity(
            id = "REP_" + UUID.randomUUID().toString().take(8),
            reporterId = reporterId,
            reportedListingId = listingId,
            reportedUserId = userId,
            reason = reason,
            comment = comment,
            timestamp = System.currentTimeMillis(),
            status = "PENDING"
        )
        db.reportDao().insertReport(report)
    }
    suspend fun updateReportStatus(reportId: String, status: String) = db.reportDao().updateReportStatus(reportId, status)

    // Favorites
    fun getFavorites(userId: String): Flow<List<FavoriteEntity>> = db.favoriteDao().getFavoritesForUser(userId)
    fun isFavorite(userId: String, listingId: String): Flow<Int> = db.favoriteDao().isFavorite(userId, listingId)
    suspend fun toggleFavorite(userId: String, listingId: String, currentlyFav: Boolean) {
        if (currentlyFav) {
            db.favoriteDao().deleteFavorite(userId, listingId)
        } else {
            db.favoriteDao().insertFavorite(
                FavoriteEntity(
                    id = "FAV_${userId}_$listingId",
                    userId = userId,
                    listingId = listingId,
                    notifyPriceDrop = true,
                    notifySimilar = true,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    // Platform Settings & Payments
    fun getPlatformSettings(): Flow<PlatformSettingsEntity?> = db.settingsDao().getSettings()
    suspend fun updatePlatformSettings(settings: PlatformSettingsEntity) = db.settingsDao().insertOrUpdateSettings(settings)
    fun getAllPayments(): Flow<List<PaymentOrderEntity>> = db.paymentDao().getAllPayments()
}
