package mobilecomputing.com.project.mobilecomputing;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;

import java.io.IOException;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import static android.content.ContentValues.TAG;

/**
 * Created by harmeet-US on 2/21/2018.
 */

public class CommConnector {
    private static final String APP_NAME = "BluetoothChatApp";
    private static final UUID MY_UUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66");
    private final BluetoothAdapter bluetoothAdapter;
    private final Handler handler;
    private final Context context;
    private AcceptThread acceptThread;
    private ConnectThread connectThread;
    private ReadWriteThread readWriteThred;
    private int state;

    static final int STATE_NONE = 0;
    static final int STATE_LISTEN = 1;
    static final int STATE_CONNECTING = 2;
    static final int STATE_CONNECTED = 3;

    public CommConnector(Context context, Handler handler) {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        state = STATE_NONE;

        this.handler = handler;
        this.context = context;
    }

    public synchronized int getState() {
        return state;
    }
    private synchronized void setState(int state) {
        this.state = state;

        //handler.obtainMessage(MainActivity.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }

    public synchronized void start() {
        // Cancel any thread
//        Toast.makeText(context, "Starting commConnector", Toast.LENGTH_SHORT).show();
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }

        // Cancel any running thresd
        if (readWriteThred != null) {
            readWriteThred.cancel();
            readWriteThred = null;
        }

        setState(STATE_LISTEN);

        if (acceptThread == null) {
            acceptThread = new AcceptThread();
            acceptThread.start();
            Log.i(TAG,"=-=--=-=-=-=-= Started the thread to listen the socket =-=-=-=-=-=-=-=");
            // Toast.makeText(context,acceptThread.toString(), Toast.LENGTH_SHORT).show();
        }

    }
    public synchronized void stop() {
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }

        if (readWriteThred != null) {
            readWriteThred.cancel();
            readWriteThred = null;
        }

