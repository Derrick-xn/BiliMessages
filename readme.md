# 使用方式
1. 运行工具
2. 根据提示使用手机端B站扫码登录
3. 输入接收消息的用户的UID（每一行一个UID，注意不要多输入空格等字符）
4. 输入需要批量发送的消息，点击发送

# 多开
1. 新建一个其他的路径
2. 复制工具到其他路径中打开
3. 删除cookie.txt文件
4. 用其他B站帐号扫码登录

# 打包成.exe文件
1. JDK14以上，Windows需要安装wix3.0及以上版本
2. 执行命令jpackage --input D:\DevelopWorkSpace\BiliMessages\target\ --name BiliMessages --main-jar BiliMessages-1.0-SNAPSHOT.jar --main-class com.mixpaper.Main