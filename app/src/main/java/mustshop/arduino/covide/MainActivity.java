package mustshop.arduino.covide;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.RecyclerView;
import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import com.bumptech.glide.Glide;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;


public class MainActivity extends AppCompatActivity implements DeviceListInterface {

    private Context context;
    private BluetoothAdapter mBluetoothAdapter;
    private List<FoundBluetoothDevice> devices = new ArrayList<>();
    private boolean searching = false;
    private SharedPreferences.Editor editor;
    private SharedPreferences prefs;
    private final String MY_PREFS_NAME = "mustshop.arduino.covide";
    private long reportTime = 1*60; // 1 minutes converted to seconds
    private long cleanUpTime = 24*60*60*1000; // 24 hours converted to milliseconds
    private long unseenTime = 24*60*60*1000; // 24 hours converted to milliseconds
    private long onlineCheckTime = 5*1000; // 5 seconds converted to milliseconds
    private long previous_online_check = System.currentTimeMillis();

    private static final long month = (long) 60 * 60 * 24 * 30 * 1000; // one month converted to milliseconds
    private static final long week = 1000*60*60*24*7; // one week converted to milliseconds
    private static final long day = 1000*60*60*24; // one day converted to milliseconds
    private static final long hour = 1000*60*60; // one day converted to milliseconds
    private static final long min = 1000*60; // one day converted to milliseconds
    private static final long sec = 1000; // one second converted to milliseconds
    private long last_online = 0;

    private TextView status, connectionIndicator, set, unset;

    private FirebaseAuth mAuth;
    private String myUid;
    private RecyclerView deviceListRecyclerView;
    private DeviceListAdapter deviceListAdapter;

    private boolean doingOnlineCheck = false;
    private boolean firstTime = true;

    private boolean ireported = false;
    private long lastCleanup = System.currentTimeMillis();

    private OkHttpClient client = new OkHttpClient();
    private List<FoundBluetoothDevice> live_devices = new ArrayList<>();
    private List<QRCode> QRCodes = new ArrayList<>();
    private boolean updatedList = false;
    private Handler handler = new Handler();
    private String mySickStatus = "";
    private ImageView qrcodingImage;
    private TextView place;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Thread.setDefaultUncaughtExceptionHandler(new TopExceptionHandler());
        context = this;

