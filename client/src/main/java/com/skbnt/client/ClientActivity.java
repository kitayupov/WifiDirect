package com.skbnt.client;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import java.lang.reflect.Method;

public class ClientActivity extends AppCompatActivity {

    public static final String TAG = ClientActivity.class.getName();

    private WifiP2pManager manager;
    private WifiP2pManager.Channel channel;

    private BroadcastReceiver receiver = null;
    private final IntentFilter intentFilter = new IntentFilter();

    private String deviceName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Отслеживает изменение статуса Wi-Fi P2P (Wi-Fi Direct)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        // Отслеживает изменение состояния Wi-Fi P2P соединения
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        // Отслеживает изменение свойств данного устройства
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this, getMainLooper(), null);

        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        deviceName = bluetoothAdapter.getName();
        setDeviceName("WiFiDirectClient");
    }

    @Override
    public void onResume() {
        super.onResume();
        receiver = new WiFiDirectBroadcastReceiver(manager, channel);
        registerReceiver(receiver, intentFilter);
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
        ((WifiManager) getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE)).setWifiEnabled(false);
        setDeviceName(deviceName);
    }

    public void setDeviceName(String devName) {
        try {
            Method m = manager.getClass().getMethod(
                    "setDeviceName",
                    WifiP2pManager.Channel.class, String.class,
                    WifiP2pManager.ActionListener.class);

            m.invoke(manager, channel, devName, new WifiP2pManager.ActionListener() {
                public void onSuccess() {
                    Log.d(TAG, "Имя устройства изменено успешно");
                }

                public void onFailure(int reason) {
                    Log.e(TAG, "Ошибка изменения имени устройства: "
                            + getFailureStringReason(reason));
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String getFailureStringReason(int i) {
        String reason;
        switch (i) {
            case WifiP2pManager.P2P_UNSUPPORTED:
                reason = "не поддерживается";
                break;
            case WifiP2pManager.ERROR:
                reason = "ошибка";
                break;
            case WifiP2pManager.BUSY:
                reason = "занят";
                break;
            default:
                reason = "неизвестная причина";
        }
        return reason;
    }
}
