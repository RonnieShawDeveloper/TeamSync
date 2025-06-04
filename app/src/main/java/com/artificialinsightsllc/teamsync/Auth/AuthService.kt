package com.artificialinsightsllc.teamsync.Auth

import com.google.firebase.auth.*
import kotlinx.coroutines.tasks.await

class AuthService(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    suspend fun login(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            Result.success(result.user!!)
        } catch (e: FirebaseAuthException) {
            Result.failure(Exception(getFriendlyErrorMessage(e)))
        } catch (e: Exception) {
            Result.failure(Exception("An unexpected error occurred. Please try again later."))
        }
    }

    fun logout() {
        auth.signOut()
    }

    fun getCurrentUser(): FirebaseUser? = auth.currentUser

    private fun getFriendlyErrorMessage(e: FirebaseAuthException): String {
        return when (e.errorCode) {
            "ERROR_INVALID_CUSTOM_TOKEN" -> "The custom token format is incorrect. Please contact support."
            "ERROR_CUSTOM_TOKEN_MISMATCH" -> "The custom token does not match the expected format."
            "ERROR_INVALID_CREDENTIAL" -> "The credentials provided are invalid. Please try again."
            "ERROR_INVALID_EMAIL" -> "The email address is not valid. Please check it."
            "ERROR_WRONG_PASSWORD" -> "The password is incorrect. Please try again."
            "ERROR_USER_MISMATCH" -> "The user credentials do not match the expected account."
            "ERROR_REQUIRES_RECENT_LOGIN" -> "Please re-authenticate and try again."
            "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" -> "An account already exists with the same email but different sign-in credentials."
            "ERROR_EMAIL_ALREADY_IN_USE" -> "This email is already in use. Try logging in or use a different email."
            "ERROR_CREDENTIAL_ALREADY_IN_USE" -> "This credential is already associated with a different user."
            "ERROR_USER_DISABLED" -> "This account has been disabled. Please contact support."
            "ERROR_USER_TOKEN_EXPIRED" -> "Your session has expired. Please log in again."
            "ERROR_USER_NOT_FOUND" -> "No account found with this email."
            "ERROR_INVALID_USER_TOKEN" -> "Your session is no longer valid. Please log in again."
            "ERROR_OPERATION_NOT_ALLOWED" -> "This operation is not allowed. Please contact support."
            "ERROR_WEAK_PASSWORD" -> "The password is too weak. Please choose a stronger one."
            else -> "Authentication failed: ${e.localizedMessage}"
        }
    }
}
