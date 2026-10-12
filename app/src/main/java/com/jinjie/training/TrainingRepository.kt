package com.jinjie.training

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

data class DaySnapshot(
    val date: LocalDate,
    val code: String,
    val session: TrainingSession?,
    val sets: List<SetLog>,
    val checkin: DailyCheckin?,
    val scheduleNote: String,
    val suggestions: List<SetSuggestion>
)

data class PlanImportResult(val importedDays: Int, val skippedDates: List<String>, val updatedExercises: Int)

private data class ImportedPlanDay(val schedule: DaySchedule, val suggestions: List<SetSuggestion>)

data class ProgressSnapshot(
    val sessions: List<TrainingSession>,
    val sets: List<SetLog>,
    val checkins: List<DailyCheckin>
)

class TrainingRepository(context: Context) {
    private val db = TrainingDatabase.get(context)
    private val dao = db.dao()
    private val preferences = context.getSharedPreferences("training_settings", Context.MODE_PRIVATE)

    val startDate: LocalDate
        get() {
            val saved = preferences.getString("start_date", null)
            if (saved != null) return LocalDate.parse(saved)
            val today = LocalDate.now()
            preferences.edit().putString("start_date", today.toString()).apply()
            return today
        }

    var remindersEnabled: Boolean
        get() = preferences.getBoolean("reminders_enabled", false)
        set(value) { preferences.edit().putBoolean("reminders_enabled", value).apply() }

    suspend fun day(date: LocalDate): DaySnapshot = withContext(Dispatchers.IO) {
        val key = date.toString()
        val session = dao.sessionOn(key)
        val schedule = dao.scheduleOn(key)
        DaySnapshot(
            date,
            session?.workoutCode ?: schedule?.workoutCode ?: TrainingPlan.defaultFor(date),
            session,
            session?.let { dao.setsFor(it.id) } ?: emptyList(),
            dao.checkinOn(key),
            schedule?.note.orEmpty(),
            dao.suggestionsOn(key)
        )
    }

    suspend fun progress(): ProgressSnapshot = withContext(Dispatchers.IO) {
        ProgressSnapshot(dao.allSessions(), dao.allSets(), dao.allCheckins())
    }

    suspend fun adjustments(): List<PlanAdjustment> = withContext(Dispatchers.IO) { dao.allAdjustments() }

    suspend fun saveAdjustment(adjustment: PlanAdjustment) = withContext(Dispatchers.IO) {
        require(adjustment.setCount == null || adjustment.setCount in 1..8)
        dao.saveAdjustment(adjustment)
    }

    suspend fun setSchedule(date: LocalDate, code: String) = withContext(Dispatchers.IO) {
        require(code == "REST" || TrainingPlan.byCode(code) != null) { "未知训练安排" }
        db.withTransaction {
            require(dao.sessionOn(date.toString()) == null) { "这一天已有训练记录，无法更改安排" }
            dao.saveSchedule(DaySchedule(date.toString(), code))
            dao.deleteSuggestionsOn(date.toString())
        }
    }

    suspend fun saveCheckin(date: LocalDate, status: String, weight: Double?, energy: Int?) = withContext(Dispatchers.IO) {
        dao.saveCheckin(DailyCheckin(date.toString(), status, weight, energy))
    }

    suspend fun startWorkout(date: LocalDate, code: String): Long = withContext(Dispatchers.IO) {
        dao.sessionOn(date.toString())?.id ?: db.withTransaction {
            val spec = requireNotNull(TrainingPlan.byCode(code))
            val id = dao.insertSession(TrainingSession(date = date.toString(), workoutCode = code, startedAt = System.currentTimeMillis()))
            val week = TrainingPlan.weekNumber(startDate, date)
            val adjustments = dao.allAdjustments().filter { it.workoutCode == code }.associateBy { it.exerciseId }
            val suggestions = dao.suggestionsOn(date.toString()).associateBy { it.exerciseId to it.setNumber }
            val rows = spec.exercises.flatMap { exercise ->
                val adjustment = adjustments[exercise.id]
                val name = adjustment?.replacementName?.takeIf { it.isNotBlank() } ?: exercise.name
                val effectiveId = if (name == exercise.name) exercise.id else "${exercise.id}|$name"
                val previous = dao.lastCompletedSet(effectiveId)
                val setCount = TrainingPlan.prescribedSets(adjustment?.setCount ?: exercise.sets, week)
                (1..setCount).map { number ->
                    SetLog(
                        sessionId = id,
                        exerciseId = effectiveId,
                        exerciseName = name,
                        setNumber = number,
                        targetReps = exercise.reps,
                        restSeconds = exercise.restSeconds,
                        weightKg = suggestions[effectiveId to number]?.weightKg ?: previous?.weightKg
                    )
                }
            }
            dao.insertSets(rows)
            id
        }
    }

