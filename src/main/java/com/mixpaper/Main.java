package com.mixpaper;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        try {
            BiliLogin.login();
        } catch (Exception e) {
            e.printStackTrace();
        }
        SwingUtilities.invokeLater(() -> {
            MessageSender sender = new MessageSender();
            sender.setVisible(true);
        });
    }
}