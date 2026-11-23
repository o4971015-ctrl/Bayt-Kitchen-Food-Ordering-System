package bayt;

import java.sql.SQLException;
import java.util.*;
import static bayt.Views.*;
import static bayt.Database.*;
import static bayt.AccountController.*;

final class OrderController {
    private static final List<String> STATES=List.of("Pending","Accepted","Rejected","Preparing","Ready for pickup","Out for delivery","Completed");

    Reply list(Web w) throws SQLException {
        read(w);w.require("Customer","Staff");
        String status=w.get("status"), type=w.get("fulfilment_type");
        long page=w.get("page").isBlank()?1:Web.integer(w.get("page"),1,1000000);
        var query=new StringBuilder("SELECT * FROM customer_order WHERE 1=1");
        var args=new ArrayList<Object>();
        var content=new StringBuilder();
        if(w.role().equals("Customer")) {query.append(" AND customer_id=?");args.add(w.id());}
        else {
            if(STATES.contains(status)) {query.append(" AND status=?");args.add(status);}
            if(List.of("Pickup","Delivery").contains(type)) {query.append(" AND fulfilment_type=?");args.add(type);}
            content.append("<form class='d-flex flex-wrap align-items-end gap-2 mb-3'><label>Status<select class='form-select' name='status'>")
                    .append(option("","Any status",status));
            for(String state:STATES)content.append(option(state,state,status));
            content.append("</select></label><label>Fulfilment<select class='form-select' name='fulfilment_type'>")
                    .append(option("","Any fulfilment",type)).append(option("Pickup","Pickup",type)).append(option("Delivery","Delivery",type))
                    .append("</select></label>").append(button("Filter")).append("</form>");
        }
        args.add((page-1)*20);
        var rows=all(w.db,query.append(" ORDER BY order_id DESC LIMIT 21 OFFSET ?").toString(),args.toArray());
        if(rows.isEmpty())content.append("<p>No matching orders.</p>");
        for(var row:rows.subList(0,Math.min(20,rows.size()))) {
            long id=row.number("order_id");
            content.append("<article class='panel mb-2'>").append(link("/orders/"+id,"Order #"+id)).append(" · ")
                    .append(e(row.text("fulfilment_type"))).append(" · <span class='status'>").append(e(row.text("status")))
                    .append("</span> · ").append(money(row.number("total_amount"))).append("<div class='small'>")
                    .append(e(time(row.text("created_at")))).append("</div></article>");
        }
        String filters=(STATES.contains(status)?"&status="+encode(status):"")+(List.of("Pickup","Delivery").contains(type)?"&fulfilment_type="+type:"");
        if(page>1)content.append(link("/orders?page="+(page-1)+filters,"Previous")).append(" ");
        if(rows.size()>20)content.append(link("/orders?page="+(page+1)+filters,"Next"));
        return w.page(w.role().equals("Customer")?"My orders":"Kitchen orders",content.toString());
    }

    private String encode(String s) {return java.net.URLEncoder.encode(s,java.nio.charset.StandardCharsets.UTF_8);}

    Reply detail(Web w,long id,boolean update) throws SQLException {
        if(update){post(w);w.require("Staff");}
        else {read(w);w.require("Customer","Staff");}
        Row order=one(w.db,"SELECT * FROM customer_order WHERE order_id=?",id);
        if(order==null||(w.role().equals("Customer")&&order.number("customer_id")!=w.id()))
            throw new Problem(404,"This order is unavailable.");
        if(update) {
            String reason=w.get("reason").strip();
            if(reason.length()>500)throw new Problem(422,"Use at most 500 characters for the reason.");
            new FulfilmentService(w.db).update(order,w.get("action"),reason,Web.integer(w.get("version"),1,Long.MAX_VALUE),w.id());
            return Reply.redirect("/orders/"+id);
        }
        var content=new StringBuilder("<p>").append(link("/orders","Back to orders")).append("</p><div class='row g-3'><section class='col-md-7'><div class='panel'><h2>Receipt</h2><p>")
                .append(e(order.text("fulfilment_type"))).append(" · ").append(e(order.text("status"))).append(" · Cash ").append(e(order.text("payment_status").toLowerCase(Locale.ROOT)))
                .append("</p><p>").append(e(order.text("recipient_name"))).append(" · ").append(e(order.text("contact_phone")));
        if(!order.text("delivery_address").isBlank())content.append("<br>").append(e(order.text("delivery_address")));
        content.append("</p>");
        for(var item:all(w.db,"SELECT * FROM order_item WHERE order_id=? ORDER BY order_item_id",id))
            content.append("<div class='line'>").append(item.number("quantity")).append(" × ").append(e(item.text("item_name"))).append(" · ")
                    .append(money(item.number("unit_price"))).append(" each</div>");
        content.append("<p class='mt-3'>Delivery fee ").append(money(order.number("delivery_fee"))).append("</p><p class='price'>Total ")
                .append(money(order.number("total_amount"))).append("</p>");
        if(!order.text("paid_at").isBlank())content.append("<p>Cash recorded ").append(e(time(order.text("paid_at")))).append("</p>");
        if(w.role().equals("Staff")) for(String action:FulfilmentService.actions(order)) {
            content.append(form(w,"/orders/"+id+"/update")).append(hidden("version",order.number("version")));
            if(action.equals("Rejected"))content.append(field("Rejection reason","reason","text","","required maxlength='500'"));
            String label=switch(action){case "Accepted" -> "Accept order";case "Rejected" -> "Reject order";case "Preparing" -> "Start preparation";case "Completed" -> "Complete order";default -> action;};
            content.append(action("action",action,label)).append("</form>");
        }
        content.append("</div></section><section class='col-md-5'><div class='panel'><h2>Status history</h2>");
        for(var row:all(w.db,"SELECT h.*,u.name FROM order_status_history h JOIN user u ON h.changed_by=u.user_id WHERE h.order_id=? ORDER BY h.history_id",id)) {
            content.append("<div class='line'><strong>").append(e(row.text("new_status"))).append("</strong><div>").append(e(time(row.text("changed_at"))));
            if(w.role().equals("Staff"))content.append(" · ").append(e(row.text("name")));
            if(!row.text("reason").isBlank())content.append("<br>").append(e(row.text("reason")));
            content.append("</div></div>");
        }
        return w.page("Order #"+id,content.append("</div></section></div>").toString());
    }
}
