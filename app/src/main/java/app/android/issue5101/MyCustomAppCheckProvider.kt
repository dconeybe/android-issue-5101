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
import java.net.Socket
import java.time.Instant
import kotlin.random.Random
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.json.JSONObject

class MyCustomAppCheckToken(
    private val token: String,
    private val expireTimeMillis: Long,
) : AppCheckToken() {
  override fun getToken(): String = token

  override fun getExpireTimeMillis(): Long = expireTimeMillis
}

class MyCustomAppCheckProvider(val app: MyApplication, val host: String, val port: Int) : AppCheckProvider {

  override fun getToken(): Task<AppCheckToken> {
    val requestId = "acrid" + Random.nextAlphanumericString(length = 8)
    val taskCompletionSource = TaskCompletionSource<AppCheckToken>()

    @OptIn(DelicateCoroutinesApi::class)
    val job = GlobalScope.async { taskCompletionSource.trySetResult(getAppCheckToken(requestId)) }

    job.invokeOnCompletion { throwable ->
      if (throwable !== null) {
        app.log("WARNING: $requestId failed: $throwable")
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
    app.log("$requestId Getting AppCheck Token from server $host:$port")
    val json =
        withContext(Dispatchers.IO) {
          val responseBytes =
              Socket(host, port).use { socket -> readAllBytes(socket.getInputStream()) }
          val responseText = String(responseBytes)

          val jsonParseResult = runCatching { JSONObject(responseText) }
          jsonParseResult.fold(
            onSuccess = {
              val token = if (it.has("token")) it.get("token") else null
              app.log("$requestId Got AppCheck response: token=${token.toString().ellipsized(13)}")
            },
            onFailure = {
              app.log("$requestId Got AppCheck response: $responseText")
            }
          )

          jsonParseResult.getOrThrow()
        }

    val token = json.getString("token")
    val ttlMillis = json.getLong("ttlMillis")
    val expireTimeMillis = Instant.now().toEpochMilli() + ttlMillis - 60000L
    return MyCustomAppCheckToken(token, expireTimeMillis)
  }
}

class MyCustomAppCheckProviderFactory(val app: MyApplication) : AppCheckProviderFactory {
  override fun create(firebaseApp: FirebaseApp): AppCheckProvider {
    return MyCustomAppCheckProvider(app=app, host = HOST, port = PORT)
  }

  companion object {
    const val HOST = "10.0.2.2"
    const val PORT = 9392
  }
}
