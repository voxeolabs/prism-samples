package com.voxeo.prism.sample.multiparty;

import java.io.IOException;

import javax.annotation.Resource;
import javax.media.mscontrol.MsControlException;
import javax.media.mscontrol.MsControlFactory;
import javax.servlet.ServletException;
import javax.servlet.sip.SipServlet;
import javax.servlet.sip.SipServletMessage;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipSession;

import org.apache.log4j.Logger;

@SuppressWarnings("serial")
public class ConferenceServlet extends SipServlet {

  private static final Logger log = Logger.getLogger(ConferenceServlet.class);

  @Resource
  protected MsControlFactory _msFactory;

  /**
   * If SIP INVITE is an initial INVITE (i.e. a new phone call), create a new
   * MediaSession and setup all the media objects. Then start the SDP
   * answer/offer negotiation using SdpPortManager. If the initial INVITE
   * contains an SDP offer, ask SdpPortManager to process the offer. Otherwise,
   * ask SdpPortManager to generate an offer.
   * 
   * @see javax.servlet.sip.SipServlet#doInvite(javax.servlet.sip.SipServletRequest)
   */
  @Override
  protected void doInvite(SipServletRequest req) throws ServletException, IOException {
    // TODO 1: handle re-Invite
    if (req.isInitial()) {
      try {
        Participant participant = new Participant(req, _msFactory);
        participant.negotiateSDP(req.getRawContent());
      }
      catch (MsControlException e) {
        throw new ServletException(e);
      }
    }
  }

  /**
   * if SIP ACK contains SDP, send to SdpPortManager for final processing.
   * otherwise, start playing "Hello World!"
   * 
   * @see javax.servlet.sip.SipServlet#doAck(javax.servlet.sip.SipServletRequest)
   */
  @Override
  protected void doAck(final SipServletRequest req) throws ServletException, IOException {
    Participant participant = getParticipant(req);
    if (participant != null) {
      final byte[] remoteSdp = req.getRawContent();
      try {
        participant.processSDP(remoteSdp);
      }
      catch (MsControlException e) {
        participant.hangup();
      }
    }
  }

  /*
   * The caller tries to cancel the call before it is answered.
   * @see
   * javax.servlet.sip.SipServlet#doCancel(javax.servlet.sip.SipServletRequest)
   */
  @Override
  protected void doCancel(final SipServletRequest req) throws ServletException, IOException {
    Participant participant = getParticipant(req);
    if (participant != null) {
      try {
        participant.unjoin();
      }
      catch (MsControlException e) {
        // ignore
      }
    }

  }

  /*
   * the client wants to hang up the call. So be it.
   * @see
   * javax.servlet.sip.SipServlet#doBye(javax.servlet.sip.SipServletRequest)
   */
  @Override
  protected void doBye(final SipServletRequest req) throws ServletException, IOException {
    req.createResponse(SipServletResponse.SC_OK).send();
    Participant participant = getParticipant(req);
    if (participant != null) {
      try {
        participant.unjoin();
      }
      catch (MsControlException e) {
        throw new ServletException(e);
      }
    }
  }

  /*
   * the client answers the re-INVITE, so go on with the remaining
   * UNSOLICITED_OFFER_GENERATED event processing.
   * @see javax.servlet.sip.SipServlet#doSuccessResponse(javax.servlet.sip.
   * SipServletResponse)
   */
  @Override
  protected void doSuccessResponse(SipServletResponse resp) throws ServletException, IOException {
    if ("INVITE".equalsIgnoreCase(resp.getMethod()) && resp.getRawContent() != null) {
      Participant participant = getParticipant(resp);
      if (participant != null) {
        try {
          participant.answerSDP(resp);
        }
        catch (MsControlException e) {
          log.error(e.getMessage(), e);
          participant.hangup();
        }
      }
    }
  }

  Participant getParticipant(SipServletMessage msg) {
    return getParticipant(msg.getSession());
  }

  Participant getParticipant(SipSession session) {
    return (Participant) session.getAttribute(Participant.class.getName());
  }

}
