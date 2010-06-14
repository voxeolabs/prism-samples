package com.micromethod.sipmethod.sample.clicktodial;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipFactory;
import javax.servlet.sip.SipServletMessage;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.URI;

public class SipAgentImpl implements SipAgent, SipListener {

  private ServletContext m_sipContext = null;

  private SipFactory m_factory = null;

  private final Map<String, Call> m_calls = new HashMap<String, Call>();

  private final Map<String, URI> m_addresses = new HashMap<String, URI>();

  public SipAgentImpl(final ServletContext context) {
    m_sipContext = context;
    m_factory = (SipFactory) context.getAttribute("javax.servlet.sip.SipFactory");
    m_sipContext.setAttribute("SIP_LISTENER", this);
  }

  public Call makeCall(final String user1, final String user2) {
    System.out.println("Make call:" + user1 + ":" + user2);
    final URI uri1 = query(user1);
    final URI uri2 = query(user2);
    if (uri1 == null || uri2 == null) {
      // TODO
      System.out.println("query failed:" + uri1 + ", " + uri2);
      return null;
    }
    final SipApplicationSession appSession = m_factory.createApplicationSession();
    SipServletRequest invite1 = null;
    try {
      invite1 = m_factory.createRequest(appSession, "INVITE", user1, user2);
    }
    catch (final Exception e1) {
      // TODO
      System.out.println("Create INVITE(user1->user2) failed." + e1);
      return null;
    }
    SipServletRequest invite2 = null;
    try {
      invite2 = m_factory.createRequest(appSession, "INVITE", user2, user1);
    }
    catch (final Exception e2) {
      // TODO
      System.out.println("Create INVITE(user2->user1) failed." + e2);
      return null;
    }

    if (invite1 == null || invite2 == null) {
      // TODO
      System.out.println("Make call failed.");
      return null;
    }
    invite1.setRequestURI(uri2);
    invite2.setRequestURI(uri1);
    final Call call = new Call(user1, user2);
    try {
      invite1.getSession().setHandler("SipAction");
      invite2.getSession().setHandler("SipAction");
    }
    catch (IllegalStateException e) {
      e.printStackTrace();
    }
    catch (ServletException e) {
      e.printStackTrace();
    }

    call.setDialogID1(invite1.getSession().getId());
    call.setDialogID2(invite2.getSession().getId());
    addCall(call);
    try {
      invite1.send();
    }
    catch (final Exception e3) {
      // TODO
      System.out.println("Send INVITE(user1->user2) failed." + e3);
      removeCall(call);
      return null;
    }
    try {
      invite2.send();
    }
    catch (final Exception e4) {
      // TODO
      System.out.println("Send INVITE(user2->user1) failed." + e4);
      try {
        invite1.createCancel().send();
      }
      catch (final Exception e5) {
        System.out.println("Send Cancel(user1->user2) failed." + e5);
      }
      removeCall(call);
      return null;
    }
    return call;
  }

  public boolean waitResultFor(final Call call) {
    synchronized (call) {
      int i = 0;
      while (call.getState() == Call.CALLSTATE_INITIALIZED && i < 60) {
        i++;
        try {
          call.wait(1000);
        }
        catch (final InterruptedException e) {
          ;
        }
      }
    }
    if (call.getState() == Call.CALLSTATE_SUCCESSED) {
      return true;
    }
    else {
      return false;
    }
  }

