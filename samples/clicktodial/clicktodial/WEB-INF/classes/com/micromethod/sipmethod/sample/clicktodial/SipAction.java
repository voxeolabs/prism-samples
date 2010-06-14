package com.micromethod.sipmethod.sample.clicktodial;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.sip.SipServlet;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;

public class SipAction extends SipServlet {

  private static final long serialVersionUID = 4091054518510819130L;

  @Override
  public void init() throws ServletException {
    super.init();
    getServletContext().setAttribute("SIP_AGENT", new SipAgentImpl(getServletContext()));
  }

  @Override
  protected void doRequest(final SipServletRequest req) throws IOException, ServletException {
    try {
      if (req.getMethod().equalsIgnoreCase("REGISTER") || req.getMethod().equalsIgnoreCase("BYE")) {
        final SipListener listener = (SipListener) getServletContext().getAttribute("SIP_LISTENER");
        listener.doRequest(req);
      }
      else {
        super.doRequest(req);
      }
    }
    catch (final Throwable t) {
      t.printStackTrace();
    }
  }

  @Override
  protected void doResponse(final SipServletResponse resp) throws IOException, ServletException {
    try {
      if (resp.getMethod().equalsIgnoreCase("INVITE") || resp.getMethod().equalsIgnoreCase("BYE")) {
        final SipListener listener = (SipListener) getServletContext().getAttribute("SIP_LISTENER");
        listener.doResponse(resp);
      }
      else {
        super.doResponse(resp);
      }
    }
    catch (final Throwable t) {
      t.printStackTrace();
    }
  }

}
