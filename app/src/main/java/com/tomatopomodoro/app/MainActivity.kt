package com.tomatopomodoro.app

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    private val ringtonePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        PomodoroEngine.setRingtone(this, uri?.toString())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        PomodoroEngine.load(this)
        if (Build.VERSION.SDK_INT >= 33) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        setContent {
            MaterialTheme {
                TomatoApp(onPickSound = { pickSound() }, onExactAlarms = { openExactAlarmSettings() })
            }
        }
    }

    private fun pickSound() {
        val existing = PomodoroEngine.state.value.ringtoneUri?.let { Uri.parse(it) }
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Tomato alarm sound")
        }
        ringtonePicker.launch(intent)
    }

    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:$packageName")
            })
        }
    }
}

@Composable
private fun TomatoApp(onPickSound: () -> Unit, onExactAlarms: () -> Unit) {
    val context = LocalContext.current
    val snapshot by PomodoroEngine.state.collectAsStateWithLifecycle()
    var remaining by remember { mutableLongStateOf(PomodoroEngine.currentRemaining()) }
    LaunchedEffect(snapshot.running, snapshot.endWallMs, snapshot.remainingMillis) {
        while (true) {
            remaining = PomodoroEngine.currentRemaining()
            if (snapshot.running && remaining <= 0L) {
                PomodoroEngine.complete(context)
                break
            }
            delay(if (snapshot.running) 200 else 600)
            if (!snapshot.running) break
        }
    }
    val bg = if (snapshot.running) Color(0xFF2A0C0A) else Color(0xFFFFF6F0)
    val ink = if (snapshot.running) Color(0xFFFFF6F0) else Color(0xFF2A120E)
    Column(
        modifier = Modifier.fillMaxSize().background(bg).padding(horizontal = 22.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Tomato", color = ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(
            if (snapshot.running) "Running in the background" else "Drag the ring · 5 to 55 minutes",
            color = ink.copy(alpha = 0.7f),
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(12.dp))
        TomatoDial(
            minutes = snapshot.durationMinutes,
            remainingMillis = remaining,
            totalMillis = snapshot.durationMinutes * 60_000L,
            running = snapshot.running,
            onSelect = { PomodoroEngine.selectMinutes(context, it, feedback = true) },
        )
        Spacer(Modifier.height(8.dp))
        Text(TimerService.format(remaining), color = ink, fontSize = 42.sp, fontWeight = FontWeight.Bold)
        Text("${snapshot.durationMinutes} min session", color = ink.copy(alpha = 0.7f))
        Spacer(Modifier.height(18.dp))
        Remote(
            running = snapshot.running,
            onMinus = { PomodoroEngine.nudge(context, -5) },
            onToggle = { PomodoroEngine.toggle(context) },
            onPlus = { PomodoroEngine.nudge(context, 5) },
            onReset = { PomodoroEngine.reset(context) },
        )
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onPickSound) {
            Text("Alarm sound", color = Color(0xFFE23B2F), fontWeight = FontWeight.Bold)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = context.getSystemService(AlarmManager::class.java)
            if (am != null && !am.canScheduleExactAlarms()) {
                TextButton(onClick = onExactAlarms) {
                    Text("Allow system alarms", color = Color(0xFFE23B2F))
                }
            }
        }
    }
}

