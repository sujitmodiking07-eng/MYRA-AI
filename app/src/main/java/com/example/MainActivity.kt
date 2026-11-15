package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CenterFocusWeak
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CenterFocusWeak
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.ai.MyraAiService
import com.example.data.firebase.configureAppCheck
import com.example.data.repository.MyraRepository
import com.example.ui.screens.AssistantScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.AutomationScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.VisionScreen
import com.example.ui.theme.MyraTheme
import com.example.ui.viewmodel.MyraViewModel
import com.example.util.VoiceAssistantManager
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private const val ROUTE_ASSISTANT = "assistant"
private const val ROUTE_CHAT = "chat"
private const val ROUTE_VISION = "vision"
private const val ROUTE_AUTOMATE = "automate"
private const val ROUTE_MEMORY = "memory"

private data class MyraNavItem(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent before any UI or ViewModels.
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            MyraTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

private fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        trySend(firebaseAuth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            MainMyraShell(currentUserId = user.uid)
        }
    }
}

@Composable
private fun MainMyraShell(currentUserId: String) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val viewModel: MyraViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                MyraViewModel(
                    repository = MyraRepository(db),
                    aiService = MyraAiService(),
                    currentUserId = currentUserId
                )
            }
        }
    )

    val voiceManager = remember(context) { VoiceAssistantManager(context) }
    DisposableEffect(voiceManager) {
        onDispose { voiceManager.release() }
    }

    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    LaunchedEffect(statusBanner) {
        val message = statusBanner
        if (!message.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message)
            viewModel.dismissBanner()
        }
    }

    val navItems = remember {
        listOf(
            MyraNavItem(
                route = ROUTE_ASSISTANT,
                labelRes = R.string.nav_assistant,
                selectedIcon = Icons.Filled.AutoAwesome,
                unselectedIcon = Icons.Outlined.AutoAwesome
            ),
            MyraNavItem(
                route = ROUTE_CHAT,
                labelRes = R.string.nav_chat,
                selectedIcon = Icons.AutoMirrored.Filled.Chat,
                unselectedIcon = Icons.AutoMirrored.Outlined.Chat
            ),
            MyraNavItem(
                route = ROUTE_VISION,
                labelRes = R.string.nav_vision,
                selectedIcon = Icons.Filled.CenterFocusWeak,
                unselectedIcon = Icons.Outlined.CenterFocusWeak
            ),
            MyraNavItem(
                route = ROUTE_AUTOMATE,
                labelRes = R.string.nav_automate,
                selectedIcon = Icons.Filled.Bolt,
                unselectedIcon = Icons.Outlined.Bolt
            ),
            MyraNavItem(
                route = ROUTE_MEMORY,
                labelRes = R.string.nav_memory,
                selectedIcon = Icons.Filled.Psychology,
                unselectedIcon = Icons.Outlined.Psychology
            )
        )
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val navigateToTab: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        navItems.forEach { item ->
                            val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                            val label = stringResource(item.labelRes)
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag("nav_${item.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        navItems.forEach { item ->
                            val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                            val label = stringResource(item.labelRes)
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag("nav_${item.route}")
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_ASSISTANT
                    ) {
                        composable(ROUTE_ASSISTANT) {
                            AssistantScreen(viewModel = viewModel, voiceManager = voiceManager)
                        }
                        composable(ROUTE_CHAT) {
                            ChatScreen()
                        }
                        composable(ROUTE_VISION) {
                            VisionScreen(viewModel = viewModel, voiceManager = voiceManager)
                        }
                        composable(ROUTE_AUTOMATE) {
                            AutomationScreen(viewModel = viewModel, voiceManager = voiceManager)
                        }
                        composable(ROUTE_MEMORY) {
                            SettingsScreen(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }
}
