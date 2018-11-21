package app.artrate.artrate;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

import com.illposed.osc.OSCMessage;
import com.illposed.osc.OSCPortOut;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class OscActivity extends AppCompatActivity {

    private OSCPortOut oscPort;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_osc);
        try {
            oscPort = new OSCPortOut(Inet4Address.getByName("192.168.178.28"), 5005);
        } catch (SocketException e) {
            e.printStackTrace();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
       // generateOscData();
    }

    /**
     * method for generating random bpm and sending it to the OSC server
     */
    private void generateOscData() {

        OSCMessage message;

        while (true) {
            int bpm = ThreadLocalRandom.current().nextInt(50, 121);
            message = new OSCMessage();
            message.setAddress("192.168.178.28");
            message.addArgument("/bpm");
            message.addArgument(bpm);
            try {
                oscPort.send(message);
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                TimeUnit.SECONDS.sleep(5);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void goBack(View view) {
        finish();
    }
}
