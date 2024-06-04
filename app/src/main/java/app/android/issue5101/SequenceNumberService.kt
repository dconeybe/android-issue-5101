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

import android.app.Service
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Binder
import android.os.IBinder
import androidx.annotation.MainThread
import java.lang.ref.WeakReference
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class SequenceNumberService : Service(), LoggerNameAndIdProvider {

  private val logger = Logger("SequenceNumberService")
  override val loggerName
    get() = logger.name

  override val loggerId
    get() = logger.id

  override val loggerNameWithId
    get() = logger.nameWithId

  private val weakThis = WeakReference(this)
  private val api = APIImpl(weakThis)

  override fun onCreate() {
    logger.onCreate()
    super.onCreate()
  }

  override fun onDestroy() {
    logger.onDestroy()
    weakThis.clear()
    @OptIn(DelicateCoroutinesApi::class) GlobalScope.launch { api.close() }
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder {
    logger.onBind(intent)
    return api
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    throw UnsupportedOperationException("service cannot be started")
  }

  interface API : IBinder {
    suspend fun nextSequenceNumber(value: String): Long
  }

  private class APIImpl(val service: WeakReference<SequenceNumberService>) : Binder(), API {
    private val mutex = Mutex()
    private var closed = false
    private var db: SequenceNumberDbOpenHelper? = null

    override suspend fun nextSequenceNumber(value: String): Long =
        withContext(Dispatchers.IO) {
          val db = getDb()
          db.insert(TABLE_NAME, COLUMN_VALUE, ContentValues().apply { put(COLUMN_VALUE, value) })
        }

    suspend fun close(): Unit =
        mutex.withLock {
          closed = true
          val capturedDb = db
          db = null
          withContext(Dispatchers.IO) { capturedDb?.close() }
        }

    private suspend fun getDb(): SQLiteDatabase =
        mutex.withLock {
          val context = if (closed) null else service.get()?.applicationContext
          checkNotNull(context) { "service has been closed" }
          val db = db ?: (SequenceNumberDbOpenHelper(context).also { db = it })
          db.writableDatabase
        }
  }

  private class SequenceNumberDbOpenHelper(context: Context) :
      SQLiteOpenHelper(context, "SequenceNumber", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
      db.execSQL(
          """
            CREATE TABLE $TABLE_NAME (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_VALUE STRING
            )
        """)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
      throw UnsupportedOperationException("onUpgrade() is not supported")
    }
  }

  private companion object {
    const val TABLE_NAME = "sequence_number"
    const val COLUMN_ID = "id"
    const val COLUMN_VALUE = "value"
  }
}

class SequenceNumberServiceConnectionImpl(private val context: Context) : ServiceConnection {

  private val _binder = MutableStateFlow<SequenceNumberService.API?>(null)
  val binder = _binder.asStateFlow()

  override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
    _binder.value = (service as SequenceNumberService.API?)
  }

  override fun onServiceDisconnected(name: ComponentName?) {
    _binder.value = null
  }

  @MainThread
  fun bind() {
    val intent = Intent(context, SequenceNumberService::class.java)
    val bindSuccess = context.bindService(intent, this, Context.BIND_AUTO_CREATE)
    check(bindSuccess) { "Failed to bind to service: $intent" }
  }

  @MainThread
  fun unbind() {
    context.unbindService(this)
  }
}
