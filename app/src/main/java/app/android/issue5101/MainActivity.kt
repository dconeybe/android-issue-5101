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
import androidx.activity.viewModels
import androidx.annotation.MainThread
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.android.issue5101.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

  private val logger = Logger("MainActivity")
  private val viewModel: MainViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    logger.onCreate(savedInstanceState)
    super.onCreate(savedInstanceState)

    val binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)

    if (savedInstanceState === null) {
      updateUi(viewModel.firebaseUser.value)
    }
    lifecycleScope.launch {
      viewModel.firebaseUser.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).collect {
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
}
