package com.admin3.byeruli.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ArticleDao {
  @Query("SELECT * FROM article_table")
  fun getAllArticles(): List<Article>

  @Insert
  fun insertAll(vararg articles: Article)

  @Delete
  fun delete(article: Article)

  @Query("DELETE FROM article_table")
  fun clear()

}