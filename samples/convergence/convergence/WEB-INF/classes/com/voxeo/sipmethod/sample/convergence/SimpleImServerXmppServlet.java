package com.voxeo.sipmethod.sample.convergence;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import javax.servlet.ServletException;
import javax.servlet.sip.SipFactory;
import javax.servlet.sip.SipServlet;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipURI;
import javax.servlet.sip.URI;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.QName;

import com.voxeo.servlet.xmpp.JID;
import com.voxeo.servlet.xmpp.XmppFactory;
import com.voxeo.servlet.xmpp.XmppServlet;
import com.voxeo.servlet.xmpp.XmppServletFeaturesRequest;
import com.voxeo.servlet.xmpp.XmppServletIQRequest;
import com.voxeo.servlet.xmpp.XmppServletIQResponse;
import com.voxeo.servlet.xmpp.XmppServletStanzaRequest;
import com.voxeo.servlet.xmpp.XmppServletStreamRequest;
import com.voxeo.servlet.xmpp.XmppSession;
import com.voxeo.servlet.xmpp.XmppStanzaError;

/**
 * This servlet is a simple xmpp IM server. can also communicate with SIP and
 * Http.
 * 
 * @author zhuwillie
 */
public class SimpleImServerXmppServlet extends XmppServlet {

  private static final long serialVersionUID = 4969175164948481304L;

  private XmppFactory _xmppFactory;

  private SipFactory _sipFactory;

  private Map<JID, XmppSession> _clientSessions = null;

  private Set<String> clients = null;

  public static String ActiveClientAttributeName = "com.micromethod.sipmethod.sample.xmppsample.aliveclient";

  public static String XMPPSessionMapAttributeName = "com.micromethod.sipmethod.sample.xmppsample.XMPPSESSIONMAP";

  public static String ECHO_USER = "echo@convergence.sample.prism.voxeo.com";

  @Override
  public void init() throws ServletException {
    clients = new CopyOnWriteArraySet<String>();
    clients.add(ECHO_USER);
    getServletContext().setAttribute(ActiveClientAttributeName, clients);

    // Get a reference to the XMPPFactory.
    _xmppFactory = (XmppFactory) getServletContext().getAttribute(XmppServlet.XMPP_FACTORY);
    _sipFactory = (SipFactory) getServletContext().getAttribute(SipServlet.SIP_FACTORY);
    _clientSessions = new ConcurrentHashMap<JID, XmppSession>();
    getServletContext().setAttribute(XMPPSessionMapAttributeName, _clientSessions);
  }

  @Override
  protected void doMessage(XmppServletStanzaRequest req) throws ServletException, IOException {
    log("Received message:" + req.getElement().asXML());

    if (req.getElement().element("body") == null && req.getElement().element("subject") == null) {
      return;
    }

    // if the destination is echo user and this application is serving domain
    // 'xmpp.im.voxoe.com'.
    if (req.getTo().getBareJID().toString().equalsIgnoreCase(ECHO_USER)
        && req.getSession().getApplicationServDomains().contains("convergence.sample.prism.voxeo.com")) {
      doEcho(req);
    }

    // if the destination is the http user. and this application is serving
    // domain 'xmpp.im.voxoe.com'.
    else if (req.getTo().getBareJID().toString().equalsIgnoreCase(ExposeURLServlet.HTTP_USER)
        && req.getSession().getApplicationServDomains().contains("convergence.sample.prism.voxeo.com")) {
      // you can post this message to a http server, then get the response
      // message from http response and send back to xmpp client.
      // doHTTPGateway(req);

      // here just echo.
      doEcho(req);
    }
    // process the message to the SIP user,
    // sipUserF@convergence.sample.prism.voxeo.com.
    else if (req.getTo().getBareJID().toString().equalsIgnoreCase(ReceiveMessageSIPServlet.SIP_USER)
        && req.getSession().getApplicationServDomains().contains("convergence.sample.prism.voxeo.com")) {
      doSIPGateway(req);
    }
    // process message to xmpp client.
    else {
      XmppSession session = _clientSessions.get(req.getTo().getBareJID());
      // intra-domain message
      if (session != null) {
        XmppServletStanzaRequest newReq = _xmppFactory.createStanzaRequest(session, req.getElement());
        newReq.send();
        log("Sent message:" + newReq.getElement().asXML());
      }
      // user not online.
      else if (req.getSession().getApplicationServDomains().contains(req.getTo().getDomain())) {
        log("User: " + req.getTo().getBareJID() + " is offline. Drop this message");
      }
      // inter-domain message. send the message to other domain.
      else {
        XmppServletStanzaRequest newReq = _xmppFactory.createStanzaRequest(_xmppFactory.createJID(req.getTo()
            .getDomain()), req.getElement(), req.getTo());

        newReq.send();
        log("Sent message to domain: " + req.getTo().getDomain() + " message: " + newReq.getElement().asXML());
      }
    }
  }

