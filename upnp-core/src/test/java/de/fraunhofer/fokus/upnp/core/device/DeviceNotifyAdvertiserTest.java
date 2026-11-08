package de.fraunhofer.fokus.upnp.core.device;

import de.fraunhofer.fokus.upnp.core.xml.UPnPStartupConfiguration;
import de.fraunhofer.fokus.upnp.ssdp.SSDPConstant;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static org.junit.Assert.*;

public class DeviceNotifyAdvertiserTest {
  private Device device() throws Exception {
    Path file = Files.createTempFile("ssdp-test-", ".xml");
    try {
      Files.write(file, ("<StartupConfiguration><WorkingDirectory></WorkingDirectory><Device>"
          + "<DeviceType>urn:schemas-upnp-org:device:MediaServer:1</DeviceType>"
          + "<ModelName>Discovery test</ModelName><FriendlyName>Discovery test</FriendlyName>"
          + "<UDN>uuid:discovery-test</UDN></Device></StartupConfiguration>").getBytes(StandardCharsets.UTF_8));
      // The base Device constructor does not start sockets or its event thread.
      return new Device(new UPnPStartupConfiguration(file.toFile()));
    } finally { Files.deleteIfExists(file); }
  }

  private static class Advertiser extends DeviceNotifyAdvertiser {
    volatile long time = 1000;
    final List<String> messages = new CopyOnWriteArrayList<String>();
    CountDownLatch aliveEntered;
    CountDownLatch releaseAlive;
    Advertiser(Device device) { super(device); }
    @Override protected long now() { return time; }
    @Override public void sendNotifyMessages(String type) {
      if (SSDPConstant.SSDP_ALIVE.equals(type) && aliveEntered != null) {
        aliveEntered.countDown();
        try { if (!releaseAlive.await(3, TimeUnit.SECONDS)) throw new AssertionError("Send blocked"); }
        catch (InterruptedException error) { throw new AssertionError(error); }
      }
      messages.add(type);
    }
  }

  @Test public void lateClientGetsARefreshWithinThirtySecondsWithoutChangingCacheLifetime() throws Exception {
    Device device = device();
    Advertiser advertiser = new Advertiser(device);
    advertiser.triggerEvents();
    advertiser.time = 1500;
    advertiser.triggerEvents();
    advertiser.time = 31499;
    advertiser.triggerEvents();
    assertEquals(2, advertiser.messages.size());
    advertiser.time = 31500;
    advertiser.triggerEvents();
    assertEquals(Arrays.asList(SSDPConstant.SSDP_BYEBYE, SSDPConstant.SSDP_ALIVE, SSDPConstant.SSDP_ALIVE), advertiser.messages);
    assertEquals(1800, device.getMaxage());
  }

  @Test public void shorterCacheLifetimesRefreshAtHalfTheirLifetime() throws Exception {
    Device device = device();
    device.setMaxage(10);
    Advertiser advertiser = new Advertiser(device);
    advertiser.triggerEvents();
    advertiser.time = 1500;
    advertiser.triggerEvents();
    advertiser.time = 6500;
    advertiser.triggerEvents();
    assertEquals(3, advertiser.messages.size());
  }

  @Test public void queuedCallbacksCannotAnnounceAliveAfterShutdown() throws Exception {
    Advertiser advertiser = new Advertiser(device());
    advertiser.triggerEvents();
    advertiser.time = 1500;
    advertiser.triggerEvents();
    advertiser.terminate();
    advertiser.terminate();
    advertiser.time = 900000;
    advertiser.triggerEvents();
    assertEquals(Arrays.asList(SSDPConstant.SSDP_BYEBYE, SSDPConstant.SSDP_ALIVE, SSDPConstant.SSDP_BYEBYE), advertiser.messages);
  }

  @Test public void shutdownWaitsForAnInFlightAnnouncementAndSendsByebyeLast() throws Exception {
    Advertiser advertiser = new Advertiser(device());
    advertiser.triggerEvents();
    advertiser.time = 1500;
    advertiser.aliveEntered = new CountDownLatch(1);
    advertiser.releaseAlive = new CountDownLatch(1);
    CountDownLatch stopping = new CountDownLatch(1);
    java.util.concurrent.ExecutorService threads = Executors.newFixedThreadPool(2);
    try {
      java.util.concurrent.Future<?> alive = threads.submit(advertiser::triggerEvents);
      assertTrue(advertiser.aliveEntered.await(2, TimeUnit.SECONDS));
      java.util.concurrent.Future<?> stop = threads.submit(() -> { stopping.countDown(); advertiser.terminate(); });
      assertTrue(stopping.await(2, TimeUnit.SECONDS));
      assertFalse(stop.isDone());
      advertiser.releaseAlive.countDown();
      alive.get(3, TimeUnit.SECONDS);
      stop.get(3, TimeUnit.SECONDS);
      advertiser.time = 900000;
      advertiser.triggerEvents();
      assertEquals(Arrays.asList(SSDPConstant.SSDP_BYEBYE, SSDPConstant.SSDP_ALIVE, SSDPConstant.SSDP_BYEBYE), advertiser.messages);
    } finally { advertiser.releaseAlive.countDown(); threads.shutdownNow(); }
  }
}
