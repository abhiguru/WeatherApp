package `in`.tutorial.weatherapp.activities

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.location.LocationRequest
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import `in`.tutorial.weatherapp.Constants
import `in`.tutorial.weatherapp.R
import `in`.tutorial.weatherapp.databinding.ActivityMainBinding
import `in`.tutorial.weatherapp.models.WeatherResponse
import `in`.tutorial.weatherapp.network.WeatherService
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var mFusedLocationProviderClient: FusedLocationProviderClient
    private var mSharedPref: SharedPreferences? = null
    private var customProgressDialog : Dialog? = null
    var binding: ActivityMainBinding? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding?.root)
        mSharedPref = getSharedPreferences(Constants.PREFERENCE_NAME, Context.MODE_PRIVATE)
        // Load from Shared pref before call to API
        setupUI()
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        if(!isLocationEnabled()){
            Toast.makeText(
                this,
                "Your location provider is turned off. Please turn it on",
                Toast.LENGTH_SHORT
            ).show()
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        }else{
            Dexter.withActivity(this)
                .withPermissions(android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION)
                .withListener(object:MultiplePermissionsListener{
                    override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                        if(report!!.areAllPermissionsGranted()){
                            requestLocationData()
                        }
                        if(report.isAnyPermissionPermanentlyDenied){
                            Toast.makeText(
                                this@MainActivity,
                                "Your location provider is turned off. Please turn it on",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    override fun onPermissionRationaleShouldBeShown(
                        permissions: MutableList<PermissionRequest>?,
                        token: PermissionToken?
                    ) {
                        showRationalDialogForPermission()
                    }
                }).onSameThread().check()
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationData(){
        val mLocationRequest = com.google.android.gms.location.LocationRequest()
        mLocationRequest.priority = LocationRequest.QUALITY_HIGH_ACCURACY
        mFusedLocationProviderClient.requestLocationUpdates(
            mLocationRequest, mLocationCallback, Looper.myLooper()
        )
    }

    private val mLocationCallback = object : LocationCallback(){
        override fun onLocationResult(locationResult: LocationResult?) {
            Toast.makeText(
                this@MainActivity,
                "Your location on2",
                Toast.LENGTH_LONG
            ).show()
            val mLastLocation : Location = locationResult!!.lastLocation
            val lat = mLastLocation.latitude
            Log.e("Current Lat ", "$lat")
            val longi = mLastLocation.longitude
            Log.e("Current Longi ", "$longi")
            getLocationWeatherDetails(lat, longi)
        }
    }

    private fun getLocationWeatherDetails(lat:Double, longi:Double){
        if(Constants.isNetworkAvailable(this)){
            val retrofit: Retrofit = Retrofit.Builder().baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create()).build()
            val service :WeatherService = retrofit.create<WeatherService>(WeatherService::class.java)
            val listCall: Call<WeatherResponse> =
                service.getWeather(lat, longi, Constants.METRIC_UNIT, Constants.APP_ID)
            showProgressDialog()
            listCall.enqueue(object: Callback<WeatherResponse> {
                override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>
                ) {
                    hideProgressDialog()
                    if(response.isSuccessful){
                        val weatherList: WeatherResponse? = response.body()
                        weatherList?.let {
                            val weatherResponseJsonString = Gson().toJson(weatherList)
                            val editor = mSharedPref?.edit()
                            editor?.putString(Constants.WEATHER_RESPONSE_DATA, weatherResponseJsonString)
                            editor?.apply()
                            setupUI()
                        };
                        Log.e("Response Result :", weatherList.toString())
                    }else{
                        Log.e("Response Result 2:", response.code().toString())
                    }
                }

                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    hideProgressDialog()
                    Log.e("Response Result 3:", t!!.message.toString())
                }

            })
        }else{
            Toast.makeText(
                this@MainActivity,
                "Your location provider is turned off. Please turn it off",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    private fun showProgressDialog(){
        customProgressDialog = Dialog(this)
        customProgressDialog?.setContentView(R.layout.dialog_custom_progress)
        customProgressDialog?.show()
    }
    private fun hideProgressDialog(){
        customProgressDialog?.dismiss()
        customProgressDialog = null
    }
    private fun showRationalDialogForPermission(){
        AlertDialog.Builder(this)
           .setMessage("It looks like you have turned off permissions")
           .setPositiveButton("Go To Settings"){
                _,_->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName,null)
                    intent.data = uri
                    startActivity(intent)
                }catch (e:Exception){
                    e.printStackTrace()
                }
            }
           .setNegativeButton("Cancel"){
               dialog,_ ->
               dialog.cancel()
           }
           .show()
    }

    private fun isLocationEnabled():Boolean{
        val locationManager: LocationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun setupUI(){
        lateinit var weatherList:WeatherResponse
        val weatherResponseJsonString = mSharedPref?.getString(Constants.WEATHER_RESPONSE_DATA,"")
        if(!weatherResponseJsonString.isNullOrEmpty()){
            weatherList = Gson().fromJson(weatherResponseJsonString, WeatherResponse::class.java)
        }
        for(i in weatherList.weather.indices){
            Log.i("Weather Name", weatherList.weather.toString())
            binding?.tvMain?.text = weatherList.weather[i].main
            binding?.tvMainDescription?.text = weatherList.weather[i].description
            binding?.tvTemp?.text = weatherList.main.temp.toString() +
                    getUnit(application.resources.configuration.locales.toString())
            binding?.tvHumidity?.text = weatherList.main.humidity.toString() + "per cent"
            binding?.tvMin?.text = weatherList.main.temp_min.toString() + " min"
            binding?.tvMax?.text = weatherList.main.temp_max.toString() + " max"
            binding?.tvSpeed?.text = weatherList.wind.speed.toString()
            binding?.tvName?.text = weatherList.name
            binding?.tvCountry?.text = weatherList.sys.country
            binding?.tvSunriseTime?.text = unixTime(weatherList.sys.sunrise)
            binding?.tvSunsetTime?.text = unixTime(weatherList.sys.sunset)
            when(weatherList.weather[i].icon){
                "01d" -> binding?.ivMain?.setImageResource(R.drawable.sunny)
                "02d" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                "03d" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                "04d" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                "04n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                "10d" -> binding?.ivMain?.setImageResource(R.drawable.rain)
                "11d" -> binding?.ivMain?.setImageResource(R.drawable.storm)
                "13d" -> binding?.ivMain?.setImageResource(R.drawable.snowflake)
                "01n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                "02n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                "03n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                "10n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                "11n" -> binding?.ivMain?.setImageResource(R.drawable.rain)
                "13n" -> binding?.ivMain?.setImageResource(R.drawable.snowflake)
                "50d" -> binding?.ivMain?.setImageResource(R.drawable.rain)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId) {
            R.id.action_refresh -> {
                requestLocationData()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    private fun unixTime(timex:Long):String?{
        val date = Date(timex *1000L)
        val sdf = SimpleDateFormat("HH:mm", Locale.UK)
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }
    private fun getUnit(value : String):String?{
        var value = "*C"
        if("US" == value || "LR" == "value" || "MM" == value){
            value = "*F"
        }
        return value
    }
}