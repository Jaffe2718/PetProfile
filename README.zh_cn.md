# PetProfile

[简体中文](README.zh_cn.md) | [English](README.md)

> 面向爬宠饲养玩家、繁育者和爬宠店家的本地优先 Android 应用。

PetProfile 是一款本地优先的 Android 应用，用于管理爬宠档案、个体的饲养记录、谱系（家族树）图、数值可视化、日常提醒通知、备份分享，以及设备间的二维码转交。所有数据都在设备本地运行，无需账号；只有在你需要地图瓦片、局域网转交，或可选的 OneDrive 备份时才会使用网络。

项目使用 Java 编写，通过 Android Studio 和 Gradle 构建。

## 亮点

- **档案管理** —— 新建、编辑、删除档案；按昵称或任意生物分类层级搜索；按日期范围、性别、状态（记录中/已归档）、生物分类多级筛选；列表与谱系两种视图；可展开的档案属性表。
- **记录** —— 建立 / 日常 / 转交 / 归档四类记录，每条都有必填标题、时间、地图选点、自定义数值与标签字段、Markdown 笔记和图片。
- **谱系图** —— 基于代目的家族树布局，头像按性别显示形状，绘制交配线并防止回环，方便血统溯源。
- **可视化** —— 数值型记录字段可绘制折线图。
- **日常提醒** —— 每个档案可设置喂食 / 换水 / 清洁等提醒，支持每周多次或一次性安排、完成策略（略过 / 顺延）以及系统通知。
- **日常待办** —— 专门页面展示当天的任务，区分已至 / 未至，支持按宠物筛选、搜索与完成状态的记录。
- **备份与分享** —— ZIP 导入/导出、PNG 长图分享，以及二维码 + 同一局域网 TCP 转交完整档案树（含先祖与图片）。
- **云端备份（OneDrive）** —— 可选地把整个数据库增量同步到登录用户自己的 OneDrive（应用专属文件夹），只传输变动部分，微软登录在设备端完成。
- **饲养者信息** —— 全局饲养者档案（昵称 + 常驻地），自动填充转交与归档的元数据，并在页面内提供 OneDrive 登录。
- **多语言** —— 简体中文、繁体中文（香港）、英文、日文。

## 功能

### 档案

- 界门纲目科属种/亚种分类字段（每级可选），加必填昵称和可选性别（雄 / 雌 / 未知）。
- 每个档案一个头像；一个唯一、不可变的短 ID（base64 编码），在备份、恢复与转交后保持稳定。
- 各自的档案属性与记录属性，既可添数值型（字段名 / 数值 / 单位），也可添文本/标签型（字段名 / 描述）。
- 父母本选择：父本必须为雄、母本必须为雌；界门纲目科必须一致；子女不能是自身亲本的先祖（家族树回环校验）；亲本的建档时间必须早于子女。
- 管理列表条目可展开属性表。

### 记录

- 四类记录：**建立记录**（必有、最早、唯一）、**日常记录**、**转交记录**、**归档记录**（最多一条，且必须最晚）。
- 建立记录原因：繁育 / 野采 / 购入；归档记录原因：死亡 / 转出。
- 转交记录包含原饲养者、新饲养者、转出地点、转入地点；地点由地图选点，并以地址 + 度分秒坐标保存。
- 每条记录都有必填标题、时间、可选地图选点、自定义字段、Markdown 笔记和图片。
- 记录顺序约束得到保证：一个档案以一条建立记录开头，最多以一条归档记录结尾。
- 新建记录时自定义字段会从上一条记录预填，而笔记、图片、时间、定位留空由你填写。

### 谱系视图

- 基于代目的网格布局；头像按性别呈现形状（正方形 = 雄、圆形 = 雌、六边形 = 未知）。
- 头像下方显示昵称；点击头像进入其记录页面。
- 同胞群体保持连续；交配线连接父母双方，分支节点以小圆点标出。
- 无亲缘关系的家族树并排独立绘制，不产生多余连线。
- 空白区域拖动（平移）画布以便查看大型族谱。

### 日常提醒与通知

