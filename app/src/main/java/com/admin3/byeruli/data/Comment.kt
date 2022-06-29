package com.admin3.byeruli.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "comment_name")
data class Comment(
  val boardId: Int,
  val articleId: Int,
  val commentId: Int,
  val boardName: String,
  val content: String,
  val createTime: Int,
  val deleted: Boolean,
) {
  @PrimaryKey(autoGenerate = true)
  var id:Int = 0
}