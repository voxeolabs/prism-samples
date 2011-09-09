package com.voxeo.sipmethod.sample.convergence;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.sip.SipFactory;
import javax.servlet.sip.SipServlet;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipURI;
import javax.servlet.sip.URI;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.DOMException;
import org.w3c.dom.Element;

import com.voxeo.servlet.xmpp.InstantMessage;
import com.voxeo.servlet.xmpp.PresenceMessage;
import com.voxeo.servlet.xmpp.XmppFactory;
import com.voxeo.servlet.xmpp.XmppServlet;
import com.voxeo.servlet.xmpp.XmppServletMessage;
import com.voxeo.servlet.xmpp.XmppSession;
import com.voxeo.servlet.xmpp.XmppSessionsUtil;

/**
 * This servlet demonstrate how to send XMPP messages to xmpp client in a SIP
 * servlet.
 * 
 * @author zhuwillie
 */
public class ReceiveMessageSIPServlet extends SipServlet {

  private static final long serialVersionUID = 3471750761178715510L;

  private XmppFactory _xmppFactory;

  private SipFactory _sipFactory;

  private XmppSessionsUtil _xmppSessionUtil;

  public static String SIP_USER = "sipuserf@convergence.sample.prism.voxeo.com";

  public static String SIPAddresses_AttributeName = "com.micromethod.sample.convergence.Addresses";

  String _queryRegInfoURL = "http://localhost:8080/registrar/query?fromOtherSample=true";

  Map<String, URI> addresses = null;

  @Override
  public void init() throws ServletException {
    super.init();
    if (getInitParameter("QueryRegInfoURL") != null) {
      _queryRegInfoURL = getInitParameter("QueryRegInfoURL");
    }

    // Get a reference to the XMPPFactory.
    _xmppFactory = (XmppFactory) getServletContext().getAttribute(XmppServlet.XMPP_FACTORY);

    _xmppSessionUtil = (XmppSessionsUtil) getServletContext().getAttribute(XmppServlet.SESSIONUTIL);

    _sipFactory = (SipFactory) getServletContext().getAttribute(SipServlet.SIP_FACTORY);

    TimerTask task = new TimerTask() {

      @Override
      public void run() {
        try {
          Map<String, URI> result = getAddresses();

          if (result.size() > 0) {
            // send presence message to xmpp client.
            Set<String> aliveClient = (Set) getServletContext().getAttribute(
                SimpleImServerXmppServlet.ActiveClientAttributeName);
            aliveClient.add(SIP_USER);
            for (String s : aliveClient) {
              Element stanzaResp = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
                  .createElement("presence");
              stanzaResp.setAttribute("from", SIP_USER);
              stanzaResp.setAttribute("to", s);

              List<XmppSession> xmppSessions = _xmppSessionUtil.getSessions(_xmppFactory.createJID(s).getBareJID());

              for (XmppSession xmppSession : xmppSessions) {
                if (xmppSession != null) {
                  PresenceMessage xmppReq = xmppSession.createPresence(SIP_USER, s, null);
                  xmppReq.send();
                  log("Registered presence message");
                }
              }
            }
          }
          else {
            // send presence message to xmpp client.
            Set<String> aliveClient = (Set) getServletContext().getAttribute(
                SimpleImServerXmppServlet.ActiveClientAttributeName);
            aliveClient.remove(SIP_USER);

            for (String s : aliveClient) {

              List<XmppSession> xmppSessions = _xmppSessionUtil.getSessions(_xmppFactory.createJID(s).getBareJID());

              for (XmppSession sid : xmppSessions) {
                if (sid != null) {
                  PresenceMessage xmppReq = sid.createPresence(SIP_USER, XmppServletMessage.TYPE_UNAVAILABLE);
                  xmppReq.send();
                  log("Unregistered presence message");
                }
              }

            }
          }

          addresses = result;

          getServletContext().setAttribute(SIPAddresses_AttributeName, addresses);
        }
        catch (Exception e) {
          e.printStackTrace();
        }
      }

    };

    Timer timer = new Timer();
    timer.scheduleAtFixedRate(task, 10000, 5000);
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

      // send message to xmpp client
      List<XmppSession> clientSessions = _xmppSessionUtil.getSessions(_xmppFactory.createJID(xmppto));
      if (clientSessions != null) {
        for (XmppSession clientSession : clientSessions) {
          Element stanzaResp = null;
          try {
            stanzaResp = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument().createElement("body");
          }
          catch (DOMException e) {
            e.printStackTrace();
          }
          catch (ParserConfigurationException e) {
            e.printStackTrace();
          }
          stanzaResp.setTextContent(getContent(sipReq));
          InstantMessage xmppReq = clientSession.createMessage(xmppfrom, XmppServletMessage.TYPE_CHAT, stanzaResp);
          xmppReq.send();
          log("Sent xmpp message from sip servlet.");
        }

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

  private Map<String, URI> getAddresses() throws Exception {
    Map<String, URI> regInfo = new HashMap<String, URI>();

    String result = getRegInfo(_queryRegInfoURL);

    if (result != null && result.length() > 0) {
      String[] s = result.split("\r\n");

      for (String t : s) {
        String[] a = t.split(" ");
        if (a.length == 2) {
          regInfo.put(a[0], _sipFactory.createURI(a[1]));
        }

      }
    }
    return regInfo;
  }

  private String getRegInfo(String posturl) throws IOException {
    URL url = new URL(posturl);
    URLConnection conn = url.openConnection();
    conn.setDoOutput(false);

    if (conn instanceof HttpURLConnection) {
      HttpURLConnection httpConnection = (HttpURLConnection) conn;
      if (httpConnection.getResponseCode() != 200) {
        throw new IOException("Status " + httpConnection.getResponseCode());
      }
    }
    return getContentFromConn(conn);
  }

  private String getContentFromConn(URLConnection conn) throws IOException {
    String response = null;

    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
    StringBuffer buf = new StringBuffer();
    String line;
    while (null != (line = br.readLine())) {
      buf.append(line).append("\r\n");
    }
    response = buf.toString();
    br.close();
    return response.trim();
  }
}
