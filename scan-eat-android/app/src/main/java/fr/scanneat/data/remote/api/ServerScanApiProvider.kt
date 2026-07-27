package fr.scanneat.data.remote.api

import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Caches one Retrofit-built ServerScanApi per base URL — server mode's URL can
 * change at runtime (Settings), so a plain @Inject-provided singleton would pin
 * whatever URL was configured at first use. Shared by ScanRepository and
 * RecipeRepository — both need the same server client for their respective
 * ApiMode.SERVER calls; extracted here instead of each repository caching its
 * own Retrofit instance for the same URL independently.
 */
@Singleton
class ServerScanApiProvider @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val moshi: Moshi,
) {
    @Volatile private var api: ServerScanApi? = null
    @Volatile private var url: String = ""

    fun get(baseUrl: String): ServerScanApi {
        // Server mode's realistic deployment is a self-hosted backend on the
        // user's own LAN (a home server, a laptop running the companion server
        // app, the Android emulator's host-loopback address) - plain http:// is
        // the normal case there, not an edge case, so only reject http:// when
        // the host looks like a public internet address where cleartext would
        // actually leak credentials over the open network.
        require(baseUrl.startsWith("https://") || isPrivateOrLocalHttp(baseUrl)) {
            "Server URL must use https:// for non-local hosts: $baseUrl"
        }
        val normUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        if (api == null || url != normUrl) {
            api = Retrofit.Builder()
                .baseUrl(normUrl)
                .client(okHttpClient)          // safe: directly injected OkHttpClient
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(ServerScanApi::class.java)
            url = normUrl
        }
        return api!!
    }

    private fun isPrivateOrLocalHttp(baseUrl: String): Boolean {
        if (!baseUrl.startsWith("http://")) return false
        val host = baseUrl.removePrefix("http://").substringBefore('/').substringBefore(':')
        if (host == "localhost" || host == "127.0.0.1" || host == "10.0.2.2" || host.endsWith(".local")) return true
        // Each octet bounded to 0-255 (not just \d{1,3}) so a malformed address
        // like 10.999.999.999 isn't misclassified as a valid private-range host.
        val octet = """(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)"""
        return host.matches(Regex("""10(\.$octet){3}""")) ||
            host.matches(Regex("""192\.168(\.$octet){2}""")) ||
            host.matches(Regex("""172\.(1[6-9]|2\d|3[01])(\.$octet){2}"""))
    }
}
