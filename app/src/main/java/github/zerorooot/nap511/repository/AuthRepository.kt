package github.zerorooot.nap511.repository

import com.elvishew.xlog.XLog
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import github.zerorooot.nap511.bean.AvatarBean
import github.zerorooot.nap511.bean.Base115Response
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.NetworkClient
import github.zerorooot.nap511.util.UserSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object AuthRepository {
    private val gson = Gson()
    private val okHttpClient get() = NetworkClient.sharedOkHttpClient
    /**
     * 校验 115 登录凭证（Cookie）并同步 UserSession 与 Avatar 信息
     */
    suspend fun checkLogin(cookie: String): Result<AvatarBean> = withContext(Dispatchers.IO) {
        val timestamp = System.currentTimeMillis() / 1000
        val avatarUrl = "https://my.115.com/?ct=ajax&ac=nav&_$timestamp"
        val ua = ConfigKeyUtil.USER_AGENT

        val request = Request.Builder()
            .url(avatarUrl)
            .addHeader("Cookie", cookie)
            .addHeader("User-Agent", ua)
            .get()
            .build()

        runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("网络请求失败: HTTP ${response.code}")
                }

                val bodyStr = response.body.string()
                XLog.v("checkLogin avatarResp: $bodyStr")

                val type = object : TypeToken<Base115Response<AvatarBean>>() {}.type
                val result = gson.fromJson<Base115Response<AvatarBean>>(bodyStr, type)

                val avatarBean = result?.data ?: error("验证失败，请重试")

                avatarBean.expireString = Instant.ofEpochSecond(avatarBean.expire)
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

                UserSessionManager.updateSession(cookie, avatarBean.userId)
                SettingsRepository.saveData(ConfigKeyUtil.AVATAR_BEAN, gson.toJson(avatarBean))

                avatarBean
            }
        }
    }


}
