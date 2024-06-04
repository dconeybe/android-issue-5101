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
import androidx.annotation.MainThread
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import java.lang.ref.WeakReference

class MainActivity : AppCompatActivity() {

  private val logger = Logger("MainActivity")
  private val weakThis = WeakReference(this)
  private val firebaseAuthStateListener = FirebaseAuthStateListenerImpl(weakThis)
  private val firebaseAuthIdTokenListener = FirebaseAuthIdTokenListenerImpl(weakThis)

  private lateinit var firebaseAuth: FirebaseAuth

  override fun onCreate(savedInstanceState: Bundle?) {
    logger.onCreate(savedInstanceState)
    super.onCreate(savedInstanceState)

    setContentView(R.layout.activity_main)

    firebaseAuth = Firebase.auth
    firebaseAuth.addAuthStateListener(firebaseAuthStateListener)
    firebaseAuth.addIdTokenListener(firebaseAuthIdTokenListener)

    logger.log { "onCreate() savedInstanceState===null: ${savedInstanceState === null}" }
    if (savedInstanceState === null) {
      val currentUser = firebaseAuth.currentUser
      logger.log { "onCreate() currentUser=$currentUser" }
      val (initialFragment, initialFragmentTag) =
          if (currentUser === null) Pair(LoginFragment(), FRAGMENT_TAG_LOGIN)
          else Pair(FirestoreFragment(), FRAGMENT_TAG_FIRESTORE)
      logger.log {
        "onCreate() adding fragment ${initialFragment.loggerNameWithId} with tag=$initialFragmentTag"
      }
      supportFragmentManager
          .beginTransaction()
          .add(R.id.bsrzamz8rn, initialFragment, initialFragmentTag)
          .commit()
    }
  }

  override fun onDestroy() {
    logger.onDestroy()
    weakThis.clear()
    firebaseAuth.removeAuthStateListener(firebaseAuthStateListener)
    firebaseAuth.removeIdTokenListener(firebaseAuthIdTokenListener)
    super.onDestroy()
  }

  override fun onRestoreInstanceState(savedInstanceState: Bundle) {
    logger.onRestoreInstanceState()
    super.onRestoreInstanceState(savedInstanceState)
  }

  override fun onSaveInstanceState(outState: Bundle) {
    logger.onSaveInstanceState()
    super.onSaveInstanceState(outState)
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
    updateUi()
  }

  override fun onPause() {
    logger.onPause()
    super.onPause()
  }

  @MainThread
  private fun updateUi() {
    val currentUser = firebaseAuth.currentUser
    val loginFragment = supportFragmentManager.findFragmentByTag(FRAGMENT_TAG_LOGIN)
    val firestoreFragment = supportFragmentManager.findFragmentByTag(FRAGMENT_TAG_FIRESTORE)

    logger.log {
      "updateUi()" +
          " currentUser=$currentUser" +
          " loginFragment=${loginFragment?.loggerNameWithId}" +
          " firestoreFragment=${firestoreFragment?.loggerNameWithId}"
    }

    val showFragment: Fragment?
    val hideFragment: Fragment?
    val showFragmentTag: String
    if (currentUser === null) {
      showFragment = if (loginFragment === null) LoginFragment() else null
      showFragmentTag = FRAGMENT_TAG_LOGIN
      hideFragment = firestoreFragment
    } else {
      showFragment = if (firestoreFragment === null) FirestoreFragment() else null
      showFragmentTag = FRAGMENT_TAG_FIRESTORE
      hideFragment = loginFragment
    }

    logger.log {
      "updateUi()" +
          " showFragment=${showFragment?.loggerNameWithId}" +
          " showFragmentTag=$showFragmentTag" +
          " hideFragment=${hideFragment?.loggerNameWithId}"
    }

    supportFragmentManager
        .beginTransaction()
        .apply {
          if (showFragment !== null) {
            add(R.id.bsrzamz8rn, showFragment, showFragmentTag)
          }
          if (hideFragment !== null) {
            remove(hideFragment)
          }
        }
        .commit()
  }

  @MainThread
  private fun onFirebaseAuthStateChanged(listener: FirebaseAuthStateListenerImpl) {
    logger.log { "onFirebaseAuthStateChanged()" }
    require(listener === firebaseAuthStateListener)
    if (lifecycle.currentState == Lifecycle.State.RESUMED) {
      updateUi()
    }
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

  private class FirebaseAuthIdTokenListenerImpl(val activity: WeakReference<MainActivity>) :
      FirebaseAuth.IdTokenListener {

    private val logger =
        Logger("FirebaseAuthIdTokenListenerImpl").apply {
          log { "Created by ${activity.get()?.logger?.nameWithId}" }
        }

    override fun onIdTokenChanged(auth: FirebaseAuth) {
      logger.log { "onIdTokenChanged()" }
    }
  }

  private companion object {
    const val FRAGMENT_TAG_LOGIN = "tfqapd9z35.LoginFragment"
    const val FRAGMENT_TAG_FIRESTORE = "dn478zdc25.FirestoreFragment"
  }
}
