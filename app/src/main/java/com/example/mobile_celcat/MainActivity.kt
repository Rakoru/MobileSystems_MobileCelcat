package com.example.mobile_celcat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.mobile_celcat.ui.theme.Mobile_CelcatTheme
import com.example.mobile_celcat.ui.theme.Typography
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import androidx.activity.viewModels
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.stackTrace
import kotlin.text.category


class MainActivity : ComponentActivity() {

    private val viewModel: CelcatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.initAndLoad(this)

        enableEdgeToEdge()
        setContent {
            Mobile_CelcatTheme {
                AppContent(viewModel = viewModel)
                }
            }
        }
}

@Composable
fun AppContent(
    viewModel: CelcatViewModel
) {
    val context = LocalContext.current
    val statusState by viewModel.statusState.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val events by viewModel.events.collectAsState()
    val apiBaseUrl by viewModel.apiBaseUrl.collectAsState()

    var selectedGroup by remember { mutableStateOf<Group?>(null) }
    var expandedGroupDropdown by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.initAndLoad(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,

            ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    horizontalAlignment = Alignment.Start,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically,) {
                        Text(
                            text = "Mobile Celcat",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }}
                Column(
                    horizontalAlignment = Alignment.End,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically,) {
                        // Group Dropdown Selector
                        Box {
                            OutlinedButton(
                                onClick = { expandedGroupDropdown = true },
                                modifier = Modifier
                                    .height(36.dp)
                                    .width(125.dp),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = selectedGroup?.name ?: "Pick group...",
                                    fontSize = 12.sp,
                                )
                            }
                            DropdownMenu(
                                expanded = expandedGroupDropdown,
                                onDismissRequest = { expandedGroupDropdown = false },
                                modifier = Modifier.background(MaterialTheme.colorScheme.onBackground)
                            ) {
                                groups.forEach { group ->
                                    DropdownMenuItem(
                                        text = { Text(group.name, fontSize = 12.sp) },
                                        onClick = {
                                            selectedGroup = group
                                            expandedGroupDropdown = false
                                            viewModel.fetchSchedule(group.id)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            val statusState by viewModel.statusState.collectAsState()
            val displayedEvents by viewModel.displayedEvents.collectAsState()

            Column(modifier = Modifier.fillMaxSize()) {

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (val state = statusState) {
                        is StatusState.Error -> {
                            DebugErrorCard(state.detail)
                        }

                        is StatusState.Loading -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {

                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "Loading schedule...",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }

                        is StatusState.Success -> {
                            Column(modifier = Modifier.fillMaxSize()) {
                                if (displayedEvents.isNotEmpty()) {
                                    LazyColumn(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        items(
                                            items = displayedEvents,
                                            key = { event -> "${event.id}_${event.dayOfWeek}_${event.startMs}" }
                                        ) { event ->
                                            EventCard(event = event)
                                        }
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "No classes scheduled for this date.",
                                            color = MaterialTheme.colorScheme.secondary,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }

                        else -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Select your group and tap Load",
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    }
@Composable
fun EventCard(event: CalendarEvent) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.onBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
            )

            Column(modifier = Modifier.padding(12.dp)) {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Time Range
                    Text(
                        text = "${event.startTime} - ${event.endTime}",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )

                    if (event.category.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = event.category,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = event.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                if (event.professor.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = event.professor,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                if (event.location.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.onBackground, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "○ ${event.location}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DebugErrorCard(detail: ErrorDetail) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "⚠️ ${detail.title}",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Target URL: ${detail.targetUrl}",
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))

            Text("Error Message:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            Text(detail.rawMessage, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)

            Spacer(modifier = Modifier.height(8.dp))

            Text("Full Exception Stack Trace:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)

            Box(
                modifier = Modifier
                    .fillMaxWidth()

                    .padding(8.dp)
            ) {
                Text(
                    text = detail.stackTrace,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

