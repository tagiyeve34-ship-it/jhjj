package com.ailenezareti.nezaretv4
import android.content.Context
object Prefs{
 private const val F="nezaret_v4"; private fun p(c:Context)=c.getSharedPreferences(F,0)
 fun token(c:Context)=p(c).getString("token","")?:""; fun setToken(c:Context,v:String)=p(c).edit().putString("token",v).apply()
 fun isLogged(c:Context)=token(c).isNotBlank(); fun child(c:Context)=p(c).getInt("child",-1); fun setChild(c:Context,v:Int)=p(c).edit().putInt("child",v).apply()
 fun parent(c:Context)=p(c).getString("parent","")?:""; fun setParent(c:Context,v:String)=p(c).edit().putString("parent",v).apply()
 fun fcm(c:Context)=p(c).getString("fcm","")?:""; fun setFcm(c:Context,v:String)=p(c).edit().putString("fcm",v).apply()
 fun logout(c:Context)=p(c).edit().clear().apply()
}
object AppLock { var unlocked=false }
