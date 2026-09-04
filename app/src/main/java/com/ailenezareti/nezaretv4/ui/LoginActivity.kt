package com.ailenezareti.nezaretv4.ui
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ailenezareti.nezaretv4.*
import com.ailenezareti.nezaretv4.api.ApiClient
import com.ailenezareti.nezaretv4.databinding.ActivityLoginBinding
import com.ailenezareti.nezaretv4.model.LoginRequest
import kotlinx.coroutines.launch
class LoginActivity:AppCompatActivity(){private lateinit var b:ActivityLoginBinding;override fun onCreate(s:Bundle?){super.onCreate(s);b=ActivityLoginBinding.inflate(layoutInflater);setContentView(b.root);b.loginBtn.setOnClickListener{login()}}
 private fun login(){val e=b.email.text.toString().trim();val p=b.password.text.toString();if(e.isBlank()||p.isBlank())return;b.progress.visibility=View.VISIBLE;lifecycleScope.launch{try{val r=ApiClient.service(this@LoginActivity).login(LoginRequest(e,p));if(r.isSuccessful&&r.body()!=null){Prefs.setToken(this@LoginActivity,r.body()!!.token);Prefs.setParent(this@LoginActivity,r.body()!!.parent.full_name);startActivity(Intent(this@LoginActivity,MainActivity::class.java));finish()}else Toast.makeText(this@LoginActivity,"Giriş alınmadı",Toast.LENGTH_SHORT).show()}catch(x:Exception){Toast.makeText(this@LoginActivity,"Server bağlantısı alınmadı",Toast.LENGTH_SHORT).show()}finally{b.progress.visibility=View.GONE}}}}
