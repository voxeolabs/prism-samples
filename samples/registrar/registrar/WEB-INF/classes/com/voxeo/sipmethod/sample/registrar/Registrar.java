package com.voxeo.sipmethod.sample.registrar;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.sip.Address;
import javax.servlet.sip.SipFactory;
import javax.servlet.sip.SipServlet;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.URI;

/**
 * provide register service, other sample can query register info from this
 * sample.
 */
public class Registrar extends SipServlet {

  private static final long serialVersionUID = 3324393418036265483L;

  SipFactory _sipFactory;

  /**
   * init() initializes the servelt.
   */
  @Override
  public void init() throws ServletException {
    super.init();
    // Create addresses map and put it in context attribute.
    final ServletContext context = getServletContext();
    final Map<String, URI> addresses = new ConcurrentHashMap<String, URI>();
    context.setAttribute("com.micromethod.sipmethod.sample.register.Addresses", addresses);
    _sipFactory = (SipFactory) getServletContext().getAttribute(SIP_FACTORY);
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
        .getAttribute("com.micromethod.sipmethod.sample.register.Addresses");

    final String aor = req.getFrom().getURI().toString();

    int expire = req.getExpires();
    if (expire == -1) {
      expire = req.getAddressHeader("Contact").getExpires();
    }

    // The non-zero value of Expires header indicates a sign-in.
    if (expire > 0) {
      // Keep the name/address mapping.
      addresses.put(aor, req.getAddressHeader("Contact").getURI());

      // reset addresses map in context attribute for replication.
      context.setAttribute("com.micromethod.sipmethod.sample.register.Addresses", addresses);

      // We accept the sign-in by returning 200 OK response.
      SipServletResponse resp = req.createResponse(SipServletResponse.SC_OK);
      Address binding = _sipFactory.createAddress(req.getAddressHeader("Contact").getURI());
      binding.setExpires(300);
      resp.addAddressHeader("Contact", binding, true);
      resp.send();
    }
    else {

      // The zero value of Expires header indicates a sign-off.
      // Remove the name/address mapping.
      addresses.remove(aor);

      // reset addresses map in context attribute for replication.
      context.setAttribute("com.micromethod.sipmethod.sample.register.Addresses", addresses);

      req.createResponse(SipServletResponse.SC_OK).send();
    }
  }
}
