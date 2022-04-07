package com.example.mapgoogle

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.os.AsyncTask
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.PersistableBundle
import android.util.Log
import android.view.View
import android.webkit.WebStorage
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.view.get
import androidx.loader.content.AsyncTaskLoader
import com.github.florent37.runtimepermission.RuntimePermission.askPermission
import com.github.florent37.runtimepermission.kotlin.askPermission
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory

import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.lang.Exception
import java.lang.IndexOutOfBoundsException
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import java.util.jar.Manifest
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity(), OnMapReadyCallback, LocationListener
    //GoogleMap.OnCameraMoveListener, GoogleMap.OnCameraMoveStartedListener, GoogleMap.OnCameraIdleListener
{

    private var mMap: GoogleMap? = null

    lateinit var mapView: MapView

    private val MAP_VIEW_BUNDLE_KEY = "MapViewBundleKey"

    private val DEFAULT_ZOOM = 15f

    lateinit var tvCurrentAddress: TextView

    private var fusedLocationProviderClient: FusedLocationProviderClient? = null

    var end_latitude= 0.0

    var end_longitude= 0.0

    var origin: MarkerOptions? = null
    var destination:MarkerOptions? = null
    var latitude= 0.0
    var longitude= 0.0


    override fun onMapReady(googleMap: GoogleMap) {
        mapView.onResume()
        mMap = googleMap
        askPermissionLocation()

        if (ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        mMap!!.setMyLocationEnabled(true)
      //  mMap!!.setOnCameraMoveListener(this)
       // mMap!!.setOnCameraMoveStartedListener(this)
        //mMap!!.setOnCameraIdleListener(this)


    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mapView = findViewById<MapView>(R.id.map1)
        tvCurrentAddress = findViewById<TextView>(R.id.tvAdd)
        askPermissionLocation()

        var mapViewBundle: Bundle? = null
        if (savedInstanceState != null){
            mapViewBundle = savedInstanceState.getBundle(MAP_VIEW_BUNDLE_KEY)
        }


        mapView.onCreate(mapViewBundle)
        mapView.getMapAsync(this)

        findViewById<Button>(R.id.B_search).setOnClickListener{
            searchArea()
        }

        findViewById<Button>(R.id.B_clear).setOnClickListener{
            mapView.onCreate(mapViewBundle)
            mapView.getMapAsync(this)
        }


    }

    private fun searchArea() {
        val tf_location =
            findViewById<View>(R.id.TF_location) as EditText
        val location = tf_location.text.toString()
        var addressList: List<Address>? = null
        val markerOptions = MarkerOptions()
        Log.d("location =", location)
        if (location != "") {
            val geocoder = Geocoder(applicationContext)
            try {
                addressList = geocoder.getFromLocationName(location, 5)
            }catch (e: IOException) {
                e.printStackTrace()
            }
            if (addressList != null) {
                for (i in addressList.indices) {
                    val myAddress = addressList[i]
                    val latLng =
                        LatLng(myAddress.latitude,myAddress.longitude)
                    markerOptions.position(latLng)
                    mMap!!.addMarker(markerOptions)
                    end_latitude = myAddress.latitude
                    end_longitude = myAddress.longitude
                    mMap!!.animateCamera(CameraUpdateFactory.newLatLng(latLng))
                    val mo = MarkerOptions()
                    mo.title("Distance")
                    val results = FloatArray(10)
                    Location.distanceBetween(
                        latitude,
                        longitude,
                        end_latitude,
                        end_longitude,
                        results
                    )
                    val s = String.format("%.1f", results[0] / 1000)

                    origin = MarkerOptions().position(LatLng(latitude,longitude))
                        .title("HSR layout").snippet("origin")
                    destination =
                        MarkerOptions().position(LatLng(end_latitude, end_longitude))
                            .title(tf_location.text.toString())
                            .snippet("Distance = $s km")
                    mMap!!.addMarker(destination!!)
                    mMap!!.addMarker(origin!!)
                    Toast.makeText(
                        this@MainActivity,
                        "Distance = $s Km",
                        Toast.LENGTH_SHORT
                    ).show()
                    tvCurrentAddress!!.setText("Distance = $s KM")

                    //få URL til directions API
                    val url: String =
                        getDirectionsUrl(origin!!.getPosition(), destination!!.getPosition())!!

                    //kalle api-en nedenfor
                    val downloadTask: DownloadTask = DownloadTask()
                    downloadTask.execute(url)
                }
            }
        }
    }

    inner class DownloadTask :
        AsyncTask<String?, Void?, String>() {

        override fun onPostExecute(result: String) {
                super.onPostExecute(result)
            val parserTask = ParserTask()
            parserTask.execute(result)

            }

        override fun doInBackground(vararg url: String?): String {
            var data = ""
            try {
                data = downloadUrl(url[0].toString()).toString()
            }catch (e: java.lang.Exception) {

                Log.d("Background task", e.toString())
            }
            return data
        }
        }

    //class to parse JSON format

    inner class ParserTask :
        AsyncTask<String?, Int?, List<List<HashMap<String, String>>>?>() {
        override fun doInBackground(vararg jsonData: String?): List<List<HashMap<String, String>>>? {
            val jObject: JSONObject
            var routes: List<List<HashMap<String, String>>>? =
                null
            try {
                jObject = JSONObject(jsonData[0])
                val parser = DataParser()
                routes = parser.parse(jObject)
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
            return routes
        }

        override fun onPostExecute(result: List<List<HashMap<String, String>>>?) {
            val points = ArrayList<LatLng?>()
            val lineOptions = PolylineOptions()
            for (i in result!!.indices) {
                val path =
                    result[i]
                for (j in path.indices) {
                    val point = path[j]
                    val lat = point["lat"]!!.toDouble()
                    val lng = point["lng"]!!.toDouble()
                    val position = LatLng(lat, lng)
                    points.add(position)
                }
                lineOptions.addAll(points)
                lineOptions.width(8f)
                lineOptions.color(Color.RED)
                lineOptions.geodesic(true)
            }
            //viser polylinje i google map for i ruta
            if (points.size != 0) mMap!!.addPolyline(lineOptions)
          }
        }


     // A method to dowload json data from url

    @Throws(IOException::class)
    private fun downloadUrl(strUrl: String): String? {
        var data = ""
        var iStream: InputStream? = null
        var urlConnetion: HttpURLConnection? = null
        try {
            val url = URL(strUrl)
            urlConnetion = url.openConnection() as  HttpURLConnection
            urlConnetion.connect()
            iStream = urlConnetion!!.inputStream
            val br =
                BufferedReader(InputStreamReader(iStream))
            val sb = StringBuffer()
            var line: String? = ""
            while (br.readLine().also { line = it} != null) {
                sb.append(line)
            }

         data = sb.toString()
            br.close()
        } catch (e: java.lang.Exception) {
            Log.d("Exception", e.toString())
        } finally {
            iStream!!.close()
            urlConnetion!!.disconnect()
        }
        return data
    }

    //metoden til directions
    private fun getDirectionsUrl(origin: LatLng, dest: LatLng): String?{

        //rute origin
        val str_origin = "origin" + origin.latitude + "," + origin.longitude

        //destinasjon
        val str_dest = "destination" + dest.latitude + "," + dest.longitude

        //transport mode
        val mode = "mode=driving"

        //parameter for web service
        val parameters = "$str_origin&$str_dest&$mode"

        //output format
        val output = "json"

        //url til webside
        return "https://maps.googleapis.com/maps/api/directions/$output?$parameters&key=AIzaSyBrLhdXhCf4E4yZDzlmFme28w4U4j8Nqt4"

    }

    public override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        askPermissionLocation()
        var mapViewBundle = outState.getBundle(MAP_VIEW_BUNDLE_KEY)
        if (mapViewBundle == null){
            mapViewBundle = Bundle()
            outState.putBundle(MAP_VIEW_BUNDLE_KEY, mapViewBundle)
        }
        mapView.onSaveInstanceState(mapViewBundle)
    }

    private fun askPermissionLocation() {
        askPermission(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ){
            getCurrentLocation()
    //        mapView.getMapAsync(this@MainActivity)
        }.onDeclined { e ->
            if (e.hasDenied()){
                e.denied.forEach{
                }
                AlertDialog.Builder(this)
                    .setMessage("PLease accept permission")
                    .setPositiveButton("yes") {_, _ ->
                        e.askAgain()
                    }
                    .setNegativeButton("no"){dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
            }

            if(e.hasForeverDenied()){
                e.foreverDenied.forEach{

                }
                e.goToSettings();
            }
        }
    }
    private fun getCurrentLocation(){
        fusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(this@MainActivity)

        try {
            @SuppressLint("missingpermission") val location =
                fusedLocationProviderClient!!.getLastLocation()

            location.addOnCompleteListener(object : OnCompleteListener<Location>{
                override fun onComplete(loc: Task<Location>) {
                    if (loc.isSuccessful){
                        val currentLocation = loc.result as Location?
                        if (currentLocation !=null){
                            moveCamera(
                                LatLng(currentLocation.latitude, currentLocation.longitude),
                                DEFAULT_ZOOM
                            )

                            latitude = currentLocation.latitude
                            longitude = currentLocation.longitude
                        }
                    } else {
                        askPermissionLocation()

                    }
                }
            })
        } catch (se: Exception) {
            Log.e("TAG", "security Exception")
        }
    }
    private fun moveCamera(latLng: LatLng, zoom: Float) {
        mMap!!.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoom))
    }

    override fun onLocationChanged(p0: Location) {

    }

    //    override fun onLocationChanged(location: Location) {
     //   val geocoder = Geocoder(this, Locale.getDefault())
      //  var addresses : List<Address>? = null
      //  try {
       //     addresses = geocoder.getFromLocation(location!!.latitude, location.longitude, 1)
      //  } catch (e: IOException){
         //   e.printStackTrace()
       // }
     //       setAddress(addresses!![0])
    //}

   // private fun setAddress(addresses: Address) {
      //  if (addresses != null){
           // if (addresses.getAddressLine(0)!= null) {

          //      tvCurrentAddress!!.setText(addresses.getAddressLine(0))
         //   }
         //   if (addresses.getAddressLine(1)!= null) {
             //   tvCurrentAddress!!.setText(
           //         tvCurrentAddress.getText().toString() + addresses.getAddressLine(1)
         //       )
       //     }
     //   }

   // }

   // override fun onStatusChanged(p0: String?, p1: Int, p2: Bundle?) {

   // }

   // override fun onProviderEnabled(provider: String) {

   // }

   // override fun onProviderDisabled(provider: String) {

   // }

  //  override fun onCameraMove() {

  //  }

   // override fun onCameraMoveStarted(p0: Int) {
   // }

  //  override fun onCameraIdle() {
    //    var addresses: List<Address>? = null
      //  val geocoder = Geocoder(this, Locale.getDefault())
       // try {
      //      addresses = geocoder.getFromLocation(mMap!!.getCameraPosition().target.latitude, mMap!!.getCameraPosition().target.longitude, 1)

       //     setAddress(addresses!![0])
      //  }catch (e: IndexOutOfBoundsException) {
        //    e.printStackTrace()
      //  }catch (e: IOException) {
       //     e.printStackTrace()
     //   }
   // }
}






