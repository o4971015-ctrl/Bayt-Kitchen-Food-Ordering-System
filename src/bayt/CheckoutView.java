package bayt;

import static bayt.Views.*;

final class CheckoutView {
    static String render(Web w,Quote quote,boolean revised) {
        var d=quote.details();
        var content=new StringBuilder("<section class='panel form-panel'>");
        if(revised)content.append("<p class='alert alert-warning' role='status'>The cart, price or fee changed. Review and confirm the revised total.</p>");
        content.append("<h2>").append(e(d.type())).append("</h2><p>").append(e(d.name())).append(" · ").append(e(d.phone()));
        if(d.address()!=null)content.append("<br>").append(e(d.address()));
        content.append("</p>");
        for(var item:quote.order().items())content.append("<div class='line'>").append(item.quantity()).append(" × ").append(e(item.name())).append(" · ").append(money(item.subtotal())).append("</div>");
        return content.append("<p class='mt-3'>Delivery fee ").append(money(quote.order().deliveryFee())).append("</p><p class='price'>Total ")
                .append(money(quote.order().total())).append("</p><p>Cash at handover</p>").append(form(w,"/checkout/confirm")).append(hidden("quote",quote.token()))
                .append(button("Confirm order")).append(" ").append(link("/cart","Back to cart")).append("</form></section>").toString();
    }
}
