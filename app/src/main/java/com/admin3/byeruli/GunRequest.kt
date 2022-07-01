package com.admin3.byeruli

import com.admin3.byeruli.data.Article
import com.admin3.byeruli.data.Comment
import com.admin3.byeruli.data.DelResp
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.Headers
import com.github.kittinunf.fuel.coroutines.awaitObjectResult
import com.github.kittinunf.fuel.coroutines.awaitStringResult
import com.github.kittinunf.fuel.serialization.kotlinxDeserializerOf
import kotlinx.coroutines.delay
import kotlinx.datetime.*
import kotlinx.serialization.json.Json
import org.jsoup.Jsoup

object GunRequest {

  private val JsonParser = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
  }
  /**
   * n 페이지에서 게시글 목록을 불러옵니다
   */
  suspend fun getOwnerArticles(sToken:String, page:Int = 1):Pair<Boolean, List<Article>> {
    val url = "https://bbs.ruliweb.com/member/mypage/myarticle"
    return Fuel.get(url, listOf("page" to page)).apply {
      timeout(5000)
      header(Headers.COOKIE, "s_token=$sToken")
    }.awaitStringResult().fold(
      success = { html ->
        val query = Jsoup.parse(html)
        // 1. check end of content
        val contents = query.select(".table_body > tr > td")
        val isEnd = (contents.firstOrNull()?.select("div")?.size ?: 0)  == 0
        if (isEnd) {
          Pair(true, emptyList<Article>())
        } else {
          // 2. serialize articles
          val articles = contents.map {
            val ids = it.select(".del > div").firstOrNull()?.let { el ->
              Pair(el.attr("article-id"), el.attr("board-id"))
            }
            val articleId = ids?.first?.toInt() ?: -1
            val boardId = ids?.second?.toInt() ?: -1
            val boardName = it.select(".board_name > a").text()
            val title = it.select(".subject > a").text()
            val createdTime = it.select(".regdate").text().trim().let { datetext ->
              if (datetext.indexOf(":") >= 0) {
                val nowTime = Clock.System.now().toLocalDateTime(TimeZone.of("Asia/Seoul"))
                nowTime.year * 10000 + nowTime.monthNumber * 100 + nowTime.dayOfMonth
              } else {
                val split = datetext.split(".").map {numStr -> numStr.toInt()}
                split[0] * 10000 + split[1] * 100 + split[2]
              }
            }
            val deleted = false
            Article(boardId, articleId, boardName, title, createdTime, deleted)
          }.filter { it.articleId != -1 }
          Pair(true, articles)
        }
      },
      failure = {
        it.printStackTrace()
        Pair(false, emptyList<Article>())
      }
    )
  }

  /**
   * 가장 마지막 페이지를 불러옵니다. (이진 검색)
   */
  suspend fun getLastOwnerArticlesPage(sToken:String, progress:(Int) -> Boolean = {true}):Pair<Int, Int> {
    return binarySearchPage(sToken, requestGetter = { token, page ->
      getOwnerArticles(token, page)
    }, progress)
    /*
    var page = 1
    var retries = 0
    // pos
    var findEmpty = false
    var leftPos = 1
    var rightPos = 1
    while (true) {
      val article_res = getOwnerArticles(sToken, page)
      if (!article_res.first) {
        retries += 1
        if (retries >= 3) {
          throw Exception("failed to get last page. Retries exceeded.")
        }
        continue
      }
      val articles = article_res.second
      // if articles is empty, it means that we are over last page..
      if (articles.isEmpty()) {
        if (!findEmpty) {
          findEmpty = true
        }
        // center page
        rightPos = page
        page = (leftPos + rightPos) / 2
      } else {
        if (!progress(page)) {
          return Pair(-1, -1)
        }
        // if article size is less then 30, we are in end page
        if (articles.size < 30) {
          return Pair(page, (page - 1) * 30 + articles.size)
        }
        // if article size is 30, we should find again
        leftPos = page
        if (!findEmpty) {
          page *= 2
        } else {
          page = (leftPos + rightPos) / 2
        }
      }
      if (findEmpty && rightPos - leftPos <= 1) {
        // break condition
        if (!progress(leftPos)) {
          return Pair(-1, -1)
        }
        return Pair(leftPos, leftPos * 30)
      }
    }
     */
  }

  suspend fun deleteOwnerArticle(sToken:String, boardId:Int, articleId:Int):Pair<Boolean, String> {
    val url = "https://api.ruliweb.com/procDeleteMyArticle"
    return Fuel.post(url, listOf("board_id" to boardId, "article_id" to articleId)).apply {
      timeout(5000)
      header(Headers.COOKIE to "s_token=$sToken")
      header("Referer" to "https://bbs.ruliweb.com/member/mypage/myarticle")
    }.awaitObjectResult<DelResp>(kotlinxDeserializerOf(json = JsonParser)).fold(
      success = {
        Pair(true, it.message)
      },
      failure = {
        it.printStackTrace()
        Pair(false, it.message ?: "")
      }
    )
  }

  suspend fun getOwnerComments(sToken: String, page:Int = 1):Pair<Boolean, List<Comment>> {
    val url = "https://bbs.ruliweb.com/member/mypage/mycomment"
    return Fuel.get(url, listOf("page" to page)).apply {
      timeout(5000)
      header(Headers.COOKIE, "s_token=$sToken")
    }.awaitStringResult().fold(
      success = { html ->
        val query = Jsoup.parse(html)
        // 1. check end of content
        val contents = query.select(".table_body > tr")
        val isEnd = (contents.firstOrNull()?.select("td")?.size ?: 1)  == 1
        if (isEnd) {
          Pair(true, emptyList())
        } else {
          // 2. serialize comments
          val comments = contents.map {
            val tdEl = it.select("td")
            val ids = it.select(".d_mycomment").firstOrNull()?.let { el ->
              Triple(el.attr("comment-id"),
                el.attr("board-id"), el.attr("article-id"))
            } ?: Triple("-1", "-1", "-1")
            val commentId = ids.first.toInt()
            val boardId = ids.second.toInt()
            val articleId = ids.third.toInt()
            val boardName = tdEl.getOrNull(0)?.text() ?: ""
            val content = tdEl.getOrNull(1)?.text() ?: ""
            val createTime = tdEl.getOrNull(2)?.text()?.trim()?.let { datetext ->
              if (datetext.indexOf(":") >= 0) {
                val nowTime = Clock.System.now().toLocalDateTime(TimeZone.of("Asia/Seoul"))
                nowTime.year * 10000 + nowTime.monthNumber * 100 + nowTime.dayOfMonth
              } else {
                val split = datetext.split(".").map {numStr -> numStr.toInt()}
                split[0] * 10000 + split[1] * 100 + split[2]
              }
            } ?: -1
            val deleted = false
            Comment(boardId, articleId, commentId, boardName, content, createTime, deleted)
          }.filter { it.articleId != -1 }

          Pair(true, comments)
        }
      },
      failure = {
        it.printStackTrace()
        Pair(false, emptyList())
      }
    )
  }

  suspend fun getLastOwnerCommentsPage(sToken:String, progress: (Int) -> Boolean = {true}):Pair<Int, Int> {
    return binarySearchPage(sToken, requestGetter = { token, page ->
      getOwnerComments(token, page)
    }, progress)
  }

  suspend fun deleteOwnerComment(sToken: String,
                                 boardId: Int,
                                 articleId: Int,
                                 commentId: Int):Pair<Boolean, String> {
    val url = "https://api.ruliweb.com/procDeleteMyComment"
    return Fuel.post(url, listOf(
      "board_id" to boardId,
      "article_id" to articleId,
      "comment_id" to commentId)
    ).apply {
      timeout(5000)
      header(Headers.COOKIE to "s_token=$sToken")
      header("Referer" to "https://bbs.ruliweb.com/member/mypage/mycomment")
    }.awaitObjectResult<DelResp>(kotlinxDeserializerOf(json = JsonParser)).fold(
      success = {
        Pair(true, it.message)
      },
      failure = {
        it.printStackTrace()
        Pair(false, it.message ?: "")
      }
    )
  }

  private suspend fun binarySearchPage(sToken: String,
                                       requestGetter:suspend (String, Int) -> Pair<Boolean, List<*>>,
                                       progress:(Int) -> Boolean): Pair<Int, Int> {
    var page = 1
    var retries = 0
    // pos
    var findEmpty = false
    var leftPos = 1
    var rightPos = 1
    while (true) {
      val res = requestGetter(sToken, page)
      if (!res.first) {
        retries += 1
        if (retries >= 10) {
          progress(-1)
          return Pair(-1, -1)
        }
        continue
      }
      val resObj = res.second
      // if articles/comments is empty, it means that we are over last page..
      if (resObj.isEmpty()) {
        if (!findEmpty) {
          findEmpty = true
        }
        // center page
        rightPos = page
        page = (leftPos + rightPos) / 2
      } else {
        if (!progress(page)) {
          return Pair(-1, -1)
        }
        // if article size is less then 30, we are in end page
        if (resObj.size < 30) {
          return Pair(page, (page - 1) * 30 + resObj.size)
        }
        // if article size is 30, we should find again
        leftPos = page
        if (!findEmpty) {
          page *= 2
        } else {
          page = (leftPos + rightPos) / 2
        }
      }
      if (findEmpty && rightPos - leftPos <= 1) {
        // break condition
        if (!progress(leftPos)) {
          return Pair(-1, -1)
        }
        return Pair(leftPos, leftPos * 30)
      }
    }
  }
}