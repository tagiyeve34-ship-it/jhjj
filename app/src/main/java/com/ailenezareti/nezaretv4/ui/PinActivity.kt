package com.ailenezareti.nezaretv4.ui
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.ailenezareti.nezaretv4.*
import com.ailenezareti.nezaretv4.databinding.ActivityPinBinding
import java.security.MessageDigest
class PinActivity:AppCompatActivity(){
 private lateinit var b:ActivityPinBinding; private var pin=""; private val hash="be41b7f1fa56ba2b0582910053c86cf6ee7e311efc51300220df0918bb9a287b"
 override fun onCreate(s:Bundle?){super.onCreate(s);b=ActivityPinBinding.inflate(layoutInflater);setContentView(b.root); val keys=listOf(b.k0,b.k1,b.k2,b.k3,b.k4,b.k5,b.k6,b.k7,b.k8,b.k9);keys.forEach{v->v.setOnClickListener{add((v as Button).text.toString())}};b.kDel.setOnClickListener{if(pin.isNotEmpty()){pin=pin.dropLast(1);render()}}}
 private fun add(x:String){if(pin.length<4){pin+=x;render();if(pin.length==4)verify()}}
 private fun render(){b.pinDots.text=(0 until 4).joinToString("  "){if(it<pin.length)"●" else "○"}}
 private fun verify(){val h=MessageDigest.getInstance("SHA-256").digest(pin.toByteArray()).joinToString(""){"%02x".format(it)};if(h==hash){AppLock.unlocked=true;startActivity(Intent(this,if(Prefs.isLogged(this))MainActivity::class.java else LoginActivity::class.java));finish()}else{b.pinError.text="PIN yanlışdır";b.pinError.visibility=View.VISIBLE;pin="";render()}}
}
