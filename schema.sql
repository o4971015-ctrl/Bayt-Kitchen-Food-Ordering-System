PRAGMA foreign_keys=ON;
CREATE TABLE IF NOT EXISTS user(
 user_id INTEGER PRIMARY KEY, name TEXT NOT NULL, email TEXT NOT NULL UNIQUE COLLATE NOCASE,
 phone TEXT NOT NULL, password_hash TEXT NOT NULL, role TEXT NOT NULL CHECK(role IN ('Customer','Staff','Admin')),
 active INTEGER NOT NULL DEFAULT 1, must_change_password INTEGER NOT NULL DEFAULT 0,
 created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE IF NOT EXISTS category(category_id INTEGER PRIMARY KEY,name TEXT UNIQUE NOT NULL COLLATE NOCASE);
CREATE TABLE IF NOT EXISTS menu_item(menu_item_id INTEGER PRIMARY KEY,category_id INTEGER NOT NULL REFERENCES category,
 name TEXT NOT NULL,description TEXT NOT NULL,price INTEGER NOT NULL CHECK(price>0),available INTEGER NOT NULL DEFAULT 1,active INTEGER NOT NULL DEFAULT 1);
CREATE TABLE IF NOT EXISTS cart(cart_id INTEGER PRIMARY KEY,customer_id INTEGER UNIQUE NOT NULL REFERENCES user,updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE IF NOT EXISTS cart_item(cart_id INTEGER NOT NULL REFERENCES cart ON DELETE CASCADE,menu_item_id INTEGER NOT NULL REFERENCES menu_item,quantity INTEGER NOT NULL CHECK(quantity BETWEEN 1 AND 99),PRIMARY KEY(cart_id,menu_item_id));
CREATE TABLE IF NOT EXISTS customer_order(order_id INTEGER PRIMARY KEY, customer_id INTEGER NOT NULL REFERENCES user,checkout_token TEXT UNIQUE NOT NULL,
 created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,fulfilment_type TEXT NOT NULL CHECK(fulfilment_type IN ('Pickup','Delivery')),
 recipient_name TEXT NOT NULL,contact_phone TEXT NOT NULL,delivery_address TEXT,delivery_fee INTEGER NOT NULL CHECK(delivery_fee>=0),
 total_amount INTEGER NOT NULL CHECK(total_amount>0),status TEXT NOT NULL DEFAULT 'Pending' CHECK(status IN ('Pending','Accepted','Rejected','Preparing','Ready for pickup','Out for delivery','Completed')),
 payment_status TEXT NOT NULL DEFAULT 'Unpaid' CHECK(payment_status IN ('Paid','Unpaid')),paid_at TEXT,paid_by INTEGER REFERENCES user,version INTEGER NOT NULL DEFAULT 1,
 CHECK((payment_status='Paid' AND paid_at IS NOT NULL AND paid_by IS NOT NULL) OR (payment_status='Unpaid' AND paid_at IS NULL AND paid_by IS NULL)),
 CHECK((fulfilment_type='Pickup' AND delivery_fee=0 AND delivery_address IS NULL) OR (fulfilment_type='Delivery' AND length(trim(delivery_address))>0 AND delivery_address IS NOT NULL)));
CREATE TABLE IF NOT EXISTS order_item(order_item_id INTEGER PRIMARY KEY,order_id INTEGER NOT NULL REFERENCES customer_order,menu_item_id INTEGER NOT NULL REFERENCES menu_item,
 item_name TEXT NOT NULL,unit_price INTEGER NOT NULL CHECK(unit_price>0),quantity INTEGER NOT NULL CHECK(quantity BETWEEN 1 AND 99),UNIQUE(order_id,menu_item_id));
CREATE TABLE IF NOT EXISTS order_status_history(history_id INTEGER PRIMARY KEY,order_id INTEGER NOT NULL REFERENCES customer_order,
 old_status TEXT,new_status TEXT NOT NULL,changed_by INTEGER NOT NULL REFERENCES user,changed_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,reason TEXT);
CREATE TABLE IF NOT EXISTS audit_event(event_id INTEGER PRIMARY KEY,actor_id INTEGER NOT NULL REFERENCES user,action TEXT NOT NULL,entity_type TEXT NOT NULL,entity_id INTEGER NOT NULL,occurred_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE INDEX IF NOT EXISTS order_owner ON customer_order(customer_id,created_at);
CREATE INDEX IF NOT EXISTS order_status ON customer_order(status,created_at);
CREATE INDEX IF NOT EXISTS history_order ON order_status_history(order_id,changed_at);
CREATE INDEX IF NOT EXISTS menu_category ON menu_item(category_id);
CREATE TABLE IF NOT EXISTS login_session(token TEXT PRIMARY KEY,user_id INTEGER NOT NULL REFERENCES user,last_seen INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS login_attempt(email TEXT NOT NULL,at INTEGER NOT NULL);
CREATE INDEX IF NOT EXISTS attempt_email ON login_attempt(email,at);
CREATE TABLE IF NOT EXISTS login_lock(email TEXT PRIMARY KEY,until_time INTEGER NOT NULL);
