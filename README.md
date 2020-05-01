
# Protobuffer conntion to Bluetooth device


## Basic
This Android app provides a line-oriented terminal / console for classic Bluetooth (2.x) devices implementing the Bluetooth Serial Port Profile (SPP)


For an overview on Android Bluetooth communication see 
[Android Bluetooth Overview](https://developer.android.com/guide/topics/connectivity/bluetooth).

This App implements RFCOMM connection to the well-known SPP UUID 00001101-0000-1000-8000-00805F9B34FB

## Protocol
ontop of this Bluetooth Serial Port Profile Protocol Buffer Messages are exchages over blutooth using Cobs encoding for the packages, one byte for specifing the type of message and one byte for an very simple checksum.
