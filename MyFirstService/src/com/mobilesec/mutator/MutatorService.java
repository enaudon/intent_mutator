package com.mobilesec.mutator;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageItemInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

public class MutatorService extends Service {
 
    public class RequestHandler extends Handler {
    	
		// Message commands (incoming what)
		public static final int FSERV_CMD_ECHO = 0;
		public static final int FSERV_CMD_FWRD = 1;
		public static final int FSERV_CMD_TEST = 2;
		public static final int FSERV_CMD_NULL = 3;

		// Message forward types (incoming arg1)
		public static final int FSERV_FWD_START_ACT = 0;
		public static final int FSERV_FWD_START_ACT_FOR_RES = 1;
		public static final int FSERV_FWD_SEND_BCST = 2;
		public static final int FSERV_FWD_SEND_ORD_BCST = 3;
		public static final int FSERV_FWD_TEST = 4;

		// Message fuzz types (incoming arg2)
		public static final int FSERV_FUZZ_MUTATE = 0;
		public static final int FSERV_FUZZ_MANGLE = 1;
		public static final int FSERV_FUZZ_BRUTAL = 2;

		// Message replies (outgoing what)
		public static final int FSERV_REPL_TIMEOUT = 1;
		public static final int FSERV_REPL_SUCCESS = 0;
		public static final int FSERV_REPL_EUNIMPL = -2;
		public static final int FSERV_REPL_ENOEXTRAS = -3;
		public static final int FSERV_REPL_EINVPAYLOAD = -4;
		public static final int FSERV_REPL_ENOTARG = -5;

		// Message bundle keys
		public static final String FSERV_KEY_SEED = "mut_seed";
		public static final String FSERV_KEY_RATIO = "mut_ratio";
		public static final String FSERV_KEY_DATA = "mut_data";
		public static final String FSERV_KEY_TIMEOUT = "ipc_timout";
		public static final String FSERV_KEY_TARGPKG = "ipc_target_package";
		public static final String FSERV_KEY_TARGCMP = "ipc_target_component";


