package com.im.njams.sdk.communication.http.https.connectable;

import com.im.njams.sdk.communication.http.HttpConstants;
import com.im.njams.sdk.communication.http.connectable.HttpReceiver;

public class HttpsReceiver extends HttpReceiver {

    @Override
    public String getName(){return HttpConstants.COMMUNICATION_NAME_HTTPS; }
}
