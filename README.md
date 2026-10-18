# 校园网守护 · 安卓版 v3.0.1

青海师范大学深澜 SRun 校园网的**断线自动重认证**工具。纯 Java、零第三方依赖、不用 Gradle/AndroidX，APK 约 158 KB（含全套图标资源）。

## 文档索引（先看哪个）

| 想干什么 | 看这个 |
|---|---|
| 装来日常用 | 本文件（安装 / 权限 / 三步上手） |
| 看图标/界面 | 任务切换器或桌面图标 |
| 判断"现在能不能放心用" | `质量结论.md`（37 个修复的分级 + 残留风险） |
| 验证重启 / 出校 / 长跑 | `验收清单.md`（步骤 + 判定标准 + 采集命令） |
| 查每次改了什么 | `CHANGELOG.md`（按编号逐条） |
| 改代码 / 重新打包 | 本文件「重新打包」+ `test/run.sh` |

## 它做什么

1. 每 N 秒（默认 60）探测真实连通性（3 个探测点，校验返回内容）
2. 判定确实断网后，直接向门户重新提交 SRun 认证（XXTEA + HMAC-MD5 + sha1 chksum）
3. 认证后复测，通了就停；连续失败按设置暂停（默认 5 次 / 10 分钟）
4. **只在已标记的校园网内动作**：换到别的网络（家里、热点）不会碰你的网

## 安装

1. 把 `dist/campus-guard-v3.0.1.apk` 拷到手机
2. 用「文件管理」打开它（**不要用 MT 管理器**，它默认没有安装权限）
3. 小米/HyperOS 会依次出现：允许安装来源 → ICP 未备案提示（选「继续安装」）→ 增强防护（右上角「⋮ → 单次安装授权」，需验证小米账号）

## 权限（共 8 个，没有定位、没有蓝牙、没有麦克风）

| 权限 | 用途 |
|---|---|
| INTERNET | 探测与认证请求 |
| ACCESS_NETWORK_STATE | 读取当前网络/网关 |
| POST_NOTIFICATIONS | 常驻通知（Android 13+ 运行时申请） |
| FOREGROUND_SERVICE(_SPECIAL_USE) | 常驻守护 |
| RECEIVE_BOOT_COMPLETED | 开机自启 |
| REQUEST_IGNORE_BATTERY_OPTIMIZATIONS | 「电源优化设置」按钮 |
| SCHEDULE_EXACT_ALARM | 无通知模式下的精确闹钟 |

> 说明：`ACCESS_FINE_LOCATION` 与 `NEARBY_WIFI_DEVICES` **已彻底移除**（早期为读 WiFi 名称申请过，实测系统不给读，改用网关判断后就不再需要）。

> 为什么没有定位权限：读 WiFi 名称（SSID）在 Android 13+ 需要定位 + 「附近的设备」，且本机实测**给了也读不到**（系统抛 SecurityException）。因此改用「网关网段」识别校园网，零敏感权限。

## 三步开始用

1. 连上校园网 WiFi
2. 点 **标记当前网络为校园网**（界面变成 `当前网络：校园网 ✓`）
3. 点 **开始守护**
4. **小米/红米用户还要做一步**：高级设置 →「自启动与省电设置」→ 打开「自启动」，否则开机后守护不会自己起来（实测 MIUI 会拦截开机广播）

> 换校区/换宿舍：在那个网络里再点一次「标记」。回家连家里的网，它显示「不是校园网（不会自动重连）」，不会乱动。

## 换学校 / 换校区怎么配

本工具只支持**深澜 SRun** 认证。换学校时改这四项（都在「账号与设置 / 高级设置」里）：

| 项 | 从哪来 |
|---|---|
| 账号 / 密码 | 学校给的校园网账号 |
| 认证服务器 IP | 连上校园网后，浏览器打开任意网页 → 跳转的登录页地址栏里的 IP（如 192.168.x.x） |
| 认证接口编号 | 一般保持 1；个别学校不同，问学校网络中心 |
| 校园网标记 | 在那个网络里点一次「标记当前网络为校园网」 |

如果登录页地址不是 `/cgi-bin/srun_portal`（比如带别的路径），当前版本不适用——那属于其他认证系统（锐捷 / Dr.COM / 802.1X）。

## 面板说明

- **状态卡**：状态灯、守护开关状态、最近检测与结果、当前网络判定
- **立即检测 / 立即重连 / 测试登录**：手动触发
- **开始守护 / 停止守护**：整条按钮，状态与通知同步
- **统计**：今日断网、累计检测、上次断网/恢复
- **账号与设置**：账号、密码（圆点遮罩）、认证服务器
- **高级设置**（默认收起）：检测间隔、连续失败几次后暂停、暂停多久、认证接口编号、常驻通知开关、保存、清除已识别校园网、电源优化、自启动与省电设置
- **运行日志**：默认最近 3 行，可「展开全部 / 复制 / 清空」

## 两种后台模式

