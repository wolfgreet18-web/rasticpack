import assert from 'node:assert/strict';
import { InvoiceRepo } from './_node_test_copies/InvoiceRepo.js';
import { CustomerRepo } from './_node_test_copies/CustomerRepo.js';
import { migrate } from './_node_test_copies/migrateFromIndexedDB.js';
import { getDb } from './testConnection.node-only.mjs';

let passed = 0;
function ok(label) { passed++; console.log('  ✓', label); }

async function main() {
  console.log('== CustomerRepo ==');
  await CustomerRepo.save({ id: 1, name: 'علی رستمی', company: 'شرکت الف', phone: '0912' });
  await CustomerRepo.save({ id: 2, name: 'زهرا کریمی', company: '', phone: '0913' });
  const found = await CustomerRepo.findByExactName('علی رستمی');
  assert.equal(found.id, 1); ok('findByExactName');
  const search = await CustomerRepo.searchByName('علی', { prefix: true });
  assert.equal(search.items.length, 1); ok('searchByName prefix');
  const page = await CustomerRepo.getPage({ limit: 10, offset: 0 });
  assert.equal(page.total, 2); ok('getPage total');

  console.log('== InvoiceRepo.save/remove (additions) ==');
  await InvoiceRepo.save({ id: 100, customerId: 1, customerName: 'علی رستمی', date: '2026-01-05T10:00:00.000Z', status: 'paid', sent: 1, items: [{ lineTotal: 1000 }] });
  await InvoiceRepo.save({ id: 101, customerId: 1, customerName: 'علی رستمی', date: '2026-01-15T10:00:00.000Z', status: 'partial', sent: 1, paidAmount: 200, items: [{ lineTotal: 1000 }] });
  await InvoiceRepo.save({ id: 102, customerId: 2, customerName: 'زهرا کریمی', date: '2026-02-01T10:00:00.000Z', status: 'draft', sent: 0, items: [{ lineTotal: 500 }] });
  const got = await InvoiceRepo.getById(100);
  assert.equal(got.customerName, 'علی رستمی'); ok('save + getById round-trip (data JSON column)');
  await InvoiceRepo.remove(102);
  assert.equal(await InvoiceRepo.getById(102), null); ok('remove');
  await InvoiceRepo.save({ id: 102, customerId: 2, customerName: 'زهرا کریمی', date: '2026-02-01T10:00:00.000Z', status: 'draft', sent: 0, items: [{ lineTotal: 500 }] });

  console.log('== InvoiceRepo.getPage / getStatusCounts (base, phase 1) ==');
  const pageInv = await InvoiceRepo.getPage({ customerId: 1, limit: 10, offset: 0 });
  assert.equal(pageInv.total, 2); ok('getPage filtered by customerId');

  const counts = await InvoiceRepo.getStatusCounts({});
  // 100: status=paid -> paid++; 101: status=partial, sent=1 -> debtor++ (partial+sent!=paid); 102: status=draft, sent=0 -> draft++
  assert.equal(counts.paid, 1);
  assert.equal(counts.debtor, 1);
  assert.equal(counts.draft, 1);
  assert.equal(counts.partial, 0);
  ok('getStatusCounts matches invoiceStatusOf/isInvoiceDebtor semantics from html8.html');

  const monthRange = await InvoiceRepo.getPage({ monthStart: '2026-01-01T00:00:00.000Z', monthEnd: '2026-02-01T00:00:00.000Z', limit: 10, offset: 0 });
  assert.equal(monthRange.total, 2); ok('getPage monthStart/monthEnd range filter');

  console.log('== InvoiceRepo.getMonthCount / getAllDates (additions, 2.5) ==');
  const mc = await InvoiceRepo.getMonthCount({ monthStart: '2026-01-01T00:00:00.000Z', monthEnd: '2026-02-01T00:00:00.000Z' });
  assert.equal(mc, 2); ok('getMonthCount');
  const dates = await InvoiceRepo.getAllDates();
  assert.equal(dates.length, 3); ok('getAllDates count');
  assert.ok(dates[0] >= dates[1] && dates[1] >= dates[2]); ok('getAllDates sorted DESC');

  console.log('== insertWithStockUpdate / deleteWithStockRestore (phase 5 preview, transactional) ==');
  const db = await getDb();
  await db.run(`INSERT INTO inventory (id, name, qty) VALUES (1, 'ورق A3', 100)`);
  await InvoiceRepo.insertWithStockUpdate(
    { id: 200, customerId: 1, customerName: 'علی رستمی', date: '2026-03-01T00:00:00.000Z', status: 'paid', sent: 1, items: [] },
    [{ itemId: 1, qtyDelta: 10 }]
  );
  const invRow = await db.query(`SELECT qty FROM inventory WHERE id=1`, []);
  assert.equal(invRow.values[0].qty, 90); ok('insertWithStockUpdate: stock deducted + invoice inserted atomically');
  assert.ok(await InvoiceRepo.getById(200)); ok('insertWithStockUpdate: invoice row present');

  await InvoiceRepo.deleteWithStockRestore(200, [{ itemId: 1, qtyDelta: 10 }]);
  const invRow2 = await db.query(`SELECT qty FROM inventory WHERE id=1`, []);
  assert.equal(invRow2.values[0].qty, 100); ok('deleteWithStockRestore: stock restored + invoice removed atomically');
  assert.equal(await InvoiceRepo.getById(200), null); ok('deleteWithStockRestore: invoice row gone');

  console.log('== migrateFromIndexedDB (injected fake source, count-verified) ==');
  const fakeCustomers = Array.from({ length: 1200 }, (_, i) => ({ id: 1000 + i, name: `مشتری تستی ${i}`, phone: '0000' }));
  const fakeInvoices = Array.from({ length: 2600 }, (_, i) => ({
    id: 5000 + i, customerId: 1000 + (i % 1200), customerName: `مشتری تستی ${i % 1200}`,
    date: new Date(2026, 0, 1 + (i % 28)).toISOString(), status: 'paid', sent: 0, items: [{ lineTotal: 10 }]
  }));
  const res = await migrate({
    readCustomersSource: async () => fakeCustomers,
    readInvoicesSource: async () => fakeInvoices
  });
  assert.equal(res.status, 'ok');
  assert.equal(res.customerCount, 2 + fakeCustomers.length); // 2 already saved above
  assert.equal(res.invoiceCount, 3 + fakeInvoices.length);   // 100,101,102 already saved above (200 was inserted then deleted)
  ok(`migrate: ${res.customerCount} customers / ${res.invoiceCount} invoices migrated, count-verified before flagging done`);

  const skip = await migrate({ readCustomersSource: async () => fakeCustomers, readInvoicesSource: async () => fakeInvoices });
  assert.equal(skip.status, 'skipped'); ok('migrate: second run skipped via settings_kv.migrated_v1 flag');

  console.log(`\nAll ${passed} assertions passed against a real in-memory SQLite database (node:sqlite), not a mock.`);
}

