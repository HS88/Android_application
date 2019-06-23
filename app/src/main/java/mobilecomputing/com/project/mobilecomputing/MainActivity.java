package mobilecomputing.com.project.mobilecomputing;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.widget.ArrayAdapter;
import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.support.v4.content.ContextCompat;
import android.text.Html;
import android.util.Log;
import android.widget.ListView;
import android.widget.TextView;
import android.telephony.TelephonyManager;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.sql.Time;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import 	java.util.Random;
import java.util.regex.Pattern;

import static android.content.ContentValues.TAG;

public class MainActivity extends Activity {

    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_OBJECT = 4;
    public static final int MESSAGE_TOAST = 5;
    public static final int MESSAGE_LOCATION_CHANGE = 6;
    public static final int MESSAGE_LOCATION_ENABLE = 7;
    public static final int MESSAGE_INITIATE = 8;
    public static final int MESSAGE_REQUEST = 9;
    public static final int MESSAGE_REPLY = 10;
    public static final String DEVICE_OBJECT = "device_name";
    public static final String LOCATION_OBJECT = "device_location";

//    private TextView status;

    private ArrayAdapter<DeviceItem> discoveredDeviceAdaptor;
    private Location mLocation = new Location("");
    LocationHandler locationHandler;
    LocationManager locationManager;


    private ArrayList<DeviceItem> deviceItemList;
    HashMap<String, DeviceItem> deviceMap;

    private BluetoothAdapter bluetoothAdapter;
    private String sNewName;// = GetDeviceName(this);
//    private static boolean permissionSet = false;
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;
    private CommConnector commConnector;
    private BluetoothDevice connectingDevice;

    DatabaseHelper db;
    private NetworkReceiver receiver;
    boolean isGPS = false;
    boolean gpsStarted = false;
    private int getRandomNumber(int min,int max) {
        return (new Random()).nextInt((max - min) + 1) + min;
    }

    public static void sleep(long sleepTime)    {
        long wakeupTime = System.currentTimeMillis() + sleepTime;
        while (sleepTime > 0)
        {
            try
            {
                Thread.sleep(sleepTime);
            }
            catch (InterruptedException e)
            {
                sleepTime = wakeupTime - System.currentTimeMillis();
            }
        }
    }

    private void setStatus(String s) {
        //status.setText(s);
    }

