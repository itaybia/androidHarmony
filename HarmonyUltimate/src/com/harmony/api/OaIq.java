package com.harmony.api;

import org.jivesoftware.smack.packet.IQ;

/**
 * 
 * @author Owner
 * an IQ packet that holds the text taken from the "oa" extension that the harmony hub uses
 *
 */
public class OaIq extends IQ {
    private String text = null;

    public OaIq(String aText) {
        text = aText;
    }

    @Override
    public String getChildElementXML() {
        return null;
    }

    public String GetData() {
        return text;
    }
}