// اجرای تکه‌ای (chunk) seed برای stress-test-phase7.mjs روی یک فایل دیتابیس
// ثابت — چون seed کامل (۱M مشتری + ۵M فاکتور) در یک اجرا از سقف زمانی هر
// دستور در این sandbox رد می‌شود. هر اجرا فقط یک بازه را درج می‌کند.
//
// Usage:
//   node --experimental-sqlite seed-chunk.node-only.mjs customers <from> <to> <dbPath>
//   node --experimental-sqlite seed-chunk.node-only.mjs invoices  <from> <to> <nCustomers> <dbPath>
import { getFileDb } from './fileDbConnection.node-only.mjs';

const [, , kind, fromArg, toArg, arg4, arg5] = process.argv;
const from = parseInt(fromArg, 10);
const to = parseInt(toArg, 10);

async function main() {
  const t0 = Date.now();
  if (kind === 'customers') {
    const dbPath = arg4;
    const db = await getFileDb(dbPath);
    await db.run('BEGIN');
    for (let i = from; i <= to; i++) {
      await db.run(`INSERT INTO customers (id, name, company, phone) VALUES (?, ?, ?, ?)`,
        [i, `مشتری تستی ${i}`, i % 7 === 0 ? `شرکت ${i}` : '', '0912' + String(1000000 + (i % 8999999))]);
    }
    await db.run('COMMIT');
  } else if (kind === 'invoices') {
    const nCustomers = parseInt(arg4, 10);
    const dbPath = arg5;
    const db = await getFileDb(dbPath);
    const statuses = ['draft', 'partial', 'paid'];
    await db.run('BEGIN');
    for (let i = from; i <= to; i++) {
      const custId = (i % nCustomers) + 1;
      const date = new Date(2022, 0, 1 + (i % 1460)).toISOString();
      await db.run(
        `INSERT INTO invoices (id, customerId, customerName, date, status, sent, sentToProduction, paidAmount, totalSheets, data)
         VALUES (?,?,?,?,?,?,?,?,?,?)`,
        [i, custId, `مشتری تستی ${custId}`, date, statuses[i % 3], i % 2, 0, (i % 3 === 1) ? 50000 : 0, (i % 20) + 1,
         JSON.stringify({ items: [{ lineTotal: 1000 + (i % 500), itemProfit: 100 + (i % 50), cartonQty: (i % 30) + 1, cartonName: `کارتن${i % 12}`, cartonLength: 30, cartonWidth: 20, cartonHeight: 15, layer: 2 }] })]
      );
    }
    await db.run('COMMIT');
  } else {
    throw new Error('unknown kind: ' + kind);
  }
  console.log(`chunk ${kind} [${from},${to}] done in ${Date.now() - t0}ms`);
}

main().catch(e => { console.error('CHUNK FAILED:', e); process.exit(1); });
