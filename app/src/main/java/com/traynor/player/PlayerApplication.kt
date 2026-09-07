package com.traynor.player

import android.app.Application
import androidx.room.Room
import com.squareup.moshi.Moshi
import com.traynor.player.data.local.PlayerDatabase
import com.traynor.player.data.network.XtreamApi
import com.traynor.player.data.parser.M3uParser
import com.traynor.player.data.preferences.PlayerPreferences
import com.traynor.player.data.repository.SourceRepository
import com.traynor.player.security.CredentialCipher
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class PlayerApplication : Application() {
    lateinit var container: AppContainer
    override fun onCreate() { super.onCreate(); container = AppContainer(this) }
}

class AppContainer(app: Application) {
    val database = Room.databaseBuilder(app, PlayerDatabase::class.java, "player.db").build()
    val preferences = PlayerPreferences(app)
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true).followSslRedirects(true).build()
    private val api = Retrofit.Builder().baseUrl("https://localhost/")
        .client(http)
        .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().build()))
        .build().create(XtreamApi::class.java)
    val sourceRepository = SourceRepository(database.sourceDao(), database.channelDao(), CredentialCipher(), api, http, app.contentResolver, M3uParser())
}