- 每个档案可设置多条日常提醒（喂食 / 换水 / 清洁等）。
- 每条提醒有标题、每周多次（Sun–Sat 多选）或一次性（日期/时间）、具体时间，以及纯文本详情。
- 用开关控制每条提醒的启停；完成策略二选一：**略过**（过期即解除）或 **顺延**（一直保留到完成）。
- 通知仅对未归档档案生效，静音、自动成组，并在任务完成或移除前持续保留。
- 为保证在后台、锁屏或上滑退出后仍能准时提醒，请允许通知权限，并在多数设备上开启自启动和后台/电池无限制访问。

### 日常待办

- 从档案页面右上角的铃铛图标进入，查看当天的任务。
- 每张卡片显示宠物头像、提醒标题、昵称 + 性别符号、生物分类、详情和时间。
- 复选框标记完成（可恢复）；只有已到时间的任务才能勾选。
- 未至任务为绿色且无复选框，已至任务为橙色，已完成任务为灰色。
- 支持搜索、筛选（起止时间、已完成/未完成、按宠物并显示头像）与排序。
- 待办由各档案的提醒在本地生成，不随 ZIP 等途径导出/导入，每次进入应用都会刷新。

### 备份、分享与转交

- ZIP 导出/导入覆盖整个数据库：档案、记录、属性、图片与饲养者信息。磁盘上已缺失的图片文件会被跳过，不会导致导出整体失败。
- PNG 长图分享：顶部为档案卡片，下方逐条渲染记录，包含属性字段与 Markdown 笔记。
- 二维码 + 同一局域网转交：二维码只承载连接信息，完整档案树（含先祖与图片）通过 TCP 传输。
- 转交时会新增一条归档（转出）记录，本地不存在的先祖档案也会补一条转出归档，便于血统溯源。

### 云端备份 / 恢复（OneDrive）

**更多**页面与**饲养者信息**页面都提供「上传到云端 / 从云端同步」，把整个数据库备份到登录用户自己的 OneDrive，并可从那里恢复。

- 微软登录采用设备端的授权码 + PKCE 流程（无服务端、无 client secret），因此**不需要** Google 那种 OAuth 同意校验。你需在 Azure 一次性注册应用（一个 client ID、一条移动/桌面重定向 URI、`Files.ReadWrite.AppFolder` 委派权限），再把 client ID 填入 `app/build.gradle` 的 `def msalClientId = ...`。用户在 App 内登录自己的 Microsoft 账户即可。
- 云端存放的是**解包结构**而非单个 ZIP：`data.json` + `images/<文件名>`，位于应用的 OneDrive 专属文件夹（`/me/drive/special/approot`），不占用用户可见空间。因为图片文件名永久不变，同步只需比对文件名与大小/SHA-1，因此**只传输变动部分**：
  - 上传（最新覆盖）：`data.json` 始终更新；图片仅在云端缺失或内容不同（大小 + SHA-1）时上传；云端已不再被引用的图片会被清理。上传顺序为「先图片、后 data.json、最后清理」，因此云端永远不会出现引用了不存在图片的 `data.json`，中断也不会破坏云端已有备份。
  - 下载（增量补齐、可中断续传）：**先取 `data.json` 并立即入库**，记录马上就能看到；然后**逐个文件**判断，名字 + 大小 + SHA-1 一致就跳过，否则下载并直接落盘。单个文件失败**不影响其它文件**，失败的文件下次同步会继续补齐。单个文件传输**停顿 5 分钟**即判定该文件失败（连接阶段上限 30 秒），且同步过程不会阻塞界面读取数据。恢复没有确认对话框，也不需要本地回档点——备份/回滚统一走 ZIP 导出与导入。
  - 恢复的合并范围：备份中包含的档案按 id 替换，且**备份带来的内容优先**（记录、记录字段与图片、日常提醒、自定义属性、亲缘关系）；备份**没有**的内容一律保留 —— 上次上传之后本地新增的记录、字段、图片、提醒、属性、亲缘关系都不会被恢复清掉，恢复不会摧毁从未上传过的本地工作，同一档案也不会出现两条建档/归档记录。备份里没有的档案保持原样。ZIP 导入则相反：对归档中包含的档案按原样重建，这正是它能充当回滚手段的原因。
  - 图片允许**空引用**：引用已经指向最终路径，只是文件尚未到位，界面直接不渲染该图（列表/详情/谱系头像留空，Markdown 按缺图处理）；图片下载完成后同一条引用自动生效，无需改数据库。ZIP 导出与局域网转交遇到缺失图片会跳过该图并继续，不会整体失败。
