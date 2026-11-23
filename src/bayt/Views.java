package bayt;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

final class Views {
    static String e(Object value) {
        return String.valueOf(value).replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");
    }
    static String money(long n) {return price(n)+" EGP";}
    static String price(long n) {return String.format(Locale.ROOT,"%d.%02d",n/100,n%100);}
    static String time(String sql) {
        if(sql.isBlank())return "";
        return LocalDateTime.parse(sql.replace(' ','T')).atZone(ZoneOffset.UTC)
                .withZoneSameInstant(ZoneId.of("Africa/Cairo")).format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm",Locale.ENGLISH));
    }
    static String link(String path,String label) {return "<a href='"+e(path)+"'>"+e(label)+"</a>";}
    static String hidden(String name,Object value) {return "<input type='hidden' name='"+e(name)+"' value='"+e(value)+"'>";}
    static String field(String label,String name,String type,String value,String attrs) {
        return "<label>"+e(label)+"<input class='form-control' name='"+e(name)+"' type='"+e(type)+"' value='"+e(value)+"' "+attrs+"></label>";
    }
    static String form(Web w,String path) {
        return "<form class='editor' method='post' action='"+e(path)+"'>"+hidden("csrf",w.csrf);
    }
    static String button(String label) {return "<button class='btn btn-primary'>"+e(label)+"</button>";}
    static String action(String name,String value,String label) {
        return "<button class='btn btn-outline-dark' name='"+e(name)+"' value='"+e(value)+"'>"+e(label)+"</button>";
    }
    static String option(String value,String label,String current) {
        return "<option value='"+e(value)+"'"+(value.equals(current)?" selected":"")+">"+e(label)+"</option>";
    }
    static String page(Web w,String title,String content) {
        String nav=link("/","Menu");
        if(w==null||w.user==null)nav+=link("/login","Sign in")+link("/register","Register");
        else {
            nav+=switch(w.role()) {
                case "Customer" -> link("/cart","Cart")+link("/orders","My orders");
                case "Staff" -> link("/orders","Order queue");
                case "Admin" -> link("/admin/menu","Edit menu")+link("/admin/staff","Staff")+link("/admin/audit","Audit log");
                default -> "";
            };
            nav+="<span class='account'>"+e(w.user.text("name"))+" · "+e(w.role())+"</span>"+form(w,"/logout")+action("action","logout","Sign out")+"</form>";
        }
        return """
          <!doctype html><html lang='en'><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>
          <link rel='stylesheet' href='/static/bootstrap.min.css'><link rel='stylesheet' href='/static/kitchen.css'>
          <script defer src='/static/kitchen.js'></script><title>%s · Bayt Kitchen</title></head>
          <body><header><div class='container d-flex flex-wrap gap-3 align-items-center justify-content-between py-3'>
          <a class='brand' href='/'>Bayt Kitchen</a><nav class='d-flex flex-wrap gap-3 align-items-center' aria-label='Main navigation'>%s</nav></div></header>
          <main class='container pb-5'><div class='heading my-4'><h1>%s</h1></div>%s</main></body></html>
          """.formatted(e(title),nav,e(title),content);
    }
}
