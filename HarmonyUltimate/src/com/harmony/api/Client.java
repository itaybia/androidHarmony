package com.harmony.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;

import org.jivesoftware.smack.ConnectionListener;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import android.util.Log;

import com.harmony.api.Authentication.GuestSessionTokenListener;

public class Client extends ClientBase implements GuestSessionTokenListener {
    private static final String TAG = "Client";

    public Client(String aHarmonyHubIP, int aXmppPort, HubClientSessionLoggedInListener hubClientSessionLoggedInListener,
            ConnectionListener connectionListenerDelegate) {
        super(aHarmonyHubIP, aXmppPort, hubClientSessionLoggedInListener, connectionListenerDelegate);
    }

    public void sendButtonPressedCommandToHub(onCommandTaskFinishedListener listener, long time, String deviceId, String buttonCommand) {
        Log.i(TAG, "sending volume up pressed command");
        sendCommandToHub("holdAction", buttonCommand, "status=press:action={\"type\"::\"IRCommand\",\"deviceId\"::\"" + deviceId + "\",\"command\"::\""
                + buttonCommand + "\"}:timestamp=" + time, CommandStatus.press, listener);
    }

    public void sendButtonReleasedCommandToHub(onCommandTaskFinishedListener listener, long time, String deviceId, String buttonCommand) {
        Log.i(TAG, "sending volume up released command");
        sendCommandToHub("holdAction", buttonCommand, "status=release:action={\"type\"::\"IRCommand\",\"deviceId\"::\"" + deviceId + "\",\"command\"::\""
                + buttonCommand + "\"}:timestamp=" + time, CommandStatus.release, listener);
    }

    public void sendStartActivityCommandToHub(onCommandTaskFinishedListener listener, String activityId) {
        Log.i(TAG, "sending startActivity command for id: " + activityId);
        sendCommandToHub("startActivity", "startActivity", "activityId=" + activityId + ":timestamp=0", null, listener);

        /*
              <iq type="startActivity" id="3580686812" from="757d218d-72ce-4be7-9ad4-af369434c5fd">
              <oa xmlns="connect.logitech.com" mime="vnd.logitech.harmony/vnd.logitech.harmony.engine?startActivity">
                  activityId=6932433:timestamp=0
              </oa>
          </iq>
        */
    }

    public void sendGetCurrentActivityCommandToHub(onCommandTaskFinishedListener listener) {
        Log.i(TAG, "sending get current activity command");
        sendCommandToHub("getCurrentActivity", "getCurrentActivity", "", null, listener);

        /*
            <iq type="get" id="782485497" from="757d218d-72ce-4be7-9ad4-af369434c5fd">
                <oa xmlns="connect.logitech.com" mime="vnd.logitech.harmony/vnd.logitech.harmony.engine?getCurrentActivity"/>
            </iq>
         */
    }

    public void sendGetConfigurationCommandToHub(onCommandTaskFinishedListener listener) {
        Log.i(TAG, "sending get configuration command");
        sendCommandToHub("config", "config", "", null, listener);
    }

    /**
     * takes a json formatted string that depicts the activities and devices of the harmony and parses them to activity and device objects
     * @param config - json formatted string that depicts the activities and devices of the harmony
     * @param activityList - array list containing harmony activity objects
     * @param deviceList - array list containing harmony device objects
     * @return true if parsed and created the lists successfully, false otherwise
     */
    public static boolean ParseConfiguration(String config, ArrayList<HarmonyActivity> activityList, ArrayList<HarmonyDevice> deviceList) {

        if (config == null || activityList == null || deviceList == null) {
            Log.e(TAG, "ParseConfiguration - bad parameters");
            return false;
        }

        activityList.clear();
        deviceList.clear();

        JSONParser parser = new JSONParser();
        Object jsonObj;
        try {
            jsonObj = parser.parse(config);
        } catch (ParseException e) {
            Log.e(TAG, "error parsing configuration");
            e.printStackTrace();
            return false;
        }
        JSONObject firstMap = (JSONObject) jsonObj;

        //extract the harmony activities objects
        JSONArray activitiesArray = (JSONArray) firstMap.get("activity");
        for (Object obj : activitiesArray) {
            JSONObject activity = (JSONObject) obj;
            HarmonyActivity ha = new HarmonyActivity();
            ha.label = activity.get("label").toString();
            ha.id = activity.get("id").toString();
            Object activityOrderObj = activity.get("activityOrder");
            if (activityOrderObj != null) {
                ha.activityOrder = Integer.parseInt(activityOrderObj.toString());
            } else {
                ha.activityOrder = -1;
            }
            activityList.add(ha);
        }

        //sort the activities in ascending order of activity order
        Collections.sort(activityList, new Comparator<HarmonyActivity>() {
            public int compare(HarmonyActivity one, HarmonyActivity other) {
                if (one.activityOrder == other.activityOrder) {
                    return 0;
                } else if (one.activityOrder >= other.activityOrder) {
                    return 1;
                } else {
                    return -1;
                }
            }
        });

        //extract the harmony devices objects
        JSONArray devicesArray = (JSONArray) firstMap.get("device");
        for (Object obj : devicesArray) {
            JSONObject device = (JSONObject) obj;
            HarmonyDevice hd = new HarmonyDevice();
            hd.label = device.get("label").toString();
            hd.id = device.get("id").toString();
            deviceList.add(hd);
        }

        return true;
    }

    /**
     * creates a json formatted string that depicts the activities and devices of the harmony from activity and device objects
     * @param activityList - array list containing harmony activity objects
     * @param deviceList - array list containing harmony device objects
     * @return the json string if successful, null otherwise
     */
    public static String createShortConfigFile(ArrayList<HarmonyActivity> activityList, ArrayList<HarmonyDevice> deviceList) {

        if (activityList == null || deviceList == null) {
            Log.e(TAG, "ParseConfiguration - bad parameters");
            return null;
        }

        JSONObject configJson = new JSONObject();

        JSONArray activityArray = new JSONArray();
        Iterator<HarmonyActivity> activityIterator = activityList.iterator();
        while (activityIterator.hasNext()) {
            activityArray.add(activityIterator.next().toJson());
        }

        JSONArray deviceArray = new JSONArray();
        Iterator<HarmonyDevice> deviceIterator = deviceList.iterator();
        while (deviceIterator.hasNext()) {
            deviceArray.add(deviceIterator.next().toJson());
        }

        configJson.put("activity", activityArray);
        configJson.put("device", deviceArray);

        return configJson.toJSONString();
    }

    //an object that holds the relevant information about a device configured in the harmony
    public static class HarmonyDevice {
        String id;
        String label;

        //used for showing in the list view
        public String toString() {
            return label;
        }

        public JSONObject toJson() {
            JSONObject deviceJson = new JSONObject();
            deviceJson.put("label", label);
            deviceJson.put("id", id);

            return deviceJson;
        }
    }

    //an object that holds the relevant information about an activity configured in the harmony
    public static class HarmonyActivity {
        String label;
        int    activityOrder;
        String id;

        //used for showing in the list view
        public String toString() {
            return label;
        }

        public JSONObject toJson() {
            JSONObject activityJson = new JSONObject();
            activityJson.put("label", label);
            activityJson.put("id", id);
            activityJson.put("activityOrder", activityOrder);

            return activityJson;
        }

        public String getActivityId() {
            return id;
        }
    }
}
