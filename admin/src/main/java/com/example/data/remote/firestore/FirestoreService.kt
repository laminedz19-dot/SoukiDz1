package com.example.data.remote.firestore

import android.util.Log
import com.example.data.local.ListingEntity
import com.example.data.local.UserEntity
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.tasks.await

class FirestoreService(
    customFirestore: FirebaseFirestore? = null
) {
    companion object {
        private const val TAG = "FirestoreService"
        const val COLLECTION_USERS = "users"
        const val COLLECTION_LISTINGS = "listings"
        const val COLLECTION_PAYMENTS = "payments"
        const val COLLECTION_SETTINGS = "settings"
    }

    private val firestore: FirebaseFirestore? = customFirestore ?: run {
        try {
            FirebaseApp.getInstance()
            FirebaseFirestore.getInstance()
        } catch (t: Throwable) {
            Log.w(TAG, "Firebase is not configured or initialized (${t.message}). Souqi DZ is operating seamlessly in local offline/Room database mode.")
            null
        }
    }

    // --- USERS COLLECTION ---

    suspend fun saveUser(user: UserEntity): Result<Unit> {
        val db = firestore ?: return Result.success(Unit)
        return try {
            val firestoreUser = FirestoreUser.fromUserEntity(user)
            db.collection(COLLECTION_USERS)
                .document(user.id)
                .set(firestoreUser)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving user to Firestore: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getUser(userId: String): Result<FirestoreUser?> {
        val db = firestore ?: return Result.success(null)
        return try {
            val snapshot = db.collection(COLLECTION_USERS)
                .document(userId)
                .get()
                .await()
            Result.success(snapshot.toObject(FirestoreUser::class.java))
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching user from Firestore: ${e.message}", e)
            Result.failure(e)
        }
    }

    // --- LISTINGS COLLECTION ---

    suspend fun saveListing(listing: ListingEntity): Result<Unit> {
        val db = firestore ?: return Result.success(Unit)
        return try {
            val firestoreListing = FirestoreListing.fromListingEntity(listing)
            db.collection(COLLECTION_LISTINGS)
                .document(listing.id)
                .set(firestoreListing)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving listing to Firestore: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun updateListingStatus(listingId: String, status: String, rejectionReason: String = ""): Result<Unit> {
        val db = firestore ?: return Result.success(Unit)
        return try {
            val updates = mapOf(
                "status" to status,
                "rejectionReason" to rejectionReason
            )
            db.collection(COLLECTION_LISTINGS)
                .document(listingId)
                .update(updates)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating listing status in Firestore: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getPublishedListings(): Result<List<FirestoreListing>> {
        val db = firestore ?: return Result.success(emptyList())
        return try {
            val snapshot = db.collection(COLLECTION_LISTINGS)
                .whereEqualTo("status", "PUBLISHED")
                .get()
                .await()
            val listings = snapshot.documents.mapNotNull { it.toObject(FirestoreListing::class.java) }
            Result.success(listings)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching published listings: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun getPublishedListingsFlow(): Flow<List<FirestoreListing>> {
        val db = firestore ?: return emptyFlow()
        return callbackFlow {
            val listenerRegistration = db.collection(COLLECTION_LISTINGS)
                .whereEqualTo("status", "PUBLISHED")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Listen failed: ${error.message}", error)
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val listings = snapshot.documents.mapNotNull { it.toObject(FirestoreListing::class.java) }
                        trySend(listings)
                    }
                }
            awaitClose { listenerRegistration.remove() }
        }
    }

    // --- PAYMENTS COLLECTION ---

    suspend fun recordPayment(payment: FirestorePayment): Result<Unit> {
        val db = firestore ?: return Result.success(Unit)
        return try {
            db.collection(COLLECTION_PAYMENTS)
                .document(payment.paymentId)
                .set(payment)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error recording payment in Firestore: ${e.message}", e)
            Result.failure(e)
        }
    }
}
