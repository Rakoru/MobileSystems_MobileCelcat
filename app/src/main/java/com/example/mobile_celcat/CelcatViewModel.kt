package com.example.mobile_celcat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.PrintWriter
import java.io.StringWriter
import java.time.DayOfWeek
import java.util.Calendar
import java.io.File
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.compareTo
import kotlin.div
import kotlin.onFailure
import kotlin.rem
import kotlin.text.category


sealed class StatusState {
    object Idle : StatusState()
    object Loading : StatusState()
    object Success : StatusState()
    data class Error(val detail: ErrorDetail) : StatusState()
}

class CelcatViewModel : ViewModel() {

    private val repository = CelcatRepository()
    private var sessionCookies: String? = null
    private var currentGroupId: String? = null

    var apiBaseUrl = MutableStateFlow("https://lodz.celcat.cloud/cal")

    private val _statusState = MutableStateFlow<StatusState>(StatusState.Idle)
    val statusState: StateFlow<StatusState> = _statusState.asStateFlow()

    private val _groups = MutableStateFlow<List<Group>>(emptyList())
    val groups: StateFlow<List<Group>> = _groups.asStateFlow()

    private val _events = MutableStateFlow<List<CalendarEvent>>(emptyList())
    val events: StateFlow<List<CalendarEvent>> = _events.asStateFlow()

    var rawDebugText = MutableStateFlow<String>("")

    private var allMasterEvents = listOf<CalendarEvent>()
    private var semesterStart: Calendar? = null
    private val _displayedEvents = MutableStateFlow<List<CalendarEvent>>(emptyList())
    val displayedEvents: StateFlow<List<CalendarEvent>> = _displayedEvents.asStateFlow()

    private var appContext: Context? = null

    fun initAndLoad(context: Context) {
        appContext = context.applicationContext

        viewModelScope.launch {
            if (_statusState.value !is StatusState.Success) {
                _statusState.value = StatusState.Loading
            }

            CelcatSessionManager(context).obtainSessionCookies { cookies ->
                sessionCookies = cookies
                fetchGroups()
            }
        }
    }

    fun fetchGroups() {
        val cookies = sessionCookies?: ""
        viewModelScope.launch {
            if (_groups.value.isNotEmpty()) {
                _statusState.value = StatusState.Success
                return@launch
            }
            _statusState.value = StatusState.Loading
            repository.fetchGroups(query = "", cookies = cookies)
                .onSuccess { groupList ->
                    _groups.value = groupList
                    _statusState.value = StatusState.Success
                }
                .onFailure { exception ->
                    _statusState.value = StatusState.Error(
                        ErrorDetail(
                            title = "Failed to Fetch Groups",
                            targetUrl = "${apiBaseUrl.value}/resources",
                            rawMessage = exception.message ?: "Unknown Connection Error \n Check your internet connection",
                            stackTrace = getStackTraceString(exception)
                        )
                    )
                }
        }
    }
    private fun parseAndApplyRawJson(rawJson: String, groupId: String) {
        rawDebugText.value = rawJson
        val (parsedEvents, monday) = repository.parseCelcatResponse(rawJson, groupId)

        allMasterEvents = parsedEvents
        semesterStart = monday
        _events.value = parsedEvents

        refreshDateEvents()
        _statusState.value = StatusState.Success
    }

    fun fetchSchedule(groupId: String) {
        currentGroupId = groupId
        val cookies = sessionCookies ?: ""

        viewModelScope.launch {
            _statusState.value = StatusState.Loading

            repository.fetchRawSchedule(groupId = groupId, cookies = cookies)
                .onSuccess { rawJson ->
                    parseAndApplyRawJson(rawJson, groupId)
                }
                .onFailure { err ->
                    _statusState.value = StatusState.Error(
                        ErrorDetail(
                            title = "Failed to Fetch Schedule",
                            targetUrl = "https://lodz.celcat.cloud/cal/events?$groupId=1001",
                            rawMessage = err.message ?: "Unknown Connection Error \n Check your internet connection",
                            stackTrace = getStackTraceString(err)
                        )
                    )
                }
        }
    }
    private fun refreshDateEvents() {
        val dateCopy = Calendar.getInstance()
        val freshEvents = repository.getEventsForDate(
            allEvents = allMasterEvents,
            targetDate = dateCopy,
            semesterStart = semesterStart
        )

        _displayedEvents.value = freshEvents
            .distinctBy { "${it.id}_${it.startMs}_${it.dayOfWeek}" }
            .sortedBy { it.startMs }

    }

    private fun getStackTraceString(throwable: Throwable): String {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        return sw.toString()
    }

}