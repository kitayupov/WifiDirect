package com.skbnt.wifidirect;

import android.os.AsyncTask;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;

public class CommandSender extends AsyncTask<String, String, String> {

    private InetAddress destinationIp;

    public CommandSender(InetAddress inetAddress) {
        this.destinationIp = inetAddress;
    }

    @Override
    protected String doInBackground(String... params) {
        try (Socket socket = new Socket(destinationIp, 12345);
             OutputStream outputStream = socket.getOutputStream();
             DataOutputStream output = new DataOutputStream(outputStream);
             InputStream inputStream = socket.getInputStream();
             InputStreamReader streamReader = new InputStreamReader(inputStream);
             BufferedReader reader = new BufferedReader(streamReader)) {

            // Сначала отправляем команду серверу
            output.writeBytes(params[0] + "\n");

            // После этого читаем ответ
            return reader.readLine();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}