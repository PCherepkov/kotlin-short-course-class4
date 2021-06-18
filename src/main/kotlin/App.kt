// import com.sun.org.apache.xpath.internal.operations.Bool
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.swing.Swing
import org.jetbrains.skija.*
import org.jetbrains.skiko.SkiaLayer
import org.jetbrains.skiko.SkiaRenderer
import org.jetbrains.skiko.SkiaWindow
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.WindowConstants
import kotlin.math.min

fun main(args: Array<String>) {
    val size = 6
    State.size = size
    createWindow("BestGameEver (${args[0]})", size)
    startNetworking(args, "127.0.0.1", 2323)
}

fun fieldCheck() : Int {
    // 0 - free; 1 - you; 2 - opponent;
    val field = Array(State.size * State.size, {0})
    val streak = min(State.size, 5)

    for (i in State.points) {
        if (i.isRemote) {
            field[i.y * State.size + i.x] = 2
        } else {
            field[i.y * State.size + i.x] = 1
        }
    }

    // horizontals
    for (i in 0..(State.size - 1) )
        for (k in 0..(State.size - streak)) {
            var win1 = 0
            var win2 = 0
            var j = 0
            while (j < streak && field[i * State.size + k + j] == 1) {
                win1++
                j++
            }

            j = 0
            while (j < streak && field[i * State.size + k + j] == 2) {
                win2++
                j++
            }
            if (win1 == streak)
                return 1
            if (win2 == streak)
                return 2
        }

    // verticals
    for (i in 0..(State.size - 1))
        for (k in 0..(State.size - streak)) {
            var win1 = 0
            var win2 = 0
            var j = 0
            while (j < streak && field[(k + j) * State.size + i] == 1) {
                win1++
                j++
            }

            j = 0
            while (j < streak && field[(k + j) * State.size + i] == 2) {
                win2++
                j++
            }

            if (win1 == streak)
                return 1
            if (win2 == streak)
                return 2
        }

    if (State.size * State.size == State.points.size)
        return 0
    return -1
}

fun createWindow(title: String, size: Int) = runBlocking(Dispatchers.Swing) {
    val window = SkiaWindow()
    window.defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE
    window.title = title

    window.layer.renderer = Renderer(window.layer, size)
    window.layer.addMouseListener(MouseListener)

    window.preferredSize = Dimension(800, 800)
    window.minimumSize = Dimension(100,100)
    window.pack()
    window.layer.awaitRedraw()
    window.isVisible = true
}

data class Point(val x: Int, val y: Int, val isRemote: Boolean = false)

object State {
    var isServer = true
    var input: ByteReadChannel? = null
    var output: ByteWriteChannel? = null
    val points = mutableListOf<Point>()
    var W: Int = 800
    var H: Int = 800
    var size: Int = 3
    var choice: Int = 0
}

class Renderer(val layer: SkiaLayer, val fieldSize: Int): SkiaRenderer {
    val typeface = Typeface.makeFromFile("fonts/JetBrainsMono-Regular.ttf")
    val font = Font(typeface, 40f)
    val paint = Paint().apply {
        color = 0xff0000ff.toInt()
        mode = PaintMode.STROKE
        strokeWidth = 8f
    }
    val paintRemote = Paint().apply {
        color = 0xffff0000.toInt()
        mode = PaintMode.STROKE
        strokeWidth = 8f
    }
    val paintText = Paint().apply {
        color = 0xff11cd11.toInt()
        mode = PaintMode.FILL
        strokeWidth = 1f
    }
    val paintField = Paint().apply {
        color = 0xff000000.toInt()
        mode = PaintMode.FILL
        strokeWidth = 1f
    }
    val size = fieldSize

    override fun onRender(canvas: Canvas, width: Int, height: Int, nanoTime: Long) {
        val contentScale = layer.contentScale
        canvas.scale(contentScale, contentScale)
        val w = (width / contentScale).toInt()
        val h = (height / contentScale).toInt()

        State.W = w
        State.H = h

        State.points.forEach { p ->
            // canvas.drawCircle(p.x, p.y, 5f, if (p.isRemote) paintRemote else paint)
            if (State.isServer && p.isRemote || !State.isServer && !p.isRemote) {
                canvas.drawCircle(
                    p.x.toFloat() * State.W / size + State.W / size.toFloat() / 2,
                    p.y.toFloat() * State.H / size + State.H / size.toFloat() / 2,
                    min(State.H / size.toFloat() / 2, State.W / size.toFloat() / 2),
                    if (p.isRemote) paintRemote else paint
                )
            } else {
                canvas.drawLine(p.x.toFloat() * State.W / size, p.y.toFloat() * State.H / size,
                                (p.x + 1).toFloat() * State.W / size, (p.y + 1).toFloat() * State.H / size,
                                    if (p.isRemote) paintRemote else paint)
                canvas.drawLine((p.x + 1).toFloat() * State.W / size, p.y.toFloat() * State.H / size,
                                p.x.toFloat() * State.W / size, (p.y + 1).toFloat() * State.H / size,
                                    if (p.isRemote) paintRemote else paint)
            }
        }

        var i = 0f
        while (i <= size) {
            canvas.drawLine(0f, i * h / size, w.toFloat(), i * h / size, paintField)
            i++
        }

        i = 0f
        while (i <= size) {
            canvas.drawLine(i * w / size, 0f, i * w / size, h.toFloat(), paintField)
            i++
        }

        val res = fieldCheck()
        if (res == 0) {
            canvas.drawString("Game over!", State.W.toFloat() / 2 - 100, State.H.toFloat() / 2 - 20, font, paintText)
            canvas.drawString("Tie!", State.W.toFloat() / 2 - 40, State.H.toFloat() / 2 + 20, font, paintText)
            State.choice = 2
        }
        if (res == 1) {
            canvas.drawString("Game over!", State.W.toFloat() / 2 - 100, State.H.toFloat() / 2 - 20, font, paintText)
            canvas.drawString("Victory!", State.W.toFloat() / 2 - 80, State.H.toFloat() / 2 + 20, font, paintText)
            State.choice = 2
        }
        if (res == 2) {
            canvas.drawString("Game over!", State.W.toFloat() / 2 - 100, State.H.toFloat() / 2 - 20, font, paintText)
            canvas.drawString("Defeat!", State.W.toFloat() / 2 - 70, State.H.toFloat() / 2 + 20, font, paintText)
            State.choice = 2
        }

        layer.needRedraw()
    }
}

object MouseListener : MouseAdapter() {
    override fun mouseClicked(event: MouseEvent?) {
        if (event != null) {
            val mouseX = event.x.toFloat()
            val mouseY = event.y.toFloat()
            val x = (mouseX * State.size / State.W).toInt()
            val y = (mouseY * State.size / State.H).toInt()

            if (State.isServer && State.choice > 0 || !State.isServer && State.choice > -1)
                return

            var i = 0
            while (i <= State.points.lastIndex) {
                if (State.points[i].x == x && State.points[i].y == y)
                    return
                i++
            }
            State.points.add(Point(x, y))
            State.choice++
            sendMouseCoordinates(x, y)
        }
    }
}
