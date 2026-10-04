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
        Text("Tomato 1.2", color = ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(
            if (snapshot.running) "Winding back to zero" else "Swipe the band · 5 to 55",
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
        val cx = size.width / 2f
        val seam = size.height * 0.46f
        val rx = size.minDimension * 0.42f
        val ry = size.minDimension * 0.36f
        drawOval(Color(0x22000000), Offset(cx - rx * 0.7f, seam + ry * 1.02f), Size(rx * 1.4f, ry * 0.22f))
        val apple = Path().apply {
            moveTo(cx - rx, seam)
            cubicTo(cx - rx * 1.02f, seam + ry * 0.72f, cx - rx * 0.62f, seam + ry * 1.08f, cx, seam + ry * 1.08f)
            cubicTo(cx + rx * 0.62f, seam + ry * 1.08f, cx + rx * 1.02f, seam + ry * 0.72f, cx + rx, seam)
            cubicTo(cx + rx * 0.98f, seam - ry * 0.78f, cx + rx * 0.42f, seam - ry * 0.9f, cx, seam - ry * 0.86f)
            cubicTo(cx - rx * 0.42f, seam - ry * 0.9f, cx - rx * 0.98f, seam - ry * 0.78f, cx - rx, seam)
            close()
        }
        drawPath(
            apple,
            brush = Brush.radialGradient(
                listOf(Color(0xFFFF5C54), Color(0xFFE53935), Color(0xFFC62828), Color(0xFF9B1B16)),
                center = Offset(cx - rx * 0.2f, seam - ry * 0.2f),
                radius = rx * 1.5f,
            ),
        )
        drawOval(
            brush = Brush.radialGradient(listOf(Color(0xAAFFFFFF), Color(0x00FFFFFF)), center = Offset(cx - rx * 0.28f, seam - ry * 0.48f), radius = rx * 0.28f),
            topLeft = Offset(cx - rx * 0.48f, seam - ry * 0.68f),
            size = Size(rx * 0.42f, ry * 0.28f),
        )
        drawLine(Color(0x66FFFFFF), Offset(cx - rx * 0.72f, seam), Offset(cx + rx * 0.72f, seam), strokeWidth = 2.5f)
        val pointer = Path().apply {
            moveTo(cx, seam + 7f)
            lineTo(cx - 10f, seam - 14f)
            lineTo(cx + 10f, seam - 14f)
            close()
        }
        drawPath(pointer, if (running) Color.White.copy(alpha = 0.55f + 0.45f * pulse) else Color.White)
        drawIntoCanvas { canvas ->
            val paint = Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.WHITE
                textAlign = Paint.Align.CENTER
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
            }
            (0..55 step 5).forEach { mark ->
                val delta = ((mark - shown) % 60f + 60f) % 60f
                val signed = if (delta > 30f) delta - 60f else delta
                val angle = Math.toRadians((-signed * 6f).toDouble())
                val front = cos(angle).toFloat()
                if (front < 0.35f) return@forEach
                val x = cx + (sin(angle) * rx * 0.55f).toFloat()
                val near = kotlin.math.abs(signed) < 2.2f
                drawLine(
                    Color.White.copy(alpha = if (near && running) pulse else 0.7f + 0.3f * front),
                    Offset(x, seam + 10f),
                    Offset(x, seam + 22f),
                    strokeWidth = if (near) 3.5f else 2f,
                    cap = StrokeCap.Round,
                )
                paint.textSize = if (near) 30f else 24f
                paint.alpha = (255 * front).toInt().coerceIn(160, 255)
                canvas.nativeCanvas.drawText(mark.toString(), x, seam + 48f, paint)
            }
        }
        drawOval(Color(0xFF241816), Offset(cx - 7f, seam - ry * 0.9f), Size(14f, 7f))
        val stem = Path().apply {
            moveTo(cx - 3.5f, seam - ry * 0.84f)
            quadraticTo(cx + 1f, seam - ry * 1.02f, cx + 5f, seam - ry * 1.05f)
            quadraticTo(cx + 2f, seam - ry * 0.96f, cx + 3f, seam - ry * 0.82f)
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
