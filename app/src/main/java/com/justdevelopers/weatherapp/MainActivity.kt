package com.justdevelopers.weatherapp

import android.Manifest
import android.annotation.TargetApi
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import com.google.android.material.snackbar.Snackbar
import com.justdevelopers.weatherapp.databinding.ActivityMainBinding
import com.justdevelopers.weatherapp.models.WeatherModel
import com.justdevelopers.weatherapp.network.WeatherService
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@TargetApi(Build.VERSION_CODES.N)
class MainActivity : AppCompatActivity() {
    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    private lateinit var binding:ActivityMainBinding
//    private val utilService : UtilService

    private val requestLocationPermission:ActivityResultLauncher<Array<String>> = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){
            permissions->
        permissions.entries.forEach {
            val permissionName = it.key
            val isGranted = it.value
            if(isGranted){
                Toast.makeText(this@MainActivity,"you granted for location",Toast.LENGTH_LONG).show()
                requestLocationData()
            }else{
                if(permissionName== Manifest.permission.ACCESS_COARSE_LOCATION || permissionName== Manifest.permission.ACCESS_FINE_LOCATION){
                    Toast.makeText(this@MainActivity,"you denied for location, enable it in settings",Toast.LENGTH_LONG).show()
                }
                showRationalDialogForPermissions()
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        mFusedLocationClient =LocationServices.getFusedLocationProviderClient(this)
        val parentView:View = findViewById(android.R.id.content)
        if(!isLocationEnabled()){
//            Toast.makeText(this,"Please turn on Location services under system settings",Toast.LENGTH_LONG).show()
            Snackbar.make(parentView,"Location disabled",Snackbar.LENGTH_LONG).setAction("Open settings"){
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))

            }.show()

        }else{
            requestLocationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }
    private fun showRationalDialogForPermissions() {
        AlertDialog.Builder(this).setMessage("Permissions required, Enable them in setting")
            .setPositiveButton("go to settings"){
                    _,_ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package",packageName,null)
                    intent.data=uri
                    startActivity(intent)
                }catch (e: ActivityNotFoundException){
                    e.printStackTrace()
                }
            }.setNegativeButton("cancel"){
                    dialog, _ ->
                try {
                    dialog.dismiss()
                }catch (e:Exception){
                    e.printStackTrace()
                }
            }.show()
    }

    private fun getLocationWeatherDetails(latitude:Double,longitude:Double){
        val progressDialog = ProgressDialog(this)
        if(Constants.isNetworkAvailable(this)){
            val retrofit:Retrofit = Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            val service:WeatherService = retrofit.create(WeatherService::class.java)
            val listCall = service.getWeather(
                latitude,longitude,Constants.METRIC_UNIT,Constants.APP_ID
            )
            progressDialog.show()
            listCall.enqueue(object :Callback<WeatherModel>{
                override fun onResponse(
                    call: Call<WeatherModel>,
                    response: Response<WeatherModel>
                ) {
                    if(response.isSuccessful){
                        progressDialog.dismiss()
                        val weatherModel=response.body()
                        setupUI(weatherModel!!)
                        Log.e("response",weatherModel.toString())
                    }else{
                        when(response.code()){
                            400 ->{
                                Log.e("Error 400","Bad connection")
                            }
                            404 ->{
                                Log.e("Error 404","Not found")
                            }
                            else ->{
                                Log.e("Errorrr","generic error")
                            }

                        }
                    }
                }

                override fun onFailure(call: Call<WeatherModel>, t: Throwable) {
                    Log.e("fail",t.message.toString())

                }

            })
        }else{
            Toast.makeText(this,"No internet connection",Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun isLocationEnabled():Boolean{
        val locationManager:LocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun requestLocationData(){
        val locationRequest  = LocationRequest()
        locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        mFusedLocationClient.requestLocationUpdates(locationRequest,mLocationCallBack, Looper.myLooper())
    }
    private val mLocationCallBack = object : LocationCallback() {
        override fun onLocationResult(p0: LocationResult?) {
            super.onLocationResult(p0)
            val lastLocation = p0?.lastLocation
            val lat=lastLocation?.latitude
            val long = lastLocation?.longitude
            Log.e("last",lat.toString()+" "+long.toString())
            getLocationWeatherDetails(lat!!,long!!)
        }

        override fun onLocationAvailability(p0: LocationAvailability?) {
            super.onLocationAvailability(p0)
        }


    }
    private fun setupUI(weatherList:WeatherModel){
        for(i in weatherList.weather.indices){
            Log.i("weathername",weatherList.weather.toString())
            binding.tvMain.text = weatherList.weather[i].main
            binding.tvMainDescription.text = weatherList.weather[i].description
            binding.tvTemp.text = weatherList.main.temp.toString() + getUnit(application.resources.configuration.toString())

        }
    }

    private fun getUnit(value: String): String {
        var valu = "°C"
        if("US" == value || "LR"==value ||"MM"==value){
            valu = "°F"
        }
        return valu
    }
}


