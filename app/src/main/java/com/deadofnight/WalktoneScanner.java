package com.deadofnight;
//Have your phone play a ringtone when it recognizes you've
//walked into a new room.
//mostly a tool that lets me play with different wifi localization techniques
//note that this tool uses non-uniform fingerprint sizes for each location
//so it's useful for developing, but not great for doing science
//If you see any terms you're not familiar with, there is a reference at http://wili.wikia.com
//If the entries there are not up to snuff, please feel free to improve them for other researchers.
//
//Getting started on a new workstation (if this is in subversion):
//Install eclipse
//Install the android sdk plugin
//Install the subclipse plugin
//Go to the SVN Repository browser
//right-click on walktones
//check out as a new project
//from the IP address of your subversion server
//package com.deadofnight
//other fields probably matter too
//now go to package explorer
//click on walktones in the package explorer
//project->clean
//then Run

//Common Problems:
//if you get a weird 5.0 error do project->android tools->fix project properties
//if you get a missing gen folder error, press the Run button
//if you get The project cannot be built until build path errors are resolved
//  right click the project, select properties
//  go to Java Build path, choose Order and export
//  swap the order of walktones/src and Android 2.1-update1
//  doesn't matter what it is, just swap it. you can also add a library, click ok, and remove it
//  once you get it to resave the build path in the project properties, it will work if you press Run

//Architecture (such as it is):
//  dbcon.java: database connector and location sensing logic
//  WalktoneScanner.java: the GUI/launcher
//  WiFiScanReceiver.java: doesn't do much anymore, just what it says
//  Recognizer.java: the thing that passes for a local service right now
//					launches scans, plays ringtones on a timer
//  WalktoneService.java: the replacement remote service, not actually implemented

//TODO:
//Settings Menu:
//(1) Make Precision menu do something
//(2) Make Frequency menu do something
//				And clarify frequency of what?
//				Is it how often it checks to see where it is
//				Or is it how often it rings in a place
//				need both
//(3) Duration setting for max length a tone will play
//    As it turns out every time it scans right now it stops playing
//(4) Background button in addition to Quit
//(5) Quit should kill any background services
//(6) Right now, escape button kills all, it should background the service
//
//Debug Section:
//
//Main Interface:
//(3) Delete Current location button
//(4) Change colors
//
//Logic:
//(1) Use GPS location as a property for real
//(2) Use cell network location as a property for real
//(3) Use 2nd strongest antenna as a property
//(4) Use weakest antenna as a property (maybe. or not)
//(5) Use ordered list of antennas as a property (do they appear in this order, regardless of strengths?)
//(6) Reinforcing or correcting a location doesn't work well under some algorithms
//(7) take multiple samples at each location before deciding i am somewhere
//(8) stop scoring linearly, use inverse square law or standard deviation to score
//
//Background Recognizer Service:
//(1) Make sure it runs in the background as an actual service
//    but it shouldn't create more than one service
//    it basically needs to be an AIDL remote service as far as I can tell
//(2) Limit the amount of time a ringtone plays to the right amount.
//    some loop forever if you let them.
//    right now each tone cuts out as the next scan starts.
//
//(*) If it should start crashing on exit, it is no doubt another null pointer error
//    Probably fixable by unregistering a listener or something similar
//
//Other:
//(1) A Walktones icon
//(2) Add advertising
//
//Future Versions:
//(1) Add Bluetooth scanner (maybe not)
//(2) More heavily weight more recent properties
//(3) Rename button
//(4) Even later, use the indoor geolocation locationmanager method when written
//(5) Package of ringtones to include, slide whistle for elevator, etc

import java.util.ArrayList;
import java.util.List;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.net.wifi.ScanResult;

