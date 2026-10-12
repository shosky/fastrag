var conn = java.sql.DriverManager.getConnection("jdbc:mysql://127.0.0.1:3306/fastrag?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai","root","123456");
var st = conn.createStatement();
var rs = st.executeQuery("SELECT status, total_chunks, succeeded_chunks, created_at, updated_at FROM kb_qa_generation_task WHERE file_id='2098715327712985090'");
while (rs.next()) System.out.println("TASK_NOW status=" + rs.getString(1) + " " + rs.getLong(3) + "/" + rs.getLong(2) + " created=" + rs.getTimestamp(4) + " updated=" + rs.getTimestamp(5));
conn.close();
/exit
