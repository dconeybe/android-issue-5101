package app.android.issue5101

import android.app.Application
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MyApplication : Application() {
  private val _logs = MutableStateFlow(emptyList<String>())
  val logs: StateFlow<List<String>> = _logs.asStateFlow()

  fun log(message: String) {
    Log.i(TAG, message)

    while (true) {
      val oldLogs = _logs.value
      val newLogs = buildList {
        addAll(oldLogs)
        add(message)
      }
      if (_logs.compareAndSet(oldLogs, newLogs)) {
        break
      }
    }
  }

  companion object {
    const val TAG = "AndroidIssue5101"
  }
}
