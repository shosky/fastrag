var conn = java.sql.DriverManager.getConnection("jdbc:mysql://127.0.0.1:3306/fastrag?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai","root","123456");
var st = conn.createStatement();
var r1 = st.executeUpdate("DELETE FROM kb_qa_pair WHERE file_id='2088520866752126977' AND source='ai'");
var r2 = st.executeUpdate("DELETE FROM kb_qa_generation_task WHERE file_id='2088520866752126977'");
var r3 = st.executeUpdate("DELETE FROM kb_qa_generation_task WHERE total_chunks=0");
System.out.println("cleaned aiPairs=" + r1 + " tasks=" + r2 + " emptyTasks=" + r3);
var rs = st.executeQuery("SELECT COUNT(*) FROM kb_qa_pair WHERE file_id='2088520866752126977' AND source='ai'");
rs.next(); System.out.println("remaining=" + rs.getLong(1));
conn.close();
/exit
