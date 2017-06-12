package com.neurosky.mindgame;

import android.bluetooth.BluetoothAdapter;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;

import com.neurosky.AlgoSdk.NskAlgoDataType;
import com.neurosky.AlgoSdk.NskAlgoSdk;
import com.neurosky.AlgoSdk.NskAlgoSignalQuality;
import com.neurosky.AlgoSdk.NskAlgoState;
import com.neurosky.AlgoSdk.NskAlgoType;
import com.neurosky.connection.ConnectionStates;
import com.neurosky.connection.DataType.MindDataType;
import com.neurosky.connection.EEGPower;
import com.neurosky.connection.TgStreamHandler;
import com.neurosky.connection.TgStreamReader;
import com.unity3d.player.UnityPlayer;
import com.unity3d.player.UnityPlayerActivity;

public class UnityThinkGear extends UnityPlayerActivity {

    private static final String TAG = UnityThinkGear.class.getSimpleName();

    public static final String STATE_IDLE = "idle";
    public static final String STATE_CONNECTING = "connecting";
    public static final String STATE_CONNECTED = "connected";
    public static final String STATE_NOT_FOUND = "not found";
    public static final String STATE_NOT_PAIRED = "not paired";
    public static final String STATE_DISCONNECTED = "disconnected";
    public static final String BLUETOOTH_ERROR = "bluetooth error";

    public boolean sendRawEnable = true; public boolean sendEEGEnable = true; public boolean sendESenseEnable = true; public boolean sendBlinkEnable = true;

    public int meditation = 0; public int meditation2 = 0;
    public int attention = 0; public int attention2 = 0;
    public int poorSignalValue = 200; public int poorSignalValue2 = 200;
    public float delta = 0.0F; public float delta2 = 0.0F;
    public float theta = 0.0F; public float theta2 = 0.0F;
    public float lowAlpha = 0.0F; public float lowAlpha2 = 0.0F;
    public float highAlpha = 0.0F; public float highAlpha2 = 0.0F;
    public float lowBeta = 0.0F; public float lowBeta2 = 0.0F;
    public float highBeta = 0.0F; public float highBeta2 = 0.0F;
    public float lowGamma = 0.0F; public float lowGamma2 = 0.0F;
    public float highGamma = 0.0F; public float highGamma2 = 0.0F;
    public int raw = 0;
    public int blink = 0;

    private static boolean signalWell = false;

    private BluetoothAdapter bluetoothAdapter =  null;
    private TgStreamReader tgStreamReader;
    private NskAlgoSdk nskAlgoSdk = null;
    public String connectState = "idle";

    private boolean bInited = false;
    private boolean bRunning = false;
    private NskAlgoType currentSelectedAlgo;
    private short[] raw_data = new short[512];
    private  int raw_data_index = 0;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(null ==bluetoothAdapter){
            this.connectState = "bluetooth error";
            return;
        }
        TgStreamReader.redirectConsoleLogToDocumentFolder();//write log to file
        nskAlgoSdk = new NskAlgoSdk();
        tgStreamReader = new TgStreamReader(bluetoothAdapter,callback);

