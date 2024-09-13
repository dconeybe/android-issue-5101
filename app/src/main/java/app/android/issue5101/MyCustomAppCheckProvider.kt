/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package app.android.issue5101

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.AppCheckProvider
import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.AppCheckToken
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.readBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import java.time.Instant
import kotlin.random.Random
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class MyCustomAppCheckToken(
    private val token: String,
    private val expireTimeMillis: Long,
) : AppCheckToken() {
  override fun getToken(): String = token

  override fun getExpireTimeMillis(): Long = expireTimeMillis
}

class MyCustomAppCheckProvider(
    firebaseApp: FirebaseApp,
    val app: MyApplication,
    val host: String,
    val port: Int
) : AppCheckProvider {

  private val appId = firebaseApp.options.applicationId
  private val projectId = firebaseApp.options.projectId ?: "<project ID unknown>"

  override fun getToken(): Task<AppCheckToken> {
    val requestId = "acrid" + Random.nextAlphanumericString(length = 8)
    app.log("[requestId_$requestId] getToken() started")
    val taskCompletionSource = TaskCompletionSource<AppCheckToken>()

    @OptIn(DelicateCoroutinesApi::class)
    val job =
        GlobalScope.async {
          val token = getAppCheckToken(requestId)
          app.log("[requestId_$requestId] getToken() completed successfully")
          taskCompletionSource.trySetResult(token)
        }

    job.invokeOnCompletion { throwable ->
      if (throwable !== null) {
        app.log("[requestId_$requestId] WARNING: request failed: $throwable")
      }
      if (throwable is Exception) {
        taskCompletionSource.trySetException(throwable)
      } else if (throwable !== null) {
        taskCompletionSource.trySetException(Exception(throwable))
      }
    }

    return taskCompletionSource.task
  }

  private suspend fun getAppCheckToken(requestId: String): MyCustomAppCheckToken {
    val url = "http://$host:$port"
    app.log("[requestId_$requestId] Getting AppCheck Token from $url")

    @Serializable data class AppCheckTokenRequest(val appId: String, val projectId: String)
    @Serializable data class AppCheckTokenResponse(val token: String, val ttlMillis: Long)

    val client = HttpClient {
      install(ContentNegotiation) {
        json(
            Json {
              prettyPrint = false
              ignoreUnknownKeys = true
            })
      }
    }

    val response =
        client.post(url) {
          contentType(ContentType.Application.Json)
          setBody(AppCheckTokenRequest(appId = appId, projectId = projectId))
        }

    if (response.status != HttpStatusCode.OK) {
      val message = String(response.readBytes())
      class UnexpectedHttpResponseCodeException(message: String) : Exception(message)
      throw UnexpectedHttpResponseCodeException(
          "unexpected http status code: ${response.status} ($message)")
    }

    val responseBody = response.body<AppCheckTokenResponse>()
    val expireTimeMillis = Instant.now().toEpochMilli() + responseBody.ttlMillis - 60000L
    return MyCustomAppCheckToken(responseBody.token, expireTimeMillis)
  }
}

class MyCustomAppCheckProviderFactory(val app: MyApplication) : AppCheckProviderFactory {
  override fun create(firebaseApp: FirebaseApp): AppCheckProvider {
    return MyCustomAppCheckProvider(firebaseApp = firebaseApp, app = app, host = HOST, port = PORT)
  }

  companion object {
    const val HOST = "10.0.2.2"
    const val PORT = 9392
  }
}
