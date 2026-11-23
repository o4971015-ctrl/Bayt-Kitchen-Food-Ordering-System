package bayt;

import com.sun.net.httpserver.HttpExchange;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.*;
import java.util.regex.Pattern;

final class Web {
    final HttpExchange exchange;
    final Connection db;
    final Map<String,String> form;
    final String csrf;
    final Row user;
    final String method;

    Web(HttpExchange exchange,Connection db) throws Exception {
        this.exchange=exchange; this.db=db; method=exchange.getRequestMethod();
        if(!method.equals("GET")&&!method.equals("POST")) throw new Problem(405,"Method unavailable.");
        byte[] body=method.equals("POST")?exchange.getRequestBody().readNBytes(32769):new byte[0];
        if(body.length>32768)throw new Problem(413,"This form is too large.");
        form=parse(method.equals("POST")?new String(body,StandardCharsets.UTF_8):exchange.getRequestURI().getRawQuery());
        String existing=cookie("bayt_csrf");
        csrf=existing.matches("[A-Za-z0-9_-]{32}")?existing:Security.token();
        if(!csrf.equals(existing)) setCookie("bayt_csrf",csrf,false);
        if(method.equals("POST")&&(existing.isEmpty()||!Security.equal(csrf,get("csrf"))))
            throw new Problem(400,"Open a fresh page before submitting this form.");
        long now=System.currentTimeMillis()/1000;
        user=Database.one(db,"SELECT u.*,s.last_seen FROM login_session s JOIN user u USING(user_id) WHERE s.token=? AND u.active=1 AND s.last_seen>?",
                cookie("bayt_session"),now-1800);
        if(user!=null&&now-user.number("last_seen")>=30)
            Database.run(db,"UPDATE login_session SET last_seen=? WHERE token=?",now,cookie("bayt_session"));
    }

    static Map<String,String> parse(String raw) {
        var map=new HashMap<String,String>();
        if(raw==null||raw.isEmpty())return map;
        try {
            for(String part:raw.split("&")) {
                String[] pair=part.split("=",2);
                map.put(URLDecoder.decode(pair[0],StandardCharsets.UTF_8),pair.length==2?URLDecoder.decode(pair[1],StandardCharsets.UTF_8):"");
            }
        } catch(IllegalArgumentException e) {throw new Problem(400,"Invalid form encoding.");}
        return map;
    }

    String cookie(String name) {
        String header=exchange.getRequestHeaders().getFirst("Cookie");
        if(header!=null) for(String item:header.split(";")) {
            String prefix=name+"=";
            if(item.strip().startsWith(prefix))return item.strip().substring(prefix.length()).replace("\"","");
        }
        return "";
    }

    void setCookie(String name,String value,boolean clear) {
        exchange.getResponseHeaders().add("Set-Cookie",name+"="+value+"; Path=/; HttpOnly; SameSite=Lax"
                +("1".equals(System.getenv("KITCHEN_HTTPS"))?"; Secure":"")+(clear?"; Max-Age=0":""));
    }

    String get(String name) { return form.getOrDefault(name,""); }
    String required(String name,int max) {
        String value=get(name).strip();
        if(value.isEmpty()||value.length()>max)throw new Problem(422,"Enter a valid "+name.replace('_',' ')+".");
        return value;
    }
    long id() { return user.number("user_id"); }
    String role() { return user==null?"":user.text("role"); }
    void require(String...roles) {
        if(user==null)throw new LoginRequired();
        if(!Arrays.asList(roles).contains(role()))throw new Problem(403,"This account cannot perform that action.");
    }
    static long integer(String value,long min,long max) {
        try {long n=Long.parseLong(value);if(n>=min&&n<=max)return n;}
        catch(NumberFormatException ignored) {}
        throw new Problem(422,"Enter a valid whole number.");
    }
    String password() {
        String pw=get("password");
        if(pw.length()<10||pw.length()>256)throw new Problem(422,"Use a password between 10 and 256 characters.");
        return pw;
    }
    String email() {
        String value=required("email",254).toLowerCase(Locale.ROOT);
        if(!Pattern.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+",value))throw new Problem(422,"Enter a valid email address.");
        return value;
    }
    Reply page(String title,String content) {return new Reply(200,Views.page(this,title,content),null);}
}

record Reply(int status,String html,String location) {
    static Reply redirect(String location) {return new Reply(303,"",location);}
}
class Problem extends RuntimeException {
    private static final long serialVersionUID=1L;
    final int status;
    Problem(int status,String message) {super(message);this.status=status;}
}
class LoginRequired extends RuntimeException {
    private static final long serialVersionUID=1L;
}
