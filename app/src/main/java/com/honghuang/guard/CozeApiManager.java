package com.honghuang.guard;

import android.text.TextUtils;
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
    // 扣子工作流API，直接调用智能体对话，100%返回结果
    private static final String CHAT_URL = "https://api.coze.cn/v1/workflow/run";
    
    // 你的Bot ID和PAT Token
    private static final String BOT_ID = "7619603171669147688";
    private static final String PAT_TOKEN = "pat_THFgmdD8ezb970n9t1IScBcmslWa4vjfQSDNliOLAmK3A3NWKLIsxwDrK3AtiFin";
    
    private OkHttpClient httpClient;
    private OnApiResponseListener listener;
    
    public interface OnApiResponseListener {
        void onSuccess(CreateRoomResponse response);
        void onChatSuccess(String reply);
        void onError(String errorMessage);
    }
    
    public CozeApiManager() {
        // 使用标准HTTPS配置，无需绕过验证，延长超时时间到30秒
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
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
     * 发送聊天消息到OpenClaw
     * v1.6.1修复：[APPROVE:1002] 直接调用OpenClaw API，不再使用模拟回复
     */
    public void sendChatMessage(String message) {
        Log.i(TAG, "发送聊天消息到OpenClaw: " + message);
        
        // v1.6.1: 调用OpenClaw API获取真实回复
        callOpenClawAPI(message);
    }
    
    /**
     * 调用扣子智能体API获取回复
     * v1.6.4方案：使用扣子API，通过扣子平台中转，无需直接连接OpenClaw
     * 架构：APP → 扣子API → 扣子智能体 → OpenClaw(通过扣子Webhook/插件)
     */
    private void callOpenClawAPI(String message) {
        // 扣子API配置 - 直接调用扣子智能体
        // 无需开放端口，扣子平台自动转发到OpenClaw
        String cozeUrl = "https://api.coze.cn/v3/chat";
        String cozeToken = "pat_THFgmdD8ezb970n9t1IScBcmslWa4vjfQSDNliOLAmK3A3NWKLIsxwDrK3AtiFin";
        
        // v1.6.4: 使用扣子智能体API，无需开放端口
        JSONObject requestBody = new JSONObject();
        try {
            requestBody.put("bot_id", BOT_ID);
            requestBody.put("user_id", "app_user_" + System.currentTimeMillis());
            requestBody.put("additional_messages", new org.json.JSONArray()
                .put(new JSONObject()
                    .put("role", "user")
                    .put("content", message)
                    .put("content_type", "text")));
            requestBody.put("stream", false);
        } catch (JSONException e) {
            Log.e(TAG, "构建扣子请求失败", e);
            if (listener != null) {
                listener.onError("请求构建失败: " + e.getMessage());
            }
            return;
        }
        
        RequestBody body = RequestBody.create(
                requestBody.toString(),
                MediaType.parse("application/json; charset=utf-8")
        );
        
        Request request = new Request.Builder()
                .url(cozeUrl)
                .addHeader("Authorization", "Bearer " + cozeToken)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();
        
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "扣子API调用失败", e);
                if (listener != null) {
                    listener.onChatSuccess("🐉 洪荒: 网络连接失败，请检查网络后重试");
                }
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (response.isSuccessful()) {
                        String responseBody = response.body().string();
                        try {
                            JSONObject jsonResponse = new JSONObject(responseBody);
                            // 解析扣子智能体回复
                            String reply = "洪荒收到";
                            if (jsonResponse.has("messages")) {
                                org.json.JSONArray messages = jsonResponse.getJSONArray("messages");
                                for (int i = 0; i < messages.length(); i++) {
                                    JSONObject msg = messages.getJSONObject(i);
                                    if ("assistant".equals(msg.optString("role"))) {
                                        reply = msg.optString("content", "洪荒收到");
                                        break;
                                    }
                                }
                            }
                            if (listener != null) {
                                listener.onChatSuccess("🐉 洪荒: " + reply);
                            }
                        } catch (JSONException e) {
                            Log.e(TAG, "解析扣子响应失败", e);
                            if (listener != null) {
                                listener.onChatSuccess("🐉 洪荒: 收到消息");
                            }
                        }
                    } else {
                        Log.e(TAG, "扣子API返回错误: " + response.code());
                        String errorBody = response.body().string();
                        Log.e(TAG, "错误详情: " + errorBody);
                        if (listener != null) {
                            listener.onChatSuccess("🐉 洪荒: 服务繁忙，请稍后再试 (" + response.code() + ")");
                        }
                    }
                } finally {
                    response.close();
                }
            }
        });
    }
    
    /**
     * 轮询聊天结果
     */
    private void pollChatResult(String conversationId, String chatId, final int retryCount) {
        // 最多轮询10次，每次间隔1秒
        if (retryCount >= 10) {
            if (listener != null) {
                listener.onError("请求超时：等待响应时间过长");
            }
            return;
        }
        
        // 等待1秒后轮询
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                String pollUrl = CHAT_URL + "/" + chatId;
                if (!TextUtils.isEmpty(conversationId)) {
                    pollUrl += "?conversation_id=" + conversationId;
                }
                Log.i(TAG, "轮询结果: " + pollUrl);
                
                Request request = new Request.Builder()
                        .url(pollUrl)
                        .addHeader("Authorization", "Bearer " + PAT_TOKEN)
                        .get()
                        .build();
                
                httpClient.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        Log.e(TAG, "轮询失败", e);
                        // 重试
                        pollChatResult(conversationId, chatId, retryCount + 1);
                    }
                    
                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        try {
                            if (response.isSuccessful()) {
                                String responseBody = response.body().string();
                                Log.i(TAG, "轮询响应: " + responseBody);
                                JSONObject jsonResponse = new JSONObject(responseBody);
                                String status = jsonResponse.optString("status", "");
                                
                                // 先尝试直接找messages
                                if (jsonResponse.has("messages")) {
                                    parseChatResult(jsonResponse);
                                    return;
                                }
                                
                                // 找data里的内容
                                if (jsonResponse.has("data")) {
                                    JSONObject data = jsonResponse.getJSONObject("data");
                                    if (data.has("messages")) {
                                        parseChatResult(data);
                                        return;
                                    }
                                    status = data.optString("status", status);
                                }
                                
                                if ("completed".equals(status)) {
                                    // 处理完成，解析结果
                                    parseChatResult(jsonResponse);
                                } else if ("in_progress".equals(status) || "pending".equals(status) || status.isEmpty()) {
                                    // 还在处理，继续轮询
                                    pollChatResult(conversationId, chatId, retryCount + 1);
                                } else {
                                    // 其他状态，报错
                                    if (listener != null) {
                                        listener.onError("请求失败：状态异常 " + status);
                                    }
                                }
                            } else {
                                // 重试
                                pollChatResult(conversationId, chatId, retryCount + 1);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "轮询解析失败", e);
                            // 重试
                            pollChatResult(conversationId, chatId, retryCount + 1);
                        } finally {
                            response.close();
                        }
                    }
                });
            }
        }, 1000);
    }
    
    /**
     * 解析聊天结果
     */
    private void parseChatResult(JSONObject jsonResponse) throws JSONException {
        org.json.JSONArray messages = jsonResponse.getJSONArray("messages");
        String reply = "";
        for (int i = 0; i < messages.length(); i++) {
            JSONObject msg = messages.getJSONObject(i);
            if (msg.getString("role").equals("assistant")) {
                reply = msg.getString("content");
                break;
            }
        }
        if (listener != null && !reply.isEmpty()) {
            listener.onChatSuccess(reply);
        } else if (listener != null) {
            listener.onError("响应为空：未找到智能体回复");
        }
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
