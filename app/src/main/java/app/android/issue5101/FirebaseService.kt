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
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import java.lang.ref.WeakReference

class FirebaseService : Service() {

  private val logger = Logger("FirebaseService")
  private val weakThis = WeakReference(this)
  private lateinit var binder: FirebaseServiceIBinder

  override fun onCreate() {
    logger.onCreate()
    super.onCreate()
    binder = FirebaseServiceBinderImpl(weakThis)
  }

  override fun onDestroy() {
    logger.onDestroy()
    weakThis.clear()
    super.onDestroy()
  }

  override fun onBind(intent: Intent): IBinder {
    logger.onBind(intent)

    val serviceIntent = Intent(this, FirebaseService::class.java)
    val componentName = startService(serviceIntent)
    checkNotNull(componentName) { "starting service failed: $serviceIntent" }

    return binder
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    logger.onStartCommand(intent, flags, startId)
    super.onStartCommand(intent, flags, startId)
    return START_NOT_STICKY
  }

  private class FirebaseServiceBinderImpl(val service: WeakReference<FirebaseService>) :
      Binder(), FirebaseServiceIBinder {

    private val logger =
        Logger("FirebaseServiceBinderImpl").apply {
          log { "Created by ${service.get()?.logger?.nameWithId}" }
        }

    override val auth: FirebaseAuth
    override val firestore: FirebaseFirestore

    init {
      FirebaseFirestore.setLoggingEnabled(true)
      logger.log { "auth = Firebase.auth" }
      auth = Firebase.auth
      logger.log { "firestore = Firebase.firestore" }
      firestore = Firebase.firestore
    }
  }
}

interface FirebaseServiceIBinder : IBinder {
  val auth: FirebaseAuth
  val firestore: FirebaseFirestore
}
