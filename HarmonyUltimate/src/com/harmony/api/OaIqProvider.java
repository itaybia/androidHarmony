package com.harmony.api;

import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.provider.IQProvider;
import org.xmlpull.v1.XmlPullParser;

import android.util.Log;

/**
 * 
 * @author Owner
 * an IQ provider that can parse the "oa" extension that the harmony hub uses.
 * sets the content of the extension into an OaIq packet.
 *
 */
public class OaIqProvider implements IQProvider {
    public static final String NAMESPACE    = "connect.logitech.com";
    public static final String ELEMENT_NAME = "oa";

    @Override
    public IQ parseIQ(XmlPullParser parser) throws Exception {
        String name = null;
        String oaText = null;
        boolean done = false;
        boolean errorResponse = false;

        //check the returned status code is 1xx or 2xx
        for (int i = 0; i < parser.getAttributeCount(); i++) {
            String attr = parser.getAttributeName(i);
            String attrVal = parser.getAttributeValue(i);

            if (attr.equals("errorcode") && (attrVal == null || attrVal.isEmpty() || Integer.parseInt(attrVal) >= 300)) {
                Log.e("OaIqProvider", "errorcode: " + attrVal);

                for (int j = 0; j < parser.getAttributeCount(); j++) {
                    if (parser.getAttributeName(j).equals("errorstring")) {
                        Log.e("OaIqProvider", "error on parser: " + parser.getAttributeValue(j));
                        break;
                    }
                }
                errorResponse = true;
                break;
            }
        }

        //get the oa tag text.
        //make sure to never go over the end tag of the oa extension or the packet will get lost.
        while (false == done && parser.next() != XmlPullParser.END_DOCUMENT) {
            name = parser.getName();

            switch (parser.getEventType()) {
                case XmlPullParser.TEXT:
                    if (!errorResponse) {
                        oaText = parser.getText();
                    }
                    break;

                case XmlPullParser.END_TAG: {
                    done = ELEMENT_NAME.equalsIgnoreCase(name);
                    break;
                }
            }
        }

        return new OaIq(oaText);
    }
}