package com.jinjie.training

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek

private val cardBackground = Color(0xFF1A2630)
private val highlight = Color(0xFFC9F15D)
private val secondary = Color(0xFF9BAAB4)

@Composable
fun PlanScreen(startDate: LocalDate, adjustments: List<PlanAdjustment>, onSave: (PlanAdjustment) -> Unit) {
    val week = TrainingPlan.weekNumber(startDate, LocalDate.now())
    var editing by remember { mutableStateOf<Pair<WorkoutSpec, ExerciseSpec>?>(null) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("12 周增肌计划", fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text("第 $week 周 · ${TrainingPlan.phase(week)}", color = secondary)
        }
        item {
            InfoCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("训练规则", color = highlight, fontWeight = FontWeight.Bold)
                    Text("每次先热身 5–8 分钟。主项组间休息约 2–3 分钟，辅助动作约 60–90 秒。")
                    Text("动作稳定、每组达到次数上限且还可再做约 2 次时，上肢加 2–2.5 kg，下肢加 2.5–5 kg。")
                    Text("第 9 周减量。跳跃练习优先动作质量，速度明显下降就停止。", color = secondary)
                }
            }
        }
        items(TrainingPlan.workouts) { workout ->
            InfoCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(workout.title, color = highlight, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(workout.focus, color = secondary)
                    HorizontalDivider(color = Color(0xFF30404B))
                    workout.exercises.forEach { exercise ->
                        val adjustment = adjustments.firstOrNull { it.workoutCode == workout.code && it.exerciseId == exercise.id }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(adjustment?.replacementName ?: exercise.name, modifier = Modifier.weight(1f))
                            Text("${TrainingPlan.prescribedSets(adjustment?.setCount ?: exercise.sets, week)} × ${exercise.reps}", color = secondary)
                            TextButton(onClick = { editing = workout to exercise }) { Text("编辑") }
                        }
                    }
                }
            }
        }
        item {
            InfoCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("增肌跟踪", color = highlight, fontWeight = FontWeight.Bold)
                    Text("目标为缓慢增重，每周约 0.2–0.3 kg。每天蛋白质参考 130–165 g；按多次晨起体重的周均值调整摄入。")
                    Text("如动作造成疼痛，停止该动作并选择更舒适的替代动作。", color = secondary)
                }
            }
        }
    }
    editing?.let { (workout, exercise) ->
        val adjustment = adjustments.firstOrNull { it.workoutCode == workout.code && it.exerciseId == exercise.id }
        var count by remember(editing) { mutableStateOf((adjustment?.setCount ?: exercise.sets).toString()) }
        var name by remember(editing) { mutableStateOf(adjustment?.replacementName ?: exercise.name) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("调整 ${exercise.name}") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedTextField(count, { count = it }, label = { Text("基础组数（1–8）") }, singleLine = true)
                Text("动作选择")
                (listOf(exercise.name) + exercise.alternatives).forEach { choice ->
                    FilterChip(selected = name == choice, onClick = { name = choice }, label = { Text(choice) })
                }
            } },
            confirmButton = { TextButton(enabled = count.toIntOrNull()?.let { it in 1..8 } == true, onClick = {
                onSave(PlanAdjustment(workout.code, exercise.id, name.takeIf { it != exercise.name }, count.toInt()))
                editing = null
            }) { Text("保存") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("取消") } }
        )
    }
}

