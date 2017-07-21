# Cordova Bluetooth Plugin

Allows the device to enable Bluetooth and discover other devices

# Supported Platforms

Android

# Installation

	cordova plugin add <path-to-plugin-dir>

# Usage

Tests whether the device has Bluetooth support.

errorCb(errorMessage)

	bluetooth.isSupported(successCb, errorCb)

Tests whether Bluetooth is enabled.

errorCb(errorMessage)

	bluetooth.isEnabled(successCb, errorCb)

Enables Bluetooth.

errorCb(errorMessage)

	bluetooth.enable(successCb, errorCb)

Disables Bluetooth.

errorCb(errorMessage)

	bluetooth.disable(successCb, errorCb)

Retrieves a JSONArray of JSONObjects {name: deviceName, mac: macAddress} of paired devices.

errorCb(errorMessage)

	bluetooth.queryPairedDevices(successCb, errorCb)

Begins discovery of available Bluetooth devices to pair. With each discovered device, calls successCb
with a JSONArray of JSONObjects {name: deviceName, mac: macAddress}.

errorCb(errorMessage)

	bluetooth.startDiscovery(successCb, errorCb)

Allows the device to be discovered by other Bluetooth devices.

errorCb(errorMessage)

duration - length of discoverability in seconds

	bluetooth.enableDiscoverability(successCb, errorCb, duration)
