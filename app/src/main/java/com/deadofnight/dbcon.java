package com.deadofnight;

//The database functions.
//Based on one or more examples I found on the web
//In another version, I use HTTP calls to PHP/MySQL on a server
//  instead of a local SQLite database
//Somehow, all my location sensing logic ended up in here. 
//  Bad programmer. Oh well.
//The question being asked is which place am I nearest, 
//  which boils down to two things:
//(1) do I recognize reliably when I am near a location
//(2) when I am near multiple locations, 
//      do I recognize which I am nearest to
//It's all in amiatPlace
//(1) "Reducing the Calibration Effort for Location Estimation Using Unlabeled Samples"
//  gives a clue how many samples to take at each location
//  I've got the Correct button taking multiple scans over a short period
//  currently 10 over 10 seconds
//  the Name button takes one sample
//  update/confirm takes one sample
//(2) "Robust Indoor Positioning Provided by Real-Time RSSI Values in Unmodified WLAN Networks"
//  suggests histogram method and standard deviation methods
//  so do several others
//good numbers i've seen in articles: 5 antennas, 
//   30 samples per location
//   but really it's completely implementation dependent

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.wifi.ScanResult;
import android.util.Log;
import android.widget.Toast;

public class dbcon extends SQLiteOpenHelper {
	public SQLiteDatabase db;
	private static final String TAG = "walktones.dbcon";
	private static final String TABLE_NAME = "place";
	public int precision = 15; //just how close to an RSSI do we consider a match
	//day after a tornado 10 didn't work well
	//but weather probably has nothing to do with it
    //my php tests suggest 12 is the best number

	public double threshold = 0.20; //what "percentage" of a score is a match
	//40% match is too high for some of these algorithms
	
	//path depends on phone
	// private static String DB_PATH = "/data/data/com.deadofnight/databases/";
	private static String DB_PATH = "/Android/data/com.deadofnight/files/";
	//above caused file access error. not sure what path should be.

	private static String DB_NAME = "placedb";
	WalktoneScanner WalktoneScanner;
	public Context context;
 
	dbcon(Context context, WalktoneScanner WalktoneScanner) {
		super(context, DB_NAME, null, 3);
		db = this.getWritableDatabase(); //opens a private database

		/* creates and opens a public database
		 * not worth the effort to make it work right now
		 * only reason to do it is for convenience
		try {
			createDataBase();
			openDataBase();
		} catch (IOException e) {
			e.printStackTrace();
		}
		//http://developer.android.com/resources/samples/ApiDemos/src/com/example/android/apis/content/ExternalStorage.html
		*/
		this.context = context;
		this.WalktoneScanner = WalktoneScanner;
	}

	public void onOpen(SQLiteDatabase mdb) {
		//during development i sometimes want to start fresh every launch
		 mdb.execSQL("DROP TABLE IF EXISTS place");
		 mdb.execSQL("DROP TABLE IF EXISTS PLACE2PROPERTY");
		 mdb.execSQL("DROP TABLE IF EXISTS PROPERTY");
        //seems like it should drop before cfeating
        onCreate(mdb);

    }

	@Override
	public void onCreate(SQLiteDatabase mdb) {
		String sql = "CREATE TABLE " + TABLE_NAME + " ("
				+ "BSSID TEXT,"
				+ "TYPE TEXT DEFAULT 'wifi', " + "SOURCE TEXT DEFAULT 'gps', "
				+ "NAME TEXT default null, " + "LAT DOUBLE, " + "LONG DOUBLE, "
				+ "ALT integer, " + "CLAIMTIME unsigned big int" + ")";
		// mdb.execSQL(sql);
		// if (mdb!=null) {
		// this only happens if there are no tables in the first place?
		// mdb.execSQL("DROP TABLE IF EXISTS place");
		// mdb.execSQL("DROP TABLE IF EXISTS PLACE2PROPERTY");
		// mdb.execSQL("DROP TABLE IF EXISTS PROPERTY");
		// Toast.makeText(context, "stuff dropped", Toast.LENGTH_SHORT).show();
		// }

		// if (checkDataBase()) {
		//symbolic locations
		sql = "CREATE TABLE place" + " (" + "placeid integer primary key,"
				+ "name TEXT, lat double, long double" + ")";
		mdb.execSQL(sql);

    //relational table
		sql = "CREATE TABLE place2property" + " ("
				+ "place2propertyid integer primary key," + "placeid integer,"
				+ "propertyid integer,"
				+ "FOREIGN KEY(placeid) REFERENCES place(placeid),"
				+ "FOREIGN KEY(propertyid) REFERENCES property(propertyid)"
				+ ")";
		mdb.execSQL(sql);

    //usually RSSIs
		sql = "CREATE TABLE property" + " ("
				+ "propertyid integer primary key," + "type text,"
				+ "antenna text," + "power real, " + "lat DOUBLE, "
				+ "long DOUBLE " + ")";
		mdb.execSQL(sql);
		// }

		//Log.d(TAG, "onCreate() sql: " + sql);
	}
	
	/* begin stuff that will allow me to have a public database */
	/**
     * Creates a empty database on the system and rewrites it with your own database.
     * */
    public void createDataBase() throws IOException{
    	boolean dbExist = checkDataBase();
    	if(dbExist){
    		//do nothing - database already exists
    	}else{
    		//By calling this method an empty database will be created into the default system path
            //of your application so we are gonna be able to overwrite that database with our database.
        	this.getReadableDatabase();
        	try {
    			copyDataBase();
    		} catch (IOException e) {
        		throw new Error("Error copying database");
        	}
    	}
    }
 
