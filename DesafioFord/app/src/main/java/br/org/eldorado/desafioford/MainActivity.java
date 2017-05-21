package br.org.eldorado.desafioford;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;

import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.motorola.mod.ModManager;

import br.org.eldorado.desafioford.R;

public class MainActivity extends AppCompatActivity {

    private Button buttonUnlock;
    private Button buttonLock;


    private static String TAG = "DesafioFord";
    private String mConnectedDeviceName = "";

    private BluetoothAdapter mBluetoothAdapter = null;
    private BluetoothService mChatService = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blinky);

        buttonLock = (Button) findViewById(R.id.buttonLock);
        buttonUnlock = (Button) findViewById(R.id.buttonUnlock);

        Log.i(TAG, "onCreate");

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mChatService = new BluetoothService(this, btHandler);

        connectBTDevice();

        buttonLock.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                connectBTDevice();
                sendBTMessage("hhhh\r");
            }
        });

        buttonUnlock.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                connectBTDevice();
                sendBTMessage("h\r");
            }
        });
    }


    /**
     * The Handler that gets information back from the BluetoothChatService
     */
    private final Handler btHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case Constants.MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case BluetoothService.STATE_CONNECTED:
                            Log.i(TAG, "Connected to " + mConnectedDeviceName);
                            break;
                        case BluetoothService.STATE_CONNECTING:
                            Log.i(TAG, "Connecting");
                            break;
                        case BluetoothService.STATE_LISTEN:
                        case BluetoothService.STATE_NONE:
                            Log.i(TAG, "BT not connected!");
                            break;
                    }
                    break;
                case Constants.MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;
                    // construct a string from the buffer
                    break;
                case Constants.MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    // construct a string from the valid bytes in the buffer
                    String readMessage = new String(readBuf, 0, msg.arg1);
                    break;
                case Constants.MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(Constants.DEVICE_NAME);
                    Toast.makeText(MainActivity.this, "Connected to "
                                + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    break;
                case Constants.MESSAGE_TOAST:
                        Toast.makeText(MainActivity.this, msg.getData().getString(Constants.TOAST),
                                Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };

    private void connectBTDevice() {

        if (mChatService.getState() != BluetoothService.STATE_CONNECTED) {
            // Get the device MAC address
            String address = "20:17:01:17:06:99";
            // Get the BluetoothDevice object
            BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
            // Attempt to connect to the device
            mChatService.connect(device, false);
        }
    }

    private void sendBTMessage(String message) {
        // Check that we're actually connected before trying anything
        if (mChatService.getState() != BluetoothService.STATE_CONNECTED) {
            Toast.makeText(this, "Not Connected", Toast.LENGTH_SHORT).show();
            return;
        }

        // Check that there's actually something to send
        if (message.length() > 0) {
            // Get the message bytes and tell the BluetoothChatService to write
            byte[] send = message.getBytes();
            mChatService.write(send);

        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

}
