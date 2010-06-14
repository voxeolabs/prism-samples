package com.micromethod.sipmethod.sample.findme;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import javax.servlet.ServletException;
import javax.servlet.sip.Proxy;
import javax.servlet.sip.SipFactory;
import javax.servlet.sip.SipServlet;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.URI;

/**
 * This servlet demonstrates how to write a Follow-Me application using a SIP
 * servlet.
 * <p>
 * This servlet porxy INVITE message to remote targets specified by init-param
 * in sip.xml.
 * </p>
 * <p>
 * This servlet supports the "Find-Me" function or the "Single Line Extension"
 * function given the init-param in sip.xml.
 * </p>
 */
public class FindMeServlet extends SipServlet {

  private static final long serialVersionUID = -3457002477015589117L;

  /**
   * flag specifying whether to proxy to multiple destinations in parallel
   * (true) or sequentially (false). In the case of parallel search, the server
   * may proxy the request to multiple destinations without waiting for final
   * responses to previous requests. If it is true, meant proxy work under the
   * Find-Me mode, otherwise is the Follow-Me mode.
   */
  private boolean m_parallel;

  /**
   * destination set.
   */
  private final List<URI> m_targets = new ArrayList<URI>();

  /**
   * init() initializes the servelt with initialization parameters.
   */
  @Override
  public void init() throws ServletException {

    // Get init-param in sip.xml.
    m_parallel = !"true".equalsIgnoreCase(getInitParameter("findme"));

    // Get a reference to the SipFactory.
    final SipFactory sipFactory = (SipFactory) getServletContext().getAttribute(SIP_FACTORY);

    // Get destination set.
    final String c = getInitParameter("contacts");
    if (c == null) {
      throw new ServletException("No 'contacts' parameter");
    }

    // Parse destination set.
    final StringTokenizer tok = new StringTokenizer(c, ", \r\n\t");
    while (tok.hasMoreTokens()) {
      final URI uri = sipFactory.createURI(tok.nextToken());
      m_targets.add(uri);
    }
  }

  /**
   * Invoked for SIP REGISTER requests.
   */
  @Override
  protected void doRegister(final SipServletRequest req) throws ServletException, IOException {
    req.createResponse(SipServletResponse.SC_OK).send();
  }

  /**
   * Invoked for SIP INVITE requests.
   */
  @Override
  protected void doInvite(final SipServletRequest req) throws ServletException, IOException {
    if (req.isInitial()) {
      // Get a reference to the Proxy helper.
      final Proxy proxy = req.getProxy();

      // Set Proxy parameters to control various aspects of the proxying
      // operation:
      proxy.setRecordRoute(true);
      proxy.setSupervised(true);
      // Set the flag indicates the working mode. True for Follow-Me; false for
      // Find-me.

      proxy.setParallel(m_parallel);

      // Proxies a SIP request to the specified set of destinations.
      proxy.proxyTo(m_targets);
    }
  }

  /**
   * Invoked for SIP 1xx class responses.
   */
  @Override
  protected void doProvisionalResponse(final SipServletResponse resp) {
    log("doProvisionalResponse: " + getSummary(resp));
  }

  /**
   * Invoked for SIP 4xx-6xx class responses.
   */
  @Override
  protected void doErrorResponse(final SipServletResponse resp) {
    log("doErrorResponse: " + getSummary(resp));
  }

  /**
   * Invoked for SIP 2xx class responses.
   */
  @Override
  protected void doSuccessResponse(final SipServletResponse resp) throws IOException {
    log("doSuccessResponse: " + getSummary(resp));
  }

  /**
   * Returns one-line description of the specified response object.
   */
  private static String getSummary(final SipServletResponse resp) {
    return "" + resp.getStatus() + "/" + resp.getMethod();
  }

}
