package com.ailenezareti.nezaretv4.api
import com.ailenezareti.nezaretv4.model.*
import retrofit2.Response
import retrofit2.http.*
interface ApiService {
 @POST("login.php") suspend fun login(@Body b:LoginRequest):Response<LoginResponse>
 @GET("children.php") suspend fun children():Response<ChildrenResponse>
 @GET("locations.php") suspend fun locations(@Query("child_id") id:Int,@Query("range") range:String="today",@Query("from") from:String?=null,@Query("to") to:String?=null):Response<LocationsResponse>
 @GET("calls.php") suspend fun calls(@Query("child_id") id:Int,@Query("from") from:String?=null,@Query("to") to:String?=null,@Query("type") type:String="all",@Query("search") search:String?=null,@Query("limit") limit:Int=100,@Query("offset") offset:Int=0):Response<CallsResponse>
 @GET("zones.php") suspend fun zones(@Query("child_id") id:Int):Response<ZonesResponse>
 @POST("zones.php") suspend fun createZone(@Body b:ZoneSaveRequest):Response<SimpleStatus>
 @PUT("zones.php") suspend fun updateZone(@Body b:ZoneSaveRequest):Response<SimpleStatus>
 @HTTP(method="DELETE",path="zones.php",hasBody=true) suspend fun deleteZone(@Body b:ZoneDeleteRequest):Response<SimpleStatus>
 @GET("alerts.php") suspend fun alerts(@Query("child_id") id:Int):Response<AlertsResponse>
 @POST("push_register.php") suspend fun registerPush(@Body b:PushRegisterRequest):Response<SimpleStatus>
}
