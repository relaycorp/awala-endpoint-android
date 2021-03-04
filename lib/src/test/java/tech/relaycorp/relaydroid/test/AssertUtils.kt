package tech.relaycorp.relaydroid.test

import org.junit.Assert
import java.time.Duration
import java.time.ZonedDateTime

internal fun assertSameDateTime(date1: ZonedDateTime, date2: ZonedDateTime) =
    Assert.assertTrue(Duration.between(date1, date2).seconds < 2)
