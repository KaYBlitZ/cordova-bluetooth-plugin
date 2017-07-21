/*
The MIT License (MIT)

Copyright (c) 2017 Kenneth Liang

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
*/

package org.kayblitz.cordova;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Set;

public class Bluetooth extends CordovaPlugin {

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (startDiscoveryCallbackContext == null) {
                return;
            }
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Discovery has found a device. Get the BluetoothDevice
                // object and its info from the Intent.
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                JSONObject object = new JSONObject();
                String deviceName = device.getName();
                String deviceHardwareAddress = device.getAddress(); // MAC address

                try {
                    object.put("name", deviceName);
                    object.put("mac", deviceHardwareAddress);

                    discoveredDevices.put(object);

                    // send updated discovered list
                    PluginResult result = new PluginResult(PluginResult.Status.OK, discoveredDevices);
                    result.setKeepCallback(true); // allows multiple callbacks
                    startDiscoveryCallbackContext.sendPluginResult(result);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
    };

    private static final String ERR_NO_BLUETOOTH = "No Bluetooth device available";
    private static final String ERR_DISABLED = "Bluetooth device is disabled";
    private static final String ERR_UNABLE_ENABLE = "Unable to enable Bluetooth";
    private static final String ERR_START_DISCOVERY = "Unable to start discovery";
    private static final String ERR_PAIRED_QUERY = "Unable to query paired devices";
    private static final String ERR_PERMISSION_DENIED = "Permission was denied";

    private static final int REQUEST_ENABLE_BT = 5000;
    private static final int REQUEST_DISCOVERABILITY = 5001;
    private static final int REQUEST_LOCATION_PERMISSION = 6000;

