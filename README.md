# android-sos-power-key

把没有电源键的手机上的 **SOS 键**改成标准安卓电源键，把**挂机键**改成功能机风格的挂断键。

Turn the **SOS key** of a phone that has no power button into a standard Android power key, and turn the red **hang-up key** into an end-call key (Magisk module + LSPosed module).

在 **百合 BIHEE A89**（展锐 UMS9157，Android 13）上开发和测试。其他机型需要自行适配，见[适配其他机型](#适配其他机型)。

## 效果

| 按键 | 短按（亮屏） | 熄屏时按 | 长按 | 通话中 |
|---|---|---|---|---|
| **SOS** | 熄屏（桌面、应用、锁屏页都一样） | 亮屏到锁屏页 | 关机 / 重启菜单 | 熄屏（安卓默认） |
| **挂机键** | 在应用里回桌面；在桌面上熄屏 | 只亮屏 | 关机 / 重启菜单 | 挂断电话 |

> 原来的 SOS 紧急求救功能会被替换，不再可用。

## 为什么需要两个模块

这台手机的固件对电源键做了定制：只有「顶层应用是原厂桌面」时才执行标准的短按 / 长按逻辑，否则电源键只是回桌面；锁屏页按电源键会直接解锁进桌面。所以光改按键映射不够。

| 部分 | 类型 | 作用 |
|---|---|---|
| [`magisk/`](magisk) | Magisk 模块 | 按键映射：SOS（`sprd-keypad` 扫描码 61）→ `POWER`，挂机键（`gpio-keys` 扫描码 116）→ `ENDCALL` |
| [`lsposed/`](lsposed) | LSPosed 模块（作用域：系统框架） | 去掉厂商的两个限制：「只认原厂桌面」和「锁屏页按电源键直接解锁」；过滤熄屏唤醒时键盘误报的音量键（见[已知硬件问题](#已知硬件问题)） |

两个都要装。只装 Magisk 模块，SOS 在桌面上只会回桌面；只装 LSPosed 模块，按键映射没变，什么都不会发生。

## 安装

需要：已 root，Magisk，LSPosed（Zygisk 版），电脑上的 `adb`。

### 方式一：下载预构建文件（[Releases](../../releases)）

| 文件 | 说明 |
|---|---|
| `sos_as_power.zip` | Magisk 模块。**由 BIHEE A89 当前固件的按键布局文件生成，只适用于同一固件**；其他固件请用方式二自己生成 |
| `a89-power-fix.apk` | LSPosed 模块。使用本地临时生成的调试签名，不是应用商店签名 |

```sh
adb push sos_as_power.zip /data/local/tmp/
adb shell su -c "magisk --install-module /data/local/tmp/sos_as_power.zip"
adb install --no-incremental -r a89-power-fix.apk
```

然后跳到下面第 3 步。

### 方式二：自己构建
1. 构建并安装 Magisk 模块（手机连电脑，已授权 adb 和 root）：
   ```sh
   magisk/build.sh                      # 从手机读取 keylayout 文件，生成 magisk/build/sos_as_power.zip
   adb push magisk/build/sos_as_power.zip /data/local/tmp/
   adb shell su -c "magisk --install-module /data/local/tmp/sos_as_power.zip"
   ```
2. 构建并安装 LSPosed 模块：
   ```sh
   lsposed/build.sh                     # 需要 JDK 11+ 和 Android SDK，输出 lsposed/build/a89-power-fix.apk
   adb install --no-incremental -r lsposed/build/a89-power-fix.apk
   ```
   务必加 `--no-incremental`：增量安装的 APK 在开机时 LSPosed 读不到，模块不会加载。
3. 在 LSPosed 管理器里启用「A89 电源键修复」，**作用域只勾「系统框架」**。
4. 把挂机键的短按行为设为「先回桌面再熄屏」（可选，也可以是 `1` 只回桌面、`2` 只熄屏）：
   ```sh
   adb shell settings put system end_button_behavior 3
   ```
5. 重启。

## 卸载 / 救砖

- 在 Magisk 里删除 `sos_as_power`，在 LSPosed 里关闭或卸载模块，重启。
- 命令行删除 Magisk 模块：`adb shell su -c "touch /data/adb/modules/sos_as_power/remove"`，然后重启。
- 开不了机：开机动画时连按三下音量减（需要「音量键救砖」之类的 Magisk 模块）；或进 Magisk 安全模式。

## 已知硬件问题

手机进入深度休眠后，按 SOS 键唤醒的那一下，键盘（`sprd-keypad`）会在上报 `KEY_F3` 的同时，多上报一对 `KEY_VOLUMEUP` + `KEY_VOLUMEDOWN`，三个事件相隔只有几十微秒，结果是每次唤醒都可能弹出音量条。在 `getevent` 这一层（安卓之下）就能看到，所以来自内核驱动或键盘硬件，不是本项目引入的。只在深度休眠唤醒时出现，插着 USB 充电时一般复现不了。

LSPosed 模块在分发按键时过滤它：音量键的按下时间和电源键相差 50 毫秒以内，就判定为误报并丢弃；正常按音量键不受影响。三个键到达系统的顺序是固定的（音量加、音量减、电源键），音量键可能在电源键之前就被分发，所以刚收到的音量键会先等最多 50 毫秒（从系统收到的时刻算起，因为休眠唤醒后事件会晚到 60～100 毫秒），等电源键到了再判断。这是一个系统层的绕过办法，没有修复内核驱动。

## 已知限制

- 挂机键在通话中挂断：按代码逻辑可以挂断，但**还没有用普通号码的真实通话验证**。紧急号码（如 112）无论如何都不能用按键挂断，这是 Android 的固定规则，用它测不出结果。
- 没有 SIM 卡时只能拨紧急号码。
- 固件 OTA 升级后，LSPosed 里被 hook 的方法可能变化，需要重新适配；Magisk 模块建议重新运行 `magisk/build.sh` 生成。

## 适配其他机型

1. 找到 SOS 键的扫描码和所属设备：`adb shell su -c "getevent -lp"`，再 `getevent -l` 按一下键。
2. 看该设备用哪个按键布局文件：`adb shell su -c "dumpsys input" | grep KeyLayoutFile`。
3. 修改 `magisk/build.sh` 里的设备名、扫描码、布局文件。
4. LSPosed 部分针对的是展锐固件的 `PhoneWindowManager`：
   - `getRunningActivityName()`：厂商加的「是不是原厂桌面」判断，返回值里的类名是 `com.sprd.simple.launcher.Launcher`；
   - `interceptKeyBeforeQueueing` 里电源键分支调用 `isKeyguardShowing()` 的位置。

   其他厂商要先反编译 `/system/framework/services.jar`，确认是否有类似的限制，没有的话只需要 Magisk 模块。

## 许可

MIT，见 [LICENSE](LICENSE)。
