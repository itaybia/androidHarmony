package com.harmony.api;

import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.PacketCollector;
import org.jivesoftware.smack.SASLAuthentication;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.PacketIDFilter;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.provider.ProviderManager;

import android.util.Log;

public class JabberSmackAPI {

    private static final String TAG                   = "JabberSmackAPI";

    private static final String SESSION_TOKEN_PREFIX  = "identity=";
    private static final String SESSION_TOKEN_POSTFIX = ":status=succeeded";
    private int                 XMPP_PORT             = 5222;
    private XMPPConnection      connection            = null;
    private ConnectionListener  connectionListener    = null;

    public JabberSmackAPI(ConnectionListener listener, int xmppPort) {
        connectionListener = listener;
        XMPP_PORT = xmppPort;
    }

    /**
     * 
     * @param harmonyIP - the IP of the harmony hub in the home network
     * @return "OK" if the connection was successful, error messages otherwise
     */
    public String login(String harmonyIP, String user, String password) {

        if (connection != null) {
            Log.e(TAG, "login - connection already running. aborting.");
            return "Connection running";
        }

        //should register providers before the connection is made
        if (ProviderManager.getInstance().getIQProvider(OaIqProvider.ELEMENT_NAME, OaIqProvider.NAMESPACE) == null) {
            ProviderManager.getInstance().addIQProvider(OaIqProvider.ELEMENT_NAME, OaIqProvider.NAMESPACE, new OaIqProvider());
        }

        ConnectionConfiguration cc = new ConnectionConfiguration(harmonyIP, XMPP_PORT);
        connection = new XMPPConnection(cc);
        connection.addConnectionListener(connectionListener);
        try {
            connection.connect();
        } catch (XMPPException e) {
            connection = null;
            Log.e(TAG, "login connect failed. " + e.getMessage());
            e.printStackTrace();
            return "XMPPException - connect failed";
        }

        try {
            // You have to put this code before you login
            SASLAuthentication.supportSASLMechanism("PLAIN", 0);
            connection.login(user, password);
            // See if you are authenticated
            System.out.println(connection.isAuthenticated());
            return connection.isAuthenticated() ? "OK" : "Not Authenticated";
        } catch (XMPPException e) {
            connection.disconnect();
            connection = null;
            Log.e(TAG, "login failed. " + e.getMessage());
            e.printStackTrace();
            return "XMPPException - login failed";
        }
    }

    /**
     * 
     * @param loginToken - the HTTPS login token
     * @return - the session ID from the hub, or null if failed
     */
    public String requestSessionId(String loginToken) {
        Log.i(TAG, "requestSessionId Start - loginToken = " + loginToken);

        IQ iq = new sessionTokenPacket(loginToken);
        iq.setFrom("guest");
        iq.setType(IQ.Type.GET);

        String packetId = iq.getPacketID();
        PacketCollector collector = connection.createPacketCollector(new PacketIDFilter(packetId));

        connection.sendPacket(iq);

        OaIq response = (OaIq) collector.nextResult(5000);
        collector.cancel();
        disconnect();

        if (null == response || response.GetData() == null) {
            Log.e(TAG, "requestSessionId - Response is null");
            return null;
        }

        //[truncated] <![CDATA[serverIdentity=757d218d-72ce-4be7-9ad4-af369434c5fd:hubId=97:identity=757d218d-72ce-4be7-9ad4-af369434c5fd:status=succeeded:protocolVersion={XMPP="1.0", HTTP="1.0", RF="1.0"}:hubProfiles={Harmony="2.0"}:productId=Pimen
        int prefixIndex = response.GetData().indexOf(SESSION_TOKEN_PREFIX, 0);
        if (prefixIndex < 0) {
            Log.e(TAG, "requestSessionId - couldn't find \"" + SESSION_TOKEN_PREFIX + "\" in the response");
            return null;
        }

        int postfixIndex = response.GetData().indexOf(SESSION_TOKEN_POSTFIX, prefixIndex + SESSION_TOKEN_PREFIX.length());
        if (postfixIndex < 0) {
            Log.e(TAG, "requestSessionId - couldn't find \"" + SESSION_TOKEN_POSTFIX + "\" in the response");
            return null;
        }

        String sessionToken = response.GetData().substring(prefixIndex + SESSION_TOKEN_PREFIX.length(), postfixIndex);

        Log.i(TAG, "requestSessionId End - sessionToken = " + sessionToken);

        return sessionToken;
    }

