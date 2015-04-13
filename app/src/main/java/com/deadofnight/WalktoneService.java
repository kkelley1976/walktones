//not sure if this file is used anymore
//might be, might not
package com.deadofnight;

import java.util.Timer;
import java.util.TimerTask;
import android.app.Service;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.widget.Toast;

public class WalktoneService extends Service {
	public int detectionfrequency=60000;
	public int scanfrequency=5000;
	private Timer timer = new Timer(true);
	
	WalktoneService(dbcon mydbcon, WifiManager wifi, WalktoneScanner WalktoneScanner) {
		super();
		startservice();
	}
	
	@Override
	public void onCreate() {
		super.onCreate();
		Toast.makeText(this, "Service created...", Toast.LENGTH_LONG).show();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		Toast.makeText(this, "Service destroyed...", Toast.LENGTH_LONG).show();
	}

	private void startservice() {
		timer.scheduleAtFixedRate(new TimerTask() {
			public void run() {
			}
		}, 0, scanfrequency);
	}
	
//	private void stopservice() { 
//		if (timer != null){ 
//			timer.cancel(); 
//		} 
//	}
	 
	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return null;
	}
}
