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

class MyCustomAppCheckToken(
    private val token: String,
    private val expiration: Long,
) : AppCheckToken() {
  override fun getToken(): String = token

  override fun getExpireTimeMillis(): Long = expiration
}

class MyCustomAppCheckProvider : AppCheckProvider {

  private val logger = Logger("MyCustomAppCheckProvider").apply { log { "created" } }

  override fun getToken(): Task<AppCheckToken> {
    val requestId = "acrid" + Random.nextAlphanumericString(length = 8)
    val taskCompletionSource = TaskCompletionSource<AppCheckToken>()

    @OptIn(DelicateCoroutinesApi::class)
    val job =
        GlobalScope.async(Dispatchers.IO) {
          logger.log { "$requestId Getting AppCheck Token from server $HOST:$PORT" }
          val tokenBytes =
              Socket(HOST, PORT).use { socket -> readAllBytes(socket.getInputStream()) }
          val token = String(tokenBytes)
          logger.log { "$requestId Got AppCheck Token: $token" }
          taskCompletionSource.trySetResult(
              MyCustomAppCheckToken(
                  token = token,
                  expiration = Instant.now().toEpochMilli() + MILLIS_FOR_30_MINUTES - 60000L))
        }

    job.invokeOnCompletion { throwable ->
      if (throwable is Exception) {
        taskCompletionSource.trySetException(throwable)
      } else if (throwable !== null) {
        taskCompletionSource.trySetException(Exception(throwable))
      }
    }

    return taskCompletionSource.task
  }

  private companion object {
    const val HOST = "10.0.2.2"
    const val PORT = 9392
    const val MILLIS_PER_SECOND = 1000
    const val MILLIS_PER_MINUTE = MILLIS_PER_SECOND * 60
    const val MILLIS_FOR_30_MINUTES = MILLIS_PER_MINUTE * 30
  }
}

class MyCustomAppCheckProviderFactory : AppCheckProviderFactory {
  override fun create(firebaseApp: FirebaseApp): AppCheckProvider {
    return MyCustomAppCheckProvider()
  }
}
