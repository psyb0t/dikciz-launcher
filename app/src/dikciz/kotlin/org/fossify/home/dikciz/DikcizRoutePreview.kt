package org.fossify.home.dikciz

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import org.json.JSONArray

internal class DikcizRoutePreview(context: Context,private val points: JSONArray) : View(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    init { contentDescription="Local route preview, "+points.length()+" recorded points"; setBackgroundColor(DikcizUiStyle.SURFACE) }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (points.length()==0) {
            paint.color=DikcizUiStyle.FOREGROUND; paint.textSize=16*resources.displayMetrics.scaledDensity
            canvas.drawText("No location points recorded",16f,height/2f,paint); return
        }
        val rows=(0 until points.length()).map { points.getJSONObject(it) }
        val west=rows.minOf { it.getDouble("longitude") }; val east=rows.maxOf { it.getDouble("longitude") }
        val south=rows.minOf { it.getDouble("latitude") }; val north=rows.maxOf { it.getDouble("latitude") }
        val padding=32*resources.displayMetrics.density
        val dx=maxOf(east-west,0.00001); val dy=maxOf(north-south,0.00001)
        val scale=minOf((width-padding*2)/dx,(height-padding*2)/dy)
        val path=Path()
        val positions=rows.map {
            val x=(padding+(it.getDouble("longitude")-west)*scale).toFloat()
            val y=(height-padding-(it.getDouble("latitude")-south)*scale).toFloat()
            x to y
        }
        positions.forEachIndexed { index,(x,y) -> if (index==0) path.moveTo(x,y) else path.lineTo(x,y) }
        paint.color=DikcizUiStyle.ACCENT; paint.style=Paint.Style.STROKE; paint.strokeWidth=4*resources.displayMetrics.density
        canvas.drawPath(path,paint)
        paint.style=Paint.Style.FILL
        val radius=6*resources.displayMetrics.density
        paint.color=Color.GREEN; canvas.drawCircle(positions.first().first,positions.first().second,radius,paint)
        paint.color=DikcizUiStyle.ACCENT; canvas.drawCircle(positions.last().first,positions.last().second,radius,paint)
    }
}
