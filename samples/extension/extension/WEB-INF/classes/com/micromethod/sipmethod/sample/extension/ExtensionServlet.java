package com.micromethod.sipmethod.sample.extension;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipFactory;
import javax.servlet.sip.SipServlet;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipURI;
import javax.servlet.sip.URI;

import com.micromethod.sipmethod.server.ModuleConstants;
import com.micromethod.sipmethod.server.container.ContainerMBean;
import com.micromethod.sipmethod.server.container.SIPMethodSipSessionsUtil;

/**
 * This application demonstrate how to use ServerAPI to create user
 * application.<br>
 * com.micromethod.sipmethod.server.container.ContainerMBean and
 * com.micromethod.sipmethod.server.container.SIPMethodSipSessionsUtil are used
 * by this sample to show how to get server information with X-Lite.
 */

public class ExtensionServlet extends SipServlet {

  private static final long serialVersionUID = 1234234667836265483L;

  // com.micromethod.sipmethod.server.container.SIPMethodSipSessionsUtil
  protected SIPMethodSipSessionsUtil _sipSessionsUtil = null;
  protected SipFactory _factory = null;

  // use Resource Annotation to get
  // com.micromethod.sipmethod.server.container.ContainerMBean
  @Resource(name = ModuleConstants.SNAME_CONTAINER)
  protected ContainerMBean _container;

  protected List<SipApplicationSession> sessions = null;
  private static String NEW_LINE = System.getProperty("line.separator");
  private static String ADDRESS_ATTRIBUTE = "com.micromethod.sample.extensionServlet.Addresses";
  private static String HELP_MESSAGE = "Usage:" + NEW_LINE + NEW_LINE
      + "Type <font color=#b8110d size=3>applications</font> to list all applications that the server has." + NEW_LINE
      + "Type <font color=#b8110d size=3>all</font> to get the size of the ApplicationSessions that the server has."
      + "Type application name(e.g. extension) to get the size of the ApplicationSessions belong to the application."
      + NEW_LINE + "Type <font color=#b8110d size=3>state</font> to get the server's state." + NEW_LINE
      + "Type 'help' to get the help about this sample.";

  /**
   * init() initializes the servelt.
   */
  @Override
  public void init() throws ServletException {
    super.init();
    _factory = (SipFactory) getServletContext().getAttribute(SIP_FACTORY);

    final ServletContext context = getServletContext();
    // retrieve SIPMethodSipSessionsUtil from Servlet Context
    _sipSessionsUtil = (SIPMethodSipSessionsUtil) context.getAttribute(SIP_SESSIONS_UTIL);

    final Map<String, URI> addresses = new HashMap<String, URI>();
    context.setAttribute(ADDRESS_ATTRIBUTE, addresses);
  }