  @Override
  protected void doIQRequest(XmppServletIQRequest req) throws ServletException, IOException {
    log("Received iq request:" + req.getElement().asXML());

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
      resElement.addElement(new QName("bind", new Namespace("", "urn:ietf:params:xml:ns:xmpp-bind"))).addElement("jid")
          .setText(req.getFrom().getBareJID().toString() + "/someresource");
      _clientSessions.put(req.getFrom().getBareJID(), req.getSession());
      getServletContext().setAttribute("com.micromethod.sipmethod.sample.xmppsample.XMPPSESSIONMAP", _clientSessions);

      req.createIQResultResponse(resElement).send();
      return;
    }

    // 1 process session feature iq
    /**
     * <iq from='example.com' type='result' id='sess_1'/>
     */
    element = req.getElement().element("session");
    if (element != null) {
      Element resElement = DocumentHelper.createElement("iq");
      resElement.addAttribute("from", "localhost");
      resElement.addAttribute("type", "result");
      resElement.addElement(new QName("session", new Namespace("", "urn:ietf:params:xml:ns:xmpp-session")));

      req.createIQResultResponse(resElement).send();
      return;
    }

    // 2 process roster request.
    /**
     * <iq type="result" id="RM4Bf-2" to="willie3@localhost/spark2"><query
     * xmlns="jabber:iq:roster"><item jid="willie4@localhost" name="willie4"
     * subscription="both"><group>Friends</group></item></query></iq>
     */
    element = req.getElement().element(new QName("query", new Namespace("", "jabber:iq:roster")));
    if (element != null) {
      Element resElement = DocumentHelper.createElement("iq");
      resElement.addAttribute("type", "result");

      Element roster = resElement.addElement(new QName("query", new Namespace("", "jabber:iq:roster")));

      Element item = roster.addElement("item");
      item.addAttribute("jid", "usera@xmpp.tropo.voxeo.com");
      item.addAttribute("name", "usera@xmpp.tropo.voxeo.com");
      item.addAttribute("subscription", "both");
      item.addElement("group").setText("Friends");

      Element item2 = roster.addElement("item");
      item2.addAttribute("jid", "userb@xmpp.tropo.voxeo.com");
      item2.addAttribute("name", "userb@xmpp.tropo.voxeo.com");
      item2.addAttribute("subscription", "both");
      item2.addElement("group").setText("Friends");

      Element item3 = roster.addElement("item");
      item3.addAttribute("jid", "userc@convergence.sample.prism.voxeo.com");
      item3.addAttribute("name", "userc@convergence.sample.prism.voxeo.com");
      item3.addAttribute("subscription", "both");
      item3.addElement("group").setText("Friends");

      Element item4 = roster.addElement("item");
      item4.addAttribute("jid", "userd@convergence.sample.prism.voxeo.com");
      item4.addAttribute("name", "userd@convergence.sample.prism.voxeo.com");
      item4.addAttribute("subscription", "both");
      item4.addElement("group").setText("Friends");

      Element item5 = roster.addElement("item");
      item5.addAttribute("jid", "echo@convergence.sample.prism.voxeo.com");
      item5.addAttribute("name", "echo@convergence.sample.prism.voxeo.com");
      item5.addAttribute("subscription", "both");
      item5.addElement("group").setText("Friends");

      Element item6 = roster.addElement("item");
      item6.addAttribute("jid", "sipuserf@convergence.sample.prism.voxeo.com");
      item6.addAttribute("name", "sipuserf@convergence.sample.prism.voxeo.com");
      item6.addAttribute("subscription", "both");
      item6.addElement("group").setText("Friends");

      Element item7 = roster.addElement("item");
      item7.addAttribute("jid", "httpuserg@convergence.sample.prism.voxeo.com");
      item7.addAttribute("name", "httpuserg@convergence.sample.prism.voxeo.com");
      item7.addAttribute("subscription", "both");
      item7.addElement("group").setText("Friends");
      
      Element item8 = roster.addElement("item");
      item8.addAttribute("jid", "convergence@convergence.sample.prism.voxeo.com");
      item8.addAttribute("name", "convergence@convergence.sample.prism.voxeo.com");
      item8.addAttribute("subscription", "both");
      item8.addElement("group").setText("Friends");

      req.createIQResultResponse(resElement).send();
      return;
    }

