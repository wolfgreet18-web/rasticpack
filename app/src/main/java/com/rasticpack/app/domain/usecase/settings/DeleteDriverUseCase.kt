package com.rasticpack.app.domain.usecase.settings

import com.rasticpack.app.core.result.RasticResult
import com.rasticpack.app.domain.repository.DriverRepository
import javax.inject.Inject

/**
 * ══ مرحله ۲ (نقشه معماری v2.5) ══
 * معادل دقیق `removeDriver()` در 4.html — حذف بی‌سروصدا اگر راننده وجود نداشته
 * باشد (همان رفتار فعلی وب/Repository: `find(...) ?: return`)، بدون خطا.
 */
class DeleteDriverUseCase @Inject constructor(
    private val repo: DriverRepository
) {
    suspend operator fun invoke(id: Int): RasticResult<Unit> {
        repo.delete(id)
        return RasticResult.success(Unit)
    }
}
