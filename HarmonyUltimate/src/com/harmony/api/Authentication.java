package com.harmony.api;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import android.annotation.SuppressLint;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;

public class Authentication {

    private static final String       TAG                               = "Authentication";
    private GuestSessionTokenTask     guestSessionTokenTask             = null;
    private GuestSessionTokenListener guestSeesionTokenAcquiredDelegate = null;

    public String getLoginToken(String email, String password) {

        try {
            // make a HTTPS connection to the logitech site

            // HttpHost targetHost = new HttpHost("svcs.myharmony.com", 80,
            // "http");
            HttpHost targetHost = new HttpHost("svcs.myharmony.com", 443, "https");

            DefaultHttpClient httpclient = new DefaultHttpClient();
            try {
                // Store the user login
                httpclient.getCredentialsProvider().setCredentials(new AuthScope(targetHost.getHostName(), targetHost.getPort()),
                        new UsernamePasswordCredentials("", ""));

                // Create AuthCache instance
                AuthCache authCache = new BasicAuthCache();
                // Generate BASIC scheme object and add it to the local
                // auth cache
                BasicScheme basicAuth = new BasicScheme();
                authCache.put(targetHost, basicAuth);

                // Add AuthCache to the execution context
                BasicHttpContext localcontext = new BasicHttpContext();
                localcontext.setAttribute(HttpClientContext.AUTH_CACHE, authCache);

                // Create request
                // You can also use the full URI http://www.google.com/
                HttpPost httppost = new HttpPost("https://svcs.myharmony.com/CompositeSecurityServices/Security.svc/json/GetUserAuthToken");
                httppost.removeHeaders("Connection");
                httppost.setHeader("content-type", "application/json; charset=utf-8");
                httppost.setHeader("Accept-Encoding", "gzip, deflate, compress"); // TODO:
                                                                                  // are
                                                                                  // these
                                                                                  // needed?
                httppost.setHeader("Accept", "*/*"); // TODO: are these needed?

                StringEntity se = new StringEntity("{\"email\": \"" + email + "\", \"password\": \"" + password + "\"}", HTTP.UTF_8);
                se.setContentType("application/json; charset=utf-8");
                httppost.setEntity(se);

                // Execute request
                HttpResponse response = httpclient.execute(targetHost, httppost, localcontext);

                HttpEntity entity = response.getEntity();
                String strResponse = EntityUtils.toString(entity);
                StatusLine statusLine = response.getStatusLine();

                if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                    Log.i(TAG + ": Post", "Failed to obtain login token");
                    Log.i(TAG + ": Post", "statusLine : " + statusLine.toString());
                    Log.i(TAG + ": Post", "________**_________________________\n" + strResponse);
                    Log.i(TAG + ": Post", "\n________**_________________________\n");
                    return null;
                }

                return extractLoginTokenFromResponse(strResponse);

            } finally {
                httpclient.getConnectionManager().shutdown();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    private String extractLoginTokenFromResponse(String response) {

        try {
            // extract the authentication token from the JSON response
            JSONParser parser = new JSONParser();
            // String strResponse =
            // "{\"GetUserAuthTokenResult\": {\"AccountId\": 0,\"UserAuthToken\": \"xyzxyz\"}}";
            Object jsonObj = parser.parse(response);
            JSONObject firstMap = (JSONObject) jsonObj;
            JSONObject secondMap = (JSONObject) firstMap.get("GetUserAuthTokenResult");

            String loginToken = secondMap.get("UserAuthToken").toString();
            Log.i(TAG + ": Post", loginToken);

            return loginToken;
        } catch (ParseException pe) {
            Log.i(TAG + ": Post", "Got a ParseException while trying to parse JSON for login token");
            Log.i(TAG + ": Post", "Message: " + pe.getMessage());
            System.out.println("position: " + pe.getPosition());
            System.out.println(pe);
            return null;
        }
    }

    public interface GuestSessionTokenListener {
        void GuestSessionTokenAcquired(String guestSessionToken);
    }

    @SuppressLint("NewApi")
    public boolean getSessionToken(String loginToken, String harmonyIP, int xmppPort, GuestSessionTokenListener delegate) {

        if (loginToken == null || harmonyIP == null || harmonyIP.isEmpty() || delegate == null) {
            Log.e(TAG, "getSessionToken - bad params");
            return false;
        }

        if (guestSessionTokenTask != null) {
            disconnect();
        }
        guestSeesionTokenAcquiredDelegate = delegate;
        guestSessionTokenTask = new GuestSessionTokenTask();
        guestSessionTokenTask.xmppPort = xmppPort;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            guestSessionTokenTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, harmonyIP, loginToken);
        } else {
            guestSessionTokenTask.execute(harmonyIP, loginToken);
        }

        return true;
    }

    private class GuestSessionTokenTask extends AsyncTask<String, String, String> {

        JabberSmackAPI hubSessionConnection = null;
        String         guestSessionToken    = null;
        int            xmppPort             = 5222;

        @Override
        protected String doInBackground(String... params) {

            Log.i(TAG, "GuestSessionTokenTask start in-background");
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
                Log.e(TAG, "GuestSessionTokenTask - could not resolve hub IP: " + hubIP);
                return "Bad hub IP";
            }

            hubSessionConnection = new JabberSmackAPI(null, xmppPort);
            String result = hubSessionConnection.login(hubIP, "guest@connect.logitech.com/gatorade.", "gatorade.");
            if (!result.equalsIgnoreCase("OK")) {
                Log.e(TAG, "GuestSessionTokenTask login error - " + result);
                return result;
            }
            if (isCancelled()) {
                return "Cancelled";
            }
            guestSessionToken = hubSessionConnection.requestSessionId(params[1]);
            if (null == guestSessionToken) {
                Log.e(TAG, "GuestSessionTokenTask no guest session token");
                return "no guest session token";
            }

            Log.i(TAG, "GuestSessionTokenTask finished in-background");
            return "OK";
        }

        @Override
        protected void onPostExecute(String result) {
            // execution of result of Long time consuming operation

            Log.i(TAG, "GuestSessionTokenTask post execute");
            if (guestSessionTokenTask == this) {
                hubSessionConnection.disconnect();

                Log.i(TAG, "GuestSessionTokenTask post execute - calling delegate with guest session token: " + guestSessionToken);
                if (guestSeesionTokenAcquiredDelegate != null) {
                    guestSeesionTokenAcquiredDelegate.GuestSessionTokenAcquired(guestSessionToken);
                }

                guestSessionTokenTask = null;
            }
        }

        @Override
        protected void onCancelled() {
            if (hubSessionConnection != null) {
                hubSessionConnection.disconnect();
            }
        }
    }

    public void disconnect() {

        Log.i(TAG, "disconnect");
        if (guestSessionTokenTask != null) {
            guestSessionTokenTask.cancel(true); //TODO - support cancel requests
            guestSessionTokenTask = null;
        }
        guestSeesionTokenAcquiredDelegate = null;
    }

}
