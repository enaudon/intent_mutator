package com.example.myfirstservice;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.Messenger;
import android.util.Log;

public class MyService extends Service {
    Messenger mService = null;
    boolean mBound;
    
    final Messenger clientMessenger =
    		new Messenger(new RequestHandler(this.getApplicationContext()));

    @Override
    public IBinder onBind(Intent intent) {
    	Log.i("MyService", "Received binding request");
        return clientMessenger.getBinder();
    }
}
