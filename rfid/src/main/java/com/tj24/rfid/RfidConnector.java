package com.tj24.rfid;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.rscja.deviceapi.RFIDWithUHFBluetooth;
import com.rscja.deviceapi.entity.UHFTAGInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static android.widget.Toast.LENGTH_SHORT;

/**
 * @Description: RFID管理者
 * @Createdtime:2019/3/20 16:04
 * @Author:TangJiang
 * @Version: V.1.0.0
 */

public class RfidConnector {
    
    public static final String TAG = RfidConnector.class.getSimpleName();
    public static final int DISCONNECTED = 98; //配对失败
    public static final int CONNECTED = 99; //配对成功
    public static final int START_INTENVAL =100;//开始扫描信息
    public static final int STOP_INTENVAL=101;//停止扫描信息
    public static final int GET_UHFINFO =102; //读取标签信息后

    public static final int FLAG_SUCCESS=103;//成功
    public static final int FLAG_FAIL=104;//失败

    //配对蓝牙设备的回调
    private ConnectCallBack connectCallBack;
    //读取信息的回调
    private InventoryCallBack inventoryCallBack;


    private BluetoothAdapter mBtAdapter = null;
    RFIDWithUHFBluetooth.StatusEnum mState = RFIDWithUHFBluetooth.StatusEnum.DISCONNECTED;
    public RFIDWithUHFBluetooth uhf = RFIDWithUHFBluetooth.getInstance();
    BTStatus btStatus;
    private Activity mContext;
    //是否正在读取
    public boolean reading = false;
    //是否配对
    public static boolean isConn = false;
    //识别标签的线程是否运行
    boolean isRuning = false;

    public boolean loopFlag = false;
    //是否退出
    public boolean isExit = false;

    public static final long SCAN_PERIOD = 10000; //10 seconds
    //声音
    com.tj24.rfid.Sounder sounder;
    //保存扫出来的信息
    Map<String,String> info = new HashMap<>();
    //单例获取
    public static  volatile RfidConnector mInStance;
    public static RfidConnector getInstance(){
        if(mInStance==null){
            synchronized (RfidConnector.class){
                if(mInStance==null){
                    mInStance = new RfidConnector();
                }
            }
        }
        return mInStance;
    }

