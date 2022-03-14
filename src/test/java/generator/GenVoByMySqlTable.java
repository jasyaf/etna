package generator;

import java.io.File;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.io.FileUtils;
import org.etnaframework.core.util.HttlTemplateUtils;
import org.etnaframework.core.util.KeyValueGetter.DbMap;
import httl.Template;

/**
 * 根据MySQL的表结构生成VO对象
 *
 * @author BlackCat
 * @since 2014-2-13
 */
public class GenVoByMySqlTable {

    public static List<String> getTableList(Connection conn) throws Exception {
        PreparedStatement ps = conn.prepareStatement("show tables");
        ps.execute();
        ResultSet rs = ps.getResultSet();
        List<String> tableList = new ArrayList<String>();
        while (rs.next()) {
            tableList.add(rs.getString(1));
        }
        ps.close();
        return tableList;
    }

    private static String _getString(ResultSet rs, String key) throws Exception {
        return new String(rs.getBytes(key), dbCharset);
    }

    public static class TableField {

        private String name;

        private String nameForMethod;

        private String type;

        private String comment;

        public TableField(String field, String type, String comment) {
            this.name = Character.toLowerCase(field.charAt(0)) + field.substring(1);
            this.nameForMethod = Character.toUpperCase(field.charAt(0)) + field.substring(1);
            if (type.contains("bigint")) {
                this.type = "Long";
            } else if (type.contains("tinyint")) {
                this.type = "Byte";
            } else if (type.contains("int")) {
                this.type = "Integer";
            } else if (type.contains("char")) {
                this.type = "String";
            } else if (type.contains("clob")) {
                this.type = "String";
            } else if (type.contains("text")) {
                this.type = "String";
            } else if (type.contains("date")) {
                this.type = "Datetime";
            } else if (type.contains("time")) {
                this.type = "Datetime";
            } else if (type.contains("timestamp")) {
                this.type = "Datetime";
            } else if (type.contains("float")) {
                this.type = "Float";
            } else if (type.contains("double")) {
                this.type = "Double";
            } else if (type.contains("binary")) {
                this.type = "byte[]";
            } else if (type.contains("blob")) {
                this.type = "byte[]";
            } else {
                this.type = "String"; // 默认按字符串处理
            }
            if (!comment.isEmpty()) {
                this.comment = "/** " + comment + " */\n    ";
            } else {
                this.comment = "";
            }
        }

        public String getName() {
            return name;
        }

        public String getNameForMethod() {
            return nameForMethod;
        }

        public String getType() {
            return type;
        }

        public String getComment() {
            return comment;
        }
    }

    public static List<TableField> getTableFields(Connection conn, String tableName) throws Exception {
        PreparedStatement ps = conn.prepareStatement("show full fields from `" + tableName + "`");
        ps.execute();
        ResultSet rs = ps.getResultSet();
        List<TableField> list = new ArrayList<TableField>();
        while (rs.next()) {
            String field = _getString(rs, "field");
            String type = _getString(rs, "type");
            String comment = _getString(rs, "comment");
            list.add(new TableField(field, type, comment));
        }
        ps.close();
        return list;
    }

    public static String getTableComment(Connection conn, String tableName) throws Exception {
        PreparedStatement ps = conn.prepareStatement("show table status from `" + dbName + "` like '" + tableName + "'");
        ps.execute();
        ResultSet rs = ps.getResultSet();
        while (rs.next()) {
            String comment = _getString(rs, "comment");
            if (!comment.isEmpty()) {
                return comment;
            }
        }
        ps.close();
        return "";
    }

    public static void build(boolean buildVo) throws Exception {
        Class.forName("com.mysql.jdbc.Driver").newInstance();
        String url = "jdbc:mysql://" + ip + ":" + port + "/" + dbName + "?useUnicode=true&characterEncoding=" + dbCharset;
        Connection conn = DriverManager.getConnection(url, user, password);
        List<String> tableList = getTableList(conn);
        StringBuilder sb = new StringBuilder();
        for (String tableName : tableList) {
            if (!buildVo) {
                System.out.println("\"" + tableName + "\", ");
            } else if (include.contains(tableName)) {
                List<TableField> fields = getTableFields(conn, tableName);
                String beanClassName = Character.toUpperCase(tableName.charAt(0)) + tableName.substring(1);
                String fixImport = "";
                for (TableField tf : fields) {
                    if (tf.getType().contains("Date") || tf.getType().contains("Time")) {
                        fixImport = "\nimport org.etnaframework.core.util.DatetimeUtil.Datetime;\n";
                    }
                }
                String comment = getTableComment(conn, tableName);

                Template template = HttlTemplateUtils.getTemplate(tmplVo);
                StringWriter sw = new StringWriter();

                DbMap dm = new DbMap();
                dm.put("beanClassName", beanClassName);
                dm.put("comment", comment);
                dm.put("date", new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
                dm.put("author", author);
                dm.put("fixImport", fixImport);
                dm.put("fieldList", fields);

                template.render(dm, sw);
                String outJava = sw.toString();

                System.out.println(outJava);
                FileUtils.writeStringToFile(new File("src/test/java/generator/out/vo/" + beanClassName + ".java"), outJava, javaFileCharset);

                // 生成空的Dao类
                template = HttlTemplateUtils.getTemplate(tmplDao);
                sw = new StringWriter();
                String daoClassName = beanClassName + "Dao";
                dm.put("daoClassName", daoClassName);

                template.render(dm, sw);
                outJava = sw.toString();

                System.out.println(outJava);
                FileUtils.writeStringToFile(new File("src/test/java/generator/out/dao/" + daoClassName + ".java"), outJava, javaFileCharset);
            }
        }
        System.out.println(sb);
        conn.close();
    }

    public static String tmplVo = "/generator/template/vo.httl";

    public static String tmplDao = "/generator/template/dao.httl";

    public static final String dbCharset = "UTF-8";

    /** 生成的.java文件的编码 */
    public static String javaFileCharset = "UTF-8";

    public static String author = "hujiachao";

    public static String ip = "192.168.1.199";

    public static int port = 3306;

    public static String user = "root";

    public static String password = "3LnDZD8W7Qz6s4UzicbE";

    public static String dbName = "mangoplus_dispatcher"; // 1.修改数据库名

    public static final Set<String> include = new HashSet<String>();

    static {
        // 3. 从2的结果里拷贝需要生成的表名，复制到这
        include.addAll(Arrays.asList(new String[] {
            "tbl_dp_channelstat",
            }));
    }

    public static void main(String[] args) throws Exception {
        try {
            build(true); // 2. 置为false执行一遍，从控制台复制需要生成的表名； 4. 置为true, 运行一遍，结果在out目录
        } finally {
            System.exit(0);
        }
    }
}