    /**
     * Check if the database already exist to avoid re-copying the file each time you open the application.
     * @return true if it exists, false if it doesn't
     */
    private boolean checkDataBase(){
    	SQLiteDatabase checkDB = null;
 
    	try{
    		String myPath = DB_PATH + DB_NAME;
    		checkDB = SQLiteDatabase.openDatabase(myPath, null, SQLiteDatabase.OPEN_READONLY);
    	}catch(SQLiteException e){
    		//database doesn't exist yet.
    	}
 
    	if(checkDB != null){
    		checkDB.close();
    	}
    	return checkDB != null ? true : false;
    }
 
    /**
     * Copies your database from your local assets-folder to the just created empty database in the
     * system folder, from where it can be accessed and handled.
     * This is done by transferring bytestream.
     * */
    private void copyDataBase() throws IOException{
 
    	//Open your local db as the input stream
    	InputStream myInput = context.getAssets().open(DB_NAME);
 
    	// Path to the just created empty db
    	String outFileName = DB_PATH + DB_NAME;
 
    	//Open the empty db as the output stream
    	OutputStream myOutput = new FileOutputStream(outFileName);
 
    	//transfer bytes from the inputfile to the outputfile
    	byte[] buffer = new byte[1024];
    	int length;
    	while ((length = myInput.read(buffer))>0){
    		myOutput.write(buffer, 0, length);
    	}
 
    	//Close the streams
    	myOutput.flush();
    	myOutput.close();
    	myInput.close();
    }
 
    public void openDataBase() throws SQLException{
        String myPath = DB_PATH + DB_NAME;
    	db = SQLiteDatabase.openDatabase(myPath, null, SQLiteDatabase.OPEN_READONLY);
    }
	/* end the stuff that allows me to have a public database */
	
	public boolean placeExists(String name) {
		// it seriously will not let you do a select in an execSQL
		Cursor cur = this.db.query("place", new String[] { "name" }, "name='"
				+ name + "'", null, null, null, "name desc");
		int tmp = cur.getCount();
		if (cur != null && !cur.isClosed()) {
			cur.close();
		}
		if (tmp > 0) // optimize. use count(*)
			return true;
		else
			return false;
	}

	public long insertPlace(String name) {
		ContentValues values = new ContentValues(1);
		values.put("name", name);
		values.put("lat", WalktoneScanner.lat);
		values.put("long", WalktoneScanner.lon);
		long rowid = this.db.insert("place", "name", values);
		return rowid;
	}

	public double getLat(long pid) {
		//long pid=placeId(name);
		String sql="select avg(lat) from property,place2property where "+
		"property.propertyid=place2property.propertyid and "+
		"place2property.placeid="+pid+
		" and lat!=0.0 "+
//		" and type='gps'"+
		"";
		double lat=-999.0;
		Cursor cur=this.db.rawQuery(sql, null);
		if (cur.moveToFirst()) {
			lat=cur.getDouble(0);
 		}
		Log.w(TAG,"average latitude is:"+lat);
		sql="update place set lat="+lat+" where placeid="+pid;
		db.rawQuery(sql, null);
		return lat;
	}

	public double getLon(long pid) {
		//long pid=placeId(name);
		String sql="select avg(long) from place2property,property where "+
		"property.propertyid=place2property.propertyid and "+
		"place2property.placeid="+pid+
		" and property.long!=0 "+
//		" and property.type='gps'"+
		""; //lets me comment or add lines without error
		double lon=0.0;
		Cursor cur=this.db.rawQuery(sql, null);
		if (cur.moveToFirst())
			lon=cur.getDouble(0);
		Log.w(TAG,"average latitude longitude is:"+lon);
		sql="update place set long="+lon+" where placeid="+pid;
		db.rawQuery(sql, null);
		return lon;
	}
	
	public long insertCoords(double lat, double lon) {
		ContentValues values2 = new ContentValues(2);
		values2.put("lat", lat);
		values2.put("long", lon);
		values2.put("type", "gps");
		long rowid2 = this.db.insert("property", null, values2);
		return rowid2;
	}

	public long relateProperty2Place(long propertyid, long placeid) {
		ContentValues values3 = new ContentValues(2);
		values3.put("propertyid", propertyid);
		values3.put("placeid", placeid);
		long rowid3 = this.db.insert("place2property", "propertyid", values3);
		return rowid3;
	}

	public long placeId(String name) {
		String[] s = new String[] { "placeid" };
		Cursor cur = this.db.query("place", s, "name='" + name + "'", null,
				null, null, null);
		int placeidColumn = cur.getColumnIndex("placeid");
		cur.moveToFirst();
		long placeid = cur.getLong(placeidColumn);
		if (cur != null && !cur.isClosed()) {
			cur.close();
		}
		return placeid;
	}

	public void nameHome(List<ScanResult> antennas, double lat, double lon) {
		namePlace(antennas, lat, lon, "home");
	}

