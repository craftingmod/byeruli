package com.admin3.byeruli.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "article_table")
data class Article(
  val boardId: Int,
  val articleId: Int,
  val boardName: String,
  val title: String,
  val createdTime: Int,
  val deleted: Boolean,
) {
  @PrimaryKey(autoGenerate = true)
  var id:Int = 0
}