@Composable
private fun Remote(
    running: Boolean,
    onMinus: () -> Unit,
    onToggle: () -> Unit,
    onPlus: () -> Unit,
    onReset: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color(0xFF3A1612),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Remote", color = Color(0xFFFFD7D2), fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                RemoteKey(onMinus) { Text("−5", color = Color.White, fontWeight = FontWeight.Bold) }
                RemoteKey(onToggle, accent = true) {
                    Text(if (running) "Pause" else "Start", color = Color.White, fontWeight = FontWeight.Bold)
                }
                RemoteKey(onPlus) { Text("+5", color = Color.White, fontWeight = FontWeight.Bold) }
                RemoteKey(onReset) { Text("Reset", color = Color.White, fontSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun RemoteKey(onClick: () -> Unit, accent: Boolean = false, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (accent) Color(0xFFE23B2F) else Color(0xFF5A2822),
        modifier = Modifier.size(if (accent) 72.dp else 56.dp),
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun TomatoDial(
    minutes: Int,
    remainingMillis: Long,
    totalMillis: Long,
    running: Boolean,
    onSelect: (Int) -> Unit,
) {
    val context = LocalContext.current
    var lastSlot by remember { mutableIntStateOf(minutes) }
    val progress = if (totalMillis <= 0L) 0f else (remainingMillis.toFloat() / totalMillis).coerceIn(0f, 1f)
    val pulse by rememberInfiniteTransition(label = "tick").animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulse",
    )
    Canvas(
        modifier = Modifier
            .size(320.dp)
            .pointerInput(running) {
                if (running) return@pointerInput
                detectTapGestures { pos ->
                    val selected = minutesFor(pos.x, pos.y, size.width.toFloat(), size.height.toFloat())
                    lastSlot = selected
                    onSelect(selected)
                }
            }
            .pointerInput(running) {
                if (running) return@pointerInput
                detectDragGestures { change, _ ->
                    val selected = minutesFor(change.position.x, change.position.y, size.width.toFloat(), size.height.toFloat())
                    if (selected != lastSlot) {
                        lastSlot = selected
                        onSelect(selected)
                    }
                }
            },
    ) {
        val c = Offset(size.width / 2f, size.height / 2f + 8f)
        val tomatoR = size.minDimension * 0.34f
        drawCircle(Color(0x33000000), tomatoR * 0.92f, c + Offset(0f, tomatoR * 0.28f))
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0xFFFF6B5A), Color(0xFFE23B2F), Color(0xFFB71C1C)),
                center = c + Offset(-tomatoR * 0.25f, -tomatoR * 0.3f),
                radius = tomatoR * 1.4f,
            ),
            radius = tomatoR,
            center = c,
        )
        drawCircle(Color(0x55FFFFFF), tomatoR * 0.22f, c + Offset(-tomatoR * 0.28f, -tomatoR * 0.32f))
        val lit = (progress * 6f).toInt().coerceIn(0, 6)
        repeat(6) { i ->
            rotate(i * 30f - 10f, c) {
                drawLine(
                    color = if (i < lit || !running) Color(0x33FFFFFF) else Color(0x22FFFFFF),
                    start = c + Offset(0f, -tomatoR * 0.15f),
                    end = c + Offset(0f, -tomatoR * 0.92f),
                    strokeWidth = 3f,
                )
            }
        }
        val stem = Path().apply {
            moveTo(c.x, c.y - tomatoR * 0.72f)
            lineTo(c.x - 8f, c.y - tomatoR * 1.18f)
            lineTo(c.x + 8f, c.y - tomatoR * 1.18f)
            close()
        }
        drawPath(stem, Color(0xFF5D4037))
        val leaf = Path().apply {
            moveTo(c.x, c.y - tomatoR * 0.95f)
            quadraticTo(c.x - tomatoR * 0.7f, c.y - tomatoR * 1.15f, c.x - tomatoR * 0.15f, c.y - tomatoR * 0.7f)
            quadraticTo(c.x + tomatoR * 0.7f, c.y - tomatoR * 1.2f, c.x, c.y - tomatoR * 0.95f)
        }
        drawPath(leaf, Color(0xFF2E7D32))
        val ring = tomatoR * 1.38f
        drawArc(
            color = Color(0x33E23B2F),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(c.x - ring, c.y - ring),
            size = Size(ring * 2, ring * 2),
            style = Stroke(8f, cap = StrokeCap.Round),
        )
        drawArc(
            color = if (running) Color(0xFFFF5252) else Color(0xFFE23B2F),
            startAngle = -90f,
            sweepAngle = 360f * progress,
            useCenter = false,
            topLeft = Offset(c.x - ring, c.y - ring),
            size = Size(ring * 2, ring * 2),
            style = Stroke(10f, cap = StrokeCap.Round),
        )
        PomodoroEngine.steps.forEachIndexed { index, step ->
            val angle = Math.toRadians((-90.0 + index * (360.0 / 11.0)))
            val tickLen = if (step == minutes) 18f else 10f
            val outer = Offset(c.x + (ring * cos(angle)).toFloat(), c.y + (ring * sin(angle)).toFloat())
            val inner = Offset(
                c.x + ((ring - tickLen) * cos(angle)).toFloat(),
                c.y + ((ring - tickLen) * sin(angle)).toFloat(),
            )
            val active = step == minutes
            val elapsedIndex = ((1f - progress) * (PomodoroEngine.steps.size - 1)).toInt()
            val sweeping = running && index == elapsedIndex
            drawLine(
                color = when {
                    sweeping -> Color(0xFFFFEB3B).copy(alpha = pulse)
                    active -> Color(0xFF2A120E)
                    running && index < elapsedIndex -> Color(0x55FFFFFF)
                    else -> Color(0xFF8D4038)
                },
                start = inner,
                end = outer,
                strokeWidth = if (active || sweeping) 7f else 3f,
                cap = StrokeCap.Round,
            )
            if (sweeping) {
                drawCircle(Color(0xFFFFEB3B).copy(alpha = pulse), 7f + 4f * pulse, outer)
            }
        }
    }
}

private fun minutesFor(x: Float, y: Float, w: Float, h: Float): Int {
    val cx = w / 2f
    val cy = h / 2f + 8f
    var deg = Math.toDegrees(atan2((y - cy).toDouble(), (x - cx).toDouble())) + 90.0
    if (deg < 0) deg += 360.0
    val index = ((deg / (360.0 / 11.0)) + 0.5).toInt() % 11
    return PomodoroEngine.steps[index]
}