        try {
            status = findViewById(R.id.status);
            place = findViewById(R.id.place);
            connectionIndicator = findViewById(R.id.connectionIndicator);
            set = findViewById(R.id.set);
            unset = findViewById(R.id.unset);

            qrcodingImage = findViewById(R.id.qrcodingImage);

            glider(context, R.drawable.qr, qrcodingImage);

            if (!doIHaveBluetoothPermission()) {
                requestBluetoohPermission();
            } else if(!doIHaveLocationPermission()){
                requestLocationPermission();
            } else if(!doIHaveConnectBluetoothPermission()){
                requestConnectBluetoothPermission();
            } else if(!doIHaveScanBluetoothPermission()){
                requestScanBluetoothPermission();
            } else {
                oncreateStuff();
            }
        } catch(Exception e){
            log(e);
        }
    }

    void glider(Context context, int source, ImageView image){
        try {
            Glide.with(context).load(source).into(image);
        } catch (Exception e) {
            Log.i("HH", e.toString());
            e.printStackTrace();
        }
    }

    private void oncreateStuff() {

        // log the person in, here i'm just using anonymous auth a.k.a it gives me a uuid for each device separately
        try {
            mAuth = FirebaseAuth.getInstance();
            FirebaseUser currentUser = mAuth.getCurrentUser();
            if(currentUser==null){
                mAuth.signInAnonymously()
                        .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                            @Override
                            public void onComplete(@NonNull Task<AuthResult> task) {
                                if (task.isSuccessful()) {
                                    // Sign in success, update UI with the signed-in user's information
                                    FirebaseUser user = mAuth.getCurrentUser();
                                    myUid = user.getUid();
                                    search();
                                } else {
                                    print("Failed to login, are you connected to the internet?");
                                }
                            }
                        });
            } else {
                myUid = currentUser.getUid();
                search();
            }
        } catch(Exception e){
            e.printStackTrace();
            log(e);
        }
    }

    @Override
    protected void onDestroy() {
        try {

            MainActivity.this.unregisterReceiver(myReceiver);
        } catch(Exception ignore){}
        super.onDestroy();
    }

    private long previous_locations_update = 0;
    private long duration_for_updating_locations = 3000; // 3 seconds
    private void search(){
        declareBluetoothStuff();

        // this is just to check if we are online
        new Thread(new Runnable() {
            @Override
            public void run() {
                while(true){
                    // check if we are still online, we should only sneak in an online check when we are in a search of devices to encourage multitasking
                    if(firstTime || (searching && !doingOnlineCheck && System.currentTimeMillis() - previous_online_check >= onlineCheckTime) ){
                        firstTime = false; // just to allow to ping google on the first launch brk
                        previous_online_check = System.currentTimeMillis();
                        doingOnlineCheck = true;
                        performOnlineCheck();
                    }
                }
            }
        }).start();

        // this is just to check if we are online
        new Thread(new Runnable() {
            @Override
            public void run() {
                listenToContaminatedPlacesDatabase();

                boolean currentlyDisplayingWeVisitedAContaminatedPlace = false;
                FirebaseDatabase database = FirebaseDatabase.getInstance();
                while(true){

                    // keep locations around u up to date, if u r sick, report all places automatically, if u r not sick remove all places
                    if(System.currentTimeMillis() - previous_locations_update > duration_for_updating_locations){
                        previous_locations_update = System.currentTimeMillis();

                        if(mySickStatus.equals("Safe")){
                            for(QRCode qrCode: QRCodes){
                                @SuppressLint("MissingPermission")
                                DatabaseReference reportLocationRef = database.getReference("contaminated_places").child(qrCode.qrcode).child(mBluetoothAdapter.getName().split(" ## ")[0] + " %% " + myUid);
                                reportLocationRef.addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override
                                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                                        if(snapshot.getValue()!=null)
                                            reportLocationRef.removeValue();
                                    }

                                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                                });
                            }
                        } else if(mySickStatus.equals("Somebody3dak") || mySickStatus.equals("selfReportedlySick")){
                            for(QRCode qrCode: QRCodes){
                                @SuppressLint("MissingPermission")
                                DatabaseReference reportLocationRef = database.getReference("contaminated_places").child(qrCode.qrcode).child(mBluetoothAdapter.getName().split(" ## ")[0] + " %% " + myUid);
                                reportLocationRef.addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override
                                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                                        if(snapshot.getValue()==null)
                                            reportLocationRef.setValue(String.valueOf(System.currentTimeMillis()));
                                    }

                                    @Override public void onCancelled(@NonNull DatabaseError error) {
                                        log("aha " + error);
                                    }
                                });
                            }
                        }
                    }

                    if(weVisitedAContaminatedPlace){
                        if(!currentlyDisplayingWeVisitedAContaminatedPlace){
                            currentlyDisplayingWeVisitedAContaminatedPlace = true;
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    place.setText("Place Infected");
                                    place.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.redbutton, null));
                                    applyColor(status, Color.WHITE, true);
                                }
                            });
                        }
                    }

                    if(!weVisitedAContaminatedPlace){
                        if(currentlyDisplayingWeVisitedAContaminatedPlace){
                            currentlyDisplayingWeVisitedAContaminatedPlace = false;
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    place.setText("Place Safe");
                                    place.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.greenbutton, null));
                                    applyColor(status, Color.BLACK, true);
                                }
                            });
                        }
                    }

                }
            }
        }).start();

        // bluetooth searching stuff
        new Thread(new Runnable() {
            @SuppressLint("MissingPermission")
            @Override
            public void run() {

                // moved these to here to reduce load on main Ui thread
                prepareSharedPrefs();
                loadSharedPrefs();
                listenToSickPeopleDatabase();
                displayBondedDevices();
                updateRecyclerView();
                attachBroadCastReceiver();

                // constant searching process
                while(true){
                    try {
                        // was bluetooth turned off
                        if(!mBluetoothAdapter.isEnabled()) {
                            setBluetoothEnable(true);
                        }

                        // if my name is not the desired one, change it back
                        if(!mBluetoothAdapter.getName().equals(mBluetoothAdapter.getName().split(" ## ")[0] + " ## " + myUid)){
                            mBluetoothAdapter.setName(mBluetoothAdapter.getName().split(" ## ")[0] + " ## " + myUid);
                        }

                        // if a cleanup is due then clean the devices list (notice we are cleaning up when search was over and before we restart it to avoid multiple places modifying the list)
                        if(!searching && System.currentTimeMillis() - lastCleanup >= cleanUpTime){
                            cleanListUp(); // from old devices that have never come back
                            saveSharedPrefs();

                            // update clean up time for next time
                            lastCleanup = System.currentTimeMillis();
                            editor.putLong("lastCleanup", lastCleanup);
                            editor.commit();
                        }

                        // restart search
                        if(!searching){
                            startSearching();
                        }

                        // if list was updated let's save it
                        if(updatedList){
                            updatedList = false;
                            displayBondedDevices(); // always keeping up to date with bonded devices
                            saveSharedPrefs();
                        }

                    } catch(Exception e){
                        e.printStackTrace();
                        log(e);
                    }

                }

            }
        }).start();
    }

    private void updateRecyclerView() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                // coordinate list stuff
                deviceListRecyclerView = findViewById(R.id.deviceListRecyclerView);
                deviceListAdapter = new DeviceListAdapter(context, devices);
                deviceListRecyclerView.setAdapter(deviceListAdapter);
                deviceListRecyclerView.setLayoutManager(new MyLinearLayoutManager(context, 1, false));
            }
        });
    }

    private void performOnlineCheck() {
        if (pingGoogle()) {
            handler.post(new Runnable() {
                public void run() {
                    connectionIndicator.setText("Online");
                    applyColor(connectionIndicator, Color.GREEN, true);
                    last_online = System.currentTimeMillis();
                    doingOnlineCheck =  false;
                }
            });
        } else {
            handler.post(new Runnable() {
                public void run() {
                    try {
                        if(last_online==0){
                            connectionIndicator.setText("Offline");
                            applyColor(connectionIndicator, Color.RED, true);
                        } else {
                            long time_since_last_online = System.currentTimeMillis() - last_online;
                            if(time_since_last_online >= month){
                                processTime(time_since_last_online, "month", month);
                                applyColor(connectionIndicator, Color.RED, true);
                            } else if(time_since_last_online >= week){
                                processTime(time_since_last_online, "week", week);
                                applyColor(connectionIndicator, Color.RED, true);
                            } else if(time_since_last_online >= day){
                                processTime(time_since_last_online, "day", day);
                                applyColor(connectionIndicator, Color.RED, true);
                            } else if(time_since_last_online >= hour){
                                processTime(time_since_last_online, "hour", hour);
                                applyColor(connectionIndicator, Color.RED, true);
                            } else if(time_since_last_online >= min){
                                processTime(time_since_last_online, "min", min);
                                applyColor(connectionIndicator, Color.GRAY, true);
                            } else {
                                processTime(time_since_last_online, "sec", sec);
                                applyColor(connectionIndicator, Color.GRAY, true);
                            }
                        }
                        doingOnlineCheck =  false;
                    } catch(Exception e){
                    }
                }
            });
        }
    }

    private void processTime(long time_since_last_online, String timeText, long baseTime) {
        int how_many_times = (int) (time_since_last_online / baseTime);
        if(how_many_times>0){
            if(how_many_times>1){
                connectionIndicator.setText(how_many_times + " " + timeText + "s");
            } else {
                connectionIndicator.setText("1 " + timeText);
            }
        }
    }

    private void cleanListUp() {
        for(FoundBluetoothDevice device: devices){
            if(System.currentTimeMillis() - device.last_time_seen >= unseenTime){
                devices.remove(device);
            }
        }
    }

    private void listenToSickPeopleDatabase() {
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference myRef = database.getReference("people_that_are_sick");
        // Read from the database
        myRef.addValueEventListener(new ValueEventListener() {
            @SuppressLint("MissingPermission")
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                // This method is called once with the initial value and again
                // whenever data at this location is updated.
                boolean found = false;
                for(DataSnapshot child: dataSnapshot.getChildren()){
                    Object sick_personObject = child.getKey();
                    if(sick_personObject!=null){
                        String sick_person_device_name = String.valueOf(sick_personObject);
                        sick_person_device_name = sick_person_device_name.replace("%%", "##");

                        // my device is on the cloud
                        if((mBluetoothAdapter.getName().split(" ## ")[0] + " ## " + myUid).equals(sick_person_device_name)){
                            found = true;

                            // display in Ui that u r sick
                            if(ireported){
                                updateUi("selfReportedlySick");
                            } else {
                                updateUi("Somebody3dak");
                            }
                            break;
                        }

                        // check if this sick person was around us for more than say 10 minutes
                        for(FoundBluetoothDevice device: devices){
                            if(device.name.equals(sick_person_device_name)){

                                // if so then let's report this to the website quick
                                if(device.total_time_encountered_with > reportTime){ // 10 minutes converted to seconds
                                    found = true;

                                    // display in Ui that u r sick
                                    updateUi("Somebody3dak");

                                    // report to other user phones that u r sick
                                    DatabaseReference didiAlreadyReportRef = database.getReference("people_that_are_sick").child(mBluetoothAdapter.getName().split(" ## ")[0] + " %% " + myUid);
                                    didiAlreadyReportRef.addListenerForSingleValueEvent(new ValueEventListener() {
                                        @Override
                                        public void onDataChange(@NonNull DataSnapshot snapshot) {

                                            // if i didn't already report that i'm sick then do it
                                            if(dataSnapshot.getValue()==null){ // if i didn't already report that somebody 3dani report it again
                                                didiAlreadyReportRef.setValue(String.valueOf(System.currentTimeMillis()));
                                            }
                                        }

                                        @Override
                                        public void onCancelled(@NonNull DatabaseError error) {
                                        }
                                    });
                                    break; // cbn no need for more
                                }
                            }
                        }

                    }
                }

                // i'm not even in the sick people list display i'm safe
                if(!found){;
                    // if i reported i'm sick but i'm not in the db, apply it again, because maybe i clicked that button while offline
                    if(ireported){
                        updateUi("selfReportedlySick");
                        DatabaseReference didiAlreadyReportRef = database.getReference("people_that_are_sick").child(mBluetoothAdapter.getName().split(" ## ")[0] + " %% " + myUid);
                        didiAlreadyReportRef.setValue(String.valueOf(System.currentTimeMillis()));

                        // in this case i was never in the database and i never self reported so i'm officially safe
                    } else {
                        updateUi("Safe");
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }


    public static class QRCode {
        String qrcode = "";
    }

    private boolean weVisitedAContaminatedPlace = false;
    private String contaminatedPlace = "";
    private void listenToContaminatedPlacesDatabase() {
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference myRef = database.getReference("contaminated_places");
        // Read from the database
        myRef.addValueEventListener(new ValueEventListener() {
            @SuppressLint("MissingPermission")
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                // This method is called once with the initial value and again
                // whenever data at this location is updated.
                boolean tempWeVisitedAContaminatedPlace = false;
                for(DataSnapshot child: dataSnapshot.getChildren()){
                    Object contaminated_placeObject = child.getKey();
                    if(contaminated_placeObject!=null){
                        for(QRCode qrCode: QRCodes){
                            if(qrCode.qrcode.equals(String.valueOf(contaminated_placeObject))){
                                tempWeVisitedAContaminatedPlace = true;
                                weVisitedAContaminatedPlace = true;
                                contaminatedPlace = String.valueOf(contaminated_placeObject);
                                break;
                            }
                        }
                    }
                }
                if(!tempWeVisitedAContaminatedPlace){
                    weVisitedAContaminatedPlace = false;
                    contaminatedPlace = "";
                }

            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }

    private void loadSharedPrefs() {

        // load visited_locations
        String visited_locations_string = prefs.getString("visited_locations", "");
        String[] visited_locations_strings = visited_locations_string.split(",");
        QRCodes = new ArrayList<>();
        for(int i=0; i<visited_locations_strings.length; i++){
            int finalI = i;
            QRCodes.add(new QRCode(){{
                qrcode = visited_locations_strings[finalI];
            }});
        }


        // load device list
        String mac_addresses_strings = prefs.getString("mac_addresses", "");
        ireported = prefs.getBoolean("ireported", false);

        // there are no previously saved macaddresses
        if(mac_addresses_strings.isEmpty())
            return;


        String names_strings = prefs.getString("names", "");
        String total_time_encountered_with_strings = prefs.getString("total_time_encountered_with", "");
        String bonded_strings = prefs.getString("bonded", "");
        String last_time_seen_strings = prefs.getString("last_time_seen", "");

        String[] mac_addresses_string = mac_addresses_strings.split(",");
        String[] names_string = names_strings.split(",");
        String[] total_time_encountered_with_string = total_time_encountered_with_strings.split(",");
        String[] bonded_string = bonded_strings.split(",");
        String[] last_time_seen_string = last_time_seen_strings.split(",");

        try {


            devices = new ArrayList<>();
            for(int i=0; i<mac_addresses_string.length; i++){
                int finalI = i;

                devices.add(new FoundBluetoothDevice(){{
                    name = names_string[finalI];
                    mac_address = mac_addresses_string[finalI];
                    bonded = bonded_string[finalI].equals("1");
                    total_time_encountered_with = Long.parseLong(total_time_encountered_with_string[finalI]);
                    last_time_seen = Long.parseLong(last_time_seen_string[finalI]);
                }});
            }

        } catch(Exception e){
            log(e);
            e.printStackTrace();
            devices = new ArrayList<>();
            saveSharedPrefs();
        }

        // when's the last time we have cleaned our list off of people that were gone for a whild and never even surpassed 10 minutes?
        lastCleanup = prefs.getLong("lastCleanup", System.currentTimeMillis());
    }

    private void saveSharedPrefs() {

        // there are no devices in list
        if(devices.size()==0)
            return;

        StringBuilder names_strings = new StringBuilder("");
        StringBuilder mac_addresses_strings = new StringBuilder("");
        StringBuilder total_time_encountered_with_string = new StringBuilder("");
        StringBuilder bonded_string = new StringBuilder("");
        StringBuilder last_time_seen_string = new StringBuilder("");
        for(FoundBluetoothDevice device:devices){
            names_strings.append(device.name).append(",");
            mac_addresses_strings.append(device.mac_address).append(",");
            total_time_encountered_with_string.append(device.total_time_encountered_with).append(",");
            bonded_string.append(device.bonded?"1":"0").append(",");
            last_time_seen_string.append(device.last_time_seen).append(",");
        }

        editor.putString("names", names_strings.toString());
        editor.putString("mac_addresses", mac_addresses_strings.toString());
        editor.putString("total_time_encountered_with", total_time_encountered_with_string.toString());
        editor.putString("bonded", bonded_string.toString());
        editor.putString("last_time_seen", last_time_seen_string.toString());
        editor.commit();
    }

    private void prepareSharedPrefs() {
        prefs = getSharedPreferences(MY_PREFS_NAME, MODE_PRIVATE);
        editor = getSharedPreferences(MY_PREFS_NAME, MODE_PRIVATE).edit();
    }

    @SuppressLint("MissingPermission")
    public void setBluetoothEnable(Boolean enable) {
        if(mBluetoothAdapter != null){
            if (enable) {
                if (!mBluetoothAdapter.isEnabled()) {
                    mBluetoothAdapter.enable();
                }
            } else {
                if (mBluetoothAdapter.isEnabled()) {
                    mBluetoothAdapter.disable();
                }
            }
        }
    }

    private boolean pingGoogle() {
        Request request = new Request.Builder()
                .url("https://www.google.com/")
                .build();

        try {
            Response response = client.newCall(request).execute();
            if(response.isSuccessful()){
                return true;
            }
        } catch(IOException e){
        } catch(Exception e4){
        }
        return false;
    }

    @SuppressLint("MissingPermission")
    private void startSearching() {

        // whichever device is not currently visible let's set his color to red
        int index = 0;
        for(FoundBluetoothDevice device1: devices){
            boolean found = false;
            int finalIndex = index;

            // if he is visible set him to green
            for(FoundBluetoothDevice device2: live_devices){
                if(device1.mac_address.equals(device2.mac_address)){
                    found = true;
                    if(!device1.live){
                        device1.live = true;
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                deviceListAdapter.notifyItemChanged(finalIndex);
                            }
                        });
                    }
                    break;
                }
            }

            if(!found){
                if(device1.live){
                    device1.live = false;
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            deviceListAdapter.notifyItemChanged(finalIndex);
                        }
                    });
                }
            }
            index ++;
        }

        // make ourselves discoverable
        //startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 0), 1);
        mBluetoothAdapter.startDiscovery();
    }

    private void attachBroadCastReceiver() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothDevice.ACTION_FOUND);
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        MainActivity.this.registerReceiver(myReceiver, intentFilter);
    }

    private BroadcastReceiver myReceiver = new BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        @Override
        public void onReceive(Context context, Intent intent) {
            Message msg = Message.obtain();
            String action = intent.getAction();
            assert action != null;

            switch (action) {
                case BluetoothAdapter.ACTION_DISCOVERY_STARTED:
                    // TODO: update a button to allow the user to stop discovery mode, or just disable it on pause or when they close the devices menu
                    searching = true;
                    break;
                case BluetoothAdapter.ACTION_DISCOVERY_FINISHED:
                    // TODO: update a button to allow the user to stop discovery mode, or just disable it on pause or when they close the devices menu
                    searching = false;
                    break;
                case BluetoothDevice.ACTION_FOUND:
                    //Found, add to a device list

                    BluetoothDevice device1 = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (device1 != null) {

                        // devices discovered during this specific discovery
                        live_devices.add(new FoundBluetoothDevice(){{
                            mac_address = device1.getAddress();
                        }});


                        //print("new device " + device.getName() + " " + device.getAddress());

                        // check if device is already in list
                        boolean alreadyInList = false;
                        int index = 0;
                        for(FoundBluetoothDevice device2:devices){
                            if(device2.mac_address.equals(device1.getAddress())){
                                alreadyInList = true;

                                // if yes, increase time he's been around by say 5 seconds
                                device2.total_time_encountered_with += 5;
                                device2.live = true;

                                // if we didn't already have his name, let's save it
                                if(device1.getName()!=null){
                                    if(device1.getName().contains(" ## "))
                                        device2.name = device1.getName();
                                    else if(device2.name==null)
                                        device2.name = device1.getName();
                                }

                                // notify to save list again
                                deviceListAdapter.notifyItemChanged(index);
                                updatedList = true;
                                break;
                            }
                            index ++;
                        }

                        // if not in list add it
                        if(!alreadyInList){
                            devices.add(new FoundBluetoothDevice(){{
                                total_time_encountered_with = 0;
                                last_time_seen = System.currentTimeMillis();
                                mac_address = device1.getAddress();
                                name = device1.getName();
                                live = true;
                            }});
                            updatedList = true;
                            deviceListAdapter.notifyItemInserted(devices.size()-1);
                        }
                    }
                    break;
            }
        }
    };

    @Override
    public void delete(int positionReal) {
        devices.remove(positionReal);
        updatedList = true;
        deviceListAdapter.notifyItemRemoved(positionReal);
        deviceListAdapter.notifyItemRangeChanged(positionReal, devices.size());
    }

    public void discoverMeClicked(View view) {
        startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 0), 1);
    }

    public void qrcodingClicked(View view) {
        Intent intent = new Intent(context, QRCoding.class);
        startActivity(intent);
        finish();
    }

    public static class FoundBluetoothDevice {
        long total_time_encountered_with = 0;
        long last_time_seen = 0;
        String mac_address = "";
        String name;
        boolean bonded = false;
        boolean live = false;
    }

    @SuppressLint("MissingPermission")
    private void displayBondedDevices(){
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();

        for(BluetoothDevice device1: pairedDevices){
            boolean alreadyInList = false;
            //print("Bonded device " + device1.getName() + " " + device1.getAddress());

            for(FoundBluetoothDevice device2: devices){
                if(device1.getAddress().equals(device2.mac_address)){
                    alreadyInList = true;
                }
            }

            // if this bonded device isn't in list just add it
            if(!alreadyInList){
                devices.add(new FoundBluetoothDevice(){{
                    total_time_encountered_with = 0;
                    last_time_seen = System.currentTimeMillis();
                    mac_address = device1.getAddress();
                    name = device1.getName();
                    bonded = true;
                }});
                updatedList = true;
            }
        }
    }

    private boolean declareBluetoothStuff() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        return mBluetoothAdapter != null;
    }

    private boolean doIHaveBluetoothPermission() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP_MR1)
            return true;
        return PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN);
    }

    private boolean doIHaveConnectBluetoothPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
            return true;
        return PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT);
    }

    private boolean doIHaveScanBluetoothPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
            return true;
        return PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN);
    }

    private boolean doIHaveLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestBluetoohPermission() {
        ActivityCompat.requestPermissions(this, new String[]{ Manifest.permission.BLUETOOTH_ADMIN }, 101);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case 101:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //granted
                    if(!doIHaveLocationPermission()) {
                        requestLocationPermission();
                    } else if(!doIHaveConnectBluetoothPermission()){
                        requestConnectBluetoothPermission();
                    } else if(!doIHaveScanBluetoothPermission()) {
                        requestScanBluetoothPermission();
                    } else {
                        oncreateStuff();
                    }
                } else {
                    //not granted
                    print("Failed to get bluetooth access");
                }
                break;
            case 102:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //granted
                    if(!doIHaveConnectBluetoothPermission()) {
                        requestConnectBluetoothPermission();
                    } else if(!doIHaveScanBluetoothPermission()) {
                        requestScanBluetoothPermission();
                    } else {
                        oncreateStuff();
                    }
                } else {
                    //not granted
                    print("Failed to get location access");
                }
                break;
            case 103:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //granted
                    if(!doIHaveScanBluetoothPermission()) {
                        requestScanBluetoothPermission();
                    } else {
                        oncreateStuff();
                    }
                } else {
                    //not granted
                    print("Failed to get location access");
                }
                break;
            case 104:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //granted
                    oncreateStuff();
                } else {
                    //not granted
                    print("Failed to get location access");
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void requestLocationPermission() {
        ActivityCompat.requestPermissions((Activity) this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                102);
    }

    private void requestConnectBluetoothPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
            return;
        ActivityCompat.requestPermissions((Activity) this,
                new String[]{Manifest.permission.BLUETOOTH_CONNECT},
                103);
    }

    private void requestScanBluetoothPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
            return;
        ActivityCompat.requestPermissions((Activity) this,
                new String[]{Manifest.permission.BLUETOOTH_SCAN},
                104);
    }


    private void print(Object log){
        Toast.makeText(context, String.valueOf(log), Toast.LENGTH_LONG).show();
    }

    private void log(Object log){
        Log.i("HH", String.valueOf(log));
    }

    @SuppressLint("MissingPermission")
    public void setClicked(View view) {
        ireported = true;
        editor.putBoolean("ireported", true);
        editor.commit();

        updateUi("Loading");
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference reportRef = database.getReference("people_that_are_sick").child(mBluetoothAdapter.getName().split(" ## ")[0] + " %% " + myUid);
        reportRef.setValue(String.valueOf(System.currentTimeMillis()));
    }

    @SuppressLint("MissingPermission")
    public void unsetClicked(View view) {
        ireported = false;
        editor.putBoolean("ireported", false);
        editor.commit();

        updateUi("Loading");
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference reportRef = database.getReference("people_that_are_sick").child(mBluetoothAdapter.getName().split(" ## ")[0] + " %% " + myUid);
        reportRef.removeValue();
    }

    private void updateUi(String tempMySickStatus) {
        mySickStatus = tempMySickStatus;
        editor.putString("mySickStatus", tempMySickStatus);
        editor.commit();
        handler.post(new Runnable() {
            public void run() {

                switch(tempMySickStatus){
                    case "selfReportedlySick":
                        status.setText("You Reported\nYou Are Sick");
                        status.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.redbutton, null));
                        applyColor(status, Color.WHITE, true);
                        break;
                    case "Somebody3dak":
                        status.setText("Someone Who Was\nAround You Is Sick");
                        status.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.redbutton, null));
                        applyColor(status, Color.WHITE, true);
                        break;
                    case "Safe":
                        status.setText("You Are Safe");
                        status.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.greenbutton, null));
                        applyColor(status, Color.BLACK, true);
                        break;
                    case "Loading":
                        status.setText("Loading...");
                        status.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.graybutton, null));
                        applyColor(status, Color.WHITE, true);
                        break;
                    default:
                        break;
                }
            }
        });

    }


    private void applyColor(TextView element, int color, boolean defaulter){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            if(defaulter){
                element.setTextColor(color);
            } else {
                element.setTextColor(getColor(color));
            }
        } else {
            if(defaulter){
                element.setTextColor(color);
            } else {
                element.setTextColor(getResources().getColor(color));
            }
        }
    }

}