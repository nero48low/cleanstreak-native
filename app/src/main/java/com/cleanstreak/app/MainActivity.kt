package com.cleanstreak.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar

// ── Colors ───────────────────────────────────────────────────
val BgDark     = Color(0xFF1A1A2E)
val BgMid      = Color(0xFF16213E)
val BgCard     = Color(0xFF1E293B)
val BgCard2    = Color(0xFF334155)
val Green      = Color(0xFF58CC02)
val GreenLight = Color(0xFF89E219)
val Orange     = Color(0xFFFF9500)
val OrangeDark = Color(0xFFFF6A00)
val Red        = Color(0xFFFF4B4B)
val Gold       = Color(0xFFFFD700)
val Purple     = Color(0xFFA78BFA)
val TextMuted  = Color(0xFF64748B)
val TextSub    = Color(0xFF94A3B8)

// ── Data ─────────────────────────────────────────────────────
data class Badge(val id: String, val label: String, val desc: String, val icon: String, val req: Int)
data class League(val name: String, val icon: String, val min: Int)
data class DayEntry(val date: String, val clean: Boolean)
data class SlipEntry(val date: String, val time: String, val note: String)

val BADGES = listOf(
    Badge("first_day",  "First Step",      "Complete 1 clean day",      "🌱", 1),
    Badge("three_days", "Triceps of Steel", "3 clean days in a row",     "💪", 3),
    Badge("week",       "Week Warrior",     "7 clean days in a row",     "🔥", 7),
    Badge("two_weeks",  "Fortnight Knight", "14 clean days in a row",    "⚔️", 14),
    Badge("month",      "Monthly Master",   "30 clean days in a row",    "👑", 30),
    Badge("hundred",    "Century Legend",   "100 clean days in a row",   "🏆", 100),
)
val LEAGUES = listOf(
    League("Bronze",   "🥉", 0),
    League("Silver",   "🥈", 50),
    League("Gold",     "🥇", 150),
    League("Platinum", "💎", 350),
    League("Diamond",  "💠", 700),
)
val ENCOURAGEMENTS = listOf(
    "Ogni giorno conta! 🙌", "Stay clean, stay strong!",
    "You're doing great!", "Keep the streak alive! 🔥",
    "Words have power — use them well.", "Chi la dura la vince!",
)
const val XP_CLEAN = 20
const val XP_SLIP  = 15

// ── AppState ─────────────────────────────────────────────────
class AppState(private val prefs: android.content.SharedPreferences) {
    var streak       by mutableIntStateOf(prefs.getInt("streak", 0))
    var xp           by mutableIntStateOf(prefs.getInt("xp", 0))
    var totalClean   by mutableIntStateOf(prefs.getInt("totalClean", 0))
    var slips        by mutableIntStateOf(prefs.getInt("slips", 0))
    var lastCheckin  by mutableStateOf(prefs.getString("lastCheckin", "") ?: "")
    var earnedBadges by mutableStateOf(
        prefs.getStringSet("earnedBadges", emptySet())?.toMutableSet() ?: mutableSetOf()
    )
    var history  by mutableStateOf(loadHistory())
    var slipLog  by mutableStateOf(loadSlipLog())

    private fun loadHistory(): List<DayEntry> {
        val raw = prefs.getString("history", "") ?: return emptyList()
        return raw.split("|").filter { it.contains(",") }.map {
            val (d, c) = it.split(","); DayEntry(d, c == "1")
        }
    }

    private fun loadSlipLog(): List<SlipEntry> {
        val raw = prefs.getString("slipLog", "") ?: return emptyList()
        return raw.split("|").filter { it.contains("~") }.map {
            val p = it.split("~")
            SlipEntry(p.getOrElse(0) { "" }, p.getOrElse(1) { "" }, p.getOrElse(2) { "" })
        }
    }

    private fun saveHistory() {
        prefs.edit().putString("history",
            history.joinToString("|") { "${it.date},${if (it.clean) "1" else "0"}" }
        ).apply()
    }

