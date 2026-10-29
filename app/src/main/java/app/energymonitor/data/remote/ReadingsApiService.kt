package app.energymonitor.data.remote

import app.energymonitor.data.remote.dto.MetersResponse
import app.energymonitor.data.remote.dto.ReadingDto
import app.energymonitor.data.remote.dto.TariffDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * Опис REST API: Retrofit за цим інтерфейсом сам генерує реалізацію. suspend-методи
 * виконуються поза головним потоком; при помилці HTTP вони кидають HttpException,
 * при збої зв'язку — IOException.
 */
interface ReadingsApiService {

    @GET("readings")
    suspend fun getReadings(): List<ReadingDto>

    @POST("readings")
    suspend fun addReading(@Body body: ReadingDto): ReadingDto

    @PUT("readings/{id}")
    suspend fun updateReading(@Path("id") id: Long, @Body body: ReadingDto): ReadingDto

    @DELETE("readings/{id}")
    suspend fun deleteReading(@Path("id") id: Long)

    @GET("meters")
    suspend fun getMeters(): MetersResponse

    @GET("tariffs")
    suspend fun getTariffs(): List<TariffDto>
}
