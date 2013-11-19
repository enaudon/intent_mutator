package com.example.myfirstservice;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

public class RequestHandler extends Handler {
	Context context;
	
	// Message commands (incoming what)
	public static final int FSERV_CMD_ECHO = 0;
	public static final int FSERV_CMD_FWRD = 1;
	
	// Message forward types (incoming arg1)
	public static final int FSERV_FWD_START_ACT = 0;
	public static final int FSERV_FWD_START_ACT_FOR_RES = 1;
	public static final int FSERV_FWD_SEND_BCST = 2;
	public static final int FSERV_FWD_SEND_ORD_BCST = 3;
	
	// Message fuzz types (incoming arg2)
	public static final int FSERV_FUZZ_MUTATE = 0;
	public static final int FSERV_FUZZ_MANGLE = 1;
	public static final int FSERV_FUZZ_BRUTAL = 2;
	
	// Message replies (outgoing what)
	public static final int FSERV_REPL_TIMEOUT = 1;
	public static final int FSERV_REPL_SUCCESS = 0;
	public static final int FSERV_REPL_EUNIMPL = -2;
	public static final int FSERV_REPL_ENODATA = -3;
	public static final int FSERV_REPL_ENOTARG = -4;
	
	// Message bundle keys
    public static final String FSERV_KEY_SEED = "mut_seed";
    public static final String FSERV_KEY_RATIO = "mut_ratio";
    public static final String FSERV_KEY_DATA = "mut_data";
    public static final String FSERV_KEY_TIMEOUT = "ipc_timout";
    public static final String FSERV_KEY_TARGPKG = "ipc_target_package";
    public static final String FSERV_KEY_TARGCMP = "ipc_target_component";
	
    public RequestHandler(Context ctx) {
    	this.context = ctx;
    }
    
    @Override
    public void handleMessage(Message msg) {
        Log.i("MyService", "Message recieved from client: "
        		+ msg.what + " " + msg.arg1 + " " + msg.arg2);
        
        // No return address, no service
        if (msg.replyTo == null) return;
        
        // Initialize reply and grab data
        Message repl = Message.obtain();
        Bundle data = msg.getData();
        
        // Ensure we were sent data to fuzz
        if (data == null) {
      		repl.what = FSERV_REPL_ENODATA;
        	send(msg.replyTo, repl);
      		return;
        }
        
        // Initialize mutator and fuzz the data
        long seed = data.getLong(FSERV_KEY_SEED, 10);
        float ratio = data.getFloat(FSERV_KEY_RATIO, 0.01f);
        Mutator m = new Mutator(seed,ratio);
        Bundle fuzzed = m.mutate(data.getBundle(FSERV_KEY_DATA));
        
        // Execute the specified command
        switch (msg.what)
        {
        
        // Echo fuzzed data
        case FSERV_CMD_ECHO :
        	repl.setData(fuzzed);
        	send(msg.replyTo, repl);
        	return;
        	
        // Forward fuzzed data
        case FSERV_CMD_FWRD :
        	Intent intent = new Intent();
        	intent.putExtras(fuzzed);
        	
            for (String key : intent.getExtras().keySet()) {
                Object obj = intent.getExtras().get(key);
                Log.d("SENDING_ITEMS", String.format("%s %s (%s)", key,  
                    obj.toString(), obj.getClass().getName()));
            }
        	
        	switch (msg.arg1)
        	{
        	case FSERV_FWD_START_ACT :
        		
        		// Make sure we have a valid target
        		String pkg = data.getString(FSERV_KEY_TARGPKG, null);
        		String cmp = data.getString(FSERV_KEY_TARGCMP, null);
        		if (pkg == null || cmp == null) {
          			repl.what = FSERV_REPL_ENOTARG;
          			send(msg.replyTo, repl);
          			return;
        		}
            	intent.setClassName(pkg, cmp);
        		Log.i("MyService", "Forwarding intent to "
        				+ String.format("{%s,%s}", pkg, cmp));
        		
        		// Required to start activities from outside of an
        		// activity
        		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        		
            	context.startActivity(intent);
            	break;
            	
        	case FSERV_FWD_SEND_BCST:
        		Log.i("MyService", "Broadcasting intent");
        		context.sendBroadcast(intent);
        		break;
        	}
        	break;
        }
    }
    
    private void send(Messenger messenger, Message msg) {
        try {
            messenger.send(msg);
        } catch (RemoteException e) {
        	Log.e("MyService", "Unable to deliver reply");
            e.printStackTrace();
        }
    	return;
    }
}