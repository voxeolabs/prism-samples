package com.voxeo.sipmethod.sample.xmppecho;

import java.io.IOException;
import java.util.List;

import javax.servlet.ServletException;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.DOMException;
import org.w3c.dom.Element;

import com.voxeo.servlet.xmpp.IQRequest;
import com.voxeo.servlet.xmpp.IQResponse;
import com.voxeo.servlet.xmpp.InstantMessage;
import com.voxeo.servlet.xmpp.JID;
import com.voxeo.servlet.xmpp.PresenceMessage;
import com.voxeo.servlet.xmpp.ProviderException;
import com.voxeo.servlet.xmpp.StanzaError.Condition;
import com.voxeo.servlet.xmpp.StanzaError.Type;
import com.voxeo.servlet.xmpp.XmppFactory;
import com.voxeo.servlet.xmpp.XmppServlet;
import com.voxeo.servlet.xmpp.XmppServletMessage;
import com.voxeo.servlet.xmpp.XmppSession;
import com.voxeo.servlet.xmpp.XmppSessionsUtil;

/**
 * @author zhuwillie
 */
public class EchoServlet extends XmppServlet {

  private static final long serialVersionUID = -1984187722304487574L;

  private XmppSessionsUtil _sessionUtil;

  private XmppFactory _xmppFactory;

  private List<String> _servingDomains;

  @Override
  public void init() throws ServletException {
    _sessionUtil = (XmppSessionsUtil) this.getServletContext().getAttribute(XmppServlet.SESSIONUTIL);
    _xmppFactory = (XmppFactory) this.getServletContext().getAttribute(XmppServlet.XMPP_FACTORY);
    _servingDomains = (List<String>) this.getServletContext().getAttribute(XmppServlet.SERVING_DOMIANS);

    _xmppFactory.registerProvider(new RosterProvider());
  }

  @Override
  protected void doMessage(InstantMessage req) throws ServletException, IOException {
    log("Received message:" + req.toString());

    if (req.getTo().toString().equalsIgnoreCase("echo@xmpp.im.voxeo.com/xmpp-test")
        || req.getTo().toString().equalsIgnoreCase("echo@xmpp.im.voxeo.com")
        || req.getTo().toString().equalsIgnoreCase("echo@xmpp.tropo.voxeo.com/xmpp-test")
        || req.getTo().toString().equalsIgnoreCase("echo@xmpp.tropo.voxeo.com")) {
      doEcho(req);
    }
    else if (_servingDomains.contains(req.getTo().getDomain())) {
      JID jid = req.getTo();
      if (jid.getResource() == null) {
        jid = _xmppFactory.createJID(req.getTo().toString());
      }
      List<XmppSession> sessions = null;
      if ((sessions = _sessionUtil.getSessions(jid)) != null) {
        for (XmppSession session : sessions) {
          session.createMessage(req.getFrom().toString(), XmppServletMessage.TYPE_CHAT,
              req.getElements().toArray(new Element[] {})).send();
        }
      }
    }
  }

