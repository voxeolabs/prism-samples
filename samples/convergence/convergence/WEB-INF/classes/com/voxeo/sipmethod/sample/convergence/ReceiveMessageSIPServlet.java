package com.voxeo.sipmethod.sample.convergence;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.sip.SipServlet;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipURI;
import javax.servlet.sip.URI;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;

import com.voxeo.servlet.xmpp.JID;
import com.voxeo.servlet.xmpp.XmppFactory;
import com.voxeo.servlet.xmpp.XmppServlet;
import com.voxeo.servlet.xmpp.XmppServletStanzaRequest;
import com.voxeo.servlet.xmpp.XmppSession;

/**
 * This servlet demonstrate how to send XMPP messages to xmpp client in a SIP
 * servlet.
 * 
 * @author zhuwillie
 */
public class ReceiveMessageSIPServlet extends SipServlet {

  private static final long serialVersionUID = 3471750761178715510L;

  private XmppFactory _xmppFactory;

  public static String SIP_USER = "sipuserf@convergence.sample.prism.voxeo.com";

  public static String SIPAddresses_AttributeName = "com.micromethod.sample.convergence.Addresses";

  @Override
  public void init() throws ServletException {
    super.init();
    final Map<String, URI> addresses = new ConcurrentHashMap<String, URI>();
    getServletContext().setAttribute(SIPAddresses_AttributeName, addresses);

    // Get a reference to the XMPPFactory.
    _xmppFactory = (XmppFactory) getServletContext().getAttribute(XmppServlet.XMPP_FACTORY);
  }

  @SuppressWarnings("unchecked")
  @Override
  protected void doRegister(final SipServletRequest req) throws IOException, ServletException {
    // get addresses map from context attribute.
    final Map<String, URI> addresses = (Map<String, URI>) getServletContext().getAttribute(SIPAddresses_AttributeName);

    final String aor = req.getFrom().getURI().toString().toLowerCase();

    int expire = req.getExpires();
    if (expire == -1) {
      expire = req.getAddressHeader("Contact").getExpires();
    }

    // The non-zero value of Expires header indicates a sign-in.
    if (expire > 0) {
      // Keep the name/address mapping.
      addresses.put(aor, req.getAddressHeader("Contact").getURI());
      log("Registered sipendpoint: " + aor + "--->" + req.getAddressHeader("Contact").getURI().toString());

      // send presence message to xmpp client.
      Set<String> aliveClient = (Set) getServletContext().getAttribute(
          SimpleImServerXmppServlet.ActiveClientAttributeName);
      aliveClient.add(SIP_USER);
      for (String s : aliveClient) {
        Element stanzaResp = DocumentHelper.createElement("presence");
        stanzaResp.addAttribute("from", SIP_USER);
        stanzaResp.addAttribute("to", s);

        Map<JID, XmppSession> clientSessionMap = (Map<JID, XmppSession>) getServletContext().getAttribute(
            SimpleImServerXmppServlet.XMPPSessionMapAttributeName);

        XmppSession xmppSession = clientSessionMap.get(_xmppFactory.createJID(s).getBareJID());

        if (xmppSession != null) {
          XmppServletStanzaRequest xmppReq = _xmppFactory.createStanzaRequest(xmppSession, stanzaResp);
          xmppReq.send();
          log("Registered presence message:" + xmppReq.getElement().asXML());
        }
      }
    }
    else {

      // The zero value of Expires header indicates a sign-off.
      // Remove the name/address mapping.
      addresses.remove(aor);

      // send presence message to xmpp client.
      Set<String> aliveClient = (Set) getServletContext().getAttribute(
          SimpleImServerXmppServlet.ActiveClientAttributeName);
      aliveClient.remove(SIP_USER);

      for (String s : aliveClient) {
        Element stanzaResp = DocumentHelper.createElement("presence");
        stanzaResp.addAttribute("from", SIP_USER);
        stanzaResp.addAttribute("to", s);
        stanzaResp.addAttribute("type", "unavailable");

        Map<JID, XmppSession> clientSessionMap = (Map<JID, XmppSession>) getServletContext().getAttribute(
            SimpleImServerXmppServlet.XMPPSessionMapAttributeName);

        XmppSession sid = clientSessionMap.get(_xmppFactory.createJID(s).getBareJID());
        XmppServletStanzaRequest xmppReq = _xmppFactory.createStanzaRequest(sid, stanzaResp);
        xmppReq.send();
        log("Unregistered presence message:" + xmppReq.getElement().asXML());
      }
    }

    // We accept the sign-in or sign-off by returning 200 OK response.
    req.createResponse(SipServletResponse.SC_OK).send();
    req.getApplicationSession().setExpires(1);
  }

  @SuppressWarnings("unchecked")
  @Override
  protected void doMessage(SipServletRequest sipReq) throws ServletException, IOException {
    log("Received sip message:" + sipReq);
    sipReq.createResponse(200).send();

    if (sipReq.getContentType().equalsIgnoreCase("text/plain") || sipReq.getContentType().equalsIgnoreCase("text/html")) {
      // use fixed user here.
      String xmppfrom = SIP_USER;

      SipURI siptoURI = (SipURI) sipReq.getTo().getURI();
      String xmppto = siptoURI.getUser() + "@" + siptoURI.getHost();

      Map<JID, XmppSession> clientSessionMap = (Map<JID, XmppSession>) getServletContext().getAttribute(
          SimpleImServerXmppServlet.XMPPSessionMapAttributeName);

      if (clientSessionMap.size() == 0) {
        log("clientSessionMap is empty");
        return;
      }

      // send message to xmpp client
      XmppSession clientSession = clientSessionMap.get(_xmppFactory.createJID(xmppto));
      if (clientSession != null) {
        Element stanzaResp = DocumentHelper.createElement("message");
        stanzaResp.addAttribute("to", _xmppFactory.createJID(xmppto).toString());
        stanzaResp.addAttribute("from", _xmppFactory.createJID(xmppfrom).toString());
        stanzaResp.addAttribute("type", "chat");

        stanzaResp.addElement("body").setText(getContent(sipReq));

        XmppServletStanzaRequest xmppReq = _xmppFactory.createStanzaRequest(clientSession, stanzaResp);
        xmppReq.send();
        log("Sent xmpp message:" + xmppReq.getElement().asXML() + " from sip servlet.");
      }
      else {
        log(xmppto + "is offline. dropped this sip message");
      }
      // TODO if the siptoURI.getHost() is not one of the serving domains of
      // this XMPP application, send message to other xmpp domain.
    }
  }

  private static final Pattern P = Pattern.compile("<[^>]+>([^<]+)<[^>]+>");

  private String getContent(SipServletRequest sipReq) throws UnsupportedEncodingException, IOException {
    String s = sipReq.getContent().toString();
    if (sipReq.getContentType().equalsIgnoreCase("text/html")) {
      Matcher m = P.matcher(s);
      String a = "";
      while (m.find()) {
        a += m.group(1);
      }
      s = a;
    }
    return s;
  }

}