    private CordovaInterface cordova;
    private BluetoothAdapter bluetoothAdapter;
    private CallbackContext enableCallbackContext, startDiscoveryCallbackContext, discoverabilityCallbackContext;
    private JSONArray discoveredDevices; // for discovery

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);

        this.cordova = cordova;
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        // registers the broadcast receiver for bluetooth devices found
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        cordova.getActivity().registerReceiver(receiver, filter);
    }

    @Override
    public boolean execute(String action, CordovaArgs args, CallbackContext callbackContext) {
        //JSONArray args = new JSONArray(rawArgs);
        if (action.equals("isSupported")) {
            actionIsSupported(callbackContext);
            return true;
        } else if (action.equals("isEnabled")) {
            actionIsEnabled(callbackContext);
            return true;
        } else if (action.equals("enable")) {
            actionEnable(callbackContext);
            return true;
        } else if (action.equals("disable")) {
            actionDisable(callbackContext);
            return true;
        } else if (action.equals("queryPairedDevices")) {
            actionQueryPairedDevices(callbackContext);
            return true;
        } else if (action.equals("startDiscovery")) {
            actionStartDiscovery(callbackContext);
            return true;
        } else if (action.equals("enableDiscoverability")) {
            int duration = 300; // seconds
            try {
                try {
                    duration = Integer.parseInt(args.getString(0));
                } catch (NumberFormatException ex) {
                    ex.printStackTrace();
                }
                // duration cannot be negative or greater than an hour
                if (duration < 0 || duration > 60 * 60) {
                    callbackContext.error("Duration cannot be negative or greater than 1 hour");
                    return true;
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
            actionEnableDiscoverability(callbackContext, duration);
            return true;
        }
        return false;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (requestCode == REQUEST_ENABLE_BT) {
            if (enableCallbackContext == null) {
                return;
            }

            if (resultCode == Activity.RESULT_OK) {
                enableCallbackContext.success();
            } else {
                enableCallbackContext.error(ERR_UNABLE_ENABLE);
            }
        } else if (requestCode == REQUEST_DISCOVERABILITY) {
            if (discoverabilityCallbackContext == null) {
                return;
            }

            if (resultCode == Activity.RESULT_CANCELED) {
                discoverabilityCallbackContext.error("User cancelled operation");
            } else {
                discoverabilityCallbackContext.success();
            }
        }
    }

    @Override
    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) throws JSONException {
        super.onRequestPermissionResult(requestCode, permissions, grantResults);
        boolean denied = false;

        for (int result : grantResults) {
            if(result == PackageManager.PERMISSION_DENIED) {
                denied = true;
                break;
            }
        }
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (denied) {
                startDiscoveryCallbackContext.error(ERR_PERMISSION_DENIED);
            } else {
                actionStartDiscovery(startDiscoveryCallbackContext);
            }
        }
    }

    @Override
    public void onDestroy() {
        cordova.getActivity().unregisterReceiver(receiver);
        super.onDestroy();
    }

    private boolean isSupported() {
        if (bluetoothAdapter == null) {
            return false;
        } else {
            return true;
        }
    }

    private boolean isEnabled() {
        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
            return true;
        }
        return false;
    }

    /**
     * Checks if Bluetooth is supported and enabled
     * @param callbackContext
     * @return
     */
    private boolean checkSupportedEnabled(CallbackContext callbackContext) {
        if (isSupported()) {
            if (isEnabled()) {
                return true;
            } else {
                callbackContext.error(ERR_DISABLED);
            }
        } else {
            callbackContext.error(ERR_NO_BLUETOOTH);
        }
        return false;
    }

    /**
     *
     * ACTIONS METHODS
     *
     * **/

    private void actionIsSupported(CallbackContext callbackContext) {
        if (isSupported()) {
            callbackContext.success();
        } else {
            callbackContext.error(ERR_NO_BLUETOOTH);
        }
    }

    private void actionIsEnabled(CallbackContext callbackContext) {
        if (checkSupportedEnabled(callbackContext)) {
            callbackContext.success();
        }
    }

    private void actionEnable(CallbackContext callbackContext) {
        enableCallbackContext = null;
        if (isSupported()) {
            if (isEnabled()) {
                // already enabled
                callbackContext.success();
            } else {
                enableCallbackContext = callbackContext;

                cordova.setActivityResultCallback(this);
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                cordova.getActivity().startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        } else {
            callbackContext.error(ERR_NO_BLUETOOTH);
        }
    }

    private void actionDisable(final CallbackContext callbackContext) {
        if (isSupported()) {
            if (isEnabled()) {
                // Show a dialog asking the user if they want to disable BT
                new AlertDialog.Builder(cordova.getActivity())
                        .setTitle("Disable Bluetooth")
                        .setMessage("Do you want to disable Bluetooth?")
                        .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (bluetoothAdapter.disable()) {
                                    callbackContext.success();
                                }
                            }
                        })
                        .setNegativeButton("No", null)
                        .show();
            } else {
                // already disabled
                callbackContext.success();
            }
        } else {
            callbackContext.error(ERR_NO_BLUETOOTH);
        }
    }

    /**
     * Return a JSONArray of JSONObjects representing each paired object {name: String, mac: String}
     * @param callbackContext
     */
    private void actionQueryPairedDevices(CallbackContext callbackContext) {
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

        try {
            JSONArray array = new JSONArray();

            if (!pairedDevices.isEmpty()) {
                // There are paired devices. Get the name and address of each paired device.
                for (BluetoothDevice device : pairedDevices) {
                    JSONObject object = new JSONObject();

                    String deviceName = device.getName();
                    String deviceHardwareAddress = device.getAddress(); // MAC address

                    object.put("name", deviceName);
                    object.put("mac", deviceHardwareAddress);

                    array.put(object);
                }
            }

            callbackContext.success(array);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        callbackContext.error(ERR_PAIRED_QUERY);
    }

    /**
     * Passes a JSONArray of JSONObjects {name: String, mac: String} of discovered devices to the
     * success callback when updated
     * @param callbackContext
     */
    private void actionStartDiscovery(CallbackContext callbackContext) {
        startDiscoveryCallbackContext = null;

        if (checkSupportedEnabled(callbackContext)) {
            startDiscoveryCallbackContext = callbackContext;
            // check runtime permission for Android 6.0+
            if (cordova.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                if (bluetoothAdapter.startDiscovery()) {
                    discoveredDevices = new JSONArray();

                    PluginResult result = new PluginResult(PluginResult.Status.OK, discoveredDevices);
                    result.setKeepCallback(true); // allows multiple callbacks
                    callbackContext.sendPluginResult(result);
                } else {
                    callbackContext.error(ERR_START_DISCOVERY);
                }
            } else {
                cordova.requestPermission(this, REQUEST_LOCATION_PERMISSION, Manifest.permission.ACCESS_COARSE_LOCATION);
            }
        }
    }

    /**
     * Enables Bluetooth if necessary and enabled discoverability for duration passed in seconds
     * @param callbackContext
     * @param duration - default 300 seconds
     */
    private void actionEnableDiscoverability(CallbackContext callbackContext, int duration) {
        discoverabilityCallbackContext = null;

        if (isSupported()) {
            discoverabilityCallbackContext = callbackContext;

            cordova.setActivityResultCallback(this);
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, duration);
            cordova.getActivity().startActivityForResult(discoverableIntent, REQUEST_DISCOVERABILITY);
        } else {
            callbackContext.error(ERR_NO_BLUETOOTH);
        }
    }
}