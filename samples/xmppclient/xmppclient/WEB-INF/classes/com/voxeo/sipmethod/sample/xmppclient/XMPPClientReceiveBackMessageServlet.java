package com.voxeo.sipmethod.sample.xmppclient;

import java.io.IOException;

import javax.servlet.ServletException;

import org.w3c.dom.Element;

import com.voxeo.servlet.xmpp.InstantMessage;
import com.voxeo.servlet.xmpp.XmppServlet;
import com.voxeo.servlet.xmpp.XmppSession;

public class XMPPClientReceiveBackMessageServlet extends XmppServlet {

  private static final long serialVersionUID = 6999452835239564703L;

  /**
   * receive message from outgoing client session.
   */
  @Override
  protected void doMessage(InstantMessage req) throws ServletException, IOException {
    // you can parse the message. here just echo.
    if (!(req.getSession().getType() == XmppSession.Type.S2S))
      doEcho(req);
  }

  private void doEcho(InstantMessage req) throws IOException {
    // create echo stanza and send.
    req.getSession().createMessage(req.getFrom().toString(), req.getType(), req.getElements().toArray(new Element[] {}))
        .send();
  }
}
