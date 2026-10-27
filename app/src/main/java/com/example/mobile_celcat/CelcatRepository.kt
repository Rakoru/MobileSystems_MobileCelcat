package com.example.mobile_celcat

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.collections.get

class CelcatRepository {

    private val client = OkHttpClient()
    private val gson = Gson()
    private val baseUrl = "https://lodz.celcat.cloud/cal/"
    private val userAgent = "Mozilla/5.0 (Linux; Android 14) Chrome/120.0.0.0 Mobile Safari/537.36"

    suspend fun fetchGroups(query: String, cookies: String): Result<List<Group>> = withContext(Dispatchers.IO) {
        try {
            val payload = GroupSearchPayload(searchTerm = query.ifBlank { null })
            val jsonPayload = gson.toJson(payload)

            val request = Request.Builder()
                .url("${baseUrl}resources")
                .post(jsonPayload.toRequestBody("application/json".toMediaType()))
                .addHeader("User-Agent", userAgent)
                .addHeader("X-Requested-With", "XMLHttpRequest")
                .addHeader("Referer", baseUrl)
                .addHeader("Cookie", cookies)
                .build()

            val response = client.newCall(request).execute()
            val bodyString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: $bodyString"))
            }

            val groups = parseCelcatGroupsJson(bodyString)
            Result.success(groups)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchRawSchedule(groupId: String, cookies: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val targetUrl = "${baseUrl}events?$groupId=1001"

            val request = Request.Builder()
                .url(targetUrl)
                .get()
                .addHeader("User-Agent", userAgent)
                .addHeader("Accept", "application/json, text/javascript, */*; q=0.01")
                .addHeader("X-Requested-With", "XMLHttpRequest")
                .addHeader("Referer", baseUrl)
                .addHeader("Cookie", cookies)
                .build()

            val response = client.newCall(request).execute()
            val bodyString = response.body?.string() ?: "[RESPONSE BODY WAS NULL]"

            val debugOutput = StringBuilder().apply {
                append("REQUEST URL:\n$targetUrl\n\n")
                append("HTTP STATUS: ${response.code}\n\n")
                append("COOKIES SENT:\n$cookies\n\n")
                append("RESPONSE HEADERS:\n${response.headers}\n\n")
                append("RAW BODY CONTENT:\n$bodyString")
            }.toString()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception(debugOutput))
            }

            Result.success(debugOutput)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    private fun parseCelcatGroupsJson(jsonString: String): List<Group> {
        val groups = mutableListOf<Group>()

        val jsonElement: JsonElement = try {
            JsonParser.parseString(jsonString)
        } catch (e: Exception) {
            return emptyList()
        }

        val resultsArray = when {
            jsonElement.isJsonObject -> {
                val obj = jsonElement.asJsonObject
                if (obj.has("results") && obj.get("results").isJsonArray) {
                    obj.getAsJsonArray("results")
                } else null
            }
            jsonElement.isJsonArray -> jsonElement.asJsonArray
            else -> null
        } ?: return emptyList()

        for (element in resultsArray) {
            if (!element.isJsonObject) continue
            val item = element.asJsonObject

            val type = item.get("type")?.asInt ?: 1001
            if (type != 1001) continue

            val id = item.get("id")?.asString ?: continue
            var rawName = item.get("name")?.asString ?: ""

            if (item.has("displayDetails") && item.get("displayDetails").isJsonArray) {
                val details = item.getAsJsonArray("displayDetails")
                if (details.size() > 0) {
                    rawName = details[0].asJsonObject.get("value")?.asString ?: rawName
                }
            }

            val cleanName = rawName.split(" [")[0].trim()
            if (cleanName.isNotEmpty()) {
                groups.add(Group(id = id, name = cleanName))
            }
        }

        return groups.sortedBy { it.name }
    }

    fun parseCelcatResponse(jsonString: String, groupId: String = ""): Pair<List<CalendarEvent>, Calendar?> {
        val rootObject = try {
            val cleanJson = if (jsonString.startsWith("REQUEST URL:")) {
                jsonString.substringAfter("RAW BODY CONTENT:\n").trim()
            } else jsonString.trim()

            val element = JsonParser.parseString(cleanJson)
            if (element.isJsonObject) element.asJsonObject else return Pair(emptyList(), null)
        } catch (e: Exception) {
            Log.e("CelcatParser", "JSON Root Parse Error: ${e.message}")
            return Pair(emptyList(), null)
        }

        val semesterStart = parseSemesterMonday(rootObject, groupId)
        val eventsList = parseCelcatEventsFromObject(rootObject)

        Log.d("CelcatParser", "Parsed Total Events: ${eventsList.size}")
        return Pair(eventsList, semesterStart)
    }

