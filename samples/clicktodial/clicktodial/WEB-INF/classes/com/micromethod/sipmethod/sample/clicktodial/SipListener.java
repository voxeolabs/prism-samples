package com.micromethod.sipmethod.sample.clicktodial;

import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;

/**
 * @author Liu Zhiyu
 */
public interface SipListener {
  
  void doResponse(SipServletResponse resp);
  
  void doRequest(SipServletRequest req);

}
