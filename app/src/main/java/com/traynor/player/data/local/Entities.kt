package com.traynor.player.data.local

import androidx.room.*
import com.traynor.player.core.model.ContentType
import com.traynor.player.core.model.SourceType
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "sources", indices = [Index("enabled")])
data class SourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: SourceType,
    val endpointEncrypted: String,
    val usernameEncrypted: String = "",
    val passwordEncrypted: String = "",
    val enabled: Boolean = true,
    val lastRefreshedAt: Long? = null
)

@Entity(
    tableName = "channels",
    foreignKeys = [ForeignKey(entity = SourceEntity::class, parentColumns = ["id"], childColumns = ["sourceId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sourceId"), Index("category"), Index("searchText"), Index(value = ["sourceId", "externalId"], unique = true)]
)
data class ChannelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    val externalId: String,
    val name: String,
    val streamUrlEncrypted: String,
    val logoUrl: String? = null,
    val category: String = "Uncategorised",
    val tvgId: String? = null,
    val searchText: String = name.lowercase()
)

@Entity(
    tableName = "movies",
    foreignKeys = [ForeignKey(entity = SourceEntity::class, parentColumns = ["id"], childColumns = ["sourceId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sourceId"), Index("category"), Index("searchText"), Index(value = ["sourceId", "externalId"], unique = true)]
)
data class MovieEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    val externalId: String,
    val title: String,
    val streamUrlEncrypted: String,
    val posterUrl: String? = null,
    val category: String = "Uncategorised",
    val description: String? = null,
    val year: String? = null,
    val rating: String? = null,
    val runtime: String? = null,
    val searchText: String = title.lowercase()
)

@Entity(
    tableName = "series",
    foreignKeys = [ForeignKey(entity = SourceEntity::class, parentColumns = ["id"], childColumns = ["sourceId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sourceId"), Index("category"), Index("searchText"), Index(value = ["sourceId", "externalId"], unique = true)]
)
data class SeriesEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    val externalId: String,
    val title: String,
    val posterUrl: String? = null,
    val category: String = "Uncategorised",
    val description: String? = null,
    val year: String? = null,
    val rating: String? = null,
    val searchText: String = title.lowercase()
)

@Entity(tableName = "favourites", primaryKeys = ["sourceId", "contentType", "contentId"], indices = [Index("contentType")])
data class FavouriteEntity(val sourceId: Long, val contentType: ContentType, val contentId: String, val createdAt: Long = System.currentTimeMillis())

@Entity(tableName = "history", indices = [Index("contentType"), Index("lastPlayedAt")])
data class HistoryEntity(
    @PrimaryKey val contentKey: String,
    val sourceId: Long,
    val contentType: ContentType,
    val contentId: String,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val watched: Boolean = false,
    val lastPlayedAt: Long = System.currentTimeMillis()
)

@Dao interface SourceDao {
    @Query("SELECT * FROM sources ORDER BY name") fun observeAll(): Flow<List<SourceEntity>>
    @Query("SELECT * FROM sources WHERE id=:id") suspend fun get(id: Long): SourceEntity?
    @Query("SELECT * FROM sources WHERE enabled=1 ORDER BY id LIMIT 1") suspend fun active(): SourceEntity?
    @Insert suspend fun insert(source: SourceEntity): Long
    @Update suspend fun update(source: SourceEntity)
    @Query("UPDATE sources SET lastRefreshedAt=:time WHERE id=:id") suspend fun markRefreshed(id: Long, time: Long)
    @Delete suspend fun delete(source: SourceEntity)
}

