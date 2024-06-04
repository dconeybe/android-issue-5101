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

import android.app.Application
import android.os.Bundle
import androidx.activity.viewModels
import androidx.annotation.MainThread
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import app.android.issue5101.databinding.ActivityMainBinding
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import java.lang.ref.WeakReference
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainActivity : AppCompatActivity() {

  private val logger = Logger("MainActivity")
  private val viewModel: MyViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    logger.onCreate(savedInstanceState)
    super.onCreate(savedInstanceState)

    val binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)

    if (savedInstanceState === null) {
      updateUi(viewModel.currentUser.value)
    }
    lifecycleScope.launch {
      viewModel.currentUser.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).collect {
        updateUi(it)
      }
    }
  }

  override fun onDestroy() {
    logger.onDestroy()
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
  }

  override fun onPause() {
    logger.onPause()
    super.onPause()
  }

  @MainThread
  private fun updateUi(currentUser: FirebaseUser?) {
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

  private companion object {
    const val FRAGMENT_TAG_LOGIN = "tfqapd9z35.LoginFragment"
    const val FRAGMENT_TAG_FIRESTORE = "dn478zdc25.FirestoreFragment"
  }

  class MyViewModel(application: Application) : AndroidViewModel(application) {
    private val logger = Logger("FirestoreFragment.MyViewModel").apply { log { "created" } }
    private val weakThis = WeakReference(this)
    private val firebaseAuthStateListener = FirebaseAuthStateListenerImpl(weakThis)
    private val firebaseAuthIdTokenListener = FirebaseAuthIdTokenListenerImpl(weakThis)
    private val firebaseAuth = Firebase.auth

    private val _currentUser = MutableStateFlow<FirebaseUser?>(null)
    val currentUser = _currentUser.asStateFlow()

    init {
      firebaseAuth.addAuthStateListener(firebaseAuthStateListener)
      firebaseAuth.addIdTokenListener(firebaseAuthIdTokenListener)
    }

    private val sequenceNumberService =
        SequenceNumberServiceConnectionImpl(getApplication()).apply { bind() }

    override fun onCleared() {
      logger.onCleared()
      weakThis.clear()
      sequenceNumberService.unbind()
      firebaseAuth.removeIdTokenListener(firebaseAuthIdTokenListener)
      firebaseAuth.removeAuthStateListener(firebaseAuthStateListener)
      super.onCleared()
    }

    private class FirebaseAuthStateListenerImpl(val viewModel: WeakReference<MyViewModel>) :
        FirebaseAuth.AuthStateListener {

      private val logger =
          Logger("FirebaseAuthStateListenerImpl").apply {
            log { "Created by ${viewModel.get()?.logger?.nameWithId}" }
          }

      override fun onAuthStateChanged(auth: FirebaseAuth) {
        val currentUser = auth.currentUser
        logger.log { "onAuthStateChanged(): currentUser=$currentUser" }
        viewModel.get()?._currentUser?.value = currentUser
      }
    }

    private class FirebaseAuthIdTokenListenerImpl(val viewModel: WeakReference<MyViewModel>) :
        FirebaseAuth.IdTokenListener {

      private val logger =
          Logger("FirebaseAuthIdTokenListenerImpl").apply {
            log { "Created by ${viewModel.get()?.logger?.nameWithId}" }
          }

      override fun onIdTokenChanged(auth: FirebaseAuth) {
        val viewModelScope = viewModel.get()?.viewModelScope ?: return
        val sequenceNumberService = viewModel.get()?.sequenceNumberService ?: return

        viewModelScope.launch {
          val callbackId = "oitc${Random.nextAlphanumericString(length=8)}"
          val sequenceNumber =
              sequenceNumberService.binder.filterNotNull().first().nextSequenceNumber(callbackId)
          val currentUser = auth.currentUser
          val logPrefix =
              "onIdTokenChanged()" +
                  " sequenceNumber=$sequenceNumber" +
                  " callbackId=$callbackId" +
                  " currentUser=$currentUser"
          logger.log { logPrefix }

          if (currentUser !== null) {
            val result = currentUser.getIdToken(false).await()
            logger.log { logPrefix + " token=${result.token}" }
            logger.log { logPrefix + " authTimestamp=${result.authTimestamp}" }
            logger.log { logPrefix + " issuedAtTimestamp=${result.issuedAtTimestamp}" }
            logger.log { logPrefix + " expirationTimestamp=${result.expirationTimestamp}" }
            logger.log { logPrefix + " signInProvider=${result.signInProvider}" }
          }
        }
      }
    }
  }
}