import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class WalktoneScanner extends Activity implements OnClickListener, LocationListener, SensorEventListener {
	private static final String TAG = "WalktoneScanner";
    public dbcon mydbcon = null;
	WifiManager wifi;
	BroadcastReceiver receiver;
	LocationManager locationManager=null;
	TextView textStatus;
	Button buttonScan;
	Recognizer recognizer = null;
	public String name = "";
	public String cname="";
    SensorManager sm;


	CharSequence placetoassignto;

	public double lat=0.0;
	public double lon=0.0;
	public double alt=0.0;
	public double acc=0.0;
	public List<ScanResult> antennas=null;
	public List<ScanResult> combined=null;
	
	int scansleft=0;
	
	/** Called when the activity is first created. */
	@TargetApi(Build.VERSION_CODES.GINGERBREAD)
    @Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		/*restore state
		TextView placename = (TextView) findViewById(R.id.place);
		if (name.length() > 0)
			placename.setText(name);
		*/
		
		textStatus = (TextView) findViewById(R.id.textStatus);
		buttonScan = (Button) findViewById(R.id.buttonScan);
		buttonScan.setOnClickListener(this);
		Button buttonName = (Button) findViewById(R.id.namer);
		buttonName.setOnClickListener(this);
//		Button buttonDel = (Button) findViewById(R.id.deleter);
//		buttonDel.setOnClickListener(this);
		Button buttonRec = (Button) findViewById(R.id.recognize);
		buttonRec.setOnClickListener(this);
		Button buttonAss = (Button) findViewById(R.id.assign);
		buttonAss.setOnClickListener(this);
		Button buttonUp = (Button) findViewById(R.id.update);
		buttonUp.setOnClickListener(this);
		Button buttonConfirm = (Button) findViewById(R.id.confirm);
		buttonConfirm.setOnClickListener(this);
		Button buttonCorrect = (Button) findViewById(R.id.correct);
		buttonCorrect.setOnClickListener(this);

		//TextView bob = (TextView) findViewById(R.id.here);
		
		// Setup WiFi
		wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		wifi.setWifiEnabled(true);
		
		// Register Broadcast Receiver
		if (receiver == null)
			receiver = new WiFiScanReceiver(this);

		registerReceiver(receiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
		
		mydbcon = new com.deadofnight.dbcon(getApplicationContext(),this);
//		if (mydbcon!=null) {
//			Toast.makeText(this, "db connection created", Toast.LENGTH_SHORT).show();
//		}
//		if (mydbcon.db !=null){
//			Toast.makeText(this, "db created", Toast.LENGTH_SHORT).show();
//		}
		//only reason the following line isn't going to work is if there's no such table
		//Toast.makeText(this, mydbcon.placeCount("home")+" places defined", Toast.LENGTH_SHORT).show();
		
		//wifi.startScan();//have this on a heartbeat now
		recognizer = new Recognizer(mydbcon,wifi,this);

        locationManager =
        	(LocationManager)getSystemService(Context.LOCATION_SERVICE);
        if (locationManager==null) Log.w(TAG,"null locationmanager");
        else Log.w(TAG,"locationmanager not null");
        Criteria crit = new Criteria();
        //String provider = "gps";
        //Location location = new Location("gps"); //should also support "network" ACCURACY_COARSE for non-gps enabled handhelds?
        if (crit !=null) crit.setAccuracy(Criteria.ACCURACY_FINE);
        //if (crit !=null && locationManager!=null)
        //    	provider = locationManager.getBestProvider(crit,true);
        //provider = locationManager.getProvider(GPS_PROVIDER);

        //if (provider != null) location =
        //    	((LocationManager)locationManager).getLastKnownLocation(provider);
        //else
        //    	Log.w(TAG,"provider is null");

        long updateTimeMsec = 1000L;
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
            		updateTimeMsec, 60.0f, this);
 	
		Intent serviceIntent = new Intent();
		serviceIntent.setAction("com.deadofnight.service.WalktoneService");
		startService(serviceIntent);

        //magnometer sensors
        SensorManager sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        sm.registerListener((SensorEventListener)this,
                sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
                SensorManager.SENSOR_DELAY_UI);
        sm.registerListener((SensorEventListener)this,
                sm.getDefaultSensor(Sensor.TYPE_GRAVITY),
                SensorManager.SENSOR_DELAY_UI);




		threadHandler.hasMessages(0);
		Log.d(TAG, "onCreate()");
	}

	//should be changed to the new action bar
	//if your phone doesn't have a settings button, hold down the multiwindow button
	//http://developer.android.com/guide/topics/ui/menus.html#options-menu
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
	    MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.menu, menu);
	    return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    switch (item.getItemId()) {
	    	case R.id.settings:	   //placeholder, no settings defined
	    		return true;
	    	case R.id.quit:
	    		this.finish();
	    		return true;
	    	default:
	    		return super.onOptionsItemSelected(item);
	    }
	}
	
	@Override
	public void onStop() {
//		unregisterReceiver(receiver);
		//mydbcon.close(); //no, recognizer is still using it
		if (locationManager != null) locationManager.removeUpdates(this);
		super.onStop();
	}

	public void onMonkey(CharSequence thing) {
	 //       Toast.makeText(getApplicationContext(), items[item], Toast.LENGTH_SHORT).show();
			mydbcon.namePlace(antennas, lat, lon, thing.toString());
	}
	    
	public void onClick(View view) {
		//technically unnecessary in some locations with frequent broadcasts
		if (view.getId() == R.id.buttonScan) {
			//Log.d(TAG, "onClick() wifi.startScan()");
				wifi.startScan();
		} else
		if (view.getId() == R.id.assign) {
				//Log.d(TAG, "onClick() assign()");
				Toast.makeText(this, "hit the assign button", Toast.LENGTH_SHORT).show();
				placePicked(mydbcon.recognizePlace(antennas));
		} else
		if (view.getId() == R.id.wipe) {
				//Log.d(TAG,"onClick() wipe");
				mydbcon.deleteAll();
		} else
		if (view.getId() == R.id.update) {
			//Log.d(TAG,"onClick() update");
			if (antennas !=null && name.length()>0)
			{
				Toast.makeText(this, "updating "+name, Toast.LENGTH_SHORT).show();
				mydbcon.namePlace(antennas, lat, lon, name);
			}
		} else
		if (view.getId() == R.id.correct) {
			//Log.w(TAG,"onClick() correct");
			Toast.makeText(this, "correcting", Toast.LENGTH_SHORT).show();
			if (antennas !=null)
			{
			    List<String> list = new ArrayList<String>();
				list=mydbcon.selectAll();
				final CharSequence[] items = list.toArray(new CharSequence[list.size()]);

				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setTitle("Which place are you at?");
				builder.setItems(items, new DialogInterface.OnClickListener() {
			    	public void onClick(DialogInterface dialog, int item) {
			    		//getApplication().
			    		scansleft=10;
			    		cname=items[item].toString();
			    		mydbcon.namePlace(antennas, lat, lon, items[item].toString());
			    	}
			    });
				
				//AlertDialog alert =
					builder.create().show();
			}
		} else
/*		if (view.getId() == R.id.debug) {
				Log.d(TAG, "onClick() debug()");
					mydbcon.showCounts();
		} else
		if (view.getId() == R.id.home) {
			//Toast.makeText(this, "home", Toast.LENGTH_SHORT).show();
			Log.d(TAG, "onClick() home");
			//if home exists pull lon and lat and compare to zero
			//String sql="insert into place (name,longitude,latitude) values ('home',lon,lat)";
			if (mydbcon.placeExists("home")) {
				Toast.makeText(this, "home already exists, updating", Toast.LENGTH_SHORT).show();
				if (antennas !=null) mydbcon.nameHome(antennas, lat, lon);
			} else {
				Toast.makeText(this, "naming this my home calling dbcon.nameHome", Toast.LENGTH_SHORT).show();
				if (antennas !=null) mydbcon.nameHome(antennas, lat, lon);
			}
		} else */
		if (view.getId() == R.id.recognize) {
			Toast.makeText(this, "hit the recognize button", Toast.LENGTH_SHORT).show();
			if (antennas!=null) {
				String place=mydbcon.recognizePlace(antennas);
				Toast.makeText(this, "you are at "+place,Toast.LENGTH_SHORT).show();
			} else
				Toast.makeText(this, "antennas is null", Toast.LENGTH_SHORT).show();
		} else
		if (view.getId() == R.id.namer) {
			Toast.makeText(this, "hit the name button", Toast.LENGTH_SHORT).show();
			String place=mydbcon.recognizePlace(antennas);
			if (place.length()>990 && !place.equals("[unknown]")) {
				Toast.makeText(this, "you are already at "+place+". assign a ringtone",Toast.LENGTH_SHORT).show();
			} else {
			    AlertDialog.Builder alert = new AlertDialog.Builder(this);
			      
			    alert.setTitle("Name");
			    alert.setMessage("Enter a name for this place");
			      
			    // Set an EditText view to get user input
			    final EditText input = new EditText(this);
			    alert.setView(input);
			      
			   alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
			   public void onClick(DialogInterface dialog, int whichButton) {
				   Editable value = input.getText();
				   mydbcon.namePlace(antennas,lat,lon,value.toString());
			     }
			   });
			   alert.setNeutralButton("Home", new DialogInterface.OnClickListener() {
				 public void onClick(DialogInterface dialog, int whichButton) {
					 mydbcon.namePlace(antennas, lat, lon, "home");
				 }
			   });
			   alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			     public void onClick(DialogInterface dialog, int whichButton) {
			       // Canceled.
			     }
			   });
			   alert.show();
			}
		} /*else
			
			if (view.getId() == R.id.deleter) {
				Toast.makeText(this, "hit the delete button", Toast.LENGTH_SHORT).show();
				    AlertDialog.Builder alert = new AlertDialog.Builder(this);
				      
				    alert.setTitle("Delete");
				    alert.setMessage("Enter a place to delete");
				      
				    // Set an EditText view to get user input
				    final EditText input = new EditText(this);
				    alert.setView(input);
				      
				   alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
				   public void onClick(DialogInterface dialog, int whichButton) {
					   Editable value = input.getText();
					   mydbcon.deletePlace(value.toString());
				     }
				   });
				   alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
				     public void onClick(DialogInterface dialog, int whichButton) {
				       // Canceled.
				     }
				   });
				   alert.show();
				}*/
	}
	
    public void placePicked(String place) {
        //String uri = null;
        Intent intent = new Intent( RingtoneManager.ACTION_RINGTONE_PICKER);
        intent.putExtra( RingtoneManager.EXTRA_RINGTONE_TYPE,
        		RingtoneManager.TYPE_ALL);
        intent.putExtra( RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Tone");
        //intent.putExtra( RingtoneManager.EXTRA_RINGTONE_INCLUDE_DRM, 1);
        //DRM ringtones are no longer supported, even though that may be all the ones on my phone
        placetoassignto=place;

//        if( uri != null) it is, i just initialized it to null
//        	intent.putExtra( RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,Uri.parse(uri));
//        else
        	intent.putExtra( RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,(Uri)null);
        
        int resultCode=0;
        startActivityForResult(intent,resultCode);
        if (resultCode == RESULT_OK) {
        	Uri toneuri = intent.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
        	if (toneuri != null) {
 //       	  ringTonePath = toneuri.toString();
        	}
        }
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data){
    // See which child activity is calling us back.
			Intent intent=data;
			String ringTonePath= new String();

            if (resultCode == RESULT_OK) {
            	Uri toneuri = intent.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            	if (toneuri != null) {
            	  ringTonePath = toneuri.toString();
            	}
            }
//            TextView tv = new TextView(this);
            //we need to save this data pair somewhere
            //so we can use it when we receive a new network
            //sharedpreferences are good enough for walktones
            //sqlite3 is better for places
            SharedPreferences prefs=getSharedPreferences("tones",0);
			SharedPreferences.Editor editor=prefs.edit();
			editor.putString(placetoassignto.toString(),ringTonePath);
			editor.commit();
//            tv.setText(buff);
//            setContentView(tv);
    }

	//public class MyLocationListener implements LocationListener {
//	@Override
	public void onLocationChanged(Location loc)    {
			this.lat=loc.getLatitude();
			this.lon=loc.getLongitude();
			this.alt=loc.getAltitude();
			this.acc=loc.getAccuracy();
Log.w(TAG,"onlocationchanged");
//			String Text = "My current location is: " +
//				"Latitude = " + loc.getLatitude() +
//				"Longitude = " + loc.getLongitude();
//			Toast.makeText( getApplicationContext(),Text,Toast.LENGTH_SHORT).show();
	}

//	@Override
	public void onProviderDisabled(String provider) {
		Toast.makeText( getApplicationContext(),
				"Gps Disabled",
				Toast.LENGTH_SHORT ).show();
	}

//	@Override
	public void onProviderEnabled(String provider) {
		Toast.makeText( getApplicationContext(),
				"Gps Enabled",
				Toast.LENGTH_SHORT).show();
	}

//	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
	}
	
    public Handler threadHandler = new Handler() {
    	long lasttime=0;
    	public void handleMessage(android.os.Message msg) {
        	long now=0;
        	//just update the ui
        	if (msg.arg1==1) {
            	String buff="Entered "+name;
        		now=System.currentTimeMillis();
        		TextView track= (TextView) findViewById(R.id.log);
            	if (lasttime!=0)
            		buff+=" "+(now-lasttime)/1000+" seconds later.";
            	else
            		buff+=". ";
        		track.append(buff);
        		lasttime=now;
        		//track.append(new Time().toString()+":Entered "+name+".");
        	} else if (msg.arg1==2){
        		TextView track= (TextView) findViewById(R.id.log);
        		track.append(" stood ");
        	} else if (msg.arg1==3){
        		TextView track= (TextView) findViewById(R.id.log);
        		track.append(" moved ");
        	}
        	
        	if (findViewById(R.id.namer)!=null) {
//			  Button NameOrAssign = (Button) findViewById(R.id.namer);
//			  NameOrAssign.setText("Update");
//			  NameOrAssign.setId(R.id.assign);
        	}
			TextView placename = (TextView) findViewById(R.id.place);
			if (name.length() > 0)
				placename.setText(name);
        }
    };



