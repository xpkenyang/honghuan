# Gradle 插件配置修正

## 问题

根目录的 `build.gradle` 缺少插件版本声明，会导致 Gradle 无法正确加载 Android 插件。

## 原始配置问题

### build.gradle（根目录）

**问题1：** 缺少 `plugins` 块
```gradle
pluginManagement {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://artifact.bytedance.com/repository/Volcengine/' }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url 'https://artifact.bytedance.com/repository/Volcengine/' }
    }
}

rootProject.name = "HongHuang Guard"
include ':app'
```

### app/build.gradle

**问题2：** 同时声明了 application 和 library 插件（不合理）
```gradle
plugins {
    id 'com.android.application'
    id 'com.android.library'  // ❌ 不应该同时声明
}
```

## 修正后的配置

### build.gradle（根目录）

**添加了插件版本声明：**
```gradle
pluginManagement {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://artifact.bytedance.com/repository/Volcengine/' }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url 'https://artifact.bytedance.com/repository/Volcengine/' }
    }
}

plugins {
    // Android Gradle Plugin
    id 'com.android.application' version '8.2.0' apply false
    id 'com.android.library' version '8.2.0' apply false
}

rootProject.name = "HongHuang Guard"
include ':app'
```

**说明：**
- `apply false` 表示声明插件版本，但不应用到根项目
- 实际应用在子模块（app）中

### app/build.gradle

**只保留 application 插件：**
```gradle
plugins {
    id 'com.android.application'
}

android {
    namespace 'com.honghuang.guard'
    compileSdk 34
    // ... 其他配置
}

dependencies {
    // ... 依赖配置
}
```

### gradle.properties（新增）

**添加了必要的 Gradle 配置：**
```gradle
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.nonTransitiveRClass=true
org.gradle.caching=true
org.gradle.configuration-cache=true
```

## 配置说明

### 插件版本

- **com.android.application** version '8.2.0'
- **com.android.library** version '8.2.0'

### Gradle 优化配置

| 配置项 | 说明 |
|--------|------|
| org.gradle.jvmargs | JVM 内存限制 2GB |
| android.useAndroidX | 启用 AndroidX 库 |
| android.nonTransitiveRClass | 启用非传递性 R 类 |
| org.gradle.caching | 启用构建缓存 |
| org.gradle.configuration-cache | 启用配置缓存 |

## Maven 仓库

已配置的仓库：
1. **google()** - Google Maven 仓库
2. **mavenCentral()** - Maven Central
3. **artifact.bytedance.com/Volcengine/** - 火山引擎 RTC SDK

## 依赖版本

| 依赖 | 版本 | 说明 |
|------|------|------|
| VolcEngineRTC | 3.58.1.19400 | 火山引擎 RTC SDK |
| coze-api | 0.4.1 | 扣子 API SDK（最新版本） |

## 构建命令

```bash
# 清理并构建
./gradlew clean build

# 同步依赖
./gradlew build --refresh-dependencies

# 编译 APK
./gradlew assembleDebug

# 安装到设备
./gradlew installDebug
```

## 验证清单

- [x] 根目录 build.gradle 有 plugins 块
- [x] Android Gradle Plugin 版本已声明（8.2.0）
- [x] app/build.gradle 只声明 application 插件
- [x] Maven 仓库已正确配置
- [x] 依赖已验证存在
- [x] gradle.properties 已创建
- [ ] 项目可以成功构建（待验证）

---

**更新时间：** 2026-03-22 21:45
**更新原因：** 发现插件配置不完整，可能导致构建失败
**验证状态：** 配置已修正，待构建验证