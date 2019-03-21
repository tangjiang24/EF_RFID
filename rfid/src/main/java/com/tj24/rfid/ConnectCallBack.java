package com.tj24.rfid;

import android.bluetooth.BluetoothDevice;

/**
 * Created by energy on 2019/3/20.
 */

public interface ConnectCallBack {
    void connected(BluetoothDevice bluetoothDevice);
    void disconnected();
}
