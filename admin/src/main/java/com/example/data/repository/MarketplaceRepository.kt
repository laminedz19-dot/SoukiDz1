package com.example.data.repository

import android.content.Context
import android.net.Uri
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
import com.example.data.remote.storage.FirebaseStorageService
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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

    fun getAllTopUpRequests(): Flow<List<TopUpRequestEntity>> =
        db.topUpRequestDao().getAllRequests()

    /**
     * Primary Source of Truth: Listens directly to all top-up requests in Firestore.
     * Caches requests in local Room and emits Result.failure on snapshot errors so the UI
     * can display Error state instead of falsely displaying Empty.
     */
    fun getAllTopUpRequestsFromFirestore(): Flow<Result<List<TopUpRequestEntity>>> {
        return firestoreService.getAllTopUpRequestsFlow().map { result ->
            if (result.isSuccess) {
                val firestoreList = result.getOrNull().orEmpty()
                val entities = firestoreList.map { it.toTopUpRequestEntity() }
                try {
                    for (entity in entities) {
                        db.topUpRequestDao().insertRequest(entity)
                    }
                } catch (e: Exception) {
                    android.util.Log.w("MarketplaceRepository", "Failed to cache requests in admin Room: ${e.message}")
                }
                Result.success(entities)
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("خطأ أثناء مزامنة طلبات الشحن من Firestore"))
            }
        }
    }

    suspend fun submitTopUpRequest(
        context: Context,
        amount: Int,
        provider: String,
        reference: String,
        receiptImageUriString: String
    ): Result<String> {
        return Result.failure(Exception("تقديم طلبات الشحن متاح حصرياً عبر تطبيق العميل."))
    }

    /**
     * Approves top-up request using a Firestore Transaction.
     * Guarantees the request is currently PENDING and cannot be re-approved or modified if already resolved.
     */
    suspend fun approveTopUpRequest(requestId: String, adminNote: String = "تم التحقق من الوصل بنجاح"): Result<String> {
        val note = adminNote.ifBlank { "تم التحقق من الوصل بنجاح" }
        val firestoreRes = firestoreService.updateTopUpStatus(requestId, "APPROVED", note)
        if (firestoreRes.isFailure) {
            val err = firestoreRes.exceptionOrNull()
            return Result.failure(err ?: Exception("فشل اعتماد طلب الشحن في Firestore"))
        }

        // Update local Room cache if document exists locally
        val now = System.currentTimeMillis()
        try {
            db.topUpRequestDao().updateStatus(requestId, "APPROVED", note, now)
        } catch (e: Exception) {
            android.util.Log.w("MarketplaceRepository", "Local cache update warning: ${e.message}")
        }

        return Result.success("تمت الموافقة على طلب الشحن وتحديث الحالة في Firestore بنجاح ✓")
    }

    /**
     * Rejects top-up request using a Firestore Transaction.
     * Guarantees the request is currently PENDING and cannot be modified if already resolved.
     */
    suspend fun rejectTopUpRequest(requestId: String, reason: String): Result<String> {
        val note = reason.ifBlank { "الوصل غير مطابق أو غير واضح" }
        val firestoreRes = firestoreService.updateTopUpStatus(requestId, "REJECTED", note)
        if (firestoreRes.isFailure) {
            val err = firestoreRes.exceptionOrNull()
            return Result.failure(err ?: Exception("فشل رفض طلب الشحن في Firestore"))
        }

        // Update local Room cache if document exists locally
        val now = System.currentTimeMillis()
        try {
            db.topUpRequestDao().updateStatus(requestId, "REJECTED", note, now)
        } catch (e: Exception) {
            android.util.Log.w("MarketplaceRepository", "Local cache update warning: ${e.message}")
        }

        return Result.success("تم رفض طلب الشحن وتحديث الحالة في Firestore بنجاح.")
    }

    suspend fun topUpWallet(userId: String, amount: Int, paymentProvider: String, txReference: String? = null): Result<String> {
        return Result.failure(Exception("يرجى إرسال وصل التحويل للمراجعة اليدوية عبر شحن المحفظة."))
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