@Dao interface ChannelDao {
    @Query("SELECT DISTINCT category FROM channels WHERE sourceId=:sourceId ORDER BY category") fun categories(sourceId: Long): Flow<List<String>>
    @Query("SELECT * FROM channels WHERE sourceId=:sourceId AND (:category IS NULL OR category=:category) AND searchText LIKE '%' || lower(:query) || '%' ORDER BY name LIMIT :limit OFFSET :offset")
    fun observePage(sourceId: Long, category: String?, query: String, limit: Int = 250, offset: Int = 0): Flow<List<ChannelEntity>>
    @Query("SELECT * FROM channels WHERE id=:id") suspend fun get(id: Long): ChannelEntity?
    @Query("SELECT * FROM channels WHERE id=:id") fun observe(id: Long): Flow<ChannelEntity?>
    @Query("SELECT COUNT(*) FROM channels WHERE sourceId=:sourceId") fun observeCount(sourceId: Long): Flow<Int>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(items: List<ChannelEntity>)
    @Query("DELETE FROM channels WHERE sourceId=:sourceId") suspend fun deleteForSource(sourceId: Long)
}

@Dao interface MovieDao {
    @Query("SELECT DISTINCT category FROM movies WHERE sourceId=:sourceId ORDER BY category") fun categories(sourceId: Long): Flow<List<String>>
    @Query("SELECT * FROM movies WHERE sourceId=:sourceId AND (:category IS NULL OR category=:category) AND searchText LIKE '%' || lower(:query) || '%' ORDER BY title LIMIT :limit OFFSET :offset") fun observePage(sourceId: Long, category: String?, query: String, limit: Int = 250, offset: Int = 0): Flow<List<MovieEntity>>
    @Query("SELECT * FROM movies WHERE id=:id") suspend fun get(id: Long): MovieEntity?
    @Query("SELECT COUNT(*) FROM movies WHERE sourceId=:sourceId") fun observeCount(sourceId: Long): Flow<Int>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(items: List<MovieEntity>)
    @Query("DELETE FROM movies WHERE sourceId=:sourceId") suspend fun deleteForSource(sourceId: Long)
}

@Dao interface SeriesDao {
    @Query("SELECT DISTINCT category FROM series WHERE sourceId=:sourceId ORDER BY category") fun categories(sourceId: Long): Flow<List<String>>
    @Query("SELECT * FROM series WHERE sourceId=:sourceId AND (:category IS NULL OR category=:category) AND searchText LIKE '%' || lower(:query) || '%' ORDER BY title LIMIT :limit OFFSET :offset") fun observePage(sourceId: Long, category: String?, query: String, limit: Int = 250, offset: Int = 0): Flow<List<SeriesEntity>>
    @Query("SELECT * FROM series WHERE id=:id") suspend fun get(id: Long): SeriesEntity?
    @Query("SELECT COUNT(*) FROM series WHERE sourceId=:sourceId") fun observeCount(sourceId: Long): Flow<Int>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(items: List<SeriesEntity>)
    @Query("DELETE FROM series WHERE sourceId=:sourceId") suspend fun deleteForSource(sourceId: Long)
}

@Dao interface LibraryDao {
    @Query("SELECT EXISTS(SELECT 1 FROM favourites WHERE sourceId=:sourceId AND contentType=:type AND contentId=:contentId)") fun isFavourite(sourceId: Long, type: ContentType, contentId: String): Flow<Boolean>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun favourite(item: FavouriteEntity)
    @Query("DELETE FROM favourites WHERE sourceId=:sourceId AND contentType=:type AND contentId=:contentId") suspend fun unfavourite(sourceId: Long, type: ContentType, contentId: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun record(item: HistoryEntity)
    @Query("DELETE FROM history") suspend fun clearHistory()
}

class Converters {
    @TypeConverter fun sourceType(value: SourceType) = value.name
    @TypeConverter fun toSourceType(value: String) = SourceType.valueOf(value)
    @TypeConverter fun contentType(value: ContentType) = value.name
    @TypeConverter fun toContentType(value: String) = ContentType.valueOf(value)
}

@Database(entities = [SourceEntity::class, ChannelEntity::class, MovieEntity::class, SeriesEntity::class, FavouriteEntity::class, HistoryEntity::class], version = 2, exportSchema = true)
@TypeConverters(Converters::class)
abstract class PlayerDatabase : RoomDatabase() {
    abstract fun sourceDao(): SourceDao
    abstract fun channelDao(): ChannelDao
    abstract fun movieDao(): MovieDao
    abstract fun seriesDao(): SeriesDao
    abstract fun libraryDao(): LibraryDao
}
