package com.tj24.rfid;

import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class DeviceActivity extends AppCompatActivity implements EqAdapter.OnItemClickListner{
    Switch aSwitch;
    RecyclerView rcEq;
    LinearLayout connected;
    TextView tvName;
    TextView tvAddress;
    TextView tvInfo;
    Button btnInventory;
    private LinearLayoutManager linearLayoutManager;
    EqAdapter mAdapter;
    List<BluetoothDevice> devices = new ArrayList<>();

    private boolean mScanning;
    RfidConnector mConector;

    private boolean isIntevory = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device);
        aSwitch = (Switch) findViewById(R.id.switch_search);
        rcEq = (RecyclerView) findViewById(R.id.rc_eq);
        tvAddress = (TextView) findViewById(R.id.tv_address);
        tvName = (TextView) findViewById(R.id.tv_name);
        connected = (LinearLayout) findViewById(R.id.connected);
        tvInfo = (TextView) findViewById(R.id.tv_info);
        btnInventory = (Button) findViewById(R.id.btn_inventory);

        mConector = RfidConnector.getInstance();
        mConector.init(this);

        connected.setVisibility(View.GONE);
        linearLayoutManager = new LinearLayoutManager(this);
        mAdapter = new EqAdapter(this, devices);
        mAdapter.setOnItemClickListner(this);
        rcEq.setLayoutManager(linearLayoutManager);
        rcEq.setAdapter(mAdapter);

        if (aSwitch.isChecked()) {
            startScan();
        }
        aSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    startScan();
                } else {
                    tvInfo.setText("");
                    connected.setVisibility(View.GONE);
                    mScanning = false;
                    isIntevory = false;
                    mConector.free();
                    devices.clear();
                    mAdapter.notifyDataSetChanged();
                }
            }
        });

        btnInventory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isIntevory) {
                    isIntevory = false;
                    btnInventory.setText("开始读卡");
                    mConector.stopInventory();
                } else {
                    isIntevory = true;
                    btnInventory.setText("停止读卡");
                    mConector.startInventory(new InventoryCallBack() {
                        @Override
                        public void getEpc(String epc) {
                            tvInfo.append(epc + "\n");
                        }
                    });
                }
            }
        });
    }

    private void startScan() {
        if (mScanning) {
            aSwitch.setChecked(false);
            Toast.makeText(this, "正在扫描，请稍后再试！", Toast.LENGTH_SHORT).show();
        } else {
            mScanning = true;
            mConector.scan(new ScanCallBack() {
                @Override
                public void getDevices(BluetoothDevice bluetoothDevice, int rssi) {
                    devices.add(bluetoothDevice);
                    mAdapter.notifyDataSetChanged();
                }
            });
        }
    }

    @Override
    public void onItemClick(final BluetoothDevice device) {
        mConector.connect(device.getAddress(), new ConnectCallBack() {
            @Override
            public void connected(BluetoothDevice bluetoothDevice) {
                tvAddress.setText(bluetoothDevice.getAddress());
                tvName.setText(bluetoothDevice.getName());
                connected.setVisibility(View.VISIBLE);
            }

            @Override
            public void disconnected() {
                connected.setVisibility(View.GONE);
                Toast.makeText(DeviceActivity.this, "连接失败", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mConector.free();
    }
}
