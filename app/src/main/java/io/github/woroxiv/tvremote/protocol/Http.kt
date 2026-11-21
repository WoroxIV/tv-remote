package io.github.woroxiv.tvremote.protocol

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object Http {
    /** Plain client for local HTTP / WebSocket (Roku, LG on port 3000). */
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
    }

    /** Client trusting only the TV certificate pinned by [trust]. */
    fun pinned(trust: PinnedTrust): OkHttpClient =
        client.newBuilder()
            .sslSocketFactory(trust.sslContext().socketFactory, trust)
            .hostnameVerifier(trust.hostnameVerifier)
            .build()
}
