package com.traynor.player

import android.app.Application
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.squareup.moshi.Moshi
import com.traynor.player.data.local.PlayerDatabase
import com.traynor.player.data.network.XtreamApi
import com.traynor.player.data.network.GitHubReleaseRepository
import com.traynor.player.data.network.TmdbRepository
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
    val database = Room.databaseBuilder(app, PlayerDatabase::class.java, "player.db")
        .addMigrations(object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS movies (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sourceId INTEGER NOT NULL, externalId TEXT NOT NULL, title TEXT NOT NULL, streamUrlEncrypted TEXT NOT NULL, posterUrl TEXT, category TEXT NOT NULL, description TEXT, year TEXT, rating TEXT, runtime TEXT, searchText TEXT NOT NULL, FOREIGN KEY(sourceId) REFERENCES sources(id) ON DELETE CASCADE)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_movies_sourceId_externalId ON movies(sourceId, externalId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_movies_sourceId ON movies(sourceId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_movies_category ON movies(category)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_movies_searchText ON movies(searchText)")
                db.execSQL("CREATE TABLE IF NOT EXISTS series (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sourceId INTEGER NOT NULL, externalId TEXT NOT NULL, title TEXT NOT NULL, posterUrl TEXT, category TEXT NOT NULL, description TEXT, year TEXT, rating TEXT, searchText TEXT NOT NULL, FOREIGN KEY(sourceId) REFERENCES sources(id) ON DELETE CASCADE)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_series_sourceId_externalId ON series(sourceId, externalId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_series_sourceId ON series(sourceId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_series_category ON series(category)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_series_searchText ON series(searchText)")
            }
        }).build()
    val preferences = PlayerPreferences(app)
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true).followSslRedirects(true).build()
    private val api = Retrofit.Builder().baseUrl("https://localhost/")
        .client(http)
        .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().build()))
        .build().create(XtreamApi::class.java)
    val sourceRepository = SourceRepository(database.sourceDao(), database.channelDao(), database.movieDao(), database.seriesDao(), CredentialCipher(), api, http, app.contentResolver, M3uParser())
    val releaseRepository = GitHubReleaseRepository(http)
    val tmdbRepository = TmdbRepository(http)
}
