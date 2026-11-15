package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AssistantLanguage
import com.example.data.model.AutomationWorkflow
import com.example.data.model.WorkflowCategory
import com.example.ui.theme.MyraCyan
import com.example.ui.theme.MyraEmerald
import com.example.ui.theme.MyraViolet
import com.example.ui.viewmodel.MyraViewModel
import com.example.ui.viewmodel.StreamUiState
import com.example.ui.viewmodel.UiState
import com.example.util.DeviceActionExecutor
import com.example.util.VoiceAssistantManager

private data class QuickDeviceAction(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val type: String,
    val query: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationScreen(
    viewModel: MyraViewModel,
    voiceManager: VoiceAssistantManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val automationsState by viewModel.automationsState.collectAsStateWithLifecycle()
    val workflowStreamState by viewModel.workflowStreamState.collectAsStateWithLifecycle()
    val activeWorkflowTitle by viewModel.activeWorkflowTitle.collectAsStateWithLifecycle()
    val language = AssistantLanguage.fromCode(prefs.language)

    var showCreateDialog by remember { mutableStateOf(false) }
    var actionFeedback by remember { mutableStateOf<String?>(null) }

    val quickActions = remember {
        listOf(
            QuickDeviceAction("5m Timer", "Focus sprint", Icons.Filled.Timer, "timer", "300"),
            QuickDeviceAction("Alarms", "Open clock", Icons.Filled.Alarm, "alarm", ""),
            QuickDeviceAction("Web Search", "AI news", Icons.Filled.Search, "search", "latest AI news today"),
            QuickDeviceAction("Email", "Compose draft", Icons.Filled.Email, "email", "Quick Note from MYRA"),
            QuickDeviceAction("Wi-Fi", "Network panel", Icons.Filled.Wifi, "settings", "wifi"),
            QuickDeviceAction("Bluetooth", "Device pairing", Icons.Filled.Bluetooth, "settings", "bluetooth")
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 760.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "MYRA Automation",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "Device controls & reusable AI workflows",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledTonalButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier
                    .height(48.dp)
                    .testTag("add_workflow_button")
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("New Flow")
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "INSTANT DEVICE ACTIONS",
            style = MaterialTheme.typography.labelSmall,
            color = MyraCyan
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            quickActions.forEach { action ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .width(122.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                            RoundedCornerShape(16.dp)
                        )
                        .clickable {
                            val res = DeviceActionExecutor.executeAction(context, action.type, action.query)
                            actionFeedback = res.exceptionOrNull()?.localizedMessage
                        }
                        .testTag("quick_action_${action.type}")
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Icon(
                            imageVector = action.icon,
                            contentDescription = action.title,
                            tint = MyraCyan,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = action.title,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = action.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (!actionFeedback.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = actionFeedback.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "SAVED AI WORKFLOWS",
            style = MaterialTheme.typography.labelSmall,
            color = MyraViolet
        )
        Spacer(modifier = Modifier.height(8.dp))

        when (val state = automationsState) {
            is UiState.Loading -> {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            is UiState.Error -> {
                Text(
                    text = state.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            is UiState.Success -> {
                if (state.data.isEmpty()) {
                    EmptyWorkflowsCard(
                        onSeedStarters = {
                            viewModel.createWorkflow(
                                title = "Morning Briefing",
                                triggerPhrase = "morning briefing",
                                promptTemplate = "Create an energizing 3-point daily focus & productivity checklist based on my saved memory.",
                                category = WorkflowCategory.BRIEFING.key
                            )
                            viewModel.createWorkflow(
                                title = "Hinglish Quick Explainer",
                                triggerPhrase = "explain in hinglish",
                                promptTemplate = "Explain one useful software engineering or AI concept today in simple, fun Hinglish with a real-life example.",
                                category = WorkflowCategory.TRANSLATE.key
                            )
                            viewModel.createWorkflow(
                                title = "Code Architecture Check",
                                triggerPhrase = "architecture check",
                                promptTemplate = "Give me a crisp 5-point Kotlin & Jetpack Compose clean architecture best-practice checklist.",
                                category = WorkflowCategory.CODING.key
                            )
                        },
                        onCreateCustom = { showCreateDialog = true }
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(state.data, key = { it.id }) { workflow ->
                            WorkflowCard(
                                workflow = workflow,
                                onRun = { viewModel.runWorkflow(workflow) },
                                onDelete = { viewModel.deleteWorkflow(workflow.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateWorkflowDialog(
            onDismiss = { showCreateDialog = false },
            onSave = { title, trigger, prompt, category ->
                viewModel.createWorkflow(title, trigger, prompt, category)
                showCreateDialog = false
            }
        )
    }

    if (workflowStreamState !is StreamUiState.Idle) {
        ModalBottomSheet(onDismissRequest = { viewModel.clearWorkflowState() }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = activeWorkflowTitle ?: "Running Workflow",
                        style = MaterialTheme.typography.titleLarge
                    )
                    if (workflowStreamState is StreamUiState.Completed) {
                        val completedText = (workflowStreamState as StreamUiState.Completed).text
                        IconButton(onClick = { voiceManager.speak(completedText, language) }) {
                            Icon(
                                imageVector = Icons.Filled.VolumeUp,
                                contentDescription = "Speak workflow output",
                                tint = MyraCyan
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                when (val s = workflowStreamState) {
                    is StreamUiState.Thinking -> {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = MyraCyan)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Executing workflow with MYRA AI…", style = MaterialTheme.typography.bodyMedium)
                    }
                    is StreamUiState.Streaming -> {
                        Text(s.text, style = MaterialTheme.typography.bodyLarge)
                    }
                    is StreamUiState.Completed -> {
                        Text(s.text, style = MaterialTheme.typography.bodyLarge)
                    }
                    is StreamUiState.Error -> {
                        Text(s.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                    StreamUiState.Idle -> Unit
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun WorkflowCard(
    workflow: AutomationWorkflow,
    onRun: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = workflow.category.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MyraEmerald
                    )
                    Text(
                        text = workflow.title,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "Delete workflow",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = onRun,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Run")
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Trigger: \"${workflow.triggerPhrase}\"",
                style = MaterialTheme.typography.labelMedium,
                color = MyraCyan
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = workflow.promptTemplate,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyWorkflowsCard(
    onSeedStarters: () -> Unit,
    onCreateCustom: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Automate repetitive prompts & briefings",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Load starter MYRA routines or build your own one-tap AI workflow.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onSeedStarters, modifier = Modifier.height(48.dp)) {
                    Text("Load Starters")
                }
                OutlinedButton(onClick = onCreateCustom, modifier = Modifier.height(48.dp)) {
                    Text("Custom Flow")
                }
            }
        }
    }
}

@Composable
private fun CreateWorkflowDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var trigger by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(WorkflowCategory.PRODUCTIVITY) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Automation Flow") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = trigger,
                    onValueChange = { trigger = it },
                    label = { Text("Trigger phrase") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("AI Prompt template") },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    WorkflowCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.label) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title, trigger, prompt, category.key) },
                enabled = title.isNotBlank() && trigger.isNotBlank() && prompt.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
