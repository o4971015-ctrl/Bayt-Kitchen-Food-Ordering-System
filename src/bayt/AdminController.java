package bayt;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import static bayt.Views.*;
import static bayt.Database.*;
import static bayt.AccountController.*;

final class AdminController {
    Reply handle(Web w,String path) throws Exception {
        w.require("Admin");
        return switch(path) {
            case "/admin/menu" -> menu(w);
            case "/admin/categories" -> category(w);
            case "/admin/items" -> item(w);
            case "/admin/staff" -> staff(w);
            default -> auditLog(w);
        };
    }

    private void audit(Web w,String action,String type,long id) throws SQLException {
        run(w.db,"INSERT INTO audit_event(actor_id,action,entity_type,entity_id) VALUES(?,?,?,?)",w.id(),action,type,id);
    }
    private long id(Web w) {return Web.integer(w.get("id"),1,Long.MAX_VALUE);}

    private Reply menu(Web w) throws SQLException {
        read(w);
        var categories=all(w.db,"SELECT * FROM category ORDER BY name");
        var content=new StringBuilder("<div class='row g-3'><section class='col-lg-7'><div class='panel'><h2>Dishes</h2>");
        if(!categories.isEmpty())content.append("<details class='line'><summary>Add dish</summary>").append(itemForm(w,null,categories)).append("</details>");
        for(var dish:all(w.db,"SELECT * FROM menu_item ORDER BY menu_item_id")) {
            content.append("<details class='line'><summary>").append(e(dish.text("name"))).append(" · ").append(money(dish.number("price")))
                    .append(" · ").append(state(dish)).append("</summary>").append(itemForm(w,dish,categories)).append("</details>");
        }
        content.append("</div></section><section class='col-lg-5'><div class='panel'><h2>Categories</h2>")
                .append(form(w,"/admin/categories")).append(field("New category","name","text","","required maxlength='80'"))
                .append(action("action","create","Add category")).append("</form>");
        for(var cat:categories)content.append("<div class='line'>").append(form(w,"/admin/categories")).append(hidden("id",cat.number("category_id")))
                .append(field("Category name","name","text",cat.text("name"),"required maxlength='80'")).append(action("action","rename","Rename"))
                .append(" <button class='btn btn-outline-danger' name='action' value='delete' formnovalidate>Delete</button></form></div>");
        return w.page("Menu management",content.append("</div></section></div>").toString());
    }

    private String state(Row dish) {
        return dish==null||dish.yes("available")&&dish.yes("active")?"Available":dish.yes("active")?"Sold out":"Archived";
    }

    private String itemForm(Web w,Row dish,List<Row> categories) {
        var content=new StringBuilder(form(w,"/admin/items"));
        if(dish!=null)content.append(hidden("id",dish.number("menu_item_id")));
        content.append(field("Name","name","text",dish==null?"":dish.text("name"),"required maxlength='120'"))
                .append("<label>Description<textarea class='form-control' name='description' required maxlength='1000'>")
                .append(e(dish==null?"":dish.text("description"))).append("</textarea></label>")
                .append(field("Price in EGP","price","number",dish==null?"":price(dish.number("price")),"required min='0.01' step='.01' max='9999999999.99'"))
                .append("<label>Category<select class='form-select' name='category' required>");
        for(var cat:categories)content.append(option(cat.text("category_id"),cat.text("name"),dish==null?"":dish.text("category_id")));
        content.append("</select></label><label>Availability<select class='form-select' name='state'>");
        for(String state:List.of("Available","Sold out","Archived"))content.append(option(state,state,state(dish)));
        return content.append("</select></label>").append(button("Save dish")).append("</form>").toString();
    }

    private Reply category(Web w) throws SQLException {
        post(w);
        String action=w.get("action");
        long id;
        switch(action) {
            case "create" -> id=insert(w.db,"INSERT INTO category(name) VALUES(?)",w.required("name",80));
            case "rename" -> {
                id=id(w);
                if(run(w.db,"UPDATE category SET name=? WHERE category_id=?",w.required("name",80),id)==0)
                    throw new Problem(404,"Category unavailable.");
            }
            case "delete" -> {
                id=id(w);
                if(run(w.db,"DELETE FROM category WHERE category_id=?",id)==0)throw new Problem(404,"Category unavailable.");
            }
            default -> throw new Problem(400,"Invalid category action.");
        }
        audit(w,action,"Category",id);
        return Reply.redirect("/admin/menu");
    }

