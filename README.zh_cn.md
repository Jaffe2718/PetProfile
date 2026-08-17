# PetProfile

PetProfile 是一款面向爬宠饲养玩家和繁育者的本地优先 Android 应用，用于管理爬宠档案、饲养记录、谱系图、数值可视化、备份分享，以及设备间二维码转交。

项目使用 Java 编写，通过 Android Studio 和 Gradle 构建。

## 功能

- **档案管理**
  - 新建、编辑、删除档案。
  - 按昵称或任意生物分类层级搜索：界、门、纲、目、科、属、种、亚种。
  - 按年份、性别、状态（记录中/已归档）和生物分类进行多级筛选。
  - 列表和谱系两种视图模式。
  - 每个档案条目可展开属性表，显示字段名和数值+单位或文本。

- **档案名片与记录**
  - 界门纲目科属种/亚种分类字段，昵称必填，性别可选。
  - 单个头像。
  - 档案属性和记录属性分别管理。
  - 父母本选择带分类学一致性和家族树回环校验。
  - 记录类型：建立记录、日常记录、转交记录、归档记录。
  - 时间、地图选点、数值/标签字段、Markdown 笔记和图片。
  - 记录顺序约束：建立记录唯一且最早，归档记录最多一条且最晚。

- **可视化**
  - 数值型记录字段可绘制折线图。

- **备份、分享与转交**
  - ZIP 导入/导出。
  - PNG 档案卡片分享。
  - 二维码结合同一局域网 TCP 传输，可转交包含图片的完整档案树。

- **多语言**
  - 简体中文、繁体中文（香港）、英文、日文。

## 技术栈

- Android：Java 17
- UI：AndroidX、Material Components、RecyclerView
- 本地数据库：Room
- 图片加载：Glide
- Markdown：Markwon
- 二维码生成：ZXing
- 二维码扫描：CameraX + ML Kit Barcode Scanning

## 构建

### Android Studio

1. 安装 [Android Studio](https://developer.android.com/studio)。
2. 以 Android 项目方式打开本仓库。
3. 等待 Gradle Sync 并下载所需 SDK 组件。
4. 执行 **Build > Build Bundle(s) / APK(s) > Build APK(s)**。

Debug APK 输出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

### 命令行

在配置好 Android SDK 和 JDK 17 后：

```bash
./gradlew assembleDebug
```

工程配置：

- `minSdk`：26
- `targetSdk`：35
- `compileSdk`：35
- Java 兼容版本：17

## 数据与图片

- 数据通过 Room 保存在本地数据库中。
- 通过系统文件选择器选中的图片以 `content://` URI 保存。
- Markdown 中的图片由 Markwon 和 Glide 渲染。
- ZIP 导出会打包相关图片，并在导入时恢复到应用私有目录。

## 地图底图

地图选择器支持：

- 高德（默认，推荐在中国大陆使用）
- Google
- OpenStreetMap

高德底图使用 GCJ-02 坐标，应用在放置和拾取图钉时会进行 WGS-84 与 GCJ-02 互转。图钉默认优先定位到最近的 GPS 位置。

## 多语言资源

- `values/`：简体中文
- `values-zh-rHK/`：繁体中文（香港）
- `values-en/`：英文
- `values-ja/`：日文

## 仓库与反馈

- 源码仓库：[https://github.com/Jaffe2718/PetProfile](https://github.com/Jaffe2718/PetProfile)
- Issues：[https://github.com/Jaffe2718/PetProfile/issues](https://github.com/Jaffe2718/PetProfile/issues)

## 说明

- 应用为本地优先版本，暂不提供云端同步。
- 二维码转交要求两台设备处于同一局域网；二维码仅承载连接信息，完整档案树通过 TCP 传输。