@Composable
fun ProgressScreen(progress: ProgressSnapshot) {
    val completed = progress.sessions.filter { it.status == "DONE" }
    val recent = completed.filter { LocalDate.parse(it.date) >= LocalDate.now().minusDays(6) }
    val doneSets = progress.sets.filter { it.completed }
    val weights = progress.checkins.mapNotNull { row -> row.weightKg?.let { LocalDate.parse(row.date) to it } }
        .sortedByDescending { it.first }
    val latestWeeks = weights.groupBy { (date, _) -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
        .toList().sortedByDescending { it.first }.take(8)
    val volume = doneSets.sumOf { (it.weightKg ?: 0.0) * (it.actualReps ?: 0) }
    val bestBroadJump = doneSets.filter { it.exerciseId.substringBefore('|') == "broad_jump" }.mapNotNull { it.distanceCm }.maxOrNull()
    val topExercises = doneSets.groupBy { it.exerciseName }.map { (name, rows) -> name to (rows.mapNotNull { it.weightKg }.maxOrNull() ?: 0.0) }
        .filter { it.second > 0 }.sortedByDescending { it.second }.take(5)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Text("训练进展", fontSize = 25.sp, fontWeight = FontWeight.Bold) }
        item {
            InfoCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Stat("近 7 天训练", "${recent.size} 次")
                    Stat("累计完成", "${doneSets.size} 组")
                    Stat("累计训练量", "${volume.toInt()} kg")
                }
            }
        }
        if (bestBroadJump != null) item {
            InfoCard { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("立定跳远最佳")
                Text("${formatWeight(bestBroadJump)} cm", color = highlight, fontWeight = FontWeight.Bold)
            } }
        }
        item { Text("体重周均值", fontSize = 19.sp, fontWeight = FontWeight.Bold) }
        if (latestWeeks.isEmpty()) item { Text("在每日打卡中记录体重后，这里会出现趋势。", color = secondary) }
        items(latestWeeks) { (monday, rows) ->
            InfoCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${monday.monthValue}/${monday.dayOfMonth} 周")
                    Text("${formatWeight(rows.map { it.second }.average())} kg", color = highlight, fontWeight = FontWeight.Bold)
                }
            }
        }
        item { Text("动作最高记录", fontSize = 19.sp, fontWeight = FontWeight.Bold) }
        if (topExercises.isEmpty()) item { Text("完成并记录重量后，这里会显示动作记录。", color = secondary) }
        items(topExercises) { (name, weight) ->
            InfoCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(name)
                    Text("${formatWeight(weight)} kg", color = highlight)
                }
            }
        }
        item { Text("最近训练", fontSize = 19.sp, fontWeight = FontWeight.Bold) }
        items(completed.take(8)) { session ->
            InfoCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(session.date)
                    Text(TrainingPlan.byCode(session.workoutCode)?.title ?: session.workoutCode, color = highlight)
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = highlight, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(label, color = secondary, fontSize = 11.sp)
    }
}

@Composable
fun SettingsScreen(remindersEnabled: Boolean, onReminder: (Boolean) -> Unit, onExport: () -> Unit, onCsv: () -> Unit, onImport: () -> Unit, onPlanImport: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Text("设置与数据", fontSize = 25.sp, fontWeight = FontWeight.Bold) }
        item {
            InfoCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("每日提醒", fontWeight = FontWeight.Bold)
                        Text("每天 18:00 提醒训练或打卡", color = secondary, fontSize = 12.sp)
                    }
                    Switch(checked = remindersEnabled, onCheckedChange = onReminder)
                }
            }
        }
        item {
            InfoCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("数据备份", fontWeight = FontWeight.Bold)
                    Text("记录只保存在这台设备。建议定期导出备份。", color = secondary, fontSize = 12.sp)
                    Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) { Text("导出 JSON 备份") }
                    OutlinedButton(onClick = onCsv, modifier = Modifier.fillMaxWidth()) { Text("导出 CSV 训练记录") }
                    OutlinedButton(onClick = onPlanImport, modifier = Modifier.fillMaxWidth()) { Text("导入训练计划 JSON") }
                    OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text("从 JSON 恢复") }
                }
            }
        }
        item {
            InfoCard {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("关于", fontWeight = FontWeight.Bold)
                    Text("进阶训练 1.0.2 · 安卓离线版", color = secondary)
                    Text("不需要账号。训练记录不会自动上传。", color = secondary, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = cardBackground)) {
        Box(Modifier.padding(18.dp)) { content() }
    }
}
