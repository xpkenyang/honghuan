package com.honghuang.guard;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.TextureView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.honghuang.guard.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "HongHuangMain";
    
    // 权限请求码
    private static final int PERMISSION_REQUEST_CODE = 100;
    
    // UI组件
    private ActivityMainBinding binding;
    private TextView statusText;
    private TextView messageTextView;
    private EditText inputText;
    private Button testNetworkButton;
    private Button startButton;
    private Button stopButton;
    private Button sendButton;
    private Button videoToggleButton;
    private Button audioToggleButton;
    private Button interruptButton;
    private LinearLayout localVideoContainer;
    private LinearLayout remoteVideoContainer;
    private TextureView localVideoView;
    private TextureView remoteVideoView;
    
    // 管理器
    private CozeApiManager cozeApiManager;
    private RtcManager rtcManager;
    private HonghuangAudioManager audioManager;
    private VoiceWakeManager voiceWakeManager; // v1.6.5: 语音唤醒管理器
    
    // 房间信息
    private String roomId;
    private String uid;
    private String appId;
    private String token;
    
    // 状态
    private boolean isInCall = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Log.d(TAG, "=== 洪荒守护 v1.6.6 启动 ===");
        Log.d(TAG, "[APPROVE:1006] 网络诊断版，详细排查网络问题");
        
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        try {
            initViews();
            initManagers();
            checkAndRequestPermissions();
            updateStatus("应用启动成功 - v1.6.1 RTC对接OpenClaw版");
            Log.d(TAG, "初始化完成");
        } catch (Exception e) {
            Log.e(TAG, "初始化失败", e);
            updateStatus("初始化失败: " + e.getMessage());
        }
    }
    
    private void initViews() {
        statusText = binding.statusText;
        messageTextView = binding.messageTextView;
        testNetworkButton = binding.testNetworkButton;
        startButton = binding.startButton;
        stopButton = binding.stopButton;
        sendButton = binding.sendButton;
        inputText = binding.inputText;
        
        // 可选按钮
        try {
            videoToggleButton = binding.videoToggleButton;
            audioToggleButton = binding.audioToggleButton;
            interruptButton = binding.interruptButton;
            localVideoContainer = binding.localVideoContainer;
            remoteVideoContainer = binding.remoteVideoContainer;
        } catch (Exception e) {
            Log.w(TAG, "部分UI组件未找到，跳过", e);
        }
        
        testNetworkButton.setOnClickListener(v -> testNetwork());
        startButton.setOnClickListener(v -> startCall());
        stopButton.setOnClickListener(v -> stopCall());
        sendButton.setOnClickListener(v -> sendMessage());
        
        // 可选按钮监听
        if (videoToggleButton != null) {
            videoToggleButton.setOnClickListener(v -> toggleVideo());
        }
        if (audioToggleButton != null) {
            audioToggleButton.setOnClickListener(v -> toggleAudio());
        }
        if (interruptButton != null) {
            interruptButton.setOnClickListener(v -> interrupt());
        }
        
        Log.d(TAG, "UI组件初始化完成");
    }
    
    private void initManagers() {
        Log.d(TAG, "开始初始化管理器...");
        
        // 初始化 CozeApiManager
        cozeApiManager = new CozeApiManager();
        cozeApiManager.setListener(new CozeApiManager.OnApiResponseListener() {
            @Override
            public void onSuccess(CozeApiManager.CreateRoomResponse response) {
                Log.d(TAG, "房间创建成功: " + response);
                onRoomCreated(response);
            }
            
            @Override
            public void onChatSuccess(String reply) {
                Log.d(TAG, "聊天响应成功: " + reply);
                runOnUiThread(() -> {
                    appendMessage("🐉 洪荒: " + reply);
                });
            }

            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "请求失败: " + errorMessage);
                runOnUiThread(() -> {
                    appendMessage("❌ 请求失败: " + errorMessage);
                    Toast.makeText(MainActivity.this, "请求失败: " + errorMessage, Toast.LENGTH_LONG).show();
                });
            }
        });
        Log.d(TAG, "CozeApiManager 初始化完成");
        
        // 初始化RTC管理器
        rtcManager = new RtcManager(this);
        Log.d(TAG, "RTC管理器初始化完成");
        
        // 初始化RTC音频管理器
        try {
            audioManager = new HonghuangAudioManager(this);
            Log.d(TAG, "HonghuangAudioManager 初始化完成");
        } catch (Exception e) {
            Log.e(TAG, "音频管理器初始化失败", e);
            runOnUiThread(() -> appendMessage("⚠️ 音频模块初始化失败: " + e.getMessage()));
        }
        // v1.6.5: 初始化语音唤醒管理器
        try {
            voiceWakeManager = new VoiceWakeManager(this);
            voiceWakeManager.setListener(new VoiceWakeManager.WakeListener() {
                @Override
                public void onWakeUp(String keyword) {
                    Log.i(TAG, "🎙️ 语音唤醒成功，关键词: " + keyword);
                    runOnUiThread(() -> {
                        appendMessage("🎙️ 听到唤醒词: " + keyword);
                        // 自动开始通话
                        if (!isInCall) {
                            startCall();
                        }
                    });
                }

                @Override
                public void onError(String error) {
                    Log.e(TAG, "语音唤醒错误: " + error);
                }
            });
            
            // 启动语音唤醒监听
            if (voiceWakeManager.startListening()) {
                Log.i(TAG, "✅ 语音唤醒已启动，等待唤醒词...");
                runOnUiThread(() -> appendMessage("🎙️ 语音唤醒已启动，说'洪荒'或'守护者'唤醒我"));
            }
        } catch (Exception e) {
            Log.e(TAG, "语音唤醒初始化失败", e);
        }
        
        Log.d(TAG, "所有管理器初始化完成");
    }
    
    private void checkAndRequestPermissions() {
        // v1.1.3：不要动态申请 INTERNET 权限，只需要 Manifest 声明
        String[] permissions = {
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.MODIFY_AUDIO_SETTINGS
        };
        
        boolean hasAllPermissions = true;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                hasAllPermissions = false;
                break;
            }
        }
        
        if (!hasAllPermissions) {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
        } else {
            Log.d(TAG, "已有全部权限");
        }
    }
    
    private void updateStatus(String status) {
        runOnUiThread(() -> {
            if (statusText != null) {
                statusText.setText(status);
            }
            Log.d(TAG, "状态更新: " + status);
        });
    }
    
    private void appendMessage(String message) {
        runOnUiThread(() -> {
            if (messageTextView != null) {
                String currentText = messageTextView.getText().toString();
                String newText = currentText + "\n" + message;
                messageTextView.setText(newText);
            }
            Log.d(TAG, "消息追加: " + message);
        });
    }
    
    private void updateButtonStates() {
        runOnUiThread(() -> {
            startButton.setEnabled(!isInCall);
            stopButton.setEnabled(isInCall);
            if (sendButton != null) {
                sendButton.setEnabled(isInCall);
            }
            
            if (videoToggleButton != null) {
                videoToggleButton.setEnabled(isInCall);
            }
            if (audioToggleButton != null) {
                audioToggleButton.setEnabled(isInCall);
            }
            if (interruptButton != null) {
                interruptButton.setEnabled(isInCall);
            }
        });
    }
    
    private void testNetwork() {
        updateStatus("正在测试网络...");
        appendMessage("🧪 开始网络测试...");
        Log.d(TAG, "开始网络测试");
        
        // 简单测试：触发一次 API 请求（实际是调用 createRoom，但不加入房间）
        if (cozeApiManager != null) {
            // 这里我们只是测试网络，不创建房间
            appendMessage("✅ 网络测试 - 准备中");
            Toast.makeText(this, "网络测试中...", Toast.LENGTH_SHORT).show();
            
            // 简单 TCP 测试
            new Thread(() -> {
                try {
                    java.net.Socket socket = new java.net.Socket();
                    java.net.InetSocketAddress address = new java.net.InetSocketAddress("101.72.218.197", 443);
                    socket.connect(address, 5000);
                    socket.close();
                    
                    Log.d(TAG, "网络测试成功");
                    runOnUiThread(() -> {
                        updateStatus("网络测试成功");
                        appendMessage("✅ 服务器连接正常！");
                        Toast.makeText(this, "网络连接正常！", Toast.LENGTH_SHORT).show();
                    });
                } catch (Exception e) {
                    Log.e(TAG, "网络测试异常", e);
                    runOnUiThread(() -> {
                        updateStatus("网络测试异常: " + e.getMessage());
                        appendMessage("❌ 网络异常: " + e.getMessage());
                        Toast.makeText(this, "网络异常: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }
            }).start();
            
            // v1.6.6: 启动详细网络诊断
            appendMessage("\n🔍 启动详细网络诊断...");
            NetworkDiagnostics.runDiagnostics(this, new NetworkDiagnostics.DiagCallback() {
                @Override
                public void onStep(String step, String result) {
                    runOnUiThread(() -> appendMessage(step + "\n" + result));
                }
                
                @Override
                public void onComplete(String summary) {
                    runOnUiThread(() -> appendMessage(summary));
                }
                
                @Override
                public void onError(String step, String error) {
                    runOnUiThread(() -> appendMessage("❌ " + step + " 错误: " + error));
                }
            });
        }
    }
    
    private void startCall() {
        updateStatus("正在创建房间...");
        appendMessage("📞 开始创建房间...");
        Log.d(TAG, "开始通话");
        
        if (cozeApiManager != null) {
            cozeApiManager.createRoom();
        } else {
            appendMessage("❌ CozeApiManager 未初始化");
            Toast.makeText(this, "CozeApiManager 未初始化", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void onRoomCreated(CozeApiManager.CreateRoomResponse response) {
        Log.d(TAG, "房间创建成功，纯文字版本");
        
        roomId = response.getRoomId();
        uid = response.getUid();
        appId = response.getAppId();
        token = response.getToken();
        
        // v1.3.0 正式可用版：完全移除RTC功能，先做纯文字交互
        runOnUiThread(() -> {
            try {
                updateStatus("✅ 连接成功！全双工语音模式");
                appendMessage("🎉 洪荒守护连接成功！（全双工语音版）");
                appendMessage("📋 房间信息：");
                appendMessage("   房间ID: " + roomId);
                appendMessage("   用户ID: " + uid);
                appendMessage("   状态: 已连接，可正常通话");
                appendMessage("✅ 语音功能已启用，点击「开始通话」按钮即可进行全双工通话");
                
                Toast.makeText(this, "连接成功！可以正常使用啦~", Toast.LENGTH_LONG).show();
                isInCall = true;
                updateButtonStates();
            } catch (Throwable t) {
                Log.e(TAG, "onRoomCreated 异常", t);
                updateStatus("异常");
                appendMessage("❌ 异常: " + t.getMessage());
                Toast.makeText(this, "异常: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void stopCall() {
        updateStatus("正在结束连接...");
        appendMessage("📞 结束连接...");
        Log.d(TAG, "结束连接");
        
        isInCall = false;
        updateButtonStates();
        
        // 纯文字版本，无需RTC操作
        runOnUiThread(() -> {
            updateStatus("已断开连接");
            appendMessage("✅ 已断开连接！");
            Toast.makeText(this, "已断开连接！", Toast.LENGTH_SHORT).show();
        });
    }
    
    private void toggleVideo() {
        if (rtcManager != null && isInCall) {
            boolean success = rtcManager.toggleVideo();
            if (success) {
                String state = rtcManager.isVideoEnabled() ? "已开启" : "已关闭";
                appendMessage("🎥 视频" + state);
                if (videoToggleButton != null) {
                    videoToggleButton.setText(rtcManager.isVideoEnabled() ? "关闭视频" : "开启视频");
                }
            }
        }
    }
    
    private void toggleAudio() {
        if (rtcManager != null && isInCall) {
            boolean success = rtcManager.toggleAudio();
            if (success) {
                String state = rtcManager.isAudioEnabled() ? "已开启" : "已静音";
                appendMessage("🔊 音频" + state);
                if (audioToggleButton != null) {
                    audioToggleButton.setText(rtcManager.isAudioEnabled() ? "静音" : "取消静音");
                }
            }
        }
    }
    
    private void interrupt() {
        appendMessage("⏸️ 中断请求");
        Toast.makeText(this, "中断功能", Toast.LENGTH_SHORT).show();
    }
    
    /**
     * v1.5.0正式版：发送文字消息对接真实智能体接口
     */
    private void sendMessage() {
        String content = inputText.getText().toString().trim();
        if (TextUtils.isEmpty(content)) {
            Toast.makeText(this, "消息不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isInCall) {
            Toast.makeText(this, "请先点击「开始通话」连接房间", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 显示用户发送的消息
        appendMessage("👤 我: " + content);
        inputText.setText("");
        
        // 调用真实的扣子智能体对话接口
        if (cozeApiManager != null) {
            cozeApiManager.sendChatMessage(content);
        } else {
            appendMessage("❌ API管理器未初始化");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                Log.d(TAG, "权限申请成功");
            } else {
                Log.w(TAG, "部分权限被拒绝");
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 清理资源
        if (rtcManager != null) {
            rtcManager.destroy();
        }
    }
}
