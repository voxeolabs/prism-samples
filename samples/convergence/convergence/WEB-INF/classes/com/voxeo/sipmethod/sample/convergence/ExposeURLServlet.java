package com.voxeo.sipmethod.sample.convergence;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;

import com.voxeo.servlet.xmpp.JID;
import com.voxeo.servlet.xmpp.XmppFactory;
import com.voxeo.servlet.xmpp.XmppServlet;
import com.voxeo.servlet.xmpp.XmppServletStanzaRequest;
import com.voxeo.servlet.xmpp.XmppSession;

/**
 * This servlet demonstrate how to send XMPP messages to xmpp client in a HTTP
 * servlet.
 * 
 * @author zhuwillie
 */
public class ExposeURLServlet extends HttpServlet {

  private static final long serialVersionUID = 6122533723019935575L;

  private XmppFactory _xmppFactory;

  public static String HTTP_USER = "httpuserg@convergence.sample.prism.voxeo.com";

  @Override
  public void init() throws ServletException {
    super.init();

    // Get a reference to the XMPPFactory.
    _xmppFactory = (XmppFactory) getServletContext().getAttribute(XmppServlet.XMPP_FACTORY);
  }

  /**
   * Delegate to doPost
   */
  @Override
  public void doGet(final HttpServletRequest request, final HttpServletResponse response) throws ServletException,
      IOException {
    doPost(request, response);
  }

  @SuppressWarnings("unchecked")
  @Override
  public void doPost(final HttpServletRequest request, final HttpServletResponse response) throws ServletException,
      IOException {

    Map<JID, XmppSession> clientSessions = null;
    // send presence message to xmpp client.
    Set<String> clients = (Set) getServletContext().getAttribute(SimpleImServerXmppServlet.ActiveClientAttributeName);

    clients.add(HTTP_USER);
    for (String s : clients) {
      Element stanzaResp = DocumentHelper.createElement("presence");
      stanzaResp.addAttribute("from", HTTP_USER);
      stanzaResp.addAttribute("to", s);

      clientSessions = (Map<JID, XmppSession>) getServletContext().getAttribute(
          SimpleImServerXmppServlet.XMPPSessionMapAttributeName);

      if (clientSessions.size() == 0) {
        log("clientSessionMap is empty");
        return;
      }
      XmppSession xmppSession = clientSessions.get(_xmppFactory.createJID(s).getBareJID());
      if (xmppSession != null) {
        XmppServletStanzaRequest xmppReq = _xmppFactory.createStanzaRequest(xmppSession, stanzaResp);
        xmppReq.send();
        log("Registered presence message:" + xmppReq.getElement().asXML());
      }
    }

    // construct message. here just use some fixed value, you can get these info
    // from the http request.
    String message = "hello, this is from http.";
    Element messageStanza = DocumentHelper.createElement("message");
    messageStanza.addAttribute("from", HTTP_USER);
    messageStanza.addAttribute("type", "chat");
    messageStanza.addElement("body").setText(message);

    // send a message to all online xmpp client.
    Set<JID> keys = clientSessions.keySet();
    for (JID jid : keys) {
      messageStanza.addAttribute("to", jid.toString());
      XmppServletStanzaRequest req = _xmppFactory.createStanzaRequest(clientSessions.get(jid), messageStanza);
      req.send();
      log("Sent xmpp message : " + req.getElement().asXML() + " from http servlet.");
    }

    response.setContentType("text/html");
    final PrintWriter out = response.getWriter();
    out.println("<html>");
    out.println("<body>");
    out.println("<head>");

    final String title = "ThreeProtocolCommunication";
    out.println("<title>" + title + "</title>");
    out.println("</head>");
    out.println("<body bgcolor=\"white\">");

    out.println("<br><br>" + "Sent message to:" + "<br>");

    for (JID jid : keys) {
      out.println(jid.toString());
      out.print("<br>");
    }

    out.println("</body>");
    out.println("</html>");
  }
}
