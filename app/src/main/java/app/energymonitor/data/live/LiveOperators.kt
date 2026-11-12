package app.energymonitor.data.live

import app.energymonitor.domain.LiveEvent
import app.energymonitor.domain.LoadLevel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map

/**
 * Сповіщення про зміну рівня навантаження.
 *
 *  1. map: з події беремо лише рівень;
 *  2. distinctUntilChanged: пропускаємо тільки зміни рівня, а не кожне показання;
 *  3. debounce: якщо рівень "смикається" швидше за паузу, користувач отримає лише
 *     останній стійкий стан, а не серію сповіщень;
 *  4. distinctUntilChanged ще раз, бо debounce міг залишити однакові сусідні рівні;
 *  5. filter: "Норма" сповіщення не потребує.
 *
 * Порядок важливий: debounce перед distinctUntilChanged не спрацював би ніколи,
 * бо показання надходять частіше за паузу й таймер скидався б щоразу.
 */
@OptIn(FlowPreview::class)
fun Flow<LiveEvent>.levelAlerts(debounceMillis: Long): Flow<LoadLevel> =
    filterIsInstance<LiveEvent.Sample>()
        .map { it.sample.level }
        .distinctUntilChanged()
        .debounce(debounceMillis)
        .distinctUntilChanged()
        .filter { it != LoadLevel.NORMAL }
