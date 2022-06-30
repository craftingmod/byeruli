package com.admin3.byeruli

import android.os.Bundle
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.*
import com.admin3.byeruli.ui.theme.ByeRuliTheme
import com.google.accompanist.web.AccompanistWebViewClient
import com.google.accompanist.web.WebView
import com.google.accompanist.web.WebViewNavigator
import com.google.accompanist.web.rememberWebViewState
import kotlinx.coroutines.Dispatchers
import java.net.HttpCookie

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      ByeRuliTheme {
        val state = rememberWebViewState(url = "https://user.ruliweb.com/member/login?mode=m")
        var urlText by remember { mutableStateOf("https://user.ruliweb.com/member/login?mode=m") }
        var requestedService by remember { mutableStateOf(false) }

        // A surface container using the 'background' color from the theme
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          Column(
            modifier = Modifier.fillMaxSize()
          ) {
            SmallTopAppBar(
              title = {
                Column {
                  Text("근근웹 삭제기")
                  Text(urlText, fontSize = 10.sp, overflow = TextOverflow.Ellipsis, maxLines = 1)
                }
              }
            )
            if (requestedService) {
              Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
              ) {
                Column(
                  horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                  Image(painter = painterResource(id = R.drawable.ic_gun), contentDescription = "근")
                  Spacer(modifier = Modifier.height(6.dp))
                  Text("삭제중!")
                }
              }
            } else {
              WebView(
                state = state,
                modifier = Modifier.fillMaxSize(),
                onCreated = {
                  it.settings.apply {
                    javaScriptEnabled = true
                    userAgentString = "Mozilla/5.0 (Linux; Android 10; SM-G960U) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/103.0.5060.70 Mobile Safari/537.36"
                  }
                },
                client = object : AccompanistWebViewClient() {
                  override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                  ): Boolean {
                    urlText = request?.url?.toString() ?: "Unknown"
                    return false
                    // return super.shouldOverrideUrlLoading(view, request)
                  }

                  override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if ((url ?: "") == "https://m.ruliweb.com/") {
                      val cookieStr = CookieManager.getInstance().getCookie(url)
                      val cookie = cookieStr.split("; ").map {
                        val split = it.split("=")
                        Pair(split[0], split[1])
                      }
                      val sToken = cookie.firstOrNull { it.first == "s_token" }?.second ?: ""
                      if (sToken.isNotEmpty()) {
                        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                          "DeletionWorker",
                          ExistingWorkPolicy.KEEP,
                          OneTimeWorkRequestBuilder<DelWorker>()
                            .setInputData(Data.Builder().apply {
                              putString(DelWorker.KEY_TOKEN, sToken)
                            }.build()).build()
                        )
                        requestedService = true
                      } else {
                        Toast.makeText(this@MainActivity, "로그인 정보를 찾을 수 없습니다.", Toast.LENGTH_LONG).show()
                      }
                    }

                  }
                }
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun Greeting(name: String) {
  Text(text = "Hello $name!")
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
  ByeRuliTheme {
    Greeting("Android")
  }
}