| 模式 | 说明 |
|---|---|
| **常驻通知**（推荐） | 前台服务，最稳；通知栏常驻一条 |
| 无通知 | 走系统闹钟唤醒，不占通知栏；但小米可能压制，可靠性差 |

## 3.0.1 收尾清理

- **移除了预填的账号密码**：首次安装后需在「账号与设置」里填自己的校园网账号（老用户升级不受影响，已保存的凭据会保留）
- 移除了源码里的全部注释
- 历史版本的 APK 与备份文件已移入回收站

## 已知限制（诚实说明）

- **读不到 WiFi 名称**：Android 13+ 对第三方封了 `getSSID()`（本机两个权限都给仍抛 SecurityException），所以用网关网段识别
- **不能自动重连 WiFi**：普通应用无权开关/连接指定 WiFi（需 Shizuku 或 root）
- **只支持深澜 SRun**：锐捷 ePortal / Dr.COM / 802.1X / PPPoE 不适用
- 没有 `ipconfig /renew` 的等价物

## 交给别人用之前（清单）

1. **无需改凭据**：发布包不含任何账号密码（`Prefs.java` 中 `DEFAULT_USER` / `DEFAULT_PASS` 均为空串），对方首次使用时在 App 内填自己的校园网账号；凭据经 Keystore 加密后只存本机
2. **重新打包**：`sh build.sh`；升级同一台手机必须沿用同一份 `keystore.jks`，否则无法覆盖安装（需要先卸载）
3. **跑一遍单测**：`sh test/run.sh`（37+ 条断言，桌面 JVM，无需手机）
4. **确认对方学校是深澜 SRun**：登录页路径应为 `/cgi-bin/srun_portal`，否则本工具不适用
5. **提醒小米/红米用户**：装完要开「自启动」，并在高级设置里点一次「电源优化设置」，否则后台可能被系统杀掉

## 安全提示

本发布包**不含任何账号密码**：`Prefs.java` 里 `DEFAULT_USER` / `DEFAULT_PASS` 均为空串（早期自用版曾预填，3.0.1 已清除并在 APK 内核实）。首次使用需在「账号与设置」里自行填写；密码经 Android Keystore 加密后只存在本机，认证请求只发往你自己填写的校园网认证服务器。

## 重新打包

工具链（一次性）：

```sh
apt-get install -y --no-install-recommends openjdk-17-jdk-headless aapt zipalign apksigner android-sdk-build-tools
mkdir -p /opt/cng-toolchain
curl -o /tmp/platform-34.zip https://dl.google.com/android/repository/platform-34-ext7_r03.zip
curl -o /tmp/bt34.zip      https://dl.google.com/android/repository/build-tools_r34-linux.zip
python3 -c "import zipfile;open('/opt/cng-toolchain/android.jar','wb').write(zipfile.ZipFile('/tmp/platform-34.zip').read('android-34/android.jar'));open('/opt/cng-toolchain/d8.jar','wb').write(zipfile.ZipFile('/tmp/bt34.zip').read('android-14/lib/d8.jar'))"
```

打包：

```sh
sh build.sh      # 产物 dist/campus-guard-v3.0.1.apk
```

> 本机 /sdcard 是 FUSE 挂载，JVM 读不了源码与 jar，所以 build.sh 会把工程复制到 /tmp 编译，成品再拷回 dist/。升级必须沿用同一份 `keystore.jks`（口令 `campusguard`），否则无法覆盖安装。

## 签名说明

- build.sh 在缺少 keystore.jks 时会自动生成一个新密钥（口令 campusguard，仅作默认值）
- 若要产出能覆盖升级已安装版本的 APK，需要把原来那份 keystore.jks 放回工程根目录再打包
- 签名密钥不要提交到公开仓库（本发布包已排除）

## 文件结构

| 文件 | 作用 |
|---|---|
| `Srun.java` | 深澜客户端：XXTEA / 自定义 base64 / HMAC-MD5 / chksum |
| `Net.java` | 网络选择、网关、本机 IP、204 探测、TCP 探活 |
| `Guard.java` | 守护引擎：探测 → 重认证 → 复检 → 熔断 → 统计 |
| `GuardService.java` | 前台服务 + 网络事件 + 定时自检 |
| `AlarmScheduler.java` / `TickReceiver.java` | 无通知模式（单次精确闹钟 + 自动续期） |
| `MainActivity.java` | 主面板（纯代码布局，无资源文件） |
| `Prefs.java` | 设置 + Keystore 加密 + 统计持久化 |
| `Logs.java` | 环形日志 + 文件（256KB 轮转） |
| `BootReceiver.java` | 开机自启 |
| `build.sh` | 手工构建脚本 |

单测：`sh test/run.sh`（桌面 JVM，无需手机）。

质量结论（37 个修复的分级与残留风险）：见 `质量结论.md`。

验收清单（三条环境相关项，含判定标准与采集命令）：见 `验收清单.md`。

变更记录见 `CHANGELOG.md`。
