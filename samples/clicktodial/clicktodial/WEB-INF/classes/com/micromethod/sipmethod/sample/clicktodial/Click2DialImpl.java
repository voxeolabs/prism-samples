package com.micromethod.sipmethod.sample.clicktodial;

import javax.annotation.Resource;
import javax.jws.WebService;
import javax.servlet.ServletContext;

@WebService
public class Click2DialImpl {
  //container will inject application context
  @Resource
  private ServletContext m_context = null;

  public String makeCall(String user1, String user2) {
    if (user1 != null && user2 != null) {
      SipAgent agent = (SipAgent) m_context.getAttribute("SIP_AGENT");
      Call call = agent.makeCall(user1, user2);
      if (call == null) {
        return "The call between [" + user1 + "] and [" + user2 + "] is failed.";
      }
      else {
        boolean ret = agent.waitResultFor(call);
        if (ret) {
          return "The call between [" + user1 + "] and [" + user2 + "] has been established.";
        }
        else {
          return "The call between [" + user1 + "] and [" + user2 + "] is failed.";
        }
      }
    }
    return "Miss users.";
  }
}
