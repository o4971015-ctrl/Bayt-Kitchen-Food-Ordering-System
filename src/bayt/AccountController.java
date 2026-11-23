package bayt;

import static bayt.Database.*;
import static bayt.Views.*;

final class AccountController {
    Reply handle(Web w,String path) throws Exception {
        if(path.equals("/logout")) {
            post(w);
            run(w.db,"DELETE FROM login_session WHERE token=?",w.cookie("bayt_session"));
            w.setCookie("bayt_session","",true);
            return Reply.redirect("/");
        }
        if(path.equals("/password"))w.require("Customer","Staff","Admin");
        if(w.method.equals("GET"))return view(w,path);
        return switch(path) {
            case "/register" -> {
                long id=create(w,"Customer");
                run(w.db,"INSERT INTO cart(customer_id) VALUES(?)",id);
                yield Reply.redirect("/login");
            }
            case "/password" -> {
                String password=w.password();
                if(Security.matches(password,w.user.text("password_hash")))throw new Problem(422,"Choose a different password.");
                run(w.db,"UPDATE user SET password_hash=?,must_change_password=0 WHERE user_id=?",Security.hash(password),w.id());
                run(w.db,"DELETE FROM login_session WHERE user_id=? AND token<>?",w.id(),w.cookie("bayt_session"));
                yield Reply.redirect(home(w.role()));
            }
            default -> login(w);
        };
    }

    static void post(Web w) {if(!w.method.equals("POST"))throw new Problem(405,"Submit the form to perform this action.");}
    static void read(Web w) {if(!w.method.equals("GET"))throw new Problem(405,"Open this page with a browser link.");}

    static long create(Web w,String role) throws Exception {
        return insert(w.db,"INSERT INTO user(name,email,phone,password_hash,role,must_change_password) VALUES(?,?,?,?,?,?)",
                w.required("name",100),w.email(),w.required("phone",25),Security.hash(w.password()),role,role.equals("Staff")?1:0);
    }

    private Reply view(Web w,String path) {
        String title=switch(path) {case "/register" -> "Create an account";case "/password" -> "Set a new password";default -> "Sign in";};
        String content="<section class='panel form-panel'>"+form(w,path);
        if(path.equals("/register"))content+=identityFields();
        if(path.equals("/login"))content+=field("Email","email","email","","required autocomplete='username' maxlength='254'");
        if(!path.equals("/register"))content+=field("Password","password","password","","required maxlength='256'");
        return w.page(title,content+button(title)+"</form></section>");
    }

    static String identityFields() {
        return field("Name","name","text","","required maxlength='100'")+field("Email","email","email","","required maxlength='254'")
                +field("Phone","phone","tel","","required maxlength='25'")+field("Password","password","password","","required minlength='10' maxlength='256' autocomplete='new-password'");
    }

    private Reply login(Web w) throws Exception {
        String email=w.email();
        long now=System.currentTimeMillis()/1000;
        run(w.db,"DELETE FROM login_attempt WHERE at<=?",now-900);
        run(w.db,"DELETE FROM login_lock WHERE until_time<=?",now);
        Row locked=one(w.db,"SELECT until_time FROM login_lock WHERE email=?",email);
        if(locked!=null)return rejected(w,429,"Sign-in is temporarily unavailable. Try again in 15 minutes.");
        Row account=one(w.db,"SELECT * FROM user WHERE email=? AND active=1",email);
        if(account==null||!Security.matches(w.get("password"),account.text("password_hash"))) {
            run(w.db,"INSERT INTO login_attempt VALUES(?,?)",email,now);
            if(one(w.db,"SELECT count(*) AS attempts FROM login_attempt WHERE email=?",email).number("attempts")>=5)
                run(w.db,"INSERT OR REPLACE INTO login_lock VALUES(?,?)",email,now+900);
            return rejected(w,401,"Sign-in details could not be verified.");
        }
        run(w.db,"DELETE FROM login_attempt WHERE email=?",email);
        run(w.db,"DELETE FROM login_session WHERE token=? OR last_seen<=?",w.cookie("bayt_session"),now-1800);
        String token=Security.token();
        run(w.db,"INSERT INTO login_session VALUES(?,?,?)",token,account.number("user_id"),now);
        w.setCookie("bayt_session",token,false);
        return Reply.redirect(account.yes("must_change_password")?"/password":home(account.text("role")));
    }

    private Reply rejected(Web w,int status,String message) {
        return new Reply(status,Views.page(w,"Sign in","<p class='alert alert-danger' role='alert'>"+e(message)+"</p>"+link("/login","Try again")),null);
    }
    private String home(String role) {return switch(role) {case "Staff" -> "/orders"; case "Admin" -> "/admin/menu"; default -> "/";};}
}
