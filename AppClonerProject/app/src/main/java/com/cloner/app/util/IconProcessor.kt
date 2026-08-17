package com.cloner.app.util

import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable

/**
 * IconProcessor: Bộ xử lý biến đổi biểu tượng ứng dụng (Đổi màu, Xoay, Lật, Thêm huy hiệu số).
 */
object IconProcessor {

    /**
     * Chuyển đổi Drawable sang Bitmap.
     */
    fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val bitmap = if (drawable.intrinsicWidth <= 0 || drawable.intrinsicHeight <= 0) {
            Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
        } else {
            Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
        }
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    /**
     * Thay đổi sắc thái màu (Hue Rotate) của biểu tượng.
     * @param hueOffset Độ lệch màu từ 0 đến 360 độ.
     */
    fun changeHue(bitmap: Bitmap, hueOffset: Float): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val colorMatrix = ColorMatrix()
        // Điều chỉnh kênh màu
        val cosVal = Math.cos(Math.toRadians(hueOffset.toDouble())).toFloat()
        val sinVal = Math.sin(Math.toRadians(hueOffset.toDouble())).toFloat()
        val lumR = 0.213f
        val lumG = 0.715f
        val lumB = 0.072f

        colorMatrix.set(floatArrayOf(
            lumR + cosVal * (1 - lumR) + sinVal * (-lumR), lumG + cosVal * (-lumG) + sinVal * (-lumG), lumB + cosVal * (-lumB) + sinVal * (1 - lumB), 0f, 0f,
            lumR + cosVal * (-lumR) + sinVal * 0.143f, lumG + cosVal * (1 - lumG) + sinVal * 0.140f, lumB + cosVal * (-lumB) + sinVal * (-0.283f), 0f, 0f,
            lumR + cosVal * (-lumR) + sinVal * (-(1 - lumR)), lumG + cosVal * (-lumG) + sinVal * lumG, lumB + cosVal * (1 - lumB) + sinVal * lumB, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))

        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return output
    }

    /**
     * Lật ngang (Flip) biểu tượng.
     */
    fun flipHorizontal(bitmap: Bitmap): Bitmap {
        val matrix = Matrix().apply { preScale(-1f, 1f) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * Thêm huy hiệu số clone (Badge number) vào góc dưới bên phải icon.
     */
    fun addCloneBadge(bitmap: Bitmap, cloneNumber: Int): Bitmap {
        val output = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)

        val badgeRadius = (bitmap.width * 0.2f)
        val badgeX = bitmap.width - badgeRadius - 8f
        val badgeY = bitmap.height - badgeRadius - 8f

        // Vẽ nền tròn huy hiệu
        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E53935") // Đỏ nổi bật
            style = Paint.Style.FILL
        }
        canvas.drawCircle(badgeX, badgeY, badgeRadius, circlePaint)

        // Vẽ viền trắng
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        canvas.drawCircle(badgeX, badgeY, badgeRadius, borderPaint)

        // Vẽ số clone
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = badgeRadius * 1.3f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val textY = badgeY - ((textPaint.descent() + textPaint.ascent()) / 2)
        canvas.drawText(cloneNumber.toString(), badgeX, textY, textPaint)

        return output
    }
}
