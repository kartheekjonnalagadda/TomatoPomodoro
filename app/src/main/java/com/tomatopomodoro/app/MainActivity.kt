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
import androidx.compose.ui.input.pointer.pointerInput
import android.graphics.Paint
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
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
    val bg = if (snapshot.running) Color(0xFF2A0C0A) else Color(0xFFF6F6F6)
    val ink = if (snapshot.running) Color(0xFFFFF6F0) else Color(0xFF2A120E)
    Column(
        modifier = Modifier.fillMaxSize().background(bg).padding(horizontal = 22.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Tomato", color = ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(
            if (snapshot.running) "Dial winding back to zero" else "Twist the apple · 5 to 55 minutes",
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
    var lastSlot by remember { mutableIntStateOf(minutes) }
    val shown = if (running && totalMillis > 0L) {
        (remainingMillis / 60_000f).coerceIn(0f, 55f)
    } else minutes.toFloat()
    val pulse by rememberInfiniteTransition(label = "tick").animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulse",
    )
    Canvas(
        modifier = Modifier
            .size(300.dp)
            .pointerInput(running, minutes) {
                if (running) return@pointerInput
                detectHorizontalDragGestures { _, drag ->
                    val selected = minutes.coerceIn(5, 55)
                    val next = (selected - (drag / 16f).toInt() * 5).coerceIn(5, 55)
                    val snapped = PomodoroEngine.steps.minBy { kotlin.math.abs(it - next) }
                    if (snapped != lastSlot) {
                        lastSlot = snapped
                        onSelect(snapped)
                    }
                }
            }
            .pointerInput(running) {
                if (running) return@pointerInput
                detectTapGestures { pos ->
                    val selected = minutesFor(pos.x, pos.y, size.width.toFloat(), size.height.toFloat(), minutes)
                    lastSlot = selected
                    onSelect(selected)
                }
            },
    ) {
        val c = Offset(size.width / 2f, size.height * 0.54f)
        val rx = size.minDimension * 0.44f
        val ry = size.minDimension * 0.34f
        val seam = c.y - ry * 0.02f

        drawOval(
            Color(0x28000000),
            Offset(c.x - rx * 0.72f, seam + ry * 1.05f),
            Size(rx * 1.44f, ry * 0.28f),
        )
        drawOval(
            brush = Brush.radialGradient(
                listOf(Color(0xFFEF3B34), Color(0xFFD42722), Color(0xFFB01A16)),
                center = Offset(c.x - rx * 0.12f, seam + ry * 0.35f),
                radius = rx * 1.25f,
            ),
            topLeft = Offset(c.x - rx, seam - ry * 0.02f),
            size = Size(rx * 2f, ry * 1.18f),
        )

        drawIntoCanvas { canvas ->
            val paint = Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.WHITE
                textAlign = Paint.Align.CENTER
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
                setShadowLayer(4f, 0f, 1f, android.graphics.Color.argb(90, 80, 0, 0))
            }
            (0..55 step 5).forEach { mark ->
                val delta = ((mark - shown) % 60f + 60f) % 60f
                val signed = if (delta > 30f) delta - 60f else delta
                val angle = Math.toRadians((-signed * 6f).toDouble())
                val front = cos(angle).toFloat()
                if (front < 0.25f) return@forEach
                val x = c.x + (sin(angle) * rx * 0.62f).toFloat()
                val y = seam + ry * 0.22f
                val near = kotlin.math.abs(signed) < 2.2f
                drawLine(
                    color = Color.White.copy(alpha = if (near && running) pulse else 0.55f + 0.45f * front),
                    start = Offset(x, seam + ry * 0.06f),
                    end = Offset(x, seam + ry * 0.14f),
                    strokeWidth = if (near) 3.4f else 2f,
                    cap = StrokeCap.Round,
                )
                paint.textSize = if (near) 32f else 26f
                paint.alpha = (255 * front).toInt().coerceIn(140, 255)
                canvas.nativeCanvas.drawText(mark.toString(), x, y + 28f, paint)
            }
        }

        drawOval(
            brush = Brush.radialGradient(
                listOf(Color(0xFFFF5A52), Color(0xFFE53935), Color(0xFFC62828)),
                center = Offset(c.x - rx * 0.22f, seam - ry * 0.42f),
                radius = rx * 1.15f,
            ),
            topLeft = Offset(c.x - rx * 0.96f, seam - ry * 0.92f),
            size = Size(rx * 1.92f, ry * 0.98f),
        )
        drawOval(
            brush = Brush.radialGradient(
                listOf(Color(0xCCFFFFFF), Color(0x00FFFFFF)),
                center = Offset(c.x - rx * 0.28f, seam - ry * 0.55f),
                radius = rx * 0.32f,
            ),
            topLeft = Offset(c.x - rx * 0.52f, seam - ry * 0.78f),
            size = Size(rx * 0.5f, ry * 0.32f),
        )
        drawLine(
            Color(0x55FFFFFF),
            Offset(c.x - rx * 0.78f, seam),
            Offset(c.x + rx * 0.78f, seam),
            strokeWidth = 2f,
        )
        val pointer = Path().apply {
            moveTo(c.x, seam + 8f)
            lineTo(c.x - 9f, seam - 12f)
            lineTo(c.x + 9f, seam - 12f)
            close()
        }
        drawPath(pointer, if (running) Color.White.copy(alpha = 0.6f + 0.4f * pulse) else Color.White)
        drawOval(Color(0xFF2C2C2C), Offset(c.x - 8f, seam - ry * 0.98f), Size(16f, 8f))
        val stem = Path().apply {
            moveTo(c.x - 3f, seam - ry * 0.92f)
            quadraticTo(c.x + 1f, seam - ry * 1.18f, c.x + 6f, seam - ry * 1.22f)
            quadraticTo(c.x + 3f, seam - ry * 1.08f, c.x + 4f, seam - ry * 0.9f)
            close()
        }
        drawPath(stem, Color(0xFF1A1A1A))
    }
}

private fun minutesFor(x: Float, y: Float, w: Float, h: Float, current: Int): Int {
    val dx = x - w / 2f
    val step = (dx / (w * 0.08f)).toInt()
    val next = (current - step * 5).coerceIn(5, 55)
    return PomodoroEngine.steps.minBy { kotlin.math.abs(it - next) }
}
