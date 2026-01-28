package server;

import config.SystemConfig;
import entity.db.User;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserService {
    private SystemConfig systemConfig;

    public UserService() {
        try {
            systemConfig = SystemConfig.getInstance();
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    // 查询用户
    public User getUserByUserName(String userName) {
        if(userName == null || userName.trim().isEmpty()){
            return null;
        }
        String sql = "SELECT * FROM user WHERE userName = ?";
        try (Connection conn = DriverManager.getConnection(systemConfig.getDbUrl(), systemConfig.getDbUsername(), systemConfig.getDbPassword());
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, userName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    User u = new User();
                    u.setId(rs.getLong("id"));
                    u.setName(rs.getString("name"));
                    u.setUserName(rs.getString("userName"));
                    u.setGender(rs.getString("gender"));
                    u.setCreatedAt(rs.getTimestamp("createdAt"));
                    u.setActive("true".equalsIgnoreCase(rs.getString("active")));
                    u.setRole(rs.getString("role"));
                    u.setCardInfo(rs.getString("cardInfo"));
                    u.setFingerPrintInfo(rs.getString("fingerPrintInfo"));
                    u.setFaceInfo(rs.getString("faceInfo"));
                    u.setPassWord(rs.getString("passWord"));
                    u.setSrttings(rs.getString("srttings"));
                    u.setOpenId(rs.getString("openId"));
                    return u;
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<User> getAllUsers() {
        List<User> users = new ArrayList<>();
        String sql = "SELECT * FROM user";

        try (Connection conn = DriverManager.getConnection(systemConfig.getDbUrl(), systemConfig.getDbUsername(), systemConfig.getDbPassword());
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                User u = new User();
                u.setId(rs.getLong("id"));
                u.setName(rs.getString("name"));
                u.setUserName(rs.getString("userName"));
                u.setGender(rs.getString("gender"));
                u.setCreatedAt(rs.getTimestamp("createdAt"));
                u.setActive("true".equalsIgnoreCase(rs.getString("active")));
                u.setRole(rs.getString("role"));
                u.setCardInfo(rs.getString("cardInfo"));
                u.setFingerPrintInfo(rs.getString("fingerPrintInfo"));
                u.setFaceInfo(rs.getString("faceInfo"));
                u.setPassWord(rs.getString("passWord"));
                u.setSrttings(rs.getString("srttings"));
                u.setOpenId(rs.getString("openId"));

                users.add(u);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return users;
    }

    // 保存用户
    public boolean saveUser(User user) {
        String sql = "INSERT INTO user(name, userName, gender, createdAt, active, role, cardInfo, fingerPrintInfo, faceInfo, passWord, srttings, openId) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(systemConfig.getDbUrl(), systemConfig.getDbUsername(), systemConfig.getDbPassword());
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, user.getName());
            ps.setString(2, user.getUserName());
            ps.setString(3, user.getGender());
            java.util.Date createdAt = user.getCreatedAt() != null ? user.getCreatedAt() : new java.util.Date();
            ps.setDate(4, new java.sql.Date(createdAt.getTime()));
            ps.setString(6, user.getRole());
            ps.setString(7, user.getCardInfo());
            ps.setString(8, user.getFingerPrintInfo());
            ps.setString(9, user.getFaceInfo());
            ps.setString(10, user.getPassWord());
            ps.setString(11, user.getSrttings());
            ps.setString(12, user.getOpenId());

            int rows = ps.executeUpdate();
            if (rows > 0) {
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        user.setId(keys.getLong(1));
                    }
                }
                return true;
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 获取人脸id和完整图片路径
     *
     * @return
     */
    public Map<String, String> getUserIdWithFacePath() {
        Map<String, String> map = new HashMap<>();
        String sql = "SELECT userName, faceInfo FROM user";

        try (Connection conn = DriverManager.getConnection(systemConfig.getDbUrl(), systemConfig.getDbUsername(), systemConfig.getDbPassword());
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String userName = rs.getString("userName");
                String faceInfo = rs.getString("faceInfo");
                if (rs.wasNull()) {
                    continue;
                }
                if (faceInfo == null || faceInfo.trim().isEmpty()) {
                    continue;
                }
                // 提取文件名（带后缀）
                String fileName = faceInfo.substring(faceInfo.lastIndexOf("/") + 1);
                // 拼接完整路径
                String fullPath = systemConfig.getFaceImagePath() + "\\" + fileName;
                map.put(userName, fullPath);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return map;
    }
}
