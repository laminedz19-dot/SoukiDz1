package com.example.data.remote.firestore

import com.example.data.local.ListingEntity
import com.example.data.local.TopUpRequestEntity
import com.example.data.local.UserEntity
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Firestore data model for the "users" collection.
 * Includes no-arg default constructor required by Firestore.
 */
@IgnoreExtraProperties
data class FirestoreUser(
    @DocumentId
    val id: String = "",
    val phone: String = "",
    val email: String = "",
    val name: String = "",
    val avatarUrl: String = "",
    val wilaya: String = "",
    val commune: String = "",
    val bio: String = "",
    val sellerRating: Double = 5.0,
    val reviewsCount: Int = 0,
    val adsCount: Int = 0,
    val isVerified: Boolean = false,
    val verificationRequested: Boolean = false,
    val isBanned: Boolean = false,
    val role: String = "USER", // "USER", "ADMIN"
    @ServerTimestamp
    val createdAt: Date? = null
) {
    fun toUserEntity(): UserEntity {
        return UserEntity(
            id = id,
            phone = phone,
            email = email,
            name = name,
            avatarUrl = avatarUrl,
            wilaya = wilaya,
            commune = commune,
            bio = bio,
            sellerRating = sellerRating,
            reviewsCount = reviewsCount,
            adsCount = adsCount,
            createdAt = createdAt?.time ?: System.currentTimeMillis(),
            isVerified = isVerified,
            verificationRequested = verificationRequested,
            isBanned = isBanned,
            role = role
        )
    }

    companion object {
        fun fromUserEntity(entity: UserEntity): FirestoreUser {
            return FirestoreUser(
                id = entity.id,
                phone = entity.phone,
                email = entity.email,
                name = entity.name,
                avatarUrl = entity.avatarUrl,
                wilaya = entity.wilaya,
                commune = entity.commune,
                bio = entity.bio,
                sellerRating = entity.sellerRating,
                reviewsCount = entity.reviewsCount,
                adsCount = entity.adsCount,
                isVerified = entity.isVerified,
                verificationRequested = entity.verificationRequested,
                isBanned = entity.isBanned,
                role = entity.role,
                createdAt = Date(entity.createdAt)
            )
        }
    }
}

/**
 * Firestore data model for the "listings" collection.
 * Includes no-arg default constructor required by Firestore.
 */
@IgnoreExtraProperties
data class FirestoreListing(
    @DocumentId
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val userPhone: String = "",
    val isPhoneVisible: Boolean = true,
    val title: String = "",
    val description: String = "",
    val categoryId: String = "",
    val categoryNameAr: String = "",
    val subcategory: String = "",
    val priceDzd: Long = 0L,
    val isNegotiable: Boolean = false,
    val condition: String = "USED", // "NEW", "LIKE_NEW", "USED"
    val wilayaCode: Int = 16,
    val wilayaName: String = "الجزائر العاصمة",
    val commune: String = "حيدرة",
    val images: List<String> = emptyList(),
    val videoUrl: String = "",
    val status: String = "PAYMENT_PENDING", // "DRAFT", "PAYMENT_PENDING", "PAYMENT_FAILED", "PAID", "UNDER_REVIEW", "PUBLISHED", "REJECTED", "EXPIRED", "SOLD"
    val rejectionReason: String = "",
    val packageType: String = "STANDARD", // "STANDARD", "FEATURED", "URGENT"
    val publishingFeeDzd: Int = 100,
    val isPaid: Boolean = false,
    val isFeatured: Boolean = false,
    val isUrgent: Boolean = false,
    val viewsCount: Int = 0,
    @ServerTimestamp
    val createdAt: Date? = null,
    val expiresAt: Long = 0L
) {
    fun toListingEntity(): ListingEntity {
        return ListingEntity(
            id = id,
            userId = userId,
            userName = userName,
            userPhone = userPhone,
            isPhoneVisible = isPhoneVisible,
            title = title,
            description = description,
            categoryId = categoryId,
            categoryNameAr = categoryNameAr,
            subcategory = subcategory,
            priceDzd = priceDzd,
            isNegotiable = isNegotiable,
            condition = condition,
            wilayaCode = wilayaCode,
            wilayaName = wilayaName,
            commune = commune,
            imagesJson = images.joinToString(","),
            videoUrl = videoUrl,
            status = status,
            rejectionReason = rejectionReason,
            packageType = packageType,
            publishingFeeDzd = publishingFeeDzd,
            isPaid = isPaid,
            isFeatured = isFeatured,
            isUrgent = isUrgent,
            viewsCount = viewsCount,
            createdAt = createdAt?.time ?: System.currentTimeMillis(),
            expiresAt = expiresAt
        )
    }

    companion object {
        fun fromListingEntity(entity: ListingEntity): FirestoreListing {
            val imgList = entity.imagesJson.split(",").map { it.trim() }.filter { it.isNotBlank() }
            return FirestoreListing(
                id = entity.id,
                userId = entity.userId,
                userName = entity.userName,
                userPhone = entity.userPhone,
                isPhoneVisible = entity.isPhoneVisible,
                title = entity.title,
                description = entity.description,
                categoryId = entity.categoryId,
                categoryNameAr = entity.categoryNameAr,
                subcategory = entity.subcategory,
                priceDzd = entity.priceDzd,
                isNegotiable = entity.isNegotiable,
                condition = entity.condition,
                wilayaCode = entity.wilayaCode,
                wilayaName = entity.wilayaName,
                commune = entity.commune,
                images = imgList,
                videoUrl = entity.videoUrl,
                status = entity.status,
                rejectionReason = entity.rejectionReason,
                packageType = entity.packageType,
                publishingFeeDzd = entity.publishingFeeDzd,
                isPaid = entity.isPaid,
                isFeatured = entity.isFeatured,
                isUrgent = entity.isUrgent,
                viewsCount = entity.viewsCount,
                createdAt = Date(entity.createdAt),
                expiresAt = entity.expiresAt
            )
        }
    }
}

