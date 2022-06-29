package com.admin3.byeruli.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Article::class, Comment::class], version = 4)
abstract class GunDatabase : RoomDatabase() {
  companion object {
    @Volatile private var INSTANCE:GunDatabase? = null
    fun getInstance(context: Context):GunDatabase {
      synchronized(this) {
        var instance = INSTANCE
        if (instance == null) {
          instance =
            Room.databaseBuilder(context.applicationContext, GunDatabase::class.java, "archive.db")
              .fallbackToDestructiveMigration()
              .build()
          INSTANCE = instance
        }
        return instance
      }
    }
  }

  abstract fun articleDao() : ArticleDao
  abstract fun commentDao() : CommentDao
}