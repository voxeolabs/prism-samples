package com.micromethod.sipmethod.sample.clicktodial;

/**
 * @author Liu Zhiyu
 */
public interface SipAgent {
  
  Call makeCall(String user1, String user2);
  
  boolean waitResultFor(Call call);

}
