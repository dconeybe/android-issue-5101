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

import android.os.Bundle
import android.util.Log
import kotlin.random.Random

class Logger(val name: String) {

  val id: String = Random.nextAlphanumericString(length = 10)

  val nameWithId: String by lazy(LazyThreadSafetyMode.PUBLICATION) { "$name[$id]" }
}

fun Logger.log(block: () -> String) {
  Log.i("Issue5101", "${nameWithId}: ${block()}")
}

fun Logger.onCreate(savedInstanceState: Bundle?) {
  log { "onCreate(savedInstanceState=$savedInstanceState)" }
}

fun Logger.onDestroy() {
  log { "onDestroy()" }
}

fun Logger.onResume() {
  log { "onResume()" }
}

fun Logger.onPause() {
  log { "onPause()" }
}

fun Logger.onStart() {
  log { "onStart()" }
}

fun Logger.onStop() {
  log { "onStop()" }
}
