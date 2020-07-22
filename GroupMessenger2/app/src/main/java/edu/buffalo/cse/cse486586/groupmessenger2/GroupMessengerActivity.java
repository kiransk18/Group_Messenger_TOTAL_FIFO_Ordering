package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import java.io.BufferedReader;
import android.content.ContentValues;
import android.net.Uri;
import java.io.IOException;
import android.os.AsyncTask;
import java.io.OutputStream;
import android.os.Bundle;
import java.net.InetAddress;
import android.text.method.ScrollingMovementMethod;
import java.net.ServerSocket;
import android.util.Log;
import java.net.Socket;
import android.view.View;
import java.net.SocketTimeoutException;
import android.widget.EditText;
import android.widget.TextView;
import android.telephony.TelephonyManager;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.io.InputStreamReader;
import java.util.Iterator;
import java.util.PriorityQueue;
import android.content.Context;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.Comparator;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 *
 * @authors stevko and Saikiran
 *
 */
public class GroupMessengerActivity extends Activity {
    static final String TAG = GroupMessengerActivity.class.getSimpleName();
    static final int SERVER_PORT = 10000;
    static Uri CONTENT_URI;
    static int seqNo = 0;
    static int counter = 0;
    static LinkedHashMap<String, Integer> portToAvdMapping = new LinkedHashMap<String, Integer>();
    static HashMap<String, Integer> msgTypeHandler = new HashMap<String, Integer>();
    private static String runningPort;
    String contentProviderURI = "edu.buffalo.cse.cse486586.groupmessenger2.provider";
    PriorityQueue<HoldBackData> holdBackQueue;
    int deliveryNo = 0;
    int senderId;
    String failed_port;
    boolean flag = true;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);

        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());

        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));


        loadPortToProcessMapping();         //Load Port to Process Id Mapping
        loadMsgTypeIdentifier();            // Loads message Type Indentifier which differentiates messages
        runningPort = fetchRunningPort();   // Fetch the current Running port of the AVD
        definePriorityQueue();              // Elements with lowest agreed priority are given high priority for delivery.
        senderId = portToAvdMapping.get(runningPort); // Process Id of the Running AVD.
        failed_port = "empty";


        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        }

        catch (IOException e) {
            Log.e(TAG, e.toString());
            return;
        }

        //Referred PA1 code
        final EditText enteredText = (EditText) findViewById(R.id.editText1);
        findViewById((R.id.button4)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String msg = enteredText.getText().toString() + "\n";
                enteredText.setText("");
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg);
            }
        });
    }

    public void loadPortToProcessMapping() {
        portToAvdMapping.put("11108", 0);
        portToAvdMapping.put("11112", 1);
        portToAvdMapping.put("11116", 2);
        portToAvdMapping.put("11120", 3);
        portToAvdMapping.put("11124", 4);
    }

    public void loadMsgTypeIdentifier() {
        msgTypeHandler.put("init", 0);
        msgTypeHandler.put("agreedMsg", 1);
        msgTypeHandler.put("Failure", 2);
    }

    /* In the below method, we connect to all the processes except failed node
     and will clear the messages sent by the failed node */

    public synchronized void handleFailure(String failedPort)
    {
        for(String ports : portToAvdMapping.keySet()) {
            if(ports.equals(failedPort))
                continue;
            try {

                Socket expSoc = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                        Integer.parseInt(ports));

                        /* References
                         https://developer.android.com/reference/java/net/Socket
                         https://developer.android.com/reference/java/io/BufferedReader
                         https://developer.android.com/reference/java/io/PrintWriter */

                OutputStream opStream = expSoc.getOutputStream();
                PrintWriter pw = new PrintWriter(opStream, true);
                StringBuilder sb = new StringBuilder();
                sb.append("Failure@");
                sb.append(failedPort);
                String msgFailure = sb.toString();
                pw.println(msgFailure);
                BufferedReader br = new BufferedReader(new InputStreamReader(expSoc.getInputStream()));
                String ack = br.readLine();
                if (ack != null && ack.equals("failureACK")) {
                    expSoc.close();
                }

            } catch (UnknownHostException exc) {
                Log.e(TAG, "UnknownHost Exception occured at client side");
            } catch (IOException exc) {
                Log.e(TAG, "Ssocket IOException occured at client side");
            }
        }
    }

    // Referred PA1 code
    public String fetchRunningPort() {
        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String lineNumber = tel.getLine1Number();
        String portId = lineNumber.substring(tel.getLine1Number().length() - 4);
        return "" + Integer.valueOf(portId) * 2;
    }

    public void definePriorityQueue()
    {
        holdBackQueue =  new PriorityQueue<HoldBackData>(200, new Comparator<HoldBackData>() {
            @Override
            public int compare(HoldBackData m1, HoldBackData m2) {
                return (m1.seqNo > m2.seqNo ? 1 : - 1);
            }
        });
    }

    private class HoldBackData
    {
        String msgId;
        float seqNo;
        String msg;
        boolean deliveryStatus;
        String senderPort;
        HoldBackData(String msgId, float seqNo, String msg, boolean deliveryStatus, String senderPort)
        {
            this.msg = msg;
            this.msgId = msgId;
            this.seqNo = seqNo;
            this.deliveryStatus = deliveryStatus;
            this.senderPort = senderPort;
        }

        void setSeqNo(float seqNo)
        {
            this.seqNo = seqNo;
        }


        void setDeliveryStatus(boolean deliveryStatus)
        {
            this.deliveryStatus = deliveryStatus;
        }

    }

    // Referred PA1 Code
    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];

            BufferedReader br = null;

            /*
             Referred https://developer.android.com/reference/java/net/ServerSocket
             Referred https://developer.android.com/reference/java/io/BufferedReader
             Referred https://developer.android.com/reference/java/io/PrintWriter
             https://developer.android.com/guide/topics/providers/content-provider-creating
             */
            ContentValues newValues = new ContentValues();
            Uri.Builder uriBuilder = new Uri.Builder();
            uriBuilder.scheme("content");
            uriBuilder.authority(contentProviderURI);
            CONTENT_URI = uriBuilder.build();

            try {

                while(true) {
                    Socket soc = serverSocket.accept();
                    //soc.setSoTimeout(5000);
                    Log.i("accept", "Server accepted connection");
                    br = new BufferedReader(new InputStreamReader(soc.getInputStream()));
                    String msgReceived = br.readLine();
//                    if(msgReceived == null)
//                        throw new SocketTimeoutException();
                    Log.i("msgRecv", msgReceived);
                    String[] msgInfo = msgReceived.split("@");
                    int msgType = msgTypeHandler.get(msgInfo[0]);

                    switch (msgType) {
                        case 0: {
                            synchronized (this) {
                                // Case 0: It's a initial request and the process sends it's proposed Number.
                                seqNo += 1;
                                String msgId = msgInfo[2];
                                String message = msgInfo[3];
                                float proposedNo = Float.parseFloat(seqNo + "." + portToAvdMapping.get(runningPort));
                                Log.i("proposedNo", message + " " + proposedNo);
                                holdBackQueue.offer(new HoldBackData(msgId, proposedNo, message, false, msgInfo[1]));
                                // Referred https://developer.android.com/reference/java/io/PrintWriter
                                PrintWriter printWriter = new PrintWriter(soc.getOutputStream(), true);
                                printWriter.println(proposedNo);
                                break;
                            }
                        }

                        case 1: {
                            synchronized (this) {

                                //case 1: It's a second request for the message and the process updates agreed Number

                                Log.i("agreedMsgs", msgInfo[2]);

                                float agreedNum = Float.parseFloat(msgInfo[2]);
                                String fport = msgInfo[3];
                                seqNo = Math.max(seqNo, (int) agreedNum);

                                Iterator itr = holdBackQueue.iterator();
                                while (itr.hasNext()) {
                                    HoldBackData temp = (HoldBackData) itr.next();
                                    if (temp.msgId.equals(msgInfo[1])) {

                                    /* If msgId is matched, Remove the record from the queue, update the sequence number
                                     for the record in the holdback queue. */

                                        holdBackQueue.remove(temp);
                                        temp.setSeqNo(agreedNum);
                                        Log.i("agreedNum", temp.msg + " " + agreedNum);
                                        temp.setDeliveryStatus(true);
                                        holdBackQueue.offer(temp);
                                        break;
                                    }
                                }


                                while (true) {

                                    // If the delivery status of the peek record is true, send it to UI for display.
                                    if (!holdBackQueue.isEmpty() &&
                                            (holdBackQueue.peek().deliveryStatus || holdBackQueue.peek().
                                                    senderPort.equals(fport) || holdBackQueue.peek().senderPort.equals(failed_port))) {
                                        if (!fport.equals("empty"))
                                            failed_port = fport;
                                        HoldBackData msgObj = holdBackQueue.poll();
                                        newValues.put("key", String.valueOf(deliveryNo));
                                        newValues.put("value", msgObj.msg);
                                        getContentResolver().insert(CONTENT_URI, newValues);
                                        String temp;
                                        if (!holdBackQueue.isEmpty())
                                            temp = holdBackQueue.peek().msg;
                                        else
                                            temp = "empty";
                                        publishProgress(msgObj.msg, "peek" + temp);
                                        deliveryNo++;
                                    } else break;
                                }


                                OutputStream opStream = soc.getOutputStream();

                                // Referred https://developer.android.com/reference/java/io/PrintWriter
                                PrintWriter printWriter = new PrintWriter(opStream, true);
                                printWriter.println("agreedACK");
                                break;
                            }
                        }
                        case 2: {
                            synchronized (this) {

                            /* This case is executed if the server receives a failure message. This logic handles the removal
                             of  entries which are sent by the failed node. */
//                                synchronized (this) {
                                if (flag) {
                                    Iterator itr = holdBackQueue.iterator();
                                    String failure_id = msgInfo[1];
                                    // portToAvdMapping.remove(failure_id);
                                    while (itr.hasNext()) {
                                        HoldBackData temp = (HoldBackData) itr.next();
                                        if (temp.senderPort.equals(failure_id)) {

                                            holdBackQueue.remove(temp);
                                        }
                                    }
                                    failed_port = failure_id;
                                    OutputStream opStream = soc.getOutputStream();
                                    PrintWriter printWriter = new PrintWriter(opStream, true);
                                    printWriter.println("failureACK");
                                    flag = false;
                                    break;
                                    //  }
                                }
                            }
                        }
                    }
                    soc.close();
                }

            }

            catch (Exception ex) {
                Log.e("SERVER-NA","Server encountered an exception!! Please check logs.");
            }

            return null;
        }

        //Referred PA1 code
        protected void onProgressUpdate(String...strings) {
            TextView remoteTextView = (TextView) findViewById(R.id.textView1);
            remoteTextView.append(strings[0] + "\t\n");
            remoteTextView.append(strings[1].trim() + "\t\n");
            return;
        }
    }

    // Referred PA1 Code
    private class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {
            counter = counter + 1;
            String msg = msgs[0].trim();
            String msgId = counter + "" + runningPort;
            // Socket clientSoc;

            /* The below Array sorts the proposed numbers with highest proposed number
             getting high priority and then the max value is sent to all process as agreed Number */

            ArrayList<Float> MsgproposedPriorities = new ArrayList<Float>();
            for(String port : portToAvdMapping.keySet())

            {
                // This initial condition prevents the sender in making request to failed Node.

                if(failed_port.equals(port))
                    continue;
                try {

                    Socket clientSoc = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(port));
                    clientSoc.setSoTimeout(1500);

                    /* References
                         https://developer.android.com/reference/java/net/Socket
                         https://developer.android.com/reference/java/io/BufferedReader
                         https://developer.android.com/reference/java/io/PrintWriter */

                    OutputStream opStream = clientSoc.getOutputStream();
                    PrintWriter pw = new PrintWriter(opStream,true);
                    StringBuilder msgToBeSent = new StringBuilder("init@" + runningPort + "@"  + msgId + "@" + msg);
                    pw.println( msgToBeSent.toString());
                    Log.i("clientmsg","Client sent message to the server");
                    BufferedReader br = new BufferedReader(new InputStreamReader(clientSoc.getInputStream()));
                    String msgReceived = br.readLine();
                    if(msgReceived == null) {
                        throw new SocketTimeoutException();
                        // Log.i("disconnect","Closing client socket");
                    }
                    Log.i("clienRec", msgReceived + " " + msg);
                    MsgproposedPriorities.add(Float.parseFloat(msgReceived));
                    clientSoc.close();

                }

                // Store failed port inorder to clear messages

                catch (SocketTimeoutException ex)
                {
                    failed_port = port;
                }
                catch (UnknownHostException ex) {
                    failed_port = port;
                    Log.e(TAG, "Could not find the host!!");
                } catch (IOException ex) {
                    failed_port = port;
                    Log.e(TAG, "socket IO Exception occured when connecting to Server!!");
                }

            }

            // If the below condition is true, clear the messages sent by  the failed node in all the processes.

            if(flag && !failed_port.equals("empty")) {
                handleFailure(failed_port);
                flag = false;
            }

            // Fetch the highest proposed Number
            float agreedNoTobeSent = Collections.max(MsgproposedPriorities);

            for(String port : portToAvdMapping.keySet())
            {
                if(failed_port.equals(port))
                    continue;
                try {

                    Socket clientSoc = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(port));
                    clientSoc.setSoTimeout(1500);

                        /* References
                         https://developer.android.com/reference/java/net/Socket
                         https://developer.android.com/reference/java/io/BufferedReader
                         https://developer.android.com/reference/java/io/PrintWriter */

                    OutputStream op = clientSoc.getOutputStream();
                    PrintWriter pw = new PrintWriter(op,true);
                    StringBuilder agreedMessage = new StringBuilder("agreedMsg@"  + msgId + "@" + agreedNoTobeSent + "@" + failed_port);
                    pw.println(agreedMessage.toString());
                    BufferedReader br = new BufferedReader(new InputStreamReader(clientSoc.getInputStream()));
                    String ack = br.readLine();
                    if(ack != null && ack.equals("agreedACK"))
                        clientSoc.close();
                    else
                        throw new SocketTimeoutException();

                }

                // Store failed port inorder to clear messages

                catch (SocketTimeoutException ex)
                {
                    failed_port = port;
                }
                catch (UnknownHostException ex) {
                    failed_port = port;
                    Log.e(TAG, "Could not find the host!!");
                } catch (IOException ex) {
                    failed_port = port;
                    Log.e(TAG, "socket IO Exception occured when connecting to Server!!");
                }
            }

            // If the below condition is true, clear the messages sent by  the failed node in all the processes.

            if(flag && !failed_port.equals("empty")) {
                handleFailure(failed_port);
                flag = false;
            }
            return null;
        }
    }
}