package bayt;

import java.util.List;

public record Order(List<OrderItem> items, long deliveryFee) {
    public Order { items=List.copyOf(items); }
    public long total() { return items.stream().mapToLong(OrderItem::subtotal).sum()+deliveryFee; }
}

record OrderItem(long menuItemId,String name,long unitPrice,long quantity) {
    long subtotal() { return unitPrice*quantity; }
}

record DeliveryDetails(String type,String name,String phone,String address) {}

record Quote(long customerId,String token,DeliveryDetails details,Order order) {}