        if (acceptThread != null) {
            acceptThread.cancel();
            acceptThread = null;
        }
        setState(STATE_NONE);
    }
    public synchronized void connect(BluetoothDevice device) {
        // Cancel any thread
        if (state == STATE_CONNECTING) {
            if (connectThread != null) {
                connectThread.cancel();
                connectThread = null;
            }
        }

        // Cancel running thread
        if (readWriteThred != null) {
            readWriteThred.cancel();
            readWriteThred = null;
        }

        // Start the thread to connect with the given device
        connectThread = new ConnectThread(device);
        Log.i(TAG,"-=-=-=-=-=-=-=- Connect thread starting with bluetooth device =-=-=-=-=-=-=-=-=-=-=-");
        connectThread.start();
        setState(STATE_CONNECTING);
    }
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {
        // Cancel the thread
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }

        // Cancel running thread
        if (readWriteThred != null) {
            readWriteThred.cancel();
            readWriteThred = null;
        }

        if (acceptThread != null) {
            acceptThread.cancel();
            acceptThread = null;
        }

        // Start the thread to manage the connection and perform transmissions
        readWriteThred = new ReadWriteThread(socket);
        readWriteThred.start();
        Log.i(TAG,"=-=--=-=-=-=-= READWRITE THREAD STARTED. It means, connection is started =-=-=-=-=-=-=-=");
        // Send the name of the connected device back to the UI Activity
        Message msg = handler.obtainMessage(MainActivity.MESSAGE_DEVICE_OBJECT);
        Bundle bundle = new Bundle();
        bundle.putParcelable(MainActivity.DEVICE_OBJECT, device);
        msg.setData(bundle);
        handler.sendMessage(msg);
        setState(STATE_CONNECTED);
    }
    private void connectionFailed( IOException e) {
/*        Message msg = handler.obtainMessage(MainActivity.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString("toast", "Unable to connect device");
        msg.setData(bundle);
        handler.sendMessage(msg);
*/
//        Toast.makeText(context, "unable to connect device" + e.toString(),Toast.LENGTH_SHORT).show();
        // Start the service over to restart listening mode
        Log.i(TAG, "INSIDE CONNECTION FAILED  ==============++++++++++++++------ " + e );
        CommConnector.this.start();
    }
    private void connectionLost() {
        Message msg = handler.obtainMessage(MainActivity.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString("toast", "Device connection was lost");
        msg.setData(bundle);
        handler.sendMessage(msg);

        // Start the service over to restart listening mode
        CommConnector.this.start();
    }

    public void write(byte[] out) {
        ReadWriteThread r;
        synchronized (this) {
            if (state != STATE_CONNECTED)
                return;
            r = readWriteThred;
        }
        Log.i(TAG,"=-=--=-=-=-=-= Going to write the message =-=-=-=-=-=-=-=");
        r.write(out);
    }

    private class AcceptThread extends Thread {
        private final BluetoothServerSocket serverSocket;

        public AcceptThread() {
            BluetoothServerSocket tmp = null;
            try {
                tmp = bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(APP_NAME, MY_UUID);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            serverSocket = tmp;
//            Toast.makeText(context, "accept thread constructor "+serverSocket, Toast.LENGTH_SHORT).show();
        }

        public void run() {
//            setName("AcceptThread");
            BluetoothSocket socket;
            boolean tt = true;
            while (state != STATE_CONNECTED) {
                try {
                    Log.i(TAG, "============= BT Name is ===============" + state + "\n I am listening" );
                    socket = serverSocket.accept();
                    Log.i(TAG, "============= BT Name is ===========================" + state );
                } catch (IOException e) {

                    break;
                }
                // If a connection was accepted
                if (socket != null) {
                    synchronized (CommConnector.this) {
                        switch (state) {
                            case STATE_LISTEN:
                            case STATE_CONNECTING:
                                // start the connected thread.
                                connected(socket, socket.getRemoteDevice());
                                break;
                            case STATE_NONE:
                            case STATE_CONNECTED:
                                // Either not ready or already connected. Terminate
                                // new socket.
                                try {
                                    socket.close();
                                } catch (IOException e) {
                                }
                                break;
                        }
                    }
                }
                //Toast.makeText(context, socket.toString(), Toast.LENGTH_LONG).show();
            }
        }

        public void cancel() {
            try {
                serverSocket.close();
            } catch (IOException e) {
            }
        }
    }
    /* runs while attempting to make an outgoing connection*/
    private class ConnectThread extends Thread {
        private final BluetoothSocket socket;
        private final BluetoothDevice device;

        public ConnectThread(BluetoothDevice device) {
            this.device = device;
            BluetoothSocket tmp = null;
            try {
                tmp = device.createInsecureRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                e.printStackTrace();
            }
            socket = tmp;
        }

        public void run() {
//            setName("ConnectThread");
            // Always cancel discovery because it will slow down a connection
            Log.i(TAG, "Cancel the bluetooth discovery ==============++++++++++++++" );
            bluetoothAdapter.cancelDiscovery();
            Log.i(TAG, "Canceled the bluetooth discovery ==============++++++++++++++------" );
            // Make a connection to the BluetoothSocket
            try {
                Log.i(TAG, "Connecting to the socket ==============++++++++++++++------" );
                socket.connect();
                Log.i(TAG, "Connected to the socket ==============++++++++++++++------" );
            } catch (IOException e) {
                try {
                    socket.close();
                } catch (IOException e2) {
                    Log.i(TAG, "EXCEPTION   ============++++++++++++++------" + e2 );
                    //Toast.makeText(context, e2.toString(), Toast.LENGTH_LONG).show();
                }
                connectionFailed( e );
                return;
            }

            // Reset the ConnectThread because we're done
            synchronized (CommConnector.this) {
                connectThread = null;
            }

            // Start the connected thread
            Log.i(TAG,"=-=-=-=-=--  SOCKET CONNECTED BETWEEN CLIENT AND SERVER =-=-=-=-=--");
            connected(socket, device);
        }

        public void cancel() {
            try {
                socket.close();
            } catch (IOException e) {
            }
        }
    }
    private class ReadWriteThread extends Thread {
        private final BluetoothSocket bluetoothSocket;
        private final InputStream inputStream;
        private final OutputStream outputStream;

        public ReadWriteThread(BluetoothSocket socket) {
            this.bluetoothSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
            }

            inputStream = tmpIn;
            outputStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;
            // Keep listening to the InputStream
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = inputStream.read(buffer);
                    // Send the obtained bytes to the UI Activity
                    handler.obtainMessage(MainActivity.MESSAGE_READ, bytes, -1, buffer).sendToTarget();
                    Log.i(TAG,"======= Inside the readwrite thread and waiting for messages.==========");
                } catch (IOException e) {
                    //connectionLost();
                    // Start the service over to restart listening mode
                    CommConnector.this.start();
                    Log.i(TAG, "Exception in line 235 " + e );
                    break;
                }
            }
        }

        // write to OutputStream
        public void write(byte[] buffer) {
            try {
                outputStream.write(buffer);
                handler.obtainMessage(MainActivity.MESSAGE_WRITE, -1, -1,buffer).sendToTarget();
                Log.i(TAG,"=-=-=-=-=-=-=-=-=- Writing the message here =-=-=-=-=-=-=-=-= ");
            } catch (IOException e) {
            }
        }

        public void cancel() {
            try {
                bluetoothSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