    private fun parseCelcatEventsFromObject(rootObject: JsonObject): List<CalendarEvent> {
        val eventsList = mutableListOf<CalendarEvent>()

        val namesObj = if (rootObject.has("names") && rootObject.get("names").isJsonObject) {
            rootObject.getAsJsonObject("names")
        } else JsonObject()

        val modulesMap = namesObj.getAsJsonObject("1000") // Subjects
        val roomsMap = namesObj.getAsJsonObject("1003")   // Rooms
        val staffMap = namesObj.getAsJsonObject("1002")   // Lecturers
        val catsMap = namesObj.getAsJsonObject("1105")    // Categories

        val eventsArray = when {
            rootObject.has("events") && rootObject.get("events").isJsonArray -> {
                rootObject.getAsJsonArray("events")
            }
            else -> {
                Log.e("CelcatParser", "No 'events' array key found in JSON root")
                return emptyList()
            }
        }

        for (elem in eventsArray) {
            try {
                if (!elem.isJsonObject) continue
                val ev = elem.asJsonObject

                val eventId = ev.get("eventId")?.asString ?: ev.get("id")?.asString ?: "ev_${eventsList.size}"

                var title = ""
                if (ev.has("modules") && ev.get("modules").isJsonArray) {
                    val mods = ev.getAsJsonArray("modules")
                    if (mods.size() > 0) {
                        val firstMod = mods[0]
                        val modId = when {
                            firstMod.isJsonObject -> firstMod.asJsonObject.get("id")?.asString
                            firstMod.isJsonPrimitive -> firstMod.asString
                            else -> null
                        }
                        if (modId != null && modulesMap != null && modulesMap.has(modId)) {
                            title = modulesMap.getAsJsonObject(modId).get("name")?.asString ?: ""
                        }
                    }
                }
                if (title.isBlank() && ev.has("eventName") && !ev.get("eventName").isJsonNull) {
                    title = ev.get("eventName").asString
                }
                if (title.isBlank()) title = "Class"

                val roomNames = mutableListOf<String>()
                if (ev.has("facilities") && ev.get("facilities").isJsonArray) {
                    for (fac in ev.getAsJsonArray("facilities")) {
                        val facId = when {
                            fac.isJsonObject -> fac.asJsonObject.get("id")?.asString
                            fac.isJsonPrimitive -> fac.asString
                            else -> null
                        }
                        if (facId != null && roomsMap != null && roomsMap.has(facId)) {
                            val rName = roomsMap.getAsJsonObject(facId).get("name")?.asString
                            if (!rName.isNullOrBlank()) roomNames.add(rName.trim())
                        }
                    }
                }
                val location = if (roomNames.isNotEmpty()) roomNames.joinToString(", ") else "No Room"

                val profNames = mutableListOf<String>()
                if (ev.has("staff") && ev.get("staff").isJsonArray) {
                    for (st in ev.getAsJsonArray("staff")) {
                        val stId = when {
                            st.isJsonObject -> st.asJsonObject.get("id")?.asString
                            st.isJsonPrimitive -> st.asString
                            else -> null
                        }
                        if (stId != null && staffMap != null && staffMap.has(stId)) {
                            val pName = staffMap.getAsJsonObject(stId).get("name")?.asString
                            if (!pName.isNullOrBlank()) profNames.add(pName.trim())
                        }
                    }
                }
                val professor = profNames.joinToString(", ")

                var category = ""
                val catId = ev.get("eventCategoryId")?.asString
                if (catId != null && catsMap != null && catsMap.has(catId)) {
                    category = catsMap.getAsJsonObject(catId).get("name")?.asString ?: ""
                }

                val startMs = ev.get("startTime")?.asLong ?: 0L
                val durationMins = ev.get("duration")?.asLong ?: 0L
                val endMs = startMs + (durationMins * 60 * 1000L)

                val startTimeStr = parseTimeElement(ev.get("startTime"))
                val endTimeStr = formatMsToTime(endMs)

                val dayOfWeek = ev.get("dayOfWeek")?.asInt ?: 1
                val notes = if (ev.has("notes") && !ev.get("notes").isJsonNull) ev.get("notes").asString else null

                val weeks = mutableListOf<Boolean>()
                if (ev.has("weeks") && ev.get("weeks").isJsonArray) {
                    for (w in ev.getAsJsonArray("weeks")) {
                        if (w.isJsonPrimitive) {
                            weeks.add(w.asBoolean)
                        }
                    }
                }
                val rawDateStr = when {
                    ev.has("start") && !ev.get("start").isJsonNull -> ev.get("start").asString
                    ev.has("eventDate") && !ev.get("eventDate").isJsonNull -> ev.get("eventDate").asString
                    ev.has("date") && !ev.get("date").isJsonNull -> ev.get("date").asString
                    else -> "DOW: $dayOfWeek"
                }

                eventsList.add(
                    CalendarEvent(
                        id = eventId,
                        title = title,
                        location = location,
                        category = category,
                        professor = professor,
                        startTime = startTimeStr,
                        endTime = endTimeStr,
                        dayOfWeek = dayOfWeek,
                        notes = notes,
                        startMs = startMs,
                        endMs = endMs,
                        weeks = weeks,
                        roomNames = roomNames,
                        professors = profNames,
                        rawDateStr = rawDateStr
                    )
                )
            } catch (itemError: Exception) {
                Log.e("CelcatParser", "Error parsing single event item: ${itemError.message}")
                continue
            }
        }

        return eventsList
    }

