package com.voxeo.sipmethod.sample.convergence;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
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
 * This servlet demonstrate how to send XMPP messages to xmpp client in a HTTP
 * servlet.
 * 
 * @author zhuwillie
 */
public class ExposeURLServlet extends HttpServlet {

  private static final long serialVersionUID = 6122533723019935575L;

  private XmppFactory _xmppFactory;

  private XmppSessionsUtil _xmppSessionUtil;

  public static String HTTP_USER = "httpuserg@convergence.sample.prism.voxeo.com";

  @Override
  public void init() throws ServletException {
    super.init();

    // Get a reference to the XMPPFactory.
    _xmppFactory = (XmppFactory) getServletContext().getAttribute(XmppServlet.XMPP_FACTORY);
    _xmppSessionUtil = (XmppSessionsUtil) getServletContext().getAttribute(XmppServlet.SESSIONUTIL);
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
    // send presence message to xmpp client.
    Set<String> clients = (Set) getServletContext().getAttribute(SimpleImServerXmppServlet.ActiveClientAttributeName);

    clients.add(HTTP_USER);
    for (String s : clients) {
      List<XmppSession> sessions = _xmppSessionUtil.getSessions(_xmppFactory.createJID(s).getBareJID());
      if (sessions != null) {
        for (XmppSession session : sessions) {
          PresenceMessage xmppReq = session.createPresence(HTTP_USER, (String) null, (Element[]) null);
          xmppReq.send();
          log("Registered presence message:");
        }
      }
    }

    // construct message. here just use some fixed value, you can get these info
    // from the http request.
    Element body = null;
    try {
      body = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument().createElement("body");
    }
    catch (DOMException e) {
      e.printStackTrace();
    }
    catch (ParserConfigurationException e) {
      e.printStackTrace();
    }
    body.setTextContent("hello, this is from http.");
    // send a message to all online xmpp client.
    StringBuilder sentUsers = new StringBuilder();
    for (String s : clients) {
      List<XmppSession> sessions = _xmppSessionUtil.getSessions(_xmppFactory.createJID(s).getBareJID());
      if (sessions != null) {
        for (XmppSession session : sessions) {
          sentUsers.append(session.getRemoteJID().toString()).append("  ");
          InstantMessage req = session.createMessage(HTTP_USER, XmppServletMessage.TYPE_CHAT, body);
          req.send();
          log("Sent xmpp message  from http servlet.");
        }
      }
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

    out.println(sentUsers.toString());

    out.println("</body>");
    out.println("</html>");
  }
}