        setAlgo();
        setListener();


    }

    private void setListener(){
        nskAlgoSdk.setOnSignalQualityListener(new NskAlgoSdk.OnSignalQualityListener() {
            @Override
            public void onSignalQuality(int level) {
                //Log.d(TAG, "NskAlgoSignalQualityListener: level: " + level);
                final int fLevel = level;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // change UI elements here
                        String sqStr = NskAlgoSignalQuality.values()[fLevel].toString();
                        //sqText.setText(sqStr);
                    }
                });
            }
        });

        nskAlgoSdk.setOnStateChangeListener(new NskAlgoSdk.OnStateChangeListener() {
            @Override
            public void onStateChange(int state, int reason) {

                if (state == NskAlgoState.NSK_ALGO_STATE_STOP.value
                        && reason == NskAlgoState.NSK_ALGO_REASON_SIGNAL_QUALITY.value) {
                    mHandler.sendEmptyMessageDelayed(MSG_RESTART_ALGO,1000);
                    Log.d(TAG,"NSK_ALGO_STATE_STOP NSK_ALGO_REASON_SIGNAL_QUALITY: restart later");

                }
                String stateStr = "";
                String reasonStr = "";
                for (NskAlgoState s : NskAlgoState.values()) {
                    if (s.value == state) {
                        stateStr = s.toString();
                    }
                }
                for (NskAlgoState r : NskAlgoState.values()) {
                    if (r.value == reason) {
                        reasonStr = r.toString();
                    }
                }
                Log.d(TAG, "NskAlgoSdkStateChangeListener: state: " + stateStr + ", reason: " + reasonStr);
                final String finalStateStr = stateStr + " | " + reasonStr;
                final int finalState = state;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // change UI elements here


                        if (finalState == NskAlgoState.NSK_ALGO_STATE_RUNNING.value || finalState == NskAlgoState.NSK_ALGO_STATE_COLLECTING_BASELINE_DATA.value) {
                            bRunning = true;

                        } else if (finalState == NskAlgoState.NSK_ALGO_STATE_STOP.value) {
                            bRunning = false;
                            raw_data_index = 0;

//                            output_data_count = 0;
//                            output_data = null;

                            System.gc();
                        } else if (finalState == NskAlgoState.NSK_ALGO_STATE_PAUSE.value) {
                            bRunning = false;

                        } else if (finalState == NskAlgoState.NSK_ALGO_STATE_ANALYSING_BULK_DATA.value) {
                            bRunning = true;

                        } else if (finalState == NskAlgoState.NSK_ALGO_STATE_INITED.value || finalState == NskAlgoState.NSK_ALGO_STATE_UNINTIED.value) {
                            bRunning = false;

                        }


                    }
                });
            }
        });

        nskAlgoSdk.setOnBPAlgoIndexListener(new NskAlgoSdk.OnBPAlgoIndexListener() {
            @Override
            public void onBPAlgoIndex(float delta, float theta, float alpha, float beta, float gamma) {
                Log.d(TAG, "NskAlgoBPAlgoIndexListener: BP: D[" + delta + " dB] T[" + theta + " dB] A[" + alpha + " dB] B[" + beta + " dB] G[" + gamma + "]");

                UnityPlayer.UnitySendMessage("ThinkGear", "receiveEEGAlgorithmValue", "delta:"+delta);
                UnityPlayer.UnitySendMessage("ThinkGear", "receiveEEGAlgorithmValue", "theta:"+theta);
                UnityPlayer.UnitySendMessage("ThinkGear", "receiveEEGAlgorithmValue", "alpha:"+alpha);
                UnityPlayer.UnitySendMessage("ThinkGear", "receiveEEGAlgorithmValue", "beta:"+beta);
                UnityPlayer.UnitySendMessage("ThinkGear", "receiveEEGAlgorithmValue", "gamma:"+gamma);

            }
        });

        nskAlgoSdk.setOnAttAlgoIndexListener(new NskAlgoSdk.OnAttAlgoIndexListener() {
            @Override
            public void onAttAlgoIndex(int value) {
                Log.d(TAG, "NskAlgoAttAlgoIndexListener: attention:" + value);
                UnityPlayer.UnitySendMessage("ThinkGear",
                        "receiveEEGAlgorithmValue", "attention:"+value);
            }
        });

        nskAlgoSdk.setOnMedAlgoIndexListener(new NskAlgoSdk.OnMedAlgoIndexListener() {
            @Override
            public void onMedAlgoIndex(int value) {
                Log.d(TAG, "NskAlgoMedAlgoIndexListener: meditation:" + value);
                UnityPlayer.UnitySendMessage("ThinkGear",
                        "receiveEEGAlgorithmValue", "meditation:"+value);

            }
        });

        nskAlgoSdk.setOnEyeBlinkDetectionListener(new NskAlgoSdk.OnEyeBlinkDetectionListener() {
            @Override
            public void onEyeBlinkDetect(int strength) {
                Log.d(TAG, "NskAlgoEyeBlinkDetectionListener: Eye blink detected: " + strength);
                if(sendBlinkEnable) {
                    UnityPlayer.UnitySendMessage("ThinkGear",
                            "receiveBlink", strength + "");
                }
            }
        });
    }

    private void setAlgo(){
        // check selected algos
        int algoTypes = 0;// = NskAlgoType.NSK_ALGO_TYPE_CR.value;

        algoTypes += NskAlgoType.NSK_ALGO_TYPE_MED.value;
        algoTypes += NskAlgoType.NSK_ALGO_TYPE_ATT.value;
        algoTypes += NskAlgoType.NSK_ALGO_TYPE_BLINK.value;
        algoTypes += NskAlgoType.NSK_ALGO_TYPE_BP.value;

            if (bInited) {
                nskAlgoSdk.NskAlgoUninit();
                bInited = false;
            }
            int ret = nskAlgoSdk.NskAlgoInit(algoTypes, getFilesDir().getAbsolutePath());
            if (ret == 0) {
                bInited = true;
            }

    }

    private int currentState = STATE_DISCONNECT;
    private static final int STATE_DISCONNECT = 0;
    private static final int STATE_CONNECT = 1;
    private static final int STATE_WORKING = 2;
    private boolean isSendRaw = true;

    private TgStreamHandler callback = new TgStreamHandler() {

        @Override
        public void onStatesChanged(int connectionStates) {
            // TODO Auto-generated method stub
            Log.d(TAG, "connectionStates change to: " + connectionStates);

            if (connectionStates == ConnectionStates.STATE_WORKING) {
                currentState = STATE_WORKING;

            } else if (connectionStates == ConnectionStates.STATE_CONNECTED
                    || connectionStates == ConnectionStates.STATE_COMPLETE
                    || connectionStates == ConnectionStates.STATE_STOPPED) {
                currentState = STATE_CONNECT;
            } else {
                currentState = STATE_DISCONNECT;
            }
            switch (connectionStates) {
                case ConnectionStates.STATE_CONNECTING:
                    // Do something when connecting
                    UnityThinkGear.this.connectState = STATE_CONNECTING;
                    UnityPlayer.UnitySendMessage("ThinkGear",
                            "receiveConnectState", UnityThinkGear.this.connectState);
                    break;
                case ConnectionStates.STATE_CONNECTED:
                    // Do something when connected
                    //tgStreamReader.start();

                    break;
                case ConnectionStates.STATE_WORKING:
                    // Do something when working

                    //(9) demo of recording raw data , stop() will call stopRecordRawData,
                    //or you can add a button to control it.
                    //You can change the save path by calling setRecordStreamFilePath(String filePath) before startRecordRawData
                    //tgStreamReader.startRecordRawData();
                    nskAlgoSdk.NskAlgoStart(false);
                    UnityThinkGear.this.connectState = STATE_CONNECTED;
                    UnityPlayer.UnitySendMessage("ThinkGear",
                            "receiveConnectState", UnityThinkGear.this.connectState);


                    break;
                case ConnectionStates.STATE_GET_DATA_TIME_OUT:
                    // Do something when getting data timeout

                    //(9) demo of recording raw data, exception handling
                    //tgStreamReader.stopRecordRawData();

                    if (tgStreamReader != null && tgStreamReader.isBTConnected()) {
                        tgStreamReader.stop();
                        tgStreamReader.close();
                    }

                    break;
                case ConnectionStates.STATE_STOPPED:
                    // Do something when stopped
                    // We have to call tgStreamReader.stop() and tgStreamReader.close() much more than
                    // tgStreamReader.connectAndstart(), because we have to prepare for that.

                    break;
                case ConnectionStates.STATE_DISCONNECTED:
                    // Do something when disconnected
                    UnityThinkGear.this.connectState = STATE_DISCONNECTED;
                    UnityPlayer.UnitySendMessage("ThinkGear",
                            "receiveConnectState", UnityThinkGear.this.connectState);
                    if(bRunning) {
                        nskAlgoSdk.NskAlgoStop();
                    }
                    break;
                case ConnectionStates.STATE_ERROR:
                    // Do something when you get error message
                    UnityThinkGear.this.connectState = BLUETOOTH_ERROR;
                    UnityPlayer.UnitySendMessage("ThinkGear",
                            "receiveConnectState", UnityThinkGear.this.connectState);
                    break;
                case ConnectionStates.STATE_FAILED:
                    // Do something when you get failed message
                    // It always happens when open the BluetoothSocket error or timeout
                    // Maybe the device is not working normal.
                    // Maybe you have to try again
                    UnityThinkGear.this.connectState = STATE_NOT_FOUND;
                    UnityPlayer.UnitySendMessage("ThinkGear",
                            "receiveConnectState", UnityThinkGear.this.connectState);
                    break;
            }
        }

        @Override
        public void onRecordFail(int flag) {
            // You can handle the record error message here
            Log.e(TAG,"onRecordFail: " +flag);

        }

        @Override
        public void onChecksumFail(byte[] payload, int length, int checksum) {
            // You can handle the bad packets here.
        }

        @Override
        public void onDataReceived(int datatype, int data, Object obj) {
            // You can handle the received data here
            // You can feed the raw data to algo sdk here if necessary.
            //Log.i(TAG,"onDataReceived");
            switch (datatype) {
                case MindDataType.CODE_ATTENTION:
                    if(sendESenseEnable) {
                        UnityPlayer.UnitySendMessage("ThinkGear",
                                "receiveAttention", data + "");
                    }
                    short attValue[] = {(short)data};
                    nskAlgoSdk.NskAlgoDataStream(NskAlgoDataType.NSK_ALGO_DATA_TYPE_ATT.value, attValue, 1);
                    break;
                case MindDataType.CODE_MEDITATION:
                    if(sendESenseEnable) {
                        UnityPlayer.UnitySendMessage("ThinkGear",
                                "receiveMeditation", data + "");
                    }
                    short medValue[] = {(short)data};
                    nskAlgoSdk.NskAlgoDataStream(NskAlgoDataType.NSK_ALGO_DATA_TYPE_MED.value, medValue, 1);
                    break;
                case MindDataType.CODE_POOR_SIGNAL:
                    poorSignalValue = data;
                    if(poorSignalValue > 100){
                        signalWell = false;
                    }else{
                        signalWell = true;
                    }
                    UnityPlayer.UnitySendMessage("ThinkGear",
                            "receivePoorSignal", data+"");
                    short pqValue[] = {(short)data};
                    nskAlgoSdk.NskAlgoDataStream(NskAlgoDataType.NSK_ALGO_DATA_TYPE_PQ.value, pqValue, 1);


                    break;
                case MindDataType.CODE_RAW:
                    if(isSendRaw && sendRawEnable) {
                        UnityPlayer.UnitySendMessage("ThinkGear",
                                "receiveRawdata", data + "");
                    }
                    raw_data[raw_data_index++] = (short)data;
                    if (raw_data_index == 512) {
                        nskAlgoSdk.NskAlgoDataStream(NskAlgoDataType.NSK_ALGO_DATA_TYPE_EEG.value, raw_data, raw_data_index);
                        raw_data_index = 0;
                    }
                    break;
                case MindDataType.CODE_EEGPOWER:
                    if(sendEEGEnable) {
                        EEGPower power = (EEGPower) obj;
                        UnityPlayer.UnitySendMessage("ThinkGear",
                                "receiveDelta", String.valueOf(power.delta));
                        UnityPlayer.UnitySendMessage("ThinkGear",
                                "receiveTheta", String.valueOf(power.theta));
                        UnityPlayer.UnitySendMessage("ThinkGear",
                                "receiveLowGamma", String.valueOf(power.lowGamma));
                        UnityPlayer.UnitySendMessage("ThinkGear",
                                "receiveLowBeta", String.valueOf(power.lowBeta));
                        UnityPlayer.UnitySendMessage("ThinkGear",
                                "receiveLowAlpha", String.valueOf(power.lowAlpha));
                        UnityPlayer.UnitySendMessage("ThinkGear",
                                "receiveHighGamma", String.valueOf(power.middleGamma));
                        UnityPlayer.UnitySendMessage("ThinkGear",
                                "receiveHighBeta", String.valueOf(power.highBeta));
                        UnityPlayer.UnitySendMessage("ThinkGear",
                                "receiveHighAlpha", String.valueOf(power.highAlpha));
                    }
                    break;
                default:
                    break;
            }
        }

    };

    @Override
    protected void onDestroy() {
        nskAlgoSdk.NskAlgoUninit();
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int i, KeyEvent keyEvent) {

        UnityPlayer.UnitySendMessage("ThinkGear", "receiveRemoteKeyCode",
                i+"");
        return super.onKeyDown(i, keyEvent);
    }

    public int checkBTState() {
        if (this.bluetoothAdapter != null) {
            return this.bluetoothAdapter.getState();
        }
        return -1;
    }

    public int getPairedDeviceNum() {
        if (this.bluetoothAdapter != null) {
            return this.bluetoothAdapter.getBondedDevices().size();
        }
        return -1;
    }

    public void connectWithRaw() {
        if (currentState == STATE_DISCONNECT) {
            tgStreamReader.connectAndStart();
            isSendRaw = true;
        }
    }



    public void connectNoRaw()
    {
        if (currentState == STATE_DISCONNECT) {
            tgStreamReader.connectAndStart();
            isSendRaw = false;
        }
    }

    public void disconnect()
    {
        tgStreamReader.close();
    }
    private static final int MSG_RESTART_ALGO = 13123;

    private Handler mHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what){
                case MSG_RESTART_ALGO:
                    if(currentState == STATE_WORKING) {
                        if (signalWell) {
                            NskAlgoSdk.NskAlgoStart(false);
                        } else {
                            mHandler.sendEmptyMessageDelayed(MSG_RESTART_ALGO, 1000);
                            Log.d(TAG,"MSG_RESTART_ALGO , signal is not good: " + poorSignalValue);
                        }
                    }
                    break;
            }

        }
    };

}
