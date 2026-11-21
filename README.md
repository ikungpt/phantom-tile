# 子进程限制开关

一个极简的原生 Android 快捷设置磁贴（Quick Settings Tile），一键切换开发者选项中的「停止限制子进程」（Phantom Process Killer）。

尤其适合在模拟器 / 虚拟机环境中使用：在模拟器里运行 Android 系统或 ROM 时，Phantom Process Killer 经常误杀后台子进程，导致终端、同步服务、后台任务无故中断；用这个磁贴点一下就能关掉限制，不用每次进开发者选项翻找。

## 功能

- 下拉控制中心，点一下磁贴即可在「不限制子进程」和「默认限制」之间切换
- 磁贴状态每次下拉时都会读取系统真实值同步显示，不会漂移
- 无桌面图标、无后台常驻、无任何权限声明、零第三方依赖
- 整包约 17KB（Release，R8 混淆 + 资源压缩后）

## 适用场景

- **模拟器 / 虚拟机**：在模拟器里跑系统镜像时，子进程限制会导致后台进程被系统杀掉，磁贴一键关闭，省事
- **Termux 等工具**：需要在后台常驻子进程的终端、同步、服务器类应用，不再被系统误杀
- **任何需要保活后台进程的场景**：不想被系统 32 个子进程上限卡住的时候

## 要求

- Android 12+（minSdk 31，targetSdk 35）
- Root（Magisk / KernelSU 等），通过 `su` 执行命令

## 原理

磁贴通过 `su` 读写系统设置：

读取状态：

```sh
su -c settings get global settings_enable_monitor_phantom_procs
```

值为 `false` 时表示已停止限制，磁贴高亮；`true` / `null` / 其他值表示默认限制中。

切换到「不限制子进程」时执行：

```sh
/system/bin/device_config set_sync_disabled_for_tests persistent
/system/bin/device_config put activity_manager max_phantom_processes 2147483647
settings put global settings_enable_monitor_phantom_procs false
```

切换回「默认限制」时执行：

```sh
/system/bin/device_config set_sync_disabled_for_tests none
/system/bin/device_config delete activity_manager max_phantom_processes
settings put global settings_enable_monitor_phantom_procs true
```

点击时先乐观更新磁贴 UI（触发系统原生过渡动画），再在后台线程执行命令，完成后重新读取真实状态校准；Root 授权失败时回退状态并 Toast 提示。

## 安装

从 [Releases](../../releases) 下载 APK 安装；首次点按磁贴时会请求 Root 授权。

然后在控制中心点「编辑」，把「子进程限制」磁贴加进来即可。

## 编译

```sh
./gradlew assembleRelease
```

产物在 `app/build/outputs/apk/release/app-release.apk`。

### 签名

Release 签名是可选的：复制 `keystore.properties.example` 为 `keystore.properties` 并填入自己的 keystore 信息后再编译，即可产出签名包。不提供该文件时编出的是未签名包。keystore 与密码请勿提交到仓库。

## 注意事项

- 开发者选项关闭时，系统可能会把 `settings_enable_monitor_phantom_procs` 重置回默认值；但 `max_phantom_processes=2147483647` 这层不受开发者选项开关影响，监控就算开着也不会杀进程。正确顺序是：先关开发者选项，再点一下磁贴；磁贴每次下拉都会读真实值，状态是准的。
- 关闭子进程限制可能让后台应用占用更多资源，请自行评估。

## 许可证

MIT，见 [LICENSE](LICENSE)。
