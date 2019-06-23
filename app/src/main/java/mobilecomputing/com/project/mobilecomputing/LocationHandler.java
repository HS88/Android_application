package mobilecomputing.com.project.mobilecomputing;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import static android.content.ContentValues.TAG;

/**
 * Created by harmeet-US on 2/25/2018.
 */

class LocationHandler implements LocationListener {
    private final Handler handler;
    private final Context context;

    public LocationHandler(Context context, Handler handler) {
        this.handler = handler;
        this.context = context;
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.i(TAG,"//////////////////  ON LOCATION CHANGED       ////////////////////////////");
        Message msg = handler.obtainMessage(MainActivity.MESSAGE_LOCATION_CHANGE);
        Bundle bundle = new Bundle();
        bundle.putParcelable(MainActivity.LOCATION_OBJECT, location);
        msg.setData(bundle);
        handler.sendMessage(msg);
    }

    public void onStatusChanged(String provider, int status, Bundle extras) {
        Log.i(TAG,"Toast.makeText(getApplicationContext(),\"GPS CALLBACK-2\",Toast.LENGTH_LONG).show()");
    }

    public void onProviderEnabled(String provider) {
        Log.i(TAG,"Toast.makeText(getApplicationContext(),\"GPS CALLBACK-3\",Toast.LENGTH_LONG).show()");
    }

    public void onProviderDisabled(String provider) {
        Log.i(TAG,"Toast.makeText(getApplicationContext(),\"GPS CALLBACK-244\",Toast.LENGTH_LONG).show()");
    }
}