	public void namePlace(List<ScanResult> antennas, double lat, double lon, String name) {
		long rowid = 0;
Log.w(TAG,"nameplace latitude");
		if (!placeExists(name)) {
			rowid = insertPlace(name);
		} else {
			rowid = placeId(name);
		}
		long rowid2 = insertCoords(lat, lon);
		// long rowid3=
		relateProperty2Place(rowid2, rowid);
		getLat(rowid);
		getLon(rowid);
		if (antennas != null)
			for (ScanResult result : antennas) { // using antennas crashes it
				addAntenna(result, name);
			}
        if (WalktoneScanner.mSumGeomagnetic_W_avg != null) {
            Log.w(TAG,"before magnetic properties added");
            addMagx(name);
            addMagy(name);
            addMagz(name);
            Log.w(TAG,"after magnetic properties added");
        }
        Log.w(TAG,"after magnetic properties should have been added");
    }

	void showCounts() {
		String buff = "" + "there are " + placeCount() + " places defined"
				+ " and " + propertyCount() + " properties" + " and "
				+ relationCount() + " relations" +
				// " and "+antennaCount(name)+" antennas at "+name+
                //separate wifi and mag count
				"";
		Toast.makeText(context, buff, Toast.LENGTH_SHORT).show();
	}

    public void addMagx(String name) {
        long rowid = placeId(name);
        ContentValues values4 = new ContentValues(3);
        values4.put("antenna", "ax");
        values4.put("power", (int)WalktoneScanner.mSumGeomagnetic_W_avg[0]);
        values4.put("type", "mag");
        long rowid4 = this.db.insert("property", null, values4);

        ContentValues values5 = new ContentValues(2);
        values5.put("propertyid", rowid4);
        values5.put("placeid", rowid);
        // long rowid5=
        this.db.insert("place2property", "propertyid", values5);
    }

    public void addMagy(String name) {
        long rowid = placeId(name);
        ContentValues values4 = new ContentValues(3);
        values4.put("antenna", "ax");
        values4.put("power", (int)WalktoneScanner.mSumGeomagnetic_W_avg[1]);
        values4.put("type", "mag");
        long rowid4 = this.db.insert("property", null, values4);

        Log.w(TAG,"magnetic y before add "+values4.get("power"));

        ContentValues values5 = new ContentValues(2);
        values5.put("propertyid", rowid4);
        values5.put("placeid", rowid);
        // long rowid5=
        this.db.insert("place2property", "propertyid", values5);
    }

    public void addMagz(String name) {
        Log.w(TAG,"before magnetic x should have been added");
        long rowid = placeId(name);
        ContentValues values4 = new ContentValues(3);
        values4.put("antenna", "ax");
        values4.put("power", (int)(WalktoneScanner.mSumGeomagnetic_W_avg[2]));
        values4.put("type", "mag");
        long rowid4 = this.db.insert("property", null, values4);

        ContentValues values5 = new ContentValues(2);
        values5.put("propertyid", rowid4);
        values5.put("placeid", rowid);
        // long rowid5=
        this.db.insert("place2property", "propertyid", values5);
        Log.w(TAG,"after magnetic x should have been added");
    }

	public void addAntenna(ScanResult result, String name) {
		long rowid = placeId(name);
		ContentValues values4 = new ContentValues(3);
		values4.put("antenna", result.BSSID);
		values4.put("power", result.level);
		values4.put("type", "wifi");
		long rowid4 = this.db.insert("property", null, values4);

		ContentValues values5 = new ContentValues(2);
		values5.put("propertyid", rowid4);
		values5.put("placeid", rowid);
		// long rowid5=
		this.db.insert("place2property", "propertyid", values5);
	}

	public boolean insertProperties(String name) {
		return false;
	}

	public long propertyCount() {
		String[] s = new String[] { "propertyid" };
		Cursor cur = this.db.query("property", s, null, null, null, null, null);
		int count = cur.getCount();
		if (cur != null && !cur.isClosed()) {
			cur.close();
		}
		return count;
	}

    /*
	// given an antenna, see if it matches any records we have
	// doubt I'm using this anywhere and not sure why I would
	public long antennaMatches(ScanResult antenna) {
		Cursor cur = this.db.rawQuery(
				"select placeid from place2property,property"
						+ " where type='wifi'"
						+ " and property.propertyid=place2property.propertyid "
						+ " and property.antenna='" + antenna.BSSID + "'"
						+ " and property.power>=" + (antenna.level - precision)
						+ " and property.power<=" + (antenna.level + precision)
						+ "", null);
		int c = cur.getCount();
		if (cur != null && !cur.isClosed()) {
			cur.close();
		}
		return c; //the number of records that match
	}

	//even less sure about this one
	public long mightMatch(ScanResult antenna) {
		Cursor cur = this.db.rawQuery(
				"select placeid from place2property,property"
						+ " where type='wifi' "
						+ " and property.propertyid=place2property.propertyid "
						+ " and property.antenna='" + antenna.BSSID + "'" + "",
				null);
		if (cur.getCount() > 0)
			Toast.makeText(context,
					"have a record for this antenna, check strength",
					Toast.LENGTH_SHORT).show();
		int count = cur.getCount();
		if (cur != null && !cur.isClosed()) {
			cur.close();
		}
		return count;
	}
*/
	// okay, so it matches a bunch of entries
	// how many antennas do i match? who cares
	// how many places do i match? i care a little more
	// how many of my antennas match each places antennas
	// so which place am i at?
	// i'm at the one where all my antennas match the same place
	// all the possible matches are all the places each antenna matches
	// compare my sorted list of antennas to each place's?
	// i normally expect to see 7 antennas of a certain power for this place
	// i currently see 6 antennas of a certain power for here. good enough
	// i currently see 3 of them, not good enough.

