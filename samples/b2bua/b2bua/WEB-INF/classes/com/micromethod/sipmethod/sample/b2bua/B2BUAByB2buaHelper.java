package com.micromethod.sipmethod.sample.b2bua;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.sip.B2buaHelper;
import javax.servlet.sip.SipFactory;
import javax.servlet.sip.SipServlet;
import javax.servlet.sip.SipServletMessage;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipSession;
import javax.servlet.sip.SipURI;
import javax.servlet.sip.UAMode;

/**
 * This servlet demonstrates how to write a back-to-back user agent (B2BUA) by
 * B2buaHelper using a SIP servlet and how to handle authentication.
 * <p>
 * The RFC 3261 defines a B2BUA as a logical entity that receives an invitation,
 * and acts as a UAS to process it. In order to determine how the request should
 * be answered, it acts as a UAC and initiates a call outwards. Unlike a proxy
 * server, it maintains complete call state and must participate in all requests
 * for a call. Since it is purely a concatenation of other logical functions, no
 * explicit definitions are needed for its behavior.
 * <p>
 * The B2buaHelper is helper class providing support for B2BUA applications. It
 * associates two SIP sessions with each other and basically relays requests and
 * responses received on one leg to the other.
 * <p>
 * This servlet demonstrate how to use
 * SipServletRequest.addAuthHeader(SipServletResponse challengeResponse, String
 * username, String password) method to handle 401 or 407 response.
 */
public class B2BUAByB2buaHelper extends SipServlet {

  private static final long serialVersionUID = -7778305763580069659L;

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
      // Get a reference to the B2buaHelper.
      final B2buaHelper helper = req.getB2buaHelper();

      // Compose callee's URI based on the request URI
      final SipURI uri = (SipURI) req.getRequestURI();
      final String user = uri.getUser();
      final SipURI callee = _factory.createSipURI(user, _server);
      callee.setPort(_port);
      callee.setTransportParam(_transport);

      // Create the request-to-callee in the B2BUA-to-callee call leg.
      // B2buaHelper automatically associate two SIP sessions with each other
      // and
      // two SIP Requests with each other.
      final SipServletRequest req2callee = helper.createRequest(req, true, null);
      req2callee.setRequestURI(callee);
      copyContent(req, req2callee);

      // Forward the request-to-callee to callee.
      req2callee.send();
    }
    // Relay the ACK from caller to the callee.
    else if (req.getMethod().equalsIgnoreCase("ACK")) {
      doAck(req);
    }
    else if (req.getMethod().equalsIgnoreCase("CANCEL")) {
      doCancel(req);
    }
    // else if (req.getMethod().equalsIgnoreCase("REGISTER")) {
    // req.createResponse(500, "Request not handled by app").send();
    // }
    // Relay other requests on an existing session.
    else {
      // Retrieve the two call legs by B2buaHelper.
      final SipSession leg1 = req.getSession();
      final SipSession leg2 = req.getB2buaHelper().getLinkedSession(leg1);

      // Create the request to be relayed and link sessions by B2buaHelper. .
      final SipServletRequest req2 = req.getB2buaHelper().createRequest(leg2, req, null);
      copyContent(req, req2);
      req2.send();
    }
  }

  /**
   * doResponse(SipServletRequest) forwards the responses.
   */
  @Override
  protected void doResponse(final SipServletResponse resp) throws ServletException, IOException {
    if (resp.getStatus() == SipServletResponse.SC_REQUEST_TERMINATED) {
      return;
    }

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
        final B2buaHelper b2b = resp.getRequest().getB2buaHelper();
        final SipSession ls = b2b.getLinkedSession(resp.getSession());
        SipServletResponse cpyresp = null;
        // process the initial request
        if (resp.getRequest().isInitial()) {
          cpyresp = b2b.createResponseToOriginalRequest(ls, resp.getStatus(), resp.getReasonPhrase());
        }
        else {
          // process the subsequent request
          final SipServletRequest otherReq = b2b.getLinkedSipServletRequest(resp.getRequest());
          cpyresp = otherReq.createResponse(resp.getStatus(), resp.getReasonPhrase());
        }
        copyContent(resp, cpyresp);
        cpyresp.send();
      }
    }
  }

  /**
   * doAck(SipServletRequest),find out the response belong to the uncommitted
   * request,create the Ack and send out.
   */
  @Override
  protected void doAck(final SipServletRequest req) throws ServletException, IOException {
    final B2buaHelper b2b = req.getB2buaHelper();
    final SipSession ss = b2b.getLinkedSession(req.getSession());
    // Get the uncommitted messages from the session with UAC mode.
    final java.util.List<SipServletMessage> msgs = b2b.getPendingMessages(ss, UAMode.UAC);
    for (final SipServletMessage msg : msgs) {
      if (msg instanceof SipServletResponse) {
        final SipServletResponse resp = (SipServletResponse) msg;
        // send Ack for SUCCESS response
        if (resp.getStatus() == SipServletResponse.SC_OK) {
          final SipServletRequest ack = resp.createAck();
          copyContent(req, ack);
          ack.send();
        }
      }
    }
  }

  /**
   * doCancel(SipServletRequest),create the cancel with B2buaHelper,and send
   * out.
   */
  @Override
  protected void doCancel(final SipServletRequest req) throws ServletException, IOException {
    final B2buaHelper b2b = req.getB2buaHelper();
    final SipSession ss = b2b.getLinkedSession(req.getSession());
    final SipServletRequest cancel = b2b.createCancel(ss);
    cancel.send();
  }

  /**
   * Copies the contents of source message to target message.
   */
  private void copyContent(final SipServletMessage source, final SipServletMessage target) throws IOException {
    target.setContent(source.getRawContent(), source.getContentType());
  }
}
