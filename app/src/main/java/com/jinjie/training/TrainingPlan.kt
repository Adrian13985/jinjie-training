package com.jinjie.training

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

data class ExerciseSpec(
    val id: String,
    val name: String,
    val sets: Int,
    val reps: String,
    val restSeconds: Int,
    val cue: String,
    val alternatives: List<String> = emptyList(),
    val isPower: Boolean = false
)

data class WorkoutSpec(val code: String, val title: String, val focus: String, val exercises: List<ExerciseSpec>)

object TrainingPlan {
    val workouts = listOf(
        WorkoutSpec("LOWER_A", "下肢 A", "深蹲 · 髋铰链 · 跳跃", listOf(
            ExerciseSpec("vertical_jump", "垂直跳", 3, "3", 60, "充分热身后轻快起跳；落地稳定，速度下降就停止。", listOf("箱跳"), true),
            ExerciseSpec("squat", "杠铃深蹲", 3, "6–8", 150, "全脚掌稳定，下降到能控制的深度；保留约 2 次余力。", listOf("高脚杯深蹲", "哈克深蹲")),
            ExerciseSpec("rdl", "罗马尼亚硬拉", 2, "8–10", 120, "髋部向后，背部保持稳定，感受腘绳肌拉伸。", listOf("哑铃罗马尼亚硬拉")),
            ExerciseSpec("split_squat", "保加利亚分腿蹲", 2, "每侧 8", 90, "先稳住身体，再逐步加重。", listOf("反向弓步")),
            ExerciseSpec("plank", "平板支撑", 2, "30–45 秒", 60, "保持自然呼吸，不塌腰。")
        )),
        WorkoutSpec("UPPER_A", "上肢 A", "卧推 · 水平拉 · 快速推", listOf(
            ExerciseSpec("power_pushup", "上斜爆发俯卧撑", 3, "3", 60, "双手置于稳固长凳；快速推起，控制落下。", listOf("药球胸前推"), true),
            ExerciseSpec("bench", "杠铃卧推", 3, "6–8", 150, "肩胛收紧、双脚稳定；保留约 2 次余力。", listOf("哑铃卧推")),
            ExerciseSpec("row", "坐姿划船", 3, "8–10", 90, "先收肩胛再拉肘，避免甩动。", listOf("胸托哑铃划船")),
            ExerciseSpec("incline_press", "上斜哑铃推", 2, "8–10", 90, "控制下放，向上平稳推起。", listOf("上斜器械推胸")),
            ExerciseSpec("pulldown", "高位下拉", 2, "8–12", 90, "把手拉向上胸，避免后仰借力。", listOf("辅助引体向上")),
            ExerciseSpec("lateral_raise", "哑铃侧平举", 2, "12–15", 60, "轻重量，手臂向两侧抬至肩高。")
        )),
        WorkoutSpec("LOWER_B", "下肢 B", "硬拉 · 腿举 · 跳远", listOf(
            ExerciseSpec("broad_jump", "立定跳远", 3, "3", 60, "每次落地稳定，记录最远距离。", listOf("箱跳"), true),
            ExerciseSpec("trap_deadlift", "六角杠硬拉", 3, "5–6", 150, "先建立站姿和核心张力，再平稳拉起。", listOf("杠铃硬拉", "哑铃硬拉")),
            ExerciseSpec("leg_press", "腿举", 3, "8–12", 180, "全程控制，膝盖与脚尖方向一致。", listOf("高脚杯深蹲")),
            ExerciseSpec("leg_curl", "腿弯举", 2, "10–12", 75, "缓慢放回，避免惯性。"),
            ExerciseSpec("calf_raise", "站姿提踵", 2, "12–15", 60, "顶点稍停顿，再充分下放。"),
            ExerciseSpec("side_plank", "侧平板支撑", 2, "每侧 30 秒", 60, "身体保持一条直线。")
        )),
        WorkoutSpec("UPPER_B", "上肢 B", "肩推 · 垂直拉 · 手臂", listOf(
            ExerciseSpec("ohp", "哑铃肩推", 3, "6–8", 120, "核心收紧，避免腰部过度后仰。", listOf("器械肩推")),
            ExerciseSpec("pullup", "辅助引体向上", 3, "6–10", 120, "先下沉肩胛，再向上拉；逐步减少辅助重量。", listOf("高位下拉")),
            ExerciseSpec("chest_press", "器械推胸", 3, "8–12", 90, "控制下放，稳定推起。", listOf("哑铃卧推")),
            ExerciseSpec("cable_row", "绳索划船", 2, "8–12", 90, "肘部向后，躯干保持稳定。", listOf("单臂哑铃划船")),
            ExerciseSpec("curl", "哑铃弯举", 2, "10–12", 60, "上臂贴近身体，避免甩动。"),
            ExerciseSpec("triceps", "绳索下压", 2, "10–12", 60, "固定上臂，伸直手肘。", listOf("哑铃臂屈伸"))
        ))
    )

    fun byCode(code: String): WorkoutSpec? = workouts.firstOrNull { it.code == code }

    fun defaultFor(date: LocalDate): String = when (date.dayOfWeek) {
        DayOfWeek.MONDAY -> "LOWER_A"
        DayOfWeek.TUESDAY -> "UPPER_A"
        DayOfWeek.THURSDAY -> "LOWER_B"
        DayOfWeek.SATURDAY -> "UPPER_B"
        else -> "REST"
    }

    fun weekNumber(start: LocalDate, date: LocalDate): Int {
        val firstMonday = start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return (ChronoUnit.WEEKS.between(firstMonday, date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))) + 1).toInt().coerceAtLeast(1)
    }

    fun prescribedSets(base: Int, week: Int): Int = when {
        week <= 2 -> (base - 1).coerceAtLeast(1)
        week == 9 -> (base - 1).coerceAtLeast(1)
        else -> base
    }

    fun phase(week: Int): String = when (week) {
        in 1..2 -> "适应期：动作优先，保留 2–3 次余力"
        in 3..8 -> "积累期：完成目标次数后逐步加重"
        9 -> "减量周：减少组数，保持动作熟练"
        in 10..12 -> "进阶期：稳定提高训练表现"
        else -> "第一轮已完成：可继续复用或调整计划"
    }
}
