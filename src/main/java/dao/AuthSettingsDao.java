package dao;

import config.SystemConfig;

import java.sql.*;

public class AuthSettingsDao {
    private SystemConfig systemConfig;

    public AuthSettingsDao() {
        try {
            systemConfig = SystemConfig.getInstance();
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public String selectFaceSdkLicenseKey() {
        String sql = "SELECT faceSdkLicenseKey FROM auth_settings LIMIT 1";
        String licenseKey = null;

        try (Connection conn = DriverManager.getConnection(
                systemConfig.getDbUrl(),
                systemConfig.getDbUsername(),
                systemConfig.getDbPassword());
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                licenseKey = rs.getString("faceSdkLicenseKey");
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return licenseKey;
    }

    public Boolean updateFaceSdkLicenseKey(String newLicenseKey) {
        String sql = "UPDATE auth_settings SET faceSdkLicenseKey = ?";

        try (Connection conn = DriverManager.getConnection(
                systemConfig.getDbUrl(),
                systemConfig.getDbUsername(),
                systemConfig.getDbPassword());
             PreparedStatement ps = conn.prepareStatement(sql)) {

            // 设置参数
            ps.setString(1, newLicenseKey);

            // 执行更新
            int affectedRows = ps.executeUpdate();

            // 如果更新到了一行数据，返回 true，否则 false
            return affectedRows == 1;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}
