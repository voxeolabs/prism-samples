package com.voxeo.sipmethod.sample.xmppecho;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.ServletException;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.QName;

import com.voxeo.servlet.xmpp.JID;
import com.voxeo.servlet.xmpp.XmppServlet;
import com.voxeo.servlet.xmpp.XmppServletFeaturesRequest;
import com.voxeo.servlet.xmpp.XmppServletIQRequest;
import com.voxeo.servlet.xmpp.XmppServletIQResponse;
import com.voxeo.servlet.xmpp.XmppServletStanzaRequest;
import com.voxeo.servlet.xmpp.XmppServletStreamRequest;
import com.voxeo.servlet.xmpp.XmppSession;
import com.voxeo.servlet.xmpp.XmppStanzaError;
import com.voxeo.servlet.xmpp.XmppSession.SessionType;

/**
 * @author zhuwillie
 */
public class EchoServlet extends XmppServlet {

  private static final long serialVersionUID = -1984187722304487574L;

  private Map<JID, XmppSession> _clientSessions;

  @Override
  public void init() throws ServletException {
    _clientSessions = new ConcurrentHashMap<JID, XmppSession>();
  }

  @Override
  protected void doMessage(XmppServletStanzaRequest req) throws ServletException, IOException {
    log("Received message:" + req.getElement().asXML());

    if (req.getElement().element("body") == null) {
      return;
    }
    doEcho(req);
  }

  @Override
  protected void doPresence(XmppServletStanzaRequest req) throws ServletException, IOException {
    log("Received presence:" + req.getElement().asXML());

    // normal presence
    if (req.getType() == null || req.getType().trim().length() == 0) {
      // this is a presence stanza from local client. send a presence message to
      // show that echo@xmpp.im.voxeo.com is online.
      if (req.getSession().getSessionType() == SessionType.CLIENT) {
        Element stanzaResp = DocumentHelper.createElement("presence");
        stanzaResp.addAttribute("from", "echo@xmpp.im.voxeo.com/someresource");
        stanzaResp.addAttribute("to", req.getFrom().toString());

        req.getSession().createStanzaRequest(stanzaResp, null, null, null, null, null).send();
      }

      // else it is a presence from s2s session. just send back presence stanza
      // to say that the requested user is online.
      else {
        // simulate the client to send.
        // for example <presence from='contact@example.org/resource'
        // to='user@example.com'/>
        Element presenceElement = DocumentHelper.createElement("presence");
        presenceElement.addAttribute("from", req.getTo().getBareJID().toString() + "/someresource");
        presenceElement.addAttribute("to", req.getFrom().toString());

        req.getSession().createStanzaRequest(presenceElement, null, null, null, null, null).send();
      }
    }

    // subscribe
    else if (req.getType().equalsIgnoreCase("subscribe")) {
      // simulate the client to send "subscribed" presence.
      // for example, <presence to='user@example.com'
      // from='contact@example.net'
      // type='subscribed'/>
      Element subscribedElement = DocumentHelper.createElement("presence");
      subscribedElement.addAttribute("from", req.getTo().toString());
      subscribedElement.addAttribute("to", req.getFrom().toString());
      subscribedElement.addAttribute("type", "subscribed");

      req.getSession().createStanzaRequest(subscribedElement, null, null, null, null, null).send();

      // send normal presence.
      // for example, <presence from='contact@example.org/resource'
      // to='user@example.com'/>
      Element presenceElement = DocumentHelper.createElement("presence");
      presenceElement.addAttribute("from", req.getTo().toString() + "/someresource");
      presenceElement.addAttribute("to", req.getFrom().toString());

      req.getSession().createStanzaRequest(presenceElement, null, null, null, null, null).send();

      if (req.getSession().getSessionType() != SessionType.CLIENT) {
        // send 'subscribe' presence.
        // for example , <presence from='contact@example.org'
        // to='user@example.com'
        // type="subscribe"/>
        Element subscribeElement = DocumentHelper.createElement("presence");
        subscribeElement.addAttribute("from", req.getTo().toString());
        subscribeElement.addAttribute("to", req.getFrom().toString());
        subscribeElement.addAttribute("type", "subscribe");
        req.getSession().createStanzaRequest(subscribeElement, null, null, null, null, null).send();
      }

    }

    // unsubscribe
    else if (req.getType().equalsIgnoreCase("unsubscribe")) {
      // send 'unsubscribed' presence.
      // for example, <presence from='contact@example.org'
      // to='user@example.com'
      // type='unsubscribed'/>
      Element subscribedElement = DocumentHelper.createElement("presence");
      subscribedElement.addAttribute("from", req.getTo().toString());
      subscribedElement.addAttribute("to", req.getFrom().toString());
      subscribedElement.addAttribute("type", "unsubscribed");
      req.getSession().createStanzaRequest(subscribedElement, null, null, null, null, null).send();

      // send <presence from='contact@example.org/resource'
      // to='user@example.com' type='unavailable'/>
      Element presenceElement = DocumentHelper.createElement("presence");
      presenceElement.addAttribute("from", req.getTo().toString() + "/someresource");
      presenceElement.addAttribute("to", req.getFrom().toString());
      presenceElement.addAttribute("type", "unavailable");
      req.getSession().createStanzaRequest(presenceElement, null, null, null, null, null).send();

    }

    else if (req.getType().equalsIgnoreCase("probe")) {
      // send <presence from='contact@example.org/resource'
      // to='user@example.com'/>
      Element presenceElement = DocumentHelper.createElement("presence");
      presenceElement.addAttribute("from", req.getTo().toString() + "/someresource");
      presenceElement.addAttribute("to", req.getFrom().toString());

      req.getSession().createStanzaRequest(presenceElement, null, null, null, null, null).send();
    }
    else if (req.getType().equalsIgnoreCase("error")) {
      // do nothing.
    }
    else if (req.getType().equalsIgnoreCase("unsubscribed")) {
      // do nothing.
    }
    else if (req.getType().equalsIgnoreCase("subscribed")) {
      // do nothing.
    }
    else if (req.getType().equalsIgnoreCase("unavailable")) {
      // do nothing.
    }
  }

