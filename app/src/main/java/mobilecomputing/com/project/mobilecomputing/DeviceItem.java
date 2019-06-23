package mobilecomputing.com.project.mobilecomputing;

import java.sql.Time;

/**
 * Created by santhu on 22/02/18.
 */

public class DeviceItem {
    private String deviceName;
    private String address;
    private long lastConnectedTime;

    public DeviceItem(String name, String address){
        this.deviceName = name;
        this.address = address;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public String getAddress() {
        return address;
    }

    public void setLastConnectedTime(long time) {
        this.lastConnectedTime = time;
    }

}
