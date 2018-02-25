package com.example.katherinezhang.dronepathcharting;

import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;

import java.util.ArrayList;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import android.os.Environment;
import java.io.BufferedReader;
import java.io.FileReader;

import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnMapClickListener;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.GoogleMap.OnCameraChangeListener;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.CameraPosition;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;


public class MapsActivity extends FragmentActivity implements OnClickListener, OnMapClickListener, OnMapReadyCallback, OnCameraChangeListener {
    protected static final String TAG = "MapsActivity";

    private GoogleMap aMap;

    private Button locate, add, clear;
    private Button config, loop, chart, save;

    private int mCheckedId = R.id.normalMap;
    private boolean isAdd = false;

    private double droneLocationLat = 37.317517;
    private double droneLocationLng = -121.971420;
    private String placeName = "The Harker School";

    private float altitude = 100.0f;
    private float speed = 3.0f;

    private ArrayList<LatLng> waypoints = new ArrayList<LatLng>();
    private boolean isLoop = true;
    // limited by DJI's API per mission - we are currently using only one mission
    // per flight.
    private static final int MAX_WAYPOINTS = 98;
    // in meters
    private static final int MIN_DISTANCE_INBETWEEN = 2;
    private boolean isCharted = false;
    private Polyline curvedPath = null;
    private Polyline straightPath = null;
    private File folder = null;
    private String dronePathDir = null;
    private String curvedPathFileName = "curved-path";
    private String straightPathFileName = "straight-path";
    private static final int ZOOM_LEVEL = 17;
    private int zoomLevel = ZOOM_LEVEL;
    private CameraPosition cameraPos = null;
    private ArrayList<LatLng> waypts = new ArrayList<LatLng>();
    private boolean isSettingsDialogShown = false;
    private boolean isLocateDialogShown = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);

        mapFragment.getMapAsync(this);

        locate = (Button) findViewById(R.id.locate);
        add = (Button) findViewById(R.id.add);
        clear = (Button) findViewById(R.id.clear);
        config = (Button) findViewById(R.id.config);
        loop = (Button) findViewById(R.id.loop);
        chart = (Button) findViewById(R.id.chart);
        save = (Button) findViewById(R.id.save);


        locate.setOnClickListener(this);
        add.setOnClickListener(this);
        clear.setOnClickListener(this);
        config.setOnClickListener(this);
        loop.setOnClickListener(this);
        chart.setOnClickListener(this);
        save.setOnClickListener(this);


        RadioGroup mapType_RG = (RadioGroup) findViewById(R.id.mapType);
	mapType_RG.check(R.id.normalMap);
        mapType_RG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {

		@Override
		    public void onCheckedChanged(RadioGroup group, int checkedId) {
		    // TODO Auto-generated method stub
		    mCheckedId = checkedId;
		    setMapType();
		}
	    });

	dronePathDir = Environment.getExternalStorageDirectory() + "/DRONEPATH";
        folder = new File(dronePathDir);
        boolean success = false;
        if(!folder.exists()){
            success = folder.mkdir();
        }
        if (!success){ 
            Log.d(TAG, "Folder not created.");
        }
        else{
            Log.d(TAG, "Folder created!");
        }
    }

    void setMapType() {
	if (mCheckedId == R.id.satelliteMap) {
	    // Use the satellite map
	    aMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
	} else if (mCheckedId == R.id.hybridMap) {
	    // Use the normal and satellite hybrid map
	    aMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
	} else {
	    aMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
	}
    }

    @Override
    public void onMapClick(LatLng point) {
        if (isAdd == true){
            markWaypoint(point);
            //Add waypoints to Waypoint arraylist;
            waypoints.add(point);
	    clearPaths();
        }
    }

    private void clearPaths() {
	if (curvedPath != null) {
	    curvedPath.remove();
	    curvedPath = null;
        waypts.clear();
	}
	if (straightPath != null) {
	    straightPath.remove();
	    straightPath = null;
	}
        isCharted = false;
    }

    private void doSplineInterpolation() {
        waypts.clear();
	if (waypoints.size() == 0) {
	    return;
	} else if (waypoints.size() < 3) {
	    waypts.add(waypoints.get(0));
        if (waypoints.size() == 2) {
            waypts.add(waypoints.get(1));
            if (isLoop) waypts.add(waypoints.get(0));
        }
	} else {
	    int cnt = waypoints.size();
	    if (isLoop) {
		cnt += 2;
	    }

	    double[] index = new double[cnt];
	    double[] lats = new double[cnt];
	    double[] lngs = new double[cnt];
	    double[] dists = new double[isLoop? cnt - 2 : cnt - 1];
	    double total_distance = 0.0;

	    int i = 0;

	    for (; i < waypoints.size(); i++) {
		index[i] = i;
		lats[i] = waypoints.get(i).latitude;
		lngs[i] = waypoints.get(i).longitude;
		if (i < waypoints.size() - 1) {
		    dists[i] = SphericalUtil.computeDistanceBetween(waypoints.get(i), waypoints.get(i+1));
		    total_distance += dists[i];
		}
		Log.d("Interpolation", "Interpolating latitude: " + lats[i] + ", longitude: " + lngs[i]);
	    }

	    if (isLoop) {
		// For closed curve flight, go back to the starting point
		index[i] = i;
		lats[i] = waypoints.get(0).latitude;
		lngs[i] = waypoints.get(0).longitude;
		dists[i - 1] = SphericalUtil.computeDistanceBetween(waypoints.get(i-1), waypoints.get(0));
		total_distance += dists[i - 1];
		Log.d("Interpolation", "Interpolating latitude: " + lats[i] + ", longitude: " + lngs[i]);

		// To prevent clamping at the closure point, use the second
		// point as the ending interpolation point, but don't actually revisit it,
		// unlike for the first point, which we have to revisit to enclose.
		i++;
		index[i] = i;
		lats[i] = waypoints.get(1).latitude;
		lngs[i] = waypoints.get(1).longitude;
		Log.d("Interpolation", "Interpolating latitude: " + lats[i] + ", longitude: " + lngs[i]);
	    }

	    SplineInterpolator s = new SplineInterpolator();
	    PolynomialSplineFunction latFunc = s.interpolate(index, lats);
	    PolynomialSplineFunction lngFunc = s.interpolate(index, lngs);
	    
	    // Now discretize the curve by choosing interpolated points
	    // as evenly as possible, but always include the input points.

	    int ptsToInterp = MAX_WAYPOINTS - (dists.length + 1);
	    for (i = 0; i < dists.length; i++) {
		waypts.add(waypoints.get(i));
		int ptsForSeg = (int)(dists[i] * ptsToInterp / total_distance);
		double prev_t = i;
		for (int j = 1; j <= ptsForSeg; j++) {
		    double t = i + (double) j / (ptsForSeg + 1);
		    if (t <= prev_t) {
			t = prev_t + (double) 1/ (ptsForSeg + 1);
		    }
		    LatLng l = new LatLng(latFunc.value(t), lngFunc.value(t));
		    while (t < i + 1 &&
			   (SphericalUtil.computeDistanceBetween(waypts.get(waypts.size() - 1), l) < MIN_DISTANCE_INBETWEEN ||
			    SphericalUtil.computeDistanceBetween(l, waypoints.get(isLoop?0:(i+1))) < MIN_DISTANCE_INBETWEEN)) {
			t += 0.1 * 1 / (ptsForSeg + 1);
			if (t >= i + 1) {
			    break;
			}
			l = new LatLng(latFunc.value(t),lngFunc.value(t));
		    }
		    if (t < i + 1) {
			prev_t = t;
			waypts.add(l);
		    } else {
			break;
		    }
		}
		ptsToInterp -= ptsForSeg;
		total_distance -= dists[i];
	    }

	    if (isLoop) {
		waypts.add(waypoints.get(0));
	    }
	    else {
		waypts.add(waypoints.get(waypoints.size() - 1));
	    }
	    
	}
    }

    private void savePath(String name, ArrayList<LatLng> wpts) {
        try {
            File file = new File(folder, name);
            PrintWriter writer = new PrintWriter(new FileOutputStream(file));
            for (LatLng wpt: wpts) {
                writer.printf("%f;%f;%f;%f", wpt.latitude, wpt.longitude, altitude, speed);
                writer.println();
            }
            if (isLoop && wpts == waypoints && waypoints.size() > 1) {
                LatLng wpt = waypoints.get(0);
                writer.printf("%f;%f;%f;%f", wpt.latitude, wpt.longitude, altitude, speed);
                writer.println();
            }
            writer.close();
        }
        catch (Exception e) {
            Log.d(TAG, "Failure writing to file " + name + ": " + e);
        }
        File file = new File(folder, name);
        BufferedReader in = null;
        try {
            in = new BufferedReader(new FileReader(file));
            int count = 0;
            String read = in.readLine();
            while (read != null) {
                Log.d(TAG, read);
                read = in.readLine();
                count++;
            }

            Log.d(TAG, "Number of points is: " + count);
            setResultToToast("Saved " + count + " Waypoints to " + name);
            in.close();
        }
        catch (Exception e) {
            Log.d(TAG, "Failure reading from file " + name + ": " + e);
        }
    }
    
    private void markWaypoint(LatLng point){
        //Create MarkerOptions object
        MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(point);
        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE));
        aMap.addMarker(markerOptions);
    }

    private void markWaypoints() {
	for (LatLng wpt: waypoints) {
	    markWaypoint(wpt);
	}
    }

    @Override
    public void onClick(View v) {
        // TODO Auto-generated method stub
        switch (v.getId()) {
            case R.id.locate:{
                showLocationDialog(); // Locate the drone's place
                break;
            }
            case R.id.add:{
                enableDisableAdd();
                break;
            }
            case R.id.clear:{
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        clear();
                    }

                });
                cameraUpdate();
                break;
            }
            case R.id.config:{
                showSettingDialog();
                break;
            }
            case R.id.loop:{
                enableDisableLoop();
                break;
            }
            case R.id.chart:{
                doSplineInterpolation();
		drawPaths();
                break;
            }
            case R.id.save:{
                save();
            }
	    default:
                break;
        }
    }

    private void drawPaths() {
	if (waypts.size() > 0) {
	    curvedPath = drawPath(waypts, android.graphics.Color.BLACK);
	}
	if (waypoints.size() > 0) {
	    straightPath = drawPath(waypoints, android.graphics.Color.RED);
	}
    }

    Polyline drawPath(ArrayList<LatLng> wpts, int color) {
	PolylineOptions lineOptions = new PolylineOptions().color(color);
	for (LatLng l: wpts) {
	    lineOptions.add(l);
	}
        if (isLoop && wpts == waypoints) {
            lineOptions.add(waypoints.get(0));
        }
        isCharted = true;
	return aMap.addPolyline(lineOptions);
    }

    private void clear() {
	clearPaths();
        waypts.clear();
        aMap.clear();
        waypoints.clear();
    }

    private void cameraUpdate(){
	if (cameraPos == null) {
	    aMap.moveCamera(CameraUpdateFactory.zoomTo(zoomLevel));
	} else {
	    aMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPos));
	}
        LatLng pos = new LatLng(droneLocationLat, droneLocationLng);
        CameraUpdate cu = CameraUpdateFactory.newLatLng(pos);
        aMap.moveCamera(cu);
        aMap.addMarker(new MarkerOptions().position(pos).title(placeName));
    }

    private void enableDisableAdd() {
        isAdd = !isAdd;
        setAddExitButtonText();
    }

    private void setAddExitButtonText() {
        if (isAdd) {
            add.setText("Exit");
        }else{
            add.setText("Add");
        }
    }

    private void enableDisableLoop() {
        isLoop = !isLoop;
        setLoopOneWayButtonText();
        if (isCharted) {
            clearPaths();
            doSplineInterpolation();
            drawPaths();
        }
    }

    private void setLoopOneWayButtonText() {
        if (isLoop) {
            loop.setText("One Way");
        }else{
            loop.setText("Loop");
        }
    }

    private void showLocationDialog(){
        LinearLayout locationSettings = (LinearLayout)getLayoutInflater().inflate(R.layout.dialog_location, null);

        final TextView latlng_TV = (TextView) locationSettings.findViewById(R.id.latlng);
        final TextView placeName_TV = (TextView) locationSettings.findViewById(R.id.placeName);
	isLocateDialogShown = true;

        new AlertDialog.Builder(this)
                .setTitle("")
                .setView(locationSettings)
                .setPositiveButton("Finish",new DialogInterface.OnClickListener(){
                    public void onClick(DialogInterface dialog, int id) {
			isLocateDialogShown = false;
                        try {
                            String[] latlng = latlng_TV.getText().toString().split(",");
                            droneLocationLat = Double.parseDouble(latlng[0]);
                            droneLocationLng = Double.parseDouble(latlng[1]);
                            Log.e(TAG, "latitude " + droneLocationLat);
                            Log.e(TAG, "longitude " + droneLocationLng);

                            //Log.e(TAG, "actionAfterFinishTask "+actionAfterFinishTask);
                            //Log.e(TAG, "heading "+heading);
                            clear();
                            cameraUpdate();
                        }
                        catch (Exception e) {
                            setResultToToast("Failure getting location: " + e);
                        }
                    }

                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
			isLocateDialogShown = false;
                        dialog.cancel();
                    }

                })
                .create()
                .show();
    }

    private void showSettingDialog(){
        LinearLayout wayPointSettings = (LinearLayout)getLayoutInflater().inflate(R.layout.dialog_waypointsetting, null);

       final TextView wpAltitude_TV = (TextView) wayPointSettings.findViewById(R.id.altitude);
        RadioGroup speed_RG = (RadioGroup) wayPointSettings.findViewById(R.id.speed);
        RadioGroup actionAfterFinished_RG = (RadioGroup) wayPointSettings.findViewById(R.id.actionAfterFinished);
        RadioGroup heading_RG = (RadioGroup) wayPointSettings.findViewById(R.id.heading);
	isSettingsDialogShown = true;

        speed_RG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener(){

            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                // TODO Auto-generated method stub
                if (checkedId == R.id.lowSpeed){
                    speed = 1.0f;
                } else if (checkedId == R.id.MidSpeed){
                    speed = 3.0f;
                } else if (checkedId == R.id.HighSpeed){
                    speed = 5.0f;
                }
            }

        });

        actionAfterFinished_RG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                // TODO Auto-generated method stub
                if (checkedId == R.id.finishNone) {
//                    actionAfterFinishTask = DJIGroundStationFinishAction.None;
                } else if (checkedId == R.id.finishGoHome) {
//                    actionAfterFinishTask = DJIGroundStationFinishAction.Go_Home;
                } else if (checkedId == R.id.finishLanding) {
//                    actionAfterFinishTask = DJIGroundStationFinishAction.Land;
                } else if (checkedId == R.id.finishToFirst) {
//                    actionAfterFinishTask = DJIGroundStationFinishAction.Back_To_First_Way_Point;
                }
            }
        });

        heading_RG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                // TODO Auto-generated method stub
                if (checkedId == R.id.headingNext){
//                    heading = DJIGroundStationMovingMode.GSHeadingTowardNextWaypoint;
                } else if (checkedId == R.id.headingInitDirec){
//                    heading = DJIGroundStationMovingMode.GSHeadingUsingInitialDirection;
                } else if (checkedId == R.id.headingRC){
//                    heading = DJIGroundStationMovingMode.GSHeadingControlByRemoteController;
                } else if (checkedId == R.id.headingWP){
//                    heading = DJIGroundStationMovingMode.GSHeadingUsingWaypointHeading;
                }
            }
        });


        new AlertDialog.Builder(this)
                .setTitle("")
                .setView(wayPointSettings)
                .setPositiveButton("Finish",new DialogInterface.OnClickListener(){
                    public void onClick(DialogInterface dialog, int id) {
			isSettingsDialogShown = false;
                        try {
                            altitude = (float) (Integer.parseInt(wpAltitude_TV.getText().toString()) / 3.0f);
                            if (altitude < 10.0f/3 || altitude > 400.0f/3) {
                                setResultToToast("Flight altitude should be between 10 and 400 feet.");
                            }
                            Log.e(TAG, "altitude " + altitude);
                            Log.e(TAG, "speed " + speed);
                            //Log.e(TAG, "actionAfterFinishTask "+actionAfterFinishTask);
                            //Log.e(TAG, "heading "+heading);
                        }
                        catch (Exception e) {
                            setResultToToast("Failure configuring waypoint: " + e);
                        }
                    }

                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
			isSettingsDialogShown = false;
                        dialog.cancel();
                    }

                })
                .create()
                .show();
    }

    private void save() {
        if (altitude >= 10.0f/3 && altitude <= 400.0f/3) {
            if (waypts.size() > 0) {
                savePath(curvedPathFileName, waypts);
            } else {
                setResultToToast("No curved path charted yet");
            }
            if (waypoints.size() > 0) {
                savePath(straightPathFileName, waypoints);

            } else {
                setResultToToast("No way points given");
            }
        } else {
            setResultToToast("Flight altitude should be between 10 and 400 feet.");
        }
    }

    private void setResultToToast(String result){
        Toast.makeText(MapsActivity.this, result, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume(){
        super.onResume();
        Log.e(TAG, "onResume");
    }

    @Override
    protected void onPause(){
        Log.e(TAG, "onPause");
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState (Bundle outState) {
        super.onSaveInstanceState(outState);
	outState.putInt("mCheckedId", mCheckedId);
	outState.putBoolean("isAdd", isAdd);
	outState.putDouble("droneLocationLat", droneLocationLat);
	outState.putDouble("droneLocationLng", droneLocationLng);
	outState.putString("placeName", placeName);
	outState.putFloat("altitude", altitude);
	outState.putFloat("speed", speed);
	outState.putParcelableArrayList("waypoints", waypoints);
	outState.putBoolean("isLoop", isLoop);
	outState.putParcelable("cameraPos", cameraPos);
	outState.putParcelableArrayList("waypts", waypts);
        outState.putBoolean("isCharted", isCharted);
	outState.putBoolean("isSettingsDialogShown", isSettingsDialogShown);
	outState.putBoolean("isLocateDialogShown", isLocateDialogShown);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
	// TODO Auto-generated method stub
	super.onRestoreInstanceState(savedInstanceState);
	mCheckedId = savedInstanceState.getInt("mCheckedId");
	isAdd = savedInstanceState.getBoolean("isAdd");
	droneLocationLat = savedInstanceState.getDouble("droneLocationLat");
	droneLocationLng = savedInstanceState.getDouble("droneLocationLng");
	placeName = savedInstanceState.getString("placeName");

	altitude = savedInstanceState.getFloat("altitude");
	speed = savedInstanceState.getFloat("speed");

	waypoints = savedInstanceState.getParcelableArrayList("waypoints");
	isLoop = savedInstanceState.getBoolean("isLoop");
	cameraPos = (CameraPosition)savedInstanceState.getParcelable("cameraPos");
	waypts = savedInstanceState.getParcelableArrayList("waypts");
        isCharted = savedInstanceState.getBoolean("isCharted");
	isSettingsDialogShown = savedInstanceState.getBoolean("isSettingsDialogShown");
	isLocateDialogShown = savedInstanceState.getBoolean("isLocateDialogShown");
    }

    @Override
    protected void onStop() {
        // TODO Auto-generated method stub
        super.onStop();
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        // TODO Auto-generated method stub
        // Initializing Amap object
        if (aMap == null) {
            aMap = googleMap;
        }

	setMapType();
        setAddExitButtonText();
        setLoopOneWayButtonText();
	cameraUpdate();
	markWaypoints();
	if (isCharted) drawPaths();

	setUpMapListeners();
    }

    public void onCameraChange (CameraPosition position) {
	cameraPos = position;
    }

    private void setUpMapListeners() {
        aMap.setOnMapClickListener(this);// add the listener for click for amap object
	aMap.setOnCameraChangeListener(this);
    }
}