    private Reply item(Web w) throws SQLException {
        post(w);
        String name=w.required("name",120), description=w.required("description",1000), amount=w.required("price",13), state=w.get("state");
        if(!amount.matches("[0-9]{1,10}(\\.[0-9]{1,2})?"))throw new Problem(422,"Enter a positive price with up to two decimal places.");
        long price=new BigDecimal(amount).movePointRight(2).longValueExact();
        if(price<=0||!List.of("Available","Sold out","Archived").contains(state))throw new Problem(422,"Enter a positive price and choose an availability state.");
        long cat=Web.integer(w.get("category"),1,Long.MAX_VALUE);
        int available=state.equals("Available")?1:0, active=state.equals("Archived")?0:1;
        long id;
        if(w.get("id").isBlank())id=insert(w.db,"INSERT INTO menu_item(category_id,name,description,price,available,active) VALUES(?,?,?,?,?,?)",cat,name,description,price,available,active);
        else {
            id=id(w);
            if(run(w.db,"UPDATE menu_item SET category_id=?,name=?,description=?,price=?,available=?,active=? WHERE menu_item_id=?",cat,name,description,price,available,active,id)==0)
                throw new Problem(404,"Dish unavailable.");
        }
        audit(w,"Save "+state,"MenuItem",id);
        return Reply.redirect("/admin/menu");
    }

    private Reply staff(Web w) throws Exception {
        if(w.method.equals("POST")) {
            audit(w,"Create","User",create(w,"Staff"));
            return Reply.redirect("/admin/staff");
        }
        var content=new StringBuilder("<div class='row g-3'><section class='col-md-6'><div class='panel'><h2>Add staff member</h2>")
                .append(form(w,"/admin/staff")).append(identityFields()).append("<p>The employee must change the initial password at first sign-in.</p>")
                .append(button("Create staff account")).append("</form></div></section><section class='col-md-6'><div class='panel'><h2>Staff access</h2>");
        for(var person:all(w.db,"SELECT * FROM user WHERE role='Staff' ORDER BY name")) {
            boolean active=person.yes("active");
            content.append("<div class='line'><strong>").append(e(person.text("name"))).append("</strong> · ").append(active?"Active":"Inactive")
                    .append("<p>").append(e(person.text("email"))).append("</p>").append(form(w,"/admin/staff/"+person.number("user_id")))
                    .append(action("active",active?"0":"1",active?"Deactivate":"Reactivate")).append("</form></div>");
        }
        return w.page("Staff accounts",content.append("</div></section></div>").toString());
    }

    Reply activate(Web w,long id) throws SQLException {
        post(w);w.require("Admin");
        long active=Web.integer(w.get("active"),0,1);
        if(run(w.db,"UPDATE user SET active=? WHERE user_id=? AND role='Staff'",active,id)==0)throw new Problem(404,"Staff account unavailable.");
        if(active==0)run(w.db,"DELETE FROM login_session WHERE user_id=?",id);
        audit(w,active==0?"Deactivate":"Activate","User",id);
        return Reply.redirect("/admin/staff");
    }

    private Reply auditLog(Web w) throws SQLException {
        read(w);
        var content=new StringBuilder("<section class='panel'>");
        var events=all(w.db,"SELECT a.*,u.name FROM audit_event a JOIN user u ON a.actor_id=u.user_id ORDER BY event_id DESC LIMIT 100");
        if(events.isEmpty())content.append("<p>No administrative changes yet.</p>");
        for(var event:events)content.append("<div class='line'><strong>").append(e(event.text("action"))).append(" · ").append(e(event.text("entity_type")))
                .append(" #").append(event.number("entity_id")).append("</strong><div>").append(e(event.text("name"))).append(" · ")
                .append(e(time(event.text("occurred_at")))).append("</div></div>");
        return w.page("Administrative activity",content.append("</section>").toString());
    }
}
