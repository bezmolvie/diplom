package com.example.spotan
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import net.openid.appauth.*
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*
class SpotifyAuthManager(context: Context) {
    // Экземпляр AppAuth-сервиса
    private val authService: AuthorizationService
    private val prefs = context.getSharedPreferences("spotify_auth", Context.MODE_PRIVATE)
    // Состояние авторизации (хранит токены и прочую информацию)
    private var authState: AuthState? = null

    // Верификатор и челендж для PKCE
    private var codeVerifier: String? = null
    init {
        // Настраиваем конечные точки авторизации и получения токена
        val serviceConfig = AuthorizationServiceConfiguration(
            Uri.parse(SPOTIFY_AUTH_ENDPOINT), // Эндпоинт для авторизации
            Uri.parse(SPOTIFY_TOKEN_ENDPOINT) // Эндпоинт для получения токена
        )
        // Инициализируем AuthState с этой конфигурацией
        authState = AuthState(serviceConfig)
        // Создаём сервис авторизации
        authService = AuthorizationService(context)

    }
    /**
     * Создаём Intent для старта запроса авторизации (открытие браузера/Custom Tab).
     * Вы можете вызвать этот метод из Activity и запустить результат через startActivityForResult().
     */
    fun getAuthorizationRequestIntent(): Intent {
        // Генерируем PKCE (верификатор и челендж)
        codeVerifier = generateCodeVerifier()
        val codeChallenge = generateCodeChallenge(codeVerifier!!)

        // Создаём запрос авторизации
        val request = AuthorizationRequest.Builder(
            authState!!.authorizationServiceConfiguration!!,
            CLIENT_ID,
            ResponseTypeValues.CODE,
            Uri.parse(REDIRECT_URI)
        )
            // Запрашиваем нужные Spotify-права (scopes)
            .setScopes("user-read-email","user-top-read", "playlist-read-private","user-read-playback-state","user-read-recently-played")
            // Добавляем PKCE
            .setCodeVerifier(codeVerifier!!, codeChallenge, CODE_CHALLENGE_METHOD)
            .setAdditionalParameters(mapOf("show_dialog" to "true"))
            .build()

        // Получаем Intent, который откроет браузер/Custom Tab
        return authService.getAuthorizationRequestIntent(request)
    }
    /**
     * Обработка ответа из onActivityResult() (или через новую API ActivityResultContracts).
     * Здесь мы получаем код авторизации и обмениваем его на токен.
     */
    fun handleAuthorizationResponse(data: Intent, callback: (success: Boolean, error: String?) -> Unit) {
        val resp = AuthorizationResponse.fromIntent(data)
        val ex = AuthorizationException.fromIntent(data)

        // Обновляем AuthState, чтобы он знал про полученный код / ошибку
        authState?.update(resp, ex)

        if (resp != null) {
            // Если есть код авторизации, обмениваем его на Access Token
            performTokenRequest(resp) { success, error ->
                if (success) {
                    // *** Здесь самое место для лога ***
                    Log.d("AuthManager", "Access token scopes: ${authState?.scope}")
                    Log.d("AuthManager", "Access token: ${authState?.accessToken}")
                    // или более детально
                    val jsonConfig = authState?.authorizationServiceConfiguration?.toJsonString()
                    Log.d("AuthManager", "Current scopes in config: $jsonConfig")
                }
                callback(success, error)
            }
        } else if (ex != null) {
            // Если пришла ошибка
            callback(false, ex.errorDescription)
        }
    }
    /**
     * Выполняет запрос на обмен кода авторизации (authorization code) на Access Token.
     */
    private fun performTokenRequest(
        response: AuthorizationResponse,
        callback: (success: Boolean, error: String?) -> Unit
    ) {
        // Создаём Token Request
        val tokenRequest = response.createTokenExchangeRequest()
        // Запускаем запрос к Spotify
        authService.performTokenRequest(tokenRequest) { tokenResp, ex ->
            if (tokenResp != null) {
                // Обновляем AuthState, чтобы сохранить токен и т.д.
                authState?.update(tokenResp, ex)
                saveAuthState()
                Log.d("AuthManager", "Access token: ${authState?.accessToken}")
                callback(true, null)
            } else {
                callback(false, ex?.errorDescription)
            }
        }
    }
    /**
     * Возвращаем актуальный Access Token (если он получен).
     * Можно также проверять, не просрочен ли токен, и вызывать refresh.
     */
    fun getAccessToken(): String? {
        return authState?.accessToken
    }
    fun logout() {
        // Очистить состояние авторизации
     //   authState = null
        // Если вы где-то сохраняли AuthState в SharedPreferences – тоже удалите
    }
    // Сохраняем текущее состояние авторизации в SharedPreferences
    private fun saveAuthState() {
        val json = authState?.jsonSerializeString() ?: return
        prefs.edit().putString("auth_state", json).apply()
    }

    // region PKCE

    companion object {
        private const val CLIENT_ID = "74b9f53b4c244eaf87937a6bb925800e"
        private const val REDIRECT_URI = "myapp://callback"

        // Spotify эндпоинты
        private const val SPOTIFY_AUTH_ENDPOINT = "https://accounts.spotify.com/authorize"
        private const val SPOTIFY_TOKEN_ENDPOINT = "https://accounts.spotify.com/api/token"

        // PKCE method
        private const val CODE_CHALLENGE_METHOD = "S256"

        /**
         * Генерация "верификатора" (codeVerifier) – случайная строка.
         */
        fun generateCodeVerifier(): String {
            val secureRandom = SecureRandom()
            val codeVerifierBytes = ByteArray(32)
            secureRandom.nextBytes(codeVerifierBytes)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(codeVerifierBytes)
        }

        /**
         * Генерация "челенджа" (codeChallenge) из верификатора по алгоритму SHA-256.
         */
        fun generateCodeChallenge(verifier: String): String {
            val bytes = verifier.toByteArray(Charsets.US_ASCII)
            val messageDigest = MessageDigest.getInstance("SHA-256")
            messageDigest.update(bytes, 0, bytes.size)
            val digest = messageDigest.digest()
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
        }
    }
    // endregion

}