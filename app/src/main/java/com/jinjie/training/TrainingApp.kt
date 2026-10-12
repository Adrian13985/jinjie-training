package com.jinjie.training

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val background = Color(0xFF0D141B)
private val surface = Color(0xFF1A2630)
private val accent = Color(0xFFC9F15D)
private val muted = Color(0xFF9BAAB4)
private val appColors = darkColorScheme(
    primary = accent,
    onPrimary = background,
    background = background,
    onBackground = Color.White,
    surface = surface,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF26343E),
    onSurfaceVariant = muted
)

private val dateFormat = DateTimeFormatter.ofPattern("M 月 d 日 · EEEE", Locale.CHINA)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingApp(repository: TrainingRepository) {
    MaterialTheme(colorScheme = appColors) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var selectedDateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
        val selectedDate = remember(selectedDateText) { LocalDate.parse(selectedDateText) }
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var inWorkout by rememberSaveable { mutableStateOf(false) }
        var refresh by remember { mutableIntStateOf(0) }
        var day by remember { mutableStateOf<DaySnapshot?>(null) }
        var progress by remember { mutableStateOf<ProgressSnapshot?>(null) }
        var adjustments by remember { mutableStateOf<List<PlanAdjustment>>(emptyList()) }
        var showSchedule by remember { mutableStateOf(false) }
        var showCheckin by remember { mutableStateOf(false) }
        var pendingImport by remember { mutableStateOf<String?>(null) }
        var pendingPlanImport by remember { mutableStateOf<String?>(null) }
        var planImportFeedback by remember { mutableStateOf<String?>(null) }

        fun runAction(action: suspend () -> Unit) {
            scope.launch {
                try { action(); refresh++ }
                catch (e: Exception) { toast(context, e.message ?: "操作失败") }
            }
        }

        val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) runAction {
                val text = repository.exportJson()
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(text) }
                    ?: error("无法写入备份文件")
                toast(context, "备份已保存")
            }
        }
        val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            if (uri != null) runAction {
                val content = repository.exportCsv()
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(content) }
                    ?: error("无法写入 CSV 文件")
                toast(context, "CSV 已导出")
            }
        }
        val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) runAction {
                pendingImport = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("无法读取备份文件")
            }
        }
        val planImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) runAction {
                pendingPlanImport = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("无法读取训练计划")
            }
        }
        val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            repository.remindersEnabled = granted
            ReminderScheduler.configure(context, granted)
            refresh++
            if (!granted) toast(context, "未获得通知权限，提醒未开启")
        }

        LaunchedEffect(selectedDateText, refresh) {
            day = repository.day(selectedDate)
            progress = repository.progress()
            adjustments = repository.adjustments()
        }
        BackHandler(inWorkout) { inWorkout = false }

        Scaffold(
            containerColor = background,
            topBar = {
                TopAppBar(
                    title = { Text(if (inWorkout) "正在训练" else "进阶训练", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        if (inWorkout) TextButton(onClick = { inWorkout = false }) { Text("返回") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = background)
                )
            },
            bottomBar = {
                if (!inWorkout) NavigationBar(containerColor = surface) {
                    val tabs = listOf("今日", "计划", "进展", "设置")
                    val icons = listOf(Icons.Default.FitnessCenter, Icons.Default.CalendarMonth, Icons.Default.Assessment, Icons.Default.Settings)
                    tabs.forEachIndexed { index, name ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = { tab = index },
                            icon = { Icon(icons[index], contentDescription = name) },
                            label = { Text(name) }
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when {
                    inWorkout -> day?.let { snapshot ->
                        WorkoutScreen(
                            snapshot,
                            onSetSave = { row -> runAction { repository.saveSet(row) } },
                            onAddSet = { id -> runAction { repository.addSet(snapshot.session!!.id, id) } },
                            onRemoveSet = { row -> runAction { repository.removeSet(row) } },
                            onReplaceExercise = { id, name -> runAction { repository.replaceExercise(snapshot.session!!.id, id, name) } },
                            onFinish = { runAction { repository.finishWorkout(snapshot.session!!); inWorkout = false } }
                        )
                    }
                    tab == 0 -> day?.let { snapshot ->
                        TodayScreen(
                            snapshot,
                            TrainingPlan.weekNumber(repository.startDate, selectedDate),
                            adjustments,
                            onPrev = { selectedDateText = selectedDate.minusDays(1).toString() },
                            onNext = { selectedDateText = selectedDate.plusDays(1).toString() },
                            onToday = { selectedDateText = LocalDate.now().toString() },
                            onSchedule = { showSchedule = true },
                            onCheckin = { showCheckin = true },
                            onStart = { runAction {
                                repository.startWorkout(selectedDate, snapshot.code)
                                day = repository.day(selectedDate)
                                inWorkout = true
                            } },
                            onResume = { inWorkout = true }
                        )
                    }
                    tab == 1 -> PlanScreen(repository.startDate, adjustments) { adjustment ->
                        runAction { repository.saveAdjustment(adjustment) }
                    }
                    tab == 2 -> progress?.let { ProgressScreen(it) }
                    tab == 3 -> SettingsScreen(
                        repository.remindersEnabled,
                        onReminder = { enabled ->
                            if (enabled && Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                repository.remindersEnabled = enabled
                                ReminderScheduler.configure(context, enabled)
                                refresh++
                            }
                        },
                        onExport = { exportLauncher.launch("进阶训练备份-${LocalDate.now()}.json") },
                        onCsv = { csvLauncher.launch("进阶训练记录-${LocalDate.now()}.csv") },
                        onImport = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                        onPlanImport = { planImportLauncher.launch(arrayOf("application/json", "text/plain")) }
                    )
                }
            }
        }

        if (showSchedule) ScheduleDialog(selectedDate, day?.code ?: "REST", onDismiss = { showSchedule = false }) { code ->
            runAction { repository.setSchedule(selectedDate, code) }
            showSchedule = false
        }
        if (showCheckin) CheckinDialog(day?.checkin, day?.code ?: "REST", onDismiss = { showCheckin = false }) { status, weight, energy ->
            runAction { repository.saveCheckin(selectedDate, status, weight, energy) }
            showCheckin = false
        }
        if (pendingImport != null) AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("恢复备份？") },
            text = { Text("这会替换手机里的全部训练记录。请确认备份文件来自你自己。") },
            confirmButton = { TextButton(onClick = {
                val raw = pendingImport ?: return@TextButton
                pendingImport = null
                runAction { repository.importJson(raw); toast(context, "恢复完成") }
            }) { Text("恢复") } },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("取消") } }
        )
        if (pendingPlanImport != null) AlertDialog(
            onDismissRequest = { pendingPlanImport = null },
            title = { Text("导入训练计划？") },
            text = { Text("这会更新计划文件列出的日期、逐组建议和动作替换。已有训练记录与每日打卡会保留；已开始训练的日期会跳过。") },
            confirmButton = { TextButton(onClick = {
                val raw = pendingPlanImport ?: return@TextButton
                pendingPlanImport = null
                runAction {
                    val result = repository.importPlanJson(raw)
                    val replacementText = if (result.updatedExercises > 0) "已更新 ${result.updatedExercises} 个动作替换。" else ""
                    planImportFeedback = if (result.skippedDates.isEmpty()) {
                        "已导入 ${result.importedDays} 天安排。$replacementText 原有训练记录和每日打卡已保留。"
                    } else {
                        "已导入 ${result.importedDays} 天。$replacementText 以下日期已有训练记录，未覆盖：${result.skippedDates.joinToString()}。"
                    }
                    tab = 0
                    selectedDateText = LocalDate.now().toString()
                }
            }) { Text("导入") } },
            dismissButton = { TextButton(onClick = { pendingPlanImport = null }) { Text("取消") } }
        )
        if (planImportFeedback != null) AlertDialog(
            onDismissRequest = { planImportFeedback = null },
            title = { Text("计划导入结果") },
            text = { Text(planImportFeedback.orEmpty()) },
            confirmButton = { TextButton(onClick = { planImportFeedback = null }) { Text("知道了") } }
        )
    }
}

private fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

@Composable
private fun SectionCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = surface)) {
        Box(Modifier.padding(18.dp)) { content() }
    }
}

@Composable
private fun Heading(title: String, subtitle: String? = null) {
    Column {
        Text(title, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        if (subtitle != null) Text(subtitle, color = muted, fontSize = 13.sp)
    }
}

@Composable
private fun TodayScreen(
    day: DaySnapshot,
    week: Int,
    adjustments: List<PlanAdjustment>,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onSchedule: () -> Unit,
    onCheckin: () -> Unit,
    onStart: () -> Unit,
    onResume: () -> Unit
) {
    val spec = TrainingPlan.byCode(day.code)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onPrev) { Icon(Icons.Default.ChevronLeft, "前一天") }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(day.date.format(dateFormat), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("第 $week 周 · ${TrainingPlan.phase(week)}", color = muted, fontSize = 11.sp, textAlign = TextAlign.Center)
                }
                IconButton(onClick = onNext) { Icon(Icons.Default.ChevronRight, "后一天") }
            }
            TextButton(onClick = onToday, modifier = Modifier.fillMaxWidth()) { Text("回到今天") }
        }
        item {
            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (spec == null) "恢复日" else spec.title, color = accent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text(spec?.focus ?: "休息、轻松活动与恢复", color = muted)
                    if (day.scheduleNote.isNotBlank()) Text(day.scheduleNote, color = muted, fontSize = 13.sp)
                    if (day.session != null) {
                        Text("已完成 ${day.sets.count { it.completed }} / ${day.sets.size} 组", fontWeight = FontWeight.SemiBold)
                        Button(onClick = onResume, modifier = Modifier.fillMaxWidth()) { Text(if (day.session.status == "DONE") "查看训练" else "继续训练") }
                    } else if (spec != null) {
                        Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("开始训练") }
                    }
                    OutlinedButton(onClick = onSchedule, modifier = Modifier.fillMaxWidth()) { Text("调整这一天") }
                }
            }
        }
        item {
            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = if (day.checkin != null) accent else muted)
                        Spacer(Modifier.width(8.dp))
                        Text(if (day.checkin == null) "今日尚未打卡" else "已打卡 · ${if (day.checkin.status == "TRAINED") "完成训练" else "恢复日"}", fontWeight = FontWeight.Bold)
                    }
                    day.checkin?.weightKg?.let { Text("体重 ${formatWeight(it)} kg", color = muted) }
                    OutlinedButton(onClick = onCheckin, modifier = Modifier.fillMaxWidth()) { Text(if (day.checkin == null) "每日打卡" else "修改打卡") }
                }
            }
        }
        if (spec != null) {
            item { Heading("今日动作", "先热身 5–8 分钟，再完成下列训练") }
            items(spec.exercises) { exercise ->
                val adjustment = adjustments.firstOrNull { it.workoutCode == spec.code && it.exerciseId == exercise.id }
                SectionCard {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(adjustment?.replacementName ?: exercise.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("${TrainingPlan.prescribedSets(adjustment?.setCount ?: exercise.sets, week)} 组 × ${exercise.reps} · 休息 ${exercise.restSeconds} 秒", color = accent)
                        Text(exercise.cue, color = muted, fontSize = 12.sp)
                    }
                }
            }
        } else item {
            Text("可以散步、做轻活动，并留意睡眠和饮食。", color = muted)
        }
    }
}

