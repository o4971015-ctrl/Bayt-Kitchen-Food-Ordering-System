package bayt;

import java.sql.SQLException;
import static bayt.Database.*;
import static bayt.Views.*;
import static bayt.AccountController.*;

final class ShoppingController {
    Reply menu(Web w) throws SQLException {
        read(w);
        String filter=w.get("category");
        var content=new StringBuilder("<p>Order for pickup or delivery. Pay cash at handover.</p><form class='d-flex gap-2 align-items-center flex-wrap mb-4'><label for='category'>Category</label><select id='category' name='category' class='form-select w-auto'>");
        content.append(option("","All dishes",filter));
        for(var cat:all(w.db,"SELECT * FROM category ORDER BY name"))
            content.append(option(cat.text("category_id"),cat.text("name"),filter));
        content.append("</select>").append(button("Show dishes")).append("</form><div class='row g-3'>");
        var rows=all(w.db,"SELECT m.*, c.name AS category FROM menu_item m JOIN category c USING(category_id) WHERE m.active=1 AND (?='' OR m.category_id=?) ORDER BY m.menu_item_id",filter,filter);
        if(rows.isEmpty())content.append("<p>No dishes match this category.</p>");
        for(var item:rows) {
            content.append("<div class='col-md-6 col-lg-4'><article class='panel h-100'><p class='category'>").append(e(item.text("category")))
                    .append("</p><h2>").append(e(item.text("name"))).append("</h2><p>").append(e(item.text("description")))
                    .append("</p><p class='price'>").append(money(item.number("price"))).append("</p>");
            if(!item.yes("available"))content.append("<p>Sold out</p>");
            else if(w.role().equals("Customer"))content.append(form(w,"/cart/"+item.number("menu_item_id")))
                    .append(quantity(1)).append(action("action","add","Add to cart")).append("</form>");
            else if(w.user==null) content.append(link("/login","Sign in to order"));
            content.append("</article></div>");
        }
        return w.page("From our kitchen",content.append("</div><p class='small mt-4'>If you have a food allergy, contact the restaurant before ordering.</p>").toString());
    }

    Reply cart(Web w) throws SQLException {
        read(w);w.require("Customer");
        var rows=new OrderRepository(w.db).cart(w.id());
        if(rows.isEmpty())return w.page("Your cart","<div class='panel'>Your cart is empty. "+link("/","Browse the menu")+".</div>");
        var content=new StringBuilder("<div class='row g-3'><section class='col-lg-7'><div class='panel'><h2>Selected dishes</h2>");
        long subtotal=0;
        for(var item:rows) {
            long line=item.number("price")*item.number("quantity");subtotal+=line;
            content.append("<div class='line'><strong>").append(e(item.text("name"))).append("</strong> · ").append(money(line));
            if(!item.yes("active")||!item.yes("available"))content.append("<p class='text-danger'>Unavailable. Remove this dish before checkout.</p>");
            content.append(form(w,"/cart/"+item.number("menu_item_id"))).append(quantity(item.number("quantity")))
                    .append(action("action","set","Update")).append(" <button class='btn btn-outline-dark' name='action' value='remove' formnovalidate>Remove</button></form></div>");
        }
        content.append("<p class='price mt-3'>Subtotal ").append(money(subtotal)).append("</p></div></section><section class='col-lg-5'><div class='panel'><h2>Checkout details</h2>")
                .append(form(w,"/checkout")).append(field("Recipient name","name","text",w.user.text("name"),"required maxlength='100'"))
                .append(field("Contact phone","phone","tel",w.user.text("phone"),"required maxlength='25'"))
                .append("<label>Fulfilment<select id='fulfilment' class='form-select' name='type'><option>Pickup</option><option>Delivery</option></select></label>")
                .append("<label id='address-field'>Delivery address<input class='form-control' name='address' maxlength='500'></label>")
                .append(button("Review total")).append("</form></div></section></div>");
        return w.page("Your cart",content.toString());
    }

    Reply edit(Web w,long dish) throws SQLException {
        post(w);w.require("Customer");
        long cart=one(w.db,"SELECT cart_id FROM cart WHERE customer_id=?",w.id()).number("cart_id");
        String action=w.get("action");
        if(action.equals("remove"))run(w.db,"DELETE FROM cart_item WHERE cart_id=? AND menu_item_id=?",cart,dish);
        else {
            if(!action.equals("add")&&!action.equals("set"))throw new Problem(400,"Invalid cart action.");
            if(one(w.db,"SELECT menu_item_id FROM menu_item WHERE menu_item_id=? AND available=1 AND active=1",dish)==null)
                throw new Problem(422,"This dish is unavailable.");
            long qty=Web.integer(w.get("quantity"),1,99);
            Row previous=one(w.db,"SELECT quantity FROM cart_item WHERE cart_id=? AND menu_item_id=?",cart,dish);
            if(action.equals("add")&&previous!=null)qty+=previous.number("quantity");
            if(qty>99)throw new Problem(422,"Limit each dish to 99 units.");
            run(w.db,"INSERT INTO cart_item VALUES(?,?,?) ON CONFLICT(cart_id,menu_item_id) DO UPDATE SET quantity=excluded.quantity",cart,dish,qty);
        }
        run(w.db,"UPDATE cart SET updated_at=CURRENT_TIMESTAMP WHERE cart_id=?",cart);
        return Reply.redirect(action.equals("add")?"/":"/cart");
    }
    private String quantity(long n) {return field("Quantity","quantity","number",Long.toString(n),"required min='1' max='99'");}
}
