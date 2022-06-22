/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.nightcode.milter.command;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

import org.nightcode.milter.Code;
import org.nightcode.milter.MilterContext;
import org.nightcode.milter.MilterException;
import org.nightcode.milter.MilterHandler;
import org.nightcode.milter.MilterState;
import org.nightcode.milter.codec.MilterPacket;
import org.nightcode.milter.util.Log;
import org.nightcode.milter.util.MilterPacketUtil;

import static java.lang.String.format;
import static org.nightcode.milter.CommandCode.SMFIC_CONNECT;

class ConnectCommandProcessor extends AbstractCommandHandler {

  public static final int SMFIA_UNKNOWN = 'U'; // Unknown (NOTE: Omits "port" and "host" fields entirely)
  public static final int SMFIA_UNIX    = 'L'; // Unix (AF_UNIX/AF_LOCAL) socket ("port" is 0)
  public static final int SMFIA_INET    = '4'; // TCPv4 connection
  public static final int SMFIA_INET6   = '6'; // TCPv6 connection

  private static final int LAST_ZERO_TERM_LENGTH = 1;
  private static final int PORT_OFFSET           = 2;

  ConnectCommandProcessor(MilterHandler handler) {
    super(handler);
  }

  @Override public Code command() {
    return SMFIC_CONNECT;
  }

  @Override public void submit(MilterContext context, MilterPacket packet) throws MilterException {
    context.setSessionState(MilterState.CONNECT);
    if (!MilterPacketUtil.isLastZeroTerm(packet.payload())) {
      Log.info().log(getClass(), format("[%s] received invalid packet: %s", context.id(), packet));
      handler.abortSession(context, packet);
      return;
    }

    final int payloadLength = packet.payload().length;
    int i = MilterPacketUtil.indexOfZeroTerm(packet.payload());

    if ((i + LAST_ZERO_TERM_LENGTH) >= payloadLength) {
      Log.info().log(getClass(), format("[%s] wrong packet length=%s %s", context.id(), payloadLength, packet));
      handler.abortSession(context, packet);
      return;
    }

    int offset = 0;
    String hostname = new String(packet.payload(), offset, i, StandardCharsets.UTF_8);
    i++;

    InetAddress address = null;
    int family = packet.payload()[i++];
    if (family == SMFIA_INET) {
      if (i + PORT_OFFSET >= payloadLength) {
        Log.info().log(getClass(), format("[%s] wrong packet length=%s %s", context.id(), payloadLength, packet));
        handler.abortSession(context, packet);
        return;
      }
      i += PORT_OFFSET;
      offset = i;
      String stringAddress = new String(packet.payload(), offset, payloadLength - offset - LAST_ZERO_TERM_LENGTH
          , StandardCharsets.UTF_8);
      try {
        address = InetAddress.getByName(stringAddress);
      } catch (UnknownHostException ex) {
        Log.info().log(getClass(), format("[%s] invalid address value: %s", context.id(), stringAddress));
        handler.abortSession(context, packet);
        return;
      }
    }

    handler.connect(context, hostname, address);
  }
}
