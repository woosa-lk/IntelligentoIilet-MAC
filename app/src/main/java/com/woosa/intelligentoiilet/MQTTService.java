package com.woosa.intelligentoiilet;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONException;
import org.json.JSONObject;


public class MQTTService extends Service {
    public static final String TAG = MQTTService.class.getSimpleName();

    private static MqttAndroidClient client;
    private MqttConnectOptions conOpt;

    private String host = "tcp://120.79.44.99:1883";
    private String userName = "admin";
    private String passWord = "public";
    private static String myTopic = "device";
    private String clientId = "mqtttest";
    private IGetMessageCallBack IGetMessageCallBack;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.e(getClass().getName(), "onCreate");
        init();
    }

    public void publish(String msg){
        String topic = "sql";
        Integer qos = 0;
        Boolean retained = false;
        try {
            if (client != null){
                client.publish(topic, msg.getBytes(), qos.intValue(), retained.booleanValue());
            }
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void init() {
        String uri = host;

        client = new MqttAndroidClient(this, uri, clientId);
        client.setCallback(mqttCallback);

        conOpt = new MqttConnectOptions();
        conOpt.setCleanSession(true);
        conOpt.setConnectionTimeout(10);
        conOpt.setKeepAliveInterval(20);
        conOpt.setUserName(userName);
        conOpt.setPassword(passWord.toCharArray());

        boolean doConnect = true;
        String message = "{\"terminal_uid\":\"" + clientId + "\"}";
        String topic = myTopic;
        Integer qos = 0;
        Boolean retained = false;
        if ((!message.equals("")) || (!topic.equals(""))) {
            try {
                conOpt.setWill(topic, message.getBytes(), qos.intValue(), retained.booleanValue());
            } catch (Exception e) {
                Log.i(TAG, "Exception Occured", e);
                doConnect = false;
                iMqttActionListener.onFailure(null, e);
            }
        }

        if (doConnect) {
            doClientConnection();
        }
    }

    @Override
    public void onDestroy() {
        stopSelf();
        try {
            client.disconnect();
        } catch (MqttException e) {
            e.printStackTrace();
        }
        super.onDestroy();
    }

    /** 连接MQTT服务器 */
    private void doClientConnection() {
        if (!client.isConnected() && isConnectIsNomarl()) {
            try {
                client.connect(conOpt, null, iMqttActionListener);
            } catch (MqttException e) {
                Log.i(TAG, "连接失败1 ");
                e.printStackTrace();
            }
        }
    }

    // MQTT是否连接成功
    private IMqttActionListener iMqttActionListener = new IMqttActionListener() {

        @Override
        public void onSuccess(IMqttToken arg0) {
            Log.i(TAG, "连接成功 ");
            try {
                // 订阅myTopic话题
                client.subscribe(myTopic,1);
            } catch (MqttException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onFailure(IMqttToken arg0, Throwable arg1) {
            Log.i(TAG, "连接失败 ");
            arg1.printStackTrace();
            // 连接失败，重连
        }
    };

    // MQTT监听并且接受消息
    private MqttCallback mqttCallback = new MqttCallback() {

        @Override
        public void messageArrived(String topic, MqttMessage message) throws Exception {

            String str1 = new String(message.getPayload());
            //To do
            if (IGetMessageCallBack != null){
                IGetMessageCallBack.setMessage(str1);
            }
            String str2 = topic + ";qos:" + message.getQos() + ";retained:" + message.isRetained();
            Log.i(TAG, "messageArrived:" + str1);
            Log.i(TAG, str2);
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken arg0) {

        }

        @Override
        public void connectionLost(Throwable arg0) {
            // 失去连接，重连
        }
    };

    /** 判断网络是否连接 */
    private boolean isConnectIsNomarl() {
        ConnectivityManager connectivityManager = (ConnectivityManager) this.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = connectivityManager.getActiveNetworkInfo();
        if (info != null && info.isAvailable()) {
            String name = info.getTypeName();
            Log.i(TAG, "MQTT当前网络名称：" + name);
            return true;
        } else {
            Log.i(TAG, "MQTT 没有可用网络");
            return false;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        Log.e(getClass().getName(), "onBind");
        return new CustomBinder();
    }

    public void setIGetMessageCallBack(IGetMessageCallBack IGetMessageCallBack){
        this.IGetMessageCallBack = IGetMessageCallBack;
    }

    public class CustomBinder extends Binder {
        public MQTTService getService(){
            return MQTTService.this;
        }

        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            //Activity里获取数据
            String cmd = data.readString();
            switch (cmd) {
                case "CMD_GET_INIT_DATA":
                    JSONObject obj_init_data = new JSONObject();
                    try {
                        obj_init_data.put("cmd", cmd);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    publish(obj_init_data.toString());
                    break;

                case "CMD_SET_BIND_DEV":
                    String dev_id = data.readString();
                    String dev_floor = data.readString();
                    String dev_sex = data.readString();
                    JSONObject obj_bind_dev = new JSONObject();
                    try {
                        obj_bind_dev.put("cmd", cmd);
                        obj_bind_dev.put("devid", dev_id);
                        obj_bind_dev.put("floor", dev_floor);
                        obj_bind_dev.put("sex", dev_sex);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    publish(obj_bind_dev.toString());
                    break;
                case "CMD_GET_SYS_INIT_DATA":
                    JSONObject obj_sys_ini_data = new JSONObject();
                    try {
                        obj_sys_ini_data.put("cmd", cmd);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    publish(obj_sys_ini_data.toString());
                    break;
            }

            return super.onTransact(code, data, reply, flags);
        }
    }
}
