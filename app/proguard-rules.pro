# 本工程的混淆规则。当前 release 未开启 minifyEnabled，
# 这份文件先保留常用条目，将来开启混淆时不用从零写。

# 无障碍服务由系统按类名实例化，必须保留。
-keep class * extends android.accessibilityservice.AccessibilityService { *; }

# 出口 Activity 由 AndroidManifest 声明，保留。
-keep class com.elomary.wxautovideocall.ui.** { *; }

# 数据模型会被 SharedPreferences 的 JSON 序列化/反序列化用到，字段名不能改。
-keep class com.elomary.wxautovideocall.data.** { *; }