  public void doResponse(final SipServletResponse resp) {
    if (resp.getMethod().equalsIgnoreCase("INVITE") && resp.getStatus() >= 200) {
      final String id = resp.getSession().getId();
      SipServletResponse coresp = null;
      final Call call = findCall(id);
      if (call == null) {
        // TODO
        System.out.println("Can't find call for: " + resp);
        return;
      }
      else {
        if (call.getDialog1().equals(id)) {
          call.setResponseForUser1(resp);
          coresp = call.getResponseForUser2();
        }
        else if (call.getDialog2().equals(id)) {
          call.setResponseForUser2(resp);
          coresp = call.getResponseForUser1();
        }
      }

      if (resp.getStatus() < 300) {
        if (coresp != null) {
          if (coresp.getStatus() < 300) {
            final SipServletRequest ack = resp.createAck();
            copyContent(coresp, ack);
            final SipServletRequest coack = coresp.createAck();
            copyContent(resp, coack);
            try {
              ack.send();
            }
            catch (final Exception e1) {
              // TODO
              System.out.println("Send ACK failed." + e1 + "\r\n" + ack);
              try {
                resp.getSession().createRequest("BYE").send();
              }
              catch (final Exception e) {
                System.out.println("Send BYE failed.");
              }
              try {
                coresp.getSession().createRequest("BYE").send();
              }
              catch (final Exception e) {
                System.out.println("Send BYE failed.");
              }
              call.setState(Call.CALLSTATE_FAILED);
              synchronized (call) {
                call.notifyAll();
              }
              removeCall(call);
              return;
            }
            try {
              coack.send();
            }
            catch (final Exception e2) {
              // TODO
              System.out.println("Send ACK failed." + e2 + "\r\n" + coack);
              try {
                resp.getSession().createRequest("BYE").send();
              }
              catch (final Exception e) {
                System.out.println("Send BYE failed.");
              }
              try {
                coresp.getSession().createRequest("BYE").send();
              }
              catch (final Exception e) {
                System.out.println("Send BYE failed.");
              }
              call.setState(Call.CALLSTATE_FAILED);
              synchronized (call) {
                call.notifyAll();
              }
              removeCall(call);
              return;
            }
            call.setState(Call.CALLSTATE_SUCCESSED);
            synchronized (call) {
              call.notifyAll();
            }
          }
          else if (coresp.getStatus() > 400) {
            try {
              resp.createAck().send();
            }
            catch (final Exception e) {
              System.out.println("Send ACK failed.");
            }
            try {
              resp.getSession().createRequest("BYE").send();
            }
            catch (final Exception e) {
              System.out.println("Send BYE failed.");
            }
            call.setState(Call.CALLSTATE_FAILED);
            synchronized (call) {
              call.notifyAll();
            }
            removeCall(call);
          }
        }
        else {
          ;
        }
      }
      else if (resp.getStatus() > 400) {
        if (coresp != null) {
          if (coresp.getStatus() < 300) {
            try {
              coresp.createAck().send();
            }
            catch (final Exception e) {
              System.out.println("Send ACK failed.");
            }
            try {
              coresp.getSession().createRequest("BYE").send();
            }
            catch (final Exception e) {
              System.out.println("Send BYE failed.");
            }
            call.setState(Call.CALLSTATE_FAILED);
            synchronized (call) {
              call.notifyAll();
            }
            removeCall(call);
          }
          else if (coresp.getStatus() > 400) {
            call.setState(Call.CALLSTATE_FAILED);
            synchronized (call) {
              call.notifyAll();
            }
            removeCall(call);
          }
        }
        else {

        }
      }
    }
  }

  public void doRequest(final SipServletRequest req) {
    if (req.getMethod().equalsIgnoreCase("REGISTER")) {
      try {
        final String aor = req.getFrom().getURI().toString().toLowerCase();
        synchronized (m_addresses) {
          if (req.getExpires() != 0) {
            m_addresses.put(aor, req.getAddressHeader("Contact").getURI());
          }
          else {
            m_addresses.remove(aor);
          }
        }
        req.createResponse(SipServletResponse.SC_OK).send();
      }
      catch (final Exception e) {
        e.printStackTrace();
      }
    }
    else if (req.getMethod().equalsIgnoreCase("BYE")) {
      try {
        req.createResponse(SipServletResponse.SC_OK).send();
      }
      catch (final Exception e) {
        e.printStackTrace();
      }
      final String id = req.getSession().getId();
      final Call call = findCall(id);
      if (call == null) {
        System.out.println("Can't find call for: " + req);
        return;
      }
      else {
        if (call.getDialog1().equals(id)) {
          try {
            call.getResponseForUser2().getSession().createRequest("BYE").send();
          }
          catch (final Exception e) {
            System.out.println("Send BYE failed.");
          }
          removeCall(call);
        }
        else if (call.getDialog2().equals(id)) {
          try {
            call.getResponseForUser1().getSession().createRequest("BYE").send();
          }
          catch (final Exception e) {
            System.out.println("Send BYE failed.");
          }
          removeCall(call);
        }
      }
    }
  }

  private URI query(final String aor) {
    URI ret = null;
    synchronized (m_addresses) {
      ret = m_addresses.get(aor.toLowerCase());
    }
    if (ret == null) {
      try {
        ret = m_factory.createURI(aor);
      }
      catch (final Exception e) {
        ret = null;
      }
    }
    return ret;
  }

  private void addCall(final String callID, final Call call) {
    synchronized (m_calls) {
      m_calls.put(callID, call);
    }
  }

  private void addCall(final Call call) {
    addCall(call.getDialog1(), call);
    addCall(call.getDialog2(), call);
  }

  private Call findCall(final String callID) {
    synchronized (m_calls) {
      return m_calls.get(callID);
    }
  }

  private void removeCall(final String callID) {
    synchronized (m_calls) {
      m_calls.remove(callID);
    }
  }

  private void removeCall(final Call call) {
    removeCall(call.getDialog1());
    removeCall(call.getDialog2());
  }

  private void copyContent(final SipServletMessage source, final SipServletMessage target) {
    try {
      target.setContent(source.getRawContent(), source.getContentType());
    }
    catch (final Exception e) {
      System.out.println("Copy content failed." + e);
    }
  }
}
