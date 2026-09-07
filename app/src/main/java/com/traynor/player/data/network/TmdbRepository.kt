package com.traynor.player.data.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

@JsonClass(generateAdapter = true) data class TmdbSearchResponse(val results: List<TmdbMovieMatch> = emptyList())
@JsonClass(generateAdapter = true) data class TmdbMovieMatch(val id: Int, val title: String? = null, @Json(name = "release_date") val releaseDate: String? = null)
@JsonClass(generateAdapter = true) data class TmdbProvidersResponse(val results: Map<String, TmdbCountryProviders> = emptyMap())
@JsonClass(generateAdapter = true) data class TmdbCountryProviders(val link: String? = null, val flatrate: List<TmdbProvider> = emptyList(), val rent: List<TmdbProvider> = emptyList(), val buy: List<TmdbProvider> = emptyList())
@JsonClass(generateAdapter = true) data class TmdbProvider(@Json(name = "provider_name") val name: String)

interface TmdbApi {
    @GET("3/search/movie") suspend fun searchMovie(@Query("api_key") key: String?, @Header("Authorization") authorization: String?, @Query("query") title: String, @Query("year") year: String? = null): TmdbSearchResponse
    @GET("3/movie/{id}/watch/providers") suspend fun providers(@Path("id") id: Int, @Query("api_key") key: String?, @Header("Authorization") authorization: String?): TmdbProvidersResponse
}

data class UkAvailability(val subscriptions: List<String> = emptyList(), val rent: List<String> = emptyList(), val buy: List<String> = emptyList(), val link: String? = null)

class TmdbRepository(client: OkHttpClient) {
    private val api = Retrofit.Builder().baseUrl("https://api.themoviedb.org/").client(client)
        .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().build())).build().create(TmdbApi::class.java)

    suspend fun movieUkAvailability(key: String, title: String, year: String?, tmdbId: String?): UkAvailability? = withContext(Dispatchers.IO) {
        val isReadToken = key.startsWith("eyJ")
        val apiKey = if (isReadToken) null else key
        val authorization = if (isReadToken) "Bearer $key" else null
        val id = tmdbId?.toIntOrNull() ?: api.searchMovie(apiKey, authorization, title, year?.take(4)).results
            .firstOrNull { it.title.equals(title, ignoreCase = true) }?.id ?: return@withContext null
        api.providers(id, apiKey, authorization).results["GB"]?.let { providers ->
            UkAvailability(providers.flatrate.map { it.name }.distinct(), providers.rent.map { it.name }.distinct(), providers.buy.map { it.name }.distinct(), providers.link)
        }
    }
}
