package com.deadofnight;
//this is a service, so we should be able to bind to it.
//if we bind to it we can either use it or kill it.
//we need the dbcon and wifiscanreceiver but 
//  walktonescanner needs to go away
//1. pressing escape should kill the scanner and leave the rest
//2. pressing quit on the menu should kill them all
//3. launching the app when the service is running should rebind
//   rather than create a second service
//
//at one point, i had it keeping the service open but
//  each time i launched it it would create another service
import java.util.Timer;
import java.util.TimerTask;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

public class Recognizer extends Service {
	private static final String TAG = "Walktones.recognizer";
	public int detectionfrequency=60000;
	public int scanfrequency=2000; //"802.11 Wireless LAN fundamentals" book says
									//usually 10 beacons per second so i could make this 100
									//with a passive scan
	private Timer timer = new Timer(true);
	dbcon mydbcon;
	WifiManager wifi;
	WalktoneScanner WalktoneScanner;
	SharedPreferences prefs = null;
	SharedPreferences lastplayed = null;
	public String lastplace="";
	public boolean moved=true;

	Recognizer(dbcon mydbcon, WifiManager wifi, WalktoneScanner WalktoneScanner) {
		super();
		this.mydbcon = mydbcon;
		this.wifi = wifi;
		this.WalktoneScanner = WalktoneScanner; //dont use this, it gets closed
		
		//these two lines are okay because it only happens when its created
		prefs = WalktoneScanner.getSharedPreferences("tones", 0);		
		lastplayed = WalktoneScanner.getSharedPreferences("lastplayed", 0);

		startservice();
	}

	private void startservice() {
		timer.scheduleAtFixedRate(new TimerTask() {
			public void run() {
				// USING TOAST FROM SERVICE DOESNT WORK
				//Log.d(TAG, "STARTSERVICE() message: " + "RUN");
				SharedPreferences.Editor editor = lastplayed.edit();
				if (wifi != null)
					wifi.startScan(); //passive
				//wifi.startScanActive() //maybe a config setting, uses hidden APs
			if (WalktoneScanner.scansleft>0) {
				WalktoneScanner.scansleft--;
				mydbcon.namePlace(WalktoneScanner.antennas, WalktoneScanner.lat, WalktoneScanner.lon, WalktoneScanner.cname);
				//Log.w(TAG,WalktoneScanner.cname+" training "+WalktoneScanner.scansleft);
			} else {
				// antennas=WalktoneScanner.antennas;

				if (WalktoneScanner != null && WalktoneScanner.combined != null) {
					String n = mydbcon.recognizePlace(WalktoneScanner.combined);
					//Log.w("recog","recognized my place as being "+n);
					if (n.length() > 0 && WalktoneScanner != null)
						WalktoneScanner.name = n;
					WalktoneScanner.threadHandler.sendEmptyMessage(0);
					Message msg= new Message();
					if(lastplace.equals(n)) { 
						moved=false;
						//Log.d("recog","didnt move from "+n+".");
					} else {
						//Log.d("recog","moved from "+lastplace+" to "+n+".");
						lastplace=n;
						moved=true;
					}
					if ( moved==true ) {
						msg.arg1=1;
						WalktoneScanner.threadHandler.sendMessage(msg);
					} else {
						msg.arg1=2;
						WalktoneScanner.threadHandler.sendMessage(msg);
					}
					if ( moved==true )
					if ((lastplayed.getLong(n, 0) + detectionfrequency) < System
							.currentTimeMillis()) {
						Ringtone ring = null;
						Uri toneuri = null;
						String place = n;// result.BSSID.toString()
						if (n.length() > 0) {
							String uristring = prefs.getString(place, "nouri");
							if (uristring != "nouri") {
								toneuri = Uri.parse(uristring);
							}
							if (toneuri != null) {
								ring = RingtoneManager.getRingtone(
										mydbcon.context, toneuri);
							}
							if (ring != null) {
								editor.putLong(place,
										System.currentTimeMillis());
								editor.commit();
								ring.setStreamType(AudioManager.STREAM_MUSIC);
								ring.play();
								moved=false;
							}
						}
					}
				}
			}
			}
		}, 0, scanfrequency);
	}

	/*
	 * private void stopservice() { if (timer != null){ timer.cancel(); } }
	 */
	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		Log.d(TAG,"onBind()");
		return null;
	}
}
