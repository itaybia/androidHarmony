package com.harmony.client;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.SmackAndroid;

import android.app.Activity;
import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ListAdapter;
import android.widget.ListView;

import com.harmony.api.Authentication;
import com.harmony.api.Client;
import com.harmony.api.Client.HarmonyActivity;
import com.harmony.api.Client.HarmonyDevice;
import com.harmony.api.ClientBase.HubClientSessionLoggedInListener;
import com.harmony.api.ClientBase.onCommandTaskFinishedListener;
import com.logitech.harmonyultimate.R;

public class MainActivity extends Activity implements OnClickListener, OnTouchListener, HubClientSessionLoggedInListener, onCommandTaskFinishedListener,
        ConnectionListener {

    private static final String        TAG               = "MainActivity";

    private Authentication             auth;
    private Client                     hubCommandsClient = null;
    private ArrayList<HarmonyActivity> activityList      = new ArrayList<HarmonyActivity>();
    private ArrayList<HarmonyDevice>   deviceList        = new ArrayList<HarmonyDevice>();
    private String                     currentActivityId = "";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.home_layout);

        Button volUpBtn = (Button) findViewById(R.id.volumeUpButton);
        Button volDownBtn = (Button) findViewById(R.id.volumeDownButton);
        Button channelUpBtn = (Button) findViewById(R.id.channelUpButton);
        Button channelDownBtn = (Button) findViewById(R.id.channelDownButton);
        volUpBtn.setOnTouchListener(this);
        volDownBtn.setOnTouchListener(this);
        channelUpBtn.setOnTouchListener(this);
        channelDownBtn.setOnTouchListener(this);

        ImageButton connectBtn = (ImageButton) findViewById(R.id.connectButton);
        ImageButton disconnectBtn = (ImageButton) findViewById(R.id.disconnectButton);
        ImageButton getConfigBtn = (ImageButton) findViewById(R.id.getConfigButton);
        ImageButton refreshConfigBtn = (ImageButton) findViewById(R.id.refreshConfigButton);
        connectBtn.setOnClickListener(this);
        disconnectBtn.setOnClickListener(this);
        getConfigBtn.setOnClickListener(this);
        refreshConfigBtn.setOnClickListener(this);

        ListView activityListView = (ListView) findViewById(R.id.activitylistView);
        activityListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        activityListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            //start the activity corresponding to the list item that was clicked
            @Override
            public void onItemClick(AdapterView<?> parent, final View view, int position, long id) {
                final HarmonyActivity item = (HarmonyActivity) parent.getItemAtPosition(position);
                if (!currentActivityId.equals(item.getActivityId())) {
                    hubCommandsClient.sendStartActivityCommandToHub(MainActivity.this, item.getActivityId());
                    currentActivityId = item.getActivityId();
                }
            }
        });

        disableCommandButtons();

        auth = new Authentication();

        refreshConfigurationFromString(readFromFile("config.txt"));
        //logInToClient();
    }

    /**
     * onTouch event for buttons that have commands that need to calculate the time between press and release
     */
    @Override
    public boolean onTouch(View v, MotionEvent event) {

        if (hubCommandsClient == null) {
            Log.e(TAG, "onClick - Client is not connected");
            return false;
        }

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            disableCommandButtons(v.getId());

            switch (v.getId()) {
                case R.id.volumeUpButton:
                    hubCommandsClient.sendButtonPressedCommandToHub(null, 0, "16107636", "VolumeUp");
                    break;
                case R.id.volumeDownButton:
                    hubCommandsClient.sendButtonPressedCommandToHub(null, 0, "16107636", "VolumeDown");
                    break;
                case R.id.channelUpButton:
                    hubCommandsClient.sendButtonPressedCommandToHub(null, 0, "16107637", "ChannelUp");
                    break;
                case R.id.channelDownButton:
                    hubCommandsClient.sendButtonPressedCommandToHub(null, 0, "16107637", "ChannelDown");
                    break;
            }
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            disableCommandButtons();

            switch (v.getId()) {
                case R.id.volumeUpButton:
                    hubCommandsClient.sendButtonReleasedCommandToHub(this, 0, "16107636", "VolumeUp");
                    break;
                case R.id.volumeDownButton:
                    hubCommandsClient.sendButtonReleasedCommandToHub(this, 0, "16107636", "VolumeDown");
                    break;
                case R.id.channelUpButton:
                    hubCommandsClient.sendButtonReleasedCommandToHub(this, 0, "16107637", "ChannelUp");
                    break;
                case R.id.channelDownButton:
                    hubCommandsClient.sendButtonReleasedCommandToHub(this, 0, "16107637", "ChannelDown");
                    break;
            }
        }

        return true;
    }

    /**
     * onClick event for buttons that only do a single command with no time relation 
     */
    @Override
    public void onClick(View v) {

        switch (v.getId()) {
            case R.id.connectButton:
                disableCommandButtons();
                logInToClient();
                break;
            case R.id.disconnectButton:
                //TODO - add ability to disconnect the GuestSessionTokenTask if it is stuck on trying to connect
                connectionClosed();
                break;
            case R.id.getConfigButton:
                hubCommandsClient.sendGetConfigurationCommandToHub(this);
                break;
            case R.id.refreshConfigButton:
                refreshConfigurationFromString(readFromFile("config.txt"));
                break;
            default:
                Log.e(TAG, "onClick - unknown button clicked");
        }
    }

    @Override
    public void onCommandTaskFInished(String commandName, String result) {
        Log.i(TAG, "onCommandTaskFInished for command: " + commandName);
        enableCommandButtons();

        if (commandName.equals("config")) {
            handleGetConfigurationFinished(result);
        } else if (commandName.equals("getCurrentActivity")) {
            handleGetCurrentActivityFinished(result);
        }
    }

    private void handleGetConfigurationFinished(String result) {
        //read the whole configuration into the corresponding array lists 
        if (!refreshConfigurationFromString(result)) {
            return;
        }

        //use the data in the array lists which actually all we currently want from the configuration data to save a minimal JSON configuration file
        String shortConfigString = Client.createShortConfigFile(activityList, deviceList);
        if (null != shortConfigString) {
            writeToFile("config.txt", shortConfigString);
        }
    }

    private void handleGetCurrentActivityFinished(String result) {
        String curActivity = result.substring(result.indexOf('=') + 1);

        ListView activityListView = (ListView) findViewById(R.id.activitylistView);
        ListAdapter adapter = activityListView.getAdapter();
        for (int i = 0; i < adapter.getCount(); i++) {
            HarmonyActivity harmonyActivity = (HarmonyActivity) adapter.getItem(i);
            if (harmonyActivity.getActivityId().equals(curActivity)) {
                activityListView.setItemChecked(i, true);
                currentActivityId = curActivity;
                break;
            }
        }
    }

    /**
     * parse the JSON configuration string into array lists and use them to fill the list views 
     * 
     * @param configString - a JSON string depicting the activities and devices in the harmony hub
     * @return true if parsing was successful, false otherwise
     */
    private boolean refreshConfigurationFromString(String configString) {
        if (!Client.ParseConfiguration(configString, activityList, deviceList)) {
            return false;
        }

        ListView activityListView = (ListView) findViewById(R.id.activitylistView);
        ArrayAdapter<HarmonyActivity> adapter = new ArrayAdapter<HarmonyActivity>(this, R.layout.listitem, activityList);
        activityListView.setAdapter(adapter);

        //TODO - add device list view

        return true;
    }

    private void logInToClient() {
        Log.i(TAG, "logInToClient");

        if (hubCommandsClient != null) {
            Log.e(TAG, "Client is alerady connected");
            return;
        }

        ImageButton connectBtn = (ImageButton) findViewById(R.id.connectButton);
        connectBtn.setEnabled(false);

        /* TODO - remove comment if for some reason you see that the hub is not responsive and might need to authenticate with the myharmony web service
        String loginToken = auth.getLoginToken(Configuration.myHarmonyUser, Configuration.myHarmonyPassword);
        if (loginToken == null) {
            return;
        }
        */
        String loginToken = "testtesttesttest";

        //this needs to be called before XMPP is used in the app
        SmackAndroid.init(this);

        //if on home wifi then use the home wifi IP and port, otherwise use the external ones
        String hubIP = Configuration.hubWifiAddress;
        int xmppPort = Configuration.hubWifiXmppPort;
        WifiManager wifiMgr = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        WifiInfo mWifi = wifiMgr.getConnectionInfo();
        String wifiName = mWifi.getSSID();
        if (wifiName == null || !wifiName.equals(Configuration.homeWifiSSID)) {
            hubIP = Configuration.hubInternetAddress;
            xmppPort = Configuration.hubInternetXmppPort;
        }

        hubCommandsClient = new Client(hubIP, xmppPort, this, this);
        //if (!auth.getSessionToken(loginToken, "192.168.2.128", hubCommandsClient)) {
        if (!auth.getSessionToken(loginToken, hubIP, xmppPort, hubCommandsClient)) {
            connectBtn.setEnabled(true);
        }
    }

    @Override
    public void hubClientSessionLoggedIn(boolean success) {
        Log.i(TAG, "hubClientSessionLoggedIn");
        if (success) {
            ImageButton connectBtn = (ImageButton) findViewById(R.id.connectButton);
            connectBtn.setEnabled(false);
            hubCommandsClient.sendGetCurrentActivityCommandToHub(this);
        } else {
            connectionClosed();
        }
    }

    private void disableCommandButtons() {
        disableCommandButtons(0);
    }

    private void disableCommandButtons(int except) {
        Button volUpBtn = (Button) findViewById(R.id.volumeUpButton);
        Button volDownBtn = (Button) findViewById(R.id.volumeDownButton);
        Button chanUpBtn = (Button) findViewById(R.id.channelUpButton);
        Button chanDownBtn = (Button) findViewById(R.id.channelDownButton);
        volUpBtn.setEnabled(false);
        volDownBtn.setEnabled(false);
        chanUpBtn.setEnabled(false);
        chanDownBtn.setEnabled(false);

        Button btn = (Button) findViewById(except);
        if (null != btn) {
            btn.setEnabled(true);
        }
    }

    private void enableCommandButtons() {
        Button volUpBtn = (Button) findViewById(R.id.volumeUpButton);
        Button volDownBtn = (Button) findViewById(R.id.volumeDownButton);
        Button chanUpBtn = (Button) findViewById(R.id.channelUpButton);
        Button chanDownBtn = (Button) findViewById(R.id.channelDownButton);
        volUpBtn.setEnabled(true);
        volDownBtn.setEnabled(true);
        chanUpBtn.setEnabled(true);
        chanDownBtn.setEnabled(true);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Hub Client Connection callback functions
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void connectionClosed() {
        Log.i(TAG, "connectionClosed");
        if (hubCommandsClient != null) {
            Client tempClient = hubCommandsClient;
            hubCommandsClient = null;
            tempClient.disconnectFromHub();
        }

        auth.disconnect();

        ImageButton connectBtn = (ImageButton) findViewById(R.id.connectButton);
        connectBtn.setEnabled(true);
        disableCommandButtons();

        ListView activityListView = (ListView) findViewById(R.id.activitylistView);
        int checkedItemIndex = activityListView.getCheckedItemPosition();
        if (checkedItemIndex >= 0 && checkedItemIndex < activityListView.getCount()) {
            activityListView.setItemChecked(checkedItemIndex, false);
            currentActivityId = "";
        }
    }

    @Override
    public void connectionClosedOnError(Exception arg0) {
        connectionClosed();
    }

    @Override
    public void reconnectingIn(int arg0) {
    }

    @Override
    public void reconnectionFailed(Exception arg0) {
        connectionClosed();
    }

    @Override
    public void reconnectionSuccessful() {
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // android file handling functions
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private void writeToFile(String fileName, String string) {
        FileOutputStream fos = null;
        try {
            fos = openFileOutput(fileName, Context.MODE_PRIVATE);
            fos.write(string.getBytes());
        } catch (FileNotFoundException fnfe) {
            Log.e(TAG, "writeToFile FileNotFoundException - " + fnfe.getMessage());
            fnfe.printStackTrace();
        } catch (IOException ioe) {
            Log.e(TAG, "writeToFile IOException - " + ioe.getMessage());
            ioe.printStackTrace();
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    Log.e(TAG, "writeToFile Exception while closing file - " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }

    private String readFromFile(String fileName) {
        FileInputStream fis = null;
        BufferedReader br = null;
        StringBuilder sb = new StringBuilder();
        try {
            fis = openFileInput(fileName);

            InputStreamReader isr = new InputStreamReader(fis);
            br = new BufferedReader(isr);
            String line = "";
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        } catch (FileNotFoundException fnfe) {
            Log.e(TAG, "writeToFile FileNotFoundException - " + fnfe.getMessage());
            fnfe.printStackTrace();
        } catch (IOException ioe) {
            Log.e(TAG, "writeToFile IOException - " + ioe.getMessage());
            ioe.printStackTrace();
        } finally {
            try {
                if (br != null) {
                    br.close();
                }
                if (fis != null) {
                    fis.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "writeToFile Exception while closing file - " + e.getMessage());
                e.printStackTrace();
            }
        }

        return sb.toString();
    }
}