    suspend fun saveSet(row: SetLog) = withContext(Dispatchers.IO) { dao.saveSet(row) }

    suspend fun addSet(sessionId: Long, exerciseId: String) = withContext(Dispatchers.IO) {
        val rows = dao.setsFor(sessionId).filter { it.exerciseId == exerciseId }
        val last = rows.lastOrNull() ?: return@withContext
        dao.saveSet(last.copy(id = 0, setNumber = (rows.maxOfOrNull { it.setNumber } ?: 0) + 1, completed = false, actualReps = null, rir = null, distanceCm = null))
    }

    suspend fun removeSet(row: SetLog) = withContext(Dispatchers.IO) { dao.deleteSet(row.id) }

    suspend fun replaceExercise(sessionId: Long, exerciseId: String, replacementName: String) = withContext(Dispatchers.IO) {
        val originalId = exerciseId.substringBefore('|')
        val original = TrainingPlan.workouts.flatMap { it.exercises }.firstOrNull { it.id == originalId }
        val newId = if (replacementName == original?.name) originalId else "$originalId|$replacementName"
        dao.setsFor(sessionId).filter { it.exerciseId == exerciseId }.forEach { row ->
            dao.saveSet(row.copy(exerciseId = newId, exerciseName = replacementName))
        }
    }

    suspend fun finishWorkout(session: TrainingSession) = withContext(Dispatchers.IO) {
        dao.saveSession(session.copy(status = "DONE", finishedAt = System.currentTimeMillis()))
        val existing = dao.checkinOn(session.date)
        dao.saveCheckin(DailyCheckin(session.date, "TRAINED", existing?.weightKg, existing?.energy))
    }