	public String recognizePlace(List<ScanResult> antennas) {
		int knn=0;
		double cumlat=0.0;
		double cumlon=0.0;
		double cumlat2=0.0;
		double cumlon2=0.0;
		double cumpres=0.0;
		if (antennas == null)
			return "No Antennas";
		String sql =
		// "select distinct placeid from property"+
		// ",place2property"+
		// " where type='wifi'"+
		// " and property.propertyid=place2property.propertyid "+
		// could optimize, but why bother
		// " and property.antenna in (";
		// "";
		// loop through all defined places and run amiatPlace
		"select distinct name from place";
		Cursor cursor = this.db.rawQuery(sql, null);
		// List<String> list = new ArrayList<String>();
		// Cursor cursor = this.db.query(TABLE_NAME, new String[] { "name" },
		// null, null, null, null, "name desc");
		float strongestpresence=0;
		String matchedplace="[unknown]";
		if (cursor.moveToFirst()) {
			do {
				String n = cursor.getString(0);
				//Log.w(TAG, "testing " + antennas.size() + " if i am at "+ n);
				float presence=amiatPlace(antennas,n);
				//Log.w(TAG, "i am at " + n +" with a presence of " + presence);
				if (presence > 0) {
					knn++;
					long pid=placeId(n);
					String sql2="select lat,long from place where placeid="+pid;
					Cursor cur2=db.rawQuery(sql2,null);
					if (cur2.moveToFirst()) {
						double lat=cur2.getDouble(0);
						double lon=cur2.getDouble(1);
						cumlat+=lat;
						cumlon+=lon;
						cumpres+=presence;
						cumlat2+=lat*presence;
						cumlon2+=lon*presence;
					}
				}
				if (presence > strongestpresence) {
					matchedplace=n;
					strongestpresence=presence;
					//Log.w(TAG, "strongest presence at " + n );
				}
			} while (cursor.moveToNext());
		} else {
			//Log.w(TAG,"empty cursor");
		}
		if (cursor != null && !cursor.isClosed()) {
			cursor.close();
		}
		//Log.w(TAG, "by presence, returning " + matchedplace );
		//except knn is dumb if you can't predict your training point distribution, need wknn
		Log.w(TAG,"there are "+knn+" neighbors with a knn lat,lon of "+cumlat/knn + ","+cumlon/knn);
		Log.w(TAG,"there are "+knn+" neighbors with a wknn lat,lon of "+cumlat2/cumpres + ","+cumlon2/cumpres);
		//sort of a wknn with a variable k. i read about something similar
		//add all the presences
		//multiply each latitude by its presence
		//and divide by the sum of its presences
		//the knn also has a variable k, dont recall if the one i read about
		//is a knn or a wknn variant
		//i should eventually display them on the main.xml
		//maybe store them in the console temporarily

		return matchedplace;
	}

