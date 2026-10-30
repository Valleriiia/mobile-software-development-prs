package app.energymonitor.data.auth

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    @SerializedName("login") val login: String?,
    @SerializedName("password") val password: String?
)

data class RegisterRequest(
    @SerializedName("name") val name: String?,
    @SerializedName("email") val email: String?,
    @SerializedName("password") val password: String?
)

data class TokenResponse(
    @SerializedName("access_token") val token: String?,
    @SerializedName("token_type") val tokenType: String?,
    @SerializedName("expires_in") val expiresInSeconds: Long?
)