    fun parseSemesterMonday(rootObject: JsonObject, groupId: String): Calendar? {
        if (!rootObject.has("viewableResourceDates")) return null
        val datesObj = rootObject.getAsJsonObject("viewableResourceDates") ?: return null

        val key = if (groupId.isNotBlank() && datesObj.has(groupId)) groupId else datesObj.keySet().firstOrNull() ?: return null
        if (!datesObj.has(key) || !datesObj.get(key).isJsonArray) return null
        val array = datesObj.getAsJsonArray(key)
        if (array.size() == 0) return null

        val startDateElem = array[0].asJsonObject.get("startDate") ?: return null

        val date = if (startDateElem.isJsonPrimitive) {
            val prim = startDateElem.asJsonPrimitive
            if (prim.isNumber) {
                java.util.Date(prim.asLong)
            } else {
                val startDateStr = prim.asString
                val cleanDateStr = if (startDateStr.contains("T")) startDateStr.split("T")[0] else startDateStr
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }
                try {
                    sdf.parse(cleanDateStr)
                } catch (e: Exception) {
                    null
                }
            }
        } else {
            null
        } ?: return null

        val cal = Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val day = cal.get(Calendar.DAY_OF_WEEK)
        val diff = if (day == Calendar.SUNDAY) -6 else Calendar.MONDAY - day
        cal.add(Calendar.DAY_OF_MONTH, diff)

        return cal
    }

    fun getEventsForDate(
        allEvents: List<CalendarEvent>,
        targetDate: Calendar = Calendar.getInstance(),
        semesterStart: Calendar?
    ): List<CalendarEvent> {
        if (allEvents.isEmpty() || semesterStart == null) return emptyList()

        val targetZero = (targetDate.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val calDow = targetZero.get(Calendar.DAY_OF_WEEK)
        val celcatDow = if (calDow == Calendar.SUNDAY) 7 else calDow - 1

        val diffMs = targetZero.timeInMillis - semesterStart.timeInMillis
        val weekIdx = Math.floor(diffMs.toDouble() / (7.0 * 24.0 * 3600.0 * 1000.0)).toInt()

        if (weekIdx < 0) return emptyList()

        val activeEvents = allEvents.filter { ev ->

            if (ev.dayOfWeek != celcatDow) return@filter false

            if (ev.weeks.isNotEmpty()) {
                return@filter if (weekIdx < ev.weeks.size) {
                    ev.weeks[weekIdx]
                } else {
                    false
                }
            }
            true
        }
        return activeEvents.sortedBy { it.startMs }
    }
    private fun parseTimeElement(element: JsonElement?): String {
        if (element == null || element.isJsonNull) return "--:--"
        return try {
            if (element.isJsonPrimitive) {
                val prim = element.asJsonPrimitive
                if (prim.isNumber) {
                    formatMsToTime(prim.asLong)
                } else {
                    val str = prim.asString
                    if (str.contains(":")) str.take(5) else formatMsToTime(str.toLongOrNull() ?: 0L)
                }
            } else "--:--"
        } catch (e: Exception) {
            "--:--"
        }
    }

    private fun formatMsToTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        return String.format(Locale.getDefault(), "%02d:%02d", hours, minutes)
    }
}