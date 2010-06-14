package com.micromethod.sipmethod.sample.ar;

import javax.servlet.sip.ar.SipApplicationRouter;
import javax.servlet.sip.ar.spi.SipApplicationRouterProvider;

public class SipForwardApplicationRouterProvider extends SipApplicationRouterProvider {

  private final SipApplicationRouter _router = new SipForwardApplicationRouter();

  public SipForwardApplicationRouterProvider() {
    super();
  }

  @Override
  public SipApplicationRouter getSipApplicationRouter() {
    return _router;
  }
}
