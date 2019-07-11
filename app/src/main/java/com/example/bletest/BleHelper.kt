package com.example.bletest

import android.bluetooth.*
import android.bluetooth.BluetoothAdapter.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import io.reactivex.Observable
import io.reactivex.Single
import timber.log.Timber
import java.util.*

class BleHelper {
    companion object {
        const val SENSOR_MAC = "98:4F:EE:10:6C:4A"
    }

    private val HEART_RATE_SERVICE_UUID = convertFromInteger(0x180D)
    private val HEART_RATE_MEASUREMENT_CHAR_UUID = convertFromInteger(0x2A37)
    private val HEART_RATE_CONTROL_POINT_CHAR_UUID = convertFromInteger(0x2A39)
    private val CLIENT_CHARACTERISTIC_CONFIG_UUID = convertFromInteger(0x2902)

    fun findSensor(bluetoothLeScanner: BluetoothLeScanner): Single<BluetoothDevice> {
        return Single.create { emitter ->
            val scanCallback = object: ScanCallback() {
                override fun onScanFailed(errorCode: Int) {
                    super.onScanFailed(errorCode)
                    emitter.onError(Exception("ScanFailed with code: $errorCode"))
                }

                override fun onScanResult(callbackType: Int, result: ScanResult?) {
                    super.onScanResult(callbackType, result)
                    if (result?.device != null
                        && result.device.address.toUpperCase() == SENSOR_MAC
                    )
                        emitter.onSuccess(result.device)
                }
            }
            bluetoothLeScanner.startScan(scanCallback)
            emitter.setCancellable { bluetoothLeScanner.stopScan(scanCallback) }
        }
    }

    fun observeSensorData(context: Context, bleDevice: BluetoothDevice ): Observable<SensorData> {
        return Observable.create { emitter ->
            val gattCallback = object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                    super.onConnectionStateChange(gatt, status, newState)
                    if (newState == STATE_CONNECTED && gatt != null) gatt.discoverServices()
                    emitter.onNext(SensorData(state = newState))
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                    super.onServicesDiscovered(gatt, status)
                    if (gatt == null) return
                     val characteristic = gatt.getService(HEART_RATE_SERVICE_UUID)
                         .getCharacteristic(HEART_RATE_MEASUREMENT_CHAR_UUID)
                    gatt.setCharacteristicNotification(characteristic,true)
                    val gattDescriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
                    gattDescriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(gattDescriptor)
                }

                override fun onDescriptorWrite(
                    gatt: BluetoothGatt?,
                    descriptor: BluetoothGattDescriptor?,
                    status: Int
                ) {
                    super.onDescriptorWrite(gatt, descriptor, status)
                    if (gatt == null) return
                    val characteristic = gatt.getService(HEART_RATE_SERVICE_UUID)
                        .getCharacteristic(HEART_RATE_CONTROL_POINT_CHAR_UUID) ?: return
                    characteristic.value = byteArrayOf(1,1)
                    gatt.writeCharacteristic(characteristic)

                }

                override fun onCharacteristicChanged(
                    gatt: BluetoothGatt?,
                    characteristic: BluetoothGattCharacteristic?
                ) {
                    super.onCharacteristicChanged(gatt, characteristic)
                    if (characteristic == null) return
                    val sensorData = SensorData(value = characteristic.value)
                    emitter.onNext(sensorData)
                }
            }
            val gatt =  bleDevice.connectGatt(context,true, gattCallback)
            emitter.setCancellable {  gatt.disconnect() }
        }
    }

    private fun convertFromInteger(i: Int): UUID {
        val MSB = 0x0000000000001000L
        val LSB = -0x7fffff7fa064cb05L
        val value = (i and -0x1).toLong()
        return UUID(MSB or (value shl 32), LSB)
    }


}