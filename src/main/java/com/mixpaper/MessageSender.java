package com.mixpaper;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageSender extends JFrame {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(MessageSender.class);
    private static String CSRF_TOKEN = "";

    private JTextArea otherUserIdsArea;
    private JTextArea messageArea;
    private JLabel userIdLabel;

    public MessageSender() {
        setTitle("消息发送器");
        setSize(800, 800);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // 用户状态显示
        JPanel userStatusPanel = new JPanel();
        userStatusPanel.setLayout(new BorderLayout());
        userIdLabel = new JLabel();
        try {
            if (BiliLogin.isLogin()) {
                String myUserId = BiliLogin.getUserIdFromCookie();
                userIdLabel.setText("已登录，用户ID: " + myUserId);
            } else {
                userIdLabel.setText("未登录，请扫描二维码登录");
            }
        } catch (IOException e) {
            logger.error("无法获取登录状态: {}", e.getMessage());
            userIdLabel.setText("无法获取登录状态");
        }
        userStatusPanel.add(userIdLabel, BorderLayout.NORTH);

        // 他人用户ID输入
        JPanel otherUserIdsPanel = new JPanel();
        otherUserIdsPanel.setLayout(new BorderLayout());
        otherUserIdsPanel.add(new JLabel("接收消息的B站用户ID（一行一个）:"), BorderLayout.NORTH);
        otherUserIdsArea = new JTextArea(5, 20);
        JScrollPane scrollPane = new JScrollPane(otherUserIdsArea);
        otherUserIdsPanel.add(scrollPane, BorderLayout.CENTER);

        // 消息输入
        JPanel messagePanel = new JPanel();
        messagePanel.setLayout(new BorderLayout());
        messagePanel.add(new JLabel("消息:"), BorderLayout.NORTH);
        messageArea = new JTextArea(5, 20);
        JScrollPane messageScrollPane = new JScrollPane(messageArea);
        messagePanel.add(messageScrollPane, BorderLayout.CENTER);

        // 包装所有输入的面板
        JPanel inputPanel = new JPanel();
        inputPanel.setLayout(new BoxLayout(inputPanel, BoxLayout.Y_AXIS));
        inputPanel.add(otherUserIdsPanel);
        inputPanel.add(messagePanel);

        // 确认按钮
        JButton sendButton = new JButton("确认发送");
        sendButton.addActionListener(new SendButtonListener());

        // 添加组件到窗口
        add(userStatusPanel, BorderLayout.NORTH);
        add(inputPanel, BorderLayout.CENTER);
        add(sendButton, BorderLayout.SOUTH);
    }


    private class SendButtonListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            String[] otherUserIds = otherUserIdsArea.getText().split("\\n");
            String message = messageArea.getText().trim();
            String myUserId = null;

            try {
                myUserId = BiliLogin.getUserIdFromCookie();
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(null, "获取用户ID失败！");
                return;
            }

            boolean allSuccess = true;
            for (String otherUserId : otherUserIds) {
                otherUserId = otherUserId.trim();
                if (!otherUserId.isEmpty()) {
                    boolean success = sendMessage(myUserId, otherUserId, message);
                    if (!success) {
                        allSuccess = false;
                    }
                }
            }

            if (allSuccess) {
                JOptionPane.showMessageDialog(null, "所有消息发送成功！");
            } else {
                JOptionPane.showMessageDialog(null, "部分消息发送失败，请检查日志！");
            }
        }
    }

    public boolean sendMessage(String myUserId, String otherUserId, String message) {
        String cookie = readCookieFile("cookie.txt");
        if (cookie == null) {
            JOptionPane.showMessageDialog(null, "无法读取cookie，消息发送失败！");
            return false;
        }

        try {
            URL url = new URL("https://api.vc.bilibili.com/web_im/v1/web_im/send_msg");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("User-Agent", "Apifox/1.0.0 (https://apifox.com)");
            connection.setRequestProperty("Accept", "*/*");
            connection.setRequestProperty("Host", "api.vc.bilibili.com");
            connection.setRequestProperty("Connection", "keep-alive");
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setRequestProperty("Cookie", cookie);

            connection.setDoOutput(true);

            if (CSRF_TOKEN.isEmpty()) {
                String pattern = "bili_jct=([^;]+)";
                Pattern r = Pattern.compile(pattern);
                Matcher m = r.matcher(cookie);
                if (m.find()) {
                    CSRF_TOKEN = m.group(1);
                } else {
                    logger.error("未找到 bili_jct 的值, cookie: {}", cookie);
                }
            }


            String params = String.format(
                    "msg[sender_uid]=%s" +
                            "&msg[receiver_id]=%s" +
                            "&msg[receiver_type]=1" +
                            "&msg[msg_type]=1" +
                            "&msg[msg_status]=0" +
                            "&msg[content]=%s" +
                            "&msg[timestamp]=%s" +
                            "&msg[new_face_version]=%s" +
                            "&msg[dev_id]=%s" +
                            "&from_firework=%s" +
                            "&build=%s" +
                            "&mobi_app=%s" +
                            "&csrf_token=%s" +
                            "&csrf=%s",
                    URLEncoder.encode(myUserId, StandardCharsets.UTF_8),
                    URLEncoder.encode(otherUserId, StandardCharsets.UTF_8),
                    URLEncoder.encode(String.format("{\"content\":\"%s\"}", message), StandardCharsets.UTF_8),
                    URLEncoder.encode("1742895064", StandardCharsets.UTF_8),
                    URLEncoder.encode("0", StandardCharsets.UTF_8),
                    URLEncoder.encode("B67B5AD4-679D-473E-BB75-F55949D5DA59", StandardCharsets.UTF_8),
                    URLEncoder.encode("0", StandardCharsets.UTF_8),
                    URLEncoder.encode("0", StandardCharsets.UTF_8),
                    URLEncoder.encode("web", StandardCharsets.UTF_8),
                    URLEncoder.encode(CSRF_TOKEN, StandardCharsets.UTF_8),
                    URLEncoder.encode(CSRF_TOKEN, StandardCharsets.UTF_8)
            );

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = params.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                logger.info("向用户[{}]发送消息[{}]成功！", otherUserId, message);
                return true;
            }
            return false;
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(null, "发送消息时发生错误: " + ex.getMessage());
            return false;
        }
    }

    private String readCookieFile(String filePath) {
        try {
            return java.nio.file.Files.readString(Path.of(filePath));
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "读取cookie文件时发生错误: " + e.getMessage());
            return null;
        }
    }
}
