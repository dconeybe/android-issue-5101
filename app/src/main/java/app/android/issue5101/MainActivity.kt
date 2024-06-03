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
import androidx.annotation.MainThread
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import java.lang.ref.WeakReference

class MainActivity : AppCompatActivity() {

  private val logger = Logger("MainActivity")
  private val weakThis = WeakReference(this)
  private val firebaseServiceConnection = FirebaseServiceConnectionImpl(weakThis)
  private val firebaseAuthStateListener = FirebaseAuthStateListenerImpl(weakThis)
  private var fragmentTransactionsEnabled = false

  val firebaseService: FirebaseServiceIBinder?
    get() = firebaseServiceConnection.service

  override fun onCreate(savedInstanceState: Bundle?) {
    logger.onCreate(savedInstanceState)
    super.onCreate(savedInstanceState)
    fragmentTransactionsEnabled = true

    setContentView(R.layout.activity_main)

    val serviceIntent = Intent(this, FirebaseService::class.java)
    val bindResult = bindService(serviceIntent, firebaseServiceConnection, BIND_AUTO_CREATE)
    check(bindResult) { "bindService(intent=$serviceIntent) failed" }
  }

  override fun onDestroy() {
    logger.onDestroy()
    fragmentTransactionsEnabled = false
    weakThis.clear()
    firebaseServiceConnection.service?.auth?.removeAuthStateListener(firebaseAuthStateListener)
    unbindService(firebaseServiceConnection)
    super.onDestroy()
  }

  override fun onSaveInstanceState(outState: Bundle) {
    logger.onSaveInstanceState()
    fragmentTransactionsEnabled = false
    super.onSaveInstanceState(outState)
  }

  override fun onStart() {
    logger.onStart()
    super.onStart()

    fragmentTransactionsEnabled = true
    updateUi()
  }

  override fun onStop() {
    logger.onStop()
    super.onStop()
  }

  override fun onResume() {
    logger.onResume()
    super.onResume()

    fragmentTransactionsEnabled = true
    updateUi()
  }

  override fun onPause() {
    logger.onPause()
    super.onPause()
  }

  @MainThread
  private fun updateUi() {
    logger.log { "updateUi()" }

    logger.log { "updateUi() fragmentTransactionsEnabled = $fragmentTransactionsEnabled" }
    if (!fragmentTransactionsEnabled) {
      return
    }

    val auth = firebaseServiceConnection.service?.auth
    logger.log { "updateUi() firebaseServiceConnection.service?.auth = $auth" }
    if (auth === null) {
      return
    }

    val fragmentManager = supportFragmentManager
    val loginFragment = fragmentManager.findFragmentByTag(FRAGMENT_TAG_LOGIN)
    val firestoreFragment = fragmentManager.findFragmentByTag(FRAGMENT_TAG_FIRESTORE)

    val currentUser = auth.currentUser
    logger.log { "updateUi() currentUser = $currentUser" }

    val newFragment: Fragment?
    val newFragmentTag: String
    val oldFragment: Fragment?
    if (currentUser === null) {
      oldFragment = firestoreFragment
      newFragment = if (loginFragment !== null) null else LoginFragment()
      newFragmentTag = FRAGMENT_TAG_LOGIN
    } else {
      oldFragment = loginFragment
      newFragment = if (firestoreFragment !== null) null else FirestoreFragment()
      newFragmentTag = FRAGMENT_TAG_FIRESTORE
    }

    logger.log {
      "updateUi() oldFragment=${oldFragment?.loggerNameWithId}" +
          " newFragment=${newFragment?.loggerNameWithId}"
    }
    val fragmentTransaction = fragmentManager.beginTransaction()
    if (oldFragment !== null) {
      fragmentTransaction.remove(oldFragment)
    }
    if (newFragment !== null) {
      fragmentTransaction.add(R.id.bsrzamz8rn, newFragment, newFragmentTag)
    }
    fragmentTransaction.commitNow()
  }

  @MainThread
  private fun onFirebaseServiceConnected(
      connection: FirebaseServiceConnectionImpl,
      service: FirebaseServiceIBinder
  ) {
    logger.log { "onFirebaseServiceConnected()" }
    require(connection === firebaseServiceConnection)

    service.auth.addAuthStateListener(firebaseAuthStateListener)

    updateUi()
  }

  @MainThread
  private fun onFirebaseAuthStateChanged(listener: FirebaseAuthStateListenerImpl) {
    logger.log { "onFirebaseAuthStateChanged()" }
    require(listener === firebaseAuthStateListener)
    updateUi()
  }

  private class FirebaseServiceConnectionImpl(val activity: WeakReference<MainActivity>) :
      ServiceConnection {

    private val logger =
        Logger("FirebaseServiceConnectionImpl").apply {
          log { "Created by ${activity.get()?.logger?.nameWithId}" }
        }

    var service: FirebaseServiceIBinder? = null
      private set

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
      logger.onServiceConnected(name, service)
      val firebaseServiceIBinder = service as FirebaseServiceIBinder
      this.service = firebaseServiceIBinder
      activity.get()?.onFirebaseServiceConnected(this, firebaseServiceIBinder)
    }

    override fun onServiceDisconnected(name: ComponentName?) {
      logger.onServiceDisconnected(name)
      this.service = null
    }
  }

  private companion object {
    const val FRAGMENT_TAG_LOGIN = "tfqapd9z35.LoginFragment"
    const val FRAGMENT_TAG_FIRESTORE = "dn478zdc25.FirestoreFragment"
  }

  private class FirebaseAuthStateListenerImpl(val activity: WeakReference<MainActivity>) :
      FirebaseAuth.AuthStateListener {

    private val logger =
        Logger("FirebaseAuthStateListenerImpl").apply {
          log { "Created by ${activity.get()?.logger?.nameWithId}" }
        }

    override fun onAuthStateChanged(auth: FirebaseAuth) {
      logger.log { "onAuthStateChanged()" }
      activity.get()?.runOnUiThread { activity.get()?.onFirebaseAuthStateChanged(this) }
    }
  }
}
