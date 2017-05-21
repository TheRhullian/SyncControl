package com.vrmassoni.forcar;


import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import com.sdl.hellosdlandroid.R;
import com.vrmassoni.forcar.model.BluetoothDevice;

import android.view.View;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    BluetoothAdapter mBluetoothAdapter;
    ListView bluetoothList;
    ImageView imageBluetooth;
    TextView lblPairing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bluetoothList = (ListView) findViewById(R.id.listBluetooth);
        imageBluetooth = (ImageView) findViewById(R.id.ivBluetooth);
        lblPairing = (TextView) findViewById(R.id.lblPairing);

        ArrayAdapter<BluetoothDevice> adapter;
        ArrayList<BluetoothDevice> bluetoothDevices;

        this.mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (mBluetoothAdapter == null) {
            //show an alert showing the message that the device have no bluetooth
        }
        else {
            if (!mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, 1);
            }

            bluetoothDevices = this.findBluetoothDevices();

            adapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_list_item_1, bluetoothDevices);

            this.bluetoothList.setAdapter(adapter);

            this.imageBluetooth.setVisibility(View.INVISIBLE);
            this.lblPairing.setVisibility(View.INVISIBLE);
            this.bluetoothList.setVisibility(View.VISIBLE);
        }

    }

    private ArrayList<BluetoothDevice> findBluetoothDevices() {

        ArrayList<BluetoothDevice> bluetoothDevices = new ArrayList<>();
        BluetoothDevice bluetoothDevice;

        Set<android.bluetooth.BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();

        if (pairedDevices.size() > 0) {

            for (android.bluetooth.BluetoothDevice device : pairedDevices) {
                String deviceName = device.getName();
                String deviceHardwareAddress = device.getAddress(); // MAC address

                bluetoothDevice = new BluetoothDevice(deviceName, deviceHardwareAddress);

                bluetoothDevices.add(bluetoothDevice);
            }
        }
        return bluetoothDevices;
    }
}