package com.voxeo.sipmethod.sample.convergence;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import javax.servlet.ServletException;
import javax.servlet.sip.SipFactory;
import javax.servlet.sip.SipServlet;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipURI;
import javax.servlet.sip.URI;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.http.client.ClientProtocolException;
import org.w3c.dom.DOMException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.voxeo.servlet.xmpp.IQRequest;
import com.voxeo.servlet.xmpp.IQResponse;
import com.voxeo.servlet.xmpp.InstantMessage;
import com.voxeo.servlet.xmpp.PresenceMessage;
import com.voxeo.servlet.xmpp.StanzaError;
import com.voxeo.servlet.xmpp.XmppFactory;
import com.voxeo.servlet.xmpp.XmppServlet;
import com.voxeo.servlet.xmpp.XmppSession;
import com.voxeo.servlet.xmpp.XmppSessionsUtil;

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

  private Set<String> clients = null;

  public static String ActiveClientAttributeName = "com.micromethod.sipmethod.sample.xmppsample.aliveclient";

  public static String ECHO_USER = "echo@convergence.sample.prism.voxeo.com";

  private List<String> _servingDomains;

  private XmppSessionsUtil _sessionUtil;

  @Override
  public void init() throws ServletException {
    clients = new CopyOnWriteArraySet<String>();
    clients.add(ECHO_USER);
    getServletContext().setAttribute(ActiveClientAttributeName, clients);

    // Get a reference to the XMPPFactory.
    _xmppFactory = (XmppFactory) getServletContext().getAttribute(XmppServlet.XMPP_FACTORY);
    _sipFactory = (SipFactory) getServletContext().getAttribute(SipServlet.SIP_FACTORY);

    _servingDomains = (List<String>) this.getServletContext().getAttribute(XmppServlet.SERVING_DOMIANS);
    _sessionUtil = (XmppSessionsUtil) this.getServletContext().getAttribute(XmppServlet.SESSIONUTIL);
  }

  @Override
  protected void doMessage(InstantMessage req) throws ServletException, IOException {
    log("Received message:" + req.toString());

    if (req.getElement("body") == null && req.getElement("subject") == null) {
      return;
    }

    // if the destination is echo user and this application is serving domain
    // 'xmpp.im.voxoe.com'.
    if (req.getTo().getBareJID().toString().equalsIgnoreCase(ECHO_USER)
        && _servingDomains.contains("convergence.sample.prism.voxeo.com")) {
      doEcho(req);
    }

    // if the destination is the http user. and this application is serving
    // domain 'xmpp.im.voxoe.com'.
    else if (req.getTo().getBareJID().toString().equalsIgnoreCase(ExposeURLServlet.HTTP_USER)
        && _servingDomains.contains("convergence.sample.prism.voxeo.com")) {
      // you can post this message to a http server, then get the response
      // message from http response and send back to xmpp client.
      // doHTTPGateway(req);

      // here just echo.
      doEcho(req);
    }
    // process the message to the SIP user,
    // sipUserF@convergence.sample.prism.voxeo.com.
    else if (req.getTo().getBareJID().toString().equalsIgnoreCase(ReceiveMessageSIPServlet.SIP_USER)
        && _servingDomains.contains("convergence.sample.prism.voxeo.com")) {
      doSIPGateway(req);
    }
    // process message to xmpp client.
    else {
      List<XmppSession> sessions = _sessionUtil.getSessions(req.getTo().getBareJID());
      // intra-domain message
      if (sessions != null && sessions.size() > 0) {
        XmppSession session = sessions.get(0);
        InstantMessage newReq = session.createMessage(req.getFrom().toString(), req.getType(), req.getElements()
            .toArray(new Element[] {}));
        newReq.send();
        log("Sent message");
      }
      // user not online.
      else if (_servingDomains.contains(req.getTo().getDomain())) {
        log("User: " + req.getTo().getBareJID() + " is offline. Drop this message");
      }
      // inter-domain message. send the message to other domain.
      else {
        InstantMessage newReq = _xmppFactory.createMessage(req.getFrom(), req.getTo(), req.getType(), req.getElements()
            .toArray(new Element[] {}));

        newReq.send();
        log("Sent message to domain: " + req.getTo().getDomain());
      }
    }
  }

  @Override
  protected void doIQRequest(IQRequest req) throws ServletException, IOException {
    log("Received iq request");
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

    // 2 process roster request.
    /**
     * <iq type="result" id="RM4Bf-2" to="willie3@localhost/spark2"><query
     * xmlns="jabber:iq:roster"><item jid="willie4@localhost" name="willie4"
     * subscription="both"><group>Friends</group></item></query></iq>
     */
    element = req.getElement("query", "jabber:iq:roster");
    if (element != null) {
      Element roster = null;
      try {
        roster = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
            .createElementNS("jabber:iq:roster", "query");
      }
      catch (DOMException e) {
        e.printStackTrace();
      }
      catch (ParserConfigurationException e) {
        e.printStackTrace();
      }

      Element item = roster.getOwnerDocument().createElement("item");
      item.setAttribute("jid", "usera@xmpp.tropo.voxeo.com");
      item.setAttribute("name", "usera@xmpp.tropo.voxeo.com");
      item.setAttribute("subscription", "both");
      Element groupElement = roster.getOwnerDocument().createElement("group");
      groupElement.setTextContent("Friends");
      roster.appendChild(item);

      Element item2 = (Element) item.cloneNode(true);
      item2.setAttribute("jid", "userb@xmpp.tropo.voxeo.com");
      item2.setAttribute("name", "userb@xmpp.tropo.voxeo.com");
      roster.appendChild(item2);

      Element item3 = (Element) item.cloneNode(true);
      item3.setAttribute("jid", "userc@convergence.sample.prism.voxeo.com");
      item3.setAttribute("name", "userc@convergence.sample.prism.voxeo.com");
      roster.appendChild(item3);

      Element item4 = (Element) item.cloneNode(true);
      item4.setAttribute("jid", "userd@convergence.sample.prism.voxeo.com");
      item4.setAttribute("name", "userd@convergence.sample.prism.voxeo.com");
      roster.appendChild(item4);

      Element item5 = (Element) item.cloneNode(true);
      item5.setAttribute("jid", "echo@convergence.sample.prism.voxeo.com");
      item5.setAttribute("name", "echo@convergence.sample.prism.voxeo.com");
      roster.appendChild(item5);

      Element item6 = (Element) item.cloneNode(true);
      item6.setAttribute("jid", "sipuserf@convergence.sample.prism.voxeo.com");
      item6.setAttribute("name", "sipuserf@convergence.sample.prism.voxeo.com");
      roster.appendChild(item6);

      Element item7 = (Element) item.cloneNode(true);
      item7.setAttribute("jid", "httpuserg@convergence.sample.prism.voxeo.com");
      item7.setAttribute("name", "httpuserg@convergence.sample.prism.voxeo.com");
      roster.appendChild(item7);

      Element item8 = (Element) item.cloneNode(true);
      item8.setAttribute("jid", "convergence@convergence.sample.prism.voxeo.com");
      item8.setAttribute("name", "convergence@convergence.sample.prism.voxeo.com");
      roster.appendChild(item8);

      req.createResult(roster).send();
      return;
    }

    // process other iq request. return stanza error.
    IQResponse resElement = req.createError(StanzaError.Type.CANCEL, StanzaError.Condition.FEATURE_NOT_IMPLEMENTED,
        null, null, null);

    resElement.send();
  }

  @Override
  protected void doPresence(PresenceMessage req) throws ServletException, IOException {
    log("Received presence");

    // <presence id="4g5yq-6"
    // to="usera@xmpp.tropo.voxeo.com/someresource"><status
    // >Available</status><priority>1</priority></presence>
    clients.add(req.getFrom().toString());
    getServletContext().setAttribute(ActiveClientAttributeName, clients);

    for (String s : clients) {
      List<XmppSession> sessions = _sessionUtil.getSessions(_xmppFactory.createJID(s));
      // intra-domain message
      if (sessions != null && sessions.size() > 0) {
        XmppSession session = sessions.get(0);
        for (String ss : clients) {
          PresenceMessage xmppReq = session.createPresence(ss, req.getFrom().toString(), null);
          if (xmppReq != null) {
            xmppReq.send();
          }
        }
      }
    }
  }

  /**
   * @param req
   * @throws IOException
   */
  @SuppressWarnings("unchecked")
  private void doSIPGateway(InstantMessage req) throws ServletException, IOException {
    // this is NOT in 'convergence.sample.prism.voxeo.com' domain. send to other
    // domain.
    if (!_servingDomains.contains(req.getTo().getDomain())) {
      InstantMessage newReq = _xmppFactory.createMessage(req.getFrom(), req.getTo(), req.getType(), req.getElements()
          .toArray(new Element[] {}));
      newReq.send();
      log("Sent message to domain: " + req.getTo().getDomain());
      return;
    }
    // SIP gateway
    else {
      String sipmsg = toString(req);

      final Map<String, URI> addresses = (Map<String, URI>) getServletContext().getAttribute(
          ReceiveMessageSIPServlet.SIPAddresses_AttributeName);
      String aor = addresses.keySet().iterator().next();

      SipServletRequest request = _sipFactory.createRequest(_sipFactory.createApplicationSession(), "MESSAGE",
          _sipFactory.createAddress("sip:" + req.getFrom().getBareJID().toString()), _sipFactory.createAddress(aor));

      SipURI routeURI = (SipURI) addresses.get(aor);
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
  private String toString(InstantMessage req) {
    StringBuffer sb = new StringBuffer();
    sb.append(req.getElement("body").getTextContent());
    return sb.toString();
  }

  /**
   * @param req
   * @throws IOException
   */
  private void doHTTPGateway(InstantMessage req) throws IOException, ServletException {
    // this is NOT in 'xmpp.tropo.voxeo.com'. send to other domain.
    if (!_servingDomains.contains(req.getTo().getDomain())) {
      InstantMessage newReq = _xmppFactory.createMessage(req.getFrom(), req.getTo(), req.getType(), req.getElements()
          .toArray(new Element[] {}));
      newReq.send();
      log("Sent message to domain: " + req.getTo().getDomain());
      return;
    }
    // http gateway
    else {
      String httpResp = null;
      try {
        httpResp = httpGateway(req);
      }
      catch (Exception e) {
        e.printStackTrace();
      }
      InstantMessage newReq = req.getSession().createMessage(req.getTo().toString(), req.getType());
      newReq.setThread(req.getThread());
      newReq.addSubject(req.getSubject());
      newReq.addBody(httpResp);
      newReq.send();
      log("Sent message");
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
  private String httpGateway(InstantMessage req) throws KeyManagementException, UnrecoverableKeyException,
      NoSuchAlgorithmException, KeyStoreException, IOException {
    StringBuffer sb = new StringBuffer();
    if (req.getElement().getAttribute("from") != null) {
      sb.append("from=" + req.getElement().getAttribute("from"));
    }
    if (req.getElement().getAttribute("to") != null) {
      sb.append("&to=" + req.getElement().getAttribute("to"));
    }
    if (req.getElement().getAttribute("type") != null) {
      sb.append("&type=" + req.getElement().getAttribute("type"));
    }
    if (getFirstChildElementByTagName(req.getElement(), "body") != null) {
    }
    if (getFirstChildElementByTagName(req.getElement(), "subject") != null) {
      sb.append("&subject=" + getFirstChildElementByTagName(req.getElement(), "subject").getTextContent());
    }
    if (getFirstChildElementByTagName(req.getElement(), "thread") != null) {
      sb.append("&thread=" + getFirstChildElementByTagName(req.getElement(), "thread").getTextContent());
    }

    String _url = "http://221.122.54.86:6061/bots/echo.jsp?a=b&c=d";

    String httpResp = HttpClient.postData(_url, sb.toString(), "application/x-www-form-urlencoded; charset=UTF-8");
    log("Sent http request to: " + _url + ". Message:" + sb.toString());

    log("Received http response:" + httpResp);
    return httpResp;
  }

  // Do echo, parse the received message and send back the same message.
  private void doEcho(InstantMessage req) throws IOException {
    req.getSession().createMessage(req.getTo().toString(), req.getType(), req.getElements().toArray(new Element[] {}))
        .send();
  }

  public static Element getFirstChildElementByTagName(Element parent, String tagName) {
    Node n = parent.getFirstChild();
    while (n != null) {
      n = n.getNextSibling();
      if (Node.ELEMENT_NODE == n.getNodeType() && ((Element) n).getTagName().equals(tagName)) {
        return (Element) n;
      }
    }

    return null;
  }
}
