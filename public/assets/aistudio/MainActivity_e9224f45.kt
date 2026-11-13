package com.pbs.attendance

import android.app.TimePickerDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar

data class Employee(val id: Long, val name: String)
data class Entry(
    val id: Long, val employeeId: Long, val date: String,
    val inMinutes: Int, val outMinutes: Int
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { AttendanceScreen() } }
    }
}

@Composable
fun AttendanceScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("pbs_attendance", 0) }
    var employees by remember { mutableStateOf(loadEmployees(prefs)) }
    var entries by remember { mutableStateOf(loadEntries(prefs)) }
    var selectedId by remember { mutableStateOf(employees.firstOrNull()?.id ?: -1L) }
    var employeeName by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var date by remember { mutableStateOf(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())) }
    var inTime by remember { mutableStateOf(9 * 60 + 30) }
    var outTime by remember { mutableStateOf(18 * 60 + 15) }
    var message by remember { mutableStateOf("") }

    fun saveAll() {
        prefs.edit().putString("employees", JSONArray().apply {
            employees.forEach { put(JSONObject().put("id", it.id).put("name", it.name)) }
        }.toString()).putString("entries", JSONArray().apply {
            entries.forEach { put(JSONObject().put("id", it.id).put("employeeId", it.employeeId)
                .put("date", it.date).put("in", it.inMinutes).put("out", it.outMinutes)) }
        }.toString()).apply()
    }

    fun timeText(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        val suffix = if (h < 12) "AM" else "PM"
        val hour = when (h % 12) { 0 -> 12; else -> h % 12 }
        return "%d:%02d %s".format(hour, m, suffix)
    }
    fun chooseTime(initial: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(context, { _, h, m -> onPicked(h * 60 + m) },
            initial / 60, initial % 60, false).show()
    }
    fun duration(a: Int, b: Int): Int = if (b >= a) b - a else (24 * 60 - a + b)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("PBS Employee Attendance", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Text("Daily time-in, time-out, full-day and overtime calculation")
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { showAdd = true }) { Text("Add Employee") }
            if (employees.isNotEmpty()) {
                var expanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { expanded = true }) {
                        Text(employees.firstOrNull { it.id == selectedId }?.name ?: "Select employee")
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        employees.forEach { emp ->
                            DropdownMenuItem(text = { Text(emp.name) }, onClick = {
                                selectedId = emp.id; expanded = false
                            })
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = date, onValueChange = { date = it },
            label = { Text("Date (DD/MM/YYYY)") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { chooseTime(inTime) { inTime = it } }, modifier = Modifier.weight(1f)) {
                Text("IN: ${timeText(inTime)}")
            }
            OutlinedButton(onClick = { chooseTime(outTime) { outTime = it } }, modifier = Modifier.weight(1f)) {
                Text("OUT: ${timeText(outTime)}")
            }
        }
        val worked = duration(inTime, outTime)
        val fullDay = worked >= (8 * 60 + 45)
        val overtime = if (outTime >= 19 * 60) outTime - 19 * 60 else 0
        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("Attendance calculation", style = MaterialTheme.typography.titleMedium)
                Text("Working time: ${worked / 60} hr ${worked % 60} min")
                Text("Day status: ${if (fullDay) "FULL DAY" else "LESS THAN FULL DAY"}")
                Text("Overtime: ${overtime / 60} hr ${overtime % 60} min")
                Text("OT starts after 7:00 PM. Time between 6:15 PM and 7:00 PM is not OT.")
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = {
            if (employees.none { it.id == selectedId }) {
                message = "Please add and select an employee first."
            } else if (date.isBlank()) {
                message = "Please enter a date."
            } else if (worked == 0) {
                message = "IN and OUT time cannot be the same."
            } else {
                entries = entries.filterNot { it.employeeId == selectedId && it.date == date } +
                    Entry(System.currentTimeMillis(), selectedId, date, inTime, outTime)
                saveAll()
                message = "Attendance saved."
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("Save Attendance") }
        if (message.isNotBlank()) Text(message, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(20.dp))
        Text("Attendance Records", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        val sorted = entries.sortedByDescending { it.id }
        if (sorted.isEmpty()) Text("No attendance records saved yet.")
        sorted.forEach { entry ->
            val emp = employees.firstOrNull { it.id == entry.employeeId }?.name ?: "Employee"
            val mins = duration(entry.inMinutes, entry.outMinutes)
            val ot = if (entry.outMinutes >= 1140) entry.outMinutes - 1140 else 0
            Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("$emp  •  ${entry.date}", style = MaterialTheme.typography.titleMedium)
                    Text("IN ${timeText(entry.inMinutes)}  |  OUT ${timeText(entry.outMinutes)}")
                    Text("${if (mins >= 525) "FULL DAY" else "LESS THAN FULL DAY"}  •  Worked ${mins / 60}h ${mins % 60}m  •  OT ${ot / 60}h ${ot % 60}m")
                    TextButton(onClick = {
                        entries = entries.filterNot { it.id == entry.id }; saveAll()
                    }) { Text("Delete record") }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("Rules: Full day = 8 hours 45 minutes or more (the standard 9:30 AM–6:15 PM shift). OT is counted only after 7:00 PM.")
    }

    if (showAdd) {
        AlertDialog(onDismissRequest = { showAdd = false },
            title = { Text("Add employee") },
            text = { OutlinedTextField(value = employeeName, onValueChange = { employeeName = it },
                label = { Text("Employee name") }) },
            confirmButton = {
                TextButton(onClick = {
                    if (employeeName.isNotBlank()) {
                        val emp = Employee(System.currentTimeMillis(), employeeName.trim())
                        employees = employees + emp
                        selectedId = emp.id
                        employeeName = ""
                        saveAll()
                    }
                    showAdd = false
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } }
        )
    }
}

private fun loadEmployees(prefs: android.content.SharedPreferences): List<Employee> = try {
    val a = JSONArray(prefs.getString("employees", "[]"))
    (0 until a.length()).map { val o = a.getJSONObject(it); Employee(o.getLong("id"), o.getString("name")) }
} catch (_: Exception) { emptyList() }

private fun loadEntries(prefs: android.content.SharedPreferences): List<Entry> = try {
    val a = JSONArray(prefs.getString("entries", "[]"))
    (0 until a.length()).map { val o = a.getJSONObject(it); Entry(o.getLong("id"), o.getLong("employeeId"),
        o.getString("date"), o.getInt("in"), o.getInt("out")) }
} catch (_: Exception) { emptyList() }
