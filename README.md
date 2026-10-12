# 进阶训练

一款简体中文、离线使用的 Android 训练记录应用，内置每周四次的 12 周上/下肢训练计划。适合用来安排训练、逐组记录表现并查看进展。

## 功能

- 按日安排训练或恢复，支持换日、补练、增减组数和替换动作。
- 逐组记录重量、次数与剩余次数，自动带入同动作上次重量，并提供组间计时。
- 每日打卡记录体重和精力；查看训练历史、完成组数、训练量、动作最高重量与体重周均值。
- 将数据导出为本机 JSON 备份，也可恢复；支持每日提醒。
- 导入独立的训练计划 JSON，调整日期安排、逐组建议和动作替换，不覆盖已有训练记录。

应用不需要账号，训练记录保存在设备上的 Room 数据库中，没有广告或云端同步。

## 构建与安装

需要 JDK 17、Android SDK Platform 35 和 Build Tools 35.0.0。可在 Android Studio 中打开项目，或配置 `JAVA_HOME` 与 `ANDROID_HOME` 后运行：

```powershell
.\gradlew.bat :app:assembleDebug
```

macOS 或 Linux 使用 `./gradlew :app:assembleDebug`。调试版 APK 位于 `app/build/outputs/apk/debug/`，支持 Android 8.0 或更高版本。

项目使用 Kotlin、Jetpack Compose、Room 和 DataStore。发布签名密钥不包含在仓库中；自行构建可分发的发布版时需要配置自己的签名。

## 数据与备份

重装应用或清除应用数据前，请先在「设置与数据」导出 JSON 备份。完整恢复会替换设备上的现有记录；导入训练计划则只调整计划内容，已有训练日期会跳过。

## 项目结构
- `app/src/main/java/com/jinjie/training/`：界面、训练计划、数据存储和提醒逻辑。
- `app/src/test/`：训练计划相关测试。
- `app/schemas/`：Room 数据库结构版本。

内置训练计划是通用起点。根据自己的经验、动作质量和恢复情况调整重量与训练量；如有健康问题，请先咨询专业人士。


