"""Optional HTTP integration test. Requires Python 3.10+ and Java 17+."""
import http.cookiejar
import html
import os
import re
import sqlite3
import subprocess
import tempfile
import time
import unittest
from pathlib import Path
from urllib import request, parse, error

ROOT = Path(__file__).resolve().parents[1]
PORT = 18653
BASE = f"http://127.0.0.1:{PORT}"

class NoRedirect(request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None

class Client:
    def __init__(self):
        self.opener = request.build_opener(NoRedirect(), request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.get('/login')

    def send(self, path, fields=None):
        data = parse.urlencode(fields).encode() if fields is not None else None
        try:
            response = self.opener.open(BASE + path, data, timeout=6)
        except error.HTTPError as caught:
            response = caught
        with response:
            body = response.read().decode()
            cookie = re.search(r"name='csrf' value='([^']+)'", body)
            if cookie:
                self.csrf = html.unescape(cookie[1])
            return response.code, body, response.headers.get('Location')

    def get(self, path):
        return self.send(path)

    def post(self, path, **fields):
        return self.send(path, fields | {'csrf': self.csrf})

class JavaAppTests(unittest.TestCase):
    @classmethod
    def start(cls):
        cls.process = subprocess.Popen(
            ['java', f'-Dbayt.port={PORT}', f'-Dbayt.data={cls.temp.name}', '-cp', 'classes' + os.pathsep + 'lib/*', 'bayt.Main'],
            cwd=ROOT, stdout=subprocess.DEVNULL,
        )
        for _ in range(100):
            if cls.process.poll() is not None:
                raise RuntimeError('The Java server exited during startup.')
            try:
                with request.urlopen(BASE, timeout=1) as response:
                    if response.status == 200:
                        return
            except OSError:
                time.sleep(.1)
        raise RuntimeError('The Java server did not start.')

    @classmethod
    def stop(cls):
        cls.process.terminate()
        cls.process.wait(timeout=10)

    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory()
        cls.addClassCleanup(cls.temp.cleanup)
        cls.addClassCleanup(cls.stop)
        cls.start()
        cls.db = Path(cls.temp.name) / 'kitchen.sqlite3'
        cls.accounts = {}
        for line in (Path(cls.temp.name)/'demo-accounts.txt').read_text().splitlines():
            role, email, password = map(str.strip, line.split('|'))
            cls.accounts[role] = (email,password)

    def login(self, role, redirect=None):
        client=Client()
        email,password=self.accounts[role]
        response=client.post('/login',email=email,password=password)
        self.assertEqual(response[0],303)
        if redirect:
            self.assertEqual(response[2],redirect)
        return client

    def details(self, delivery=False):
        return dict(name='Demo Name', phone='01012345678',type='Delivery' if delivery else 'Pickup',address='Sample street' if delivery else '')

    def token(self, body):
        return html.unescape(re.search(r"name='quote' value='([^']+)'",body)[1])

    def order(self, client, delivery=False):
        self.assertEqual(client.post('/cart/3',action='add',quantity=2)[0],303)
        status,body,_=client.post('/checkout',**self.details(delivery))
        self.assertEqual(status,200,body)
        self.assertIn('195.00 EGP' if delivery else '170.00 EGP',body)
        token=self.token(body)
        status,body,path=client.post('/checkout/confirm',quote=token)
        self.assertEqual(status,303,body)
        self.assertEqual(client.post('/checkout/confirm',quote=token)[2],path)
        return path, token

    def change(self, client, path, action, version, expected=303, **more):
        status,body,_=client.post(path+'/update',action=action,version=version,**more)
        self.assertEqual(status,expected,body)

    def test_01_pickup_delivery_and_staff_rules(self):
        customer=self.login('Customer')
        staff=self.login('Staff')
        admin=self.login('Admin')
        for delivery in (False,True):
            path,_=self.order(customer,delivery)
            self.assertIn(path,staff.get('/orders')[1])
            self.change(staff,path,'Preparing',1,422)
            self.change(admin,path,'Accepted',1,403)
            self.change(staff,path,'Accepted',1)
            self.change(staff,path,'Preparing',1,409)
            self.change(staff,path,'Preparing',2)
            self.change(staff,path,'Ready for pickup' if delivery else 'Out for delivery',3,422)
            self.change(staff,path,'Out for delivery' if delivery else 'Ready for pickup',3)
            self.change(staff,path,'Completed',4,422)
            self.change(staff,path,'Record cash',4)
            self.change(staff,path,'Record cash',4)
            self.change(staff,path,'Completed',5)
            result=customer.get(path)[1]
            self.assertIn('Completed',result)
            self.assertIn('Cash paid',result)

    def test_02_accounts_and_access(self):
        guest=Client()
        form=dict(name='Another Customer',email='another@demo.test',password='SamplePass!123',phone='01012345678')
        self.assertEqual(guest.post('/register',**(form|{'password':'too-short'}))[0],422)
        self.assertEqual(guest.post('/register',**form)[0],303)
        self.assertEqual(guest.post('/register',**form)[0],422)
        self.assertEqual(guest.post('/login',email=form['email'],password=form['password'])[0],303)
        self.assertEqual(guest.get('/orders/1')[0],404)
        self.assertEqual(guest.get('/admin/menu')[0],403)
        admin=self.login('Admin')
        fields=form|{'email':'newstaff@demo.test'}
        self.assertEqual(admin.post('/admin/staff',**fields)[0],303)
        staff=Client()
        self.assertEqual(staff.post('/login',email=fields['email'],password=fields['password'])[2],'/password')
        self.assertEqual(staff.get('/orders')[2],'/password')
        self.assertEqual(staff.post('/password',password='DifferentPass123!')[2],'/orders')
        with sqlite3.connect(self.db) as db:
            uid=db.execute('SELECT user_id FROM user WHERE email=?',(fields['email'],)).fetchone()[0]
        self.assertEqual(admin.post(f'/admin/staff/{uid}',active=0)[0],303)
        self.assertEqual(staff.get('/orders')[2],'/login')
        self.assertEqual(admin.post(f'/admin/staff/{uid}',active=1)[0],303)
        self.assertEqual(staff.post('/login',email=fields['email'],password='DifferentPass123!')[2],'/orders')
        self.assertIn('Deactivate',admin.get('/admin/audit')[1])

    def test_03_cart_price_snapshot_and_rollback(self):
        customer=self.login('Customer')
        admin=self.login('Admin')
        self.assertEqual(customer.post('/cart/3',action='add',quantity=100)[0],422)
        self.assertEqual(customer.post('/cart/3',action='add',quantity=2)[0],303)
        self.assertEqual(customer.post('/logout')[0],303)
        customer=self.login('Customer')
        self.assertIn('170.00 EGP',customer.get('/cart')[1])
        self.assertEqual(customer.post('/checkout',**(self.details(True)|{'address':''}))[0],422)
        old=self.token(customer.post('/checkout',**self.details())[1])
        # Change a menu price through the normal Admin page.
        item=dict(id=3,category=2,name='Koshary bowl',description='Rice and lentils',price='90.00',state='Available')
        self.assertEqual(admin.post('/admin/items',**item)[0],303)
        self.assertEqual(admin.post('/admin/categories',id=2,action='delete')[0],422)
        status,page,_=customer.post('/checkout/confirm',quote=old)
        self.assertEqual(status,200,page)
        self.assertIn('180.00 EGP',page)
        new=self.token(page)
        with sqlite3.connect(self.db) as db:
            before=db.execute('SELECT count(*) FROM customer_order').fetchone()[0]
            db.execute("CREATE TRIGGER simulate_failure BEFORE INSERT ON order_item BEGIN SELECT RAISE(ABORT,'test'); END")
        self.assertEqual(customer.post('/checkout/confirm',quote=new)[0],422)
        with sqlite3.connect(self.db) as db:
            self.assertEqual(db.execute('SELECT count(*) FROM customer_order').fetchone()[0],before)
            db.execute('DROP TRIGGER simulate_failure')
        self.assertIn('180.00 EGP',customer.get('/cart')[1])
        status,body,order=customer.post('/checkout/confirm',quote=new)
        self.assertEqual(status,303,body)
        staff=self.login('Staff')
        self.change(staff,order,'Rejected',1,422)
        self.change(staff,order,'Rejected',1,reason='Kitchen closed')
        self.assertIn('Kitchen closed',customer.get(order)[1])
        self.assertEqual(admin.post('/admin/items',**(item|{'state':'Archived','price':'99.00'}))[0],303)
        self.assertNotIn('Koshary bowl',customer.get('/')[1])
        self.assertIn('180.00 EGP',customer.get(order)[1])
        self.stop()
        self.start()
        self.assertIn('180.00 EGP',self.login('Customer').get(order)[1])
        self.assertEqual(self.login('Customer').post('/checkout/confirm',quote=new)[2],order)

    def test_04_session_lock_and_backup(self):
        client=Client()
        for _ in range(5):
            self.assertEqual(client.post('/login',email='unknown@demo.test',password='WrongPassword99')[0],401)
        self.assertEqual(client.post('/login',email='unknown@demo.test',password='WrongPassword99')[0],429)
        customer=self.login('Customer')
        self.assertEqual(customer.send('/cart/1',{'action':'add','quantity':'1'})[0],400)
        with sqlite3.connect(self.db) as db:
            db.execute("UPDATE login_session SET last_seen=0")
        self.assertEqual(customer.get('/cart')[2],'/login')
        backup_dir=Path(self.temp.name)/'backups'
        for _ in range(30):
            copies=list(backup_dir.glob('*.sqlite3'))
            if copies:
                break
            time.sleep(.1)
        self.assertTrue(copies)
        with sqlite3.connect(copies[0]) as db:
            self.assertEqual(db.execute('PRAGMA integrity_check').fetchone()[0],'ok')
            self.assertEqual(db.execute('SELECT count(*) FROM menu_item').fetchone()[0],6)

if __name__=='__main__':
    unittest.main(verbosity=2)
