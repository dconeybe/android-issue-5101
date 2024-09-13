package app.android.issue5101

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.google.firebase.Firebase
import com.google.firebase.appcheck.AppCheckToken
import com.google.firebase.appcheck.FirebaseAppCheck.AppCheckListener
import com.google.firebase.appcheck.appCheck
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.firestore
import java.lang.ref.WeakReference

class MainViewModel(application: Application) : AndroidViewModel(application) {
  private val weakThis = WeakReference(this).apply { addCloseable { clear() } }
  private val appCheckListenerImpl = AppCheckListenerImpl(weakThis)
  private val querySnapshotEventListenerImpl = QuerySnapshotEventListenerImpl(weakThis)

  init {
    val app = getApplication<MyApplication>()
    FirebaseFirestore.setLoggingEnabled(true)

    Firebase.appCheck.let { appCheck ->
      app.log("installAppCheckProviderFactory()")
      appCheck.installAppCheckProviderFactory(MyCustomAppCheckProviderFactory(app))
      appCheck.addAppCheckListener(appCheckListenerImpl)
      addCloseable { appCheck.removeAppCheckListener(appCheckListenerImpl) }
    }

    Firebase.firestore.let { firestore ->
      val collectionReference = firestore.collection("tny753e84m")
      app.log("addSnapshotListener(${collectionReference.path})")
      val registration = collectionReference.addSnapshotListener(querySnapshotEventListenerImpl)
      addCloseable(registration::remove)
    }
  }

  private class QuerySnapshotEventListenerImpl(
      private val viewModel: WeakReference<MainViewModel>
  ) : EventListener<QuerySnapshot> {
    override fun onEvent(snapshot: QuerySnapshot?, error: FirebaseFirestoreException?) {
      val app = viewModel.get()?.getApplication<MyApplication>() ?: return
      if (error !== null) {
        app.log("addSnapshotListener() ERROR: $error")
      } else if (snapshot !== null) {
        app.log("addSnapshotListener() got snapshot with ${snapshot.size()} documents")
      } else {
        app.log("addSnapshotListener() INTERNAL ERROR: snapshot==null && error==null")
      }
    }
  }

  private class AppCheckListenerImpl(private val viewModel: WeakReference<MainViewModel>) :
      AppCheckListener {
    override fun onAppCheckTokenChanged(token: AppCheckToken) {
      val app = viewModel.get()?.getApplication<MyApplication>() ?: return
      app.log("onAppCheckTokenChanged() token=${token.token.ellipsized(maxLength = 13)}")
    }
  }
}