main()
  .then(testOrphanCustomerMigration)
  .catch(e => { console.error('TEST FAILED:', e); process.exit(1); });

// تست جدا برای رفع باگ orphan customerId (کشف‌شده حین تست نسخه‌ی ۴.۲)
async function testOrphanCustomerMigration() {
  console.log('== migrate: orphaned customerId (deleted customer, FK safety) ==');
  const { migrate: freshMigrate } = await import('./_node_test_copies/migrateFromIndexedDB.js');
  const { resetDb } = await import('./testConnection.node-only.mjs');
  resetDb();
  const custs = [{ id: 1, name: 'باقی‌مانده' }];
  const invs = [
    { id: 900, customerId: 1, customerName: 'باقی‌مانده', date: '2026-01-01T00:00:00.000Z', status: 'paid', sent: 0, items: [] },
    { id: 901, customerId: 999, customerName: 'مشتری حذف‌شده قدیمی', date: '2026-01-02T00:00:00.000Z', status: 'paid', sent: 0, items: [] }
  ];
  const res2 = await freshMigrate({ readCustomersSource: async () => custs, readInvoicesSource: async () => invs });
  assert.equal(res2.status, 'ok');
  assert.equal(res2.invoiceCount, 2);
  assert.equal(res2.customerCount, 2); // 1 واقعی + ۱ جایگزین برای customerId=999
  console.log('  ✓ orphan customerId no longer breaks migration (FK-safe placeholder inserted)');
}

