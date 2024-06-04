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
import androidx.annotation.MainThread
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import app.android.issue5101.databinding.FragmentLoginBinding
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import java.lang.ref.WeakReference
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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

  private var viewBinding: FragmentLoginBinding? = null

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
    val binding = FragmentLoginBinding.inflate(inflater, container, false).also { viewBinding = it }
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    logger.onViewCreated()
    super.onViewCreated(view, savedInstanceState)
    viewBinding!!.login.setOnClickListener { handleLoginButtonClick() }

    if (savedInstanceState === null) {
      updateUi(viewModel.isLoginInProgress.value)
    }
    viewLifecycleOwner.lifecycleScope.launch {
      viewModel.isLoginInProgress
          .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
          .collect { updateUi(it) }
    }
  }

  override fun onDestroyView() {
    logger.onDestroyView()
    viewBinding = null
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
  }

  override fun onPause() {
    logger.onPause()
    super.onPause()
  }

  @MainThread
  private fun updateUi(isLoginInProgress: Boolean) {
    logger.log { "updateUi()" }
    val loginButton = viewBinding!!.login
    val spinner = viewBinding!!.spinner
    if (isLoginInProgress) {
      loginButton.isEnabled = false
      spinner.visibility = View.VISIBLE
    } else {
      loginButton.isEnabled = true
      spinner.visibility = View.GONE
    }
  }

  @MainThread
  private fun handleLoginButtonClick() {
    logger.log { "handleLoginButtonClick()" }
    viewModel.login()
  }

  class MyViewModel : ViewModel() {
    private val firebaseAuth = Firebase.auth

    private val loginJob = MutableStateFlow<Job?>(null)
    val isLoginInProgress =
        loginJob
            .map { it?.isActive ?: false }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    @MainThread
    fun login() {
      val oldJob = loginJob.value
      if (oldJob === null || !oldJob.isActive) {
        viewModelScope
            .launch { firebaseAuth.signInAnonymously().await() }
            .also { job ->
              loginJob.value = job
              job.invokeOnCompletion { loginJob.compareAndSet(job, null) }
            }
      }
    }
  }
}