    private fun saveSlipLog() {
        prefs.edit().putString("slipLog",
            slipLog.joinToString("|") { "${it.date}~${it.time}~${it.note}" }
        ).apply()
    }

    fun save() {
        prefs.edit()
            .putInt("streak", streak).putInt("xp", xp)
            .putInt("totalClean", totalClean).putInt("slips", slips)
            .putString("lastCheckin", lastCheckin)
            .putStringSet("earnedBadges", earnedBadges)
            .apply()
        saveHistory()
    }

    fun today() = LocalDate.now().format(DateTimeFormatter.ISO_DATE)!!

    fun logClean(): List<Badge> {
        val t = today()
        val yesterday = LocalDate.now().minusDays(1).format(DateTimeFormatter.ISO_DATE)
        streak = if (lastCheckin == yesterday || lastCheckin == t) streak + 1 else 1
        xp += XP_CLEAN; totalClean += 1; lastCheckin = t
        history = history.takeLast(29) + DayEntry(t, true)
        val nb = BADGES.filter { it.id !in earnedBadges && streak >= it.req }
        earnedBadges = (earnedBadges + nb.map { it.id }).toMutableSet()
        save(); return nb
    }

    fun logSlip(note: String = "") {
        val t = today()
        val time = java.time.LocalTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
        streak = 0; xp = maxOf(0, xp - XP_SLIP); slips += 1; lastCheckin = t
        history = history.takeLast(29) + DayEntry(t, false)
        slipLog = (slipLog + SlipEntry(t, time, note)).takeLast(50)
        save(); saveSlipLog()
    }

    fun reset() {
        streak = 0; xp = 0; totalClean = 0; slips = 0
        lastCheckin = ""; earnedBadges = mutableSetOf()
        history = emptyList(); slipLog = emptyList()
        prefs.edit().clear().apply()
    }

    fun currentLeague() = LEAGUES.lastOrNull { xp >= it.min } ?: LEAGUES.first()
    fun nextLeague()    = LEAGUES.firstOrNull { it.min > xp }
    fun xpProgress(): Float {
        val c = currentLeague(); val n = nextLeague() ?: return 1f
        return ((xp - c.min).toFloat() / (n.min - c.min)).coerceIn(0f, 1f)
    }
}

// ── Notifications ─────────────────────────────────────────────
const val CHANNEL_ID = "cleanstreak_reminder"
const val NOTIF_ID   = 1001

fun createNotificationChannel(context: Context) {
    val ch = NotificationChannel(CHANNEL_ID, "Daily Reminder",
        NotificationManager.IMPORTANCE_DEFAULT).apply {
        description = "Evening reminder to log your day"
    }
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
}

fun scheduleEveningReminder(context: Context) {
    val mgr    = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = PendingIntent.getBroadcast(
        context, 0, Intent(context, ReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 20); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
    }
    mgr.setRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis, AlarmManager.INTERVAL_DAY, intent)
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs     = context.getSharedPreferences("cleanstreak", Context.MODE_PRIVATE)
        val lastCheck = prefs.getString("lastCheckin", "") ?: ""
        val today     = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
        if (lastCheck == today) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) return
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("CleanStreak 🔥")
            .setContentText("Don't forget to log your day! Keep that streak alive.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true).build()
        NotificationManagerCompat.from(context).notify(NOTIF_ID, notif)
    }
}

// ── MainActivity ──────────────────────────────────────────────
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        createNotificationChannel(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        scheduleEveningReminder(this)
        setContent {
            CleanStreakApp(AppState(getSharedPreferences("cleanstreak", MODE_PRIVATE)))
        }
    }
}