- 访问令牌过期后会自动用 refresh token 静默续期，用户无需反复登录；只有 refresh token 失效（被撤销等）时才需要重新登录，届时会提示「OneDrive 登录已失效，请重新登录」。
- 恢复完成后会广播刷新前台数据页（档案列表、记录列表与详情、日常待办、图表）。
- 退出登录（**饲养者信息**页的「退出登录」）会先弹确认框：退出只清除本机保存的令牌，云端已有备份不受影响。
- 旧版本（≤ 0.3.0）上传的单个 `pet-profile-backup.zip` 仍可被识别并恢复；一旦下一次上传成功，该旧文件会被自动清除。
- 上传/下载进行中时，按钮会全局保持禁用（离开页面也不会失效），再点会提示「正在同步，请稍后」；通知栏会显示 `x/y` 的文件进度（需通知权限），同步结束、或同步中被杀后再次启动 App 时都会自动清除该通知。
- 「检查更新」按钮会查询 GitHub 最新 release（`v{x}.{y}.{z}`），若高于当前版本，则提供从 `https://github.com/Jaffe2718/PetProfile/releases/download/v{x}.{y}.{z}/petprofile.apk` 下载。

### 饲养者信息

- 全局昵称与常驻地（地图选点，地址 + 度分秒）。
- **常驻地**按钮带房子图标：单击用导航软件打开该坐标，长按重新选点。
- 页面内提供 OneDrive 登录、备份与恢复入口。
- 用于自动填充转交与归档的饲养者姓名、地点，并通过 ZIP 导入/导出共享。

## 地图底图

位置选择器支持高德（默认，推荐中国大陆使用）、Google 与 OpenStreetMap。高德使用 GCJ-02 坐标，应用在放置和拾取图钉时进行 WGS-84 与 GCJ-02 互转。图钉默认优先定位到最近的 GPS 位置；选择的地点以可读地址 + 度分秒坐标保存（南纬 / 西经为负值）。

## 多语言资源

- `values/`：简体中文
- `values-zh-rHK/`：繁体中文（香港）
- `values-en/`：英文
- `values-ja/`：日文

## 技术栈

- Android：Java 17
- UI：AndroidX、Material Components、RecyclerView
- 本地数据库：Room
- 图片加载：Glide（存储于应用私有目录）
- Markdown 渲染：Markwon
- 二维码生成：ZXing
- 二维码扫描：CameraX + ML Kit Barcode Scanning
- 地图瓦片：高德 / Google Maps / OpenStreetMap
- 云端备份：Microsoft Graph（OneDrive）+ 授权码/PKCE OAuth

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
- `targetSdk`：37
- `compileSdk`：37
- Android Gradle Plugin：8.13.2（Gradle 8.13）
- Java 兼容版本：17

release 产物输出为 `app/build/outputs/apk/release/petprofile.apk`，与 App 内「检查更新」从 GitHub release 下载的文件名一致。

## 权限

- `CAMERA` —— 扫描转交二维码。
- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` —— 地图选点与 GPS。
- `INTERNET` —— 地图瓦片、局域网转交、检查更新，以及 OneDrive 备份。
- `POST_NOTIFICATIONS` —— 日常提醒。
- `RECEIVE_BOOT_COMPLETED` —— 系统重启后重新调度提醒。

## MCP（Model Context Protocol）

PetProfile 提供一个本地 Model Context Protocol 服务，让同一局域网的 AI Agent 可以读取和管理 App 数据。

### 开启

1. 在 App 内打开 **关于 → MCP**。
2. 打开**开关**。这会启动一个前台服务，让 MCP 服务在后台、锁屏下持续运行（是否能在“划退”后存活取决于机型/后台白名单）。
3. 从弹窗复制 **服务地址**（`http://<手机IP>:18999/petprofile`）与**授权 Key**。