@Composable
private fun ScheduleDialog(date: LocalDate, current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var selected by remember(date) { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("调整 ${date.format(dateFormat)}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (TrainingPlan.workouts.map { it.code to it.title } + ("REST" to "休息日")).forEach { (code, title) ->
                    FilterChip(selected = selected == code, onClick = { selected = code }, label = { Text(title) })
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(selected) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun CheckinDialog(existing: DailyCheckin?, scheduledCode: String, onDismiss: () -> Unit, onSave: (String, Double?, Int?) -> Unit) {
    var status by remember(existing) { mutableStateOf(existing?.status ?: if (scheduledCode == "REST") "REST" else "TRAINED") }
    var weight by remember(existing) { mutableStateOf(existing?.weightKg?.toString() ?: "") }
    var energy by remember(existing) { mutableIntStateOf(existing?.energy ?: 3) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("每日打卡") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("今天的状态")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = status == "TRAINED", onClick = { status = "TRAINED" }, label = { Text("已训练") })
                    FilterChip(selected = status == "REST", onClick = { status = "REST" }, label = { Text("恢复日") })
                }
                OutlinedTextField(weight, { weight = it }, label = { Text("体重（kg，可选）") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                Text("精力感受 · $energy / 5")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..5).forEach { value -> FilterChip(selected = energy == value, onClick = { energy = value }, label = { Text("$value") }) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(status, weight.toDoubleOrNull()?.takeIf { it > 0 && it < 500 }, energy) }) { Text("打卡") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
