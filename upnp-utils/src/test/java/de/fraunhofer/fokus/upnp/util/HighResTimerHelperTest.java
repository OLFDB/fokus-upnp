package de.fraunhofer.fokus.upnp.util;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HighResTimerHelperTest {
  @Test public void timestampsUseTheMonotonicNanosecondClock() {
    long before = System.nanoTime();
    long timestamp = HighResTimerHelper.getTimeStamp();
    long after = System.nanoTime();
    assertTrue(timestamp - before >= 0);
    assertTrue(after - timestamp >= 0);
  }

  @Test public void intervalsAreReportedInMicroseconds() {
    assertEquals(2, HighResTimerHelper.getMicroseconds(100, 2100));
    assertEquals(-2, HighResTimerHelper.getMicroseconds(2100, 100));
    assertEquals(3600000000L, HighResTimerHelper.getMicroseconds(0, 3600000000000L));
  }
}
