import java.sql.*;
public class VerifyIterations {
    public static void main(String[] a) throws Exception {
        Connection c = DriverManager.getConnection("jdbc:mysql://127.0.0.1:3306/fastrag?useSSL=false&allowPublicKeyRetrieval=true", "root", "123456");
        ResultSet rs = c.createStatement().executeQuery("SHOW COLUMNS FROM app_conversation_message LIKE 'iterations'");
        boolean any = false;
        while (rs.next()) { any = true; System.out.println("iterations: " + rs.getString("Type") + " null=" + rs.getString("Null")); }
        if (!any) System.out.println("[MISSING] iterations 列不存在");
    }
}
