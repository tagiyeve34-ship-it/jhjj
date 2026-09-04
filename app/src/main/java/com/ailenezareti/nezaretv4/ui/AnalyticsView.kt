package com.ailenezareti.nezaretv4.ui
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.ailenezareti.nezaretv4.R
class AnalyticsView @JvmOverloads constructor(c:Context,a:AttributeSet?=null):View(c,a){
 var incoming=0; var outgoing=0; var missed=0; var battery:List<Int> = emptyList(); var mode="calls"
 private val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{strokeWidth=12f;style=Paint.Style.STROKE;strokeCap=Paint.Cap.ROUND}
 override fun onDraw(x:Canvas){super.onDraw(x); if(mode=="calls") drawCalls(x) else drawBattery(x)}
 private fun drawCalls(c:Canvas){val total=(incoming+outgoing+missed).coerceAtLeast(1); val s=Math.min(width,height)*.60f; val r=RectF((width-s)/2,(height-s)/2,(width+s)/2,(height+s)/2); var a=-90f; val vals=listOf(incoming to Color.rgb(109,76,246),outgoing to Color.rgb(24,201,154),missed to Color.rgb(242,93,101)); vals.forEach{(v,col)->p.color=col; val sw=360f*v/total;c.drawArc(r,a,(sw-3f).coerceAtLeast(0f),false,p);a+=sw}; p.style=Paint.Style.FILL;p.color=Color.rgb(23,25,39);p.textAlign=Paint.Align.CENTER;p.textSize=18f*resources.displayMetrics.scaledDensity;c.drawText("Cəmi",width/2f,height/2f-4,p);p.textSize=30f*resources.displayMetrics.scaledDensity;c.drawText((incoming+outgoing+missed).toString(),width/2f,height/2f+34,p);p.style=Paint.Style.STROKE}
 private fun drawBattery(c:Canvas){if(battery.size<2)return;p.style=Paint.Style.STROKE;p.strokeWidth=5f;p.color=Color.rgb(109,76,246);val path=Path();battery.forEachIndexed{i,v->val xx=i.toFloat()/(battery.size-1)*(width-24)+12;val yy=height-12-(v/100f)*(height-24);if(i==0)path.moveTo(xx,yy)else path.lineTo(xx,yy)};c.drawPath(path,p);p.style=Paint.Style.STROKE}
}
