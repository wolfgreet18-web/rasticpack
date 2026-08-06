package com.rasticpack.app.data.repository

import com.rasticpack.app.data.dao.InventorySheetDao
import com.rasticpack.app.data.entities.InventorySheetEntity
import com.rasticpack.app.domain.model.InventorySheet
import com.rasticpack.app.domain.repository.InventorySheetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * ══ مرحله ۳.۲ (نقشه معماری v2.7) — پیاده‌سازی واقعی InventorySheetRepository با Room ══
 * این کلاس جایگزین قدیمی `data/repo/InventoryRepository.kt` می‌شود (که خودش منطق
 * اعتبارسنجی را هم داشت). اینجا فقط دسترسی خام به داده + Mapping بین
 * `InventorySheetEntity` (Room) و `InventorySheet` (domain) است — منطق اعتبارسنجی
 * (ابعاد نامعتبر، تکراری‌بودن) به `domain/usecase/inventory/*UseCase.kt` منتقل شده،
 * طبق قانون لایه‌بندی نقشه‌ی معماری (بخش ۱: presentation → domain ← data).
 *
 * فایل قدیمی `data/repo/InventoryRepository.kt` عمداً حذف نشده — چون طبق قوانین
 * پرامپت معرفی («هیچ فایل موجودی را به بهانه‌ی ساده‌سازی حذف نکن، مگر دستور صریح
 * باشد») حذف فایل نیاز به تأیید صریح دارد. علاوه‌بر این، `Calc2ViewModel` (نیاز به
 * `getAllAsSheetItems()` برای `CalculatorEngine`) و `SettingsViewModel`/`SettingsScreen`
 * (نیاز به `layerLabel()` استاتیک) همچنان مستقیماً به `data.repo.InventoryRepository`
 * قدیمی وابسته‌اند — این وابستگی‌ها عمداً در این مرحله دست‌نخورده می‌مانند.
 */
class InventorySheetRepositoryImpl @Inject constructor(
    private val dao: InventorySheetDao
) : InventorySheetRepository {

    private fun InventorySheetEntity.toDomain() = InventorySheet(
        id = id, sw = sw, sh = sh, layer = layer, qty = qty, flute = flute, paperType = paperType
    )

    private fun InventorySheet.toEntity() = InventorySheetEntity(
        id = id, sw = sw, sh = sh, layer = layer, qty = qty, flute = flute, paperType = paperType
    )

    override fun observeAll(): Flow<List<InventorySheet>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getAll(): List<InventorySheet> = dao.getAll().map { it.toDomain() }

    override suspend fun getById(id: Int): InventorySheet? = dao.getAll().find { it.id == id }?.toDomain()

    override suspend fun getUniqueDims(): List<Pair<Double, Double>> {
        val seen = linkedSetOf<Pair<Double, Double>>()
        dao.getAll().forEach { seen.add(it.sh to it.sw) }
        return seen.sortedWith(compareBy({ it.first }, { it.second }))
    }

    override suspend fun insert(sheet: InventorySheet): InventorySheet {
        val newId = dao.insert(sheet.toEntity())
        return sheet.copy(id = newId.toInt())
    }

    override suspend fun update(sheet: InventorySheet) {
        dao.update(sheet.toEntity())
    }

    override suspend fun delete(id: Int) {
        dao.deleteById(id)
    }

    override suspend fun decreaseQty(sheetId: Int, amount: Int) {
        val sheet = getById(sheetId) ?: return
        val newQty = (sheet.qty - amount).coerceAtLeast(0)
        dao.update(sheet.copy(qty = newQty).toEntity())
    }

    override suspend fun increaseQty(id: Int, amount: Int) {
        val sheet = getById(id) ?: return
        dao.update(sheet.copy(qty = sheet.qty + amount).toEntity())
    }

    override suspend fun findOrCreateSheet(
        sh: Double,
        sw: Double,
        layer: String,
        flute: String,
        paperType: String
    ): InventorySheet {
        dao.getAll().find {
            it.sw == sw && it.sh == sh && it.layer == layer && it.flute == flute && it.paperType == paperType
        }?.let { return it.toDomain() }
        val newId = dao.insert(
            InventorySheetEntity(sw = sw, sh = sh, layer = layer, qty = 0, flute = flute, paperType = paperType)
        )
        return InventorySheet(id = newId.toInt(), sw = sw, sh = sh, layer = layer, qty = 0, flute = flute, paperType = paperType)
    }
}
