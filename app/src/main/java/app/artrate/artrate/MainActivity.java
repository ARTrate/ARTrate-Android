package app.artrate.artrate;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.illposed.osc.OSCMessage;
import com.illposed.osc.OSCPortOut;
import com.polidea.rxandroidble2.RxBleClient;
import com.polidea.rxandroidble2.RxBleDevice;
import com.polidea.rxandroidble2.internal.RxBleLog;
import com.polidea.rxandroidble2.scan.ScanSettings;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import io.reactivex.disposables.Disposable;

/**
 * This activity handles all the bluetooth things
 */
public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int LOCATION_PERMISSION_REQUEST = 738;
    private static final int HEART_RATE_SERVICE = 0x180D;
    private static final int HEART_RATE_MEASUREMENT_CHAR = 0x2A37;
    private static final int RESPIRATION_RATE_UUID_CHAR = 0x2222;
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
    private String rawRrData;
    private String rrString;
    public ListView btList;
    public ArrayAdapter<RxBleDevice> deviceList;
    public ArrayList<RxBleDevice> bleDevices = new ArrayList<>();
    private int selectedDevice = -1;
    private boolean shouldSend;
    private String ip;
    private OSCPortOut oscPort;
    TextView bpmText;

    public class ArtrateRes {
        byte[] heartRate;
        byte[] respirationRate;

        ArtrateRes(byte[] hr, byte[] rr) {
            this.heartRate = hr;
            this.respirationRate = rr;
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
        if (BA == null) {
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
     *
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
     * Extracts the HR from the received bytes from the BT device
     *
     * @param bytes received bytearray
     * @return heartrate
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    private int extractHRfromBytes(byte[] bytes) {
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
            return 0 + two << 8 + one;
        } else {
            // 8 bit uint meas
            byte bit8meas = bytes[1];
            return Byte.toUnsignedInt(bit8meas);
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
                    if (tmp.getName() != null && !bleDevices.contains(tmp)) {
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
     *
     * @param view
     */
    public void OnScanOrConnectClick(View view) {
        if (!scanningForDevices) {
            scanningForDevices = true;
            scanBTDevices();
            scanOrConnectButton.setText("scanning...");
            scanOrConnectButton.setClickable(false);
        } else {
            scanSubscription.dispose();
            //readHRData();
            new BluetoothTask().execute();
            startOsc();
        }
    }

    /**
     * Starts the OSC Client
     */
    public void startOsc() {
        shouldSend = true;
        EditText ipField = findViewById(R.id.inputIP);
        ip = ipField.getText().toString();
        setContentView(R.layout.activity_osc);
        bpmText = findViewById(R.id.BPM);
        try {
            oscPort = new OSCPortOut(Inet4Address.getByName(ip), 5005);
        } catch (SocketException e) {
            e.printStackTrace();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        new BpmTask().execute();
    }

    /**
     * STop the OSC client
     */
    public void stopOsc(View view) {
        shouldSend = false;
        setContentView(R.layout.activity_main);
    }

    /**
     * when app is closed
     */
    @Override
    public void onDestroy() {
        if (deviceSubscription != null) {
            deviceSubscription.dispose();
        }
        if (scanSubscription != null) {
            scanSubscription.dispose();
        }
        super.onDestroy();
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

    /**
     * Class for asynchronous bluetooth operation
     */
    public class BluetoothTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... voids) {
            if (selectedDevice >= 0) {
                RxBleDevice device = bleDevices.get(selectedDevice);
                if (device.getName() == "ARTrate") {
                    // our sensor is detected --> subscribe to rr values
                    deviceSubscription = device.establishConnection(false)
                            .flatMap(rxBleConnection -> rxBleConnection.setupNotification(convertFromInteger(RESPIRATION_RATE_UUID_CHAR))
                                    .doOnNext(notificationObservable -> {
                                        Log.d("testtesttest", notificationObservable.toString());
                                    })
                                    .flatMap(notificationObservable -> notificationObservable)).subscribe(
                                    bytes -> {
                                        rrString = buildRrString(bytes);
                                        //Log.d(">>> data from device ", bytes.toString());
                                        for (byte b : bytes) {
                                            Log.d("current hr", rrString);
                                        }
                                    },
                                    throwable -> {
                                        Log.e("Error reading HR", throwable.getMessage());
                                    }
                            );
                } else {
                    // connect to an normal HR sensor
                    deviceSubscription = device.establishConnection(false)
                            .flatMap(rxBleConnection -> rxBleConnection.setupNotification(convertFromInteger(HEART_RATE_MEASUREMENT_CHAR))
                                    .doOnNext(notificationObservable -> {
                                        Log.d("testtesttest", notificationObservable.toString());
                                    })
                                    .flatMap(notificationObservable -> notificationObservable)).subscribe(
                                    bytes -> {
                                        hr = extractHRfromBytes(bytes);
                                        //Log.d(">>> data from device ", bytes.toString());
                                        for (byte b : bytes) {
                                            Log.d("current hr", " " + hr);
                                        }
                                    },
                                    throwable -> {
                                        Log.e("Error reading HR", throwable.getMessage());
                                    }
                            );
                }
            }
            return null;
        }
    }


    private String buildRrString(byte[] bytes) {
        int[] xs = new int[3];
        int[] ys = new int[3];
        int[] zs = new int[3];

        for (int i = 0; i < 6; i += 2) {
            xs[i / 2] = (bytes[i + 1] << 8) | bytes[i];
        }

        for (int i = 6; i < 12; i += 2) {
            ys[i / 2] = (bytes[i + 1] << 8) | bytes[i];
        }

        for (int i = 12; i < 18; i += 2) {
            zs[i / 2] = (bytes[i + 1] << 8) | bytes[i];
        }

        String result = xs[0] + "," + ys[0] + "," + zs[0] + "," +
                xs[1] + "," + ys[1] + "," + zs[1] + "," +
                xs[2] + "," + ys[2] + "," + zs[2] + ",";
        return result;
    }

    /**
     * Class for asynchronous network operations sending Heart Rate Data
     */
    private class BpmTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... voids) {
            while (shouldSend) {
//                int bpm = ThreadLocalRandom.current().nextInt(50, 121);
                Object[] payload = new Object[]{hr};
                OSCMessage message = new OSCMessage("/bpm", Arrays.asList(payload));
                try {
                    oscPort.send(message);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                bpmText.setText(Integer.toString(hr));
                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException e) {
                    break;
                }
            }
            return null;
        }
    }

    /**
     * Class for asynchronous network operations sending RR Data
     */
    private class RrTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... voids) {
            while (shouldSend) {
                Object[] payload = new Object[]{rawRrData};
                OSCMessage message = new OSCMessage("/RR", Arrays.asList(payload));
                try {
                    oscPort.send(message);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                try {
                    // 60 miliseconds for a sample rate of 50 and 3 data points per String
                    TimeUnit.MILLISECONDS.sleep(60);
                } catch (InterruptedException e) {
                    break;
                }
            }
            return null;
        }
    }
}
