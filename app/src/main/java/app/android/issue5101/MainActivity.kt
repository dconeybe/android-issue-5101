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
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.android.issue5101.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

  private val viewModel: MainViewModel by viewModels()
  private lateinit var logView: TextView

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    println(viewModel)

    val binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)
    logView = binding.logs

    val logsFlow = (application as MyApplication).logs
    updateLogView(logsFlow.value)

    lifecycleScope.launch {
      logsFlow.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).collect { updateLogView(it) }
    }
  }

  private fun updateLogView(logs: List<String>) {
    logView.text = logs.joinToString("\n")
  }
}
