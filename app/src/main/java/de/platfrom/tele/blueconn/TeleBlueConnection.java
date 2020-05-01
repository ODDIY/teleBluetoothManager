package de.platfrom.tele.blueconn;

/*
*
Was passiert wann ...
*
//start
attachActivity
24:0A:C4:31:6A:E2
startActivity
reconnect
onServiceConnected
connect
onSerialConnect

//lock
 stopActivity

//reopen
startActivity
reconnect

//send
send

//echo
receive
receive

//goback / close
stopActivity
destroyActivity
Disconnect
detachActivity
*
* */

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import com.google.protobuf.InvalidProtocolBufferException;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class TeleBlueConnection implements SerialListener, ServiceConnection {

    private enum TelePackType { Control,Collision,Distance,None }

    private String deviceAddress;
    private SerialSocket socket;
    private SerialService service;
    private boolean initialStart = true;
    private boolean connected = false;
    private Context context;
    private byte[] inputPackageBuffer = new byte[0];

    public void attachTeleBlueListerner(TeleBlueListerner teleBlueListerner) {
        this.teleBlueListerner = teleBlueListerner;
    }

    private TeleBlueListerner teleBlueListerner;



    //UNKNOWN ANDROID SERVICE FUNCTION...
    // this starts the bluetooth service ?
    public void startActivity(Activity activity, Context con) {
        Log.d("TBC", "startActivity");
        context = con;
        if(service != null)
            service.attach(this);
        else
            activity.startService(new Intent(activity, SerialService.class)); // prevents service destroy on unbind from recreated activity caused by orientation change
    }
    public void stopActivity(Activity activity) {
        Log.d("TBC", "stopActivity");

        if(service != null && !activity.isChangingConfigurations())
            service.detach();
    }
    // binds something ?
    public void attachActivity(Activity activity)  {
        Log.d("TBC", "attachActivity");
        activity.bindService(new Intent(activity, SerialService.class), this, Context.BIND_AUTO_CREATE);
    }
    public void detachActivity(Activity activity)  {
        Log.d("TBC", "detachActivity");
        try { activity.unbindService(this); } catch(Exception ignored) {}
    }
    public void destroyActivity(Activity activity){
        Log.d("TBC", "destroyActivity");
        if (connected)
             disconnect();
        activity.stopService(new Intent(activity, SerialService.class));
    }

    /**
     * set the device to use
     * @param deviceAdr bluetooth mac address of the device to connect to
     */
    public void setDevice(String deviceAdr) {
        deviceAddress = deviceAdr;
        Log.d("TBC", deviceAdr);
    }

    private void disconnect() {
        Log.d("TBC", "Disconnect");
        connected = false;
        service.detach();
        service.disconnect();
        socket.disconnect();
        socket = null;
    }

    /**
     * calculate the checksum of a data package
     * @param data cobs decoded package of data [type, [.. data ..], checksum]
     * @return the checksum of the package
     */
    private static byte checkSum(byte[] data) {
        byte sum = 0;
        for(int i = 0; i < data.length- 1; i++) {
            sum += data[i];
        }
        return sum;
    }

    /**
     * checks the integrity of the package
     * @param data cobs decoded package of data [type, [.. data ..], checksum]
     * @return true if the package is ok
     */
    private static boolean checkSumCheck(byte[] data) {
        byte sum = checkSum(data);
        return sum == data[data.length- 1];
    }

    /**
     * extract the type information from the received package to determine with proto Message to use
     * @param data cobs decoded package of data [type, [.. data ..], checksum]
     * @return type of the message
     */
    private static TelePackType extractPackageType(byte[] data) {
        switch (data[0]){
            case 1:
                return TelePackType.Control;
            case 2:
                return TelePackType.Collision;
            case 3:
                return TelePackType.Distance;
            default:
                return TelePackType.None;
        }
    }

    /**
     * removes checksum and type data from the package to make a clean proto data package
     * @param data cobs decoded package of data with type and check information
     * @return clean proto data package for parsing to Proto Object.
     */
    private static byte[] getCleanProtoData(byte[] data) {
        return Arrays.copyOfRange(data, 1 , data.length-1);
    }

    /**
     * decode one ore more packages from the input buffer
     * TODO: what happens with wrong data in the package
     * @return list of decoded byte packages
     */
    private List<byte[]> decodeCobsBuffer() {
        List<byte[]>result = new LinkedList<>();

        while(inputPackageBuffer.length > 0){
            byte[] cobsBuffer = new byte[Cobs.decodeDstBufMaxLen(inputPackageBuffer.length)];
            Cobs.DecodeResult r = Cobs.decode(cobsBuffer,cobsBuffer.length, inputPackageBuffer, inputPackageBuffer.length );

            Log.d("TBC", "outlen: " + r.outLen);
            Log.d("TBC", r.status.name());

            switch (r.status){
                case OK:
                    result.add( Arrays.copyOfRange(cobsBuffer,0, r.outLen));
                    break;
                case NULL_POINTER:
                case OUT_BUFFER_OVERFLOW:
                    throw new NullPointerException();
                case ZERO_BYTE_IN_INPUT:
                    break;
                case INPUT_TOO_SHORT:
                    return result;
            }

            if(inputPackageBuffer.length - r.inLen > 0 ) {
                //remove read data
                inputPackageBuffer = Arrays.copyOfRange(inputPackageBuffer,r.inLen, inputPackageBuffer.length);
            } else {
                break;
            }
        }
        return  result;
    }

    private void receive(@org.jetbrains.annotations.NotNull byte[] data) {

        Log.d("TBC", "receive");
        // -------------------------- collect input package ---------------------------------------

        StringBuilder st = new StringBuilder();
        for (byte b : data) {
            st.append(String.format("%02X ", b));
        }
        Log.d("TBC", st.toString());

        //collect data in input buffer if a package is split
        //add data to Buffer
        byte[] result = Arrays.copyOf(inputPackageBuffer, inputPackageBuffer.length + data.length);
        System.arraycopy(data, 0, result, inputPackageBuffer.length, data.length);
        inputPackageBuffer = result;

        //decode Buffer
        List<byte[]> packs = decodeCobsBuffer();


        for (byte[] pack : packs ) {
            // -------------------------- check the package -----------------------
            if(!checkSumCheck(pack)) {
                Log.d("TBC", "Checksum err");
                continue; // next package or exit
            }

            //----------------------- read package type---------------------------
            TelePackType packtype = extractPackageType(pack);
            byte[] proto = getCleanProtoData(pack);

            try {
                // unpack protobuff
                switch (packtype) {
                    case Control:
                        //TeleCommunication.ControlMessage cont = TeleCommunication.ControlMessage.parseFrom(proto);
                        //if(teleBlueListerner != null) { teleBlueListerner.onControlMessage(cont);}
                        break;
                    case Collision:
                        TeleCommunication.CollisionMessage coll = TeleCommunication.CollisionMessage.parseFrom(proto);
                        if (teleBlueListerner != null) {
                            teleBlueListerner.onCollisionMessage(coll);
                        }
                        break;
                    case Distance:
                        TeleCommunication.DistancesMessage dist = TeleCommunication.DistancesMessage.parseFrom(proto);
                        if (teleBlueListerner != null) {
                            teleBlueListerner.onDistanceMessage(dist);
                        }
                        break;
                    case None:
                        // unknown type wrong package
                        break;
                }
            } catch (InvalidProtocolBufferException e) {
                onSerialConnectError(e);
            }
        }
    }

    public void sendControlMessage(TeleCommunication.ControlMessage msg) {
        msg.toByteArray();
        sendPackage(msg.toByteArray());
    }

    //create send function for the specific object
    public void send(String str) {
        Log.d("TBC", "send" + str);
        //sends a test package

        TeleCommunication.ControlMessage.Builder builder  = TeleCommunication.ControlMessage.newBuilder();
        builder.setSpeedX(1021);
        builder.setSpeedY(43);
        builder.setPan(247);
        builder.setSpeedRot(360);
        builder.setTilt(2044);
        TeleCommunication.ControlMessage msg = builder.build();

        msg.toByteArray();
        sendPackage(msg.toByteArray());

    }

    private void connect() {
        Log.d("TBC", "connect");

        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
            String deviceName = device.getName() != null ? device.getName() : device.getAddress();
            //status("connecting...");
            socket = new SerialSocket();
            service.connect(this, "Connected to " + deviceName);
            service.attach(this);
            socket.connect(context, service, device);
        } catch (Exception e) {
            onSerialConnectError(e);
        }
    }

    private void sendPackage(byte[] pack) {
        if(!connected) {
            //error
            return;
        }

        byte[] bytes = new byte[pack.length+2];

        bytes[0] = 0x01; //fist byte msg type
        System.arraycopy(pack,0, bytes, 1, pack.length); // msg bytes

        //calculate checksum and insert checksum
        bytes[bytes.length-1] = checkSum(bytes);

        //cobs encode the package
        byte[] encoded = new byte[Cobs.encodeDstBufMaxLen(bytes.length)];
        Cobs.EncodeResult r = Cobs.encode(encoded,encoded.length, bytes, bytes.length);

        if(r.status == Cobs.EncodeStatus.OK) {
            bytes = Arrays.copyOf(encoded,r.outLen+1);
            bytes[bytes.length-1] = 0;

            try {
                socket.write(bytes);
            } catch (IOException e) {
                onSerialConnectError(e);
            }

        }
    }

    public void reconnect() {
        Log.d("TBC", "reconnect");
        if(initialStart && service !=null) {
            initialStart = false;
            this.connect();
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        Log.d("TBC", "onServiceConnected");

        service = ((SerialService.SerialBinder) binder).getService();
        if(initialStart) {
            initialStart = false;
            this.connect();
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        Log.d("TBC", "onServiceDisconnected");
        service = null;
    }




    // ----------------------------------------------------------------------

    @Override
    public void onSerialConnect() {
        Log.d("TBC", "onSerialConnect");

        connected = true;
    }

    @Override
    public void onSerialConnectError(Exception e) {
        Log.d("TBC","connection failed: " + e.getMessage());
        disconnect();
    }

    @Override
    public void onSerialRead(byte[] data) {
        receive(data);
    }

    @Override
    public void onSerialIoError(Exception e) {
        Log.d("TBC","connection lost: " + e.getMessage());
        disconnect();
    }
}
