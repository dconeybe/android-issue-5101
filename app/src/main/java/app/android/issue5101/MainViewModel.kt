package app.android.issue5101

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.tasks.Task
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GetTokenResult
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import java.lang.ref.WeakReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
  private val logger = Logger("MainViewModel").apply { log { "created" } }
  private val weakThis = WeakReference(this)
  private val firebaseAuthStateListener = FirebaseAuthStateListenerImpl(weakThis)
  private val firebaseAuthIdTokenListener = FirebaseAuthIdTokenListenerImpl(weakThis)
  private val firebaseAuth = Firebase.auth

  init {
    FirebaseFirestore.setLoggingEnabled(true)
    firebaseAuth.addAuthStateListener(firebaseAuthStateListener)
    firebaseAuth.addIdTokenListener(firebaseAuthIdTokenListener)
  }

  private val _firebaseUser = MutableStateFlow<FirebaseUser?>(null)
  val firebaseUser = _firebaseUser.asStateFlow()

  init {
    viewModelScope.launch { firebaseUser.collect { logger.log { "FirebaseUser: $it" } } }
  }

  private val _firebaseUserIdToken =
      MutableStateFlow(SequencedReference<FirebaseUserIdToken?>(nextSequenceNumber(), null))
  val firebaseUserIdToken =
      _firebaseUserIdToken
          .map { it.ref }
          .stateIn(viewModelScope, SharingStarted.Eagerly, _firebaseUserIdToken.value.ref)

  init {
    viewModelScope.launch {
      firebaseUserIdToken.collect { logger.log { "FirebaseUserIdToken: $it" } }
    }
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

  private fun onAuthStateChanged(listener: FirebaseAuthStateListenerImpl, user: FirebaseUser?) {
    logger.log { "onAuthStateChanged(): user=$user" }
    require(listener === firebaseAuthStateListener)
    _firebaseUser.value = user
  }

  private fun onIdTokenChanged(
      listener: FirebaseAuthIdTokenListenerImpl,
      task: Task<GetTokenResult>?
  ) {
    logger.log { "onIdTokenChanged()" }
    val sequenceNumber = nextSequenceNumber()
    if (task === null) {
      updateIdToken(SequencedReference(sequenceNumber, null))
    } else {
      task.addOnSuccessListener { getTokenResult ->
        updateIdToken(
            SequencedReference(
                sequenceNumber,
                FirebaseUserIdToken(
                    token = getTokenResult.token,
                    authTimestamp = getTokenResult.authTimestamp,
                    issuedAtTimestamp = getTokenResult.issuedAtTimestamp,
                    expirationTimestamp = getTokenResult.expirationTimestamp,
                    signInProvider = getTokenResult.signInProvider,
                )))
      }
    }
  }

  private fun updateIdToken(newToken: SequencedReference<FirebaseUserIdToken?>) {
    while (true) {
      val oldValue = _firebaseUserIdToken.value
      if (oldValue.sequenceNumber > newToken.sequenceNumber) {
        break
      }
      if (_firebaseUserIdToken.compareAndSet(oldValue, newToken)) {
        break
      }
    }
  }

  data class FirebaseUserIdToken(
      val token: String?,
      val authTimestamp: Long,
      val issuedAtTimestamp: Long,
      val expirationTimestamp: Long,
      val signInProvider: String?
  )

  suspend fun nextSequenceNumber(value: String): Long =
      sequenceNumberService.binder.filterNotNull().first().nextSequenceNumber(value)

  private class FirebaseAuthStateListenerImpl(val viewModel: WeakReference<MainViewModel>) :
      FirebaseAuth.AuthStateListener {
    override fun onAuthStateChanged(auth: FirebaseAuth) {
      viewModel.get()?.onAuthStateChanged(this, auth.currentUser)
    }
  }

  private class FirebaseAuthIdTokenListenerImpl(val viewModel: WeakReference<MainViewModel>) :
      FirebaseAuth.IdTokenListener {
    override fun onIdTokenChanged(auth: FirebaseAuth) {
      viewModel.get()?.onIdTokenChanged(this, auth.currentUser?.getIdToken(false))
    }
  }
}
