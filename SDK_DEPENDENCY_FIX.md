# Realtime Android SDK 依赖修正（完整版）

## 问题

之前配置的依赖地址不正确：
```gradle
implementation 'cn.coze:realtime-android:1.0.0'  // ❌ 404 Not Found
implementation 'cn.coze:api:1.0.0'               // ❌ 404 Not Found
```

## 解决方法

通过访问扣子官网文档（https://docs.coze.cn）找到了正确的配置。

### 1. Maven 仓库配置（已在 settings.gradle 中）

```gradle
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url 'https://artifact.bytedance.com/repository/Volcengine/' }
    }
}
```

### 2. 正确的依赖配置

**从扣子官网文档获取：**

```toml
[versions]
volcenginertc = "3.58.1.19400"
cozeApi = "0.2.1"  # 文档中的版本

[libraries]
volcenginertc = { module = "com.volcengine:VolcEngineRTC", version.ref = "volcenginertc" }
coze-api = { module = "com.coze:coze-api", version.ref = "cozeApi" }
```

**转换为 Gradle 配置：**

```gradle
implementation 'com.volcengine:VolcEngineRTC:3.58.1.19400'  // ✅ 来自 Volcengine 仓库
implementation 'com.coze:coze-api:0.4.1'                     // ✅ 来自 Maven Central（最新版本）
```

### 3. 依赖验证

**VolcEngineRTC 验证：**
```bash
curl -I https://artifact.bytedance.com/repository/Volcengine/com/volcengine/VolcEngineRTC/3.58.1.19400/VolcEngineRTC-3.58.1.19400.pom
# 返回：HTTP 200 ✅
```

**coze-api 验证：**
```bash
# 先查找版本
curl -s "https://repo1.maven.org/maven2/com/coze/coze-api/" | grep -o '<a href="[^"]*"'
# 发现版本：0.1.0 ~ 0.4.1

# 验证最新版本 0.4.1
curl -I https://repo1.maven.org/maven2/com/coze/coze-api/0.4.1/coze-api-0.4.1.pom
# 返回：HTTP 200 ✅
```

**coze-api 版本列表（按版本号排序）：**
- 0.1.0, 0.1.1, 0.1.3, 0.1.4, 0.1.5, 0.1.6
- 0.2.0, 0.2.1, 0.2.3, 0.2.4, 0.2.5, 0.2.6, 0.2.7, 0.2.8
- 0.3.0, 0.3.1, 0.3.2, 0.3.3
- 0.4.0, 0.4.1（最新）

**说明：**
- 扣子文档中提到的 `cozeApi = "0.2.1"` 是较早的版本
- Maven Central 上最新的版本是 `0.4.1`
- 建议使用最新版本 0.4.1

## 更新后的配置

### app/build.gradle

```gradle
dependencies {
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
    implementation 'androidx.lifecycle:lifecycle-runtime:2.6.1'
    implementation 'androidx.activity:activity:1.7.2'
    
    // Realtime Android SDK 依赖（2026-03-22 从扣子官网获取）
    implementation 'com.volcengine:VolcEngineRTC:3.58.1.19400'
    // coze-api 依赖（从 Maven Central 获取最新版本）
    implementation 'com.coze:coze-api:0.4.1'
    
    implementation 'com.google.code.gson:gson:2.8.9'
    implementation 'com.squareup.okhttp3:okhttp:4.11.0'
}
```

### settings.gradle

```gradle
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url 'https://artifact.bytedance.com/repository/Volcengine/' }
    }
}
```

## 依赖来源总结

| 依赖 | 版本 | 仓库 | 验证 |
|------|------|------|------|
| VolcEngineRTC | 3.58.1.19400 | artifact.bytedance.com | ✅ HTTP 200 |
| coze-api | 0.4.1（最新） | Maven Central | ✅ HTTP 200 |

## 下一步

1. ✅ 依赖配置已完成
2. 可以尝试构建项目：
   ```bash
   ./gradlew clean build
   ```
3. 验证依赖是否正常下载
4. 参考 Realtime Android SDK 文档完成集成

## 参考文档

- 扣子官网：https://docs.coze.cn
- Realtime Android SDK 集成文档：https://docs.coze.cn/doc/XXXXXX
- Maven Central：https://repo1.maven.org/maven2/com/coze/coze-api/
- 火山引擎 RTC 文档：https://www.volcengine.com/docs/XXXXXX

---

**更新时间：** 2026-03-22 21:43
**更新方式：** 通过 agent-browser 查询扣子官网 + curl 验证 Maven Central
**验证状态：** VolcEngineRTC ✅，coze-api ✅（已找到 Maven Central 最新版本 0.4.1）