// ── Root App ──────────────────────────────────────────────────
@Composable
fun CleanStreakApp(state: AppState) {
    var tab          by remember { mutableStateOf("home") }
    var showSlip     by remember { mutableStateOf(false) }
    var slipNote     by remember { mutableStateOf("") }
    var pendingBadge by remember { mutableStateOf<Badge?>(null) }
    var xpPop        by remember { mutableStateOf<String?>(null) }
    val todayDone    = state.lastCheckin == state.today()

    LaunchedEffect(xpPop) { if (xpPop != null) { delay(1800); xpPop = null } }

    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(BgDark, BgMid, Color(0xFF0F3460))))) {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

            // Header
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(state.currentLeague().icon, fontSize = 24.sp)
                    Text(state.currentLeague().name, fontWeight = FontWeight.ExtraBold, color = Gold, fontSize = 14.sp)
                }
                Text("CleanStreak", fontWeight = FontWeight.Black, fontSize = 20.sp, color = Green)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("⚡", fontSize = 16.sp)
                    Text("${state.xp} XP", fontWeight = FontWeight.ExtraBold, color = Gold)
                }
            }

            // XP Bar
            Column(Modifier.padding(horizontal = 20.dp)) {
                LinearProgressIndicator(
                    progress = { state.xpProgress() },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(20.dp)),
                    color = Green, trackColor = BgCard
                )
                state.nextLeague()?.let {
                    Text("${state.xp}/${it.min} XP → ${it.icon} ${it.name}",
                        fontSize = 11.sp, color = TextMuted,
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                        textAlign = TextAlign.End)
                }
            }

            Spacer(Modifier.height(8.dp))

            // Tabs
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("home" to "🏠", "badges" to "🏅", "stats" to "📊", "slips" to "💀").forEach { (v, icon) ->
                    Button(onClick = { tab = v }, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (tab == v) Green else BgCard,
                            contentColor   = if (tab == v) Color.White else TextMuted),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) { Text(icon, fontSize = 18.sp) }
                }
            }

            Spacer(Modifier.height(12.dp))

            Box(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                when (tab) {
                    "home"   -> HomeTab(state, todayDone,
                        onClean = { val nb = state.logClean(); xpPop = "+$XP_CLEAN XP"; if (nb.isNotEmpty()) pendingBadge = nb.first() },
                        onSlip  = { showSlip = true })
                    "badges" -> BadgesTab(state)
                    "stats"  -> StatsTab(state)
                    "slips"  -> SlipLogTab(state)
                }
            }
        }

        // XP pop
        AnimatedVisibility(visible = xpPop != null,
            enter = fadeIn() + slideInVertically(), exit = fadeOut() + slideOutVertically(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 100.dp)) {
            Surface(shape = RoundedCornerShape(30.dp),
                color = if (xpPop?.startsWith("+") == true) Green else Red, shadowElevation = 8.dp) {
                Text(xpPop ?: "", fontWeight = FontWeight.Black, fontSize = 20.sp, color = Color.White,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 10.dp))
            }
        }
    }

    // Slip dialog
    if (showSlip) {
        AlertDialog(
            onDismissRequest = { showSlip = false; slipNote = "" },
            containerColor = BgCard,
            title = { Text("💀 Report a Slip?", fontWeight = FontWeight.Black, color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("This resets your streak and costs −$XP_SLIP XP.", color = TextSub)
                    OutlinedTextField(
                        value = slipNote, onValueChange = { slipNote = it },
                        placeholder = { Text("What happened? (optional)", color = TextMuted, fontSize = 13.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Red, unfocusedBorderColor = BgCard2,
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                        shape = RoundedCornerShape(10.dp), maxLines = 3
                    )
                }
            },
            confirmButton = {
                Button(onClick = { state.logSlip(slipNote); xpPop = "-$XP_SLIP XP"; slipNote = ""; showSlip = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Red)) {
                    Text("I Slipped", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showSlip = false; slipNote = "" }) {
                    Text("Cancel", color = TextSub)
                }
            }
        )
    }

    // Badge reward dialog
    pendingBadge?.let { b ->
        AlertDialog(
            onDismissRequest = { pendingBadge = null },
            containerColor = BgCard,
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(b.icon, fontSize = 60.sp)
                    Text("NEW BADGE UNLOCKED", fontSize = 11.sp, letterSpacing = 3.sp, color = Gold, fontWeight = FontWeight.Bold)
                    Text(b.label, fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color.White)
                }
            },
            text = { Text(b.desc, color = TextSub, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                Button(onClick = { pendingBadge = null }, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Gold)) {
                    Text("CLAIM 🎉", fontWeight = FontWeight.Black, color = BgDark)
                }
            }
        )
    }
}

