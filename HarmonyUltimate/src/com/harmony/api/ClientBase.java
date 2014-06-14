package com.harmony.api;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.jivesoftware.smack.ConnectionListener;

import android.os.AsyncTask;
import android.util.Log;

import com.harmony.api.Authentication.GuestSessionTokenListener;

public class ClientBase implements GuestSessionTokenListener {
    public enum CommandStatus {
        press, release
    }

    private static final String              TAG                           = "ClientBase";

    private HubClientSessionLoggedInListener clientSessionLoggedInDelegate = null;
    private hubLoginTask                     authenticatedSessionTokenTask = null;
    private hubCommandTask                   sendCommandTask               = null;
    private String                           harmonyHubIP                  = null;
    private int                              xmppPort                      = 5222;
    private String                           hubGuestToken                 = null;
    private JabberSmackAPI                   hubSessionConnection          = null;
    private ConnectionListener               connectionDelegate            = null;

    protected ClientBase(String aHarmonyHubIP, int aXmppPort, HubClientSessionLoggedInListener hubClientSessionLoggedInListener,
            ConnectionListener connectionListenerDelegate) {
        harmonyHubIP = aHarmonyHubIP;
        xmppPort = aXmppPort;
        clientSessionLoggedInDelegate = hubClientSessionLoggedInListener;
        connectionDelegate = connectionListenerDelegate;
    }

    private void loginToHub(String hubGuestSessionToken, String harmonyIP, int xmppPort) {

        Log.i(TAG, "loginToHub - start");
        if (authenticatedSessionTokenTask != null) {
            disconnectFromHub();
        }
        hubGuestToken = hubGuestSessionToken;
        authenticatedSessionTokenTask = new hubLoginTask();
        authenticatedSessionTokenTask.xmppPort = xmppPort;
        authenticatedSessionTokenTask.execute(harmonyIP, hubGuestSessionToken);
        Log.i(TAG, "loginToHub - end");
    }

    private class hubLoginTask extends AsyncTask<String, String, String> {
        public int xmppPort = 5222;

        @Override
        protected String doInBackground(String... params) {

            Log.i(TAG, "hubLoginTask - in background Start");

            if (isCancelled()) {
                return "Cancelled";
            }

            //resolve the IP from the DNS in case this is a hostname and not an IP
            String hubIP = params[0];
            try {
                InetAddress address = InetAddress.getByName(hubIP);
                if (address != null) {
                    hubIP = address.getHostAddress();
                }
            } catch (UnknownHostException e) {
                Log.e(TAG, "hubLoginTask - could not resolve hub IP: " + hubIP);
                return "Bad hub IP";
            }

            hubSessionConnection = new JabberSmackAPI(connectionDelegate, xmppPort);
            String result = hubSessionConnection.login(hubIP, params[1] + "@connect.logitech.com/gatorade.", params[1]);
            if (!result.equalsIgnoreCase("OK")) {
                Log.e(TAG, "hubLoginTask - login failed");
                hubSessionConnection = null;
            }
            Log.i(TAG, "hubLoginTask - in background End");
            return result;
        }

        @Override
        protected void onPostExecute(String result) {
            if (authenticatedSessionTokenTask == this) {
                authenticatedSessionTokenTask = null;
                if (clientSessionLoggedInDelegate != null) {
                    clientSessionLoggedInDelegate.hubClientSessionLoggedIn(result != null && result.equals("OK"));
                }
            }
            // execution of result of Long time consuming operation
        }
    }

    public interface HubClientSessionLoggedInListener {
        void hubClientSessionLoggedIn(boolean success);
    }

    //the client was given to the authentication object as a delegate. when it gets the guest login token from the hub it will try to log in to it.
    @Override
    public void GuestSessionTokenAcquired(String guestSessionToken) {
        if (guestSessionToken == null) {
            Log.e(TAG, "GuestSessionTokenAcquired - null guest session token");
            clientSessionLoggedInDelegate.hubClientSessionLoggedIn(false);
            return;
        }
        loginToHub(guestSessionToken, harmonyHubIP, xmppPort);
    }

    protected void sendCommandToHub(String aActionType, String aCommandName, String aCommand, CommandStatus aCommandStatus,
            onCommandTaskFinishedListener listener) {

        if (hubGuestToken == null || hubSessionConnection == null) {
            return;
        }

        hubCommandTask tempCmdTask = new hubCommandTask();
        tempCmdTask.onTaskFinishedlistener = listener;
        tempCmdTask.commandStatus = aCommandStatus;
        tempCmdTask.commandName = aCommandName;
        tempCmdTask.actionType = aActionType;
        tempCmdTask.command = aCommand;
        tempCmdTask.hubToken = hubGuestToken;

        if (sendCommandTask != null) {
            if (aCommandName.equals(sendCommandTask.commandName) && sendCommandTask.secondaryCommandTask == null
                    && sendCommandTask.commandStatus == CommandStatus.press && aCommandStatus == CommandStatus.release) {
                //the release command corresponding to the current press command has been issued while the press is being handled.
                //save the release command to be called when the press is finished.
                Log.i(TAG, "sendCommandToHub - setting secondary task");
                sendCommandTask.secondaryCommandTask = tempCmdTask;
            }
            return;
        }

        sendCommandTask = tempCmdTask;
        sendCommandTask.execute();
    }

    public interface onCommandTaskFinishedListener {
        public void onCommandTaskFInished(String commandName, String result);
    }

    /**
     * 
     * @author Owner
     * an asynctask that sends a command to the hub. when the task finishes, it may issue a secondary task for closing the first task.
     * this is used for the commands that have a pressed-release relationship, though that could generally be done through the button callbacks for button pressed/released.
     *
     */
    private class hubCommandTask extends AsyncTask<String, String, String> {

        public onCommandTaskFinishedListener onTaskFinishedlistener = null;
        public CommandStatus                 commandStatus          = null;
        public String                        actionType             = null;
        public String                        command                = null;
        public String                        commandName            = null;
        public hubCommandTask                secondaryCommandTask   = null;
        public String                        hubToken               = null;

        @Override
        protected String doInBackground(String... params) {
            Log.i(TAG, "hubCommandTask in background");
            if (isCancelled()) {
                return "Cancelled";
            }
            return hubSessionConnection.sendHubCommand(hubToken, actionType, command);
        }

        @Override
        protected void onPostExecute(String result) {
            Log.i(TAG, "hubCommandTask Post execute");
            if (sendCommandTask == this) {
                sendCommandTask = null;
            }

            if (secondaryCommandTask != null && !isCancelled()) {
                sendCommandTask = secondaryCommandTask;
                secondaryCommandTask.execute();
            } else if (onTaskFinishedlistener != null) {
                onTaskFinishedlistener.onCommandTaskFInished(commandName, result);
            }
            // execution of result of Long time consuming operation
        }
    }

    public void disconnectFromHub() {
        Log.i(TAG, "disconnectFromHub Start");

        if (hubSessionConnection != null) {
            JabberSmackAPI tempConnection = hubSessionConnection;
            hubSessionConnection = null;
            tempConnection.disconnect();
        }

        if (authenticatedSessionTokenTask != null) {
            Log.i(TAG, "disconnectFromHub - cancelling authenticatedSessionTokenTask");
            authenticatedSessionTokenTask.cancel(true);
            authenticatedSessionTokenTask = null;
        }

        if (sendCommandTask != null) {
            Log.i(TAG, "disconnectFromHub - cancelling sendCommandTask");
            sendCommandTask.cancel(true);
            sendCommandTask = null;
        }

        Log.i(TAG, "disconnectFromHub End");
    }
}
