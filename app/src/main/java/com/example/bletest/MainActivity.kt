package com.example.bletest

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposables
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_main.*

import timber.log.Timber

class MainActivity : AppCompatActivity() {


    private val REQUEST_ENABLE_BT = 100

    private val REQUEST_LOCATION_PERMISSIONS = 101
    private var bleSensorDisposable = Disposables.disposed()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.plant(Timber.DebugTree())
        setContentView(R.layout.activity_main)


    }

    private fun showSnackbar(message: CharSequence) {
        Snackbar.make(mainView, message, Snackbar.LENGTH_LONG).show()
    }

    private fun startScan() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Timber.e("Bluetooth is not enabled")
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(intent, REQUEST_ENABLE_BT)
        }
        Timber.d("BluetoothEnabled")
        val bleHelper = BleHelper()
        if (!bleSensorDisposable.isDisposed) return
        bleSensorDisposable = bleHelper.findSensor(bluetoothAdapter.bluetoothLeScanner)
            .flatMapObservable { bleHelper.observeSensorData(applicationContext, it) }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(
                onNext = { sensorData ->
                    sensorData.stateSting?.let { tvSensorState.text = it }
                    sensorData.value?.let {  tvSensorValue.text = it[1].toString() }

                },
                onError = { showSnackbar(it.message.toString()) })
    }

    private fun checkPermissionsAndScan() {
        val permissionsGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!permissionsGranted) {
            // No explanation needed, we can request the permission.
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
                REQUEST_LOCATION_PERMISSIONS
            )
        } else {
           startScan()
        }
    }

    override fun onPause() {
        super.onPause()
        bleSensorDisposable.dispose()
    }

    override fun onResume() {
        super.onResume()
        checkPermissionsAndScan()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_OK) {
            startScan()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>, grantResults: IntArray
    ) {
        when (requestCode) {
            REQUEST_LOCATION_PERMISSIONS -> {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    startScan()
                } else {
                    val snackbar =
                        Snackbar.make(mainView, "The app needed permissions to work with BLE", Snackbar.LENGTH_LONG)
                    snackbar.setAction("Give permissions") { checkPermissionsAndScan() }
                    snackbar.show()
                }
                return
            }
            else -> {
                // Ignore all other requests.
            }
        }
    }
}