		@Override
		public void handleMessage(Message msg) {
			Log.i("handleMessage", "Message recieved from client: "
					+ msg.what + " " + msg.arg1 + " " + msg.arg2);

			// No return address, no service
			if (msg.replyTo == null) return;

			
			

			// Initialize reply and grab data
			Message repl = Message.obtain();
			Bundle data = msg.getData();

			if(msg.what == FSERV_CMD_NULL)
			{
				nullFuzz();
				repl.what = FSERV_REPL_SUCCESS;
				send(msg.replyTo, repl);
				return;
			}
			
			// Ensure we were sent data to fuzz
			if (data == null) {
				repl.what = FSERV_REPL_ENOEXTRAS;
				send(msg.replyTo, repl);
				return;
			}
			Integer seed = data.getInt(FSERV_KEY_SEED, 10);
			Float ratio = data.getFloat(FSERV_KEY_RATIO, 0.01f);
			Bundle mut_data = data.getBundle(FSERV_KEY_DATA);
			if(mut_data == null || seed == null || ratio == null)
			{
				Log.i("Null Check", "Invalid Payoad... replying with error");
				repl.what = FSERV_REPL_EINVPAYLOAD;
				Bundle extras = new Bundle();
				extras.putBundle("mut_data", mut_data);
				extras.putInt("seed", seed);
				extras.putFloat("ratio", ratio);
				repl.setData(extras);
				send(msg.replyTo, repl);
				return;	
			}

			// Initialize mutator and fuzz the data
			Mutator m;
			Bundle fuzzed;

			// Execute the specified command
			switch (msg.what)
			{

			// Echo fuzzed data
			case FSERV_CMD_ECHO :
				Log.i("FSERV_CMD_ECHO", "Test Command! Returning mut_data");

				m = new Mutator(seed,ratio);

				Bundle payload = deserializeBundle(mut_data.getString("EXTRAS"));

				//ensure payload is not null
				if(payload == null)
				{
					Log.i("FSERV_CMD_ECHO", String.format("payload :: is null"));
					repl.what = FSERV_REPL_EINVPAYLOAD;
					send(msg.replyTo, repl);
					return;
				}

				repl.setData(payload);
				payload = repl.getData();
				Log.i("FSERV_CMD_ECHO", String.format("payload :: %s", payload.toString()));

				Bundle mut_payload = m.mutate(payload);
				mut_payload.putBoolean("com.mobilesec.FUZZ_INTENT", true);
				Log.i("FSERV_CMD_ECHO", String.format("mutated payload :: %s", mut_payload.keySet().toString()));

				mut_data.putBundle("EXTRAS", mut_payload);
				repl.setData(mut_data);
				Log.i("FSERV_CMD_ECHO", String.format("mutated data w/ payload :: %s", mut_data.keySet().toString()));


				send(msg.replyTo, repl);
				return;

			// Forward fuzzed data
			case FSERV_CMD_FWRD :
				// Initialize mutator and fuzz the data
				m = new Mutator(seed,ratio);

				Bundle payload2 = deserializeBundle(mut_data.getString("EXTRAS"));

				//ensure payload is not null
				if(payload2 == null)
				{
					Log.i("FSERV_CMD_FWRD", String.format("payload :: is null"));
					repl.what = FSERV_REPL_EINVPAYLOAD;
					send(msg.replyTo, repl);
					return;
				}

				repl.setData(payload2);
				payload2 = repl.getData();
				Log.i("FSERV_CMD_FWRD", String.format("payload :: %s", payload2.toString()));

				Bundle mut_payload2 = m.mutate(payload2);
				mut_payload2.putBoolean("com.mobilesec.FUZZ_INTENT", true);
				Log.i("FSERV_CMD_FWRD", String.format("mutated payload :: %s", mut_payload2.keySet().toString()));

				mut_data.putBundle("EXTRAS", mut_payload2);

				Intent fuzzed_intent = buildFuzzedIntent(mut_data);
				
				Log.i("FSERV_CMD_FWRD",fuzzed_intent.toString());

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
					fuzzed_intent.setClassName(pkg, cmp);
					Log.i("com.mobilesec.mutatorservice", "Forwarding intent to "
							+ String.format("{%s,%s}", pkg, cmp));

					// Required to start activities from outside of an
					// activity
					//TODO: Strings for explicit keys in activities
					fuzzed_intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

					getApplication().startActivity(fuzzed_intent);
					break;

				case FSERV_FWD_SEND_BCST:
					Log.i("FSERV_FWD_SEND_BCST", "Broadcasting intent");
					sendComponentBroadcast(fuzzed_intent);
					repl.what = FSERV_REPL_SUCCESS;
					send(msg.replyTo, repl);
					break;
				case FSERV_FWD_TEST:
					repl.what = FSERV_REPL_SUCCESS;
					send(msg.replyTo, repl);
					break;
				}
				break;
			}
		}

		private void sendComponentBroadcast(Intent fuzzed_intent) {
			PackageManager pm = getPackageManager();
			ArrayList<ComponentName> found = new ArrayList<ComponentName>();
        	Log.i("sendComponentBroadcast...", "Method Start");

        	Intent queryIntent = new Intent();
        	queryIntent.setAction(fuzzed_intent.getAction());
	        final List<ResolveInfo> activities = pm.queryBroadcastReceivers(queryIntent, 0);
	        for (ResolveInfo resolveInfo : activities) {
	            ActivityInfo activityInfo = resolveInfo.activityInfo;
	            
	            if (activityInfo != null)
	            {
	            	if(activityInfo.name.contains("sniffer"))
	            		continue;
	            	//Log.i("sendComponentBroadcast...", "Found component::" + activityInfo.name);
	                found.add(new ComponentName(activityInfo.applicationInfo.packageName, activityInfo.name));
	    	     }

	        }
	        for( ComponentName cn : found)
	        {
	        	Log.i("sendComponentBroadcast++", cn.toShortString());
	        	fuzzed_intent.setComponent(cn);
	        	getApplication().sendBroadcast(fuzzed_intent);
	        	Log.i("sendComponentBroadcast--", cn.toShortString());

	        	//Thread.sleep(1000);
	        }
		}