### 连接 Agent

用 `streamable-http` 类型配置 MCP 客户端：

```json
{
  "type": "streamable-http",
  "url": "http://x.x.x.x:18999/petprofile",
  "headers": {
    "Authorization": "Bearer <你的Key>"
  }
}
```

要求：手机与 Agent 处于同一局域网，且 App 保持运行（开关开启）。Key 是自动生成的，用 App 里显示的那个（点击“刷新”会更换）。

### 工具

服务通过 JSON-RPC（`initialize` / `tools/list` / `tools/call`）暴露以下工具：

**读** —— `list_profiles`、`search_profiles`、`get_profile`、`get_profile_family`、`list_records`、`get_record`、`get_record_timeseries`、`list_routines`、`get_daily_todo`、`get_keeper_info`、`get_stats`、`export_json`、`get_app_version`。

**写** —— `create_profile`、`update_profile`、`delete_profile`、`set_profile_parents`、`set_profile_custom_fields`、`create_record`、`update_record`、`delete_record`、`create_routine`、`update_routine`、`delete_routine`、`complete_routine`、`save_keeper_info`、`export_zip`、`import_zip`。

**OneDrive 云端备份** —— `is_onedrive_connected`、`upload_to_onedrive`、`download_onedrive`、`get_onedrive_result`。**不开放登录工具**：用户需在设备上登录 Microsoft（饲养者信息 → 登录 OneDrive）。`upload_to_onedrive` / `download_onedrive` 先返回 `{"status":"started"}` 并把上一次结果重置为 `pending`，结果通过 `get_onedrive_result` 获取；访问令牌过期会自动静默续期，只有 refresh token 失效时才需要用户重新登录。`download_onedrive` 是**增量补齐**：先入库 `data.json`、再逐文件比对（名字 + 大小 + SHA-1，一致的跳过），单文件失败不影响其它文件，重复调用即可继续补齐。注意：备份中包含的档案按 id 替换，同一项以备份为准；而备份没有带来的内容（上次上传之后本地新增的记录、提醒、字段、图片、亲缘关系）会合并保留，不会被丢弃。

图片随档案/记录操作一起处理，而非单独导入：`create_record`/`update_record` 接受 `images` 数组，`create_profile`/`update_profile` 接受 `avatarData`。单张图片可以给 `uri`（content:/file:）或 base64 `data`（可带 `extension`/`mimeType`），并存入应用私有目录。`update_record` 还能用 `imagesMode`（`append`/`replace`，默认 `replace`）决定追加还是覆盖，用 `removeImages`（数组内填图片 id，通过 `get_record` 读取）删除指定图片。Markdown 支持内联 `![alt](data:image/...;base64,....)` 图片，会自动解码并改写为私有 `file://` URI。`export_zip` 返回 base64 `data`（或写到 `targetUri`），`import_zip` 接受 base64 `data`（或 `uri`）。

写操作成功后，前台数据页（档案列表、记录列表、日常待办、记录详情、图表）会自动刷新，且不会关闭已打开的弹窗。

> 注意：`import_zip` 会整体替换数据库（破坏性）。

## 数据与图片

- 所有数据通过 Room 保存在本地数据库中。
- 图片会复制到应用私有目录（`files/images/`），即使原图从相册删除，记录依然完整；Markdown 中的图片引用会改写为这些文件。
- ZIP 导出会打包相关图片，并在导入时恢复到应用私有目录；唯一文件名避免跨设备冲突。引用存在但文件缺失时，`data.json` 里的引用会保留、打包时跳过该图，所以「等待云端补齐图片」的库依然可以导出。
- 应用为本地优先版本；可选的 OneDrive 备份会自动把数据库备份/恢复到你的 OneDrive，但即使不用它，应用也完全离线可用。

## 仓库与反馈

- 源码仓库：[https://github.com/Jaffe2718/PetProfile](https://github.com/Jaffe2718/PetProfile)
- Issues：[https://github.com/Jaffe2718/PetProfile/issues](https://github.com/Jaffe2718/PetProfile/issues)