package bayt;

import java.sql.*;
import java.util.List;
import static bayt.Database.*;

final class OrderRepository {
    private final Connection db;
    OrderRepository(Connection db) { this.db=db; }

    Row findByToken(long customer,String token) throws SQLException {
        return one(db,"SELECT order_id FROM customer_order WHERE customer_id=? AND checkout_token=?",customer,token);
    }

    List<Row> cart(long customer) throws SQLException {
        return all(db,"SELECT m.*, ci.quantity FROM cart_item ci JOIN menu_item m USING(menu_item_id) JOIN cart c USING(cart_id) WHERE c.customer_id=? ORDER BY m.menu_item_id",customer);
    }

    long saveCheckout(Quote quote) throws SQLException {
        var d=quote.details(); var order=quote.order();
        long id=insert(db,"INSERT INTO customer_order(customer_id,checkout_token,fulfilment_type,recipient_name,contact_phone,delivery_address,delivery_fee,total_amount) VALUES(?,?,?,?,?,?,?,?)",
                quote.customerId(),quote.token(),d.type(),d.name(),d.phone(),d.address(),order.deliveryFee(),order.total());
        for (var item:order.items()) {
            run(db,"INSERT INTO order_item(order_id,menu_item_id,item_name,unit_price,quantity) VALUES(?,?,?,?,?)",
                    id,item.menuItemId(),item.name(),item.unitPrice(),item.quantity());
        }
        run(db,"INSERT INTO order_status_history(order_id,new_status,changed_by) VALUES(?,'Pending',?)",id,quote.customerId());
        run(db,"DELETE FROM cart_item WHERE cart_id=(SELECT cart_id FROM cart WHERE customer_id=?)",quote.customerId());
        run(db,"UPDATE cart SET updated_at=CURRENT_TIMESTAMP WHERE customer_id=?",quote.customerId());
        return id;
    }
}
