package app.artrate.artrate;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_ENABLE_BT = 1;
    private BluetoothAdapter BA;

    ListView btList;
    ArrayAdapter<BluetoothDevice> btDevices;
    ArrayAdapter<String> btNames;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        BA = BluetoothAdapter.getDefaultAdapter();
        CheckBluetoothState();
    }

    private void CheckBluetoothState() {
        // Checks for the Bluetooth support and then makes sure it is turned on
        // If it isn't turned on, request to turn it on
        // List paired devices
        if(BA==null) {
            return;
        } else {
            if (BA.isEnabled()) {

                // Listing paired devices
                Set<BluetoothDevice> devices = BA.getBondedDevices();
                ArrayList<BluetoothDevice> deviceList = new ArrayList<BluetoothDevice>();
                ArrayList<String> deviceNames = new ArrayList<String>();
                deviceList.addAll(devices);

                btDevices = new ArrayAdapter<BluetoothDevice>(this, android.R.layout.simple_list_item_single_choice, deviceList);

                for (BluetoothDevice device : devices) {
                   // btText.append("\n  Device: " + device.getName() + ", " + device);
                    deviceNames.add(device.getName());
                }
                btNames = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, deviceNames);

                btList = (ListView) findViewById(R.id.btList);
                btList.setAdapter(btDevices);

            } else {
                //Prompt user to turn on Bluetooth
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }
    }

    /* It is called when an activity completes.*/
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT) {
            CheckBluetoothState();
        }
    }

    public void startOsc(View view) {
        Intent intent = new Intent(MainActivity.this, OscActivity.class);
        startActivity(intent);
    }
}
