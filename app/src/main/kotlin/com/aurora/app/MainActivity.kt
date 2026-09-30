package com.aurora.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

/**
 * Foundation-phase UI. Deliberately plain: the PRD defers visual design; this
 * screen only proves the offline runtime path end to end.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val deps = AuroraCompositionRoot.create(applicationContext)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ChatScreen(
                        viewModel(
                            factory = viewModelFactory {
                                initializer {
                                    ChatViewModel(
                                        conversations = deps.conversations,
                                        memories = deps.memories,
                                        runtime = deps.runtime,
                                        provider = deps.provider,
                                        assistant = deps.assistant,
                                        ledger = deps.ledger,
                                        routing = deps.routing,
                                    )
                                }
                            }
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val state by viewModel.state.collectAsState()
    var input by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // PRD-03: provider, model, fallback reason, usage, estimated cost.
        Text(text = state.statusLine, style = MaterialTheme.typography.labelSmall)
        if (state.memoryCount > 0) {
            Text(
                text = "기억 ${state.memoryCount}개 사용 중",
                style = MaterialTheme.typography.labelSmall,
            )
        }
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.messages) { message ->
                Text(
                    text = "${message.role.name}: ${message.content}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("메시지를 입력하세요 (오프라인 동작)") },
                enabled = !state.busy,
            )
            Button(
                onClick = {
                    viewModel.send(input)
                    input = ""
                },
                enabled = !state.busy,
            ) {
                Text("전송")
            }
        }
    }
}
