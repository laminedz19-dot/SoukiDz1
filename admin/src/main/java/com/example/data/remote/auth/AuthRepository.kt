package com.example.data.remote.auth

import android.util.Log
import com.parse.ParseAnonymousUtils
import com.parse.ParseRole
import com.parse.ParseUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

data class AuthUser(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val phoneNumber: String?
)

/** مصادقة الإدارة عبر ParseUser مع التحقق من Role باسم Admin. */
class AuthRepository {
    companion object {
        private const val TAG = "AdminAuthRepository"
        private const val ADMIN_ROLE = "Admin"
    }

    private fun ParseUser.toAuthUser(): AuthUser = AuthUser(
        uid = objectId.orEmpty(),
        email = email,
        displayName = getString("name"),
        phoneNumber = getString("phone")
    )

    val currentUser: AuthUser?
        get() = ParseUser.getCurrentUser()?.takeIf { it.objectId != null }?.toAuthUser()

    val currentUserId: String?
        get() = ParseUser.getCurrentUser()?.objectId

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
            Result.failure(Exception(toArabicMessage(e), e))
        }
    }

    suspend fun loginWithEmail(email: String, password: String): Result<AuthUser?> = withContext(Dispatchers.IO) {
        try {
            Result.success(ParseUser.logIn(email, password).toAuthUser())
        } catch (e: Exception) {
            Log.e(TAG, "فشل تسجيل دخول الإدارة: ${e.message}", e)
            Result.failure(Exception(toArabicMessage(e), e))
        }
    }

    suspend fun signInAnonymously(): Result<AuthUser?> = withContext(Dispatchers.IO) {
        try {
            val user = withTimeout(4_000L) {
                kotlinx.coroutines.suspendCancellableCoroutine<ParseUser> { cont ->
                    ParseAnonymousUtils.logIn { parseUser, e ->
                        if (e != null) {
                            cont.resumeWith(Result.failure(e))
                        } else if (parseUser != null) {
                            cont.resumeWith(Result.success(parseUser))
                        } else {
                            cont.resumeWith(Result.failure(Exception("تعذر تسجيل الدخول كزائر")))
                        }
                    }
                }
            }
            Result.success(user.toAuthUser())
        } catch (e: Exception) {
            Result.failure(Exception(toArabicMessage(e), e))
        }
    }

    suspend fun loginAdminWithClaims(email: String, password: String): Result<AuthUser> = withContext(Dispatchers.IO) {
        try {
            val user = ParseUser.logIn(email.trim(), password)
            if (!isAdminMember(user)) {
                ParseUser.logOut()
                return@withContext Result.failure(
                    SecurityException("الحساب (${user.email ?: email}) غير عضو في دور Admin في Back4App.")
                )
            }
            Result.success(user.toAuthUser())
        } catch (e: Exception) {
            Log.e(TAG, "فشل التحقق من حساب الإدارة: ${e.message}", e)
            Result.failure(Exception(toArabicMessage(e), e))
        }
    }

    suspend fun checkIsCurrentAdmin(): Boolean = withContext(Dispatchers.IO) {
        val user = ParseUser.getCurrentUser() ?: return@withContext false
        try {
            isAdminMember(user)
        } catch (e: Exception) {
            Log.w(TAG, "تعذر التحقق من Role Admin: ${e.message}")
            false
        }
    }

    private fun isAdminMember(user: ParseUser): Boolean {
        val roleQuery = ParseRole.getQuery()
        roleQuery.whereEqualTo("name", ADMIN_ROLE)
        val role = roleQuery.getFirst()
        val members = role.getRelation<ParseUser>("users").query
        members.whereEqualTo("objectId", user.objectId)
        val userCount: Int = members.count()
        return userCount > 0
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            ParseUser.requestPasswordReset(email)
            Result.success(Unit)
        } catch (e: Exception) {
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
            101 -> "البريد أو كلمة المرور غير صحيحة"
            125 -> "صيغة البريد الإلكتروني غير صحيحة"
            150 -> "لا يمكن تنفيذ العملية دون اتصال بالخادم"
            200, 202 -> "بيانات الحساب موجودة من قبل"
            203 -> "البريد الإلكتروني مستخدم من قبل"
            205 -> "لا يوجد حساب بهذا البريد الإلكتروني"
            else -> error.message?.takeIf { it.isNotBlank() } ?: "تعذر تنفيذ عملية المصادقة"
        }
    }
}