//    public void onAccuracyChanged(Sensor sensor, int accuracy) {    }
/*
    @Override
    public void onSaveInstanceState(Bundle savedInstanceState)
    {
      // Store UI state to the savedInstanceState.
      // This bundle will be passed to onCreate on next call.

      EditText txtName = (EditText)findViewById(R.id.place);
      String strName = txtName.getText().toString();
      
      savedInstanceState.putString("Name", strName);
          
      super.onSaveInstanceState(savedInstanceState);
    }
*/
    /////////////////////////////////////////////////////////////////////////////
    //
    // magnetic sensor info
    //
    //////////////////////////////////////////////////////////////////////////////

    /*
Sensor.TYPE_ORIENTATION is deprecated.
The documentation says you should use SensorManager.getOrientation() instead.
So you should read from Sensor.TYPE_ACCELEROMETER as well as from Sensor.TYPE_MAGNETIC_FIELD,
 and then call SensorManager.getRotationMatrix() and finally SensorManager.getOrientation()
  which will return you the orientation of the phone. From there if you see this diagram it is
   trivial to get the phone's orientation. This is probably what your second example does, but I
    don't know because you didn't show me what it is.
 */

    float[] mGravity;
    float[] mGeomagnetic;
    float[] mGeomagnetic_W = new float[3];
    float[] mSumGeomagnetic;
    float[] mSumGeomagnetic_W = new float[3];
    float[] mSumGeomagnetic_W_avg = new float[3];
    int magneticcount=0;
    //float mLinear[]=new float[3];
    float[] padded_mGeomagnetic = new float[4];

    /*
    I finally managed to solve it! So to get acceleration vector in Earth's coordinate system you need to:

_/1. get rotation matrix (float[16] so it could be used later by android.opengl.Matrix class) from SensorManager.getRotationMatrix() (using SENSOR.TYPE_GRAVITY and SENSOR.TYPE_MAGNETIC_FIELD sensors values as parameters),
_/2. use android.opengl.Matrix.invertM() on the rotation matrix to invert it (not transpose!),
_/3. use Sensor.TYPE_LINEAR_ACCELERATION sensor to get linear acceleration vector (in device's coord. sys.),
4. use android.opengl.Matrix.multiplyMV() to multiply the rotation matrix by linear acceleration vector.
And there you have it! I hope I will save some precious time for others.

Thanks for Edward Falk and Ali for hints!!
http://developer.android.com/reference/android/opengl/Matrix.html
http://stackoverflow.com/questions/11578636/acceleration-from-devices-coordinate-system-into-absolute-coordinate-system
add a 0 to the end of the magnetic vector
     */

    public void onSensorChanged(SensorEvent event) {
        float orientation[] = new float[3];
        float Rot[] = new float[16];
        float I[] = new float[16];
        float Invertm[] = new float[16];
        float resultVec[]=new float[4];


        if (event.sensor.getType() == Sensor.TYPE_GRAVITY)
            mGravity = event.values.clone();
        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
            mGeomagnetic = event.values.clone();
        //nope, dont need it, i am trying to get magnetic field in world coords,  not linear acceleration
        //if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION)
         //   mLinear =event.values.clone();

        if (mGravity != null && mGeomagnetic != null) {

            boolean success = SensorManager.getRotationMatrix(Rot, I, mGravity, mGeomagnetic);
            if (success) {
                boolean success2 = android.opengl.Matrix.invertM(Invertm,0,Rot,0);
                if (success2) {
                    padded_mGeomagnetic[0]=mGeomagnetic[0];
                    padded_mGeomagnetic[1]=mGeomagnetic[1];
                    padded_mGeomagnetic[2]=mGeomagnetic[2];
                    padded_mGeomagnetic[3]=0;

                    //multiplyMV(float[] resultVec, int resultVecOffset, float[] lhsMat, int lhsMatOffset, float[] rhsVec, int rhsVecOffset)
                    android.opengl.Matrix.multiplyMV(resultVec,0,Invertm,0,padded_mGeomagnetic,0);
                }
                SensorManager.getOrientation(Rot, orientation);
                //azimut = orientation[0]; // orientation contains: azimut, pitch and roll
            }
        }

        if (mGravity != null && padded_mGeomagnetic != null && event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {

            //String newstr = String.format("magnetic field\nx: %f\ny:%f\nz:%f",
            //        new Object[]{mGeomagnetic[0], mGeomagnetic[1], mGeomagnetic[2]});

            //newstr = newstr.concat(String.format("\norientation\nazimut: %f\npitch: %f\nroll:%f\n", orientation[0], orientation[1], orientation[2]));

            /* this probably didn't work
            mGeomagnetic_W[0] = Rot[0] * mGeomagnetic[0] + Rot[1] * mGeomagnetic[1] + Rot[2] * mGeomagnetic[2];
            mGeomagnetic_W[1] = Rot[3] * mGeomagnetic[0] + Rot[4] * mGeomagnetic[1] + Rot[5] * mGeomagnetic[2];
            mGeomagnetic_W[2] = Rot[6] * mGeomagnetic[0] + Rot[7] * mGeomagnetic[1] + Rot[8] * mGeomagnetic[2];
            */
            mGeomagnetic_W[0] = resultVec[0];
            mGeomagnetic_W[1] = resultVec[1];
            mGeomagnetic_W[2] = resultVec[2];
            //newstr = newstr.concat(String.format("world magnetic field\nx: %f\ny:%f\nz:%f",
            //        new Object[]{mGeomagnetic_W[0], mGeomagnetic_W[1], mGeomagnetic_W[2]}));
            magneticcount++;
            String buff="";
            if (magneticcount < 150) {
                mSumGeomagnetic_W[0]+=mGeomagnetic_W[0];
                mSumGeomagnetic_W[1]+=mGeomagnetic_W[1];
                mSumGeomagnetic_W[2]+=mGeomagnetic_W[2];
                buff=String.format("x:  %f  y:%f  z:%f",
                               new Object[]{mGeomagnetic[0], mGeomagnetic[1], mGeomagnetic[2]});
                TextView bob = (TextView) this
                        .findViewById(R.id.magStatus);
                bob.setText(buff);

                buff=String.format("wx:  %f  wy:%f  wz:%f",
                        new Object[]{mGeomagnetic_W[0], mGeomagnetic_W[1], mGeomagnetic_W[2]});
                bob = (TextView) this
                        .findViewById(R.id.magwStatus);
                bob.setText(buff);
            } else if (magneticcount == 150) {
                mSumGeomagnetic_W_avg[0]=mSumGeomagnetic_W[0]/150;
                mSumGeomagnetic_W_avg[1]=mSumGeomagnetic_W[1]/150;
                mSumGeomagnetic_W_avg[2]=mSumGeomagnetic_W[2]/150;
                mSumGeomagnetic_W[0]=0;
                mSumGeomagnetic_W[1]=0;
                mSumGeomagnetic_W[2]=0;
                magneticcount=0;
                buff=String.format("ax: %f  ay:%f az:%f",
                              new Object[]{mSumGeomagnetic_W_avg[0], mSumGeomagnetic_W_avg[1], mSumGeomagnetic_W_avg[2]});
                    TextView bob = (TextView) this
                            .findViewById(R.id.magaStatus);
                    bob.setText(buff);
            }

        }
        //github is broken. keeps saying it is in it when it is not. jwauahjkhkj
        // not the best way
        //float[] rotationMatrixFromOrientation = getRotationMatrixFromOrientation(orientation);
        //newstr=newstr.concat(String.format("\norientation matrix\n%f %f %f\n%f %f %f\n%f %f %f\n",
        //rotationMatrixFromOrientation[0][0],
        //rotationMatrixFromOrientation[0][1],
        //rotationMatrixFromOrientation[0][2],
        //rotationMatrixFromOrientation[1][0],
        //rotationMatrixFromOrientation[1][1],
        //rotationMatrixFromOrientation[1][2],
        //rotationMatrixFromOrientation[2][0],
        //rotationMatrixFromOrientation[2][1],
        //rotationMatrixFromOrientation[2][2],
        //));
        //
        // better way http://stackoverflow.com/questions/15315129/convert-magnetic-field-x-y-z-values-from-device-into-global-reference-frame/15317814#15317814
        //http://developer.android.com/reference/android/hardware/SensorManager.html#getRotationMatrix(float[], float[], float[], float[])
        //now, get the rotation matrix from the sensormanager
        //then, do a few dot products to put the microteslas in world coordinates
        //maybe like this http://simple.wikipedia.org/wiki/Dot_product
        //or more directly like this http://stackoverflow.com/questions/14963190/calculate-acceleration-in-reference-to-true-north/14988559#14988559
        //except with magnetic rather than accceleration like this http://stackoverflow.com/questions/15315129/convert-magnetic-field-x-y-z-values-from-device-into-global-reference-frame
        /*
_ R = rotation matrix obtained by calling getRotationMatrix
_ mGeomagnetic = accelerator vector return by sensor ( mGeomagnetic = event.values.clone )
_ mGeomagnetic_W = R * mGeomagnetic is the same acceleration vector in the world coordinate system.

_ mGeomagnetic_W is an array of dimension 3
_ mGeomagnetic_W[0] is magnetic field in X.
_ mGeomagnetic_W[1] is magnetic field in Y.

_ float[] R = new float[9];
_ float[] I = new float[9];
_ SensorManager.getRotationMatrix(R, I, gravity, magnetic);
_float [] mGeomagnetic = values.clone();
__float [] mGeomagnetic_W = new float[3];
            mGeomagnetic_W[0] = R[0] * mGeomagnetic[0] + R[1] * mGeomagnetic[1] + R[2] * mGeomagnetic[2];
            mGeomagnetic_W[1] = R[3] * mGeomagnetic[0] + R[4] * mGeomagnetic[1] + R[5] * mGeomagnetic[2];
            mGeomagnetic_W[2] = R[6] * mGeomagnetic[0] + R[7] * mGeomagnetic1] + R[8] * mGeomagnetic[2];

         */
        //  setText(newstr);
    }

    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
    /*
    private float[] matrixMultiplication(float[] A, float[] B) {
        float[] result = new float[9];

        result[0] = A[0] * B[0] + A[1] * B[3] + A[2] * B[6];
        result[1] = A[0] * B[1] + A[1] * B[4] + A[2] * B[7];
        result[2] = A[0] * B[2] + A[1] * B[5] + A[2] * B[8];

        result[3] = A[3] * B[0] + A[4] * B[3] + A[5] * B[6];
        result[4] = A[3] * B[1] + A[4] * B[4] + A[5] * B[7];
        result[5] = A[3] * B[2] + A[4] * B[5] + A[5] * B[8];

        result[6] = A[6] * B[0] + A[7] * B[3] + A[8] * B[6];
        result[7] = A[6] * B[1] + A[7] * B[4] + A[8] * B[7];
        result[8] = A[6] * B[2] + A[7] * B[5] + A[8] * B[8];

        return result;
    }
    private float[] getRotationMatrixFromOrientation(float[] o) {
        float[] xM = new float[9];
        float[] yM = new float[9];
        float[] zM = new float[9];

        float sinX = (float)Math.sin(o[1]);
        float cosX = (float)Math.cos(o[1]);
        float sinY = (float)Math.sin(o[2]);
        float cosY = (float)Math.cos(o[2]);
        float sinZ = (float)Math.sin(o[0]);
        float cosZ = (float)Math.cos(o[0]);

        // rotation about x-axis (pitch)
        xM[0] = 1.0f; xM[1] = 0.0f; xM[2] = 0.0f;
        xM[3] = 0.0f; xM[4] = cosX; xM[5] = sinX;
        xM[6] = 0.0f; xM[7] = -sinX; xM[8] = cosX;

        // rotation about y-axis (roll)
        yM[0] = cosY; yM[1] = 0.0f; yM[2] = sinY;
        yM[3] = 0.0f; yM[4] = 1.0f; yM[5] = 0.0f;
        yM[6] = -sinY; yM[7] = 0.0f; yM[8] = cosY;

        // rotation about z-axis (azimuth)
        zM[0] = cosZ; zM[1] = sinZ; zM[2] = 0.0f;
        zM[3] = -sinZ; zM[4] = cosZ; zM[5] = 0.0f;
        zM[6] = 0.0f; zM[7] = 0.0f; zM[8] = 1.0f;

        // rotation order is y, x, z (roll, pitch, azimuth)
        float[] resultMatrix = matrixMultiplication(xM, yM);
        resultMatrix = matrixMultiplication(zM, resultMatrix);
        return resultMatrix;
    }
    */
}