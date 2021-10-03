package com.example.mfe;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
public class NotificationReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String message = intent.getStringExtra("toastMessage");
        Server.notificationbuttons=message;
        Client.notificationbuttons=message;
    }
}