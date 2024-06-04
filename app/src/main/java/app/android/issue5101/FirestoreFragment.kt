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
import android.widget.TextView
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.firestore
import java.lang.ref.WeakReference
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FirestoreFragment : Fragment(), LoggerNameAndIdProvider {

  private val logger = Logger("FirestoreFragment")
  override val loggerName
    get() = logger.name

  override val loggerId
    get() = logger.id

  override val loggerNameWithId
    get() = logger.nameWithId

  private val viewModel: MyViewModel by viewModels()
  private val weakThis = WeakReference(this)

  private var logTextView: TextView? = null

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
    val view = inflater.inflate(R.layout.fragment_firestore, container, false)

    view.findViewById<Button>(R.id.logout).setOnClickListener { handleLogoutButtonClick() }
    view.findViewById<Button>(R.id.add_item).setOnClickListener { handleAddItemButtonClick() }
    logTextView = view.findViewById(R.id.log)

    if (savedInstanceState === null) {
      updateUi(viewModel.snapshot.value)
    }
    viewLifecycleOwner.lifecycleScope.launch {
      viewModel.snapshot
          .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.RESUMED)
          .collect { updateUi(it) }
    }

    return view
  }

  override fun onDestroyView() {
    logger.onCreateView()
    logTextView = null
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
  private fun handleLogoutButtonClick() {
    logger.log { "handleLogoutButtonClick()" }
    viewModel.firebaseAuth.signOut()
  }

  @MainThread
  private fun handleAddItemButtonClick() {
    logger.log { "handleAddItemButtonClick()" }
    viewModel.addDocument()
  }

  @MainThread
  private fun updateUi(snapshot: MyViewModel.SnapshotErrorPair?) {
    logger.log { "updateUi()" }
    logTextView?.text = snapshot.toString()
  }

  class MyViewModel : ViewModel() {
    private val logger = Logger("FirestoreFragment.MyViewModel").apply { log { "created" } }

    val firebaseAuth = Firebase.auth
    private val firestore = Firebase.firestore

    private val _snapshot = MutableStateFlow<SnapshotErrorPair?>(null)
    val snapshot = _snapshot.asStateFlow()

    private val weakThis = WeakReference(this)
    private val querySnapshotListener = QuerySnapshotListenerImpl(weakThis)
    private val collectionReference = firestore.collection("AndroidIssue5101")
    private val collectionReferenceListenerRegistration =
        collectionReference.addSnapshotListener(querySnapshotListener)

    override fun onCleared() {
      logger.onCleared()
      weakThis.clear()
      collectionReferenceListenerRegistration.remove()
      super.onCleared()
    }

    @AnyThread
    fun addDocument() {
      logger.log { "addDocument()" }
      val documentReference = collectionReference.document()
      val data = Random.nextAlphanumericString(length = 20)
      logger.log { "addDocument() Creating document ${documentReference.path} with data: $data" }
      documentReference.set(mapOf("data" to Random.nextAlphanumericString(length = 20)))
    }

    data class SnapshotErrorPair(
        val snapshot: QuerySnapshot?,
        val error: FirebaseFirestoreException?
    )

    private class QuerySnapshotListenerImpl(val viewModel: WeakReference<MyViewModel>) :
        EventListener<QuerySnapshot> {

      private val logger =
          Logger("QuerySnapshotListenerImpl").apply {
            log { "Created by ${viewModel.get()?.logger?.nameWithId}" }
          }

      override fun onEvent(snapshot: QuerySnapshot?, error: FirebaseFirestoreException?) {
        logger.log { "onEvent(snapshot=$snapshot, error=$error)" }
        val snapshotErrorPair = SnapshotErrorPair(snapshot, error)
        viewModel.get()?._snapshot?.value = snapshotErrorPair
      }
    }
  }
}
