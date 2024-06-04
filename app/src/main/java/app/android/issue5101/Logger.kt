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

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import kotlin.random.Random

class Logger(val name: String) {

  val id: String = Random.nextAlphanumericString(length = 10)

  val nameWithId: String = "$name[$id]"

  val loggerNameAndIdProvider =
      object : LoggerNameAndIdProvider {
        override val loggerName
          get() = name

        override val loggerId
          get() = id

        override val loggerNameWithId
          get() = nameWithId
      }
}

interface LoggerNameAndIdProvider {
  val loggerName: String
  val loggerId: String
  val loggerNameWithId: String
}

val Any.loggerNameWithId: String
  get() = (this as? LoggerNameAndIdProvider)?.loggerNameWithId ?: toString()

fun Logger.log(block: () -> String) {
  Log.i("Issue5101", "${nameWithId}: ${block()}")
}

fun Logger.onCreate(savedInstanceState: Bundle?) {
  log { "onCreate(savedInstanceState=$savedInstanceState)" }
}

fun Logger.onAttach(context: Context) {
  log { "onAttach(context=$context)" }
}

fun Logger.onDetach() {
  log { "onDetach()" }
}

fun Logger.onCreate() {
  log { "onCreate()" }
}

fun Logger.onDestroy() {
  log { "onDestroy()" }
}

fun Logger.onRestoreInstanceState() {
  log { "onRestoreInstanceState()" }
}

fun Logger.onSaveInstanceState() {
  log { "onSaveInstanceState()" }
}

fun Logger.onViewStateRestored() {
  log { "onViewStateRestored()" }
}

fun Logger.onCreateView() {
  log { "onCreateView()" }
}

fun Logger.onDestroyView() {
  log { "onDestroyView()" }
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

fun Logger.onStartCommand(intent: Intent?, flags: Int, startId: Int) {
  log {
    "onStartCommand(intent=$intent, flags=${nameFromOnStartCommandFlags(flags)}, startId=$startId})"
  }
}

fun Logger.onBind(intent: Intent) {
  log { "onBind(intent=$intent)" }
}

fun Logger.onServiceConnected(name: ComponentName?, service: IBinder?) {
  log { "onServiceConnected(name=$name, service=$service)" }
}

fun Logger.onServiceDisconnected(name: ComponentName?) {
  log { "onServiceDisconnected(name=$name)" }
}

private fun nameFromOnStartCommandFlags(flags: Int): String {
  if (flags == 0) {
    return "0"
  }

  val flagNames = mutableSetOf<String>()
  var flagsFound = 0

  if ((flags and Service.START_FLAG_REDELIVERY) == Service.START_FLAG_REDELIVERY) {
    flagNames.add("START_FLAG_REDELIVERY")
    flagsFound = flagsFound or Service.START_FLAG_REDELIVERY
  }
  if ((flags and Service.START_FLAG_RETRY) == Service.START_FLAG_RETRY) {
    flagNames.add("START_FLAG_RETRY")
    flagsFound = flagsFound or Service.START_FLAG_RETRY
  }

  val sortedFlagNames = flagNames.sorted().toMutableList()

  val unusedFlags = flagsFound.inv() and flags
  if (unusedFlags != 0) {
    sortedFlagNames.add("0x${unusedFlags.toString(16).uppercase().padStart(8, '0')}")
  }

  return sortedFlagNames.joinToString("|")
}
