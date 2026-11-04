package com.foldfinance.core.data.db

import androidx.room.TypeConverter
import com.foldfinance.core.model.ParseStatus
import com.foldfinance.core.model.Source
import com.foldfinance.core.model.SubscriptionPeriod
import com.foldfinance.core.model.SubscriptionStatus
import com.foldfinance.parser.Direction
import com.foldfinance.parser.Kind
import com.foldfinance.parser.MessageChannel
import java.time.Instant

/**
 * Конвертеры Room: время хранится как epoch millis (UTC), перечисления — по имени.
 * Имена перечислений — часть схемы БД: переименование требует миграции.
 */
class Converters {
    @TypeConverter fun instantToLong(value: Instant?): Long? = value?.toEpochMilli()
    @TypeConverter fun longToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter fun directionToString(value: Direction?): String? = value?.name
    @TypeConverter fun stringToDirection(value: String?): Direction? = value?.let(Direction::valueOf)

    /** Неизвестный вид (например, после отката версии) читается как [Kind.UNKNOWN]. */
    @TypeConverter fun kindToString(value: Kind?): String? = value?.name
    @TypeConverter fun stringToKind(value: String?): Kind? =
        value?.let { name -> Kind.entries.firstOrNull { it.name == name } ?: Kind.UNKNOWN }

    @TypeConverter fun channelToString(value: MessageChannel?): String? = value?.name
    @TypeConverter fun stringToChannel(value: String?): MessageChannel? = value?.let(MessageChannel::valueOf)

    @TypeConverter fun sourceToString(value: Source?): String? = value?.name
    @TypeConverter fun stringToSource(value: String?): Source? = value?.let(Source::valueOf)

    @TypeConverter fun parseStatusToString(value: ParseStatus?): String? = value?.name
    @TypeConverter fun stringToParseStatus(value: String?): ParseStatus? = value?.let(ParseStatus::valueOf)

    @TypeConverter fun periodToString(value: SubscriptionPeriod?): String? = value?.name
    @TypeConverter fun stringToPeriod(value: String?): SubscriptionPeriod? = value?.let(SubscriptionPeriod::valueOf)

    @TypeConverter fun subStatusToString(value: SubscriptionStatus?): String? = value?.name
    @TypeConverter fun stringToSubStatus(value: String?): SubscriptionStatus? = value?.let(SubscriptionStatus::valueOf)
}