		private Intent buildFuzzedIntent(Bundle mut_data) {
			Intent fuzzed_intent = new Intent();
			for(String key : mut_data.keySet())
			{
				if(key.contentEquals("SCHEME")){
				}
				else if(key.contentEquals("CATEGORY")){
					fuzzed_intent.addCategory(mut_data.getString(key));
				}
				else if(key.contentEquals("ACTION")){
					fuzzed_intent.setAction(mut_data.getString(key));
				}
				else if(key.contentEquals("EXTRAS")){
					fuzzed_intent.putExtras(mut_data.getBundle(key));
				}
				else if(key.contentEquals("FLAGS")){
					fuzzed_intent.addFlags(Integer.parseInt(mut_data.getString(key)));
				}
				else if(key.contentEquals("TYPE")){
					fuzzed_intent.setType(mut_data.getString(key));
				}
				else if(key.contentEquals("URI")){
					fuzzed_intent.setData(Uri.parse(mut_data.getString(key)));
				}
			}
			
			return fuzzed_intent;
		}

		private void send(Messenger messenger, Message msg) {
			try {
				messenger.send(msg);
			} catch (RemoteException e) {
				Log.e("com.mobilesec.mutatorservice", "Unable to deliver reply");
				e.printStackTrace();
			}
			return;
		}

		private Bundle deserializeBundle(final String base64) {
			Bundle bundle = null;
			final Parcel parcel = Parcel.obtain();
			try {
				final ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
				final byte[] buffer = new byte[1024];
				final GZIPInputStream zis = new GZIPInputStream(new ByteArrayInputStream(Base64.decode(base64, 0)));
				int len = 0;
				while ((len = zis.read(buffer)) != -1) {
					byteBuffer.write(buffer, 0, len);
				}
				zis.close();
				parcel.unmarshall(byteBuffer.toByteArray(), 0, byteBuffer.size());
				parcel.setDataPosition(0);
				bundle = parcel.readBundle();
			} catch (IOException e) {
				e.printStackTrace();
				bundle = null;
			} finally {
				parcel.recycle();
			}

			return bundle;
		}
    }
    
	private void nullFuzz() {
		ArrayList<ComponentName> found = new ArrayList<ComponentName>();
		PackageManager pm = getPackageManager();
		for (PackageInfo pi : pm.getInstalledPackages(PackageManager.GET_RECEIVERS)) {
			ActivityInfo items[] = null;
			items = pi.receivers;
			
			
			if (items != null)
				for (ActivityInfo recvr : items)
					found.add(new ComponentName(pi.packageName, recvr.name));
		}
		String str = String.format("%s components found", found.size());
		Toast.makeText(getApplicationContext(), str,Toast.LENGTH_SHORT).show();
		Log.i("com.mobilesec.mutatorservice", "BroadCastFuzz..."+str);

		/*
		for (int i = 0; i < found.size(); i++) {
			Intent in = new Intent();
			in.setComponent(found.get(i));
			sendBroadcast(in);
		}*/
		ComponentName cn = new ComponentName("com.amazon.kindle", "com.amazon.kcp.recommendation.CampaignWebView");
		Intent in = new Intent();
		in.setComponent(cn);
		sendBroadcast(in);
	}
    
    final Messenger clientMessenger =
    		new Messenger(new RequestHandler());

    @Override
    public IBinder onBind(Intent intent) {
    	Log.i("MyService", "Received binding request");
        return clientMessenger.getBinder();
    }
}
