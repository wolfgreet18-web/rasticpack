/**
 * CustomerRepo.js — Repository مشتریان (فاز ۱ نقشه‌راه).
 * امضای متدها دقیقاً همان چیزی است که در roadmap-infinite-records-v4.md
 * مستند شده: searchByName, getPage, findByExactName, insert/save/remove, bulkInsert.
 */

import { getDb } from './connection.js';

function rowToCustomer(row) {
  return {
    id: row.id,
    name: row.name,
    company: row.company || '',
    address: row.address || '',
    phone: row.phone || '',
    lat: row.lat,
    lng: row.lng,
    locationLink: row.locationLink || '',
    createdAt: row.createdAt
  };
}

export const CustomerRepo = {
  /**
   * جستجوی نام. `prefix:true` از الگوی «شروع با» (`name%`) استفاده می‌کند
   * که از ایندکس idx_cust_name بهره می‌برد (فاز ۳ برای جستجوی کامل‌تر FTS5
   * اضافه می‌کند)؛ در غیر این صورت `%query%` (کندتر روی دیتاست خیلی بزرگ).
   */
  async searchByName(query, { limit = 50, offset = 0, prefix = false } = {}) {
    const db = await getDb();
    const like = prefix ? `${query}%` : `%${query}%`;
    const totalRes = await db.query(`SELECT COUNT(*) AS c FROM customers WHERE name LIKE ?`, [like]);
    const total = Number(totalRes?.values?.[0]?.c || 0);
    const res = await db.query(
      `SELECT * FROM customers WHERE name LIKE ? ORDER BY name LIMIT ? OFFSET ?`,
      [like, limit, offset]
    );
    return { items: (res?.values || []).map(rowToCustomer), total };
  },

  async getPage({ limit = 50, offset = 0 } = {}) {
    const db = await getDb();
    const totalRes = await db.query(`SELECT COUNT(*) AS c FROM customers`, []);
    const total = Number(totalRes?.values?.[0]?.c || 0);
    const res = await db.query(`SELECT * FROM customers ORDER BY name LIMIT ? OFFSET ?`, [limit, offset]);
    return { items: (res?.values || []).map(rowToCustomer), total };
  },

  /** معادل findCustomerByName فعلی در html8.html — تطابق دقیق (نه substring). */
  async findByExactName(name) {
    const db = await getDb();
    const res = await db.query(`SELECT * FROM customers WHERE name = ? COLLATE NOCASE LIMIT 1`, [name]);
    const row = res?.values?.[0];
    return row ? rowToCustomer(row) : null;
  },

  async insert(customer) {
    return CustomerRepo.save(customer);
  },

  async save(customer) {
    if (!customer || customer.id == null) throw new Error('CustomerRepo.save: customer.id الزامی است.');
    const db = await getDb();
    const sql = `
      INSERT INTO customers (id, name, company, address, phone, lat, lng, locationLink)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT(id) DO UPDATE SET
        name=excluded.name, company=excluded.company, address=excluded.address,
        phone=excluded.phone, lat=excluded.lat, lng=excluded.lng, locationLink=excluded.locationLink
    `;
    await db.run(sql, [
      customer.id, customer.name || '', customer.company || null, customer.address || null,
      customer.phone || null, customer.lat ?? null, customer.lng ?? null, customer.locationLink || null
    ]);
    return true;
  },

  async remove(id) {
    const db = await getDb();
    await db.run(`DELETE FROM customers WHERE id = ?`, [id]);
    return true;
  },

  async bulkInsert(customers = []) {
    if (!customers.length) return 0;
    const db = await getDb();
    return db.withTransaction(async (tx) => {
      for (const c of customers) {
        await tx.run(
          `INSERT INTO customers (id, name, company, address, phone, lat, lng, locationLink)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?)
           ON CONFLICT(id) DO UPDATE SET
             name=excluded.name, company=excluded.company, address=excluded.address,
             phone=excluded.phone, lat=excluded.lat, lng=excluded.lng, locationLink=excluded.locationLink`,
          [c.id, c.name || '', c.company || null, c.address || null, c.phone || null, c.lat ?? null, c.lng ?? null, c.locationLink || null]
        );
      }
      return customers.length;
    });
  }
};

export default CustomerRepo;