  @Override
  protected void doPresence(PresenceMessage req) throws ServletException, IOException {
    log("Received presence:" + req.toString());

    // normal presence
    if (req.getType() == null || req.getType().trim().length() == 0) {
      // this is a presence stanza from local client. send a presence message to
      // show that echo@xmpp.im.voxeo.com is online.
      if (!(req.getSession().getType() == XmppSession.Type.S2S)) {
        req.getSession().createPresence("echo@xmpp.im.voxeo.com/xmpp-test", req.getType(), (Element[]) null).send();

        req.getSession().createPresence("userc@xmpp.im.voxeo.com/xmpp-test", req.getType(), (Element[]) null).send();

        req.getSession().createPresence("userd@xmpp.im.voxeo.com/xmpp-test", req.getType(), (Element[]) null).send();

        req.getSession().createPresence("echo@xmpp.tropo.voxeo.com/xmpp-test", req.getType(), (Element[]) null).send();
      }
      // else it is a presence from s2s session. just send back presence stanza
      // to say that the requested user is online.
      else {
        // simulate the client to send.
        // for example <presence from='contact@example.org/resource'
        // to='user@example.com'/>
        req.getSession()
            .createPresence(req.getTo().getBareJID().toString() + "/xmpp-test", req.getType(), (Element[]) null).send();
      }
    }

    // subscribe
    else if (req.getType().equalsIgnoreCase("subscribe")) {
      // simulate the client to send "subscribed" presence.
      // for example, <presence to='user@example.com'
      // from='contact@example.net'
      // type='subscribed'/>
      req.getSession().createPresence(req.getTo().toString(), XmppServletMessage.TYPE_SUBSCRIBED, (Element[]) null)
          .send();

      // send normal presence.
      // for example, <presence from='contact@example.org/resource'
      // to='user@example.com'/>
      req.getSession().createPresence(req.getTo().toString() + "/xmpp-test", null, (Element[]) null).send();

      // send 'subscribe' presence.
      // for example , <presence from='contact@example.org'
      // to='user@example.com'
      // type="subscribe"/>
      req.getSession().createPresence(req.getTo().toString(), XmppServletMessage.TYPE_SUBSCRIBE, (Element[]) null)
          .send();
    }

    // unsubscribe
    else if (req.getType().equalsIgnoreCase("unsubscribe")) {
      // send 'unsubscribed' presence.
      // for example, <presence from='contact@example.org'
      // to='user@example.com'
      // type='unsubscribed'/>
      req.getSession().createPresence(req.getTo().toString(), XmppServletMessage.TYPE_UNSUBSCRIBED, (Element[]) null)
          .send();

      // send <presence from='contact@example.org/resource'
      // to='user@example.com' type='unavailable'/>
      req.getSession()
          .createPresence(req.getTo().toString() + "/xmpp-test", XmppServletMessage.TYPE_UNAVAILABLE, (Element[]) null)
          .send();
    }

    else if (req.getType().equalsIgnoreCase("probe")) {
      // send <presence from='contact@example.org/resource'
      // to='user@example.com'/>
      Element presenceElement = null;
      try {
        presenceElement = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
            .createElement("presence");
      }
      catch (DOMException e) {
        e.printStackTrace();
      }
      catch (ParserConfigurationException e) {
        e.printStackTrace();
      }
      presenceElement.setAttribute("from", req.getTo().toString() + "/xmpp-test");
      presenceElement.setAttribute("to", req.getFrom().toString());

      req.getSession().createPresence(req.getTo().toString() + "/xmpp-test", null, (Element[]) null).send();
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
  protected void doIQRequest(IQRequest req) throws ServletException, IOException {
    log("Received iq request:" + req.toString());

    if (!(req.getSession().getType() == XmppSession.Type.S2S)) {
      // 1 process session feature iq
      /**
       * <iq from='example.com' type='result' id='sess_1'/>
       */
      Element element = req.getElement("session");
      if (element != null) {
        Element resElement = null;
        try {
          resElement = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
              .createElementNS("urn:ietf:params:xml:ns:xmpp-session", "session");
        }
        catch (DOMException e) {
          e.printStackTrace();
        }
        catch (ParserConfigurationException e) {
          e.printStackTrace();
        }

        req.createResult(resElement).send();
        return;
      }

      // 2 process roster iq.
      element = req.getElement("query", "jabber:iq:roster");
      Object obj = null;
      try {
        obj = req.getChild();
      }
      catch (ProviderException e) {
        e.printStackTrace();
        log(e.getMessage());
      }
      if (obj != null && obj instanceof Roster) {
        // if the client is retrieving roster on login. add echo user to roster.
        if (req.getType().equalsIgnoreCase("get")) {
          Roster roster = new Roster();

          roster.addItem(new Item("echo@xmpp.im.voxeo.com", "imecho", "both", "Friends"));
          roster.addItem(new Item("userc@xmpp.im.voxeo.com", "userc", "both", "Friends"));
          roster.addItem(new Item("userd@xmpp.im.voxeo.com", "userd", "both", "Friends"));
          roster.addItem(new Item("echo@xmpp.tropo.voxeo.com", "tropoecho", "both", "Friends"));

          IQResponse resp = req.createResult();
          resp.addChild(roster);
          resp.send();
          return;
        }
      }
    }

    // process other iq request. return 'unsupport' stanza error.
    req.createError(Type.CANCEL, Condition.FEATURE_NOT_IMPLEMENTED, "").send();
  }

  // Do echo, parse the received message and send back the same message.
  private void doEcho(InstantMessage req) throws IOException, ServletException {

    if (!(req.getSession().getType() == XmppSession.Type.S2S)) {
      if (_servingDomains.contains(req.getTo().getDomain())) {
        req.getSession()
            .createMessage(req.getTo().toString(), req.getType(), req.getElements().toArray(new Element[] {})).send();
      }
      else {
        InstantMessage newReq = _xmppFactory.createMessage(req.getFrom(), req.getTo(), req.getType(), req.getElements()
            .toArray(new Element[] {}));
        newReq.send();
        log("Sent message to domain: " + req.getTo().getDomain() + " message: " + newReq.toString());
      }
    }
    else if (req.getSession().getType() == XmppSession.Type.S2S && _servingDomains.contains(req.getTo().getDomain())) {
      InstantMessage newReq = _xmppFactory.createMessage(req.getTo(), req.getFrom(), req.getType(), req.getElements()
          .toArray(new Element[] {}));
      newReq.send();
      log("Sent message to domain: " + req.getTo().getDomain() + " message: " + newReq.toString());
    }
  }
}
