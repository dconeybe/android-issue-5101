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
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import androidx.annotation.MainThread
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.AtomicReference
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.tasks.await
import java.lang.ref.WeakReference
import kotlin.random.Random

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
  private val querySnapshotListener = QuerySnapshotListenerImpl(weakThis)

  private lateinit var itemsRecyclerView: RecyclerView

  private val collectionReference: CollectionReference? get() = (context as MainActivity?)?.firebaseService?.let { viewModel.getCollectionReference(it) }

  override fun onCreate(savedInstanceState: Bundle?) {
    logger.onCreate(savedInstanceState)
    super.onCreate(savedInstanceState)
  }

  override fun onDestroy() {
    logger.onDestroy()
    weakThis.clear()
    super.onDestroy()
  }

  override fun onAttach(context: Context) {
    logger.onAttach(context)
    super.onAttach(context)
  }

  override fun onDetach() {
    logger.onDetach()
    super.onDetach()
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
    itemsRecyclerView = view.findViewById(R.id.items)
    itemsRecyclerView.

    return view
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
  fun onFirestoreAvailable(activity: MainActivity) {

  }

  @MainThread
  private fun handleLogoutButtonClick() {
    logger.log { "handleLogoutButtonClick()" }
    (context as MainActivity).firebaseService?.auth?.signOut()
  }

  @MainThread
  private fun handleAddItemButtonClick() {
    logger.log { "handleAddItemButtonClick()" }
    val documentReference = collectionReference?.document()
    logger.log { "handleAddItemButtonClick() documentReference=${documentReference?.path}" }
    if (documentReference === null) {
      return
    }

    val data = Random.nextAlphanumericString(length = 20)
    logger.log { "handleAddItemButtonClick() Creating document ${documentReference.path} with data: $data" }
    documentReference.set(mapOf("data" to Random.nextAlphanumericString(length = 20)))
  }

  @MainThread
  private fun onItemsChanged(listener: QuerySnapshotListenerImpl, snapshot: QuerySnapshot) {
    logger.log { "onItemsChanged()" }
    itemsListView.adapter = ArrayAdapter(context)
  }

  private class QuerySnapshotListenerImpl(val fragment: WeakReference<FirestoreFragment>) : EventListener<QuerySnapshot> {

    private val logger =
      Logger("QuerySnapshotListenerImpl").apply {
        log { "Created by ${fragment.get()?.logger?.nameWithId}" }
      }

    override fun onEvent(snapshot: QuerySnapshot?, error: FirebaseFirestoreException?) {
      logger.log { "onEvent(snapshot=$snapshot, error=$error)" }
      if (snapshot !== null) {
        fragment.get()?.onItemsChanged(this, snapshot)
      }
    }
  }

  private class MyViewModel : ViewModel() {

    private val collectionReference = AtomicReference<CollectionReference?>(null)

    fun getCollectionReference(service: FirebaseServiceIBinder) : CollectionReference {
      return collectionReference.get() ?: service.firestore.collection("AndroidIssue5101").also { collectionReference.set(it) }
    }

  }

  private class MyItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val textViewItem: TextView = itemView.findViewById(R.id.textViewItem)
  }

  class MyItemsAdapter : RecyclerView.Adapter<MyItemViewHolder>() {

    var items: List<String> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyItemViewHolder {
      val itemView = LayoutInflater.from(parent.context).inflate(R.layout.list_item, parent, false)
      return ItemViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: MyItemViewHolder, position: Int) {
      holder.textViewItem.text = items[position]
    }

    override fun getItemCount(): Int {
      return items.size
    }
  }

}
