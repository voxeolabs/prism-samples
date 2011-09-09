package com.micromethod.sipmethod.sample.b2bua;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.sip.SipFactory;
import javax.servlet.sip.SipServlet;
import javax.servlet.sip.SipServletMessage;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipSession;
import javax.servlet.sip.SipURI;

/**
 * This servlet demonstrates how to write a back-to-back user agent (B2BUA)
 * using a SIP servlet and how to handle authentication.
 * <p>
 * The RFC 3261 defines a B2BUA as a logical entity that receives an invitation,
 * and acts as a UAS to process it. In order to determine how the request should
 * be answered, it acts as a UAC and initiates a call outwards. Unlike a proxy
 * server, it maintains complete call state and must participate in all requests
 * for a call. Since it is purely a concatenation of other logical functions, no
 * explicit definitions are needed for its behavior.
 * <p>
 * This servlet associates two SIP sessions with each other and basically relays
 * requests and responses received on one leg to the other.
 * <p>
 * This servlet demonstrate how to use
 * {@link SipServletRequest#addAuthHeader(SipServletResponse, String, String)}
 * method to handle 401 or 407 response.
 */
public class B2BUA extends SipServlet {

  private static final long serialVersionUID = -3020330681933829273L;

  protected SipFactory _factory = null;

  protected String _server = "127.0.0.1";

  protected int _port = 5060;

  protected String _transport = "udp";

  /**
   * init() initializes the servelt with initialization parameters.
   */
  @Override
  public void init() throws ServletException {
    super.init();
    // Get a reference to the SipFactory.
    _factory = (SipFactory) getServletContext().getAttribute("javax.servlet.sip.SipFactory");

    // Get the real UAS's IP
    final String remote = getInitParameter("remote");
    if (remote != null) {
      _server = remote;
    }

    // Get the real UAS's port
    final String port = getInitParameter("port");
    if (port != null) {
      _port = Integer.parseInt(port);
    }

    // Get the real UAS's transport type
    final String transport = getInitParameter("transport");
    if (transport != null) {
      _transport = transport;
    }
  }

  /**
   * doRequest(SipServletRequest) establishes the B2BUA-to-callee call leg and
   * forwards requests.
   */
  @Override
  protected void doRequest(final SipServletRequest req) throws ServletException, IOException {
    // Manages call legs (caller-to-B2BUA and B2BUA-to-calle) for initial
    // request.
    // Relay the request from the caller to the callee.
    if (req.isInitial()) {
      // Compose callee's URI based on the request URI
      final SipURI uri = (SipURI) req.getRequestURI();
      final String user = uri.getUser();
      final SipURI callee = _factory.createSipURI(user, _server);
      callee.setPort(_port);
      callee.setTransportParam(_transport);

      // Create the request-to-callee in the B2BUA-to-callee call leg
      @SuppressWarnings("deprecation")
      final SipServletRequest req2callee = _factory.createRequest(req, true);
      req2callee.setRequestURI(callee);
      copyContent(req, req2callee);

      // Keep the reference to the orginal request so we can relay the response
      // back.
      req2callee.setAttribute("PEER_REQ", req);

      // Associate two call legs.
      final SipSession caller2B2BUA = req.getSession();
      final SipSession B2BUA2callee = req2callee.getSession();
      caller2B2BUA.setAttribute("PEER", B2BUA2callee);
      B2BUA2callee.setAttribute("PEER", caller2B2BUA);

      // Remove the Contact header for REGISTER messages so that
      // container will fill out the proper Contact header for the
      // B2BUA-to-callee call leg.
      if (req2callee.getMethod().equalsIgnoreCase("REGISTER")) {
        req2callee.removeHeader("contact");
      }

      // Forward the request-to-callee to callee.
      req2callee.send();
    }
    // Relay the ACK from caller to the callee.
    else if (req.getMethod().equalsIgnoreCase("ACK")) {
      // Retrieve the two call legs.
      final SipSession caller2B2BUA = req.getSession();
      final SipSession B2BUA2callee = (SipSession) caller2B2BUA.getAttribute("PEER");

      // Retrieve the previous response from the callee to the B2BUA
      final SipServletResponse resp2B2BUA = (SipServletResponse) B2BUA2callee.getAttribute("RESP_INV");

      // Create the ACK to the callee
      final SipServletRequest ack2callee = resp2B2BUA.createAck();
      copyContent(req, ack2callee);
      ack2callee.send();
    }
    // Relay other requests on an existing session.
    else {
      // Retrieve the two call legs.
      final SipSession leg1 = req.getSession();
      final SipSession leg2 = (SipSession) leg1.getAttribute("PEER");

      // Create the request to be relayed.
      final SipServletRequest req2 = leg2.createRequest(req.getMethod());
      copyContent(req, req2);

      // Keep the reference to the orginal request so we can relay the response
      // back.
      req2.setAttribute("PEER_REQ", req);
      req2.send();
    }
  }

  /**
   * doResponse(SipServletRequest) forwards the responses.
   */
  @Override
  protected void doResponse(final SipServletResponse resp) throws ServletException, IOException {
    // 100 responses are handled by the container automatically.
    if (resp.getStatus() > 100) {
      if (resp.getStatus() == 401 || resp.getStatus() == 407) {
        // Demonstrates how to send a authorization header in sipservlet.
        final SipServletRequest req = resp.getSession().createRequest(resp.getMethod());
        try {
          req.addAuthHeader(resp, "user", "pwd");
        }
        catch (final Exception e) {
          e.printStackTrace();
        }
        copyContent(resp.getRequest(), req);
        req.send();
      }
      else {
        if ("INVITE".equals(resp.getMethod())) {
          // Keep the reference to the response so we can forward ACK properly
          // later.
          resp.getSession().setAttribute("RESP_INV", resp);
        }
        final SipServletRequest req1 = resp.getRequest();
        final SipServletRequest req2 = (SipServletRequest) req1.getAttribute("PEER_REQ");
        if (req2 != null) {
          final SipServletResponse resp2 = req2.createResponse(resp.getStatus(), resp.getReasonPhrase());
          copyContent(resp, resp2);
          resp2.send();
        }
      }
    }
  }

  /**
   * Copies the contents of source message to target message.
   */
  private void copyContent(final SipServletMessage source, final SipServletMessage target) throws IOException {
    target.setContent(source.getRawContent(), source.getContentType());
  }
}
