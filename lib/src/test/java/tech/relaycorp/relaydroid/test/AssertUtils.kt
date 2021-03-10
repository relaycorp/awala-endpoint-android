package tech.relaycorp.relaydroid.test

import java.time.Duration
import java.time.ZonedDateTime
import org.junit.Assert

internal fun assertSameDateTime(date1: ZonedDateTime, date2: ZonedDateTime) =
    Assert.assertTrue(Duration.between(date1, date2).seconds < 2)
