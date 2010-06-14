package com.micromethod.sipmethod.sample.clicktodial;

import javax.servlet.sip.SipServletResponse;

/**
 * @author Liu Zhiyu
 */
public class Call {
  
  public final static int CALLSTATE_INITIALIZED = 0;
  
  public final static int CALLSTATE_SUCCESSED = 1;

  public final static int CALLSTATE_FAILED = 2;
  
  private String m_user1 = null;
  
  private String m_user2 = null;
    
  private SipServletResponse m_response1 = null;
  
  private SipServletResponse m_response2 = null;
  
  private String m_dialogID1 = null;
  
  private String m_dialogID2 = null;
  
  private int m_state = 0;
  
  public Call(String user1, String user2) {
    m_user1 = user1;
    m_user2 = user2;
  }
  
  public synchronized int getState() {
    return m_state;
  }
  
  public synchronized void setState(int state) {
    m_state = state;
  }
  
  public synchronized String getUser1() {
    return m_user1;
  }
  
  public synchronized String getUser2() {
    return m_user2;
  }
  
  public synchronized void setResponseForUser1(SipServletResponse resp) {
    m_response1 = resp;
  }
  
  public synchronized void setResponseForUser2(SipServletResponse resp) {
    m_response2 = resp;
  }
  
  public synchronized SipServletResponse getResponseForUser1() {
    return m_response1;
  }
  
  public synchronized SipServletResponse getResponseForUser2() {
    return m_response2;
  }
  
  public synchronized void setDialogID1(String id) {
    m_dialogID1 = id;
  }
  
  public synchronized void setDialogID2(String id) {
    m_dialogID2 = id;
  }

  public synchronized String getDialog1() {
    return m_dialogID1;
  }
  
  public synchronized String getDialog2() {
    return m_dialogID2;
  }
  
}