    /**
     * 
     * @param loginToken - the HTTPS login token
     * @return - the session ID from the hub, or null if failed
     */
    public String sendHubCommand(String sessionId, String actionType, String command) {
        Log.i(TAG, "sendHubCommand Start: sessionId=" + sessionId + ", name=" + actionType + ", command=" + command);

        boolean bShouldWaitForResponse = true;
        OaIq response = null;
        IQ iq = new hubCommandPacket(actionType, command);
        iq.setFrom(sessionId);

        //the original harmony app uses a "RENDER" request instead of "GET. but it seems to work anyway.
        if (actionType.equals("holdAction")) {
            iq.setType(IQ.Type.GET); //should change to RENDER perhaps, but this requires a change in the asmack lib
            bShouldWaitForResponse = false;
        } else {
            iq.setType(IQ.Type.GET);
        }

        String packetId = iq.getPacketID();
        PacketCollector collector = bShouldWaitForResponse ? connection.createPacketCollector(new PacketIDFilter(packetId)) : null;

        connection.sendPacket(iq);

        if (!bShouldWaitForResponse) {
            Log.i(TAG, "sendHubCommand - sending finished. not waiting for response.");
            return "OK";
        }

        response = (OaIq) collector.nextResult(5000);
        collector.cancel();

        if (null == response || response.GetData() == null) {
            Log.e(TAG, "sendHubCommand - Response is null");
            return null;
        }

        Log.i(TAG, "sendHubCommand End - Response: " + response.GetData());

        return response.GetData();
    }

    /**
     * close the connection with the hub
     */
    public void disconnect() {
        Log.i(TAG, "disconnect");
        if (connection != null) {
            XMPPConnection tempConnection = connection;
            connection = null;
            tempConnection.disconnect();
        }
    }

    // an IQ packet class for sending raw data with the HTTPS login token to the hub
    private class sessionTokenPacket extends IQ {

        String loginToken;

        sessionTokenPacket(String aLoginToken) {
            loginToken = aLoginToken;
        }

        @Override
        public String getChildElementXML() {
            String str = "<oa xmlns=\"connect.logitech.com\" mime=\"vnd.logitech.connect/vnd.logitech.pair\">" + "token=" + loginToken
                    + ":name=foo#iOS6.0.1#iPhone" + "</oa>";

            Log.i(TAG, str);
            return str;
        }

        /*
            <iq type="get" id="3174962747" from="guest">
              <oa xmlns="connect.logitech.com" mime="vnd.logitech.connect/vnd.logitech.pair">
                token=y6jZtSuYYOoQ2XXiU9cYovqtT+cCbcyjhWqGbhQsLV/mWi4dJVglFEBGpm08OjCW:name=1vm7ATw/tN6HXGpQcCs/A5MkuvI#iOS6.0.1#iPhone
              </oa>
            </iq>
         */
    }

    // an IQ packet class for sending raw data with the HTTPS login token to the hub
    private class hubCommandPacket extends IQ {

        String command;
        String actionType;

        hubCommandPacket(String aActionType, String aCommand) {
            command = aCommand;
            actionType = aActionType;
        }

        @Override
        public String getChildElementXML() {
            String str = null;
            if (command.isEmpty()) {
                str = "<oa xmlns=\"connect.logitech.com\" mime=\"vnd.logitech.harmony/vnd.logitech.harmony.engine?" + actionType + "\"/>";
            } else {
                str = "<oa xmlns=\"connect.logitech.com\" mime=\"vnd.logitech.harmony/vnd.logitech.harmony.engine?" + actionType + "\">" + command + "</oa>";
            }

            Log.i(TAG, str);
            return str;
        }

        /*
            <iq type="get" id="5e518d07-bcc2-4634-ba3d-c20f338d8927-2">
                <oa xmlns="connect.logitech.com" mime="vnd.logitech.harmony/vnd.logitech.harmony.engine?holdAction">
                    action={"type"::"IRCommand","deviceId"::"11586428","command"::"VolumeDown"}:status=press
                </oa>
            </iq>
         */
    }
}