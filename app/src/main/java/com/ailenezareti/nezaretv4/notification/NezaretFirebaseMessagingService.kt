package com.ailenezareti.nezaretv4.notification
import android.app.*
import android.content.*
import android.os.Build
import androidx.core.app.NotificationCompat
import com.ailenezareti.nezaretv4.*
import com.ailenezareti.nezaretv4.api.ApiClient
import com.ailenezareti.nezaretv4.model.PushRegisterRequest
import com.ailenezareti.nezaretv4.ui.PinActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.*
class NezaretFirebaseMessagingService:FirebaseMessagingService(){
 override fun onNewToken(t:String){Prefs.setFcm(this,t);if(Prefs.isLogged(this))CoroutineScope(Dispatchers.IO).launch{try{ApiClient.service(this@NezaretFirebaseMessagingService).registerPush(PushRegisterRequest(t))}catch(_:Exception){}}}
 override fun onMessageReceived(m:RemoteMessage){val title=m.notification?.title?:m.data["title"]?:"Nezaret V4";val body=m.notification?.body?:m.data["body"]?:"Yeni hadisə";val nm=getSystemService(NOTIFICATION_SERVICE) as NotificationManager;val ch="nezaret_v4_events";if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(NotificationChannel(ch,"Nezaret bildirişləri",NotificationManager.IMPORTANCE_HIGH));val pi=PendingIntent.getActivity(this,0,Intent(this,PinActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE);nm.notify((System.currentTimeMillis()%100000).toInt(),NotificationCompat.Builder(this,ch).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body)).setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).setContentIntent(pi).build())}
}