	//a measure of my presence at a place, given a set of antennas and their strengths
	//given originally in a score, now in a percentage
	public float amiatPlace(List<ScanResult> antennas, String name) {
		float score = 0;
		float maxscore = 0;

		//the next blocks are wifi fingerprint recognition methods
		//rather than using only one, can use all weighted differently
		//so as to get benefits of multiple methods
		//while mitigating the drawbacks
		//apart from weights, some of these methods work better with
		//different numbers of samples, different thresholds, different precision
		double alg1weight=0.0; //all observed vs all possible
		double alg2weight=0.0; //all possible vs observed
		double alg3weight=0.0; //observed vs average
		double alg4weight=0.0; //proximity
		double alg5weight=0.0; //k strongest antennas
		double alg6weight=0.0; //bad histogram code, don't use
		double alg7weight=0.0; //better histogram code
		double alg8weight=0.0; //difference code 1
		double alg9weight=0.0; //difference code 2
		double alg10weight=0.0; //standard deviation
        double alg11weight=1.0; //magnetic

        //original well functioning block
		//compare all observed antennas vs all possible antennas
		//originally used an absolute score based on how close each
		//nearby antenna matched any and all expected ones
		//works well for one matching location
		//but not as well for competing locations
		//correcting one location would cause it to have a higher score
		//just based on the fact it had more recorded entries
		//I don't even know how well it works now that this function is based
		//on a ratio of score versus maxscore
		//correcting a location probably doesn't happen very quickly and may
		//require correcting from both physical locations
		//a wrongly identified location is a convergence of two problems
		//(1) a false positive, whereby a location scores too strongly
		//(2) a false negative, whereby a location scores too weakly
		//time complexity scales with the number of observed antennas
		//this worked pretty well with only a couple samples per location
		if (alg1weight>0)
			for (ScanResult antenna : antennas) {
				int temp=antennaisatPlace(antenna, name);
				maxscore += precision*alg1weight;
				if (temp>0) //in case for some reason its negative
					score+=temp*alg1weight;
			}

		//all possible antennas vs observed
		//computes score based on how close each expected antenna matches a nearby one
		//meant to be used with percent of maximum score rather than absolute score
		//should work better for competing locations, but doesn't really
		//my directions to myself were:
		//okay, yeah, this isn't working as well as it could
		//especially the update to reinforce a location definition part
		//antennaCount needs to stop using distinct, and use all the entries
		//correspondingly, i need to reverse the way this works
		//instead of looping through antennas and matching them to entries
		//i need to loop through recorded entries and match to live antennas
		//so this for loop is going to be based on an sql query
		//then, instead of asking if antenna is at place,
		//i need to ask if this antenna property is nearby
		//so i take a bssid and a strength from the row and the antennas list
		//in the function loop through the antennas or if list was indexed or
		//sorted by bssid maybe i could do it another way. but we're talking only
		//a difference of 1 check vs ~12 checks. worth it only if convenient
		//time complexity scales with observed antennas*samples*observed antennas
		//worked pretty well with 1-10 samples per location
		long plid=placeId(name);
		Cursor cursor=null;
		String sql =
			"select antenna, power from"+
			" property,place2property"+
			" where (type='wifi' or type='mag')"+
			" and property.propertyid=place2property.propertyid "+
			" and place2property.placeid="+plid;
		if (alg2weight>0) {
			cursor = this.db.rawQuery(sql, null);
			if (cursor.moveToFirst()) {
				do {
					maxscore+=precision*alg2weight;
					for (ScanResult antenna : antennas) {
						Long power=cursor.getLong(1);
						String antennab=cursor.getString(0);
						//Log.w(TAG,"antenna from db:"+antennab+" vs antenna from scan:"+antenna.BSSID);
						if (antennab.equalsIgnoreCase(antenna.BSSID)) {
							long diff=Math.abs(antenna.level-power);
							if (diff<precision)
								score+=(precision-diff)*alg2weight;
						}
					}
				} while (cursor.moveToNext());
			} else {
				//Log.w(TAG,"empty cursor");
			}
			if (cursor != null && !cursor.isClosed()) {
				cursor.close();
			}
		}

		//the last of my original methods
		//observed vs average
		//where all observed antennas' strengths are compared
		//to the average of all the possible expected values for that antenna
		if (alg3weight>0)
			for (ScanResult antenna : antennas) {
				int temp=antennaavgatPlace(antenna, name);
				maxscore += precision*alg3weight;
				if (temp>0) //in case for some reason its negative. it never should be.
					score+=temp*alg3weight;
			}

		//the first of the published methods I tried: Proximity
		//"Survey of Wireless Indoor Positioning Techniques and Systems" II.C.
		//never had a chance of working
		//requires uniformly spaced antennas that are as close together as you need
		//your accuracy to be
		if (alg4weight>0) {
			int maxstrength=-1000;
			String strongestantenna="";
			for (ScanResult antenna : antennas) {
				if (antenna.level>maxstrength) {
					maxstrength=antenna.level;
					strongestantenna=antenna.BSSID;
				}
			}
			sql =
				"select antenna from"+
				" property,place2property"+
				" where type='wifi'"+
				" and property.propertyid=place2property.propertyid "+
				" and place2property.placeid="+plid+
				" order by power desc limit 1";
			cursor = this.db.rawQuery(sql, null);
			if (cursor.moveToFirst()) {
				String strongestexpected=cursor.getString(0);
				//Log.w(TAG,"alg4:"+name+" expected bssid:"+strongestexpected +" saw:"+strongestantenna);
				if (strongestantenna.equals(strongestexpected)) //in case for some reason its negative. it never should be.
					score+=precision*alg4weight; //full score for simply having the expected strongest antenna
			}
			maxscore += precision*alg4weight;
		}

		//only the k strongest antennas are used
		//doesn't actually seem to work any better
		int k=3;
		if (alg5weight>0) {
			sql =
				"select antenna, avg(power) as dog from"+
				" property,place2property"+
				" where type='wifi'"+
				" and property.propertyid=place2property.propertyid "+
				" and place2property.placeid="+plid+
				" group by antenna" +
				" order by dog desc limit "+k;
			cursor = this.db.rawQuery(sql, null);
			if (cursor.moveToFirst()) {
				do {
					Long pow=cursor.getLong(1);
					String ant=cursor.getString(0);
					//Log.w(TAG,"place:"+name+" ant:"+ant+" avgpow:"+pow);
					for (ScanResult antenna : antennas) {
						if (ant.equalsIgnoreCase(antenna.BSSID)) {
							long diff=Math.abs(antenna.level-pow);
							score+=(precision-diff)*alg5weight;
							maxscore += precision*alg5weight;
						}
					}
				} while (cursor.moveToNext());
			} else {
				//Log.w(TAG,"empty cursor");
			}
			if (cursor != null && !cursor.isClosed()) {
				cursor.close();
			}
		}
		
		//bad histogram code
		//doesn't work right
		//doing it all backwards
		//don't believe I left this in a working state at all
		if (alg6weight>0){
			k=5;
			sql =
				"select antenna,power,count(*) as cow from property,place2property where placeid="+plid
				+" and place2property.propertyid=property.propertyid "
				+" and property.type='wifi'"
				+" group by property.power"
				+" order by cow desc limit 5";
			//loop through the 5 powers
			//if it matches, increase score and maxscore
			cursor = this.db.rawQuery(sql, null);
			if (cursor.moveToFirst()) {
				int scorething=precision;
				do {
					//Long pow=cursor.getLong(1);
					String ant=cursor.getString(0);
					//Long cow=cursor.getLong(2);
					scorething--;
					for (ScanResult antenna : antennas) {
						if (ant.equalsIgnoreCase(antenna.BSSID)) {
							//Log.w(TAG,"hist place:"+name+" ant:"+ant+" pow:"+pow+" count:"+cow+" sc:"+scorething);
							score+=(scorething)*alg6weight;
							continue;
						}
					}
					maxscore += precision*alg6weight;
				} while (cursor.moveToNext());
			} else {
				//Log.w(TAG,"empty cursor");
			}
			if (cursor != null && !cursor.isClosed()) {
				cursor.close();
			}
		}

		//better histogram code
		//compares observed strength to most common recorded strength for that ant at that place
		//and also next most common and next most common and assigns a score accordingly
		//requires lots of entries per location
		//it works, but its lousy
		if (alg7weight>0)
			for (ScanResult antenna : antennas) {
				//Log.w(TAG,"checking observed against mode for bssid:"+antenna.BSSID+name);
				int temp=antennalikelyatPlace(antenna, name);
				maxscore += precision*alg7weight;
				if (temp>0) //in case for some reason its negative
					score+=temp*alg7weight;
			}

		//difference code attempt 1
		//compare every observed antenna's strength with the strength of the one before it
		//could also do a version where i compare each observed antenna's strength with the strongest
		//but i don't see that it is worth it after having seen this
		//can't tell the difference between 1 room facing west
		//and the next room 3 meters away facing south
		int iter=0;
		int odiff=100;
		int ddiff=100;
		ScanResult lastantenna=null;
		if (alg8weight>0)
			for (ScanResult antenna : antennas) {
				iter++;
				if (iter==1) {
				}
				else {
					odiff=Math.abs(lastantenna.level-antenna.level);
					int t1=antennaexpavgatPlace(antenna,name);
					int t2=antennaexpavgatPlace(lastantenna,name);
					int ediff=Math.abs(t1-t2);
					//Log.w(TAG,"observed vec diff "+odiff+" vs "+ediff+" expected");
					maxscore+=precision*alg8weight;
					ddiff=Math.abs(odiff-ediff);
					if (ddiff<precision) score+=(precision-ddiff)*alg8weight;
				}
				lastantenna=antenna;
			}

		//difference code attempt 2
		//compare every antenna to every other
		//seems to work better than difference code attempt 1
		//if i base my score entirely on how closely observed antennas match
		//the upside is that there is no penalty if someone swaps out an AP
		//the downside is that there is no penalty if I don't see something I should
		//
		//on the other hand, were i to loop through all possible expected antennas
		//i could be forever penalized due to an aberration that appeared once
		odiff=100;
		ddiff=100;
		lastantenna=null;
		if (alg9weight>0)
			for (ScanResult outerantenna : antennas) {
				antennastddevatPlace(outerantenna,name);
				for (ScanResult innerantenna : antennas) {
					if (outerantenna.equals(innerantenna)) continue;
					//if inner=outer we expected 0 signal, get 0 signal and rack up a lot
					//of points for every single antenna in range
					odiff=Math.abs(outerantenna.level-innerantenna.level);
					int t1=antennaexpavgatPlace(innerantenna,name);
					int t2=antennaexpavgatPlace(outerantenna,name);
					if (t1==0 || t2==0) continue; //not a valid value, even if i'm sitting on it
					//Log.w(TAG,name+" expected avg outer "+t2);
					//Log.w(TAG,name+" expected avg inner "+t1);
					int ediff=Math.abs(t1-t2);
					//Log.w(TAG,name+" observed vec diff "+odiff+" vs "+ediff+" expected");
					maxscore+=precision*alg9weight;
					ddiff=Math.abs(odiff-ediff);
					if (ddiff<precision) score+=(precision-ddiff)*alg9weight;
				}
			}
		
		//standard deviation code
		if (alg10weight>0)
			for (ScanResult antenna : antennas) {
				//Log.w(TAG,"checking observed against mode for bssid:"+antenna.BSSID+name);
				double temp=antennastddevatPlace(antenna, name);
				maxscore += precision*alg10weight;
				if (temp>0) //in case for some reason its negative
					score+=temp*alg10weight*precision;
			}
		//Log.w(TAG,"place "+name+" has "+antennaCount(name)+" properties");

        //magnetic code
        int mdiff=100;
        if (alg11weight>0) {
            //compare current average to stored averages
            maxscore+=300*alg11weight;
            int magx=magexpavgatPlace("x", name);
            int magy=magexpavgatPlace("y", name);
            int magz=magexpavgatPlace("z", name);
            score+=(100-Math.abs(WalktoneScanner.mSumGeomagnetic_W_avg[0]-magx))*alg11weight*precision;
            score+=(100-Math.abs(WalktoneScanner.mSumGeomagnetic_W_avg[1]-magy))*alg11weight*precision;
            score+=(100-Math.abs(WalktoneScanner.mSumGeomagnetic_W_avg[2]-magz))*alg11weight*precision;
            //TODO: output differences and originals
            Log.w(TAG, "magx_e " + magx +" magy_e " + magy+ " magz_e " + magz);
            Log.w(TAG, "magx_o " + WalktoneScanner.mSumGeomagnetic_W_avg[0] +" magy_o " + WalktoneScanner.mSumGeomagnetic_W_avg[1]+ " magz_o " + WalktoneScanner.mSumGeomagnetic_W_avg[2]);
        }

		if (score>0)  Log.w(TAG, score + "/" + maxscore + " match to " + name);
		if (score > ((maxscore) * threshold)) { //match
			//trilateration implies I only need 4 antennas to locate myself in 3D, so when going on pure points
			//I called anything that was at least a match to 4 antennas good enough for a match
			//point way
			//		if (score > (Math.max(4, (int) (maxscore) * threshold))) {
			//			return score;
			//percent way
			// if i work by percentage of maxscore, no "correct" button can ever work well
			// also true if i work with averages
			// but if i work with pure number of matches, the one i have more scans of
			// always wins, even if its less of a match
			//Log.w(TAG,score/maxscore + " / "+ threshold + " pct to "+ name);
			return (float)(score/maxscore);
		} else { //not a match
			return 0;
		}
	}

