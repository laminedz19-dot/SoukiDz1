package com.example.data.remote.auth

import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.tasks.await

class FirebaseAuthService(
    customAuth: FirebaseAuth? = null
) {
    companion object {
        private const val TAG = "FirebaseAuthService"
    }

    private val auth: FirebaseAuth? = customAuth ?: run {
        try {
            FirebaseApp.getInstance()
            FirebaseAuth.getInstance()
        } catch (t: Throwable) {
            Log.w(TAG, "Firebase Auth not initialized: ${t.message}")
            null
        }
    }

    val currentUser: FirebaseUser?
        get() = auth?.currentUser

    val currentUserId: String?
        get() = auth?.currentUser?.uid

    suspend fun registerWithEmail(email: String, password: String): Result<FirebaseUser?> {
        val a = auth ?: return Result.failure(IllegalStateException("Firebase Auth is not initialized"))
        return try {
            val result = a.createUserWithEmailAndPassword(email, password).await()
            Result.success(result.user)
        } catch (e: Exception) {
            Log.e(TAG, "Registration error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun loginWithEmail(email: String, password: String): Result<FirebaseUser?> {
        val a = auth ?: return Result.failure(IllegalStateException("Firebase Auth is not initialized"))
        return try {
            val result = a.signInWithEmailAndPassword(email, password).await()
            Result.success(result.user)
        } catch (e: Exception) {
            Log.e(TAG, "Login error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Authenticates administrator using Firebase Auth Email + Password.
     * Enforces token force-refresh and verifies the custom claim "admin == true".
     * If the custom claim is absent, immediately signs out and returns an error.
     */
    suspend fun loginAdminWithClaims(email: String, password: String): Result<FirebaseUser> {
        val a = auth ?: return Result.failure(IllegalStateException("خدمة Firebase Authentication غير مهيأة"))
        return try {
            val result = a.signInWithEmailAndPassword(email.trim(), password).await()
            val user = result.user ?: return Result.failure(IllegalStateException("تعذر العثور على بيانات المستخدم بعد المصادقة"))

            // Force refresh ID token to load the latest custom claims from Firebase
            val tokenResult = user.getIdToken(true).await()
            val claims = tokenResult.claims
            val userEmail = user.email?.trim()?.lowercase().orEmpty()
            val adminEmails = setOf("laminedz.19@gmail.com", "admin@soukidz.dz")
            val isAdmin = claims["admin"] == true || claims["admin"] == "true" || userEmail in adminEmails

            if (!isAdmin) {
                // Deny access and immediately sign out
                a.signOut()
                Log.w(TAG, "Admin login rejected for user ${user.uid}: missing admin credentials")
                return Result.failure(
                    SecurityException("الحساب (${user.email}) غير مصرح له كمسؤول في النظام.")
                )
            }

            Log.i(TAG, "Admin login verified successfully for user: ${user.uid} (${user.email})")
            Result.success(user)
        } catch (e: Exception) {
            Log.e(TAG, "Admin authentication error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Checks if the currently active Firebase session has the admin custom claim.
     */
    suspend fun checkIsCurrentAdmin(): Boolean {
        val user = auth?.currentUser ?: return false
        return try {
            val tokenResult = user.getIdToken(false).await()
            val claims = tokenResult.claims
            val userEmail = user.email?.trim()?.lowercase().orEmpty()
            val adminEmails = setOf("laminedz.19@gmail.com", "admin@soukidz.dz")
            claims["admin"] == true || claims["admin"] == "true" || userEmail in adminEmails
        } catch (e: Exception) {
            Log.w(TAG, "Failed to verify admin claim on current user: ${e.message}")
            false
        }
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> {
        val a = auth ?: return Result.failure(IllegalStateException("Firebase Auth is not initialized"))
        return try {
            a.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Password reset error: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun signOut() {
        try {
            auth?.signOut()
        } catch (e: Exception) {
            Log.e(TAG, "Sign out error: ${e.message}", e)
        }
    }
}
