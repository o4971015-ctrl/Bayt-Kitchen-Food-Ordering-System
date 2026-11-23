package bayt;

import java.nio.file.*;
import java.sql.*;
import java.util.*;

final class Database {
    final Path file;
    Database(Path file) { this.file = file; }

    Connection open() throws SQLException {
        var config = new org.sqlite.SQLiteConfig();
        config.setTransactionMode(org.sqlite.SQLiteConfig.TransactionMode.IMMEDIATE);
        Connection db = DriverManager.getConnection("jdbc:sqlite:" + file, config.toProperties());
        try (Statement s = db.createStatement()) {
            s.execute("PRAGMA foreign_keys=ON");
            s.execute("PRAGMA busy_timeout=5000");
        } catch (SQLException e) { db.close(); throw e; }
        return db;
    }

    void initialize(Path schema) throws Exception {
        Files.createDirectories(file.getParent());
        try (Connection db = open(); Statement s = db.createStatement()) {
            for (String sql : Files.readString(schema).split(";"))
                if (!sql.isBlank()) s.execute(sql);
            if (one(db, "SELECT user_id FROM user LIMIT 1") != null) return;
            db.setAutoCommit(false);
            StringBuilder credentials = new StringBuilder();
            for (String role : List.of("Customer", "Staff", "Admin")) {
                String email = role.toLowerCase(Locale.ROOT) + "@demo.test";
                String password = Security.token();
                long id = insert(db, "INSERT INTO user(name,email,phone,password_hash,role) VALUES(?,?,?,?,?)",
                        role, email, "01000000000", Security.hash(password), role);
                if (role.equals("Customer")) run(db,"INSERT INTO cart(customer_id) VALUES(?)",id);
                credentials.append(role).append(" | ").append(email).append(" | ").append(password).append("\n");
            }
            insert(db,"INSERT INTO category(name) VALUES(?)","Grill");
            insert(db,"INSERT INTO category(name) VALUES(?)","Kitchen classics");
            insert(db,"INSERT INTO category(name) VALUES(?)","Sides");
            insert(db,"INSERT INTO category(name) VALUES(?)","Drinks");
            run(db,"INSERT INTO menu_item(category_id,name,description,price) VALUES (1,'Lemon chicken','Chargrilled chicken, lemon rice and garlic sauce.',16500),(1,'Kofta plate','Grilled kofta, tahini and baladi bread.',18000),(2,'Koshary bowl','Rice, lentils, pasta and crisp onions.',8500),(3,'Roasted potatoes','Cumin and fresh herbs.',5500),(3,'Baladi salad','Cucumber, tomato and parsley.',4500),(4,'Hibiscus cooler','Chilled karkadeh with lime.',3500)");
            db.commit();
            Files.writeString(file.resolveSibling("demo-accounts.txt"), credentials);
        }
    }

    void backup() throws Exception {
        Path folder=Files.createDirectories(file.getParent().resolve("backups"));
        Path dest=folder.resolve(java.time.LocalDate.now(java.time.ZoneOffset.UTC)+".sqlite3");
        if (Files.exists(dest)) return;
        Path tmp=folder.resolve(Security.token()+".tmp");
        try (Connection db=open()) {
            run(db,"VACUUM INTO ?",tmp.toString());
            Files.move(tmp,dest);
        } finally { Files.deleteIfExists(tmp); }
    }

    static PreparedStatement prepare(Connection db,String sql,Object... args) throws SQLException {
        PreparedStatement s=db.prepareStatement(sql);
        try {
            for (int i=0;i<args.length;i++) s.setObject(i+1,args[i]);
            return s;
        } catch(SQLException e) { s.close(); throw e; }
    }

    static int run(Connection db,String sql,Object...args) throws SQLException {
        try(var s=prepare(db,sql,args)) { return s.executeUpdate(); }
    }

    static long insert(Connection db,String sql,Object...args) throws SQLException {
        run(db,sql,args);
        return one(db,"SELECT last_insert_rowid() AS id").number("id");
    }

    static List<Row> all(Connection db,String sql,Object...args) throws SQLException {
        try(var s=prepare(db,sql,args);var r=s.executeQuery()) {
            var result=new ArrayList<Row>();
            var meta=r.getMetaData();
            while(r.next()) {
                var values=new HashMap<String,Object>();
                for(int i=1;i<=meta.getColumnCount();i++) values.put(meta.getColumnLabel(i),r.getObject(i));
                result.add(new Row(values));
            }
            return result;
        }
    }

    static Row one(Connection db,String sql,Object...args) throws SQLException {
        var rows=all(db,sql,args);
        return rows.isEmpty()?null:rows.get(0);
    }
}

record Row(Map<String,Object> values) {
    String text(String key) { return Objects.toString(values.get(key),""); }
    long number(String key) { return ((Number) values.get(key)).longValue(); }
    boolean yes(String key) { return number(key)!=0; }
}