    // process other iq request. return stanza error.
    XmppServletIQResponse resElement = req.createIQErrorResponse(XmppStanzaError.Type_CANCEL,
        XmppStanzaError.FEATURE_NOT_IMPLEMENTED_CONDITION, null, null, null);

    resElement.send();
  }

  @Override
  protected void doPresence(XmppServletStanzaRequest req) throws ServletException, IOException {
    log("Received presence:" + req.getElement().asXML());

    // <presence id="4g5yq-6"
    // to="usera@xmpp.tropo.voxeo.com/someresource"><status
    // >Available</status><priority>1</priority></presence>
    clients.add(req.getFrom().toString());
    getServletContext().setAttribute(ActiveClientAttributeName, clients);
    for (String s : clients) {
      Element stanzaResp = DocumentHelper.createElement("presence");
      stanzaResp.addAttribute("from", req.getFrom().toString());
      stanzaResp.addAttribute("to", s);
      XmppSession sid = _clientSessions.get(_xmppFactory.createJID(s).getBareJID());
      XmppServletStanzaRequest xmppReq = _xmppFactory.createStanzaRequest(sid, stanzaResp);
      if (xmppReq != null) {
        xmppReq.send();
      }
    }
    for (String s : clients) {
      Element stanzaResp = DocumentHelper.createElement("presence");
      stanzaResp.addAttribute("from", s);
      stanzaResp.addAttribute("to", req.getFrom().toString());
      XmppSession sid = _clientSessions.get(_xmppFactory.createJID(req.getFrom().toString()).getBareJID());
      XmppServletStanzaRequest xmppReq = _xmppFactory.createStanzaRequest(sid, stanzaResp);
      if (xmppReq != null) {
        xmppReq.send();
      }
    }
  }

  @Override
  protected void doStreamEnd(XmppServletStreamRequest req) throws ServletException, IOException {
    log("received doStreamEnd:" + req.getElement().asXML());

    XmppSession session = req.getSession();
    if (session.getSessionType() == XmppSession.SessionType.CLIENT) {
      if (session.getRemoteJIDs() != null) {
        for (JID jid : session.getRemoteJIDs()) {
          clients.remove(jid.toString());
          _clientSessions.remove(jid.getBareJID());
        }
      }
    }
  }

  @Override
  protected void doStreamStart(XmppServletStreamRequest req) throws ServletException, IOException {
    log("Received doStreamStart:" + req.getElement().asXML() + ". The session id:" + req.getSession().getId());

    // received the stream after SASL negotiation.
    // if it is a incoming client session.
    if (req.getSession().getSessionType() == XmppSession.SessionType.CLIENT && req.isInitial()) {
      req.createRespXMPPServletStreamRequest().send();
      XmppServletFeaturesRequest featuresReq = req.createFeaturesRequest();
      featuresReq.addFeature("urn:ietf:params:xml:ns:xmpp-session", "session");
      featuresReq.send();
    }
  }

  /**
   * @param req
   * @throws IOException
   */
  @SuppressWarnings("unchecked")
  private void doSIPGateway(XmppServletStanzaRequest req) throws ServletException, IOException {
    // this is NOT in 'convergence.sample.prism.voxeo.com' domain. send to other
    // domain.
    if (!req.getSession().getApplicationServDomains().contains(req.getTo().getDomain())) {
      XmppServletStanzaRequest newReq = _xmppFactory.createStanzaRequest(_xmppFactory
          .createJID(req.getTo().getDomain()), req.getElement(), req.getTo());
      newReq.send();
      log("Sent message to domain: " + req.getTo().getDomain() + " message: " + newReq.getElement().asXML());

      return;
    }
    // SIP gateway
    else {
      String sipmsg = toString(req);

      SipServletRequest request = _sipFactory.createRequest(_sipFactory.createApplicationSession(), "MESSAGE",
          _sipFactory.createAddress("sip:" + req.getFrom().getBareJID().toString()), _sipFactory.createAddress("sip:"
              + ReceiveMessageSIPServlet.SIP_USER));

      final String to = "sip:" + ReceiveMessageSIPServlet.SIP_USER.toLowerCase();
      final Map<String, URI> addresses = (Map<String, URI>) getServletContext().getAttribute(
          ReceiveMessageSIPServlet.SIPAddresses_AttributeName);

      SipURI routeURI = (SipURI) addresses.get(to);
      routeURI.setLrParam(true);
      request.setRequestURI(routeURI);

      request.getSession().setHandler("ReceiveMessageSIPServlet");

      request.setContent(sipmsg, "text/plain");
      request.send();

      log("Sent sip request to: " + ReceiveMessageSIPServlet.SIP_USER + ". Message:" + sipmsg);
      return;
    }
  }

  /**
   * @param req
   * @return
   */
  private String toString(XmppServletStanzaRequest req) {
    StringBuffer sb = new StringBuffer();
    sb.append(req.getElement().element("body").getText());
    return sb.toString();
  }

  /**
   * @param req
   * @throws IOException
   */
  private void doHTTPGateway(XmppServletStanzaRequest req) throws IOException {
    // this is NOT in 'xmpp.tropo.voxeo.com'. send to other domain.
    if (!req.getSession().getApplicationServDomains().contains(req.getTo().getDomain())) {
      XmppServletStanzaRequest newReq = _xmppFactory.createStanzaRequest(_xmppFactory
          .createJID(req.getTo().getDomain()), req.getElement(), req.getTo());
      newReq.send();
      log("Sent message to domain: " + req.getTo().getDomain() + " message: " + newReq.getElement().asXML());
      return;
    }
    // http gateway
    else {
      Element stanzaResp = null;
      try {
        stanzaResp = httpGateway(req);
      }
      catch (Exception e) {
        e.printStackTrace();
      }
      XmppServletStanzaRequest newReq = _xmppFactory.createStanzaRequest(req.getSession(), stanzaResp);
      newReq.send();
      log("Sent message:" + newReq.getElement().asXML());
      return;
    }
  }

  /**
   * @param req
   * @param m
   * @param stanzaResp
   * @throws KeyManagementException
   * @throws UnrecoverableKeyException
   * @throws NoSuchAlgorithmException
   * @throws KeyStoreException
   * @throws IOException
   * @throws ClientProtocolException
   */
  private Element httpGateway(XmppServletStanzaRequest req) throws KeyManagementException, UnrecoverableKeyException,
      NoSuchAlgorithmException, KeyStoreException, IOException {
    Element stanzaResp = DocumentHelper.createElement("message");
    StringBuffer sb = new StringBuffer();
    if (req.getElement().attribute("from") != null) {
      sb.append("from=" + req.getElement().attribute("from").getValue());
      stanzaResp.addAttribute("to", req.getElement().attribute("from").getValue());
    }
    if (req.getElement().attribute("to") != null) {
      sb.append("&to=" + req.getElement().attribute("to").getValue());
      stanzaResp.addAttribute("from", req.getElement().attribute("to").getValue());
    }
    if (req.getElement().attribute("type") != null) {
      sb.append("&type=" + req.getElement().attribute("type").getValue());
      stanzaResp.addAttribute("type", req.getElement().attribute("type").getValue());
    }
    if (req.getElement().element("body") != null) {
      sb.append("&body=" + req.getElement().element("body").getText());
    }
    if (req.getElement().element("subject") != null) {
      sb.append("&subject=" + req.getElement().element("subject").getText());
      stanzaResp.addElement("subject").setText(req.getElement().element("subject").getText());
    }
    if (req.getElement().element("thread") != null) {
      sb.append("&thread=" + req.getElement().element("thread").getText());
      stanzaResp.addElement("thread").setText(req.getElement().element("thread").getText());
    }

    String _url = "http://221.122.54.86:6061/bots/echo.jsp?a=b&c=d";

    String httpResp = HttpClient.postData(_url, sb.toString(), "application/x-www-form-urlencoded; charset=UTF-8");
    log("Sent http request to: " + _url + ". Message:" + sb.toString());

    log("Received http response:" + httpResp);
    stanzaResp.addElement("body").setText(httpResp);
    return stanzaResp;
  }

  // Do echo, parse the received message and send back the same message.
  private void doEcho(XmppServletStanzaRequest req) throws IOException {
    // construst echo element.
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