    suspend fun exportJson(): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("format", "jinjie-training")
        root.put("version", 1)
        root.put("startDate", startDate.toString())
        root.put("sessions", JSONArray().apply {
            dao.allSessions().forEach { row -> put(JSONObject().apply {
                put("id", row.id); put("date", row.date); put("workoutCode", row.workoutCode)
                put("startedAt", row.startedAt); put("finishedAt", row.finishedAt ?: JSONObject.NULL); put("status", row.status)
            }) }
        })
        root.put("sets", JSONArray().apply {
            dao.allSets().forEach { row -> put(JSONObject().apply {
                put("id", row.id); put("sessionId", row.sessionId); put("exerciseId", row.exerciseId)
                put("exerciseName", row.exerciseName); put("setNumber", row.setNumber)
                put("targetReps", row.targetReps); put("restSeconds", row.restSeconds)
                put("weightKg", row.weightKg ?: JSONObject.NULL); put("actualReps", row.actualReps ?: JSONObject.NULL)
                put("rir", row.rir ?: JSONObject.NULL); put("distanceCm", row.distanceCm ?: JSONObject.NULL)
                put("completed", row.completed); put("note", row.note)
            }) }
        })
        root.put("checkins", JSONArray().apply {
            dao.allCheckins().forEach { row -> put(JSONObject().apply {
                put("date", row.date); put("status", row.status)
                put("weightKg", row.weightKg ?: JSONObject.NULL); put("energy", row.energy ?: JSONObject.NULL)
            }) }
        })
        root.put("schedule", JSONArray().apply {
            dao.allSchedules().forEach { row -> put(JSONObject().apply {
                put("date", row.date); put("workoutCode", row.workoutCode); put("note", row.note)
            }) }
        })
        root.put("suggestions", JSONArray().apply {
            dao.allSuggestions().forEach { row -> put(JSONObject().apply {
                put("date", row.date); put("exerciseId", row.exerciseId); put("setNumber", row.setNumber)
                put("weightKg", row.weightKg ?: JSONObject.NULL); put("guidance", row.guidance)
            }) }
        })
        root.put("adjustments", JSONArray().apply {
            dao.allAdjustments().forEach { row -> put(JSONObject().apply {
                put("workoutCode", row.workoutCode); put("exerciseId", row.exerciseId)
                put("replacementName", row.replacementName ?: JSONObject.NULL)
                put("setCount", row.setCount ?: JSONObject.NULL)
            }) }
        })
        root.toString(2)
    }

    suspend fun exportCsv(): String = withContext(Dispatchers.IO) {
        val sessions = dao.allSessions().associateBy { it.id }
        buildString {
            append("\uFEFF日期,训练,动作,组数,目标次数,实际次数,重量kg,余力,距离cm,完成\r\n")
            dao.allSets().forEach { row ->
                val session = sessions[row.sessionId]
                val fields = listOf(
                    session?.date.orEmpty(),
                    TrainingPlan.byCode(session?.workoutCode.orEmpty())?.title.orEmpty(),
                    row.exerciseName,
                    row.setNumber.toString(),
                    row.targetReps,
                    row.actualReps?.toString().orEmpty(),
                    row.weightKg?.toString().orEmpty(),
                    row.rir?.toString().orEmpty(),
                    row.distanceCm?.toString().orEmpty(),
                    if (row.completed) "是" else "否"
                )
                append(fields.joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" })
                append("\r\n")
            }
        }
    }

    suspend fun importPlanJson(raw: String): PlanImportResult = withContext(Dispatchers.IO) {
        require(raw.length <= 1_000_000) { "计划文件过大" }
        val root = JSONObject(raw)
        require(root.optString("format") == "jinjie-training-plan" && root.optInt("version") in 1..2) { "训练计划格式不受支持" }
        val overrides = root.optJSONArray("exerciseOverrides")?.objects()?.map { row ->
            val code = row.getString("workoutCode")
            val exerciseId = row.getString("exerciseId")
            val exercise = TrainingPlan.byCode(code)?.exercises?.firstOrNull { it.id == exerciseId }
                ?: error("未知动作替换：$code / $exerciseId")
            val name = row.getString("replacementName").trim()
            require(name in exercise.alternatives && '|' !in name) { "${exercise.name} 不支持替换为 $name" }
            PlanAdjustment(code, exerciseId, replacementName = name)
        } ?: emptyList()
        require(overrides.distinctBy { it.workoutCode to it.exerciseId }.size == overrides.size) { "计划中有重复动作替换" }
        val array = root.getJSONArray("days")
        require(array.length() in 1..90) { "计划日期数量应为 1–90 天" }
        val days = array.objects().map { item ->
            val date = LocalDate.parse(item.getString("date")).toString()
            val code = item.getString("workoutCode")
            val spec = TrainingPlan.byCode(code)
            require(code == "REST" || spec != null) { "未知训练类型：$code" }
            val note = item.optString("note", "").trim()
            require(note.length <= 240) { "$date 的备注过长" }
            val setArray = item.getJSONArray("sets")
            require(setArray.length() <= 60) { "$date 的组数过多" }
            val suggestions = setArray.objects().map { row ->
                val exerciseId = row.getString("exerciseId")
                val exercise = spec?.exercises?.firstOrNull { it.id == exerciseId }
                require(exercise != null) { "$date 有未知动作：$exerciseId" }
                val forName = row.optString("forExerciseName", "").trim()
                if (forName.isNotEmpty()) {
                    require(overrides.any { it.workoutCode == code && it.exerciseId == exerciseId && it.replacementName == forName }) {
                        "$date 的 $exerciseId 缺少匹配的动作替换"
                    }
                }
                val effectiveId = if (forName.isEmpty()) exerciseId else "$exerciseId|$forName"
                val setNumber = row.getInt("setNumber")
                require(setNumber in 1..8) { "$date 的组号应为 1–8" }
                val weight = row.nullableDouble("suggestedWeightKg")
                require(weight == null || (weight.isFinite() && weight >= 0 && weight < 1000)) { "$date 的建议重量无效" }
                val guidance = row.optString("weightGuide", "").trim()
                require(guidance.length <= 240 && (weight != null || guidance.isNotBlank())) { "$date 第 $setNumber 组缺少重量建议" }
                SetSuggestion(date, effectiveId, setNumber, weight, guidance)
            }
            require(suggestions.distinctBy { it.exerciseId to it.setNumber }.size == suggestions.size) { "$date 有重复组号" }
            ImportedPlanDay(DaySchedule(date, code, note), suggestions)
        }
        require(days.map { it.schedule.date }.distinct().size == days.size) { "计划中有重复日期" }
        db.withTransaction {
            val skipped = mutableListOf<String>()
            val existingAdjustments = dao.allAdjustments().associateBy { it.workoutCode to it.exerciseId }
            overrides.forEach { replacement ->
                val existing = existingAdjustments[replacement.workoutCode to replacement.exerciseId]
                dao.saveAdjustment(replacement.copy(setCount = existing?.setCount))
            }
            days.forEach { planned ->
                val date = planned.schedule.date
                if (dao.sessionOn(date) != null) {
                    skipped += date
                } else {
                    dao.saveSchedule(planned.schedule)
                    dao.deleteSuggestionsOn(date)
                    dao.saveSuggestions(planned.suggestions)
                }
            }
            PlanImportResult(days.size - skipped.size, skipped, overrides.size)
        }
    }

    suspend fun importJson(raw: String) = withContext(Dispatchers.IO) {
        val root = JSONObject(raw)
        require(root.getString("format") == "jinjie-training" && root.getInt("version") == 1) { "备份格式不受支持" }
        val sessions = root.getJSONArray("sessions").objects().map { row ->
            TrainingSession(row.getLong("id"), row.getString("date"), row.getString("workoutCode"), row.getLong("startedAt"), row.nullableLong("finishedAt"), row.getString("status"))
        }
        val sets = root.getJSONArray("sets").objects().map { row ->
            SetLog(row.getLong("id"), row.getLong("sessionId"), row.getString("exerciseId"), row.getString("exerciseName"), row.getInt("setNumber"), row.getString("targetReps"), row.getInt("restSeconds"), row.nullableDouble("weightKg"), row.nullableInt("actualReps"), row.nullableInt("rir"), row.nullableDouble("distanceCm"), row.getBoolean("completed"), row.optString("note", ""))
        }
        val checkins = root.getJSONArray("checkins").objects().map { row ->
            DailyCheckin(row.getString("date"), row.getString("status"), row.nullableDouble("weightKg"), row.nullableInt("energy"))
        }
        val schedule = root.getJSONArray("schedule").objects().map { row ->
            DaySchedule(row.getString("date"), row.getString("workoutCode"), row.optString("note", ""))
        }
        val suggestions = root.optJSONArray("suggestions")?.objects()?.map { row ->
            SetSuggestion(row.getString("date"), row.getString("exerciseId"), row.getInt("setNumber"), row.nullableDouble("weightKg"), row.optString("guidance", ""))
        } ?: emptyList()
        val adjustments = root.optJSONArray("adjustments")?.objects()?.map { row ->
            PlanAdjustment(row.getString("workoutCode"), row.getString("exerciseId"), row.nullableString("replacementName"), row.nullableInt("setCount"))
        } ?: emptyList()
        val parsedStart = LocalDate.parse(root.getString("startDate"))
        val ids = sessions.map { it.id }.toSet()
        require(sets.all { it.sessionId in ids }) { "备份中的训练组缺少对应训练记录" }
        db.withTransaction {
            dao.clearSets(); dao.clearSessions(); dao.clearCheckins(); dao.clearSchedules(); dao.clearAdjustments(); dao.clearSuggestions()
            dao.restoreSessions(sessions); dao.restoreSets(sets)
            dao.restoreCheckins(checkins); dao.restoreSchedules(schedule); dao.restoreAdjustments(adjustments); dao.restoreSuggestions(suggestions)
        }
        preferences.edit().putString("start_date", parsedStart.toString()).apply()
    }
}

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
private fun JSONObject.nullableLong(key: String): Long? = if (isNull(key)) null else getLong(key)
private fun JSONObject.nullableInt(key: String): Int? = if (isNull(key)) null else getInt(key)
private fun JSONObject.nullableDouble(key: String): Double? = if (isNull(key)) null else getDouble(key)
private fun JSONObject.nullableString(key: String): String? = if (isNull(key)) null else getString(key)