// ── Home Tab ──────────────────────────────────────────────────
@Composable
fun HomeTab(state: AppState, todayDone: Boolean, onClean: () -> Unit, onSlip: () -> Unit) {
    val encouragement = remember { ENCOURAGEMENTS.random() }
    val today = state.today()
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Streak card
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(Orange, OrangeDark))).padding(28.dp),
            contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🔥", fontSize = 56.sp)
                Text("${state.streak}", fontSize = 56.sp, fontWeight = FontWeight.Black, color = Color.White)
                Text("day streak", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.9f))
                Text(encouragement, fontSize = 13.sp, color = Color.White.copy(alpha = 0.75f), modifier = Modifier.padding(top = 6.dp))
            }
        }
        // Week grid
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(BgCard).padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("M","T","W","T","F","S","S").forEachIndexed { i, label ->
                val date  = LocalDate.now().minusDays((6 - i).toLong()).format(DateTimeFormatter.ISO_DATE)
                val entry = state.history.find { it.date == date }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, fontSize = 11.sp, color = TextMuted)
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (entry != null) if (entry.clean) Green else Red else BgCard2)
                        .border(2.dp, if (date == today) Gold else Color.Transparent, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center) {
                        if (entry != null) Text(if (entry.clean) "✓" else "✗", fontSize = 14.sp, color = Color.White)
                    }
                }
            }
        }
        // Buttons
        if (!todayDone) {
            Button(onClick = onClean, modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Green), shape = RoundedCornerShape(16.dp)) {
                Text("✅ I STAYED CLEAN TODAY", fontWeight = FontWeight.Black, fontSize = 16.sp)
            }
            OutlinedButton(onClick = onSlip, modifier = Modifier.fillMaxWidth().height(50.dp),
                border = BorderStroke(2.dp, Red), shape = RoundedCornerShape(16.dp)) {
                Text("💀 Report a Slip", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Red)
            }
        } else {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(BgCard)
                .border(2.dp, Green, RoundedCornerShape(16.dp)).padding(20.dp),
                contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("✅", fontSize = 30.sp)
                    Text("Today's logged!", fontWeight = FontWeight.ExtraBold, color = Green, modifier = Modifier.padding(top = 4.dp))
                    Text("Come back tomorrow to continue your streak.", fontSize = 13.sp, color = TextMuted, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        // Mini stats
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(Triple("📅", "${state.totalClean}", "Total Clean"),
                   Triple("⚡", "${state.xp}", "Total XP"),
                   Triple("💀", "${state.slips}", "Slips")).forEach { (icon, v, label) ->
                Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(BgCard).padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(icon, fontSize = 20.sp)
                    Text(v, fontWeight = FontWeight.Black, fontSize = 20.sp, color = Color.White)
                    Text(label, fontSize = 11.sp, color = TextMuted)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ── Badges Tab ────────────────────────────────────────────────
@Composable
fun BadgesTab(state: AppState) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Achievements", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Color.White)
        BADGES.forEach { b ->
            val earned = b.id in state.earnedBadges
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(BgCard)
                .border(2.dp, if (earned) Gold else BgCard2, RoundedCornerShape(16.dp)).padding(16.dp)
                .alpha(if (earned) 1f else 0.5f),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(b.icon, fontSize = 36.sp)
                Column(Modifier.weight(1f)) {
                    Text(b.label, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = Color.White)
                    Text(b.desc, fontSize = 12.sp, color = TextMuted)
                    if (!earned) {
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { (state.streak.toFloat() / b.req).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(4.dp)),
                            color = Green, trackColor = BgCard2)
                    }
                }
                if (earned) Text("⭐", fontSize = 18.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Leagues", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Color.White)
        LEAGUES.forEach { l ->
            val current  = state.currentLeague().name == l.name
            val unlocked = state.xp >= l.min
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(BgCard)
                .border(2.dp, if (current) Gold else BgCard2, RoundedCornerShape(14.dp))
                .padding(12.dp, 14.dp).alpha(if (unlocked) 1f else 0.4f),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(l.icon, fontSize = 26.sp)
                Column(Modifier.weight(1f)) {
                    Text(l.name, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("${l.min} XP required", fontSize = 12.sp, color = TextMuted)
                }
                if (current) Text("CURRENT", fontSize = 11.sp, color = Gold, fontWeight = FontWeight.ExtraBold)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ── Stats Tab ─────────────────────────────────────────────────
@Composable
fun StatsTab(state: AppState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Your Stats", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Color.White)
        listOf(
            Triple("🔥", "Current Streak",   "${state.streak} days"  to Orange),
            Triple("⚡", "Total XP",          "${state.xp} XP"        to Gold),
            Triple("✅", "Total Clean Days",  "${state.totalClean}"   to Green),
            Triple("💀", "Total Slips",       "${state.slips}"        to Red),
            Triple("🏆", "Current League",    "${state.currentLeague().icon} ${state.currentLeague().name}" to GreenLight),
            Triple("🏅", "Badges Earned",     "${state.earnedBadges.size} / ${BADGES.size}" to Purple),
        ).forEach { (icon, label, vc) ->
            val (v, c) = vc
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(BgCard).padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(icon, fontSize = 20.sp)
                    Text(label, color = TextSub, fontSize = 14.sp)
                }
                Text(v, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = c)
            }
        }
        // 30-day heatmap
        Text("Last 30 Days", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color.White, modifier = Modifier.padding(top = 4.dp))
        val cells = (29 downTo 0).map { o ->
            val d = LocalDate.now().minusDays(o.toLong()).format(DateTimeFormatter.ISO_DATE)
            state.history.find { it.date == d }
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(BgCard).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            cells.chunked(10).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { e ->
                        Box(Modifier.size(22.dp).clip(RoundedCornerShape(6.dp))
                            .background(if (e != null) if (e.clean) Green else Red else BgCard2))
                    }
                }
            }
        }
        OutlinedButton(onClick = { state.reset() },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            border = BorderStroke(1.dp, BgCard2), shape = RoundedCornerShape(10.dp)) {
            Text("🔄 Reset All Data", color = TextMuted, fontSize = 13.sp)
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ── Slip Log Tab ──────────────────────────────────────────────
@Composable
fun SlipLogTab(state: AppState) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Slip Log", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Color.White)
        if (state.slipLog.isEmpty()) {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(BgCard).padding(32.dp),
                contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎉", fontSize = 40.sp)
                    Text("No slips recorded!", fontWeight = FontWeight.Bold, color = Green, modifier = Modifier.padding(top = 8.dp))
                    Text("Keep it up!", color = TextMuted, fontSize = 13.sp)
                }
            }
        } else {
            state.slipLog.reversed().forEach { slip ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(BgCard)
                    .border(2.dp, Red.copy(alpha = 0.4f), RoundedCornerShape(14.dp)).padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    Text("💀", fontSize = 24.sp)
                    Column(Modifier.weight(1f)) {
                        Text("${slip.date}  ${slip.time}", fontSize = 12.sp, color = TextMuted)
                        Text(
                            if (slip.note.isNotBlank()) slip.note else "No note added",
                            color = if (slip.note.isNotBlank()) Color.White else TextMuted,
                            fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}