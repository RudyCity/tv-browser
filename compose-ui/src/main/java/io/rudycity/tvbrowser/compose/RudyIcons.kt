package io.rudycity.tvbrowser.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Handcrafted minimalist line iconography for Rudy Browser.
 * Enforces uniform 1.8-2.0dp stroke weight, rounded caps and joins,
 * geometric precision, and zero stock Material clunkiness.
 */
internal object RudyIcons {

    @Composable
    fun RudyMark(
        modifier: Modifier = Modifier,
        size: Dp = 28.dp,
        showBackground: Boolean = true
    ) {
        Canvas(modifier = modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val s = w / 512f

            if (showBackground) {
                // Background Squircle: Obsidian to Deep Space Navy
                val squircleCorner = CornerRadius(116f * s, 116f * s)
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF0C1322), Color(0xFF070A12), Color(0xFF020408)),
                        start = Offset(0f, 0f),
                        end = Offset(w, h)
                    ),
                    topLeft = Offset(24f * s, 24f * s),
                    size = Size(464f * s, 464f * s),
                    cornerRadius = squircleCorner
                )

                // Hairline Rim Border: Top-lit Cyan Glow
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF38BDF8).copy(alpha = 0.85f),
                            Color(0xFF2563EB).copy(alpha = 0.35f),
                            Color(0xFF0F172A).copy(alpha = 0.10f)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(w, h)
                    ),
                    topLeft = Offset(24f * s, 24f * s),
                    size = Size(464f * s, 464f * s),
                    cornerRadius = squircleCorner,
                    style = Stroke(width = (1.8f * s).coerceAtLeast(1.dp.toPx()))
                )
            }

            // Ribbon Gradient: Electric Ice Cyan to Vibrant Cobalt
            val ribbonBrush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFFF0F9FF),
                    Color(0xFFBAE6FD),
                    Color(0xFF38BDF8),
                    Color(0xFF2563EB),
                    Color(0xFF1D4ED8)
                ),
                start = Offset(136f * s, 124f * s),
                end = Offset(386f * s, 388f * s)
            )

            // Leg Gradient: Speed Cobalt to Deep Navy
            val legBrush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF60A5FA),
                    Color(0xFF2563EB),
                    Color(0xFF1D4ED8),
                    Color(0xFF0F172A)
                ),
                start = Offset(196f * s, 244f * s),
                end = Offset(374f * s, 388f * s)
            )

            // Part A: Vertical Stem (Anchor Pillar)
            val stemCorner = CornerRadius(30f * s, 30f * s)
            drawRoundRect(
                brush = ribbonBrush,
                topLeft = Offset(136f * s, 124f * s),
                size = Size(60f * s, 264f * s),
                cornerRadius = stemCorner
            )

            // Part B: Kinetic Thrust Leg (SVG path underneath loop)
            val legPath = Path().apply {
                moveTo(196f * s, 244f * s)
                lineTo(264f * s, 244f * s)
                cubicTo(288f * s, 244f * s, 310f * s, 258f * s, 322f * s, 280f * s)
                lineTo(374f * s, 366f * s)
                cubicTo(382f * s, 378f * s, 372f * s, 388f * s, 358f * s, 388f * s)
                lineTo(300f * s, 388f * s)
                cubicTo(282f * s, 388f * s, 266f * s, 376f * s, 256f * s, 360f * s)
                lineTo(208f * s, 280f * s)
                cubicTo(202f * s, 270f * s, 196f * s, 258f * s, 196f * s, 244f * s)
                close()
            }
            drawPath(path = legPath, brush = legBrush)

            // Part C: Orbital Compass Loop (Browser Aperture Ribbon)
            val loopPath = Path().apply {
                moveTo(166f * s, 124f * s)
                lineTo(272f * s, 124f * s)
                cubicTo(338f * s, 124f * s, 386f * s, 170f * s, 386f * s, 228f * s)
                cubicTo(386f * s, 286f * s, 338f * s, 332f * s, 272f * s, 332f * s)
                lineTo(196f * s, 332f * s)
                cubicTo(179f * s, 332f * s, 166f * s, 319f * s, 166f * s, 302f * s)
                lineTo(166f * s, 288f * s)
                cubicTo(166f * s, 271f * s, 179f * s, 258f * s, 196f * s, 258f * s)
                lineTo(264f * s, 258f * s)
                cubicTo(292f * s, 258f * s, 316f * s, 242f * s, 316f * s, 228f * s)
                cubicTo(316f * s, 214f * s, 292f * s, 198f * s, 264f * s, 198f * s)
                lineTo(166f * s, 198f * s)
                close()
            }
            drawPath(path = loopPath, brush = ribbonBrush)

            // Part D: Precision Specular Highlight Ridge on Top Edge
            val specularPath = Path().apply {
                moveTo(166f * s, 126f * s)
                lineTo(272f * s, 126f * s)
                cubicTo(334f * s, 126f * s, 382f * s, 170f * s, 384f * s, 226f * s)
            }
            val specularBrush = Brush.horizontalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.95f),
                    Color(0xFFBAE6FD).copy(alpha = 0.75f),
                    Color(0xFF38BDF8).copy(alpha = 0f)
                ),
                startX = 166f * s,
                endX = 384f * s
            )
            drawPath(
                path = specularPath,
                brush = specularBrush,
                style = Stroke(width = (3f * s).coerceAtLeast(1.5f.dp.toPx()), cap = StrokeCap.Round)
            )
        }
    }

    @Composable
    fun Back(
        modifier: Modifier = Modifier,
        tint: Color = LocalContentColor.current,
        size: Dp = 20.dp
    ) {
        Canvas(modifier = modifier.size(size)) {
            val stroke = Stroke(width = 1.9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val w = this.size.width
            val h = this.size.height
            val path = Path().apply {
                moveTo(w * 0.78f, h * 0.50f)
                lineTo(w * 0.24f, h * 0.50f)
                moveTo(w * 0.50f, h * 0.24f)
                lineTo(w * 0.24f, h * 0.50f)
                lineTo(w * 0.50f, h * 0.76f)
            }
            drawPath(path, color = tint, style = stroke)
        }
    }

    @Composable
    fun Forward(
        modifier: Modifier = Modifier,
        tint: Color = LocalContentColor.current,
        size: Dp = 20.dp
    ) {
        Canvas(modifier = modifier.size(size)) {
            val stroke = Stroke(width = 1.9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val w = this.size.width
            val h = this.size.height
            val path = Path().apply {
                moveTo(w * 0.22f, h * 0.50f)
                lineTo(w * 0.76f, h * 0.50f)
                moveTo(w * 0.50f, h * 0.24f)
                lineTo(w * 0.76f, h * 0.50f)
                lineTo(w * 0.50f, h * 0.76f)
            }
            drawPath(path, color = tint, style = stroke)
        }
    }

    @Composable
    fun Search(
        modifier: Modifier = Modifier,
        tint: Color = LocalContentColor.current,
        size: Dp = 18.dp
    ) {
        Canvas(modifier = modifier.size(size)) {
            val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val w = this.size.width
            val h = this.size.height
            val center = Offset(w * 0.42f, h * 0.42f)
            val radius = w * 0.27f
            drawCircle(color = tint, radius = radius, center = center, style = stroke)
            drawLine(
                color = tint,
                start = Offset(w * 0.62f, h * 0.62f),
                end = Offset(w * 0.86f, h * 0.86f),
                strokeWidth = 2.0.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }

    @Composable
    fun Lock(
        modifier: Modifier = Modifier,
        tint: Color = LocalContentColor.current,
        size: Dp = 17.dp
    ) {
        Canvas(modifier = modifier.size(size)) {
            val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val w = this.size.width
            val h = this.size.height
            // Shackle arc
            val shackle = Path().apply {
                moveTo(w * 0.32f, h * 0.44f)
                lineTo(w * 0.32f, h * 0.30f)
                arcTo(
                    rect = Rect(w * 0.32f, h * 0.12f, w * 0.68f, h * 0.48f),
                    startAngleDegrees = 180f,
                    sweepAngleDegrees = 180f,
                    forceMoveTo = false
                )
                lineTo(w * 0.68f, h * 0.44f)
            }
            drawPath(shackle, color = tint, style = stroke)
            // Body
            drawRoundRect(
                color = tint,
                topLeft = Offset(w * 0.20f, h * 0.44f),
                size = Size(w * 0.60f, h * 0.44f),
                cornerRadius = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx()),
                style = stroke
            )
            // Center pin
            drawCircle(
                color = tint,
                radius = 1.2.dp.toPx(),
                center = Offset(w * 0.50f, h * 0.64f)
            )
        }
    }

    @Composable
    fun More(
        modifier: Modifier = Modifier,
        tint: Color = LocalContentColor.current,
        size: Dp = 20.dp
    ) {
        Canvas(modifier = modifier.size(size)) {
            val dotRadius = 1.75.dp.toPx()
            val cy = this.size.height * 0.50f
            val w = this.size.width
            drawCircle(color = tint, radius = dotRadius, center = Offset(w * 0.22f, cy))
            drawCircle(color = tint, radius = dotRadius, center = Offset(w * 0.50f, cy))
            drawCircle(color = tint, radius = dotRadius, center = Offset(w * 0.78f, cy))
        }
    }

    @Composable
    fun Clear(
        modifier: Modifier = Modifier,
        tint: Color = LocalContentColor.current,
        size: Dp = 16.dp
    ) {
        Canvas(modifier = modifier.size(size)) {
            val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
            val w = this.size.width
            val h = this.size.height
            drawLine(
                color = tint,
                start = Offset(w * 0.26f, h * 0.26f),
                end = Offset(w * 0.74f, h * 0.74f),
                strokeWidth = stroke.width,
                cap = stroke.cap
            )
            drawLine(
                color = tint,
                start = Offset(w * 0.74f, h * 0.26f),
                end = Offset(w * 0.26f, h * 0.74f),
                strokeWidth = stroke.width,
                cap = stroke.cap
            )
        }
    }

    @Composable
    fun Reload(
        modifier: Modifier = Modifier,
        tint: Color = LocalContentColor.current,
        size: Dp = 18.dp
    ) {
        Canvas(modifier = modifier.size(size)) {
            val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val w = this.size.width
            val h = this.size.height
            val arcRect = Rect(w * 0.18f, h * 0.18f, w * 0.82f, h * 0.82f)
            val arcPath = Path().apply {
                arcTo(arcRect, startAngleDegrees = -60f, sweepAngleDegrees = 290f, forceMoveTo = false)
            }
            drawPath(arcPath, color = tint, style = stroke)
            // Arrow tip
            val arrow = Path().apply {
                moveTo(w * 0.72f, h * 0.08f)
                lineTo(w * 0.88f, h * 0.22f)
                lineTo(w * 0.68f, h * 0.32f)
            }
            drawPath(arrow, color = tint, style = stroke)
        }
    }

    @Composable
    fun Star(
        filled: Boolean = false,
        modifier: Modifier = Modifier,
        tint: Color = LocalContentColor.current,
        size: Dp = 18.dp
    ) {
        Canvas(modifier = modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val cx = w * 0.50f
            val cy = h * 0.52f
            val rOuter = w * 0.40f
            val rInner = w * 0.18f

            val path = Path()
            for (i in 0 until 10) {
                val r = if (i % 2 == 0) rOuter else rInner
                val angle = Math.toRadians((i * 36 - 90).toDouble())
                val x = cx + (r * cos(angle)).toFloat()
                val y = cy + (r * sin(angle)).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()

            if (filled) {
                drawPath(path, color = tint)
            } else {
                drawPath(
                    path,
                    color = tint,
                    style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
    }

    @Composable
    fun Home(
        modifier: Modifier = Modifier,
        tint: Color = LocalContentColor.current,
        size: Dp = 18.dp
    ) {
        Canvas(modifier = modifier.size(size)) {
            val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val w = this.size.width
            val h = this.size.height
            // Roof
            val roof = Path().apply {
                moveTo(w * 0.18f, h * 0.48f)
                lineTo(w * 0.50f, h * 0.20f)
                lineTo(w * 0.82f, h * 0.48f)
            }
            drawPath(roof, color = tint, style = stroke)
            // House outline and door
            val body = Path().apply {
                moveTo(w * 0.26f, h * 0.44f)
                lineTo(w * 0.26f, h * 0.82f)
                lineTo(w * 0.42f, h * 0.82f)
                lineTo(w * 0.42f, h * 0.60f)
                lineTo(w * 0.58f, h * 0.60f)
                lineTo(w * 0.58f, h * 0.82f)
                lineTo(w * 0.74f, h * 0.82f)
                lineTo(w * 0.74f, h * 0.44f)
            }
            drawPath(body, color = tint, style = stroke)
        }
    }

    @Composable
    fun Bookmark(
        modifier: Modifier = Modifier,
        tint: Color = LocalContentColor.current,
        size: Dp = 18.dp
    ) {
        Canvas(modifier = modifier.size(size)) {
            val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val w = this.size.width
            val h = this.size.height
            val ribbon = Path().apply {
                moveTo(w * 0.26f, h * 0.18f)
                lineTo(w * 0.74f, h * 0.18f)
                lineTo(w * 0.74f, h * 0.82f)
                lineTo(w * 0.50f, h * 0.62f)
                lineTo(w * 0.26f, h * 0.82f)
                close()
            }
            drawPath(ribbon, color = tint, style = stroke)
        }
    }

    @Composable
    fun History(
        modifier: Modifier = Modifier,
        tint: Color = LocalContentColor.current,
        size: Dp = 18.dp
    ) {
        Canvas(modifier = modifier.size(size)) {
            val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val w = this.size.width
            val h = this.size.height
            val center = Offset(w * 0.50f, h * 0.50f)
            val radius = w * 0.36f
            drawCircle(color = tint, radius = radius, center = center, style = stroke)
            // Clock hands
            drawLine(
                color = tint,
                start = center,
                end = Offset(w * 0.50f, h * 0.28f),
                strokeWidth = stroke.width,
                cap = stroke.cap
            )
            drawLine(
                color = tint,
                start = center,
                end = Offset(w * 0.68f, h * 0.50f),
                strokeWidth = stroke.width,
                cap = stroke.cap
            )
        }
    }

    @Composable
    fun Tabs(
        modifier: Modifier = Modifier,
        tint: Color = LocalContentColor.current,
        size: Dp = 18.dp
    ) {
        Canvas(modifier = modifier.size(size)) {
            val stroke = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val w = this.size.width
            val h = this.size.height
            // Back tab card
            drawRoundRect(
                color = tint,
                topLeft = Offset(w * 0.32f, h * 0.18f),
                size = Size(w * 0.50f, h * 0.52f),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                style = stroke
            )
            // Front tab card
            drawRoundRect(
                color = tint,
                topLeft = Offset(w * 0.18f, h * 0.32f),
                size = Size(w * 0.50f, h * 0.52f),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                style = stroke
            )
        }
    }

    @Composable
    fun Settings(
        modifier: Modifier = Modifier,
        tint: Color = LocalContentColor.current,
        size: Dp = 18.dp
    ) {
        // Modern minimalist sliders icon (cleaner & more elegant than a heavy gear)
        Canvas(modifier = modifier.size(size)) {
            val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
            val w = this.size.width
            val h = this.size.height
            // Slider 1 track & knob
            drawLine(
                color = tint,
                start = Offset(w * 0.18f, h * 0.34f),
                end = Offset(w * 0.82f, h * 0.34f),
                strokeWidth = stroke.width,
                cap = stroke.cap
            )
            drawCircle(
                color = tint,
                radius = 2.2.dp.toPx(),
                center = Offset(w * 0.38f, h * 0.34f)
            )
            // Slider 2 track & knob
            drawLine(
                color = tint,
                start = Offset(w * 0.18f, h * 0.66f),
                end = Offset(w * 0.82f, h * 0.66f),
                strokeWidth = stroke.width,
                cap = stroke.cap
            )
            drawCircle(
                color = tint,
                radius = 2.2.dp.toPx(),
                center = Offset(w * 0.62f, h * 0.66f)
            )
        }
    }

    @Composable
    fun Extension(
        modifier: Modifier = Modifier,
        tint: Color = LocalContentColor.current,
        size: Dp = 18.dp
    ) {
        // Minimalist modular geometric blocks
        Canvas(modifier = modifier.size(size)) {
            val stroke = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val w = this.size.width
            val h = this.size.height
            val block = w * 0.28f
            val cr = CornerRadius(1.8.dp.toPx(), 1.8.dp.toPx())
            // Top-left
            drawRoundRect(color = tint, topLeft = Offset(w * 0.18f, h * 0.18f), size = Size(block, block), cornerRadius = cr, style = stroke)
            // Top-right
            drawRoundRect(color = tint, topLeft = Offset(w * 0.54f, h * 0.18f), size = Size(block, block), cornerRadius = cr, style = stroke)
            // Bottom-left
            drawRoundRect(color = tint, topLeft = Offset(w * 0.18f, h * 0.54f), size = Size(block, block), cornerRadius = cr, style = stroke)
            // Bottom-right with accent dot
            drawRoundRect(color = tint, topLeft = Offset(w * 0.54f, h * 0.54f), size = Size(block, block), cornerRadius = cr, style = stroke)
        }
    }

    @Composable
    fun Plus(
        modifier: Modifier = Modifier,
        tint: Color = LocalContentColor.current,
        size: Dp = 18.dp
    ) {
        Canvas(modifier = modifier.size(size)) {
            val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
            val w = this.size.width
            val h = this.size.height
            drawLine(
                color = tint,
                start = Offset(w * 0.22f, h * 0.50f),
                end = Offset(w * 0.78f, h * 0.50f),
                strokeWidth = stroke.width,
                cap = stroke.cap
            )
            drawLine(
                color = tint,
                start = Offset(w * 0.50f, h * 0.22f),
                end = Offset(w * 0.50f, h * 0.78f),
                strokeWidth = stroke.width,
                cap = stroke.cap
            )
        }
    }

    @Composable
    fun ZoomIn(
        modifier: Modifier = Modifier,
        tint: Color = LocalContentColor.current,
        size: Dp = 18.dp
    ) {
        Canvas(modifier = modifier.size(size)) {
            val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val w = this.size.width
            val h = this.size.height
            val center = Offset(w * 0.42f, h * 0.42f)
            val radius = w * 0.27f
            drawCircle(color = tint, radius = radius, center = center, style = stroke)
            drawLine(
                color = tint,
                start = Offset(w * 0.62f, h * 0.62f),
                end = Offset(w * 0.86f, h * 0.86f),
                strokeWidth = 2.0.dp.toPx(),
                cap = StrokeCap.Round
            )
            // Plus
            drawLine(
                color = tint,
                start = Offset(center.x - radius * 0.5f, center.y),
                end = Offset(center.x + radius * 0.5f, center.y),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = tint,
                start = Offset(center.x, center.y - radius * 0.5f),
                end = Offset(center.x, center.y + radius * 0.5f),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }

    @Composable
    fun ZoomOut(
        modifier: Modifier = Modifier,
        tint: Color = LocalContentColor.current,
        size: Dp = 18.dp
    ) {
        Canvas(modifier = modifier.size(size)) {
            val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val w = this.size.width
            val h = this.size.height
            val center = Offset(w * 0.42f, h * 0.42f)
            val radius = w * 0.27f
            drawCircle(color = tint, radius = radius, center = center, style = stroke)
            drawLine(
                color = tint,
                start = Offset(w * 0.62f, h * 0.62f),
                end = Offset(w * 0.86f, h * 0.86f),
                strokeWidth = 2.0.dp.toPx(),
                cap = StrokeCap.Round
            )
            // Minus
            drawLine(
                color = tint,
                start = Offset(center.x - radius * 0.5f, center.y),
                end = Offset(center.x + radius * 0.5f, center.y),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }

    @Composable
    fun ArrowUp(
        modifier: Modifier = Modifier,
        tint: Color = LocalContentColor.current,
        size: Dp = 18.dp
    ) {
        Canvas(modifier = modifier.size(size)) {
            val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val w = this.size.width
            val h = this.size.height
            val path = Path().apply {
                moveTo(w * 0.50f, h * 0.78f)
                lineTo(w * 0.50f, h * 0.24f)
                moveTo(w * 0.24f, h * 0.50f)
                lineTo(w * 0.50f, h * 0.24f)
                lineTo(w * 0.76f, h * 0.50f)
            }
            drawPath(path, color = tint, style = stroke)
        }
    }

    @Composable
    fun ArrowDown(
        modifier: Modifier = Modifier,
        tint: Color = LocalContentColor.current,
        size: Dp = 18.dp
    ) {
        Canvas(modifier = modifier.size(size)) {
            val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val w = this.size.width
            val h = this.size.height
            val path = Path().apply {
                moveTo(w * 0.50f, h * 0.22f)
                lineTo(w * 0.50f, h * 0.76f)
                moveTo(w * 0.24f, h * 0.50f)
                lineTo(w * 0.50f, h * 0.76f)
                lineTo(w * 0.76f, h * 0.50f)
            }
            drawPath(path, color = tint, style = stroke)
        }
    }

    @Composable
    fun Document(
        modifier: Modifier = Modifier,
        tint: Color = LocalContentColor.current,
        size: Dp = 18.dp
    ) {
        Canvas(modifier = modifier.size(size)) {
            val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val w = this.size.width
            val h = this.size.height
            val path = Path().apply {
                moveTo(w * 0.24f, h * 0.16f)
                lineTo(w * 0.58f, h * 0.16f)
                lineTo(w * 0.76f, h * 0.34f)
                lineTo(w * 0.76f, h * 0.84f)
                lineTo(w * 0.24f, h * 0.84f)
                close()
            }
            drawPath(path, color = tint, style = stroke)
            // Fold line
            val fold = Path().apply {
                moveTo(w * 0.58f, h * 0.16f)
                lineTo(w * 0.58f, h * 0.34f)
                lineTo(w * 0.76f, h * 0.34f)
            }
            drawPath(fold, color = tint, style = stroke)
        }
    }

    @Composable
    fun Copy(
        modifier: Modifier = Modifier,
        tint: Color = LocalContentColor.current,
        size: Dp = 18.dp
    ) {
        Canvas(modifier = modifier.size(size)) {
            val stroke = Stroke(width = 1.7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val w = this.size.width
            val h = this.size.height
            // Back document
            val back = Path().apply {
                moveTo(w * 0.32f, h * 0.16f)
                lineTo(w * 0.78f, h * 0.16f)
                lineTo(w * 0.78f, h * 0.68f)
            }
            drawPath(back, color = tint, style = stroke)
            // Front document
            drawRoundRect(
                color = tint,
                topLeft = Offset(w * 0.18f, h * 0.28f),
                size = Size(w * 0.54f, h * 0.56f),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                style = stroke
            )
        }
    }

    @Composable
    fun Video(
        modifier: Modifier = Modifier,
        tint: Color = LocalContentColor.current,
        size: Dp = 18.dp
    ) {
        Canvas(modifier = modifier.size(size)) {
            val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val w = this.size.width
            val h = this.size.height
            drawRoundRect(
                color = tint,
                topLeft = Offset(w * 0.18f, h * 0.22f),
                size = Size(w * 0.64f, h * 0.56f),
                cornerRadius = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx()),
                style = stroke
            )
            val playPath = Path().apply {
                moveTo(w * 0.42f, h * 0.38f)
                lineTo(w * 0.62f, h * 0.50f)
                lineTo(w * 0.42f, h * 0.62f)
                close()
            }
            drawPath(playPath, color = tint)
        }
    }

    @Composable
    fun Tools(
        modifier: Modifier = Modifier,
        tint: Color = LocalContentColor.current,
        size: Dp = 18.dp
    ) {
        Canvas(modifier = modifier.size(size)) {
            val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val w = this.size.width
            val h = this.size.height
            // Modern terminal prompt / tool symbol >_
            val prompt = Path().apply {
                moveTo(w * 0.22f, h * 0.28f)
                lineTo(w * 0.46f, h * 0.48f)
                lineTo(w * 0.22f, h * 0.68f)
            }
            drawPath(prompt, color = tint, style = stroke)
            drawLine(
                color = tint,
                start = Offset(w * 0.52f, h * 0.68f),
                end = Offset(w * 0.78f, h * 0.68f),
                strokeWidth = stroke.width,
                cap = stroke.cap
            )
        }
    }

    @Composable
    fun Info(
        modifier: Modifier = Modifier,
        tint: Color = LocalContentColor.current,
        size: Dp = 18.dp
    ) {
        Canvas(modifier = modifier.size(size)) {
            val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
            val w = this.size.width
            val h = this.size.height
            val center = Offset(w * 0.50f, h * 0.50f)
            val radius = w * 0.36f
            drawCircle(color = tint, radius = radius, center = center, style = stroke)
            // Dot
            drawCircle(color = tint, radius = 1.2.dp.toPx(), center = Offset(w * 0.50f, h * 0.34f))
            // Stem
            drawLine(
                color = tint,
                start = Offset(w * 0.50f, h * 0.46f),
                end = Offset(w * 0.50f, h * 0.68f),
                strokeWidth = stroke.width,
                cap = stroke.cap
            )
        }
    }

    @Composable
    fun Trash(
        modifier: Modifier = Modifier,
        tint: Color = LocalContentColor.current,
        size: Dp = 18.dp
    ) {
        Canvas(modifier = modifier.size(size)) {
            val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val w = this.size.width
            val h = this.size.height
            // Lid line
            drawLine(
                color = tint,
                start = Offset(w * 0.20f, h * 0.28f),
                end = Offset(w * 0.80f, h * 0.28f),
                strokeWidth = stroke.width,
                cap = stroke.cap
            )
            // Handle
            val handle = Path().apply {
                moveTo(w * 0.38f, h * 0.28f)
                lineTo(w * 0.38f, h * 0.18f)
                lineTo(w * 0.62f, h * 0.18f)
                lineTo(w * 0.62f, h * 0.28f)
            }
            drawPath(handle, color = tint, style = stroke)
            // Bin Body
            val body = Path().apply {
                moveTo(w * 0.26f, h * 0.28f)
                lineTo(w * 0.30f, h * 0.82f)
                lineTo(w * 0.70f, h * 0.82f)
                lineTo(w * 0.74f, h * 0.28f)
            }
            drawPath(body, color = tint, style = stroke)
            // Slits
            drawLine(color = tint, start = Offset(w * 0.42f, h * 0.38f), end = Offset(w * 0.42f, h * 0.72f), strokeWidth = stroke.width, cap = stroke.cap)
            drawLine(color = tint, start = Offset(w * 0.58f, h * 0.38f), end = Offset(w * 0.58f, h * 0.72f), strokeWidth = stroke.width, cap = stroke.cap)
        }
    }

    @Composable
    fun Edit(
        modifier: Modifier = Modifier,
        tint: Color = LocalContentColor.current,
        size: Dp = 18.dp
    ) {
        Canvas(modifier = modifier.size(size)) {
            val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val w = this.size.width
            val h = this.size.height
            val path = Path().apply {
                moveTo(w * 0.68f, h * 0.18f)
                lineTo(w * 0.82f, h * 0.32f)
                lineTo(w * 0.36f, h * 0.78f)
                lineTo(w * 0.20f, h * 0.82f)
                lineTo(w * 0.24f, h * 0.66f)
                close()
            }
            drawPath(path, color = tint, style = stroke)
        }
    }

    @Composable
    fun Sliders(
        modifier: Modifier = Modifier,
        tint: Color = LocalContentColor.current,
        size: Dp = 18.dp
    ) {
        Canvas(modifier = modifier.size(size)) {
            val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
            val w = this.size.width
            val h = this.size.height
            // Line 1
            drawLine(color = tint, start = Offset(w * 0.18f, h * 0.32f), end = Offset(w * 0.82f, h * 0.32f), strokeWidth = stroke.width, cap = stroke.cap)
            drawCircle(color = tint, radius = 2.4.dp.toPx(), center = Offset(w * 0.38f, h * 0.32f))
            // Line 2
            drawLine(color = tint, start = Offset(w * 0.18f, h * 0.68f), end = Offset(w * 0.82f, h * 0.68f), strokeWidth = stroke.width, cap = stroke.cap)
            drawCircle(color = tint, radius = 2.4.dp.toPx(), center = Offset(w * 0.64f, h * 0.68f))
        }
    }

    @Composable
    fun Sun(
        modifier: Modifier = Modifier,
        tint: Color = LocalContentColor.current,
        size: Dp = 18.dp
    ) {
        Canvas(modifier = modifier.size(size)) {
            val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
            val w = this.size.width
            val h = this.size.height
            drawCircle(color = tint, radius = w * 0.22f, style = stroke)
            val center = Offset(w * 0.5f, h * 0.5f)
            val rIn = w * 0.32f
            val rOut = w * 0.44f
            for (i in 0 until 8) {
                val angle = (i * 45) * (Math.PI / 180.0)
                val cosA = kotlin.math.cos(angle).toFloat()
                val sinA = kotlin.math.sin(angle).toFloat()
                drawLine(
                    color = tint,
                    start = Offset(center.x + rIn * cosA, center.y + rIn * sinA),
                    end = Offset(center.x + rOut * cosA, center.y + rOut * sinA),
                    strokeWidth = stroke.width,
                    cap = stroke.cap
                )
            }
        }
    }

    @Composable
    fun Moon(
        modifier: Modifier = Modifier,
        tint: Color = LocalContentColor.current,
        size: Dp = 18.dp
    ) {
        Canvas(modifier = modifier.size(size)) {
            val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val w = this.size.width
            val h = this.size.height
            val path = Path().apply {
                moveTo(w * 0.74f, h * 0.16f)
                cubicTo(w * 0.42f, h * 0.16f, w * 0.18f, h * 0.40f, w * 0.18f, h * 0.72f)
                cubicTo(w * 0.18f, h * 0.88f, w * 0.26f, h * 0.94f, w * 0.32f, h * 0.94f)
                cubicTo(w * 0.22f, h * 0.86f, w * 0.16f, h * 0.70f, w * 0.16f, h * 0.52f)
                cubicTo(w * 0.16f, h * 0.26f, w * 0.38f, h * 0.10f, w * 0.64f, h * 0.10f)
                cubicTo(w * 0.70f, h * 0.10f, w * 0.76f, h * 0.12f, w * 0.80f, h * 0.14f)
                close()
            }
            drawPath(path, color = tint, style = stroke)
        }
    }

    @Composable
    fun Monitor(
        modifier: Modifier = Modifier,
        tint: Color = LocalContentColor.current,
        size: Dp = 18.dp
    ) {
        Canvas(modifier = modifier.size(size)) {
            val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val w = this.size.width
            val h = this.size.height
            drawRoundRect(
                color = tint,
                topLeft = Offset(w * 0.14f, h * 0.18f),
                size = Size(w * 0.72f, h * 0.52f),
                cornerRadius = CornerRadius(3.dp.toPx()),
                style = stroke
            )
            drawLine(
                color = tint,
                start = Offset(w * 0.50f, h * 0.70f),
                end = Offset(w * 0.50f, h * 0.84f),
                strokeWidth = stroke.width,
                cap = stroke.cap
            )
            drawLine(
                color = tint,
                start = Offset(w * 0.32f, h * 0.84f),
                end = Offset(w * 0.68f, h * 0.84f),
                strokeWidth = stroke.width,
                cap = stroke.cap
            )
        }
    }
}
