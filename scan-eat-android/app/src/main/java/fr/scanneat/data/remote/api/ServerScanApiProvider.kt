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
}
