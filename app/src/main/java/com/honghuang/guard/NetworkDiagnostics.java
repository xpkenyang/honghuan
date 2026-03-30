package com.honghuang.guard;

import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 网络诊断工具
 * v1.6.6-网络诊断版专用
 */
public class NetworkDiagnostics {
    
    private static final String TAG = "NetworkDiag";
    
    // 测试目标
    private static final String TEST_HOST = "api.coze.cn";
    private static final String TEST_URL = "https://api.coze.cn/v1/audio/rooms";
    private static final String PAT_TOKEN = "pat_THFgmdD8ezb970n9t1IScBcmslWa4vjfQSDNliOLAmK3A3NWKLIsxwDrK3AtiFin";
    
    public interface DiagCallback {
        void onStep(String step, String result);
        void onComplete(String summary);
        void onError(String step, String error);
    }
    
    /**
     * 执行完整网络诊断
     */
    public static void runDiagnostics(Context context, DiagCallback callback) {
        new Thread(() -> {
            try {
                callback.onStep("1️⃣ DNS解析测试", "开始...");
                String dnsResult = testDNS();
                callback.onStep("1️⃣ DNS解析测试", dnsResult);
                
                callback.onStep("2️⃣ HTTP连通性测试", "开始...");
                String httpResult = testHTTP();
                callback.onStep("2️⃣ HTTP连通性测试", httpResult);
                
                callback.onStep("3️⃣ HTTPS/SSL测试", "开始...");
                String httpsResult = testHTTPS();
                callback.onStep("3️⃣ HTTPS/SSL测试", httpsResult);
                
                callback.onStep("4️⃣ 扣子API认证测试", "开始...");
                String apiResult = testAPIAuth();
                callback.onStep("4️⃣ 扣子API认证测试", apiResult);
                
                String summary = generateSummary(dnsResult, httpResult, httpsResult, apiResult);
                callback.onComplete(summary);
                
            } catch (Exception e) {
                callback.onError("诊断过程", e.getMessage());
            }
        }).start();
    }
    
    /**
     * 测试DNS解析
     */
    private static String testDNS() {
        try {
            long start = System.currentTimeMillis();
            InetAddress address = InetAddress.getByName(TEST_HOST);
            long time = System.currentTimeMillis() - start;
            return "✅ 成功\n   域名: " + TEST_HOST + 
                   "\n   IP: " + address.getHostAddress() + 
                   "\n   解析时间: " + time + "ms";
        } catch (UnknownHostException e) {
            return "❌ 失败\n   错误: DNS解析失败\n   详情: " + e.getMessage();
        }
    }
    
    /**
     * 测试HTTP连通性
     */
    private static String testHTTP() {
        try {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .build();
            
            Request request = new Request.Builder()
                    .url("http://" + TEST_HOST)
                    .build();
            
            long start = System.currentTimeMillis();
            Response response = client.newCall(request).execute();
            long time = System.currentTimeMillis() - start;
            
            return "✅ 成功\n   响应码: " + response.code() + 
                   "\n   响应时间: " + time + "ms" +
                   "\n   连接正常";
        } catch (IOException e) {
            return "❌ 失败\n   错误: " + e.getClass().getSimpleName() + 
                   "\n   详情: " + e.getMessage();
        }
    }
    
    /**
     * 测试HTTPS/SSL
     */
    private static String testHTTPS() {
        try {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .build();
            
            Request request = new Request.Builder()
                    .url("https://" + TEST_HOST)
                    .build();
            
            long start = System.currentTimeMillis();
            Response response = client.newCall(request).execute();
            long time = System.currentTimeMillis() - start;
            
            return "✅ 成功\n   响应码: " + response.code() + 
                   "\n   握手时间: " + time + "ms" +
                   "\n   SSL证书有效";
        } catch (IOException e) {
            return "❌ 失败\n   错误: " + e.getClass().getSimpleName() + 
                   "\n   详情: " + e.getMessage() +
                   "\n   可能原因: 证书问题/代理拦截/中间人攻击";
        }
    }
    
    /**
     * 测试扣子API认证
     */
    private static String testAPIAuth() {
        try {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .build();
            
            String json = "{\"bot_id\": \"7619603171669147688\"}";
            okhttp3.RequestBody body = okhttp3.RequestBody.create(
                    json, okhttp3.MediaType.parse("application/json"));
            
            Request request = new Request.Builder()
                    .url(TEST_URL)
                    .addHeader("Authorization", "Bearer " + PAT_TOKEN)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();
            
            long start = System.currentTimeMillis();
            Response response = client.newCall(request).execute();
            long time = System.currentTimeMillis() - start;
            
            String responseBody = response.body() != null ? response.body().string() : "";
            
            if (response.code() == 200) {
                return "✅ 成功\n   响应码: 200\n   响应时间: " + time + "ms" +
                       "\n   API认证正常\n   可以创建RTC房间";
            } else {
                return "⚠️ 异常\n   响应码: " + response.code() + 
                       "\n   响应: " + responseBody.substring(0, Math.min(100, responseBody.length()));
            }
        } catch (IOException e) {
            return "❌ 失败\n   错误: " + e.getClass().getSimpleName() + 
                   "\n   详情: " + e.getMessage();
        }
    }
    
    /**
     * 生成诊断总结
     */
    private static String generateSummary(String dns, String http, String https, String api) {
        int pass = 0;
        int fail = 0;
        
        if (dns.contains("✅")) pass++; else fail++;
        if (http.contains("✅")) pass++; else fail++;
        if (https.contains("✅")) pass++; else fail++;
        if (api.contains("✅")) pass++; else fail++;
        
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("═══════════════════════════════════════\n");
        sb.append("📊 网络诊断总结\n");
        sb.append("═══════════════════════════════════════\n");
        sb.append("通过: ").append(pass).append("/4\n");
        sb.append("失败: ").append(fail).append("/4\n");
        sb.append("\n");
        
        if (fail == 0) {
            sb.append("✅ 所有测试通过！网络正常\n");
            sb.append("APP应该可以正常连接\n");
        } else if (fail == 1) {
            sb.append("⚠️ 部分测试失败，可能存在网络限制\n");
            sb.append("建议：切换网络环境或检查代理设置\n");
        } else {
            sb.append("❌ 多项测试失败，网络问题严重\n");
            sb.append("建议：\n");
            sb.append("1. 切换WiFi/移动数据\n");
            sb.append("2. 关闭VPN/代理\n");
            sb.append("3. 更换网络环境测试\n");
        }
        
        return sb.toString();
    }
}
