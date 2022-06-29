package com.admin3.byeruli.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query


@Dao
interface CommentDao {
  @Query("SELECT * FROM comment_name")
  fun getAllComments(): List<Comment>

  @Insert
  fun insertAll(vararg comments: Comment)

  @Delete
  fun delete(comment: Comment)

  @Query("DELETE FROM comment_name")
  fun clear()


}