    Handler handler = new Handler() {
        @Override
        public void handleMessage(final Message msg) {
            switch (msg.what){
                case CONNECTED :
                    mState= RFIDWithUHFBluetooth.StatusEnum.CONNECTED;
                    isConn=true;
                    Log.e(TAG,"连接成功!");
                    //延迟一秒回调给调用者
                    final BluetoothDevice device = (BluetoothDevice) msg.obj;
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            connectCallBack.connected(device);
                        }
                    },1000);
                    break;
                case DISCONNECTED:
                    isConn=false;
                    loopFlag = false;
                    reading=false;
                    mState= RFIDWithUHFBluetooth.StatusEnum.DISCONNECTED;
                    Log.e(TAG,"连接失败!");
                    connectCallBack.disconnected();
                    break;
                case STOP_INTENVAL:
                    if(msg.arg1==FLAG_SUCCESS) {
                        //停止成功
                    }else{
                        //停止失败
                        sounder.playSound(2);
                    }
                    break;
                case START_INTENVAL:
                    if(msg.arg1==FLAG_SUCCESS){
                        //开始读取标签成功
                    }else{
                        //开始读取标签失败
                        sounder.playSound(2);
                    }
                    break;
                case GET_UHFINFO: //读取到标签信息
                    String result = msg.obj + "";
                    String[] strs = result.split("@");
                    sounder.playSound(1);
                    //将读取到的标签信息返回
                    if(!info.containsKey(strs[0])){
                        info.put(strs[0],strs[0]);
                        if(inventoryCallBack!=null){
                            inventoryCallBack.getEpc(strs[0]);
                        }
                    }
                    break;
            }
        }
    };

    /**
     * 初始化
     * @param mContext
     */
    public void init(Activity mContext){
        this.mContext = mContext;
        sounder = new Sounder();
        btStatus  = new BTStatus();

        uhf.setBluetoothMode(true);
        uhf.init(mContext);
        uhf.setKeyEventCallback(new RFIDWithUHFBluetooth.KeyEventCallback() {
            @Override
            public void getKeyEvent(int keycode) {
                if(!isExit) {
                    startInventory(inventoryCallBack);
                }else {
                    stopInventory();
                }
            }
        });
        sounder.initSound(mContext);
    }

    /**
     * 释放资源
     */
    public void free(){
        isExit = true;
        isConn = false;
        loopFlag = false;
        isExit = false;
        isRuning = false;
        stopScan();
        stopInventory();
        sounder.freeSound();
    }


    //--------------------------搜寻/停止蓝牙-----------------------------------------
    /**
     * 开始扫描蓝牙设备
     * @param scanCallBack
     */
    public void scan(ScanCallBack scanCallBack){
        PermissionUtil.checkLocationEnable(mContext);

        if(reading) {
            return;
        }
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBtAdapter == null) {
            Toast.makeText(mContext, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            return;
        }
        if (mBtAdapter.isEnabled()) {
            if (!isConn){
                uhf.disconnect();
            }
            searchEq(scanCallBack);
        }else {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            mContext.startActivity(enableIntent);
        }
    }

    /**
     * 停止扫描设备
     */
    public void stopScan(){
        reading = false;
        deviceList.clear();
        uhf.stopScanBTDevices();
    }

    /**
     * 搜寻蓝牙设备
     * @param scanCallBack
     */

    List<BluetoothDevice> deviceList = new ArrayList<>();
    private void searchEq(final ScanCallBack scanCallBack) {
        // 只扫描 SCAN_PERIOD
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                reading = false;
                uhf.stopScanBTDevices();
            }
        }, SCAN_PERIOD);

        reading = true;
        uhf.scanBTDevices(new RFIDWithUHFBluetooth.ScanBTCallback() {
            @Override
            public void getDevices(final BluetoothDevice bluetoothDevice, final int rssi, byte[] bytes) {
                if(bluetoothDevice!=null && !TextUtils.isEmpty(bluetoothDevice.getName())){
                    boolean deviceFound = false;
                    for (BluetoothDevice listDev : deviceList) {
                        if (listDev.getAddress().equals(bluetoothDevice.getAddress())) {
                            deviceFound = true;
                            break;
                        }
                    }
                    if (!deviceFound) {
                        deviceList.add(bluetoothDevice);
                        scanCallBack.getDevices(bluetoothDevice,rssi);
                    }
                }
            }
        });
    }


    //--------------------------连接/断开蓝牙-----------------------------------------

    /**
     * 配对设备
     * @param deviceAddress
     * @param callBack
     */
    public void connect(String deviceAddress, ConnectCallBack callBack){
        Log.e(TAG,"connect=====>"+deviceAddress);
        this.connectCallBack = callBack;
        if(uhf.getConnectStatus()== RFIDWithUHFBluetooth.StatusEnum.CONNECTING) {
            Toast.makeText(mContext,"连接中....", LENGTH_SHORT).show();
        }
        else {
            uhf.connect(deviceAddress, btStatus);
        }
    }
    
    class BTStatus implements  RFIDWithUHFBluetooth.BTStatusCallback{
        @Override
        public void getStatus(final RFIDWithUHFBluetooth.StatusEnum statusEnum, final BluetoothDevice device) {
            if(statusEnum== RFIDWithUHFBluetooth.StatusEnum.CONNECTED){
                Message msg = handler.obtainMessage();
                msg.what = CONNECTED;
                msg.obj = device;
                handler.sendMessage(msg);
            }else if(statusEnum== RFIDWithUHFBluetooth.StatusEnum.DISCONNECTED){
                handler.sendEmptyMessage(DISCONNECTED);
            }
        }
    }

    //--------------------------识别标签-----------------------------------------
    /**
     * 开始识别
     * @param
     */
    public void startInventory(InventoryCallBack callBack){
        if(isRuning) {
            return;
        }
        isRuning=true;
        this.inventoryCallBack = callBack;
        new TagThread().start();
    }

    class TagThread extends Thread {
        @Override
        public void run() {
            Message msg = handler.obtainMessage(START_INTENVAL);
            if (uhf.startInventoryTag()) {
                loopFlag = true;
                reading=true;
                msg.arg1=FLAG_SUCCESS;
            } else {
                msg.arg1=FLAG_FAIL;
            }
            handler.sendMessage(msg);
            isRuning=false;//执行完成设置成false
            while (loopFlag) {
                getUHFInfo();
            }
        }
    }

    /**
     * 获取信息
     * @return
     */
    public synchronized  boolean getUHFInfo(){
        String strResult="";
        ArrayList<UHFTAGInfo> list = uhf.readTagFromBuffer();
        if (list != null) {
            for (int k = 0; k < list.size(); k++) {
                UHFTAGInfo info = list.get(k);
                Message msg = handler.obtainMessage(GET_UHFINFO);
                msg.obj = strResult + "EPC:" + info.getEPC() + "@N/A";
                handler.sendMessage(msg);
            }
            if (list.size() > 0) {
                return true;
            }
        } else {
            return false;
        }
        return false;
    }

    /**
     * 停止识别
     */
    public void stopInventory() {
        isRuning = false;
        if (loopFlag) {
            RFIDWithUHFBluetooth.StatusEnum statusEnum=uhf.getConnectStatus();
            Message msg=handler.obtainMessage(STOP_INTENVAL);
            boolean result= uhf.stopInventoryTag();
            if (result || statusEnum== RFIDWithUHFBluetooth.StatusEnum.DISCONNECTED) {
                loopFlag = false;
                msg.arg1=FLAG_SUCCESS;
            } else {
                msg.arg1=FLAG_FAIL;
            }
            if(statusEnum== RFIDWithUHFBluetooth.StatusEnum.CONNECTED) {
                //在连接的情况下，结束之后继续接收未接收完的数据
                //getUHFInfo();
                getUHFInfoEx();
            }
            if (result || statusEnum== RFIDWithUHFBluetooth.StatusEnum.DISCONNECTED) {
                reading =false;
            }
            handler.sendMessage(msg);
        }
    }

    private synchronized  void getUHFInfoEx(){
        String strResult="";
        Log.d("DeviceAPI_UHFReadTa","readTagFromBuffer beigin");
        long begintime= System.currentTimeMillis();
        while (!isExit) {
            ArrayList<UHFTAGInfo> list = uhf.readTagFromBuffer();
            //    Log.d("DeviceAPI_nRFUART_ZP","readTagFromBuffer end");
            if (list != null) {
                for (int k = 0; k < list.size() && !isExit; k++) {
                    UHFTAGInfo info = list.get(k);
                    Message msg = handler.obtainMessage(GET_UHFINFO);
                    msg.obj = strResult + "EPC:" + info.getEPC() + "@N/A";
                    handler.sendMessage(msg);
                }
                if (list.size() == 0) {
                    if(System.currentTimeMillis()-begintime>1000*1) {
                        return;
                    }
                }
            } else {
                if(System.currentTimeMillis()-begintime>1000*1) {
                    return;
                }
            }
        }
    }
}
