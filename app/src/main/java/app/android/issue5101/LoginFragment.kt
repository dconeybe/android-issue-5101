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

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.annotation.MainThread
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import java.lang.ref.WeakReference
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.tasks.await

class LoginFragment : Fragment(), LoggerNameAndIdProvider {

  private val logger = Logger("LoginFragment")
  override val loggerName
    get() = logger.name

  override val loggerId
    get() = logger.id

  override val loggerNameWithId
    get() = logger.nameWithId

  private val weakThis = WeakReference(this)

  private val viewModel: MyViewModel by viewModels()

  private var loginButton: Button? = null
  private var spinner: View? = null

  override fun onAttach(context: Context) {
    logger.onAttach(context)
    super.onAttach(context)
  }

  override fun onDetach() {
    logger.onDetach()
    super.onDetach()
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    logger.onCreate(savedInstanceState)
    super.onCreate(savedInstanceState)
  }

  override fun onDestroy() {
    logger.onDestroy()
    weakThis.clear()
    super.onDestroy()
  }

  override fun onViewStateRestored(savedInstanceState: Bundle?) {
    logger.onViewStateRestored()
    super.onViewStateRestored(savedInstanceState)
  }

  override fun onSaveInstanceState(outState: Bundle) {
    logger.onSaveInstanceState()
    super.onSaveInstanceState(outState)
  }

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?
  ): View {
    logger.onCreateView()
    val view = inflater.inflate(R.layout.fragment_login, container, false)

    loginButton =
        view.findViewById<Button>(R.id.login).apply {
          setOnClickListener { handleLoginButtonClick() }
        }
    spinner = view.findViewById(R.id.spinner)

    return view
  }

  override fun onDestroyView() {
    logger.onDestroyView()
    spinner = null
    loginButton = null
    super.onDestroyView()
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
    logger.log { "updateUi()" }
    if (viewModel.isLoginInProgress) {
      loginButton?.isEnabled = false
      spinner?.visibility = View.VISIBLE
    } else {
      loginButton?.isEnabled = true
      spinner?.visibility = View.GONE
    }
  }

  @MainThread
  private fun handleLoginButtonClick() {
    logger.log { "handleLoginButtonClick()" }
    viewModel.startLogin()
    updateUi()
  }

  class MyViewModel : ViewModel() {
    private val firebaseAuth = Firebase.auth
    private var loginJob: Deferred<Unit>? = null

    val isLoginInProgress: Boolean
      get() = loginJob?.isActive ?: false

    fun startLogin() {
      check(!isLoginInProgress) { "must not be called when isLoginInProgress=$isLoginInProgress" }
      loginJob = viewModelScope.async { firebaseAuth.signInAnonymously().await() }
    }
  }
}
