var conn = java.sql.DriverManager.getConnection("jdbc:mysql://127.0.0.1:3306/fastrag?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai","root","123456");
var st = conn.createStatement();
var rs = st.executeQuery("SELECT COUNT(*) cnt, MIN(unit_index) mn, MAX(unit_index) mx, MIN(created_at) mnT, MAX(created_at) mxT, SUM(status=1) done, SUM(status=2) fail FROM kb_graph_unit WHERE kb_id='2096146641693196290' AND file_id='2098715327712985090'");
rs.next(); System.out.println("UNIT_ROWS cnt=" + rs.getLong(1) + " index[" + rs.getLong(2) + ".." + rs.getLong(3) + "] done=" + rs.getLong(6) + " fail=" + rs.getLong(7) + " created[" + rs.getTimestamp(4) + " ~ " + rs.getTimestamp(5) + "]");
var rs2 = st.executeQuery("SELECT unit_index, status, LENGTH(extraction_result) er, created_at, updated_at FROM kb_graph_unit WHERE kb_id='2096146641693196290' AND file_id='2098715327712985090' AND unit_index IN (25,26,27) ORDER BY unit_index, created_at");
while (rs2.next()) System.out.println("ROW idx=" + rs2.getInt(1) + " status=" + rs2.getInt(2) + " resultLen=" + rs2.getLong(3) + " created=" + rs2.getTimestamp(4) + " updated=" + rs2.getTimestamp(5));
var rs3 = st.executeQuery("SELECT status, build_error FROM kb_graph_index WHERE kb_id='2096146641693196290'");
while (rs3.next()) System.out.println("GRAPH_INDEX status=" + rs3.getString(1) + " err=" + (rs3.getString(2)==null?"null":rs3.getString(2).substring(0, Math.min(160, rs3.getString(2).length()))));
var rs4 = st.executeQuery("SELECT COUNT(*) FROM kb_chunk WHERE file_id='2098715327712985090'");
rs4.next(); System.out.println("CHUNK_ROWS(当前分片)=" + rs4.getLong(1));
conn.close();
/exit
