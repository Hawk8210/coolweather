package com.example.coolweather.util;

import okhttp3.OkHttpClient;
import okhttp3.Request;

public class HttpUtil {
    public static void sendOKHttpRequest(String address, okhttp3.Callback callback) {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(address).build();  // 传入请求地址
        client.newCall(request).enqueue(callback);  // 回调处理服务器响应
    }
}