  @Override
  protected void doIQRequest(XmppServletIQRequest req) throws ServletException, IOException {
    log("Received iq request:" + req.getElement().asXML());

    if (req.getSession().getSessionType() == SessionType.CLIENT) {
      // 0 process resource bind
      /**
       * <iq type='result' id='bind_2'> <bind
       * xmlns='urn:ietf:params:xml:ns:xmpp-bind'>
       * <jid>somenode@example.com/someresource</jid> </bind> </iq>
       */
      Element element = req.getElement().element("bind");
      if (element != null) {
        Element resElement = DocumentHelper.createElement("iq");
        resElement.addAttribute("type", "result");
        // resElement.addAttribute("id", "sess_1");
        resElement.addElement(new QName("bind", new Namespace("", "urn:ietf:params:xml:ns:xmpp-bind"))).addElement(
            "jid").setText(req.getFrom().toString() + "/someresource");
        req.createIQResultResponse(resElement).send();
        _clientSessions.put(req.getFrom().getBareJID(), req.getSession());
        return;
      }

      // 1 process session feature iq
      /**
       * <iq from='example.com' type='result' id='sess_1'/>
       */
      element = req.getElement().element("session");
      if (element != null) {
        Element resElement = DocumentHelper.createElement("iq");
        resElement.addAttribute("from", req.getTo().toString());
        resElement.addAttribute("type", "result");
        resElement.addElement(new QName("session", new Namespace("", "urn:ietf:params:xml:ns:xmpp-session")));

        req.createIQResultResponse(resElement).send();
        return;
      }

      // 2 process roster iq.
      element = req.getElement().element(new QName("query", new Namespace("", "jabber:iq:roster")));
      if (element != null) {
        // if the client is retrieving roster on login. add echo user to roster.
        if (req.getType().equalsIgnoreCase("get")) {
          Element resElement = DocumentHelper.createElement("iq");
          resElement.addAttribute("type", "result");

          Element roster = resElement.addElement(new QName("query", new Namespace("", "jabber:iq:roster")));

          Element item5 = roster.addElement("item");
          item5.addAttribute("jid", "echo@xmpp.im.voxeo.com");
          item5.addAttribute("name", "echo");
          item5.addAttribute("subscription", "both");
          item5.addElement("group").setText("Friends");

          req.createIQResultResponse(resElement).send();
          return;
        }
        // if the client is adding a roster item.
        else if (req.getType().equalsIgnoreCase("set")) {
          Element resElement = DocumentHelper.createElement("iq");
          resElement.addAttribute("type", "result");
          req.createIQResultResponse(resElement).send();

          Element setIQElement = DocumentHelper.createElement("iq");
          setIQElement.addAttribute("type", "set");
          setIQElement.addAttribute("id", "thisistodo11");
          setIQElement.addAttribute("to", req.getFrom().toString());
          setIQElement.addAttribute("from", req.getTo().toString());

          Element roserElement = req.getElement().element(new QName("query", new Namespace("", "jabber:iq:roster")))
              .createCopy();
          Element itemElement = roserElement.element("item");
          if (itemElement != null) {
            itemElement.addAttribute("subscription", "none");
          }
          setIQElement.add(roserElement);
          req.getSession().createStanzaIQRequest(setIQElement, null, null, null, null, null).send();
          return;
        }
      }
    }

    // process other iq request. return 'unsupport' stanza error.
    XmppServletIQResponse stanzaError = req.createIQErrorResponse(XmppStanzaError.Type_CANCEL,
        XmppStanzaError.FEATURE_NOT_IMPLEMENTED_CONDITION, null, null, null);

    stanzaError.send();
  }

  @Override
  protected void doStreamEnd(XmppServletStreamRequest req) throws ServletException, IOException {
    log("received doStreamEnd:" + req.getElement().asXML());

    XmppSession session = req.getSession();
    if (session.getSessionType() == XmppSession.SessionType.CLIENT) {
      if (session.getRemoteJIDs() != null) {
        for (JID jid : session.getRemoteJIDs()) {
          _clientSessions.remove(jid.getBareJID());
        }

      }
    }
  }

  @Override
  protected void doStreamStart(XmppServletStreamRequest req) throws ServletException, IOException {
    log("Received doStreamStart:" + req.getElement().asXML());

    // received the stream after SASL negotiation.
    // if it is a incoming client session.
    if (req.getSession().getSessionType() == SessionType.CLIENT && req.isInitial()) {
      req.createRespXMPPServletStreamRequest().send();
      XmppServletFeaturesRequest featuresReq = req.createFeaturesRequest();
      featuresReq.addFeature("urn:ietf:params:xml:ns:xmpp-session", "session");
      featuresReq.send();
    }
  }

  // Do echo, parse the received message and send back the same message.
  private void doEcho(XmppServletStanzaRequest req) throws IOException {
    // construct the echo element.
    Element echoElement = DocumentHelper.createElement("message");
    if (req.getElement().attribute("from") != null) {
      echoElement.addAttribute("to", req.getElement().attribute("from").getValue());
    }
    if (req.getElement().attribute("to") != null) {
      echoElement.addAttribute("from", req.getElement().attribute("to").getValue());
    }
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

    req.getSession().createStanzaRequest(echoElement, null, null, null, null, null).send();
  }
}
