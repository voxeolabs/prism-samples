package com.micromethod.sipmethod.sample.userjoin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.sip.SipFactory;
import javax.servlet.sip.SipServlet;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipSession;
import javax.servlet.sip.SipURI;

/**
 * UserJoinServlet provides a simple example of a SIP servlet.<br/>
 * 3-way Conference (Third Party Joins) According to the SIP Service Example
 * from http://www.tech-invite.com.
 */
public class UserJoinServlet extends SipServlet {

  private static final long serialVersionUID = -8776306340340837021L;

  String _queryRegInfoURL = "http://localhost:8080/registrar/query?fromOtherSample=true";

  SipFactory _factory;

  @Override
  public void init() throws ServletException {
    if (getInitParameter("QueryRegInfoURL") != null) {
      _queryRegInfoURL = getInitParameter("QueryRegInfoURL");
    }

    _factory = (SipFactory) getServletContext().getAttribute(SipServlet.SIP_FACTORY);
  }

  /**
   * Invoked for SIP INVITE requests.
   */
  @Override
  protected void doInvite(SipServletRequest req) throws ServletException, IOException {

    // get caller address from addresses map.
    SipURI uriCaller = null;
    uriCaller = (SipURI) _factory.createURI(getAddresses().get(req.getFrom().getURI().toString()));
    if (uriCaller == null) {
      // reject the request if it is not from a registered user.
      req.createResponse(SipServletResponse.SC_FORBIDDEN).send();
      return;
    }

    // get callee address from addresses map.
    SipURI uriCallee = null;
    uriCallee = (SipURI) (SipURI) _factory.createURI(getAddresses().get(req.getTo().getURI().toString()));
    if (uriCallee == null) {
      // Reject the message if the callee has not registered.
      req.createResponse(SipServletResponse.SC_TEMPORARLY_UNAVAILABLE).send();
      return;
    }

    if (req.isInitial()) {// if it is initial INVITE.
      // Relay the request from the caller to the callee.
      // create new request.
      SipServletRequest bReq = req.getB2buaHelper().createRequest(req, true, null);
      bReq.setRequestURI(uriCallee);
      bReq.setContent(req.getRawContent(), req.getContentType());
      bReq.send();
    }
    else {// if it is re-INVITE.
      // Relay the request from the caller to the callee.
      // get the peer session.
      SipSession peerSession = req.getB2buaHelper().getLinkedSession(req.getSession());
      // create request from session.
      SipServletRequest bReq = req.getB2buaHelper().createRequest(peerSession, req, null);
      bReq.setRequestURI(uriCallee);
      bReq.setContent(req.getRawContent(), req.getContentType());
      bReq.send();
    }
  }

  /**
   * Invoked for SIP 1xx class responses.
   */
  @Override
  protected void doProvisionalResponse(SipServletResponse resp) throws ServletException, IOException {

    // relay the provision response to the peer session.
    // get the peer request.
    SipServletRequest aReq = resp.getRequest().getB2buaHelper().getLinkedSipServletRequest(resp.getRequest());
    // create response and send .
    if (aReq != null) {
      SipServletResponse aResp = aReq.createResponse(resp.getStatus(), resp.getReasonPhrase());
      aResp.setContent(resp.getRawContent(), resp.getContentType());
      aResp.send();
    }
  }

  /**
   * Invoked for SIP 4xx-6xx class responses.
   */
  @Override
  protected void doErrorResponse(SipServletResponse resp) throws ServletException, IOException {

    // if the response is for INVITE request.
    if (resp.getMethod().equals("INVITE")) {
      // Keep the reference to the response so we can forward ACK properly
      // later.
      resp.getSession().setAttribute("RESP_INV", resp);
    }
    // relay the response to the peer session.
    // get the peer request.
    SipServletRequest aReq = resp.getRequest().getB2buaHelper().getLinkedSipServletRequest(resp.getRequest());
    // create response and send .
    if (aReq != null) {
      SipServletResponse aResp = aReq.createResponse(resp.getStatus(), resp.getReasonPhrase());
      aResp.setContent(resp.getRawContent(), resp.getContentType());
      aResp.send();
    }
  }

  /**
   * Invoked for SIP 2xx class responses.
   */
  @Override
  protected void doSuccessResponse(SipServletResponse resp) throws ServletException, IOException {

    // if the response is for INVITE request.
    if (resp.getMethod().equals("INVITE")) {
      // Keep the reference to the response so we can forward ACK properly
      // later.
      resp.getSession().setAttribute("RESP_INV", resp);
    }
    // relay the response to the peer session.
    // get the request.
    SipServletRequest aReq = resp.getRequest().getB2buaHelper().getLinkedSipServletRequest(resp.getRequest());
    // create response and send .
    if (aReq != null) {
      SipServletResponse aResp = aReq.createResponse(resp.getStatus(), resp.getReasonPhrase());
      aResp.setContent(resp.getRawContent(), resp.getContentType());
      aResp.send();
    }

  }

  /**
   * Invoked for SIP ACK requests.
   */
  @Override
  protected void doAck(SipServletRequest req) throws ServletException, IOException {

    // get the peer session.
    SipSession session = req.getB2buaHelper().getLinkedSession(req.getSession());
    // get the invite response from session.
    SipServletResponse bResp = (SipServletResponse) session.getAttribute("RESP_INV");
    // create ACK from the response and send.
    SipServletRequest bReq = bResp.createAck();
    bReq.setContent(req.getRawContent(), req.getContentType());
    bReq.send();
  }

  /**
   * Invoked for SIP BYE requests.
   */
  @Override
  protected void doBye(SipServletRequest req) throws ServletException, IOException {

    // Relay the BYE request from the caller to the callee.
    SipServletRequest bReq = req.getB2buaHelper().createRequest(
        req.getB2buaHelper().getLinkedSession(req.getSession()), req, null);
    bReq.setContent(req.getRawContent(), req.getContentType());
    bReq.send();

  }

  private Map<String, String> getAddresses() throws IOException {
    Map<String, String> regInfo = new HashMap<String, String>();

    String result = getRegInfo(_queryRegInfoURL);

    if (result != null && result.length() > 0) {
      String[] s = result.split("\r\n");

      for (String t : s) {
        String[] a = t.split(" ");
        if (a.length == 2) {
          regInfo.put(a[0], a[1]);
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