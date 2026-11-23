# Bayt Kitchen Phase 2 — Java

Bayt Kitchen is the food ordering system from the included final Word SRS. It uses Java 17, the JDK HTTP server, SQLite through JDBC, and a Bootstrap web interface. The ZIP includes the compiled Java classes and libraries. Running the app requires Java 17 or newer.

## Start the application

1. Extract the ZIP.
2. On Windows, run **Start_Windows.bat** inside the **BaytKitchenJava** folder. On macOS or Linux, run **sh Start_Linux_Mac.sh** from that folder.
3. Keep the terminal open and visit **http://127.0.0.1:8000**.
4. Open **instance/demo-accounts.txt** to find the generated passwords for **customer@demo.test**, **staff@demo.test**, and **admin@demo.test**.

The database is created under **instance/kitchen.sqlite3** on first launch. All users, menu changes, carts, confirmed orders, and audit history persist across restarts. Unconfirmed quotes expire after 30 minutes and require a fresh review after a server restart. The bundled restaurant data is for demonstration. The delivery fee is EGP 25.00 and can be configured through **KITCHEN_FEE_PIASTRES**.

## Demonstrate the order lifecycle

1. As Customer, add two Koshary bowls. Choose pickup for EGP 170.00, or delivery for EGP 195.00 with a sample delivery address. Review the quote and confirm.
2. In a separate browser session, sign in as Staff and open the order. Accept it, start preparation, and choose the correct fulfilment action. Record cash at handover and complete the order.
3. Refresh the customer receipt to see **Completed**, **Cash paid**, and the complete status history.
4. As Admin, try menu and category edits, staff registration and deactivation, and the audit log.

## UML to Java

| SRS module | Java code |
|---|---|
| Account access | AccountController and Security |
| Menu and cart | ShoppingController and OrderRepository |
| Checkout | CheckoutController, CheckoutView, CheckoutService, OrderRepository, Order and OrderItem |
| Customer order history | OrderController |
| Restaurant operations | OrderController and FulfilmentService |
| Menu administration | AdminController and the menu and audit tables |
| Staff administration | AdminController, AccountController and the user and session tables |

The checkout sequence and communication diagrams describe the same method calls. Message **1** confirms a reviewed quote. **1.1** invokes CheckoutService.placeOrder. **1.1.1** looks for a previous confirmation using OrderRepository.findByToken. The service then revalidates the quote. **1.1.2** invokes OrderRepository.saveCheckout inside the controller's transaction. The repository writes the order, line snapshots and first history entry and clears the cart before commit. Order owns its immutable list of OrderItems and delegates line calculation to OrderItem.subtotal.

CheckoutService holds an OrderRepository association. OrderRepository holds a JDBC Connection association. CheckoutController delegates rendering to CheckoutView. FulfilmentService checks the order version and allowed action before recording payment or applying a transition.

The food-ordering example in Tutorial 07 illustrates an online payment gateway. This project follows the SRS cash-at-handover rule. Prices use integer piastres in SQLite and Java. The schema includes the nine SRS business tables and supporting session and login-attempt tables.

## Rebuild and verify

From this folder, use a JDK 17+ to compile the sources.

```text
javac --release 17 -encoding UTF-8 -cp "lib/*" -d classes src/bayt/*.java
```

If your Windows shell passes the wildcard literally, use **Build_Windows.bat**. To run the included integration checks, execute **python tests/check_app.py** from this folder. Python is used only by this optional test harness. The app and its business logic run in Java. The checks make HTTP requests to a temporary local server and use a temporary database.

The final Word SRS contains the manual acceptance cases. Manual execution and recording, the specified 10-minute load test, and the deployment restore drill remain separate assessment evidence.