  /**
   * Invoked for SIP REGISTER requests, which are sent by X-Lite for sign-in
   * and sign-off.
   */
  @SuppressWarnings("unchecked")
  @Override
  protected void doRegister(final SipServletRequest req) throws IOException, ServletException {
    final ServletContext context = getServletContext();
    final Map<String, URI> addresses = (Map<String, URI>) context.getAttribute(ADDRESS_ATTRIBUTE);

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
      context.setAttribute(ADDRESS_ATTRIBUTE, addresses);
    }
    else {
      // The zero value of Expires header indicates a sign-off.
      // Remove the name/address mapping.
      addresses.remove(aor);

      // reset addresses map in context attribute for replication.
      context.setAttribute(ADDRESS_ATTRIBUTE, addresses);
    }
    req.createResponse(SipServletResponse.SC_OK).send();
    req.getApplicationSession().setExpires(1);
  }

  /**
   * Invoked for SIP MESSAGE requests, which are sent by X-Lite for instant
   * messages.It will extract the "question" from the request,and return the
   * "answer";
   */
  @SuppressWarnings("unchecked")
  @Override
  protected void doMessage(final SipServletRequest req) throws IOException, ServletException {
    // get addresses map from context attribute.
    final ServletContext context = getServletContext();
    StringBuffer message = new StringBuffer();
    final Map<String, URI> addresses = (Map<String, URI>) context.getAttribute(ADDRESS_ATTRIBUTE);
    // Create an extension SIP MESSAGE request.
    final SipServletRequest answer = req.getSession().createRequest("MESSAGE");
    SipURI uri = (SipURI) addresses.get(req.getFrom().getURI().toString().toLowerCase());
    // Can not found the uri from the address list.
    if (uri == null) {
      answer.setContent("You haven't registered", "text/plain");
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
      answer.setRequestURI(uri);
      answer.send();
      return;
    }
    // We accept the instant message by returning 200 OK response.
    req.createResponse(SipServletResponse.SC_OK).send();
    String question = getContent(req);

    final String charset = req.getCharacterEncoding();
    if (charset != null) {
      answer.setCharacterEncoding(charset);
    }
    answer.setRequestURI(uri);
    // Can not find the question ,return the help message.
    if (question == null) {
      message.append(help());
    }
    // Get the SipApplicationSessions number for each application running on
    // the server.
    else if (_container.getApplication(question) != null) {
      message.append("");
      for (SipApplicationSession session : _sipSessionsUtil.getApplicationSessionsByApplicationName(question)) {
        log(question + ":" + session.toString());
      }
      message.append("Application " + question + " has "
          + _sipSessionsUtil.getApplicationSessionsByApplicationName(question).size() + " SipApplicationSessions");
    }
    // Get the total number of SipApplicationSessions currently on the server.
    else if (question.equalsIgnoreCase("all")) {
      message.append("");
      _sipSessionsUtil.getAllApplicationSessions();
      for (SipApplicationSession session : _sipSessionsUtil.getApplicationSessionsByApplicationName(question)) {
        log("server :" + session.toString());
      }
      message.append("Server has " + _sipSessionsUtil.getAllApplicationSessions().size() + " SipApplicationSessions");
    }
    // Get all the applications name running on the server.
    else if (question.equalsIgnoreCase("applications")) {
      int size = _container.getApplicationNames().size();
      message.append("Server has " + size + " applications:\n  ");
      for (String app : _container.getApplicationNames()) {
        message.append(app);
        message.append(",  ");
      }
    }
    // Get the server state.
    else if (question.equalsIgnoreCase("state")) {
      message.append("Server is " + _container.getState() + " .\n");
    }
    // Not match the question ,return the help message.
    else {
      message.append(help());
    }
    answer.setContent(message.toString(), req.getContentType());
    answer.send();
  }

  /**
   * Return the help message.
   * 
   * @return help message
   */
  private String help() {
    return HELP_MESSAGE;
  }

  /**
   * Extract the request message from the request. The message from X-Lite is
   * like:"<front>message</front>"
   * 
   * @param req
   * @return the content
   */
  protected String getContent(SipServletRequest req) {
    String result = null;
    String question = null;
    String content = null;

    try {
      content = new String(req.getRawContent());
      int begin = content.indexOf("<font");
      int end = content.indexOf("</font");
      if (begin >= 0 && end > 0 && begin <= end) {
        result = content.substring(begin, end);
        // extract the message.
        question = result.substring(result.indexOf(">") + 1);
      }
    }
    catch (Exception e) {
      log(e.getMessage(), e);
    }
    return question;
  }

  @Override
  protected void doInfo(final SipServletRequest req) throws ServletException, IOException {
    req.createResponse(SipServletResponse.SC_OK).send();
  }

  @Override
  protected void doSuccessResponse(final SipServletResponse resp) throws IOException, ServletException {
    if (resp.getMethod().equalsIgnoreCase("MESSAGE")) {
      log("\"" + resp.getRequest().getContent() + "\" was accepted: " + resp.getStatus());
    }
  }

  @Override
  protected void doErrorResponse(final SipServletResponse resp) throws IOException, ServletException {
    if (resp.getMethod().equalsIgnoreCase("MESSAGE")) {
      log("\"" + resp.getRequest().getContent() + "\" was rejected: " + resp.getStatus());
    }
  }

  @Override
  protected void doBye(final SipServletRequest req) throws IOException, ServletException {
    req.createResponse(SipServletResponse.SC_OK).send();
  }
}
