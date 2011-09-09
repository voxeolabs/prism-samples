package com.micromethod.sipmethod.sample.echo;

import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

import javax.sdp.Connection;
import javax.sdp.SdpException;
import javax.sdp.SdpFactory;
import javax.sdp.SessionDescription;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.sip.SipFactory;
import javax.servlet.sip.SipServlet;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipURI;
import javax.servlet.sip.URI;

/**
 * EchoServlet provides a simple example of a SIP servlet.<br/>
 * EchoServlet echoes instant messages sent by X-Lite.
 */
public class EchoServlet extends SipServlet {

  private static final long serialVersionUID = 3324393418036265483L;

  protected SipFactory _factory = null;

  /**
   * init() initializes the servelt.
   */
  @Override
  public void init() throws ServletException {
    super.init();
    // Create addresses map and put it in context attribute.
    final ServletContext context = getServletContext();
    final Map<String, URI> addresses = new HashMap<String, URI>();
    context.setAttribute("com.micromethod.sample.echoServlet.Addresses", addresses);
    _factory = (SipFactory) getServletContext().getAttribute("javax.servlet.sip.SipFactory");
  }

  /**
   * Invoked for SIP INVITE requests, which are sent by X-Lite to establish a
   * chat session.
   */
  @Override
  protected void doInvite(final SipServletRequest req) throws IOException, ServletException {
    // Get SDP message from SIP Content.
    if ("application/sdp".equals(req.getContentType())) {
      final SessionDescription sd = (SessionDescription) req.getContent();
      log(sd.toString());
    }

    req.createResponse(180).send();

    req.getSession().setAttribute("DialogType", "INVITE");

    try {
      // Get SdpFactory.
      final SdpFactory factory = (SdpFactory) this.getServletContext().getAttribute("javax.servlet.sdp.SdpFactory");

      // Create Session Description from SdpFactory.
      final SessionDescription sd = factory.createSessionDescription();

      // Create Connection Field.
      final Connection c = factory.createConnection("IN", "IP4", InetAddress.getLocalHost().getHostAddress(), 127, 2);

      // Set Connection Field to Session Description.
      sd.setConnection(c);

      // Create 200_OK Response.
      final SipServletResponse resp = req.createResponse(200);

      // Set SDP message to SIP message content.
      resp.setContent(sd, "application/sdp");

      resp.send();
    }
    catch (final SdpException e) {
      // Create Internal Server Error response and send.
      final SipServletResponse resp = req.createResponse(500);
      resp.setStatus(500, e.getMessage());

      resp.send();
    }
  }

  /**
   * Invoked for SIP REGISTER requests, which are sent by X-Lite for sign-in and
   * sign-off.
   */
  @Override
  protected void doRegister(final SipServletRequest req) throws IOException, ServletException {
    // get addresses map from context attribute.
    final ServletContext context = getServletContext();
    final Map<String, URI> addresses = (Map<String, URI>) context
        .getAttribute("com.micromethod.sample.echoServlet.Addresses");

    final String aor = req.getFrom().getURI().toString().toLowerCase();

    int expire = req.getExpires();
    if (expire == -1) {
      expire = req.getAddressHeader("Contact").getExpires();
    }

    // The non-zero value of Expires header indicates a sign-in.
    if (expire > 0) {
      // Keep the name/address mapping.
      addresses.put(aor, req.getAddressHeader("Contact").getURI());

      // reset addresses map in context attribute for replication.
      context.setAttribute("com.micromethod.sample.echoServlet.Addresses", addresses);
    }
    else {

      // The zero value of Expires header indicates a sign-off.
      // Remove the name/address mapping.
      addresses.remove(aor);

      // reset addresses map in context attribute for replication.
      context.setAttribute("com.micromethod.sample.echoServlet.Addresses", addresses);
    }

    // We accept the sign-in or sign-off by returning 200 OK response.
    req.createResponse(SipServletResponse.SC_OK).send();

    req.getApplicationSession().setExpires(1);
  }

  /**
   * Invoked for SIP MESSAGE requests, which are sent by X-Lite for instant
   * messages.
   */
  @Override
  protected void doMessage(final SipServletRequest req) throws IOException, ServletException {
    // get addresses map from context attribute.
    final ServletContext context = getServletContext();
    final Map addresses = (Map) context.getAttribute("com.micromethod.sample.echoServlet.Addresses");

    // We accept the instant message by returning 200 OK response.
    req.createResponse(SipServletResponse.SC_OK).send();

    // Create an echo SIP MESSAGE request with the same content.
    final SipServletRequest echo = req.getSession().createRequest("MESSAGE");

    final String charset = req.getCharacterEncoding();
    if (charset != null) {
      echo.setCharacterEncoding(charset);
    }

    echo.setContent(req.getContent(), req.getContentType());

    // Get the previous registered address for the sender.
    SipURI uri = (SipURI) addresses.get(req.getFrom().getURI().toString().toLowerCase());
    if (uri == null) {
      if (req.getAddressHeader("Contact") != null) {
        uri = (SipURI) req.getAddressHeader("Contact").getURI();
      }
      else {
        final SipURI requesturi = (SipURI) req.getRequestURI();
        final String user = requesturi.getUser();
        uri = _factory.createSipURI(user, req.getRemoteAddr());
        uri.setPort(req.getRemotePort());
        uri.setTransportParam(req.getTransport());
      }
    }

    echo.setRequestURI(uri);

    // Send the echo MESSAGE request back to Windows Messenger.
    echo.send();
  }

  /**
   * Invoked for SIP Info requests, which are sent by X-Lite for instant
   * messages.
   */
  @Override
  protected void doInfo(final SipServletRequest req) throws ServletException, IOException {
    // We accept invitation for a new session by returning 200 OK response.
    req.createResponse(SipServletResponse.SC_OK).send();
  }

  /**
   * Invoked for SIP 2xx class responses.
   */
  @Override
  protected void doSuccessResponse(final SipServletResponse resp) throws IOException, ServletException {
    // Print out when the echo message was accepted.
    if (resp.getMethod().equalsIgnoreCase("MESSAGE")) {
      System.out.println("\"" + resp.getRequest().getContent() + "\" was accepted: " + resp.getStatus());
      try {
        if (!"INVITE".equals(resp.getSession().getAttribute("DialogType"))) {
          resp.getSession().invalidate();
        }
      }
      catch (final IllegalStateException ise) {

      }
    }
  }

  /**
   * Invoked for SIP 4xx-6xx class responses.
   */
  @Override
  protected void doErrorResponse(final SipServletResponse resp) throws IOException, ServletException {
    // Print out when the echo message was rejected/
    if (resp.getMethod().equalsIgnoreCase("MESSAGE")) {
      System.out.println("\"" + resp.getRequest().getContent() + "\" was rejected: " + resp.getStatus());
    }
  }

  /**
   * Invoked for SIP BYE requests, which are sent by X-Lite to terminate a chat
   * session/
   */
  @Override
  protected void doBye(final SipServletRequest req) throws IOException, ServletException {
    // Accept session termination by returning 200 OK response.
    req.createResponse(SipServletResponse.SC_OK).send();
  }
}
