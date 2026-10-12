var conn = java.sql.DriverManager.getConnection("jdbc:mysql://127.0.0.1:3306/fastrag?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai","root","123456");
var st = conn.createStatement();
var rs = st.executeQuery("SELECT file_id, kb_id, status, total_chunks, succeeded_chunks, failed_chunks, created_at, updated_at FROM kb_qa_generation_task WHERE created_at >= '2026-09-27 13:00:00' ORDER BY created_at DESC LIMIT 5");
boolean any=false; while (rs.next()) { any=true; System.out.println("TASK file=" + rs.getString(1) + " status=" + rs.getString(3) + " " + rs.getLong(5) + "/" + rs.getLong(4) + " created=" + rs.getTimestamp(7)); }
if (!any) System.out.println("TASK: 今天 13 点后无任务行");
var rs2 = st.executeQuery("SELECT COUNT(*), MIN(created_at), MAX(created_at) FROM kb_qa_pair WHERE source='ai' AND created_at >= '2026-09-27 13:00:00'");
rs2.next(); System.out.println("AI_PAIRS today>=13:00: " + rs2.getLong(1) + " 条 (" + rs2.getTimestamp(2) + " ~ " + rs2.getTimestamp(3) + ")");
conn.close();
/exit