    private void initiateInformationSharing(){
        // choose the appropriate device to connect to.
       // boolean isSmallerID = false;
        bluetoothAdapter.cancelDiscovery();
        if (!deviceMap.isEmpty()) {
            Iterator it = deviceMap.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, DeviceItem> entry = (Map.Entry<String, DeviceItem>)it.next();
                DeviceItem dev = entry.getValue();
                Log.i(TAG, "------------------------  Trying to connect to device " + dev.getDeviceName());
                String deviceAddress = dev.getAddress();
                BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
                Toast.makeText(getApplicationContext(), bluetoothAdapter.getName() + "\n   trying to connect with \n" + device.getName(), Toast.LENGTH_SHORT).show();
                int isSmallerID = bluetoothAdapter.getName().compareTo(device.getName());
                if( isSmallerID < 0 ) {
                    Log.i(TAG, "+++++++++++++++++++++ Connecting to device: " + device.getName() );
                    commConnector.connect(device);
                }
            }
        }        // once you are connected to device, you will be getting a callback to handler: MESSAGE_DEVICE_OBJECT
    }

    private void sendMessage( boolean replyback) {
        // connection is initiated by device of small ID
        // once connection is established, both devices have an active ReadWriteThread
        // and wait for message. So, device with small id needs to send the data.

        // MESSAGE_DEVICE_OBJECT: inside this, we set the connecting device. Connectingdevice is stored in variable 'connectingDevice'

        // now we need to share the information tha`t I have.
        // once you write the message, you go in passive state. The writing thread will get callback to MESSAGE_WRITE
        // On the other hand, receiving thread will get callback to MESSAGE_READ

        boolean isSmallerID = bluetoothAdapter.getName().compareTo(connectingDevice.getName()) < 0;
        Log.i(TAG,"=- =-= -= - =- =- =- Inside the sendMessage block" + isSmallerID + " :: "+ replyback + "==================");
        if ( replyback ^ isSmallerID ) // if i am smaller id, i will send data
        {
            // TODO
            // get the list of all mac-addresses and form a string
            String message = MESSAGE_INITIATE +"," + mLocation.getLatitude() + "," + mLocation.getLongitude();//bluetoothAdapter.getName();
            if (commConnector.getState() != CommConnector.STATE_CONNECTED) {
                Toast.makeText(this, "Connection was lost!", Toast.LENGTH_SHORT).show();
                commConnector.start();// we need to start listening again.
                return;
            }

            if (message.length() > 0){
                byte[] send = message.getBytes();
                commConnector.write(send);
            }
        }
        else if(replyback && isSmallerID){
            Log.i(TAG,"=- =-= -= - =- =- =- Inside the else condition of sendMessage block. ==================");
            commConnector.start();// now the other device will call its connectionLost() method and restart the commConnector.
        }
    }
    private void sendMessageThreeWay( String readMessage ) {
        String[]message  = readMessage.split(";");
        switch (Integer.parseInt(message[0])) {
            case MESSAGE_INITIATE:
                Log.i(TAG, "=-=--=-=-=-=-= Inside sendMessageThreeWay :: MESSAGE_INITIATE  -=-=-=-=-=-=-=-=-=-");
                // TODO
                // Read the message and get two way difference of the mac addresses.
                // Send the macaddresses that you need and macaddresses that the other device needs
                // < message_type; macaddress needed(,separated); macaddress:long-lat( ')'separated)>
                String request = MESSAGE_REQUEST + ";" + message[1] + ";" + bluetoothAdapter.getAddress() + "," + mLocation.getLatitude()+ "-" +mLocation.getLongitude();//bluetoothAdapter.getName();
                if (commConnector.getState() != CommConnector.STATE_CONNECTED) {
                    Toast.makeText(this, "Connection was lost!", Toast.LENGTH_SHORT).show();
                    commConnector.start();// we need to start listening again.
                    return;
                }
                if ( request.length() > 0 ){
                    byte[] send = request.getBytes();
                    commConnector.write(send);
                }
                break;
            case MESSAGE_REQUEST:
                // todo
                // Send the requested macaddresses and update the data in your db
                // < message_type; macaddress:long-lat(,separated)>
                String response = MESSAGE_REPLY + ";" + bluetoothAdapter.getAddress() + "," + mLocation.getLatitude()+ "-" +mLocation.getLongitude();//bluetoothAdapter.getName();
                Log.i(TAG, "=-=--=-=-=-=-= Inside sendMessageThreeWay :: MESSAGE_REQUEST  -=-=-=-=-=-=-=-=-=-" + response );
                if (commConnector.getState() != CommConnector.STATE_CONNECTED) {
                    Toast.makeText(this, "Connection was lost!", Toast.LENGTH_SHORT).show();
                    commConnector.start();// we need to start listening again.
                    return;
                }
                if ( response.length() > 0 ){
                    byte[] send = response.getBytes();
                    commConnector.write(send);
                }break;
            case MESSAGE_REPLY:
                Log.i(TAG, "=-=--=-=-=-=-= Inside sendMessageThreeWay :: MESSAGE_REPLY  -=-=-=-=-=-=-=-=-=-");
                //todo
                // update the db and close the connection
                commConnector.start();
                break;
        }
    }

    private void sendMessageReply( ) {
        boolean replyback = false;

        // TODO
        // messageReceived should be in the form <request,data>
        // IF the data is empty and I am the smaller id, i will disconnect
        // Get the mac addresses that I have and subtract them from the received addresses.
        // Store thr macarresses uniquely and send back those addresses that were not present in earlier message

        // < message_type; macaddress stored(,separated); >

        String message = MESSAGE_INITIATE + ";" + bluetoothAdapter.getAddress();//bluetoothAdapter.getName();
        Log.i(TAG,"=- =-= -= - =- =- =- Inside the sendMessageReply block ==================" + message );
        if (commConnector.getState() != CommConnector.STATE_CONNECTED) {
            Toast.makeText(this, "Connection was lost!", Toast.LENGTH_SHORT).show();
            commConnector.start();// we need to start listening again.
            return;
        }

        if ( message.length() > 0 ){
            byte[] send = message.getBytes();
            commConnector.write(send);
        }
    }

    private Handler handler = new Handler(new Handler.Callback(){
        @Override
        public boolean handleMessage(Message msg){
            switch (msg.what) {
                case MESSAGE_LOCATION_CHANGE:
                    Log.i(TAG,"=-=--=-=-=-=-= Inside Handler message :: MESSAGE_LOCATION_CHANGE  -=-=-=-=-=-=-=-=-=-");
                    mLocation = msg.getData().getParcelable(LOCATION_OBJECT);
                    Log.i(TAG,"-=-=-=-=-=-=-=-=-=- " + mLocation.getLatitude());
                    locationManager.removeUpdates(locationHandler);
                    break;
                case MESSAGE_LOCATION_ENABLE:
                    Log.i(TAG,"=-=--=-=-=-=-= Inside Handler message :: MESSAGE_LOCATION_CHANGE  -=-=-=-=-=-=-=-=-=-");
                    mLocation = msg.getData().getParcelable(LOCATION_OBJECT);
                    Log.i(TAG,"-=-=-=-=-=-=-=-=-=- " + mLocation.getLatitude());
                    locationManager.removeUpdates(locationHandler);
                    break;
                case MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case CommConnector.STATE_CONNECTED:
                            break;
                        case CommConnector.STATE_CONNECTING:
                            setStatus("Connecting...");
                            break;
                        case CommConnector.STATE_LISTEN:
                        case CommConnector.STATE_NONE:
                            break;
                    }
                    break;
                case MESSAGE_WRITE:
                    // you will come here once you have written the message
                    byte[] writeBuf = (byte[]) msg.obj;
                    String writeMessage = new String(writeBuf);
                    Log.i(TAG,"=-=-=- -= - =- =- = -=- Message write =- = -=-- =- =- = -= - =- " + writeMessage);
                    // you do not need to do anything here. Maybe we can update the timings here.
                    break;
                case MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    String readMessage = new String(readBuf, 0, msg.arg1);
                    // <message type, macaddresses, data>
                    Log.i(TAG,"======= MESSAGE RECEIVED.==========" + readMessage);
                    Toast.makeText(getApplicationContext(),readMessage,Toast.LENGTH_LONG).show();
                    // chatMessages.add(connectingDevice.getName() + ":  " + readMessage);
                    // chatAdapter.notifyDataSetChanged();

                    // Now that you are reading the message, it means you need to store this message.
                    // update your local database.
                   // sendMessageReply(true, readMessage); // we will reply back depending on the device id
                    sendMessageThreeWay(readMessage);
                    break;
                case MESSAGE_DEVICE_OBJECT:
                    connectingDevice = msg.getData().getParcelable(DEVICE_OBJECT);
                    Toast.makeText(getApplicationContext(), "Connected to " + connectingDevice.getName(),
                            Toast.LENGTH_SHORT).show();
                    // now you know device is connected.
                    // you need to call the send message.
                    //sendMessage(false);
                    sendMessageReply();
                    break;
                case MESSAGE_TOAST:
                    Toast.makeText(getApplicationContext(), msg.getData().getString("toast"),
                            Toast.LENGTH_SHORT).show();
                    break;
            }
            return false;
        }
    });

    private boolean CheckBluetoothStatus(){
        return bluetoothAdapter.getScanMode() == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE;
    }

    private void ToggleBluetooth(){
        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 400);
        startActivity(discoverableIntent);
    }

    public String GetDeviceName(MainActivity mainActivity){
        String Imei = "ClientLocationFinder_" + Settings.Secure.getString(getContentResolver(),Settings.Secure.ANDROID_ID);
        return Imei;
    }

    protected void checkLocationPermissionAndStartDiscovery() {
        Log.i(TAG," _+_+_+_+_=-=-=-= checkLocationPermissionAndStartDiscovery =-=-=-=-=-=-=-=-=- ");
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                                    PERMISSION_REQUEST_COARSE_LOCATION);
        }
        else {
            Log.i(TAG," _+_+_+_+_=-=-=-= checkLocationPermissionAndStartDiscovery -2 =-=-=-=-=-=-=-=-=- ");
            getLocation();
            startDiscovery();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_COARSE_LOCATION: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    getLocation();
                    startDiscovery();
                } else {
                    // TODO
                }
                break;
            }
        }
    }

    private void getLocation() {
        try {
            if (isGPS) {
                Log.d(TAG, "GPS on");
                Log.i(TAG, "INSIDE GETLOCATION");
                if( !gpsStarted ) {
                    Log.i(TAG, "INSIDE GETLOCATION - 2 ");
                    gpsStarted = true;
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationHandler);
                }
                if (locationManager != null) {
                    mLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                    if (mLocation != null)
                        Log.i(TAG, "INSIDE GETLOCATION. Getting locattion from lastKnownLocation : " + mLocation.getLatitude());
                    else {
                        mLocation = new Location("");
                        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationHandler);
                    }
                }
            }
            else {
                Log.d(TAG, "Can't get location");
            }
        }
        catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    public void startDiscovery() {
        if( bluetoothAdapter.isDiscovering())
            bluetoothAdapter.cancelDiscovery();

        IntentFilter bluetoothFoundFilter = new IntentFilter( BluetoothDevice.ACTION_FOUND );
        registerReceiver(discoverDevice, bluetoothFoundFilter);
        bluetoothFoundFilter = new IntentFilter( BluetoothAdapter.ACTION_DISCOVERY_FINISHED );
        registerReceiver(discoverDevice, bluetoothFoundFilter);
//        commConnector.start();
        bluetoothAdapter.startDiscovery();
    }

    private final BroadcastReceiver discoverDevice = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent){
            TextView text = (TextView) findViewById(R.id.text);
            String action = intent.getAction();
            text.setText(action);
            if( BluetoothDevice.ACTION_FOUND.equals(action) ){
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                Log.i(TAG, "------------------------  found device " + device.getName());
                if( device.getName() != null && Pattern.compile(Pattern.quote("clientlocationfinder"), Pattern.CASE_INSENSITIVE).matcher(device.getName()).find())
                    {
                        String address = device.getAddress();
                        Log.i(TAG,"_+_+_+_+_+_+_+_+_=-=- Found required object with name: " + device.getName() + " =--=--=-=-=-=");
                        if (!deviceMap.containsKey( address ) ) {
                            DeviceItem newDevice = new DeviceItem(device.getName(), address);
                            deviceMap.put(address, newDevice);
                            Log.i(TAG,"_+_+_+_+_+_+_+_+_=-=- adding device: " + device.getName() + " =--=--=-=-=-=");;
                        }
                    }
            }
            else if( BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)){
                Log.i(TAG, "------------------------  Discovery finished  " );
                Log.i(TAG, deviceMap.toString() );
                initiateInformationSharing();
                text.setText("");
            }
        }
    };

    public void showSettingsAlert() {
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
        alertDialog.setTitle("GPS is not Enabled!");
        alertDialog.setMessage("Turn on GPS for this App");
        alertDialog.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                startActivity(intent);
            }
        });
        alertDialog.show();
    }

    private void getLastLocation() {
        try {
            Criteria criteria = new Criteria();
            String provider = locationManager.getBestProvider(criteria, false);
            Location loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
//            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000,0, locationHandler);
            Log.d(TAG, "INDSIDE GET LAST KNOWN LOCATION::   =========== " + provider);
            Log.d(TAG, loc == null ? "NO LastLocation" : loc.toString());
            if( loc != null)
                mLocation = loc;
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        status = (TextView) findViewById(R.id.status);
//        int permissionCheck1 = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE);
//        int permissionCheck2 = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION);
        setContentView(R.layout.activity_main);
        TextView text = (TextView) findViewById(R.id.text);
//        deviceItemList = new ArrayList<DeviceItem>();
        deviceMap = new HashMap<String, DeviceItem>();
        db = new DatabaseHelper(this);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        locationHandler = new LocationHandler(this, handler );
        locationManager = (LocationManager) getSystemService(Service.LOCATION_SERVICE);
        mLocation.setLatitude(0.0);
        mLocation.setLongitude(0.0);
        isGPS = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        Log.i(TAG," _+_+_+_+_=-=-=-= "+ isGPS +"=-=-=-=-=-=-=-=-=- ");
        if (!isGPS) {
            Log.d(TAG, "Connection off");
            showSettingsAlert();
            getLastLocation();
        }
        sNewName = GetDeviceName(this);
        Log.i(TAG," _+_+_+_+_=-=-=-= "+ sNewName +"=-=-=-=-=-=-=-=-=- ");
        if( !CheckBluetoothStatus() ){
            Log.i(TAG," _+_+_+_+_=-=-=-= BLUETOOTH STATUS =-=-=-=-=-=-=-=-=- ");
            ToggleBluetooth();
        }


        int count = 0;
        Log.i(TAG," _+_+_+_+_=-=-=-= BEFORE CHECK STATUS =-=-=-=-=-=-=-=-=- ");
        while(!CheckBluetoothStatus()) {}
        Log.i(TAG," _+_+_+_+_=-=-=-= AFTER CHECK STATUS =-=-=-=-=-=-=-=-=- ");
        // Change name of the Bluetooth adapter
        bluetoothAdapter.setName( sNewName );
        final Handler runDiscovery = new Handler();
        Log.i(TAG," _+_+_+_+_=-=-=-= POSTING HANDLER =-=-=-=-=-=-=-=-=- ");
        runDiscovery.post(
                new Runnable() {
                    @Override
                    public void run() {
                        checkLocationPermissionAndStartDiscovery();
                        runDiscovery.postDelayed(this, 30000);
                    }
                }

        );

        discoveredDeviceAdaptor = new ArrayAdapter<DeviceItem>(this, android.R.layout.simple_list_item_1);
        ListView discoveredDevices = (ListView) this.findViewById(R.id.discoveredDeviceList);
        discoveredDevices.setAdapter(discoveredDeviceAdaptor);

    }

    @Override
    public void onStart() {
        super.onStart();
        Log.i(TAG,"_+_+_+__+_+_+_+_+_  INSIDE OnStart   _+_+_+_+_+_+__+__");
        commConnector = new CommConnector(this, handler);
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.i(TAG,"_+_+_+__+_+_+_+_+_  INSIDE OnResume   _+_+_+_+_+_+__+__");
        isGPS = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        Log.i(TAG,"_+_+_+__+_+_+_+_+_  " + isGPS + "  _+_+_+_+_+_+__+__");
        sNewName = GetDeviceName(this);
        if (commConnector != null) {
            if (commConnector.getState() == CommConnector.STATE_NONE) {
                //Toast.makeText(getApplicationContext(), "Starting comm", Toast.LENGTH_SHORT).show();
                //commConnector.start();
                //Toast.makeText(getApplicationContext(), commConnector.toString(), Toast.LENGTH_SHORT).show();
                commConnector.start();
                long random = getRandomNumber(1000,5000);//Random.nextInt(n);
                //sleep(random);
                String address = "54:40:AD:E5:94:17";
              //  if( !bluetoothAdapter.getAddress().equals(address) )
              //      connectToDevice(address);
            }
        }
    }
}
