package fr.scanneat.presentation.result

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import fr.scanneat.domain.model.Grade
import java.io.File

/** What the shared card shows (Roadmap A9). */
internal data class ShareCardData(
    val productName: String,
    val score: Int,
    val grade: Grade,
    /** Personal verdict line, or the block reason when vetoed for the profile. */
    val personalLine: String?,
    val personalBlocked: Boolean,
    val sourceLine: String,
    val footer: String,
)

/** Fixed light palette (the card is a standalone image, independent of the app theme). */
private fun gradeArgb(g: Grade): Int = when (g) {
    Grade.A_PLUS -> 0xFF1B5E20.toInt(); Grade.A -> 0xFF2E7D32.toInt(); Grade.B -> 0xFF8A7A00.toInt()
    Grade.C -> 0xFFB26A00.toInt(); Grade.D -> 0xFFC25100.toInt(); Grade.E -> 0xFFC62828.toInt(); Grade.F -> 0xFF8E0000.toInt()
}

internal fun renderShareCard(d: ShareCardData): Bitmap {
    val w = 1080; val h = 1080
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.drawColor(0xFFF6F1EC.toInt())
    val pad = 72f
    val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1A1A1A.toInt(); textSize = 56f; isFakeBoldText = true }
    val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF333333.toInt(); textSize = 40f }
    val small = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF666666.toInt(); textSize = 30f }

    fun block(text: String, paint: TextPaint, top: Float, maxLines: Int = 3): Float {
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, (w - 2 * pad).toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setMaxLines(maxLines)
            .setEllipsize(android.text.TextUtils.TruncateAt.END).build()
        c.save(); c.translate(pad, top); layout.draw(c); c.restore()
        return top + layout.height
    }

    var y = block(d.productName, title, pad, maxLines = 2) + 48f
    // Score disc
    val color = gradeArgb(d.grade)
    val cx = w / 2f; val r = 210f; val cy = y + r
    c.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
    val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.WHITE; textSize = 170f; isFakeBoldText = true; textAlign = Paint.Align.CENTER }
    c.drawText(d.grade.label, cx, cy + 20f, big)
    val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.WHITE; textSize = 52f; textAlign = Paint.Align.CENTER }
    c.drawText("${d.score}/100", cx, cy + 100f, sub)
    y = cy + r + 56f
    d.personalLine?.let { line ->
        val p = TextPaint(body).apply { if (d.personalBlocked) { this.color = 0xFFC62828.toInt(); isFakeBoldText = true } }
        if (d.personalBlocked) c.drawRoundRect(RectF(pad - 16f, y - 12f, w - pad + 16f, y + 120f), 24f, 24f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0x22C62828 })
        y = block(line, p, y, maxLines = 2) + 40f
    }
    block(d.sourceLine, small, h - pad - 90f, maxLines = 1)
    block(d.footer, small, h - pad - 40f, maxLines = 1)
    return bmp
}

/** Writes the card into the cache folder exposed by FileProvider and opens the share sheet. */
internal fun shareResultCard(context: Context, d: ShareCardData, text: String) {
    val dir = File(context.cacheDir, "shared_cards").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() } // only the latest card is kept
    val file = File(dir, "scaneat-${System.currentTimeMillis()}.png")
    file.outputStream().use { renderShareCard(d).compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(context, context.packageName + ".shareprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, text)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, null))
}

