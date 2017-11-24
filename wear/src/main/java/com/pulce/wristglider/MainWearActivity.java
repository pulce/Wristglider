package com.pulce.wristglider;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.wearable.activity.WearableActivity;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.pulce.commonclasses.Statics;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.Set;
import java.util.UUID;

public class MainWearActivity extends WearableActivity implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener,
        DataApi.DataListener,
        Thread.UncaughtExceptionHandler {

    private static final String TAG = "WearMain";

    private static SimpleDateFormat clockFormat;

    private static SharedPreferences prefs;
    private static boolean debugMode = false;

    private static boolean mockup = false;

    private TextView speedTextView;
    private TextView altTextView;
    private TextView alternatives;
    private ImageView directionView;
    private TextView loggerState;
    //private TextView batteryState;
    private ProgressBar progressBar;
    private RelativeLayout coreLayout;
    private TextView varioTextView;
    private TextView varioMinusTextView;
    private View[] varioBarPos = new View[10];
    private View[] varioBarNeg = new View[10];

    private Bitmap arrowBitmap;
    private Matrix rotateMatrix = new Matrix();

    private PrintWriter pw;
    private boolean loggerRunning = false;
    private String recentIgcFileName;

    private GoogleApiClient mGoogleApiClient;

    private Thread.UncaughtExceptionHandler mDefaultUncaughtExceptionHandler;

    private LinkedList<Location> pointStack;
    private Location lastLoggedLocation;
    private long startTimeOfFlight = 0;
    private int killFirstDirtylocations = 0;

    private float speedmultiplier;
    private float heightmultiplier;
    private float variomultiplier;

    private boolean activityStopping;

    private EarthGravitationalModel gh;

    private BroadcastReceiver mBatInfoReceiver;
	
    private BluetoothAdapter mBluetoothAdapter = null;
    private ConnectThread mBTConnectThread = null;
    private ConnectedThread mBTConnectedThread = null;

    private static final UUID MY_UUID_SECURE =
            UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
    private static final UUID MY_UUID_INSECURE =
            UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case Statics.MY_BT_MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;
                    // construct a string from the buffer
                    String writeMessage = new String(writeBuf);
                    //if (debugMode) Log.d(TAG, "BT write message: " + writeMessage);
                    break;
                case Statics.MY_BT_MESSAGE_READ:
                    String readMessage = (String) msg.obj;
                    /*byte[] readBuf = (byte[]) msg.obj;
                    // construct a string from the valid bytes in the buffer
                    String readMessage = new String(readBuf, 0, msg.arg1);*/
                    //if (debugMode) Log.d(TAG, "BT read message: " + readMessage);
                    if (readMessage.startsWith("$LK8EX1")) parseLK8EX1Vario(readMessage);
                    else if (readMessage.startsWith("$PTAS1")) parseGenericVario(readMessage);

                    updateVario();
                    break;
            }
        }
    };

    private class VarioData {
        // pressure in hpa
        public float pressure = Statics.MY_NULL_VALUE;
        // vert speed in m/s
        public float vario = Statics.MY_NULL_VALUE;
        // QNE alt in m
        public int baroAlt = Statics.MY_NULL_VALUE;
        // tempperature in C
        public int temperature = Statics.MY_NULL_VALUE;
        // airspeed in m/s
        public float airSpeed = Statics.MY_NULL_VALUE;
        // vario battery in V
        public float varioBatt = Statics.MY_NULL_VALUE;
    }
    private VarioData mVarioData = new VarioData();

    private View stdView, stdViewVario;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        debugMode = BuildConfig.LOG_ENABLED;
        if (debugMode) Log.d(TAG, "creating " + BuildConfig.VERSION_NAME);
        setAmbientEnabled();
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        mDefaultUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(this);

        gh = new EarthGravitationalModel();
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    gh.load(MainWearActivity.this);
                } catch (Exception e) {
                    if (debugMode) Log.e(TAG, Log.getStackTraceString(e));
                    reportException(e);
                }
            }
        });
        if (debugMode) Log.d(TAG, "Screen flag " + prefs.getBoolean(Statics.PREFSCREENON, false));
        if (!prefs.getBoolean(Statics.PREFSCREENON, false)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        if (prefs.getString(Statics.PREFROTATEDEGREES, "0").equals("-90")) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
        } else if (prefs.getString(Statics.PREFROTATEDEGREES, "0").equals("90")){
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
        if (DateFormat.is24HourFormat(this)) {
            clockFormat = new SimpleDateFormat("HH:mm");
        } else {
            clockFormat = new SimpleDateFormat("hh:mm");
        }

        stdView = getLayoutInflater().inflate(R.layout.activity_wear_main, null);
        stdViewVario = getLayoutInflater().inflate(R.layout.activity_wear_main_vario, null);
        if (prefs.getBoolean(Statics.PREFUSEBTVARIO, false)) {
            switchView(stdViewVario);
        } else {
            switchView(stdView);
        }

        // vario specific elements
        varioTextView = (TextView) stdViewVario.findViewById(R.id.variotext);
        varioMinusTextView = (TextView) stdViewVario.findViewById(R.id.variominus);
        varioBarPos[0] = stdViewVario.findViewById(R.id.varioBar1);
        varioBarPos[1] = stdViewVario.findViewById(R.id.varioBar2);
        varioBarPos[2] = stdViewVario.findViewById(R.id.varioBar3);
        varioBarPos[3] = stdViewVario.findViewById(R.id.varioBar4);
        varioBarPos[4] = stdViewVario.findViewById(R.id.varioBar5);
        varioBarPos[5] = stdViewVario.findViewById(R.id.varioBar6);
        varioBarPos[6] = stdViewVario.findViewById(R.id.varioBar7);
        varioBarPos[7] = stdViewVario.findViewById(R.id.varioBar8);
        varioBarPos[8] = stdViewVario.findViewById(R.id.varioBar9);
        varioBarPos[9] = stdViewVario.findViewById(R.id.varioBar10);
        varioBarNeg[0] = stdViewVario.findViewById(R.id.varioBar_1);
        varioBarNeg[1] = stdViewVario.findViewById(R.id.varioBar_2);
        varioBarNeg[2] = stdViewVario.findViewById(R.id.varioBar_3);
        varioBarNeg[3] = stdViewVario.findViewById(R.id.varioBar_4);
        varioBarNeg[4] = stdViewVario.findViewById(R.id.varioBar_5);
        varioBarNeg[5] = stdViewVario.findViewById(R.id.varioBar_6);
        varioBarNeg[6] = stdViewVario.findViewById(R.id.varioBar_7);
        varioBarNeg[7] = stdViewVario.findViewById(R.id.varioBar_8);
        varioBarNeg[8] = stdViewVario.findViewById(R.id.varioBar_9);
        varioBarNeg[9] = stdViewVario.findViewById(R.id.varioBar_10);

        setMultipliers();

        arrowBitmap = BitmapFactory.decodeResource(this.getResources(),
                R.drawable.direction);

        pointStack = new LinkedList<>();

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        /*mBatInfoReceiver = new BroadcastReceiver(){
            @Override
            public void onReceive(Context ctxt, Intent intent) {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
                batteryState.setText("Bat: " + String.valueOf(level) + "%");
            }
        };*/
        this.registerReceiver(this.mBatInfoReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

        if (mockup) {
            progressBar.setVisibility(View.INVISIBLE);
            speedTextView.setText("36.5");
            altTextView.setText("3585");
            directionView.setImageDrawable(getRotatedDir(30));
        }
/*        alternatives.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                File dir = getFilesDir();
                File[] subFiles = dir.listFiles();
                if (subFiles != null) {
                    for (File file : subFiles) {
                        if (debugMode) Log.d(TAG, "Available Logfile: " + file.getName());
                    }
                }
                return true;
            }
        });*/

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }


    @Override
    public void onStart() {
        super.onStart();
        if (debugMode) Log.d(TAG, "starting");
        activityStopping = false;
        mGoogleApiClient.connect();
        Runnable screenUpdate = new Runnable() {
            public void run() {
                if (debugMode) Log.d(TAG, "schedule running");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                    if (alternatives != null)
                            alternatives.setText(clockFormat.format(new Date()).replaceAll(":", "\n"));
                    }
                });
                mHandler.postDelayed(this, 1000 * 60);
            }
        };
        screenUpdate.run();

        if (prefs.getBoolean(Statics.PREFUSEBTVARIO, false) && mBluetoothAdapter != null) {
            // If BT is not on, request that it be enabled.
            if (!mBluetoothAdapter.isEnabled()) {
                if (debugMode) Log.d(TAG, "asking to enable BT on start");
                Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableIntent, Statics.MY_REQUEST_ENABLE_BT);
            } else {
                setupBTConnection();
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (debugMode) Log.d(TAG, "stopping");
        activityStopping = true;
        Wearable.DataApi.removeListener(mGoogleApiClient, this);
        if (mGoogleApiClient.isConnected()) {
            LocationServices.FusedLocationApi
                    .removeLocationUpdates(mGoogleApiClient, this);
        }
        mGoogleApiClient.disconnect();
        mHandler.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (debugMode) Log.d(TAG, "pausing");
        Wearable.DataApi.removeListener(mGoogleApiClient, this);
        mGoogleApiClient.disconnect();
    }

    @Override
    public void onResume() {
        super.onResume();
        mGoogleApiClient.connect();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        if (debugMode) Log.d(TAG, "on connected");
        Wearable.DataApi.addListener(mGoogleApiClient, this);

        PendingResult<DataItemBuffer> results = Wearable.DataApi.getDataItems(mGoogleApiClient);
        results.setResultCallback(new ResultCallback<DataItemBuffer>() {
            @Override
            public void onResult(@NonNull DataItemBuffer dataItems) {
                if (dataItems.getCount() != 0) {
                    for (DataItem item : dataItems) {
                        if (item.getUri().getPath().contains(Statics.DATAPREFERENCES)) {
                            updatePreferencesFromDataItem(item);
                        } else if (item.getUri().getPath().contains(Statics.DATADELETE)) {
                            deleteSingleIgcFile(item);
                        }
                    }
                }
                dataItems.release();
            }
        });

        File dir = getFilesDir();
        File[] subFiles = dir.listFiles();
        if (subFiles != null) {
            for (File file : subFiles) {
                if (!file.getName().contains(".igc")) continue;
                Asset asset = createAssetFromTextfile(file);
                PutDataMapRequest dataMap = PutDataMapRequest.create(Statics.DATAIGC + file.getName());
                dataMap.getDataMap().putString("igcname", file.getName());
                dataMap.getDataMap().putAsset("igcfile", asset);
                PutDataRequest request = dataMap.asPutDataRequest();
                request.setUrgent();
                Wearable.DataApi.putDataItem(mGoogleApiClient, request);
            }
        }
        requestLocationUpdates();
        //throw new RuntimeException("This is a wear croshhhhh");
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        if (debugMode) Log.d("TAG", "data event happened");
        for (DataEvent event : dataEvents) {
            if (event.getType() == DataEvent.TYPE_CHANGED &&
                    event.getDataItem().getUri().getPath().contains(Statics.DATAPREFERENCES)) {
                updatePreferencesFromDataItem(event.getDataItem());
            } else if (event.getType() == DataEvent.TYPE_CHANGED &&
                    event.getDataItem().getUri().getPath().contains(Statics.DATADELETE)) {
                deleteSingleIgcFile(event.getDataItem());
            }
        }
    }

    private void switchView(View newView) {
        setContentView(newView);

        speedTextView = (TextView) newView.findViewById(R.id.speedtext);
        altTextView = (TextView) newView.findViewById(R.id.altitext);
        directionView = (ImageView) newView.findViewById(R.id.directionImage);
        alternatives = (TextView) newView.findViewById(R.id.otherfeed);
        loggerState = (TextView) newView.findViewById(R.id.loggerstate);
        //batteryState = (TextView) newView.findViewById(R.id.batterystate);
        progressBar = (ProgressBar) newView.findViewById(R.id.progress);
        coreLayout = (RelativeLayout) newView.findViewById(R.id.container);
        coreLayout.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (loggerRunning) {
                    final AlertDialog.Builder dialog = new AlertDialog.Builder(MainWearActivity.this)
                            .setTitle(R.string.stop_logger)
                            .setMessage(R.string.stop_logger_confirm)
                            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    stopLogger();
                                }
                            })
                            .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                }
                            })
                            .setIcon(android.R.drawable.ic_dialog_alert);
                    final AlertDialog alert = dialog.create();
                    alert.show();
                    final Handler handler = new Handler();
                    final Runnable runnable = new Runnable() {
                        @Override
                        public void run() {
                            if (alert.isShowing()) {
                                alert.dismiss();
                            }
                        }
                    };
                    alert.setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                            handler.removeCallbacks(runnable);
                        }
                    });
                    handler.postDelayed(runnable, 3000);
                } else {
                    startLogger();
                }
                return true;
            }
        });
    }

    private void deleteSingleIgcFile(DataItem item) {
        item.freeze();
        DataMapItem dataMapItem = DataMapItem.fromDataItem(item);
        String filename = dataMapItem.getDataMap().getString(Statics.DATADELETE);
        File dir = getFilesDir();
        File[] subFiles = dir.listFiles();
        if (subFiles != null) {
            for (File file : subFiles) {
                if (filename.contains(Statics.getUTCdateReverse())) return;
                if (file.getName().equals(filename)) {
                    if (file.delete()) {
                        if (debugMode) Log.d(TAG, "File " + filename + " is deleted.");
                        Wearable.DataApi.deleteDataItems(mGoogleApiClient, item.getUri());
                    } else {
                        if (debugMode) Log.d(TAG, "File " + filename + " delete error.");
                    }
                }
            }
        }
    }

    private void updatePreferencesFromDataItem(DataItem dataItem) {
        DataMapItem dataMapItem = DataMapItem.fromDataItem(dataItem);
        prefs.edit().putString(Statics.PREFPILOTNAME, dataMapItem.getDataMap().getString(Statics.PREFPILOTNAME)).apply();
        prefs.edit().putString(Statics.PREFGLIDERTYPE, dataMapItem.getDataMap().getString(Statics.PREFGLIDERTYPE)).apply();
        prefs.edit().putString(Statics.PREFGLIDERID, dataMapItem.getDataMap().getString(Statics.PREFGLIDERID)).apply();
        prefs.edit().putLong(Statics.PREFLOGGERSECONDS, dataMapItem.getDataMap().getLong(Statics.PREFLOGGERSECONDS)).apply();
        prefs.edit().putBoolean(Statics.PREFLOGGERAUTO, dataMapItem.getDataMap().getBoolean(Statics.PREFLOGGERAUTO)).apply();
        if (prefs.getBoolean(Statics.PREFSCREENON, false) != dataMapItem.getDataMap().getBoolean(Statics.PREFSCREENON)) {
            Intent i = getBaseContext().getPackageManager()
                    .getLaunchIntentForPackage(getBaseContext().getPackageName());
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            finish();
            startActivity(i);
        }
        prefs.edit().putBoolean(Statics.PREFSCREENON, dataMapItem.getDataMap().getBoolean(Statics.PREFSCREENON)).apply();
        prefs.edit().putString(Statics.PREFSPEEDUNIT, dataMapItem.getDataMap().getString(Statics.PREFSPEEDUNIT)).apply();
        prefs.edit().putString(Statics.PREFHEIGTHUNIT, dataMapItem.getDataMap().getString(Statics.PREFHEIGTHUNIT)).apply();
        prefs.edit().putString(Statics.PREFBTVARIOUNIT, dataMapItem.getDataMap().getString(Statics.PREFBTVARIOUNIT)).apply();
        if (!prefs.getString(Statics.PREFROTATEDEGREES, "0").equals(dataMapItem.getDataMap().getString(Statics.PREFROTATEDEGREES, "0"))) {
            if (dataMapItem.getDataMap().getString(Statics.PREFROTATEDEGREES, "0").equals("-90")) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
            } else if (dataMapItem.getDataMap().getString(Statics.PREFROTATEDEGREES, "0").equals("90")){
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            } else {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER);
            }
        }
        prefs.edit().putString(Statics.PREFROTATEDEGREES, dataMapItem.getDataMap().getString(Statics.PREFROTATEDEGREES, "0")).apply();
        if (prefs.getBoolean(Statics.PREFUSEBTVARIO, false) != dataMapItem.getDataMap().getBoolean(Statics.PREFUSEBTVARIO)) {
            if (mBluetoothAdapter != null) {
                if (dataMapItem.getDataMap().getBoolean(Statics.PREFUSEBTVARIO, false)) {
                    switchView(stdViewVario);
                    // If BT is not on, request that it be enabled.
                    if (!mBluetoothAdapter.isEnabled()) {
                        if (debugMode) Log.d(TAG, "asking to enable BT");
                        Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                        startActivityForResult(enableIntent, Statics.MY_REQUEST_ENABLE_BT);
                    } else {
                        setupBTConnection();
                    }
                } else {
                    switchView(stdView);
                    prefs.edit().putString(Statics.PREFBTVARIODEVICE, "").apply();
                    disableBTConnection();
                }
                prefs.edit().putBoolean(Statics.PREFUSEBTVARIO, dataMapItem.getDataMap().getBoolean(Statics.PREFUSEBTVARIO)).apply();
            } else {
                sendBTFailed(Statics.MY_BT_FAILED_NO_BT);
            }
        }
        setMultipliers();
        if (debugMode) Log.d(TAG, "Preferences updated");
    }

    @Override
    public void onLocationChanged(Location location) {
        if (debugMode) Log.d(TAG, "location changed");
        killFirstDirtylocations++;
        if (location.hasSpeed()) {
            speedTextView.setText(String.format("%.1f", location.getSpeed() * speedmultiplier));
            if (location.getAccuracy() < 15 && killFirstDirtylocations++ > 4) {
                pointStack.add(location);
            }
        } else
            speedTextView.setText("--");
        if (location.hasAltitude()) {
            //Log.d(TAG, "Offsett: " + gh.heightOffset(location.getLongitude(), location.getLatitude(), location.getAltitude()));
            // Bearing
            progressBar.setVisibility(View.INVISIBLE);
            if (location.hasBearing()) {
                directionView.setImageDrawable(getRotatedDir(location.getBearing()));
            }
            altTextView.setText(String.format("%.0f", (location.getAltitude() - gh.heightOffset(location.getLongitude(), location.getLatitude(), location.getAltitude())) * heightmultiplier));
        } else {
            altTextView.setText("--");
        }
        if (!location.hasBearing()) {
            directionView.setImageResource(android.R.color.transparent);
        }
        alternatives.setText(clockFormat.format(new Date()).replaceAll(":", "\n"));

        if (pointStack.size() > 10) pointStack.removeFirst();
        if (loggerRunning) {
            if (startTimeOfFlight > 0) {
                int minutes = (int) (((location.getTime() - startTimeOfFlight) / (1000*60)) % 60);
                int hours   = (int) ((location.getTime() - startTimeOfFlight) / (1000*60*60));
                loggerState.setText(getString(R.string.flight_time) + " " + String.format("%02d", hours) + ":" + String.format("%02d", minutes));
            }
            if (location.hasAltitude()) logIGCline(location);
        } else if (pointStack.size() > 9 &&
                location.distanceTo(pointStack.getFirst()) > (location.getTime() - pointStack.getFirst().getTime()) * 0.004) {
            if (prefs.getBoolean(Statics.PREFLOGGERAUTO, false)) {
                startLogger();
                for (Location loc : pointStack) logIGCline(loc);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void requestLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(MainWearActivity.this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {
                Toast.makeText(this, R.string.permission_fine_location_hint, Toast.LENGTH_LONG).show();
            }
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    Statics.MY_PERMISSION_FINE_LOCATION);
            return;
        }
        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(1000);
        locationRequest.setFastestInterval(900);
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, locationRequest, this);
    }


    @Override
    public void onConnectionSuspended(int i) {
        if (debugMode) Log.d(TAG, "connection to location client suspended");
        if (!activityStopping) mGoogleApiClient.connect();
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        if (debugMode) Log.d(TAG, "connection to location client failed");
        reportException(new RuntimeException(TAG, new Throwable("connection to location client failed")));
        if (!activityStopping) mGoogleApiClient.connect();
    }

    public BitmapDrawable getRotatedDir(float angle) {
        Bitmap canvasBitmap = arrowBitmap.copy(Bitmap.Config.ARGB_8888, true);
        canvasBitmap.eraseColor(0x00000000);
        Canvas canvas = new Canvas(canvasBitmap);
        rotateMatrix.setRotate(angle, canvas.getWidth() / 2, canvas.getHeight() / 2);
        canvas.drawBitmap(arrowBitmap, rotateMatrix, null);
        return new BitmapDrawable(getResources(), canvasBitmap);
     }

    private Asset createAssetFromTextfile(File filename) {
        FileInputStream fis = null;
        ByteArrayOutputStream byteStream = null;
        try {
            fis = new FileInputStream(filename);
            byte[] buffer = new byte[4096];
            byteStream = new ByteArrayOutputStream();
            int read;
            while ((read = fis.read(buffer)) != -1) {
                byteStream.write(buffer, 0, read);
            }
        } catch (IOException e) {
            reportException(e);
        } finally {
            try {
                if (byteStream != null)
                    byteStream.close();
            } catch (IOException e) {
                reportException(e);
            }
            try {
                if (fis != null)
                    fis.close();
            } catch (IOException e) {
                reportException(e);
            }
        }
        assert byteStream != null;
        return Asset.createFromBytes(byteStream.toByteArray());
    }

    private void startLogger() {
        if (debugMode) Log.d(TAG, "logger triggered");
        int num = 1;
        boolean unique;
        do {
            File dir = getFilesDir();
            File[] subFiles = dir.listFiles();
            recentIgcFileName = Statics.getUTCdateReverse() + "_WG_" + num++ + ".igc";
            unique = true;
            if (subFiles != null) {
                for (File file : subFiles) {
                    if (file.getName().equals(recentIgcFileName)) unique = false;
                }
            }
        } while (!unique);
        if (debugMode) Log.d(TAG, "Now  creating " + recentIgcFileName);
        FileOutputStream fos = null;
        try {
            fos = openFileOutput(recentIgcFileName, Context.MODE_PRIVATE);
            pw = new PrintWriter(fos, true);
            pw.println("AXXX"); // No Manufaxcturer code
            pw.println("HFFXA020"); // fixy acuracy 35m
            pw.println("HFDTE" + Statics.getUTCdateAsString());
            pw.println("HFPLTPILOTINCHARGE:" + prefs.getString(Statics.PREFPILOTNAME, "Anno Nymos"));
            pw.println("HFGTYGLIDERTYPE:" + prefs.getString(Statics.PREFGLIDERTYPE, "Dummy Glider"));
            pw.println("HFGIDGLIDERID:" + prefs.getString(Statics.PREFGLIDERID, "12345"));
            pw.println("HFFTYFRTYPE:WristGlider_V_" + BuildConfig.VERSION_NAME);
            pw.println("HFGPS:Generic");
            pw.println("HFDTM100DATUM:WGS-1984");
            pw.println("I013638FXA");
            loggerRunning = true;
            if (pointStack.size() > 0) {
                startTimeOfFlight = pointStack.getFirst().getTime();
            } else {
                startTimeOfFlight = new Date().getTime();
            }
            loggerState.setText(getString(R.string.logger_started));
        } catch (FileNotFoundException e) {
            reportException(e);
        } finally {
            try {
                assert fos != null;
                fos.close();
            } catch (IOException e) {
                reportException(e);
            }
        }
    }

    private void stopLogger() {
        File dir = getFilesDir();
        File[] subFiles = dir.listFiles();
        if (subFiles != null) {
            for (File file : subFiles) {
                if (file.getName().contains(recentIgcFileName)) {
                    if (debugMode) Log.d(TAG, "Now checking File " + file.getName());
                    Asset asset = createAssetFromTextfile(file);
                    PutDataMapRequest dataMap = PutDataMapRequest.create(Statics.DATAIGC + file.getName());
                    dataMap.getDataMap().putString("igcname", file.getName());
                    dataMap.getDataMap().putAsset("igcfile", asset);
                    PutDataRequest request = dataMap.asPutDataRequest();
                    request.setUrgent();
                    Wearable.DataApi.putDataItem(mGoogleApiClient, request);
                }
            }
        }
        loggerRunning = false;
        loggerState.setText("");
    }

    private void logIGCline(Location location) {
        if (lastLoggedLocation == null) lastLoggedLocation = location;
        if ((location.getTime() - lastLoggedLocation.getTime()) <
                prefs.getLong(Statics.PREFLOGGERSECONDS, 1000) + 50) {
            return;
        }
        if (getFileStreamPath(recentIgcFileName) == null || !getFileStreamPath(recentIgcFileName).exists()) {
            startLogger();
        }
        FileOutputStream fos = null;
        try {
            fos = openFileOutput(recentIgcFileName, Context.MODE_APPEND);
            pw = new PrintWriter(fos, true);
            pw.println("B" + Statics.getUTCtimeAsString(location.getTime()) +
                    Statics.decToIgcFormat(location.getLatitude(), true) +
                    Statics.decToIgcFormat(location.getLongitude(), false) +
                    "A00000" + new DecimalFormat("00000").format(location.getAltitude() - gh.heightOffset(location.getLongitude(), location.getLatitude(), location.getAltitude())) +
                    new DecimalFormat("000").format(location.getAccuracy()));
        } catch (FileNotFoundException e) {
            reportException(e);
        } finally {
            try {
                assert fos != null;
                fos.close();
            } catch (IOException e) {
                reportException(e);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[], @NonNull int[] grantResults) {
        if (requestCode == Statics.MY_PERMISSION_FINE_LOCATION) {
            if (grantResults.length <= 0
                    || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, R.string.permission_fine_location_hint, Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void setMultipliers() {
        if (prefs.getString(Statics.PREFSPEEDUNIT, "km/h").equals("km/h")) {
            speedmultiplier = 3.6f;
        } else if (prefs.getString(Statics.PREFSPEEDUNIT, "km/h").equals("kn")){
            speedmultiplier = 1.943844f;
        } else {
            speedmultiplier = 2.236936f;
        }
        if (prefs.getString(Statics.PREFHEIGTHUNIT, "m").equals("m")) {
            heightmultiplier = 1f;
        } else {
            heightmultiplier = 3.28084f;
        }
        if (prefs.getString(Statics.PREFBTVARIOUNIT, "m/s").equals("kn")) {
            variomultiplier = 1.943844f;
        } else {
            variomultiplier = 1f;
        }
        Log.d(TAG, "" + speedmultiplier + heightmultiplier + variomultiplier);
    }

    private void reportException(Throwable throwable) {
        if (debugMode) Log.d(TAG, "reporting exception");
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = null;
        try {
            oos = new ObjectOutputStream(bos);
            oos.writeObject(throwable);
            byte[] exceptionData = bos.toByteArray();
            PutDataMapRequest dataMap = PutDataMapRequest.create(Statics.DATATHROWABLE + throwable.toString());
            dataMap.getDataMap().putString("board", Build.BOARD);
            dataMap.getDataMap().putString("fingerprint", Build.FINGERPRINT);
            dataMap.getDataMap().putString("model", Build.MODEL);
            dataMap.getDataMap().putString("manufacturer", Build.MANUFACTURER);
            dataMap.getDataMap().putString("product", Build.PRODUCT);
            dataMap.getDataMap().putByteArray("exception", exceptionData);
            PutDataRequest request = dataMap.asPutDataRequest();
            request.setUrgent();
            Wearable.DataApi.putDataItem(mGoogleApiClient, request);
        } catch (IOException e) {
            Log.e(TAG, Log.getStackTraceString(e));
        } finally {
            try {
                if (oos != null)
                    oos.close();
            } catch (IOException e) {
                Log.e(TAG, Log.getStackTraceString(e));
            }
            try {
                bos.close();
            } catch (IOException e) {
                Log.e(TAG, Log.getStackTraceString(e));
            }
        }
    }

    @Override
    public void uncaughtException(Thread thread, final Throwable throwable) {
        if (debugMode) Log.d(TAG, "exception thrown and caught");
        reportException(throwable);
        mDefaultUncaughtExceptionHandler.uncaughtException(thread, throwable);
    }

    public void parseLK8EX1Vario(String readMessage) {
        //if (debugMode) Log.d(TAG, "parsing LK8EX1 vario: " + readMessage);
        // parsing $LK8EX1,98668,99999,0,25,3.81,*30 - prot,pressure_hpa*100,alt(baro)_in_metric,vario*100_in_metric,temp_c,battery_volt(float)_or_perc,checksum
        String[] field = readMessage.split(",");

        try {
            float pressure = Float.parseFloat(field[1]);
            if (pressure != 999999) mVarioData.pressure = pressure / 100;
        } catch (NumberFormatException e) {
            //if (debugMode) Log.d(TAG, "LK8EX1 pressure incorrect");
        }

        try {
            int baroAlt = Integer.parseInt(field[2]);
            if (baroAlt != 99999) mVarioData.baroAlt = baroAlt;
        } catch (NumberFormatException e) {
            //if (debugMode) Log.d(TAG, "LK8EX1 baro alt incorrect");
        }

        try {
            float vario = Float.parseFloat(field[3]);
            if (vario != 9999) mVarioData.vario = vario / 100;
        } catch (NumberFormatException e) {
            //if (debugMode) Log.d(TAG, "LK8EX1 vario incorrect");
        }

        try {
            int temp = Integer.parseInt(field[4]);
            if (temp != 99) mVarioData.temperature = temp;
        } catch (NumberFormatException e) {
            //if (debugMode) Log.d(TAG, "LK8EX1 temp incorrect");
        }

        try {
            float batt = Float.parseFloat(field[5]);
            if (batt != 999) mVarioData.varioBatt = batt;
        } catch (NumberFormatException e) {
            //if (debugMode) Log.d(TAG, "LK8EX1 batt incorrect");
        }
        //if (debugMode) Log.d(TAG, "LK8EX1 pressure: " + mVarioData.pressure + ", baro alt: " + mVarioData.baroAlt + ", vario: " + mVarioData.vario + ", temp: " + mVarioData.temperature + ", batt: " + mVarioData.varioBatt);
    }

    public void parseGenericVario(String readMessage) {
        //if (debugMode) Log.d(TAG, "parsing generic vario: " + readMessage);
        // parsing $PTAS1,200,,2731,*12 - prot,vario*10+200_range_0-400_in_knots,average_vario*10+200_range_0-400_in_knots,baro_alt_in_feet_+2000,airspeed_in_knots*checksum
        String[] field = readMessage.split(",");

        try {
            float vario = Float.parseFloat(field[1]);
            mVarioData.vario = ((vario - 200) / 10) * 0.514444444f;
        } catch (NumberFormatException e) {
            //if (debugMode) Log.d(TAG, "PTAS1 vario incorrect");
        }

        try {
            int baroAlt = Integer.parseInt(field[3]);
            mVarioData.baroAlt = Math.round((baroAlt - 2000) * 0.3048f);
        } catch (NumberFormatException e) {
            //if (debugMode) Log.d(TAG, "PTAS1 baro alt incorrect");
        }

        try {
            int airSpeed = Integer.parseInt(field[4].substring(0, field[4].indexOf("*")));
            mVarioData.airSpeed = airSpeed * 0.514444444f;
        } catch (NumberFormatException e) {
            //if (debugMode) Log.d(TAG, "PTAS1 speed incorrect");
        }
        //if (debugMode) Log.d(TAG, "generic vario: " + mVarioData.vario + ", baro alt: " + mVarioData.baroAlt + ", speed: " + mVarioData.airSpeed);
    }

    private void updateVario() {
        if (mVarioData.vario != Statics.MY_NULL_VALUE) {
            varioTextView.setText(String.format("%.1f", Math.abs(mVarioData.vario) * variomultiplier));
            int i = 0;
            if (mVarioData.vario < 0) {
                varioMinusTextView.setVisibility(View.VISIBLE);
                for (View varBar : varioBarNeg) {
                    if (i++ < (Math.abs(mVarioData.vario) * 2)) varBar.setVisibility(View.VISIBLE);
                    else varBar.setVisibility(View.INVISIBLE);
                }
                for (View varBar : varioBarPos) {
                    varBar.setVisibility(View.INVISIBLE);
                }
            }
            else {
                varioMinusTextView.setVisibility(View.INVISIBLE);
                for (View varBar : varioBarPos) {
                    if (i++ < (mVarioData.vario * 2)) varBar.setVisibility(View.VISIBLE);
                    else varBar.setVisibility(View.INVISIBLE);
                }
                for (View varBar : varioBarNeg) {
                    varBar.setVisibility(View.INVISIBLE);
                }
            }
        }
        else {
            varioTextView.setText("--");
            varioMinusTextView.setVisibility(View.INVISIBLE);
            for (View varBar : varioBarNeg) {
                varBar.setVisibility(View.VISIBLE);
            }
            for (View varBar : varioBarPos) {
                varBar.setVisibility(View.VISIBLE);
            }
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case Statics.MY_REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    setupBTConnection();
                } else {
                    // User did not enable Bluetooth or an error occurred
                    if (debugMode) Log.d(TAG, "user did not enable BT");
                    sendBTFailed(Statics.MY_BT_FAILED_USER);
                    // TODO Check why there is 2 requests to enable BT when declined
                }
        }
    }

    private void sendBTFailed(int reason) {
        if (debugMode) Log.d(TAG, "disabling Use BT Vario option and sending to mobile, reason: " + reason);
        prefs.edit().putString(Statics.PREFBTVARIODEVICE, "").apply();
        prefs.edit().putBoolean(Statics.PREFUSEBTVARIO, false).apply();
        PutDataMapRequest dataMap = PutDataMapRequest.create(Statics.DATABTFAILED);
        dataMap.getDataMap().putInt("reason", reason);
        PutDataRequest request = dataMap.asPutDataRequest();
        request.setUrgent();
        Wearable.DataApi.putDataItem(mGoogleApiClient, request);
    }

    private void setupBTConnection() {
        if (mBTConnectedThread == null) {
            if (debugMode) Log.d(TAG, "trying to setup BT connection");
            boolean bFoundBTVario = false;
            Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
            if (pairedDevices.size() > 0) {
                final String[] pairedDevicesStrings = new String[pairedDevices.size()];
                int i = 0;
                // There are paired devices. Get the name and address of each paired device.
                for (BluetoothDevice device : pairedDevices) {
                    pairedDevicesStrings[i++] = device.getName() + "\n" + device.getAddress();
                    if (prefs.getString(Statics.PREFBTVARIODEVICE, "").equals(device.getAddress())) {
                        bFoundBTVario = true;
                        break;
                    }
                }
                if (bFoundBTVario) {
                    connectBTVarioDevice(true);
                } else {
                    if (debugMode) Log.d(TAG, "no known BT device, user need to choose one");
                    final AlertDialog.Builder dialog = new AlertDialog.Builder(MainWearActivity.this)
                            .setTitle(R.string.choose_bt_device)
                            .setSingleChoiceItems(pairedDevicesStrings, -1, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    if (debugMode) Log.d(TAG, "selected BT device: " + pairedDevicesStrings[which] + ", adress: " + pairedDevicesStrings[which].substring(pairedDevicesStrings[which].length() - 17));
                                    prefs.edit().putString(Statics.PREFBTVARIODEVICE, pairedDevicesStrings[which].substring(pairedDevicesStrings[which].length() - 17)).apply();
                                }
                            })
                            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    connectBTVarioDevice(true);
                                }
                            })
                            .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    sendBTFailed(Statics.MY_BT_FAILED_USER);
                                }
                            });
                    final AlertDialog alert = dialog.create();
                    alert.show();
                }
            } else {
                sendBTFailed(Statics.MY_BT_FAILED_NO_DEVICE);
            }
        } else {
            if (debugMode) Log.d(TAG, "BT already connected");
        }
    }

    private void disableBTConnection() {
        if (debugMode) Log.d(TAG, "disabling BT connection");
        // Cancel any thread currently running a connection
        if (mBTConnectedThread != null) {
            mBTConnectedThread.cancel();
            mBTConnectedThread = null;

            Toast.makeText(getApplicationContext(), R.string.bt_disconnect, Toast.LENGTH_LONG).show();
        }
        // Cancel any thread attempting to make a connection
        if (mBTConnectThread != null) {
            mBTConnectThread.cancel();
            mBTConnectThread = null;
        }
    }

    private void connectBTVarioDevice(boolean secure) {
        // Get the BluetoothDevice object
        BluetoothDevice device = null;
        try {
            device = mBluetoothAdapter.getRemoteDevice(prefs.getString(Statics.PREFBTVARIODEVICE, ""));
        } catch (IllegalArgumentException e) {
            if (debugMode) Log.d(TAG, "device address invalid");
        }
        if (device != null) {
            // Cancel any thread currently running a connection
            if (mBTConnectedThread != null) {
                if (debugMode) Log.d(TAG, "cancel estabilished BT connection to make a new one");
                mBTConnectedThread.cancel();
                mBTConnectedThread = null;
            }
            // Cancel any thread attempting to make a connection
            if (mBTConnectThread != null) {
                if (debugMode) Log.d(TAG, "cancel pending BT connection to make a new one");
                mBTConnectThread.cancel();
                mBTConnectThread = null;
            }
            // Start the thread to connect with the given device
            mBTConnectThread = new ConnectThread(device, secure);
            mBTConnectThread.start();

            Toast.makeText(getApplicationContext(), R.string.bt_connecting, Toast.LENGTH_LONG).show();
        } else {
            if (debugMode) Log.d(TAG, "selected BT device not found");
            sendBTFailed(Statics.MY_BT_FAILED_NO_DEVICE);
        }
    }

    private void connectedBT(BluetoothSocket socket, BluetoothDevice
            device, final String socketType) {
        if (debugMode) Log.d(TAG, "connected BT, Socket Type:" + socketType);
        // Start the thread to manage the connection and perform transmissions
        mBTConnectedThread = new ConnectedThread(socket, socketType);
        mBTConnectedThread.start();

        Toast.makeText(getApplicationContext(), R.string.bt_connected, Toast.LENGTH_LONG).show();
    }

    private void connectionBTFailed() {
        if (debugMode) Log.d(TAG, "connection BT failed");
        // reconnect after some delay
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                connectBTVarioDevice(true);
            }
        }, 1000 * 5);
        Toast.makeText(getApplicationContext(), R.string.bt_connection_failed, Toast.LENGTH_LONG).show();
    }

    private void connectionBTLost() {
        if (debugMode) Log.d(TAG, "connection BT lost");
        // reconnect after some delay
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                connectBTVarioDevice(true);
            }
        }, 1000);
        Toast.makeText(getApplicationContext(), R.string.bt_connection_lost, Toast.LENGTH_LONG).show();
    }

    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        private String mSocketType;

        public ConnectThread(BluetoothDevice device, boolean secure) {
            // Use a temporary object that is later assigned to mmSocket
            // because mmSocket is final.
            BluetoothSocket tmp = null;
            mmDevice = device;
            mSocketType = secure ? "Secure" : "Insecure";

            try {
                if (secure) {
                    tmp = device.createRfcommSocketToServiceRecord(
                            MY_UUID_SECURE);
                } else {
                    tmp = device.createInsecureRfcommSocketToServiceRecord(
                            MY_UUID_INSECURE);
                }
            } catch (IOException e) {
                Log.e(TAG, "Socket's create() method failed", e);
            }
            mmSocket = tmp;
        }

        public void run() {
            // Cancel discovery because it otherwise slows down the connection.
            //mBluetoothAdapter.cancelDiscovery();

            try {
                // Connect to the remote device through the socket. This call blocks
                // until it succeeds or throws an exception.
                mmSocket.connect();
            } catch (IOException connectException) {
                // Unable to connect; close the socket and return.
                try {
                    mmSocket.close();
                } catch (IOException closeException) {
                    Log.e(TAG, "Could not close the client socket", closeException);
                }
                //if (debugMode) Log.e(TAG, "mmSocket connect failed: ", connectException);
                new Handler(Looper.getMainLooper()).post(new Runnable () {
                    @Override
                    public void run () {
                        connectionBTFailed();
                    }
                });
                return;
            }

            // Start the connected thread
            new Handler(Looper.getMainLooper()).post(new Runnable () {
                @Override
                public void run () {
                    connectedBT(mmSocket, mmDevice, mSocketType);
                }
            });
        }

        // Closes the client socket and causes the thread to finish.
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the client socket", e);
            }
        }
    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        //private byte[] mmBuffer; // mmBuffer store for the stream
        private final BufferedReader mmReader; // mmReader for raed whole line
        private boolean bKeepAlive = true;

        public ConnectedThread(BluetoothSocket socket, String socketType) {
            if (debugMode) Log.d(TAG, "create ConnectedThread: " + socketType);
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            BufferedReader tmpReader = null;

            // Get the input and output streams; using temp objects because
            // member streams are final.
            try {
                tmpIn = socket.getInputStream();
                tmpReader = new BufferedReader(new InputStreamReader(tmpIn), 256);
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when creating input stream", e);
            }
            try {
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when creating output stream", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
            mmReader = tmpReader;
        }

        public void run() {
            //mmBuffer = new byte[1024];
            int numBytes; // bytes returned from read()
            String line = null;

            // Keep listening to the InputStream until an exception occurs.
            while (bKeepAlive) {
                try {
                    line = mmReader.readLine();
                    numBytes = line.length();
                    Message readMsg = mHandler.obtainMessage(
                            Statics.MY_BT_MESSAGE_READ, numBytes, -1,
                            line);
                    /*// Read from the InputStream.
                    numBytes = mmInStream.read(mmBuffer);
                    // Send the obtained bytes to the UI activity.
                    Message readMsg = mHandler.obtainMessage(
                            Statics.MY_BT_MESSAGE_READ, numBytes, -1,
                            mmBuffer);*/
                    readMsg.sendToTarget();
                } catch (IOException e) {
                    if (bKeepAlive) {
                        Log.e(TAG, "Input stream was disconnected", e);
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                connectionBTLost();
                            }
                        });
                    }
                    break;
                }
            }
        }

        // Call this from the main activity to send data to the remote device.
        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);

                // Share the sent message with the UI activity.
                Message writtenMsg = mHandler.obtainMessage(
                        Statics.MY_BT_MESSAGE_WRITE, -1, -1, bytes);
                writtenMsg.sendToTarget();
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when sending data", e);

                // Send a failure message back to the activity.
                /*Message writeErrorMsg =
                        mHandler.obtainMessage(MessageConstants.MESSAGE_TOAST);
                Bundle bundle = new Bundle();
                bundle.putString("toast",
                        "Couldn't send data to the other device");
                writeErrorMsg.setData(bundle);
                mHandler.sendMessage(writeErrorMsg);*/
            }
        }

        // Call this method from the main activity to shut down the connection.
        public void cancel() {
            try {
            	bKeepAlive = false;
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the connect socket", e);
            }
        }
    }
}