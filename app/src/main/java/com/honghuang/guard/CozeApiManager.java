package com.honghuang.guard;

import android.util.Log;
import okhttp3.*;
import org.json.JSONObject;
import org.json.JSONException;

import java.io.IOException;
import java.security.cert.X509Certificate;
import javax.net.ssl.*;
import java.security.SecureRandom;

/**
 * 扣子API管理器
 * 负责与扣子平台交互，包括创建房间、获取认证信息等
 */
public class CozeApiManager {
    
    private static final String TAG = "CozeApiManager";
    
    // 官方API域名，无需IP直连
    private static final String BASE_URL = "https://api.coze.cn/v1/audio/rooms";
    
    // 你的Bot ID和PAT Token
    private static final String BOT_ID = "7619603171669147688";
    private static final String PAT_TOKEN = "pat_THFgmdD8ezb970n9t1IScBcmslWa4vjfQSDNliOLAmK3A3NWKLIsxwDrK3AtiFin";
    
    private OkHttpClient httpClient;
    private OnApiResponseListener listener;
    
    public interface OnApiResponseListener {
        void onSuccess(CreateRoomResponse response);
        void onError(String errorMessage);
    }
    
    public CozeApiManager() {
        // 使用标准HTTPS配置，无需绕过验证
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build();
    }
    
    public void setListener(OnApiResponseListener listener) {
        this.listener = listener;
    }
    
    /**
     * 创建扣子音视频房间
     */
    public void createRoom() {
        createRoomWithRetry(0); // 第一次尝试，重试次数0
    }

    /**
     * 带重试的创建房间方法
     */
    private void createRoomWithRetry(final int retryCount) {
        Log.i(TAG, "开始创建房间，Bot ID: " + BOT_ID + "，重试次数: " + retryCount);

        JSONObject requestBody = new JSONObject();
        try {
            requestBody.put("bot_id", BOT_ID);
            Log.i(TAG, "请求体: " + requestBody.toString());
        } catch (JSONException e) {
            Log.e(TAG, "构建请求失败", e);
            if (listener != null) {
                listener.onError("构建请求失败: " + e.getMessage());
            }
            return;
        }

        RequestBody body = RequestBody.create(
                requestBody.toString(),
                MediaType.parse("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url(BASE_URL)
                .addHeader("Authorization", "Bearer " + PAT_TOKEN)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        Log.i(TAG, "发送请求到: " + BASE_URL);
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "网络请求失败", e);

                // 失败重试，最多重试2次
                if (retryCount < 2) {
                    Log.i(TAG, "请求失败，" + (retryCount + 1) + "秒后重试...");
                    new android.os.Handler(android.os.Looper.getMainLooper())
                            .postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    createRoomWithRetry(retryCount + 1);
                                }
                            }, (retryCount + 1) * 1000);
                    return;
                }

                String errorMsg = "网络请求失败: " + e.getMessage();
                if (e instanceof java.net.UnknownHostException) {
                    errorMsg += "\n\n可能原因：\n1. DNS 解析失败\n2. 手机网络无法访问 api.coze.cn\n3. 需要配置代理或检查网络设置";
                } else if (e instanceof java.net.SocketTimeoutException) {
                    errorMsg += "\n\n可能原因：\n1. 网络超时\n2. 网络不稳定\n3. api.coze.cn 响应缓慢";
                } else if (e instanceof javax.net.ssl.SSLHandshakeException) {
                    errorMsg += "\n\n可能原因：\n1. SSL 证书验证失败\n2. Android 不信任 api.coze.cn 的证书\n3. 时间设置不正确";
                }

                if (listener != null) {
                    listener.onError(errorMsg);
                }
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (response.isSuccessful()) {
                        String responseBody = response.body().string();
                        try {
                            JSONObject jsonResponse = new JSONObject(responseBody);
                            CreateRoomResponse roomResponse = new CreateRoomResponse();
                            
                            if (jsonResponse.getInt("code") == 0) {
                                JSONObject data = jsonResponse.getJSONObject("data");
                                roomResponse.setUid(data.getString("uid"));
                                roomResponse.setRoomId(data.getString("room_id"));
                                roomResponse.setAppId(data.getString("app_id"));
                                roomResponse.setToken(data.getString("token"));
                                
                                if (listener != null) {
                                    listener.onSuccess(roomResponse);
                                }
                            } else {
                                int code = jsonResponse.getInt("code");
                                String msg = jsonResponse.getString("msg");
                                
                                // 可重试的错误码：429(限流)、5xx(服务器错误)
                                if (retryCount < 2 && (code == 429 || code >= 500)) {
                                    Log.i(TAG, "API错误 " + code + "，" + (retryCount + 1) + "秒后重试...");
                                    new android.os.Handler(android.os.Looper.getMainLooper())
                                            .postDelayed(new Runnable() {
                                                @Override
                                                public void run() {
                                                    createRoomWithRetry(retryCount + 1);
                                                }
                                            }, (retryCount + 1) * 1000);
                                    return;
                                }

                                if (listener != null) {
                                    listener.onError("API错误: " + code + " - " + msg);
                                }
                            }
                        } catch (JSONException e) {
                            if (listener != null) {
                                listener.onError("解析响应失败: " + e.getMessage());
                            }
                        }
                    } else {
                        int httpCode = response.code();
                        
                        // 可重试的HTTP错误：429(限流)、5xx(服务器错误)、504(网关超时)
                        if (retryCount < 2 && (httpCode == 429 || httpCode >= 500)) {
                            Log.i(TAG, "HTTP错误 " + httpCode + "，" + (retryCount + 1) + "秒后重试...");
                            new android.os.Handler(android.os.Looper.getMainLooper())
                                    .postDelayed(new Runnable() {
                                        @Override
                                        public void run() {
                                            createRoomWithRetry(retryCount + 1);
                                        }
                                    }, (retryCount + 1) * 1000);
                            return;
                        }

                        if (listener != null) {
                            listener.onError("HTTP错误: " + httpCode);
                        }
                    }
                } finally {
                    response.close();
                }
            }
        });
    }
    
    /**
     * 创建房间响应数据类
     */
    public static class CreateRoomResponse {
        private String uid;
        private String roomId;
        private String appId;
        private String token;
        
        public String getUid() {
            return uid;
        }
        
        public void setUid(String uid) {
            this.uid = uid;
        }
        
        public String getRoomId() {
            return roomId;
        }
        
        public void setRoomId(String roomId) {
            this.roomId = roomId;
        }
        
        public String getAppId() {
            return appId;
        }
        
        public void setAppId(String appId) {
            this.appId = appId;
        }
        
        public String getToken() {
            return token;
        }
        
        public void setToken(String token) {
            this.token = token;
        }
        
        @Override
        public String toString() {
            return "CreateRoomResponse{" +
                    "uid='" + uid + '\'' +
                    ", roomId='" + roomId + '\'' +
                    ", appId='" + appId + '\'' +
                    ", token='" + token + '\'' +
                    '}';
        }
    }
}
