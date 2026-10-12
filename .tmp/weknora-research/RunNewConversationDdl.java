import java.sql.*;

/**
 * new-conversation DDL 直连执行器（幂等，可重复跑）。
 * 与 SchemaInitializer 的启动时增补语句逐一对应——此脚本只是把同一批 DDL 提前执行并验证。
 */
public class RunNewConversationDdl {
    public static void main(String[] args) throws Exception {
        String user = args.length > 0 ? args[0] : "root";
        String pass = args.length > 1 ? args[1] : "";
        String url = "jdbc:mysql://127.0.0.1:3306/fastrag?useUnicode=true&characterEncoding=UTF-8"
                + "&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";
        Connection conn;
        try {
            conn = DriverManager.getConnection(url, user, pass);
        } catch (SQLException e) {
            System.out.println("[denied] user=" + user + " : " + e.getMessage());
            return;
        }
        try (conn) {
            System.out.println("[ok] connected as " + user + ": "
                    + conn.getMetaData().getDatabaseProductVersion());

            exec(conn, "ALTER TABLE app_conversation MODIFY COLUMN app_id VARCHAR(32) DEFAULT NULL "
                    + "COMMENT '挂载应用；NULL=知识库直聊会话'");
            exec(conn, "ALTER TABLE app_conversation ADD COLUMN request_state "
                    + "TEXT COMMENT '会话请求态 JSON（ConversationRequestState）'");
            exec(conn, "ALTER TABLE app_conversation ADD COLUMN is_pinned TINYINT DEFAULT 0 COMMENT '置顶标记'");
            exec(conn, "ALTER TABLE app_conversation_message ADD COLUMN `references` "
                    + "TEXT COMMENT '消息引用（JSON数组，MessageReference投影）'");
            exec(conn, "ALTER TABLE app_conversation_message ADD COLUMN iterations "
                    + "INT DEFAULT NULL COMMENT '引擎迭代轮数'");
            exec(conn, "ALTER TABLE app_conversation ADD INDEX idx_conv_user (user_id)");

            System.out.println("\n=== app_conversation 结构（验证） ===");
            show(conn, "app_conversation");
            System.out.println("\n=== app_conversation_message.references（验证） ===");
            showRefs(conn);
            System.out.println("\n=== idx_conv_user 索引（验证） ===");
            showIndex(conn);
            System.out.println("\n[done] 全部 DDL 已在真实库生效");
        }
    }

    /** 幂等执行：1060=列已存在 1061=索引已存在 视为成功 */
    static void exec(Connection conn, String ddl) {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate(ddl);
            System.out.println("[applied] " + shortOf(ddl));
        } catch (SQLException e) {
            if (e.getErrorCode() == 1060 || e.getErrorCode() == 1061) {
                System.out.println("[exists ] " + shortOf(ddl));
            } else {
                System.out.println("[FAILED] " + shortOf(ddl) + " -> [" + e.getErrorCode() + "] " + e.getMessage());
            }
        }
    }

    static String shortOf(String ddl) {
        return ddl.length() > 72 ? ddl.substring(0, 72) + "..." : ddl;
    }

    static void show(Connection conn, String table) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SHOW COLUMNS FROM " + table)) {
            while (rs.next()) {
                System.out.printf("  %-18s %-12s null=%s%n",
                        rs.getString("Field"), rs.getString("Type"), rs.getString("Null"));
            }
        }
    }

    static void showRefs(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SHOW COLUMNS FROM app_conversation_message LIKE 'references'")) {
            while (rs.next()) {
                System.out.printf("  %-18s %-12s null=%s%n",
                        rs.getString("Field"), rs.getString("Type"), rs.getString("Null"));
            }
        }
    }

    static void showIndex(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SHOW INDEX FROM app_conversation WHERE Key_name='idx_conv_user'")) {
            boolean any = false;
            while (rs.next()) {
                any = true;
                System.out.printf("  idx_conv_user -> %s (seq %s)%n", rs.getString("Column_name"), rs.getString("Seq_in_index"));
            }
            if (!any) System.out.println("  [MISSING] idx_conv_user 不存在！");
        }
    }
}
