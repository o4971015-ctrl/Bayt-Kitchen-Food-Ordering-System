package bayt;

import com.sun.net.httpserver.*;
import java.net.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.concurrent.*;

public final class Main {
    private final Path home;
    private final Database database;
    private final CheckoutController checkout;
    private final AccountController accounts=new AccountController();
    private final ShoppingController shopping=new ShoppingController();
    private final OrderController orders=new OrderController();
    private final AdminController admin=new AdminController();

    Main(Path home,Path data,long fee) {
        this.home=home; database=new Database(data.resolve("kitchen.sqlite3"));
        checkout=new CheckoutController(fee);
    }

    public static void main(String[] args) throws Exception {
        Path home=Path.of(System.getProperty("bayt.home",".")).toAbsolutePath();
        Path data=Path.of(System.getProperty("bayt.data",home.resolve("instance").toString())).toAbsolutePath();
        long fee=Web.integer(System.getenv().getOrDefault("KITCHEN_FEE_PIASTRES","2500"),0,999999999999L);
        new Main(home,data,fee).start(Integer.getInteger("bayt.port",8000));
    }

    void start(int port) throws Exception {
        database.initialize(home.resolve("schema.sql"));
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",port),64);
        server.createContext("/",this::handle);
        server.setExecutor(Executors.newFixedThreadPool(16));
        server.start();
        var clock=Executors.newSingleThreadScheduledExecutor();
        clock.scheduleWithFixedDelay(()->{
            try {database.backup();}
            catch(Exception e) {System.err.println("Daily backup unavailable. It will be retried.");}
        },0,1,TimeUnit.HOURS);
        System.out.println("Bayt Kitchen  http://127.0.0.1:"+port);
        System.out.println("Demo accounts  "+database.file.resolveSibling("demo-accounts.txt"));
    }

    private Reply dispatch(Web w,String path) throws Exception {
        if(w.user!=null&&w.user.yes("must_change_password")&&!path.equals("/password")&&!path.equals("/logout"))
            return Reply.redirect("/password");
        return switch(path) {
            case "/" -> shopping.menu(w);
            case "/login","/register","/logout","/password" -> accounts.handle(w,path);
            case "/cart" -> shopping.cart(w);
            case "/checkout","/checkout/confirm" -> checkout.handle(w,path);
            case "/orders" -> orders.list(w);
            case "/admin/menu","/admin/categories","/admin/items","/admin/staff","/admin/audit" -> admin.handle(w,path);
            default -> {
                if(path.matches("/cart/[0-9]{1,18}"))yield shopping.edit(w,Long.parseLong(path.substring(6)));
                if(path.matches("/orders/[0-9]{1,18}(/update)?"))yield orders.detail(w,Long.parseLong(path.split("/")[2]),path.endsWith("/update"));
                if(path.matches("/admin/staff/[0-9]{1,18}"))yield admin.activate(w,Long.parseLong(path.substring(13)));
                throw new Problem(404,"This page or order is unavailable.");
            }
        };
    }

    private void handle(HttpExchange exchange) {
        try(exchange) {
            String path=exchange.getRequestURI().getPath();
            if(exchange.getRequestMethod().equals("GET")&&path.matches("/static/(bootstrap\\.min\\.css|kitchen\\.css|kitchen\\.js)")) {
                byte[] bytes=Files.readAllBytes(home.resolve(path.substring(1)));
                exchange.getResponseHeaders().set("Content-Type",path.endsWith(".js")?"text/javascript":"text/css");
                exchange.sendResponseHeaders(200,bytes.length);
                exchange.getResponseBody().write(bytes);
                return;
            }
            Reply reply;
            Web w=null;
            try (var db=database.open()) {
                if(exchange.getRequestMethod().equals("POST"))db.setAutoCommit(false);
                try {
                    w=new Web(exchange,db);
                    reply=dispatch(w,path);
                    if(!db.getAutoCommit())db.commit();
                } catch(Exception error) {
                    if(!db.getAutoCommit())db.rollback();
                    throw error;
                }
            } catch(LoginRequired e) {
                reply=Reply.redirect("/login");
            } catch(Problem e) {
                reply=error(w,e.status,e.getMessage());
            } catch(SQLException e) {
                boolean constraint=(e.getErrorCode()&255)==19;
                reply=error(w,constraint?422:503,constraint?"The entry already exists, is invalid, or is in use.":"The database is temporarily unavailable. Reopen the page and retry.");
            } catch(Exception e) {
                System.err.println("Request failed: "+e.getClass().getSimpleName());
                reply=error(w,500,"The request could not be completed.");
            }
            exchange.getResponseHeaders().set("Content-Type","text/html; charset=utf-8");
            exchange.getResponseHeaders().set("Cache-Control","no-store");
            exchange.getResponseHeaders().set("X-Content-Type-Options","nosniff");
            if(reply.location()!=null)exchange.getResponseHeaders().set("Location",reply.location());
            byte[] bytes=reply.html().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(reply.status(),bytes.length==0?-1:bytes.length);
            if(bytes.length>0)exchange.getResponseBody().write(bytes);
        } catch(java.io.IOException e) { System.err.println("HTTP response interrupted."); }
    }
    private Reply error(Web w,int status,String message) {
        return new Reply(status,Views.page(w,"Please review","<section class='panel'><p role='alert'>"+Views.e(message)+"</p><button id='back' class='btn btn-outline-dark'>Go back</button> <a href='/'>Return to menu</a></section>"),null);
    }
}
