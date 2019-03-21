package com.tj24.rfid;

import android.bluetooth.BluetoothDevice;

/**
 * Created by energy on 2019/3/20.
 */

public interface ScanCallBack {
   void getDevices(final BluetoothDevice bluetoothDevice, final int rssi);
}
