import java.sql.*;
public class JdbcProbe {
    public static void main(String[] a) throws Exception {
        String url = "jdbc:mysql://127.0.0.1:3306/fastrag?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";
        try (Connection c = DriverManager.getConnection(url, "root", "123456")) {
            try (PreparedStatement ps = c.prepareStatement(
                "DELETE FROM widget WHERE creator='system' AND name IN (?, ?)")) {
                ps.setString(1, "\u64cd\u4f5c\u786e\u8ba4\u63d0\u793a\u5361");
                ps.setString(2, "\u4e0b\u8f7d\u6587\u4ef6\u5361");
                System.out.println("deleted builtin rows: " + ps.executeUpdate());
            }
            ResultSet rs = c.createStatement().executeQuery(
                "SELECT name FROM widget WHERE creator='system' ORDER BY created_at");
            while (rs.next()) System.out.println("builtin: " + rs.getString(1));
        }
    }
}