	public double antennastddevatPlace(ScanResult antenna, String name) {
		long id = placeId(name);
		String sql = "select "
				+ " property.power"
//				+ " avg(property.power) as average, count(*) as cow "
//				+ " property.power-average as deviation,"
//				+ " deviation*deviation as square"
				+ " from property,place2property"
				+ " where type='wifi'"
				+ " and property.propertyid=place2property.propertyid "
				+ " and property.antenna='" + antenna.BSSID + "'"
				+ " and place2property.placeid=" + id
//				+ " group by power"
//				+ " order by cow desc"
//				+ " limit "+precision
				+ "";
				
		Cursor cur = this.db.rawQuery(sql, null);
		double score = 0;
		int average = 0;
		int count=0;
		int deviation=0;
		int square=0;
		int sum=0;

		average=antennaexpavgatPlace(antenna,name);
		Log.w(TAG,"average "+average);
		if (cur.moveToFirst()) {
			do {
				 count++;
				 //Log.w(TAG,"level "+cur.getInt(0));
				 deviation=cur.getInt(0)-average;
				 //Log.w(TAG,"deviation "+deviation);
				 square=deviation*deviation;
				 //Log.w(TAG,"square "+square);
				 sum+=square;
//				 average=cur.getInt(0);
//				 count=cur.getInt(1);
//				 deviation=cur.getInt(2);
//				 square=cur.getInt(3);
//				 sum+=square;
			} while (cur.moveToNext());
		} else {
			//Log.w(TAG,"empty cursor");
		}
		if (cur != null && !cur.isClosed()) {
			cur.close();
		}
		double stddev=Math.sqrt(sum/(count-1)); //sqrt of variance
Log.w(TAG,"standard deviation "+stddev);
		if (antenna.level<(average+stddev*2) && antenna.level>(average-stddev*2))
			score+=1;//0.6826895;
		else if (antenna.level<(average+stddev*2) && antenna.level>(average-stddev*2))
			score+=0.9544997-0.6826895;
		if (score < 0)
			score=0;
		return score;
	}
	
