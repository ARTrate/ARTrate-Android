package app.artrate.artrate;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;

import com.polidea.rxandroidble2.NotificationSetupMode;
import com.polidea.rxandroidble2.RxBleClient;
import com.polidea.rxandroidble2.RxBleConnection;
import com.polidea.rxandroidble2.RxBleDevice;
import com.polidea.rxandroidble2.internal.RxBleLog;
import com.polidea.rxandroidble2.scan.ScanSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.disposables.Disposable;

/**
 * This activity handles all the bluetooth things
 */
public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int LOCATION_PERMISSION_REQUEST = 738;
    private static final int HEART_RATE_SERVICE = 0x180D;
    private static final int HEART_RATE_MEASUREMENT_CHAR = 0x2A37;
    private static final int HEART_RATE_CHAR_CONFIG = 0x2902;
    private static final int HEART_RATE_CONTROL_PIONT_CHAR = 0x2A39;
    private static final int CLIENT_CHARACTERISTIC_CONFIG = 0x2902;
    private Button scanOrConnectButton;
    private boolean scanningForDevices = false;
    private BluetoothAdapter BA;
    private Disposable scanSubscription;
    private Disposable deviceSubscription;
    private Disposable gattSubscription;
    private int hr;
    public ListView btList;
    public ArrayAdapter<RxBleDevice> deviceList;
    public ArrayList<RxBleDevice> bleDevices = new ArrayList<>();
    private int selectedDevice = -1;

    public class HRResult {
        final byte[] hrService;
        final byte[] hrMeas;
        final byte[] hrControl;

        HRResult(byte[] hrService, byte[] hrMeas, byte[] hrControl){
            this.hrControl = hrControl;
            this.hrMeas = hrMeas;
            this.hrService = hrService;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        scanOrConnectButton = findViewById(R.id.button_scan_or_connect);
        BA = BluetoothAdapter.getDefaultAdapter();
        initBT();
        deviceList = new ArrayAdapter<>(this, android.R.layout.simple_list_item_single_choice);
        btList = findViewById(R.id.btList);
        btList.setAdapter(deviceList);
        btList.setOnItemClickListener((parent, view, position, id) -> {
            selectedDevice = position;
            Log.d("Selected Devise is", " " + selectedDevice);
            if (selectedDevice >= 0) {
                scanOrConnectButton.setText("connect");
                scanOrConnectButton.setClickable(true);
            }
        });

    }

    /**
     * Initialisation of the BT adapter, also looking if location permission granted
     */
    private void initBT() {
        // Checks for the Bluetooth support and then makes sure it is turned on
        // If it isn't turned on, request to turn it on
        // List paired devices
        if(BA==null) {
            return;
        } else {
            if (BA.isEnabled()) {
                // also check if there is the location permission granted (absolutely necessary for BT LE)
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    // location permission not granted --> request it
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_PERMISSION_REQUEST);
                }
                return;
            } else {
                //Prompt user to turn on Bluetooth
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }
    }

    /**
     * Converts an Gatt int to an UUID
     * @param i int to convert
     * @return UUID to use
     */
    private UUID convertFromInteger(int i) {

        final long MSB = 0x0000000000001000L;
        final long LSB = 0x800000805f9b34fbL;
        long value = i & 0xFFFFFFFF;
        return new UUID(MSB | (value << 32), LSB);

    }

    /**
     * Reads the data from the heart rate sensor
     */
    private void readHRData(){
        if(selectedDevice >= 0) {

            UUID clientCharacteristicConfigDescriptorUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
            deviceSubscription = bleDevices.get(selectedDevice).establishConnection(false)
                    .flatMap(rxBleConnection -> rxBleConnection.setupNotification(convertFromInteger(HEART_RATE_MEASUREMENT_CHAR))
                            .doOnNext(notificationObservable -> {
                                Log.d("testtesttest", notificationObservable.toString());
                            })
                            .flatMap(notificationObservable -> notificationObservable)).subscribe(
                            bytes -> {
                                hr = extractHRfromBytes(bytes);
                                //Log.d(">>> data from device ", bytes.toString());
                                for (byte b: bytes) {
                                    Log.d("current hr", " " + hr);
                                }
                            },
                            throwable -> {
                                Log.e("Error reading HR", throwable.getMessage());
                            }
                    );
        }
    }

    /**
     * Extracts the HR from the received bytes from the BT device
     * @param bytes received bytearray
     * @return heartrate
     */
    private int extractHRfromBytes (byte[] bytes) {
        // this is how the byte is defined https://www.bluetooth.com/specifications/gatt/viewer?attributeXmlFile=org.bluetooth.characteristic.heart_rate_measurement.xml
        if (bytes.length < 2) {
            // something is not good
            return -1;
        }

        //
        byte flags = bytes[0];
        if ((flags & 0x01) == 1) {
            // 16 bit uint meas
            byte one = bytes[1];
            byte two = bytes[2];
            return (int) (two<<8 + one);
        } else {
            // 8 bit uint meas
            byte bit8meas = bytes[1];
            return bit8meas;
        }
    }

    /**
     * Scans for nearby BT devices
     */
    private void scanBTDevices() {
        RxBleClient rxBleClient = RxBleClient.create(this);
        rxBleClient.setLogLevel(RxBleLog.DEBUG);
        scanSubscription = rxBleClient.scanBleDevices(
                new ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES).build()
        ).subscribe(
                rxBleScanResult -> {
                    RxBleDevice tmp = rxBleScanResult.getBleDevice();
                    Log.v("Dev found", tmp.getName());
                    if(tmp.getName() != null && !bleDevices.contains(tmp)){
                        bleDevices.add(tmp);
                        deviceList.add(tmp);
                        deviceList.notifyDataSetChanged();
                    }
                },
                throwable -> {
                    Log.e("Error: ", throwable.getMessage());
                }
        );
    }

    /* It is called when an activity completes.*/
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT) {
            initBT();
        }
    }

    /**
     * handles the button presses
     * @param view
     */
    public void OnScanOrConnectClick(View view) {
        if(!scanningForDevices){
            scanningForDevices = true;
            scanBTDevices();
            scanOrConnectButton.setText("scanning...");
            scanOrConnectButton.setClickable(false);
        } else {
            scanSubscription.dispose();
            readHRData();
            //startOsc(view);
        }
    }

    /**
     * Starts the OSC Activity
     * @param view
     */
    public void startOsc(View view) {
        EditText ipField = findViewById(R.id.inputIP);
        String ip = ipField.getText().toString();
        Intent intent = new Intent(MainActivity.this, OscActivity.class);
        intent.putExtra("IP", ip);
        startActivity(intent);
    }


    public void onStop () {
        if (deviceSubscription != null) {
            deviceSubscription.dispose();
        }
        if (scanSubscription!= null) {
            scanSubscription.dispose();
        }
        super.onStop();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case LOCATION_PERMISSION_REQUEST: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    return;
                } else {
                    System.exit(-1);
                }
                return;
            }
        }
    }
}
