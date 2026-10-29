package app.energymonitor.data.remote

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/** Збирає HTTP-клієнт і Retrofit. Interceptor-и виконуються в порядку додавання. */
object RetrofitClient {

    // Хост вигаданий: до справжньої мережі запит не доходить (його обробляє MockServerInterceptor).
    const val BASE_URL = "https://mock.energymonitor.local/"

    fun buildHttpClient(vararg interceptors: Interceptor): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
        interceptors.forEach { builder.addInterceptor(it) }
        return builder.build()
    }

    fun <T> createService(client: OkHttpClient, service: Class<T>): T =
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(service)
}
