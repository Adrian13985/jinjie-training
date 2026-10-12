package com.jinjie.training

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val workoutCard = Color(0xFF1A2630)
private val workoutAccent = Color(0xFFC9F15D)
private val workoutMuted = Color(0xFF9BAAB4)

@Composable
fun WorkoutScreen(
    day: DaySnapshot,
    onSetSave: (SetLog) -> Unit,
    onAddSet: (String) -> Unit,
    onRemoveSet: (SetLog) -> Unit,
    onReplaceExercise: (String, String) -> Unit,
    onFinish: () -> Unit
) {
    var restUntil by rememberSaveable { mutableLongStateOf(0L) }
    var secondsLeft by remember { mutableLongStateOf(0L) }
    var confirmFinish by remember { mutableStateOf(false) }
    var replaceTarget by remember { mutableStateOf<String?>(null) }
    val grouped = remember(day.sets) { day.sets.groupBy { it.exerciseId }.entries.toList() }
    LaunchedEffect(restUntil) {
        while (restUntil > System.currentTimeMillis()) {
            secondsLeft = ((restUntil - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
            delay(500)
        }
        secondsLeft = 0
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(TrainingPlan.byCode(day.session?.workoutCode ?: "")?.title ?: "训练", fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text("${day.sets.count { it.completed }} / ${day.sets.size} 组已完成 · 重量与次数会自动保存", color = workoutMuted)
            }
        }
        if (secondsLeft > 0) item {
            Card(colors = CardDefaults.cardColors(containerColor = workoutAccent), shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("组间休息  ${secondsLeft / 60}:${(secondsLeft % 60).toString().padStart(2, '0')}", color = Color(0xFF0D141B), fontWeight = FontWeight.Bold)
                    Text("跳过", color = Color(0xFF0D141B), modifier = Modifier.clickable { restUntil = 0L }.padding(start = 12.dp))
                }
            }
        }
        grouped.forEach { (exerciseId, rows) ->
            item(key = "heading_$exerciseId") {
                Column(Modifier.padding(top = 10.dp)) {
                    Text(rows.first().exerciseName, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    val spec = TrainingPlan.workouts.flatMap { it.exercises }.firstOrNull { it.id == exerciseId.substringBefore('|') }
                    Text("目标 ${rows.first().targetReps} · ${spec?.cue ?: "按动作要领稳定完成"}", color = workoutMuted, fontSize = 12.sp)
                    if (!spec?.alternatives.isNullOrEmpty()) TextButton(onClick = { replaceTarget = exerciseId }) { Text("更换动作") }
                }
            }
            items(rows, key = { it.id }) { row ->
                val suggestion = day.suggestions.firstOrNull {
                    it.exerciseId == row.exerciseId && it.setNumber == row.setNumber
                }
                SetEditor(row, suggestion, onSave = { updated ->
                    onSetSave(updated)
                    if (updated.completed && !row.completed) restUntil = System.currentTimeMillis() + updated.restSeconds * 1000L
                }, onRemove = { onRemoveSet(row) })
            }
            item(key = "add_$exerciseId") {
                OutlinedButton(onClick = { onAddSet(exerciseId) }, modifier = Modifier.fillMaxWidth()) { Text("+ 增加一组") }
            }
        }
        item {
            Button(onClick = { confirmFinish = true }, modifier = Modifier.fillMaxWidth()) { Text("结束本次训练") }
        }
    }

    if (confirmFinish) AlertDialog(
        onDismissRequest = { confirmFinish = false },
        title = { Text("结束训练") },
        text = { Text("已完成 ${day.sets.count { it.completed }} / ${day.sets.size} 组。未完成的组会保留，之后仍可修改。") },
        confirmButton = { TextButton(onClick = { confirmFinish = false; onFinish() }) { Text("结束并保存") } },
        dismissButton = { TextButton(onClick = { confirmFinish = false }) { Text("继续训练") } }
    )
    replaceTarget?.let { target ->
        val original = TrainingPlan.workouts.flatMap { it.exercises }.firstOrNull { it.id == target.substringBefore('|') }
        AlertDialog(
            onDismissRequest = { replaceTarget = null },
            title = { Text("更换动作") },
            text = { Column {
                (listOfNotNull(original?.name) + (original?.alternatives ?: emptyList())).forEach { name ->
                    TextButton(onClick = { onReplaceExercise(target, name); replaceTarget = null }, modifier = Modifier.fillMaxWidth()) { Text(name) }
                }
            } },
            confirmButton = { TextButton(onClick = { replaceTarget = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun SetEditor(row: SetLog, suggestion: SetSuggestion?, onSave: (SetLog) -> Unit, onRemove: () -> Unit) {
    var weight by remember(row.id, row.weightKg) { mutableStateOf(row.weightKg?.let(::formatWeight) ?: "") }
    var reps by remember(row.id, row.actualReps) { mutableStateOf(row.actualReps?.toString() ?: "") }
    var rir by remember(row.id, row.rir) { mutableStateOf(row.rir?.toString() ?: "") }
    var distance by remember(row.id, row.distanceCm) { mutableStateOf(row.distanceCm?.let(::formatWeight) ?: "") }
    var showRemove by remember { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = workoutCard), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("第 ${row.setNumber} 组", fontWeight = FontWeight.Bold, color = if (row.completed) workoutAccent else Color.White)
                Text(if (row.completed) "✓ 已完成" else "目标 ${row.targetReps}", color = workoutMuted)
            }
            if (suggestion != null) {
                val weightText = suggestion.weightKg?.let { "建议 ${formatWeight(it)} kg" }
                    ?: if (suggestion.guidance.startsWith("自重")) "建议：自重" else "建议：现场试重"
                Text("$weightText · ${suggestion.guidance}", color = workoutAccent, fontSize = 12.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(weight, { weight = it }, modifier = Modifier.weight(1f), label = { Text("kg") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(reps, { reps = it }, modifier = Modifier.weight(1f), label = { Text("次数") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(rir, { rir = it }, modifier = Modifier.weight(1f), label = { Text("余力") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            if (row.exerciseId.substringBefore('|') == "broad_jump") {
                OutlinedTextField(distance, { distance = it }, modifier = Modifier.fillMaxWidth(), label = { Text("最远距离（cm，可选）") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    onSave(row.copy(
                        weightKg = weight.toDoubleOrNull()?.takeIf { it >= 0 && it < 1000 },
                        actualReps = reps.toIntOrNull()?.takeIf { it >= 0 && it < 1000 },
                        rir = rir.toIntOrNull()?.takeIf { it in 0..10 },
                        distanceCm = distance.toDoubleOrNull()?.takeIf { it > 0 && it < 1000 },
                        completed = true
                    ))
                }, modifier = Modifier.weight(1f)) { Text(if (row.completed) "更新记录" else "完成本组") }
                if (row.completed) OutlinedButton(onClick = { onSave(row.copy(completed = false)) }) { Text("撤销") }
                else TextButton(onClick = { showRemove = true }) { Text("删除") }
            }
        }
    }
    if (showRemove) AlertDialog(
        onDismissRequest = { showRemove = false }, title = { Text("删除这一组？") },
        confirmButton = { TextButton(onClick = { showRemove = false; onRemove() }) { Text("删除") } },
        dismissButton = { TextButton(onClick = { showRemove = false }) { Text("取消") } }
    )
}

fun formatWeight(weight: Double): String = if (weight % 1.0 == 0.0) weight.toInt().toString() else String.format(java.util.Locale.US, "%.1f", weight)
