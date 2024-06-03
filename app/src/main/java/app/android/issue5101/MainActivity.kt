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

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.appcompat.app.AppCompatActivity
import java.lang.ref.WeakReference

class MainActivity : AppCompatActivity() {

  private val logger = Logger("MainActivity")
  private val weakThis = WeakReference(this)
  private val firebaseServiceConnection = FirebaseServiceConnectionImpl(weakThis)

  override fun onCreate(savedInstanceState: Bundle?) {
    logger.onCreate(savedInstanceState)
    super.onCreate(savedInstanceState)

    val serviceIntent = Intent(this, FirebaseService::class.java)
    val bindResult = bindService(serviceIntent, firebaseServiceConnection, BIND_AUTO_CREATE)
    check(bindResult) { "bindService(intent=$serviceIntent) failed" }
  }

  override fun onDestroy() {
    logger.onDestroy()
    weakThis.clear()
    super.onDestroy()
  }

  override fun onStart() {
    logger.onStart()
    super.onStart()
  }

  override fun onStop() {
    logger.onStop()
    super.onStop()
  }

  override fun onResume() {
    logger.onResume()
    super.onResume()
  }

  override fun onPause() {
    logger.onPause()
    super.onPause()
  }

  private class FirebaseServiceConnectionImpl(val activity: WeakReference<MainActivity>) :
      ServiceConnection {

    private val logger = Logger("FirebaseServiceConnectionImpl")

    var service: FirebaseServiceIBinder? = null
      private set

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
      logger.onServiceConnected(name, service)
      this.service = service as FirebaseServiceIBinder
    }

    override fun onServiceDisconnected(name: ComponentName?) {
      logger.onServiceDisconnected(name)
      this.service = null
    }
  }
}
