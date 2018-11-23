package app.artrate.artrate;

import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.illposed.osc.OSCMessage;
import com.illposed.osc.OSCPortOut;

import org.w3c.dom.Text;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class OscActivity extends AppCompatActivity {

    private OSCPortOut oscPort;
    private boolean shouldSend;
    TextView bpmText;
    private String ip;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_osc);
        bpmText = (TextView) findViewById(R.id.BPM);
        Bundle extras = getIntent().getExtras();
        ip = extras.getString("IP");
        try {
            oscPort = new OSCPortOut(Inet4Address.getByName(ip), 5005);
        } catch (SocketException e) {
            e.printStackTrace();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        shouldSend = true;
        new OscTask().execute();
    }


    /**
     * Class for asynchronous network operations
     */
    private class OscTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... voids) {
            while (shouldSend) {
                int bpm = ThreadLocalRandom.current().nextInt(50, 121);
                Object[] payload = new Object[]{bpm};
                OSCMessage message = new OSCMessage("/bpm", Arrays.asList(payload));
                try {
                    oscPort.send(message);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                bpmText.setText(Integer.toString(bpm));
                try {
                    TimeUnit.SECONDS.sleep(5);
                } catch (InterruptedException e) {
                    break;
                }
            }
            return null;
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        shouldSend = false;
    }

    public void goBack(View view) {
        finish();
    }
}
