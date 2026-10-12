var conn = java.sql.DriverManager.getConnection("jdbc:mysql://127.0.0.1:3306/fastrag?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai","root","123456");
var st = conn.createStatement();
var rs = st.executeQuery("SELECT COUNT(*), COUNT(DISTINCT file_id) FROM kb_chunk WHERE chunk_type='parent'");
rs.next();
System.out.println("parentChunkRows=" + rs.getLong(1) + " distinctParentFiles=" + rs.getLong(2));
var rs2 = st.executeQuery("SELECT c.file_id, f.kb_id, f.name, f.status, COUNT(*) cnt FROM kb_chunk c JOIN kb_file f ON f.id=c.file_id WHERE c.chunk_type='parent' GROUP BY c.file_id, f.kb_id, f.name, f.status ORDER BY cnt DESC LIMIT 5");
while (rs2.next()) System.out.println("CANDIDATE file=" + rs2.getString(1) + " kb=" + rs2.getString(2) + " status=" + rs2.getString(4) + " parents=" + rs2.getLong(5) + " name=" + rs2.getString(3));
conn.close();
/exit
