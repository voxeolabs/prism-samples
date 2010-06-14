package com.micromethod.sipmethod.sample.vxml;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.sip.Address;
import javax.servlet.sip.B2buaHelper;
import javax.servlet.sip.SipFactory;
import javax.servlet.sip.SipServlet;
import javax.servlet.sip.SipServletMessage;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipSession;
import javax.servlet.sip.SipURI;

public class AnnouncerServlet extends SipServlet {

  private static final long serialVersionUID = 8253289236737492930L;

  protected SipFactory _factory = null;

  protected String _host = "127.0.0.1";

  protected int _port = 5066;

  protected String _transport = "udp";

  protected String _vxml = null;

  protected String _ext = ".xml";

  @Override
  public void init() throws ServletException {
    super.init();
    // Get a reference to the SipFactory.
    _factory = (SipFactory) getServletContext().getAttribute(SIP_FACTORY);

    // Get the host of media server.
    final String host = getInitParameter("mediaserver-host");
    if (host != null) {
      _host = host;
    }

    // Get the port of media server.
    final String port = getInitParameter("mediaserver-port");
    if (port != null) {
      _port = Integer.parseInt(port);
    }

    // Get the transport type of media server.
    final String transport = getInitParameter("mediaserver-transport");
    if (transport != null) {
      _transport = transport;
    }

    // Get the URL of voice xml host server.
    String vxml = getInitParameter("vxml");
    if (vxml != null) {
      if (!vxml.endsWith("/")) {
        vxml = vxml + "/";
      }
      _vxml = vxml;
    }
    else {
      throw new IllegalArgumentException("no vxml host server");
    }

    String ext = getInitParameter("ext");
    if (ext != null) {
      if (!ext.startsWith(".")) {
        ext = "." + ext;
      }
      if (".*".equals(ext)) {
        ext = "";
      }
      _ext = ext;
    }
  }

  @Override
  protected void doRequest(final SipServletRequest req) throws ServletException, IOException {
    if (req.isInitial()) {
      if ("INVITE".equalsIgnoreCase(req.getMethod())) {
        req.createResponse(SipServletResponse.SC_TRYING).send();
        // Get a reference to the B2buaHelper.
        final B2buaHelper helper = req.getB2buaHelper();

        // Create the request-to-MS.
        // B2buaHelper automatically associate two SIP sessions with each other
        // and two SIP Requests with each other.
        final SipURI uri = generateRequestURI(_transport, _host, _port, _vxml, ((SipURI) req.getTo().getURI())
            .getUser(), _ext);
        final Address toHeader = _factory.createAddress(uri);
        final List<String> toHeaders = new ArrayList<String>(1);
        toHeaders.add(toHeader.toString());
        final Map<String, List<String>> headers = new HashMap<String, List<String>>(1);
        headers.put("To", toHeaders);
        final SipServletRequest req2MS = helper.createRequest(req, true, headers);
        req2MS.setRequestURI(uri);
        copyContent(req, req2MS);

        // Forward the request-to-MS to MS.
        req2MS.send();
      }
      else {
        req.createResponse(SipServletResponse.SC_OK).send();
      }
    }
    // Relay the ACK from caller to the MS.
    else if (req.getMethod().equalsIgnoreCase("ACK")) {
      // Retrieve the two call legs by B2buaHelper.
      final SipSession leg1 = req.getSession();
      final SipSession leg2 = req.getB2buaHelper().getLinkedSession(leg1);
      // Retrieve the previous response from the MS to the B2BUA
      final SipServletResponse res4MS = (SipServletResponse) leg2.getAttribute("RESP_INV");
      // Create the ACK to the MS
      final SipServletRequest ack2MS = res4MS.createAck();
      copyContent(req, ack2MS);
      ack2MS.send();
    }
    // Relay the CANCEL from caller to the MS.
    else if (req.getMethod().equalsIgnoreCase("CANCEL")) {
      final SipSession leg1 = req.getSession();
      final SipSession leg2 = req.getB2buaHelper().getLinkedSession(leg1);
      final SipServletRequest req2 = req.getB2buaHelper().createCancel(leg2);
      copyContent(req, req2);
      req2.send();
    }
    // Relay other requests on an existing session.
    else {
      // Retrieve the two call legs by B2buaHelper.
      final SipSession leg1 = req.getSession();
      final SipSession leg2 = req.getB2buaHelper().getLinkedSession(leg1);
      // Create the request to be relayed and link sessions by B2buaHelper.
      final SipServletRequest req2 = req.getB2buaHelper().createRequest(leg2, req, null);
      copyContent(req, req2);
      req2.send();
    }
  }

  @Override
  protected void doResponse(final SipServletResponse res) throws ServletException, IOException {
    if ("INVITE".equalsIgnoreCase(res.getMethod())) {
      if (res.getStatus() >= 200 && res.getStatus() <= 299) {
        // Keep the reference to the response so we can forward ACK properly
        // later.
        res.getSession().setAttribute("RESP_INV", res);
      }
      else if (res.getStatus() == 487) {
        res.createAck().send();
        return;
      }
    }
    else if ("CANCEL".equalsIgnoreCase(res.getMethod())) {
      return;
    }
    final B2buaHelper helper = res.getRequest().getB2buaHelper();
    final SipServletRequest peerReq = helper.getLinkedSipServletRequest(res.getRequest());
    if (peerReq != null) {
      final SipServletResponse peerRes = peerReq.createResponse(res.getStatus(), res.getReasonPhrase());
      copyContent(res, peerRes);
      try {
        peerRes.send();
      }
      catch (final IllegalStateException ise) {
        log("Send reponse: " + peerRes, ise);
      }
    }
  }

  /**
   * Copies the contents of source message to target message.
   */
  private void copyContent(final SipServletMessage source, final SipServletMessage target) throws IOException {
    target.setContent(source.getRawContent(), source.getContentType());
  }

  /**
   * Compose MS's URI based on the voice xml URL.
   * sip:dialog.vxml.http%3a//127.0.0
   * .1%3a8080/vxml/hello.vxml%3fsession.accountid
   * %3d1%26session.id%3d1@127.0.0.1:5066;transport=udp
   */
  private SipURI generateRequestURI(final String transport, final String host, final int port, final String URL,
      final String name, final String ext) {
    final SipURI retval = _factory.createSipURI(
        "dialog.vxml." + URL + name + ext + "?session.accountid=1&session.id=1", host);
    retval.setPort(port);
    retval.setTransportParam(transport);
    return retval;
  }
}
