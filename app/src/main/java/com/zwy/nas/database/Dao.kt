package com.zwy.nas.database

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Dao
interface UserDao {
    @Query("SELECT * FROM user_table WHERE id = :userId")
    suspend fun findUser(userId: Int): UserBean?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserBean)
}

@Dao
interface TokenDao {
    @Query("SELECT * FROM token_table limit 1")
    suspend fun findToken(): String?

    @Query("SELECT * FROM token_table limit 1")
    fun findTokenSync(): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertToken(tokenBean: TokenBean)

    @Query("DELETE FROM token_table")
    suspend fun delAll()
}

@Dao
interface UploadFileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUploadFile(uploadFileBean: UploadFileBean)

    @Query("SELECT * FROM upload_file")
    suspend fun findUploadFile(): List<UploadFileBean>

    @Query("select * from upload_file where status= :status")
    fun findByStatusSync(status: Int): List<UploadFileBean>

    @Query("select * from upload_file where status= :status")
    suspend fun findByStatus(status: Int):List<UploadFileBean>

    @Query("DELETE FROM upload_file WHERE id = :id")
    suspend fun delUploadFile(id: Long)

    @Query("DELETE FROM upload_file WHERE id = :id")
    fun delUploadFileSync(id: Long)

    @Query("SELECT * FROM upload_file WHERE id = :id")
    suspend fun findById(id: Long): UploadFileBean

    @Query("SELECT * FROM upload_file WHERE name = :name AND superId = :superId")
    suspend fun findByName(name: String, superId: String): UploadFileBean?

    @Query("UPDATE upload_file SET progress = :progress WHERE id = :id")
    suspend fun keepProgress(id: Long, progress: Int)

    @Query("update upload_file set status = :status where id = :id")
    suspend fun updateStatus(id: Long, status: Int)

    @Query("update upload_file set status = :status , progress = :progress where id=:id")
    suspend fun updateStatusAndProgress(id: Long, status: Int, progress: Int)
}


@Database(
    entities = [UserBean::class, TokenBean::class, UploadFileBean::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun tokenDao(): TokenDao
    abstract fun UploadFileDao(): UploadFileDao
}

object DatabaseHolder {
    private var instance: AppDatabase? = null

    fun getInstance(context: Context): AppDatabase {
        return instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java, "nas-database"
            ).build().also { instance = it }
        }
    }
}