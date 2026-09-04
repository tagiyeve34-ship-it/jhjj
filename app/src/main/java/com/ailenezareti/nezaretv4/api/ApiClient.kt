package com.ailenezareti.nezaretv4.api
import com.ailenezareti.nezaretv4.Prefs
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
object ApiClient{
 const val BASE="https://hesabat.site/usaq/webpanel/api/"
 fun service(ctx:android.content.Context):ApiService{
  val client=OkHttpClient.Builder().addInterceptor{chain-> val t=Prefs.token(ctx); val b=chain.request().newBuilder(); if(t.isNotBlank()) b.header("Authorization","Bearer $t"); chain.proceed(b.build())}.addInterceptor(HttpLoggingInterceptor().apply{level=HttpLoggingInterceptor.Level.BASIC}).build()
  return Retrofit.Builder().baseUrl(BASE).client(client).addConverterFactory(GsonConverterFactory.create()).build().create(ApiService::class.java)
 }
}