/**
 * Firestore data model for the "payments" collection.
 */
@IgnoreExtraProperties
data class FirestorePayment(
    @DocumentId
    val paymentId: String = "",
    val userId: String = "",
    val listingId: String = "",
    val amount: Int = 0,
    val currency: String = "DZD",
    val status: String = "SUCCESS",
    val provider: String = "EDAHABIA", // "EDAHABIA", "CIB", "BARIDIMOB", "WALLET"
    val transactionReference: String = "",
    @ServerTimestamp
    val createdAt: Date? = null,
    val completedAt: Long = 0L
)

/**
 * Firestore data model for the "topUpRequests" collection.
 * Required fields: id, userId, userName, userPhone, amountDzd, provider,
 * reference, receiptImageUri, status, adminNote, createdAt, reviewedAt.
 */
@IgnoreExtraProperties
data class FirestoreTopUpRequest(
    @DocumentId
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val userPhone: String = "",
    val amountDzd: Int = 0,
    val provider: String = "BARIDIMOB", // "BARIDIMOB", "CCP", "EDAHABIA", "CIB"
    val reference: String = "",
    val receiptImageUri: String = "",
    val status: String = "PENDING", // "PENDING", "APPROVED", "REJECTED"
    val adminNote: String = "",
    @ServerTimestamp
    val createdAt: Date? = null,
    val reviewedAt: Date? = null
) {
    fun toTopUpRequestEntity(): TopUpRequestEntity {
        return TopUpRequestEntity(
            id = id,
            userId = userId,
            userName = userName,
            userPhone = userPhone,
            amountDzd = amountDzd,
            provider = provider,
            reference = reference,
            receiptImageUri = receiptImageUri,
            status = status,
            adminNote = adminNote,
            createdAt = createdAt?.time ?: System.currentTimeMillis(),
            reviewedAt = reviewedAt?.time ?: 0L
        )
    }

    companion object {
        fun fromTopUpRequestEntity(entity: TopUpRequestEntity): FirestoreTopUpRequest {
            return FirestoreTopUpRequest(
                id = entity.id,
                userId = entity.userId,
                userName = entity.userName,
                userPhone = entity.userPhone,
                amountDzd = entity.amountDzd,
                provider = entity.provider,
                reference = entity.reference,
                receiptImageUri = entity.receiptImageUri,
                status = entity.status,
                adminNote = entity.adminNote,
                createdAt = if (entity.createdAt > 0) Date(entity.createdAt) else null,
                reviewedAt = if (entity.reviewedAt > 0) Date(entity.reviewedAt) else null
            )
        }
    }
}
