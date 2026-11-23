package bayt;

import java.sql.SQLException;
import java.util.ArrayList;

final class CheckoutService {
    private final OrderRepository repository;
    private final long fee;
    CheckoutService(OrderRepository repository,long fee) { this.repository=repository; this.fee=fee; }

    Quote quote(long customer,DeliveryDetails details) throws SQLException {
        var cart=repository.cart(customer);
        if(cart.isEmpty()) throw new Problem(422,"Your cart is empty.");
        var items=new ArrayList<OrderItem>();
        for(var row:cart) {
            if (!row.yes("active") || !row.yes("available"))
                throw new Problem(422,"Remove unavailable dishes before checkout.");
            items.add(new OrderItem(row.number("menu_item_id"),row.text("name"),row.number("price"),row.number("quantity")));
        }
        return new Quote(customer,Security.token(),details,new Order(items,details.type().equals("Delivery")?fee:0));
    }

    CheckoutResult placeOrder(Quote original) throws SQLException {
        // 1.1.1 Return the original receipt when the same confirmation is retried.
        var existing=repository.findByToken(original.customerId(),original.token());
        if(existing!=null) return new CheckoutResult(existing.number("order_id"),null);
        // 1.1.2 Revalidate before saving Order and its owned OrderItems.
        var fresh=quote(original.customerId(),original.details());
        if(!fresh.order().equals(original.order())) return new CheckoutResult(0,fresh);
        return new CheckoutResult(repository.saveCheckout(original),null);
    }
}
record CheckoutResult(long orderId,Quote revised) {}
