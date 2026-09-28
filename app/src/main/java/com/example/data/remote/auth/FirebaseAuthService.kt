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
