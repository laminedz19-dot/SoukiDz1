package com.example.data.remote.auth

import android.util.Log
import com.parse.ParseAnonymousUtils
import com.parse.LogInCallback
import com.parse.ParseException
import com.parse.ParseUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** بيانات جلسة المصادقة التي تحتاجها طبقات التطبيق دون ربطها بمزوّد محدد. */
data class AuthUser(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val phoneNumber: String?
)

/** مستودع مصادقة مستقل عن المزوّد، ويستخدم ParseUser في Back4App. */
class AuthRepository {
    companion object {
        private const val TAG = "AuthRepository"
    }

    private fun ParseUser.toAuthUser(): AuthUser = AuthUser(
        uid = objectId.orEmpty(),
        email = email,
        displayName = getString("name"),
        phoneNumber = getString("phone")
    )

    val currentUser: AuthUser?
        get() = runCatching { ParseUser.getCurrentUser() }
            .getOrNull()?.takeIf { !it.isDataAvailable || it.objectId != null }?.toAuthUser()

    val currentUserId: String?
        get() = runCatching { ParseUser.getCurrentUser()?.objectId }.getOrNull()

    suspend fun registerWithEmail(email: String, password: String): Result<AuthUser?> = withContext(Dispatchers.IO) {
        try {
            val user = ParseUser().apply {
                username = email
                setEmail(email)
                setPassword(password)
            }
            user.signUp()
            Result.success(user.toAuthUser())
        } catch (e: Exception) {
            Log.e(TAG, "فشل إنشاء الحساب: ${e.message}", e)
            Result.failure(Exception(toArabicMessage(e), e))
        }
    }

    suspend fun loginWithEmail(email: String, password: String): Result<AuthUser?> = withContext(Dispatchers.IO) {
        try {
            Result.success(ParseUser.logIn(email, password).toAuthUser())
        } catch (e: Exception) {
            Log.e(TAG, "فشل تسجيل الدخول: ${e.message}", e)
            Result.failure(Exception(toArabicMessage(e), e))
        }
    }

    suspend fun updateCurrentUserProfile(
        name: String,
        phone: String,
        wilaya: String,
        commune: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val user = ParseUser.getCurrentUser()
                ?: return@withContext Result.failure(SecurityException("يجب تسجيل الدخول أولاً."))
            user.put("name", name.trim())
            user.put("phone", phone.trim())
            user.put("wilaya", wilaya.trim())
            user.put("commune", commune.trim())
            user.save()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "فشل حفظ ملف المستخدم في Back4App: ${e.message}", e)
            Result.failure(Exception(toArabicMessage(e), e))
        }
    }

    suspend fun signInAnonymously(): Result<AuthUser?> = withContext(Dispatchers.IO) {
        try {
            val user = withTimeout(4_000L) {
                suspendCancellableCoroutine { continuation ->
                    ParseAnonymousUtils.logIn(object : LogInCallback {
                        override fun done(user: ParseUser?, error: ParseException?) {
                            if (!continuation.isActive) return
                            when {
                                error != null -> continuation.resumeWithException(error)
                                user != null -> continuation.resume(user)
                                else -> continuation.resumeWithException(IllegalStateException("لم تُرجع خدمة الحساب مستخدماً."))
                            }
                        }
                    })
                }
            }
            Result.success(user.toAuthUser())
        } catch (e: Exception) {
            Log.e(TAG, "فشل الدخول كزائر: ${e.message}", e)
            Result.failure(Exception(toArabicMessage(e), e))
        }
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            ParseUser.requestPasswordReset(email)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "فشل إرسال استعادة كلمة المرور: ${e.message}", e)
            Result.failure(Exception(toArabicMessage(e), e))
        }
    }

    fun signOut() {
        try {
            ParseUser.logOut()
        } catch (e: Exception) {
            Log.e(TAG, "فشل تسجيل الخروج: ${e.message}", e)
        }
    }

    private fun toArabicMessage(error: Exception): String {
        val code = (error as? com.parse.ParseException)?.code
        return when (code) {
            202 -> "اسم المستخدم مستخدم من قبل"
            203 -> "البريد الإلكتروني مستخدم من قبل"
            101 -> "البريد أو كلمة المرور غير صحيحة"
            125 -> "صيغة البريد الإلكتروني غير صحيحة"
            200 -> "بيانات الحساب موجودة من قبل"
            205 -> "لا يوجد حساب بهذا البريد الإلكتروني"
            150 -> "لا يمكن تنفيذ العملية دون اتصال بالخادم"
            else -> error.message?.takeIf { it.isNotBlank() } ?: "تعذر تنفيذ عملية المصادقة"
        }
    }
}
