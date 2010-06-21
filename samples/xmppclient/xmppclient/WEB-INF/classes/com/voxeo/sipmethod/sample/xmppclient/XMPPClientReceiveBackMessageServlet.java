package com.voxeo.sipmethod.sample.xmppclient;

import java.io.IOException;
import java.util.Map;

import javax.servlet.ServletException;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.QName;

import com.voxeo.servlet.xmpp.XmppServlet;
import com.voxeo.servlet.xmpp.XmppServletFeaturesRequest;
import com.voxeo.servlet.xmpp.XmppServletIQResponse;
import com.voxeo.servlet.xmpp.XmppServletStanzaRequest;
import com.voxeo.servlet.xmpp.XmppSession;
import com.voxeo.servlet.xmpp.XmppSession.SessionType;

public class XMPPClientReceiveBackMessageServlet extends XmppServlet {

  private static final long serialVersionUID = 6999452835239564703L;

  /**
   *receive message from outgoing client session.
   */
  @Override
  protected void doMessage(XmppServletStanzaRequest req) throws ServletException, IOException {
    // you can parse the message. here just echo.
    if (req.getElement().element("body") == null) {
      return;
    }

    if (req.getSession().getSessionType() == SessionType.CLIENT)
      doEcho(req);
  }

  @Override
  protected void doIQResponse(XmppServletIQResponse req) throws ServletException, IOException {
    if (req.getElement().element(new QName("bind", new Namespace("", "urn:ietf:params:xml:ns:xmpp-bind"))) != null) {
      Map<String, XmppSession> sessionMap = (Map<String, XmppSession>) getServletContext().getAttribute("SessionMap");
      sessionMap.put(req.getTo().getBareJID().toString(), req.getSession());
      synchronized (req.getSession()) {
        req.getSession().notify();
      }
    }
  }

  @Override
  protected void doStreamFeatures(XmppServletFeaturesRequest req) throws ServletException, IOException {
    if (req.getFeature("bind", "urn:ietf:params:xml:ns:xmpp-bind") != null) {
      // send session iq request per RFC3921
      Element bindElement = DocumentHelper.createElement("iq");
      bindElement.addAttribute("type", "set");

      bindElement.addElement(new QName("bind", new Namespace("", "urn:ietf:params:xml:ns:xmpp-bind")));
      req.getSession().createStanzaIQRequest(bindElement, null, null, null, "bind_1", null).send();
    }

    // if there is a session feature.
    if (req.getFeature("session", "urn:ietf:params:xml:ns:xmpp-session") != null) {
      // send session iq request per RFC3921
      Element sessionIQ = DocumentHelper.createElement("iq");

      sessionIQ.addAttribute("type", "set");
      sessionIQ.addAttribute("id", "sess_1");
      sessionIQ.addElement(new QName("session", new Namespace("", "urn:ietf:params:xml:ns:xmpp-session")));
      req.getSession().createStanzaIQRequest(sessionIQ, null, null, null, null, null).send();
    }
  }

  private void doEcho(XmppServletStanzaRequest req) throws IOException {
    // construct the echo element.
    Element echoElement = DocumentHelper.createElement("message");
    if (req.getElement().attribute("from") != null) {
      echoElement.addAttribute("to", req.getElement().attribute("from").getValue());
    }
    echoElement.addAttribute("from", req.getTo().toString());

    if (req.getElement().attribute("type") != null) {
      echoElement.addAttribute("type", req.getElement().attribute("type").getValue());
    }
    if (req.getElement().element("body") != null) {
      echoElement.addElement("body").setText(req.getElement().element("body").getText());
    }
    if (req.getElement().element("subject") != null) {
      echoElement.addElement("subject").setText(req.getElement().element("subject").getText());
    }
    if (req.getElement().element("thread") != null) {
      echoElement.addElement("thread").setText(req.getElement().element("thread").getText());
    }

    // create echo stanza and send.
    req.getSession().createStanzaRequest(echoElement, null, null, null, null, null).send();
  }
}
