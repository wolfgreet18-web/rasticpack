/**
 * InvoiceCache.js — لایه‌ی LRU مستقل در RAM برای فاکتورها (ریزمرحله‌ی ۲.۷.۱
 * نقشه‌راه، نسخه‌ی ۴.۱۲). هدف: بعد از این‌که loadState دیگر کل جدول
 * invoices را از IndexedDB نمی‌خواند (۲.۷.۲)، این کش همان نقش «چیزی به‌جای
 * آرایه‌ی کامل» را بازی می‌کند — با ظرفیت ثابت، مستقل از تعداد کل فاکتورها.
 *
 * پیاده‌سازی از `Map` استفاده می‌کند چون ترتیب درج/دسترسی کلیدهای `Map` در
 * جاوااسکریپت تضمین‌شده است (insertion order) — یعنی «قدیمی‌ترین» همیشه
 * اولین کلید در `keys()` است، بدون نیاز به لیست پیوندی دستی.
 */
export class InvoiceCache {
  /**
   * @param {number} capacity ظرفیت حداکثری (پیش‌فرض ۵۰۰ رکورد طبق نقشه‌راه)
   */
  constructor(capacity = 500) {
    if (!Number.isFinite(capacity) || capacity <= 0) {
      throw new Error('InvoiceCache: capacity باید یک عدد مثبت باشد');
    }
    this.capacity = capacity;
    /** @type {Map<any, any>} کلید = invoice.id */
    this._map = new Map();
  }

  /**
   * @param {any} id
   * @returns {any|undefined} فاکتور، یا undefined اگر در کش نبود (miss)
   */
  get(id) {
    if (!this._map.has(id)) return undefined;
    const invoice = this._map.get(id);
    // انتقال به انتهای ترتیب LRU (جدیدترین استفاده‌شده)
    this._map.delete(id);
    this._map.set(id, invoice);
    return invoice;
  }

  /**
   * @param {any} id
   * @returns {boolean} بدون تغییر ترتیب LRU — فقط برای بررسی وجود
   */
  has(id) {
    return this._map.has(id);
  }

  /**
   * درج/به‌روزرسانی یک فاکتور. اگر ظرفیت پر باشد، قدیمی‌ترین رکورد
   * (کمترین استفاده‌شده) به‌صورت خودکار حذف می‌شود — بدون دخالت UI.
   * @param {any} id
   * @param {any} invoice
   */
  set(id, invoice) {
    if (this._map.has(id)) {
      this._map.delete(id);
    } else if (this._map.size >= this.capacity) {
      const oldestKey = this._map.keys().next().value;
      this._map.delete(oldestKey);
    }
    this._map.set(id, invoice);
    return invoice;
  }

  /**
   * چند رکورد را یک‌جا اضافه می‌کند (مثلاً یک صفحه‌ی کامل از getPage) —
   * ترتیب eviction دقیقاً مثل فراخوانی‌های متوالی set است.
   * @param {any[]} invoices
   * @param {(invoice:any)=>any} [idOf] در صورت نیاز، استخراج id سفارشی
   */
  setMany(invoices = [], idOf = (inv) => inv.id) {
    for (const inv of invoices) this.set(idOf(inv), inv);
  }

  delete(id) {
    return this._map.delete(id);
  }

  clear() {
    this._map.clear();
  }

  get size() {
    return this._map.size;
  }

  /** آرایه‌ی رکوردهای فعلاً کش‌شده، به ترتیب LRU (قدیمی → جدید). */
  values() {
    return Array.from(this._map.values());
  }
}

export default InvoiceCache;
