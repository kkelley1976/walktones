//this is where the walktones app receives scan results
package com.deadofnight;

import java.util.ArrayList;
import java.util.List;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.wifi.ScanResult;
import android.util.Log;
import android.widget.TextView;

public class WiFiScanReceiver extends BroadcastReceiver {
	private static final String TAG = "walktonereceiver";
	WalktoneScanner WalktoneScanner;
	public String lastplace="";
	public boolean moved=true;
	List<ScanResult> previous = null;

    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

	public WiFiScanReceiver(WalktoneScanner WalktoneScanner) {
		super();
		this.WalktoneScanner = WalktoneScanner;
	}

	@Override
	public void onReceive(Context c, Intent intent) {
		List<ScanResult> previousmk2 = previous;
		previous = this.WalktoneScanner.antennas;
		List<ScanResult> results = WalktoneScanner.wifi.getScanResults();
		List<ScanResult> combined = new ArrayList<ScanResult>();
		this.WalktoneScanner.antennas = results;
if (previousmk2 !=null)		combined.addAll(previousmk2);
if (previous !=null)		combined.addAll(previous);
		combined.addAll(results);
		this.WalktoneScanner.combined=combined; //sloppily storing global info in the gui

//		for (ScanResult result : results) {
				String buff = "\nthis place is near latitude "
						+ this.WalktoneScanner.lat + "\n near longitude"
						+ this.WalktoneScanner.lon + ",\n with accuracy "
						+ this.WalktoneScanner.acc
						+ "\n and " + results.size() + " antennas\n";
//				for (ScanResult res : results) { //combined if you prefer
//					buff += ", bssid " + res.BSSID + ", power " + res.level;
//				}
				
				boolean campus=false;
				boolean lower=false;
				boolean first=false;
				boolean second=false;
				boolean third=false;
				int samefloor=-65;
				for (ScanResult result : results) {
					if (result.SSID.equals("tsunami"))
						campus=true;
					if (result.BSSID.equals("00:23:69:38:19:25")
						&& result.level>samefloor)
						lower=true;
					if (result.BSSID.equals("00:00:00:01")
							&& result.level>samefloor)
							first=true;
					if (result.BSSID.equals("00:00:00:02")
							&& result.level>samefloor)
							second=true;
					if (result.BSSID.equals("00:00:00:03")
							&& result.level>samefloor)
							third=true;
				}
				//if one or more is that close
				//it must be true
				if (campus==true)
					buff+=" You are on campus.";
				if (lower==true)
					buff+=" You are on lower level.";
				if (first==true)
					buff+=" You are on first floor.";
				if (second==true)
					buff+=" You are on second floor.";
				if (third==true)
					buff+=" You are on third floor.";

        // this changes text to something useful
        if (WalktoneScanner != null) {
            TextView bob = (TextView) WalktoneScanner
                    .findViewById(R.id.textStatus);
            bob.setText(buff);
        }

//		}

		long noplaces=WalktoneScanner.mydbcon.placeCount();
		TextView placesname= (TextView) WalktoneScanner.findViewById(R.id.places);
		placesname.setText(Long.valueOf(noplaces).toString());

		long noprops=WalktoneScanner.mydbcon.propertyCount();
		TextView propsname= (TextView) WalktoneScanner.findViewById(R.id.properties);
		propsname.setText(Long.valueOf(noprops).toString());
		
		String message = String.format("%s networks found.", results.size());
		Log.d(TAG, "onReceive(): " + message);
	}




}