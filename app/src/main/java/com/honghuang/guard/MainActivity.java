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
        
        Log.d(TAG, "=== 洪荒守护 v1.1.36 启动 ===");
        Log.d(TAG, "完整版本，使用所有管理器");
        
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        try {
            initViews();
            initManagers();
            checkAndRequestPermissions();
            updateStatus("应用启动成功 - v1.1.36");
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
            public void onError(String errorMessage) {
                Log.e(TAG, "房间创建失败: " + errorMessage);
                runOnUiThread(() -> {
                    updateStatus("房间创建失败");
                    appendMessage("❌ 房间创建失败: " + errorMessage);
                    Toast.makeText(MainActivity.this, "房间创建失败: " + errorMessage, Toast.LENGTH_LONG).show();
                });
            }
        });
        Log.d(TAG, "CozeApiManager 初始化完成");
        
        // 初始化 RtcManager
        rtcManager = new RtcManager(this);
        rtcManager.setListener(new RtcManager.RtcEventListener() {
            @Override
            public void onJoinRoomSuccess(String roomId) {
                Log.d(TAG, "RTC 加入房间成功: " + roomId);
                runOnUiThread(() -> {
                    updateStatus("已加入房间");
                    appendMessage("✅ 已加入房间: " + roomId);
                    isInCall = true;
                    updateButtonStates();
                });
            }

            @Override
            public void onJoinRoomError(int errorCode, String errorMessage) {
                Log.e(TAG, "RTC 加入房间失败: " + errorCode + " - " + errorMessage);
                runOnUiThread(() -> {
                    updateStatus("加入房间失败");
                    appendMessage("❌ 加入房间失败: " + errorMessage);
                    Toast.makeText(MainActivity.this, "加入房间失败: " + errorMessage, Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onRemoteUserJoined(String uid) {
                Log.d(TAG, "远程用户加入: " + uid);
                runOnUiThread(() -> {
                    appendMessage("👤 远程用户加入: " + uid);
                });
            }

            @Override
            public void onRemoteUserLeft(String uid) {
                Log.d(TAG, "远程用户离开: " + uid);
                runOnUiThread(() -> {
                    appendMessage("👤 远程用户离开: " + uid);
                });
            }

            @Override
            public void onMessageReceived(String message) {
                Log.d(TAG, "收到消息: " + message);
                runOnUiThread(() -> {
                    appendMessage("🤖 洪荒: " + message);
                });
            }

            @Override
            public void onError(int errorCode, String errorMessage) {
                Log.e(TAG, "RTC 错误: " + errorCode + " - " + errorMessage);
                runOnUiThread(() -> {
                    appendMessage("❌ RTC 错误: " + errorMessage);
                });
            }

            @Override
            public void onWarning(int warningCode, String warningMessage) {
                Log.w(TAG, "RTC 警告: " + warningCode + " - " + warningMessage);
                runOnUiThread(() -> {
                    appendMessage("⚠️ RTC 警告: " + warningMessage);
                });
            }
        });
        Log.d(TAG, "RtcManager 初始化完成");
        
        // v1.1.40：暂时注释掉 HonghuangAudioManager，排查是否是它导致的闪退
        // 初始化 HonghuangAudioManager（如果有的话）
        // try {
        //     audioManager = new HonghuangAudioManager(this);
        //     Log.d(TAG, "HonghuangAudioManager 初始化完成");
        // } catch (Exception e) {
        //     Log.w(TAG, "HonghuangAudioManager 初始化失败，跳过", e);
        // }
        
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
        Log.d(TAG, "房间创建成功，准备加入 RTC 房间");
        
        roomId = response.getRoomId();
        uid = response.getUid();
        appId = response.getAppId();
        token = response.getToken();
        
        // v1.1.41：把所有 RTC 相关操作都放到 UI 线程里执行
        runOnUiThread(() -> {
            try {
                updateStatus("正在加入 RTC 房间...");
                appendMessage("✅ 房间创建成功！");
                appendMessage("📞 正在加入 RTC 房间...");
                
                // 创建 RTC 引擎
                if (rtcManager != null) {
                    boolean engineCreated = rtcManager.createEngine(appId);
                    if (!engineCreated) {
                        Log.e(TAG, "RTC 引擎创建失败");
                        appendMessage("❌ RTC 引擎创建失败");
                        Toast.makeText(this, "RTC 引擎创建失败", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    
                    // 加入 RTC 房间
                    boolean joined = rtcManager.joinRoom(roomId, uid, token);
                    if (!joined) {
                        Log.e(TAG, "RTC 加入房间请求失败");
                        // 注意：joinRoom 是异步的，失败会通过 onJoinRoomError 回调
                    }
                }
            } catch (Throwable t) {
                Log.e(TAG, "onRoomCreated 异常", t);
                updateStatus("加入房间异常");
                appendMessage("❌ 加入房间异常: " + t.getMessage());
                appendMessage("📋 异常详情: " + Log.getStackTraceString(t));
                Toast.makeText(this, "加入房间异常: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void stopCall() {
        updateStatus("正在结束通话...");
        appendMessage("📞 结束通话...");
        Log.d(TAG, "结束通话");
        
        isInCall = false;
        updateButtonStates();
        
        // 离开 RTC 房间
        if (rtcManager != null) {
            rtcManager.leaveRoom();
            rtcManager.destroy();
        }
        
        runOnUiThread(() -> {
            updateStatus("通话已结束");
            appendMessage("✅ 通话已结束！");
            Toast.makeText(this, "通话已结束！", Toast.LENGTH_SHORT).show();
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