	public int antennalikelyatPlace(ScanResult antenna, String name) {
		long id = placeId(name);
		String sql = "select "
				+ " property.power, count(*) as cow "
				+ " from property,place2property"
				+ " where type='wifi'"
				+ " and property.propertyid=place2property.propertyid "
				+ " and property.antenna='" + antenna.BSSID + "'"
				+ " and place2property.placeid=" + id
				+ " group by power"
				+ " order by cow desc"
				+ " limit "+precision;
				
		Cursor cur = this.db.rawQuery(sql, null);
		int score = 0;
		int expected=0;

		if (cur.moveToFirst()) {
			int iter=1;
			do {
				 expected=cur.getInt(0);
				 //Log.w(TAG,iter+" most likely power is "+expected+"("+cur.getInt(1)+") observed is "+antenna.level);
				 if (expected==antenna.level) {
						long diff=Math.abs(antenna.level-expected);
						score+=(precision-diff);
						//Log.w(TAG,name+" mark "+iter);
				 }
				 iter++;
			} while (cur.moveToNext());
			Log.w(TAG,"iter: "+iter);
		} else {
			//Log.w(TAG,"empty cursor");
		}
		if (cur != null && !cur.isClosed()) {
			cur.close();
		}
		if (score < 0)
			score=0;
		//Log.w(TAG,"place:"+name+" average strength: "+ expected + " observed strength: "+antenna.level+ " score: "+score);
		return score;
	}
	
	public int antennaexpavgatPlace(ScanResult antenna, String name) {
		long id = placeId(name);
		String sql = "select "
				+ " avg(property.power) "
				+ " from property,place2property"
				+ " where type='wifi'"
				+ " and property.propertyid=place2property.propertyid "
				+ " and property.antenna='" + antenna.BSSID + "'"
				+ " and place2property.placeid=" + id
				+ "";
		
		Cursor cur = this.db.rawQuery(sql, null);
		int count = cur.getCount();
		int expected=0;

		if (count > 0) {
			 cur.moveToFirst();
			 expected=cur.getInt(0);
		}
		
		if (cur != null && !cur.isClosed()) {
			cur.close();
		}
		return expected;
	}
	
	public int antennaavgatPlace(ScanResult antenna, String name) {
		long id = placeId(name);
		String sql = "select "
				+ " avg(property.power) "
				+ " from property,place2property"
				+ " where type='wifi'"
				+ " and property.propertyid=place2property.propertyid "
				+ " and property.antenna='" + antenna.BSSID + "'"
				+ " and place2property.placeid=" + id
				+ "";
		
		Cursor cur = this.db.rawQuery(sql, null);
		int count = cur.getCount();
		int score = 0;
		int expected=0;

		if (count > 0) {
			 cur.moveToFirst();
			 expected=cur.getInt(0);
     		 score = precision- (Math.abs(expected-antenna.level));
		}
		
		if (cur != null && !cur.isClosed()) {
			cur.close();
		}
		if (score < 0)
			score=0;
		//Log.w(TAG,"place:"+name+" average strength: "+ expected + " observed strength: "+antenna.level+ " score: "+score);
		return score;
	}
	
