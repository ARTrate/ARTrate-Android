package app.artrate.artrate;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;

import com.polidea.rxandroidble2.RxBleClient;
import com.polidea.rxandroidble2.RxBleDevice;
import com.polidea.rxandroidble2.scan.ScanFilter;
import com.polidea.rxandroidble2.scan.ScanSettings;

import java.util.ArrayList;
import java.util.UUID;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int LOCATION_PERMISSION_REQUEST = 738;
    private BluetoothAdapter BA;
    private RxBleClient rxBleClient;
    private Disposable scanSubscription;
    private Disposable gattSubscription;
    public byte[] hr;

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

    ListView btList;
    ArrayAdapter<RxBleDevice> deviceList;
    //ArrayAdapter<String> btNames;
    //ArrayList<String> deviceNames = new ArrayList<>();
    ArrayList<RxBleDevice> bleDevices = new ArrayList<>();
    int selectedDevice = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        BA = BluetoothAdapter.getDefaultAdapter();
        initBT();
        //btNames = new ArrayAdapter<>(this, android.R.layout.simple_list_item_single_choice, deviceNames);
        deviceList = new ArrayAdapter<RxBleDevice>(this, android.R.layout.simple_list_item_single_choice);
        btList = (ListView) findViewById(R.id.btList);
        btList.setAdapter(deviceList);
        btList.setOnItemClickListener((parent, view, position, id) -> {
            selectedDevice = position;
            Log.d("Selected Devise is", " " + selectedDevice);
            getHRData();
        });
        rxBleClient = rxBleClient.create(this);

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
                        //deviceNames.add(tmp.getName());
                        deviceList.add(tmp);
                    }
                },
                throwable -> {
                    Log.e("Error: ", throwable.getMessage());
                }
        );
    }

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

    private UUID convertFromInteger(int i) {

        final long MSB = 0x0000000000001000L;
        final long LSB = 0x800000805f9b34fbL;
        long value = i & 0xFFFFFFFF;
        return new UUID(MSB | (value << 32), LSB);

    }

    public void getHRData(){
        if(selectedDevice >= 0) {
            /*gattSubscription = bleDevices.get(selectedDevice).establishConnection(false)
                    .flatMapSingle(rxBleConnection -> rxBleConnection.readCharacteristic(convertFromInteger(0x2A37)))
                    .subscribe(
                            characteristicValue -> {
                                Log.v("got val", characteristicValue.toString());
                                hr = characteristicValue;
                            },
                            throwable -> {
                                Log.e("Gatt Error", throwable.getMessage());
                            }
                    );*/
            gattSubscription = bleDevices.get(selectedDevice).establishConnection(false)
                    .flatMapSingle(rxBleConnection -> rxBleConnection.readCharacteristic(convertFromInteger(0x180D)))
                    .subscribe(
                            services -> {
                                Log.v("Offered Services", services.toString());

                            },
                            throwable -> {
                                Log.e("Gatt Error", throwable.getMessage());
                            }
                    );
        }
    }

    /* It is called when an activity completes.*/
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT) {
            initBT();
        }
    }

    public void OnConnectClick(View view) {
        scanSubscription.dispose();
        startOsc(view);
    }

    public void startOsc(View view) {
        EditText ipField = findViewById(R.id.inputIP);
        String ip = ipField.getText().toString();
        Intent intent = new Intent(MainActivity.this, OscActivity.class);
        intent.putExtra("IP", ip);
        startActivity(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case LOCATION_PERMISSION_REQUEST: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    return;
                } else {
                    System.exit(-1);
                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request.
        }
    }
}
