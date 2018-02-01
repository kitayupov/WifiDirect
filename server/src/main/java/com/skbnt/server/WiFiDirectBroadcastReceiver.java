package com.skbnt.server;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.util.Log;

import com.skbnt.wifidirect.CommandSender;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class WiFiDirectBroadcastReceiver extends BroadcastReceiver {

    public static final String TAG = WiFiDirectBroadcastReceiver.class.getName();

    private WifiP2pManager manager;
    private Channel channel;

    private WifiP2pDevice wifiP2pDevice;

    private InetAddress serverAddress;

    public WiFiDirectBroadcastReceiver(WifiP2pManager manager, Channel channel) {
        this.channel = channel;
        this.manager = manager;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
            // Показывает, включен ли Wi-Fi P2P (Wi-Fi Direct)

            // UI update to indicate wifi p2p status.
            int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
            if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                // Wifi Direct mode is enabled
                Log.d(TAG, "Wi-Fi Direct включен");

                /*manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        // Code for when the discovery initiation is successful goes here.
                        // No services have actually been discovered yet, so this method
                        // can often be left blank.  Code for peer discovery goes in the
                        // onReceive method, detailed below.

                        Log.e(TAG, "Discovery Initiated");
                    }

                    @Override
                    public void onFailure(int i) {
                        // Code for when the discovery initiation fails goes here.
                        // Alert the user that something went wrong.

                        Log.e(TAG, "Discovery Failed : " + getFailureReason(i));
                    }
                });*/

            } else {
                Log.e(TAG, "Wi-Fi Direct выключен");
                ((WifiManager) context.getSystemService(Context.WIFI_SERVICE)).setWifiEnabled(true);
            }

        } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
            // Указывает, что список доступных узлов изменился

            // request available peers from the wifi p2p manager. This is an
            // asynchronous call and the calling activity is notified with a
            // callback on PeerListListener.onPeersAvailable()
            Log.d(TAG, "Список доступных узлов изменился");
            manager.requestPeers(channel, new WifiP2pManager.PeerListListener() {
                @Override
                public void onPeersAvailable(WifiP2pDeviceList wifiP2pDeviceList) {
                    for (WifiP2pDevice device : wifiP2pDeviceList.getDeviceList()) {
                        Log.e(TAG, device.toString());
                        if (device.deviceName.contains("WiFiDirectClient") ||
                                device.deviceName.contains("Attribute")) {
                            if (wifiP2pDevice == null) {
                                wifiP2pDevice = device;

                                WifiP2pConfig config = new WifiP2pConfig();
                                config.groupOwnerIntent = 15;
                                config.deviceAddress = device.deviceAddress;
                                config.wps.setup = WpsInfo.PBC;

                                manager.connect(channel, config, new WifiP2pManager.ActionListener() {
                                    @Override
                                    public void onSuccess() {
                                        Log.d(TAG, "Соединение с устройством установлено");
                                    }

                                    @Override
                                    public void onFailure(int i) {
                                        Log.e(TAG, "Ошибка соединения с другим устройством: "
                                                + getFailureReason(i));
                                    }
                                });
                            }
                        }
                    }
                }
            });

        } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
            // Указывает, что состояние Wi-Fi P2P соединения изменилось

            if (manager == null) {
                return;
            }

            NetworkInfo networkInfo = (NetworkInfo) intent
                    .getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);

            if (networkInfo.isConnected()) {
                // we are connected with the other device, request connection
                // info to find group owner IP
                Log.d(TAG, "Состояние Wi-Fi P2P соединения изменилось");
                manager.requestConnectionInfo(channel, new WifiP2pManager.ConnectionInfoListener() {
                    // Сообщает, когда изменяется состояние подключения
                    @Override
                    public void onConnectionInfoAvailable(WifiP2pInfo wifiP2pInfo) {
                        // Адрес сервера (адрес владельца группы)
                        serverAddress = wifiP2pInfo.groupOwnerAddress;

                        // After the group negotiation, we can determine the group owner.
                        if (wifiP2pInfo.groupFormed) {
                            if (wifiP2pInfo.isGroupOwner) {
                                // Do whatever tasks are specific to the group owner.
                                // One common case is creating a server thread and accepting
                                // incoming connections.
                                Log.e(TAG, "Группа сформирована, это устройство - ВЛАДЕЛЕЦ");

                                createServerSocket();
                            } else {
                                // The other device acts as the client. In this case,
                                // you'll want to create a client thread that connects to the group
                                // owner.
                                Log.e(TAG, "Группа сформирована, это устройство - УЧАСТНИК");

                                createClientSocket();
                            }
                        }
                    }
                });
            } else {
                // It's a disconnect
                Log.d(TAG, "Соединение отсутствует");

                wifiP2pDevice = null;

                manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        Log.d(TAG, "Обнаружение доступных узлов запущено");
                    }

                    @Override
                    public void onFailure(int i) {
                        Log.e(TAG, "Ошибка запуска обнаружения узлов: " + getFailureReason(i));
                    }
                });
            }
        } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
            // Указывает, что детали конфигурации этого устройства изменились
            Log.d(TAG, "Детали конфигурации этого устройства изменились\n" +
                    ((WifiP2pDevice) intent.getParcelableExtra(
                            WifiP2pManager.EXTRA_WIFI_P2P_DEVICE)).toString());
        }
    }

    private String getFailureReason(int i) {
        String reason;
        switch (i) {
            case WifiP2pManager.P2P_UNSUPPORTED:
                reason = "НЕ ПОДДЕРЖИВАЕТСЯ";
                break;
            case WifiP2pManager.ERROR:
                reason = "ОШИБКА";
                break;
            case WifiP2pManager.BUSY:
                reason = "ЗАНЯТ";
                break;
            default:
                reason = "НЕИЗВЕСТНАЯ ПРИЧИНА";
        }
        return reason;
    }

    private void createClientSocket() {
        CommandSender client = new CommandSender(serverAddress);

        client.execute("hello world");
        Log.e(TAG, "Отправил сообщение: " + "hello world " + "\nпо адресу: " + serverAddress);

        try {
            String result = client.get(1000, TimeUnit.MILLISECONDS);
            Log.e(TAG, "Получил сообщение: " + result);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            e.printStackTrace();
        }
    }

    private void createServerSocket() {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // create ServerSocket using specified port
                    ServerSocket serverSocket = new ServerSocket(12345);

                    while (true) {
                        // block the call until connection is created and return
                        // Socket object
                        Socket socket = serverSocket.accept();

                        BufferedReader commandsReader = new BufferedReader(
                                new InputStreamReader(socket.getInputStream()));

                        String request = commandsReader.readLine();
                        Log.e(TAG, "Получил сообщение: " + request + "\nот: " + socket.toString());

                        String answer = "ok";

                        Log.e(TAG, "Отправил сообщение: " + answer);
                        SocketServerReplyThread socketServerReplyThread =
                                new SocketServerReplyThread(socket, answer);
                        socketServerReplyThread.run();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        thread.start();
    }

    private class SocketServerReplyThread extends Thread {

        private Socket hostThreadSocket;
        private String message;

        SocketServerReplyThread(Socket socket, String message) {
            hostThreadSocket = socket;
            this.message = message;
        }

        @Override
        public void run() {
            OutputStream outputStream;
            try {
                outputStream = hostThreadSocket.getOutputStream();
                PrintStream printStream = new PrintStream(outputStream);
                printStream.println(message);
                printStream.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }
}
