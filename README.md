Fraunhofer FOKUS UPnP Stack
===========================

UPnP is a technology for automatically connecting heterogeneous devices and offers the following features:

 * Automatic address configuration through Auto-IP or DHCP
 * Automatic discovery and automatic sign-off of devices
 * Self-explanatory description of device features through XML
 * Triggering of actions on devices through SOAP messages
 * Receiving of events when the state of a device has changed.

The UPnP-standard is maintained by the UPnP-Consortium. On the Web site http://www.upnp.org you can find the documents that are necessary for developing custom UPnP-compatible devices.

At the time being, UPnP is mainly used in the area of multimedia for connecting media such as photos, music and videos (over a media service) with the right playback devices.

But UPnP is able to do more than merely connecting AV devices. It is suited for making all IP-capable devices accessible on a network in a universally usable form. The range of devices includes sensors or actuators but also virtual devices that e.g. retrieve weather information from the Internet and act as UPnP-compatible temperature sensor in a network.

Fraunhofer Institute FOKUS has been working for many years in the domain of home networks. The present Java-based UPnP-stack is highly standards compliant. It contains diverse extensions that e.g. allow connecting various local UPnP networks over the Internet, secure devices against unauthorized access or increase scalability for many devices.

It is published under the [GNU General Public License Version 3](http://upnp.fokus.fraunhofer.de/license.txt, "GNU General Public License 3").

http://upnp.fokus.fraunhofer.de

Copyright (C) Fraunhofer FOKUS, 2011
http://www.fokus.fraunhofer.de

## OLFDB maintenance fork

This fork preserves the original packages and API. It builds all five modules
with Maven 3.9.9 (wrapper included) on JDK 17 or newer and emits Java 8 bytecode:

```sh
./mvnw clean install
```

`upnp-utils` uses `System.nanoTime()` instead of the removed `sun.misc.Perf` API.
`upnp-core` uses the Log4j 1.2 API adapter backed by Log4j 2, rather than bundling
Log4j 1.x. The matching `org.apache.logging.log4j:log4j-core` runtime is declared
explicitly because the compatibility adapter requires it. RXTX and JCalendar remain regular Maven dependencies of
the optional gateway/GUI modules; no dependency classes are embedded in the
core or utilities JARs.

JitPack builds this repository using JDK 17 and the Maven wrapper. Individual
modules use group ID `com.github.OLFDB.fokus-upnp`, artifact ID `upnp-core` or
`upnp-utils`, and a full commit SHA as the version. Add `https://jitpack.io` as
a Maven repository. A core-only consumer needs `upnp-core`; `upnp-utils` is
transitive. Build locally with `-pl upnp-core -am` to omit the example and GUI
modules.

### Reliable discovery for late clients

Devices repeat SSDP alive announcements at least every 30 seconds while retaining
an advertised cache lifetime of 1800 seconds. This provides a bounded fallback
when a client starts later and its multicast M-SEARCH request is lost. Explicit
M-SEARCH processing is unchanged; the SSDP log records incoming searches and
outgoing announcement types and interfaces. Announcement callbacks and shutdown
are serialized, so a queued callback cannot resurrect the device after byebye.
`UPnPDefaults.DEVICE_MAX_NOTIFY_INTERVAL` controls the maximum interval in
milliseconds. Shorter cache lifetimes still refresh at half their own lifetime.