	public int antennaisatPlace(ScanResult antenna, String name) {
		long id = placeId(name);
		String sql = "select "
				+ "abs(property.power + "+(antenna.level*(-1))+") as diff,"
				+ " property.power "
				+ " from property,place2property"
				+ " where type='wifi'"
				+ " and property.propertyid=place2property.propertyid "
				+ " and property.antenna='" + antenna.BSSID + "'"
				+ " and diff<="+precision
				+ " and place2property.placeid=" + id
				+ " order by diff asc limit 1"
				+ "";
//		+ " and property.power>=" + (antenna.level - precision)
//		+ " and property.power<=" + (antenna.level + precision)
//i don't get how sometimes i get the wrong place
//this order by clause should mean the place i have the greatest presence at
//is where i am, not just any old place i happen to match the criteria for
		
		Cursor cur = this.db.rawQuery(sql, null);
		int count = cur.getCount();
		int score = 0;
		//int expected = 0;

		if (count > 0) {
			 cur.moveToFirst();
     		 score= precision-cur.getInt(0);
     		 //expected = cur.getInt(1);
		}
		
		if (cur != null && !cur.isClosed()) {
			cur.close();
		}
		//Log.w(TAG," iscore: "+score + " antenna: "+antenna.level+ " expected: "+expected);
		return score;
	}

	public long antennaCount(String name) {
		long id = placeId(name);
		// String[] s = new String[] {"antenna"};
		// Cursor cur = this.db.query("property", s, "type='wifi'", null, null,
		// null, null);
		Cursor cur = this.db.rawQuery("select antenna from property"
				+ ",place2property,place" + " where type='wifi' "
				+ "and property.propertyid=place2property.propertyid "
				+ "and place2property.placeid=" + id
				+ " and place.name='"+name+"'", null);
		int count = cur.getCount();
		if (cur != null && !cur.isClosed()) {
			cur.close();
		}
		return count;
	}

	public long relationCount() {
		String[] s = new String[] { "placeid" };
		Cursor cur = this.db.query("place2property", s, null, null, null, null,
				null);
		int count = cur.getCount();
		if (cur != null && !cur.isClosed()) {
			cur.close();
		}
		return count;
	}

	//number of symbolic locations defined in radio map
	public long placeCount() {
		String[] s = new String[] { "placeid" };
		Cursor cur = this.db.query("place", s, null, null, null, null, null);
		int count = cur.getCount();
		if (cur != null && !cur.isClosed()) {
			cur.close();
		}
		return count;
	}

	// get the number of coordinate records for this place
	public long findCoords(String name) {
		long id = placeId(name);
		String[] s = new String[] { "placeid" };
		Cursor cur = this.db.query("property", s, "type='gps' AND id=" + id,
				null, null, null, null);
		int count = cur.getCount();
		if (cur != null && !cur.isClosed()) {
			cur.close();
		}
		return count;
	}

	public long updatePlace(String name) {
		// we don't actually update place, place only stores the name
		// we don't update property either, we add more
		// first, get the placeid of the place
		return 1;
	}

	public void deleteAll() {
		this.db.delete("place", null, null);
		this.db.delete("property", null, null);
	}

	public boolean deletePlace(String name) {
		return true;
	}
	
	//all the defined locations in the radio map
	//used in the "Correct" button list
	public List<String> selectAll() {
		List<String> list = new ArrayList<String>();
		Cursor cursor = this.db.rawQuery("select distinct name from place",null);
		if (cursor.moveToFirst()) {
			do {
		            list.add(cursor.getString(0));
		         } while (cursor.moveToNext());
		}
		if (cursor != null && !cursor.isClosed()) {
			cursor.close();
		}
		return list;
	}
	
    //A placeholder from the database example I based this part
    //  of the app on
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
       //Log.w(TAG, "Upgrading database, this will drop tables and recreate.");
       //db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
       //onCreate(db);
    }

    public int magexpavgatPlace(String axis, String name) {
        long id = placeId(name);
        String sql = "select "
                + " avg(property.power) "
                + " from property,place2property"
                + " where type='mag'"
                + " and property.propertyid=place2property.propertyid "
                + " and property.antenna='" + axis + "'"
                + " and place2property.placeid=" + id
                + "";
/* this was to test if it was all zeroes
        sql = "select "
                + " avg(property.power) "
                + " from property,place2property"
                + " where type='mag'"
                + " and property.propertyid=place2property.propertyid "
                + " and place2property.placeid=" + id
                + ""; // -1 instead of 0
*/
        Cursor cur = this.db.rawQuery(sql, null);
        int count = cur.getCount();
        int expected=0;
        Log.w(TAG,"magnetic property count "+count);

        if (count > 0) {
            cur.moveToFirst();
            expected=cur.getInt(0);
        }
        Log.w(TAG,"magnetic expected "+" "+axis+" "+expected);

        if (cur != null && !cur.isClosed()) {
            cur.close();
        }
        return expected;
    }
}
