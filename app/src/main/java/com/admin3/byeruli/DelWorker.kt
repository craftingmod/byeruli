package com.admin3.byeruli

import android.app.Notification
import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.admin3.byeruli.data.Article
import com.admin3.byeruli.data.Comment
import com.admin3.byeruli.data.GunDatabase
import kotlinx.coroutines.*
import kotlin.math.round

class DelWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
  companion object {
    const val KEY_TOKEN = "stoken"
    const val KEY_COOKIE = "cookie"
    const val NOTI_ID = 4880268
  }
  private val cancelIntent = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)

  private val notificationManager = NotificationManagerCompat.from(context)
  private val largeIcon = BitmapFactory.decodeResource(context.resources, R.drawable.ic_gun_bitmap)
  private val notificationBuilder = NotificationCompat.Builder(applicationContext, "ruli-del-worker").apply {
    setSmallIcon(R.drawable.ic_delete_time)
    setLargeIcon(largeIcon)
    setShowWhen(true)
    setSilent(true)
    setOnlyAlertOnce(true)
    setOngoing(true)
    addAction(R.drawable.ic_cancel, "삭제 중지", cancelIntent)
    priority = NotificationManagerCompat.IMPORTANCE_HIGH
  }
  private val database = GunDatabase.getInstance(applicationContext)

  override suspend fun doWork(): Result {
    val sToken = inputData.getString(KEY_TOKEN) ?: return Result.failure()
    createChannel()
    // Init noti
    val initNoti = createNotification(ProgressState.FETCH_NUM_ARTICLE, 1, 1)
    notificationManager.notify(NOTI_ID, initNoti)
    setForeground(ForegroundInfo(NOTI_ID, initNoti))

    val failNoti = {
      NotificationCompat.Builder(applicationContext, "ruli-del-worker").apply {
        setSmallIcon(R.drawable.ic_delete_time)
        setStyle(NotificationCompat.BigPictureStyle().bigPicture(largeIcon))
        setContentTitle("삭제 실패")
        setContentText("관리-03가 탄핵당했습니다.\n관리-03이 나중에 다시 고로시를 시도할 것 같습니다.")
      }.build().also {
        notificationManager.notify(NOTI_ID - 1, it)
      }
    }

    val articleTaskResult = deleteTask(sToken, true)
    if (!articleTaskResult) {
      failNoti()
      return Result.failure()
    }
    val commentTaskResult = deleteTask(sToken, false)
    if (!commentTaskResult) {
      failNoti()
      return Result.failure()
    }
    // 성공 알림
    NotificationCompat.Builder(applicationContext, "ruli-del-worker").apply {
      setSmallIcon(R.drawable.ic_delete_time)
      setStyle(NotificationCompat.BigPictureStyle().bigPicture(largeIcon))
      setContentTitle("삭제 완료")
      setContentText("관리-03가 고로시를 다 했습니다.\n관리-03: 이때까지 트래픽을 발생시켜줘서 감사합니다.")
    }.build().also {
      notificationManager.notify(NOTI_ID - 1, it)
    }

    return Result.success()
  }
  private suspend fun deleteTask(sToken:String, isArticle: Boolean): Boolean {
    return withContext(Dispatchers.IO + SupervisorJob()) {
      val tag = if (isArticle) "Article" else "Comment"
      // 1. get end page
      val progressFn = { progress:Int ->
        Log.d("DelWorker", "$tag last page finder: $progress")
        if (isStopped) {
          false
        } else {
          launch {
            notify(
              if (isArticle) ProgressState.FETCH_NUM_ARTICLE else ProgressState.FETCH_NUM_COMMENT,
                progress, Int.MAX_VALUE)
          }
          true
        }
      }
      // 2. fetch pages
      Log.d("DelWorker", "Fetching $tag..")
      val pageInfo = if (isArticle) {
        GunRequest.getLastOwnerArticlesPage(sToken, progressFn)
      } else {
        GunRequest.getLastOwnerCommentsPage(sToken, progressFn)
      }
      // 3. fetch articles/comments
      if (isArticle) {
        val articles:List<Article> = (1..pageInfo.first).map { page ->
          Log.d("DelWorker", "page $page/${pageInfo.first}")
          if (isStopped) {
            // canceled work
            database.articleDao().clear()
            return@withContext false
          }
          for (i in 0 until 5) {
            try {
              val pageArticles = GunRequest.getOwnerArticles(sToken, page)
              if (!pageArticles.first) {
                continue
              }
              launch {
                notify(ProgressState.COLLECT_ARTICLE_ID, (page - 1) * 30 + pageArticles.second.size, pageInfo.second)
              }
              return@map pageArticles.second
            } catch (e:Exception) {
              e.printStackTrace()
            }
          }
          return@withContext false
        }.flatten().asReversed()

        database.articleDao().insertAll(*articles.toTypedArray())

        // 4. delete articles
        Log.d("DelWorker", "Deleting ${tag}s..")
        for (i in articles.indices) {
          Log.d("DelWorker", "Article ${i+1} / ${articles.size}")
          val article = articles[i]
          if (isStopped) {
            // canceled work
            database.articleDao().clear()
            return@withContext false
          }
          var deleteResult = false
          try {
            deleteResult = GunRequest.deleteOwnerArticle(sToken, article.boardId, article.articleId).first
            // deleteResult = true
            delay(50L)
          } catch (e:Exception) {
            e.printStackTrace()
          }
          if (!deleteResult) {
            // FAIL
            database.articleDao().delete(article)
            database.articleDao().insertAll(article)
          } else {
            // SUCCESS
            database.articleDao().delete(article)
            database.articleDao().insertAll(article.copy(deleted = true))
          }
          launch {
            notify(ProgressState.DELETE_ARTICLE, i + 1, articles.size)
          }
        }
        true
      } else {
        val comments:List<Comment> = (1..pageInfo.first).map { page ->
          Log.d("DelWorker", "page $page/${pageInfo.first}")
          if (isStopped) {
            // canceled work
            database.commentDao().clear()
            return@withContext false
          }
          for (i in 0 until 5) {
            try {
              val pageComments = GunRequest.getOwnerComments(sToken, page)
              if (!pageComments.first) {
                continue
              }
              launch {
                notify(ProgressState.COLLECT_COMMENT_ID, (page - 1) * 30 + pageComments.second.size, pageInfo.second)
              }
              return@map pageComments.second
            } catch (e:Exception) {
              e.printStackTrace()
            }
          }
          return@withContext false
        }.flatten().asReversed()

        database.commentDao().insertAll(*comments.toTypedArray())
        // 4. delete comments
        Log.d("DelWorker", "Deleting ${tag}s..")
        for (i in comments.indices) {
          Log.d("DelWorker", "Comment ${i+1} / ${comments.size}")
          val comment = comments[i]
          if (isStopped) {
            // canceled work
            database.commentDao().clear()
            return@withContext false
          }
          var deleteResult = false
          try {
            deleteResult = GunRequest.deleteOwnerComment(sToken, comment.boardId, comment.articleId, comment.commentId).first
            // deleteResult = true
            delay(50L)
          } catch (e:Exception) {
            e.printStackTrace()
          }

          if (!deleteResult) {
            // FAIL
            database.commentDao().delete(comment)
            database.commentDao().insertAll(comment)
          } else {
            // SUCCESS
            database.commentDao().delete(comment)
            database.commentDao().insertAll(comment.copy(deleted = true))
          }
          launch {
            notify(ProgressState.DELETE_COMMENT, i + 1, comments.size)
          }
        }
        true
      }
    }
  }
  private fun createChannel() {
    val channel = NotificationChannelCompat.Builder("ruli-del-worker", NotificationManagerCompat.IMPORTANCE_HIGH)
      .setName("관리-03 알림")
      .setDescription("관리자 메세지입니다. 귀하의 근근웹 계정이 영구 이용정지 조치되었습니다.\n사유 - 무슨무슨 규정 위반")
      .build()
    notificationManager.createNotificationChannel(channel)
  }

  private fun notify(state:ProgressState, currentValue:Int, maxValue:Int) {
    if (!isStopped) {
      notificationManager.notify(NOTI_ID, createNotification(state, currentValue, maxValue))
    }
  }

  private fun createNotification(state:ProgressState, currentValue:Int, maxValue:Int): Notification {
    val progress = if (state == ProgressState.FETCH_NUM_ARTICLE || state == ProgressState.FETCH_NUM_COMMENT) {
      "?%"
    } else {
      String.format("%.1f", currentValue.toFloat() / maxValue * 100) + "%"
    }

    notificationBuilder.apply {
      val title = when (state) {
        ProgressState.FETCH_NUM_ARTICLE -> "게시글 수 찾는 중 - $currentValue"
        ProgressState.COLLECT_ARTICLE_ID -> "게시글 수집 중 - $currentValue/$maxValue"
        ProgressState.DELETE_ARTICLE -> "게시글 삭제 중 - $currentValue/$maxValue"
        ProgressState.FETCH_NUM_COMMENT -> "댓글 수 찾는 중 - $currentValue"
        ProgressState.COLLECT_COMMENT_ID -> "댓글 수집 중 - $currentValue/$maxValue"
        ProgressState.DELETE_COMMENT -> "댓글 삭제 중 - $currentValue/$maxValue"
      }
      val content = when (state) {
        ProgressState.FETCH_NUM_ARTICLE -> "관리-03이 얼마나 유게이가 시간을 낭비했는지 찾고 있습니다."
        ProgressState.COLLECT_ARTICLE_ID -> "관리-03이 유게이의 게시글이 꼴리는지 평가하고 있습니다."
        ProgressState.DELETE_ARTICLE -> "관리-03이 유게이가 마음에 안들어 유게이를 고로시하고 있습니다.\n관리-03: 저는 님 친구가 아닙니다."
        ProgressState.FETCH_NUM_COMMENT -> "관리-03이 유게이가 어떻게 다니는지 보기 위해 댓글을 찾고 있습니다."
        ProgressState.COLLECT_COMMENT_ID -> "관리-03이 유게이의 댓글들을 보면서 어떤 걸 고로시할지 고민하고 있습니다."
        ProgressState.DELETE_COMMENT -> "관리-03이 유게이의 흔적을 완전히 지워 기록말살을 하고 있습니다.\n황달의 돈이 터지고 있습니다."
      }
      setStyle(NotificationCompat.BigTextStyle().setBigContentTitle(title))
      setContentTitle(title)
      setContentText(content)
      setSubText(progress)
      setProgress(maxValue, currentValue, state == ProgressState.FETCH_NUM_ARTICLE || state == ProgressState.FETCH_NUM_COMMENT)

    }

    return notificationBuilder.build()
  }
}

enum class ProgressState {
  FETCH_NUM_ARTICLE,
  COLLECT_ARTICLE_ID,
  DELETE_ARTICLE,
  FETCH_NUM_COMMENT,
  COLLECT_COMMENT_ID,
  DELETE_COMMENT,
}