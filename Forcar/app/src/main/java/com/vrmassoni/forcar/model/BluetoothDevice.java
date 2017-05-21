package com.vrmassoni.forcar.model;

/**
 * Created by vrjunior on 21/05/17.
 */

public class BluetoothDevice {
    private String name;
    private String hardwareAddress;

    public BluetoothDevice(String name, String hardwareAddress) {
        this.name = name;
        this.hardwareAddress = hardwareAddress;
    }

    public String getName() {
        return this.name;
    }

    public String getHardwareAddress() {
        return this.hardwareAddress;
    }

}
