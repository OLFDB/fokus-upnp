/**
* 
* Copyright (C) 2004-2008 FhG Fokus
*
* This file is part of the FhG Fokus UPnP stack - an open source UPnP implementation
* with some additional features
*
* You can redistribute the FhG Fokus UPnP stack and/or modify it
* under the terms of the GNU General Public License Version 3 as published by
* the Free Software Foundation.
*
* For a license to use the FhG Fokus UPnP stack software under conditions
* other than those described here, or to purchase support for this
* software, please contact Fraunhofer FOKUS by e-mail at the following
* addresses:
*   upnpstack@fokus.fraunhofer.de
*
* The FhG Fokus UPnP stack is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program; if not, see <http://www.gnu.org/licenses/>
* or write to the Free Software Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
*
*/
package de.fraunhofer.fokus.upnp.core.device;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.Vector;

import org.apache.log4j.Logger;

import de.fraunhofer.fokus.upnp.configuration.UPnPDefaults;
import de.fraunhofer.fokus.upnp.ssdp.NotifyMessageBuilder;
import de.fraunhofer.fokus.upnp.ssdp.SSDPConstant;
import de.fraunhofer.fokus.upnp.util.StringHelper;
import de.fraunhofer.fokus.upnp.util.threads.IEventListener;

/**
 * This class is responsible for the advertisement of the device. It sends discovery messages in a
 * regular interval bounded independently of the advertised cache lifetime. A device unavailable message is sent
 * out at the termination device.
 * 
 * @author icu, Alexander Koenig
 * 
 */
public class DeviceNotifyAdvertiser implements IEventListener
{
  protected static Logger logger     = Logger.getLogger("upnp.ssdp");

  private long            wakeupTime = 0;

  private boolean         initialized;

  private boolean         terminated;

  protected Device        device;

  /**
   * Creates DeviceNotifyAdvertiser object
   */
  public DeviceNotifyAdvertiser(Device device)
  {
    // if (DeviceRepository.getDevice().getMaxage() < 1800){
    // throw new Exception("max age value should > 1800 (30 min)");
    // }
    this.device = device;
    device.getDeviceEventThread().register(this);
  }

  /**
   * Send all NOTIFY messages for a certain ssdp type (byebye or alive) to all external interfaces
   */
  public synchronized void sendNotifyMessages(String messageType)
  {
    if (terminated && !SSDPConstant.SSDP_BYEBYE.equals(messageType)) return;
    // send to all associated multicast sockets
    Vector socketStructures = device.getSocketStructures();
    for (int j = 0; j < socketStructures.size(); j++)
    {
      DeviceHostAddressSocketStructure socketStructure =
        (DeviceHostAddressSocketStructure)socketStructures.elementAt(j);

      sendNotifyMessagesToSocketStructure(messageType, socketStructure);
    }
  }

  /**
   * Send all NOTIFY messages for a certain ssdp type (byebye or alive) to one external interface
   */
  public synchronized void sendNotifyMessagesToSocketStructure(String messageType, DeviceHostAddressSocketStructure socketStructure)
  {
    if (terminated && !SSDPConstant.SSDP_BYEBYE.equals(messageType)) return;
    Vector messages =
      NotifyMessageBuilder.createAllMessages(device,
        socketStructure.getHTTPServerAddress(),
        messageType,
        device.getIPVersion());

    // send all messages to the current socket
    for (int i = 0; i < messages.size(); i++)
    {
      String currentMessage = (String)messages.elementAt(i);
      logger.info("Sending " + messageType + " NOTIFY " + (i + 1) + "/" + messages.size() +
        " on " + socketStructure.getHostAddress().getHostAddress());

      sendNotifyMessageToMulticastSocket(socketStructure.getSSDPMulticastSocket(),
        device.getSSDPMulticastSocketAddress(),
        currentMessage);
    }
  }

  /**
   * Sends one NOTIFY message to SSDP multicast channel
   * 
   * @param socket
   *          Socket to use for sending
   * @param msg
   *          message to be sent
   */
  protected void sendNotifyMessageToMulticastSocket(DatagramSocket socket,
    InetSocketAddress multicastSocketAdress,
    String msg)
  {
    try
    {
      // logger.debug("send message:[\n" + msg+"]");

      byte[] msgBytes = StringHelper.stringToByteArray(msg);
      DatagramPacket packet = new DatagramPacket(msgBytes, msgBytes.length, multicastSocketAdress);

      // System.out.println("Send multicast packet to
      // "+SSDPConstant.getSSDPMulticastAddress().getHostAddress());
      for (int j = 0; j < UPnPDefaults.UDP_SEND_COUNT; j++)
      {
        socket.send(packet);
        Thread.sleep(UPnPDefaults.DEVICE_NOTIFY_MESSAGE_DELAY);
      }
    } catch (Exception e)
    {
      System.out.println("DeviceNotifyAdvertiser: " + e.getMessage());
      logger.error("ssdp client cannot send packet to " + device.getSSDPMulticastSocketAddressString());
      logger.error("reason: " + e.getMessage());
    }
  }

  /**
   * Stops sending discovery message and sends device unavailable message.
   */
  public synchronized void terminate()
  {
    if (terminated) return;
    terminated = true;
    logger.info("terminating device....");
    device.getDeviceEventThread().unregister(this);

    // send bye bye NOTIFY
    sendNotifyMessages(SSDPConstant.SSDP_BYEBYE);
  }

  /*
   * (non-Javadoc)
   * 
   * @see de.fraunhofer.fokus.upnp.util.threads.IEventListener#triggerEvents()
   */
  /** Monotonic milliseconds; overridable for deterministic scheduler tests. */
  protected long now()
  {
    return System.nanoTime() / 1000000L;
  }

  public synchronized void triggerEvents()
  {
    if (terminated) return;
    long currentTime = now();
    if (!initialized)
    {
      initialized = true;
      // Clear stale discovery state left by a previous process.
      sendNotifyMessages(SSDPConstant.SSDP_BYEBYE);
      wakeupTime = currentTime + UPnPDefaults.DEVICE_INITIAL_NOTIFY_DELAY;
    }
    if (currentTime >= wakeupTime)
    {
      sendNotifyMessages(SSDPConstant.SSDP_ALIVE);
      long cacheRefresh = Math.max(1L, device.getMaxage() * 1000L / 2);
      long interval = Math.min(cacheRefresh, Math.max(1000L, UPnPDefaults.DEVICE_MAX_NOTIFY_INTERVAL));
      wakeupTime = currentTime + interval;
    }
  }

}
