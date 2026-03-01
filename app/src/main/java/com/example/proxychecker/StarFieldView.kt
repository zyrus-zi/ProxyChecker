package com.example.proxychecker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import java.util.Random

class StarFieldView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val random = Random()
    private val stars = mutableListOf<Star>()
    private val paint = Paint().apply { isAntiAlias = true }
    private var starColor: Int = 0

    private data class Star(
        var x: Float,
        var y: Float,
        var size: Float,
        var speed: Float,
        var alpha: Int,
        var alphaSpeed: Int
    )

    init {
        updateColors()
    }

    fun updateColors() {
        // УЛУЧШЕНО: Проверяем реальное состояние темы приложения
        val isDark = AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES

        starColor = if (isDark) {
            ContextCompat.getColor(context, android.R.color.white)
        } else {
            // В светлой теме звезды будут нежно-голубыми (Primary цветом)
            ContextCompat.getColor(context, R.color.md_theme_light_primary)
        }
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        stars.clear()
        for (i in 0 until 100) { // Оптимально для производительности
            stars.add(Star(
                random.nextFloat() * w,
                random.nextFloat() * h,
                random.nextFloat() * 5f + 1f,
                random.nextFloat() * 1.2f + 0.3f,
                random.nextInt(155) + 100,
                random.nextInt(5) + 2
            ))
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (star in stars) {
            star.y += star.speed
            if (star.y > height) {
                star.y = -10f
                star.x = random.nextFloat() * width
            }
            star.alpha += star.alphaSpeed
            if (star.alpha > 255 || star.alpha < 100) star.alphaSpeed *= -1

            paint.color = starColor
            paint.alpha = star.alpha.coerceIn(0, 255)
            canvas.drawCircle(star.x, star.y, star.size, paint)
        }
        postInvalidateOnAnimation()
    }
}