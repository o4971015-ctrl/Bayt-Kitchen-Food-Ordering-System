package bayt;

import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;

final class CheckoutController {
    private final long fee;
    private final ConcurrentHashMap<String,PendingQuote> pending=new ConcurrentHashMap<>();
    private record PendingQuote(Quote quote,long expires) {}
    CheckoutController(long fee) {this.fee=fee;}

    Reply handle(Web w,String path) throws SQLException {
        AccountController.post(w);w.require("Customer");
        var repository=new OrderRepository(w.db);
        var service=new CheckoutService(repository,fee);
        if(path.equals("/checkout")) {
            String type=w.get("type");
            if(!type.equals("Delivery")&&!type.equals("Pickup"))throw new Problem(422,"Choose pickup or delivery.");
            var details=new DeliveryDetails(type,w.required("name",100),w.required("phone",25),type.equals("Delivery")?w.required("address",500):null);
            return review(w,service.quote(w.id(),details),false);
        }
        String token=w.required("quote",60);
        Row existing=repository.findByToken(w.id(),token);
        if(existing!=null)return Reply.redirect("/orders/"+existing.number("order_id"));
        var saved=pending.get(token);
        if(saved==null||saved.expires()<System.currentTimeMillis()||saved.quote().customerId()!=w.id())
            throw new Problem(422,"Open your cart and review a fresh quote.");
        // 1 -> 1.1 Route the confirmation to the application service.
        var result=service.placeOrder(saved.quote());
        if(result.revised()!=null)return review(w,result.revised(),true);
        return Reply.redirect("/orders/"+result.orderId());
    }

    private Reply review(Web w,Quote quote,boolean revised) {
        long now=System.currentTimeMillis();
        pending.values().removeIf(item->item.expires()<now);
        if(pending.size()>=10000)throw new Problem(503,"Checkout is busy. Try again shortly.");
        pending.put(quote.token(),new PendingQuote(quote,now+1800000));
        return w.page("Review your order",CheckoutView.render(w,quote,revised));
    }
}
