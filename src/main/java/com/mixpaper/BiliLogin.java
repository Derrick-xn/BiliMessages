package com.mixpaper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.*;
import com.google.zxing.client.j2se.*;
import com.google.zxing.common.*;
import com.google.zxing.qrcode.decoder.*;
import org.slf4j.Logger;

import javax.swing.*;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BiliLogin {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/97.0.4692.99 Safari/537.36 Edg/97.0.1072.69";
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(BiliLogin.class);
    private static String CK = "";

    private static ObjectMapper objectMapper = new ObjectMapper();

    public static String[] getLoginKeyAndLoginUrl() throws IOException {
        String url = "https://passport.bilibili.com/x/passport-login/web/qrcode/generate";
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", USER_AGENT);

        JsonNode responseJson = objectMapper.readTree(connection.getInputStream());
        String loginKey = responseJson.path("data").path("qrcode_key").asText();
        String loginUrl = responseJson.path("data").path("url").asText();

        connection.disconnect();
        return new String[]{loginKey, loginUrl};
    }

    public static void verifyLogin(String loginKey) throws IOException, InterruptedException {
        while (true) {
            String url = "https://passport.bilibili.com/x/passport-login/web/qrcode/poll?qrcode_key=" + loginKey;
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", USER_AGENT);

            JsonNode responseJson = objectMapper.readTree(connection.getInputStream());
            if (!responseJson.path("data").path("url").asText().isEmpty()) {
                List<String> setCookies = connection.getHeaderFields().get("Set-Cookie");
                Map<String, String> cookies = new HashMap<>();
                for (String cookie : setCookies) {
                    String[] kv = cookie.split(";", 2)[0].split("=", 2);
                    cookies.put(kv[0], kv[1]);
                }

                String filename = "cookie.txt";
                String cookieContent = "DedeUserID=" + cookies.get("DedeUserID") +
                        ";DedeUserID__ckMd5=" + cookies.get("DedeUserID__ckMd5") +
                        ";Expires=" + cookies.get("Expires") +
                        ";SESSDATA=" + cookies.get("SESSDATA") +
                        ";bili_jct=" + cookies.get("bili_jct") + ";";

                java.nio.file.Files.writeString(Path.of(filename), cookieContent);

                logger.info("扫码成功, cookie如下,已自动保存在当前目录下: {}, 文件: {}", filename, cookieContent);
                CK = cookieContent;
                break;
            }
            connection.disconnect();
            Thread.sleep(3000);
        }
    }

    public static boolean isLogin() throws IOException {
        // 检查 cookie.txt 文件是否存在
        Path cookiePath = Path.of("cookie.txt");
        if (!Files.exists(cookiePath)) {
            // 如果文件不存在，则返回 false
            return false;
        }

        // 如果 CK 是空的，读取 cookie.txt 文件中的内容
        if (CK.isEmpty()) {
            CK = Files.readString(cookiePath);
        }

        // 设置请求 URL
        String url = "https://api.bilibili.com/x/web-interface/nav";
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();

        // 设置请求方法和请求头
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Cookie", CK);

        // 读取响应并解析 JSON
        JsonNode responseJson = objectMapper.readTree(connection.getInputStream());
        boolean isLoggedIn = responseJson.path("code").asInt() == 0;

        // 断开连接
        connection.disconnect();

        // 返回登录状态
        return isLoggedIn;
    }

    public static void login() throws IOException, InterruptedException, WriterException {
        if (isLogin()) {
            logger.info("已经是登录状态了！");
            return;
        }

        JFrame frame = new JFrame("请使用手机B站扫码登录");

        while (true) {
            logger.info("正在获取登录二维码...");

            String[] loginInfo = getLoginKeyAndLoginUrl();
            String loginKey = loginInfo[0];
            String loginUrl = loginInfo[1];

            String qrCodePath = generateQRCode(loginUrl);

            SwingUtilities.invokeLater(() -> {
                frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                frame.setSize(250, 250);
                JLabel label = new JLabel(new ImageIcon(qrCodePath));
                frame.add(label);
                frame.setVisible(true);
            });

            logger.info("若依然无法扫描，请将以下链接复制到B站打开并确认(任意私信一个人,最好是B站官号，发送链接即可打开)");
            logger.info(loginUrl);

            verifyLogin(loginKey);

            if (isLogin()) {
                logger.info("登录成功！");
                frame.setVisible(false);
                frame.dispose();
                break;
            }
        }
    }

    public static String generateQRCode(String data) throws WriterException, IOException {
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L);
        BitMatrix bitMatrix = new MultiFormatWriter().encode(data, BarcodeFormat.QR_CODE, 200, 200, hints);
        Path path = FileSystems.getDefault().getPath("qrcode.png");
        MatrixToImageWriter.writeToPath(bitMatrix, "PNG", path);

        logger.info("二维码已生成: {}", path.toAbsolutePath());
        return path.toString(); // 返回二维码图片的路径
    }

    public static String getUserIdFromCookie() throws IOException {
        if (CK.isEmpty()) {
            CK = java.nio.file.Files.readString(Path.of("cookie.txt"));
        }
        Pattern pattern = Pattern.compile("DedeUserID=([^;]+);");
        Matcher matcher = pattern.matcher(CK);
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new IOException("无法从cookie中获取用户ID");
    }
}