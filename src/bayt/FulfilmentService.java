package bayt;

import java.util.List;
import java.sql.*;
import static bayt.Database.run;

final class FulfilmentService {
    private final Connection db;
    FulfilmentService(Connection db) { this.db=db; }

    static List<String> actions(Row order) {
        return switch(order.text("status")) {
            case "Pending" -> List.of("Accepted","Rejected");
            case "Accepted" -> List.of("Preparing");
            case "Preparing" -> List.of(order.text("fulfilment_type").equals("Pickup")?"Ready for pickup":"Out for delivery");
            case "Ready for pickup","Out for delivery" -> List.of(order.text("payment_status").equals("Paid")?"Completed":"Record cash");
            default -> List.of();
        };
    }

    void update(Row order,String action,String reason,long version,long staff) throws SQLException {
        if(action.equals("Record cash") && order.text("payment_status").equals("Paid")) return;
        if(order.number("version")!=version) throw new Problem(409,"This order has changed. Reopen it and retry.");
        if(!actions(order).contains(action)) throw new Problem(422,"That action is unavailable for this order.");
        if(action.equals("Rejected") && reason.isBlank()) throw new Problem(422,"Enter a rejection reason.");
        if(action.equals("Record cash")) recordCash(order,staff);
        else applyTransition(order,action,reason,staff);
    }

    private void recordCash(Row order,long staff) throws SQLException {
        run(db,"UPDATE customer_order SET payment_status='Paid',paid_by=?,paid_at=CURRENT_TIMESTAMP,version=version+1 WHERE order_id=?",staff,order.number("order_id"));
    }

    private void applyTransition(Row order,String action,String reason,long staff) throws SQLException {
        long id=order.number("order_id");
        run(db,"UPDATE customer_order SET status=?,version=version+1 WHERE order_id=?",action,id);
        run(db,"INSERT INTO order_status_history(order_id,old_status,new_status,changed_by,reason) VALUES(?,?,?,?,?)",
                id,order.text("status"),action,staff,action.equals("Rejected")?reason:null);
    }
}
