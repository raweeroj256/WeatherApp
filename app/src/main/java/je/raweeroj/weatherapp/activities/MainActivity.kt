package je.raweeroj.weatherapp.activities

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.annotation.RequiresApi
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
import je.raweeroj.weatherapp.R
import je.raweeroj.weatherapp.databinding.ActivityMainBinding
import je.raweeroj.weatherapp.models.WeatherResponse
import je.raweeroj.weatherapp.network.WeatherService
import je.raweeroj.weatherapp.utils.Constants
import retrofit.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var mFusedLocationClient : FusedLocationProviderClient

    private var binding : ActivityMainBinding? = null
    private var mProgressDialog : Dialog? = null

    private lateinit var mSharedPreferences: SharedPreferences

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)

        setContentView(binding?.root)

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        mSharedPreferences = getSharedPreferences(Constants.PREFERENCE_NAME,Context.MODE_PRIVATE)

        val weatherResponseJsonString =
            mSharedPreferences.getString(Constants.WEATHER_RESPONSE_DATA,"")

        if(!weatherResponseJsonString.isNullOrEmpty()) {
            setupUI()
        }

        if(!isLocationEnabled()){
            Toast.makeText(this,
            "Your location provider is turned off. Please turn it on!",
            Toast.LENGTH_SHORT).show()

            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        }else{
            Dexter.withActivity(this).withPermissions(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ).withListener(object : MultiplePermissionsListener {
                override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                    if(report!!.areAllPermissionsGranted()){
                        requestNewLocationData()
                    }

                    if(report.isAnyPermissionPermanentlyDenied){
                        Toast.makeText(this@MainActivity,
                        "You have denied location permission. Please enable them as it is needed",
                        Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onPermissionRationaleShouldBeShown(
                    permissions: MutableList<PermissionRequest>?,
                    token: PermissionToken?
                ) {
                    showRationalDialogForPermissions()
                }
            }).onSameThread().check()
        }


    }

    @SuppressLint("MissingPermission")
    private fun requestNewLocationData() {
        var mLocationRequest = com.google.android.gms.location.LocationRequest()
        mLocationRequest.priority = com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY
        mLocationRequest.interval = 1000
        mLocationRequest.numUpdates = 1


        mFusedLocationClient.requestLocationUpdates(
            mLocationRequest,
            mLocationCallBack,
            Looper.myLooper()
        )
    }

    private val mLocationCallBack=object: LocationCallback(){
        override fun onLocationResult(locationResult: LocationResult) {
            val lastLocation: Location? =locationResult.lastLocation
            val mLatitude=lastLocation!!.latitude
            val mLongitude=lastLocation!!.longitude

            getLocationWeatherDetails(mLatitude,mLongitude)

        }
    }

    private fun isLocationEnabled(): Boolean{
        val locationManager : LocationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER)
    }

    private fun showCustomProgressDialog(){
        mProgressDialog = Dialog(this)

        mProgressDialog!!.setContentView(R.layout.dialog_custom_progress)

        mProgressDialog!!.show()
    }

    private fun hideProgressDialog(){
        if (mProgressDialog!=null){
            mProgressDialog!!.dismiss()
        }
    }

    private fun getLocationWeatherDetails(latitude: Double, longitude : Double){
        if(Constants.isNetworkAvailable(this)){
         val retrofit : Retrofit =  Retrofit.Builder().baseUrl(Constants.BASE_URL)
             .addConverterFactory(GsonConverterFactory.create()).build()

            val service : WeatherService = retrofit
                .create<WeatherService>(WeatherService::class.java)

            val listCall : Call<WeatherResponse> = service.getWeather(
                latitude,longitude,Constants.METRIC_UNIT,Constants.APP_ID
            )

            showCustomProgressDialog()

            listCall.enqueue(object : Callback<WeatherResponse>{
                @RequiresApi(Build.VERSION_CODES.N)
                override fun onResponse(response: Response<WeatherResponse>?, retrofit: Retrofit?) {
                    if(response!!.isSuccess){
                        hideProgressDialog()

                        val weatherList:WeatherResponse = response.body()

                        val weatherResponseJsonString = Gson().toJson(weatherList)
                        val editor = mSharedPreferences.edit()
                        editor.putString(Constants.WEATHER_RESPONSE_DATA,weatherResponseJsonString)
                        editor.apply()

                        setupUI()
                        Log.i("Response result","$weatherList")
                    }else{
                        val rc = response.code()
                        hideProgressDialog()
                        when (rc){
                            400->{
                                Log.e("Error 400","Bad Connection")
                            }
                            404->{
                                Log.e("Error 404","Not found")
                            }else ->{
                            Log.e("Other Error ","Other Error")
                            }
                        }
                    }
                }

                override fun onFailure(t: Throwable?) {
                    hideProgressDialog()
                    Log.e("Errorrrrrr ","Error")
                }

            })

        }else{
            Toast.makeText(this,"You have no internet connection",Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRationalDialogForPermissions(){
        AlertDialog.Builder(this).setMessage("It looks like you have turn off permissions required for this feature. " +
                "It can be enabled in your Application setting")
            .setPositiveButton("GO TO SETTINGS"){
                    _,_ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package",packageName,null)
                    intent.data = uri
                    startActivity(intent)
                }catch (e: ActivityNotFoundException){
                    e.printStackTrace()
                }

            }.setNegativeButton("Cancel"){dialog,_->
                dialog.dismiss()
            }.show()
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun setupUI(){

        val weatherResponseJsonString =
            mSharedPreferences.getString(Constants.WEATHER_RESPONSE_DATA,"")

        if(!weatherResponseJsonString.isNullOrEmpty()){

            val weatherList = Gson().fromJson(weatherResponseJsonString,WeatherResponse::class.java)
            for(i in weatherList.weather.indices){
                Log.i("Weather Name",weatherList.weather.toString())

                binding?.tvMain?.text = weatherList.weather[i].main
                binding?.tvMainDescription?.text = weatherList.weather[i].description
                binding?.tvTemp?.text = weatherList.main.temp.toString() + getUnit(application.resources.configuration.locales.toString())

                binding?.tvSunriseTime?.text = unixTime(weatherList.sys.sunrise)
                binding?.tvSunsetTime?.text = unixTime(weatherList.sys.sunset)
                binding?.tvHumidity?.text = weatherList.main.humidity.toString() + " %"
                binding?.tvMin?.text = weatherList.main.temp_min.toString() + " min"
                binding?.tvMax?.text = weatherList.main.temp_max.toString() + " max"
                binding?.tvSpeed?.text = weatherList.wind.speed.toString()
                binding?.tvName?.text = weatherList.name
                binding?.tvCountry?.text = weatherList.sys.country

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
                }

            }
        }



    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main,menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId){
            R.id.action_refresh -> {
                requestNewLocationData()
                true
            }else -> super.onOptionsItemSelected(item)
        }


    }


    private fun getUnit(value:String):String?{
        var unit = "°C"
        if("US" == value || "LR" == value || "MM" == value){
            unit = "°F"
        }
        return unit
    }

    private fun unixTime(timex:Long):String?{
        val date = Date(timex*1000L)
        val sdf = SimpleDateFormat("HH:mm",Locale.UK)
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }


}