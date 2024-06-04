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
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import app.android.issue5101.databinding.FragmentFirestoreBinding
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.firestore
import java.lang.ref.WeakReference
import java.time.Instant
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class FirestoreFragment : Fragment(), LoggerNameAndIdProvider {

  private val logger = Logger("FirestoreFragment")
  override val loggerName
    get() = logger.name

  override val loggerId
    get() = logger.id

  override val loggerNameWithId
    get() = logger.nameWithId

  private val viewModel: MyViewModel by viewModels()

  private var viewBinding: FragmentFirestoreBinding? = null

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

    viewBinding =
        FragmentFirestoreBinding.inflate(inflater, container, false).apply {
          logout.setOnClickListener { viewModel.logout() }
          addItem.setOnClickListener { viewModel.addDocument() }
          deleteItem.setOnClickListener { viewModel.deleteDocument() }
        }

    if (savedInstanceState === null) {
      updateUi(viewModel.collectionReference, viewModel.snapshot.value)
    }
    viewLifecycleOwner.lifecycleScope.launch {
      viewModel.snapshot
          .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
          .collect { updateUi(viewModel.collectionReference, it) }
    }

    return viewBinding!!.root
  }

  override fun onDestroyView() {
    logger.onCreateView()
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
  private fun handleLogoutButtonClick() {
    logger.log { "handleLogoutButtonClick()" }
  }

  @MainThread
  private fun handleAddItemButtonClick() {
    logger.log { "handleAddItemButtonClick()" }
  }

  @MainThread
  private fun updateUi(
      collectionReference: CollectionReference,
      snapshot: MyViewModel.SnapshotInfo?
  ) {
    logger.log { "updateUi() snapshot=$snapshot" }

    val text = buildString {
      append("Collection: ${collectionReference.path}")
      if (snapshot !== null) {
        append('\n')
        append("Time: ${snapshot.time}")
        append('\n')
        append("Sequence Number: ${snapshot.sequenceNumber}")
        append('\n')
        append("Snapshot ID: ${snapshot.snapshotId}")
        append('\n')

        append("Num Documents: ")
        if (snapshot.snapshot !== null) {
          append(snapshot.snapshot.size())
        } else {
          append("n/a")
        }
        append('\n')

        append("Error: ")
        if (snapshot.error !== null) {
          append(snapshot.error.message)
        } else {
          append("none")
        }
      }
    }

    viewBinding?.log?.text = text
  }

  class MyViewModel(application: Application) : AndroidViewModel(application) {
    private val logger = Logger("FirestoreFragment.MyViewModel").apply { log { "created" } }

    val firebaseAuth = Firebase.auth
    private val firestore = Firebase.firestore

    private val _snapshot = MutableStateFlow<SnapshotInfo?>(null)
    val snapshot = _snapshot.asStateFlow()

    private val weakThis = WeakReference(this)
    private val querySnapshotListener = QuerySnapshotListenerImpl(weakThis)
    val collectionReference = firestore.collection("AndroidIssue5101")
    private val collectionReferenceListenerRegistration =
        collectionReference.addSnapshotListener(querySnapshotListener)

    private val sequenceNumberService =
        SequenceNumberServiceConnectionImpl(getApplication()).apply { bind() }

    override fun onCleared() {
      logger.onCleared()
      weakThis.clear()
      sequenceNumberService.unbind()
      collectionReferenceListenerRegistration.remove()
      super.onCleared()
    }

    @AnyThread
    fun logout() {
      logger.log { "logout()" }
      firebaseAuth.signOut()
    }

    @AnyThread
    fun addDocument() {
      logger.log { "addDocument()" }
      val documentReference = collectionReference.document()
      val data = Random.nextAlphanumericString(length = 20)
      logger.log { "addDocument() Creating document ${documentReference.path} with data: $data" }
      documentReference.set(mapOf("data" to Random.nextAlphanumericString(length = 20)))
    }

    @AnyThread
    fun deleteDocument() {
      val operationId = "dd" + Random.nextAlphanumericString(length = 8)
      logger.log { "deleteDocument() operationId=$operationId" }
      viewModelScope.launch {
        val snapshotResult = collectionReference.get().runCatching { await() }
        val snapshot =
            snapshotResult.fold(
                onSuccess = { it },
                onFailure = {
                  logger.warn(it) { "deleteDocument() operationId=$operationId get failed" }
                  null
                },
            )

        if (snapshot !== null && snapshot.size() > 0) {
          val documentReference = snapshot.documents.random().reference
          logger.log {
            "deleteDocument() operationId=$operationId" +
                " deleting document ${documentReference.path}"
          }
          val deleteResult = documentReference.delete().runCatching { await() }
          deleteResult.onFailure {
            logger.warn(it) { "deleteDocument() operationId=$operationId delete failed" }
          }
        }
      }
    }

    data class SnapshotInfo(
        val time: Instant,
        val sequenceNumber: Long,
        val snapshotId: String,
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
        val time = Instant.now()
        val snapshotId = "spst${Random.nextAlphanumericString(length = 8)}"
        logger.log { "onEvent(snapshot=$snapshot, error=$error) snapshotId=$snapshotId" }
        val viewModelScope = viewModel.get()?.viewModelScope ?: return
        val sequenceNumberService = viewModel.get()?.sequenceNumberService ?: return
        viewModelScope.launch {
          val sequenceNumber =
              sequenceNumberService.binder.filterNotNull().first().nextSequenceNumber(snapshotId)
          val snapshotInfo = SnapshotInfo(time, sequenceNumber, snapshotId, snapshot, error)
          viewModel.get()?._snapshot?.value = snapshotInfo
        }
      }
    }
  }
}
