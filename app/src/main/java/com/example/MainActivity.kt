package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.StudySaathiViewModel
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.PracticeScreen
import com.example.ui.screens.SolverScreen
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryNavy
import com.example.ui.theme.StudySaathiTheme
import com.example.util.configureAppCheck

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Mandatory App Check configuration with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        setContent {
            StudySaathiTheme {
                val viewModel: StudySaathiViewModel = viewModel()
                StudySaathiApp(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudySaathiApp(viewModel: StudySaathiViewModel) {
    val activeTab by viewModel.activeTab.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    // Handle back button when in sub-screens
    BackHandler(enabled = activeTab != 0) {
        viewModel.setActiveTab(0)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.studysaathi_icon_1793337717083),
                            contentDescription = "StudySaathi Logo",
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "StudySaathi",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = PrimaryNavy
                            )
                        )
                    }
                },
                actions = {
                    if (activeTab == 0) {
                        IconButton(
                            onClick = { viewModel.clearInputs() },
                            modifier = Modifier.testTag("clear_inputs_topbar")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CleaningServices,
                                contentDescription = "Clear Inputs",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.testTag("bottom_nav_bar"),
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = activeTab == 0,
                    onClick = { viewModel.setActiveTab(0) },
                    icon = {
                        Icon(
                            imageVector = if (activeTab == 0) Icons.Filled.AutoAwesome else Icons.Outlined.AutoAwesome,
                            contentDescription = "AI Solver"
                        )
                    },
                    label = { Text("AI Solver") },
                    modifier = Modifier.testTag("nav_solver"),
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = PrimaryBlue.copy(alpha = 0.15f),
                        selectedIconColor = PrimaryNavy,
                        selectedTextColor = PrimaryNavy
                    )
                )

                NavigationBarItem(
                    selected = activeTab == 1,
                    onClick = { viewModel.setActiveTab(1) },
                    icon = {
                        Icon(
                            imageVector = if (activeTab == 1) Icons.Filled.Quiz else Icons.Outlined.Quiz,
                            contentDescription = "Practice"
                        )
                    },
                    label = { Text("Practice") },
                    modifier = Modifier.testTag("nav_practice"),
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = PrimaryBlue.copy(alpha = 0.15f),
                        selectedIconColor = PrimaryNavy,
                        selectedTextColor = PrimaryNavy
                    )
                )

                NavigationBarItem(
                    selected = activeTab == 2,
                    onClick = { viewModel.setActiveTab(2) },
                    icon = {
                        Icon(
                            imageVector = if (activeTab == 2) Icons.Filled.MenuBook else Icons.Outlined.MenuBook,
                            contentDescription = "Notebook"
                        )
                    },
                    label = { Text("Notebook") },
                    modifier = Modifier.testTag("nav_notebook"),
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = PrimaryBlue.copy(alpha = 0.15f),
                        selectedIconColor = PrimaryNavy,
                        selectedTextColor = PrimaryNavy
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Crossfade(targetState = activeTab, label = "tab_crossfade") { tabIndex ->
                when (tabIndex) {
                    0 -> SolverScreen(
                        viewModel = viewModel,
                        onOpenPractice = { viewModel.setActiveTab(1) }
                    )
                    1 -> PracticeScreen(
                        viewModel = viewModel,
                        uiState = uiState,
                        onBackToSolver = { viewModel.setActiveTab(0) }
                    )
                    2 -> HistoryScreen(
                        viewModel = viewModel,
                        onQuestionSelected = { entity ->
                            viewModel.viewSavedQuestion(entity)
                        }
                    )
                }
            }
        }
    }
}
