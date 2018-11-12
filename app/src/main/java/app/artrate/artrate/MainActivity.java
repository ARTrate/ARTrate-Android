package app.artrate.artrate;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;

import java.util.Set;

public class MainActivity extends AppCompatActivity {

    TextView btList;
    private static final int REQUEST_ENABLE_BT = 1;
    private BluetoothAdapter BA;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btList = (TextView) findViewById(R.id.btList);
        BA = BluetoothAdapter.getDefaultAdapter();
        CheckBluetoothState();
    }

    private void CheckBluetoothState() {
        // Checks for the Bluetooth support and then makes sure it is turned on
        // If it isn't turned on, request to turn it on
        // List paired devices
        if(BA==null) {
            btList.append("\nBluetooth NOT supported. Aborting.");
            return;
        } else {
            if (BA.isEnabled()) {

                // Listing paired devices
                Set<BluetoothDevice> devices = BA.getBondedDevices();
                for (BluetoothDevice device : devices) {
                    btList.append("\n  Device: " + device.getName() + ", " + device);
                }
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
}
