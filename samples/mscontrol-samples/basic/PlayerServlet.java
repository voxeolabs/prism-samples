/* ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 * Copyright (c) 2008-2009 Hewlett-Packard, Inc. All rights reserved.
 * Copyright (c) 2008-2009 Oracle and/or its affiliates. All rights reserved.
 *
 * Use is subject to license terms.
 * 
 * This code should only be used for further understanding of the
 * specifications and is not of production quality in terms of robustness,
 * scalability etc.
 * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 */
package basic;

import java.io.IOException;
import java.net.URI;

import javax.media.mscontrol.MediaConfigException;
import javax.media.mscontrol.MediaEventListener;
import javax.media.mscontrol.MediaSession;
import javax.media.mscontrol.MsControlFactory;
import javax.media.mscontrol.MsControlException;
import javax.media.mscontrol.Parameters;
import javax.media.mscontrol.join.Joinable.Direction;
import javax.media.mscontrol.mediagroup.MediaGroup;
import javax.media.mscontrol.mediagroup.PlayerEvent;
import javax.media.mscontrol.networkconnection.NetworkConnection;
import javax.media.mscontrol.networkconnection.SdpPortManagerEvent;
import javax.media.mscontrol.networkconnection.SdpPortManagerException;
import javax.media.mscontrol.networkconnection.SdpException;
import javax.media.mscontrol.resource.RTC;
import javax.media.mscontrol.spi.DriverManager;
import javax.servlet.ServletException;
import javax.servlet.sip.SipServlet;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipSession;

/**
 * This SipServlet is a very simple application that answers
 * incoming calls and plays a prompt to the caller.
 * <br>At the end of the play, this application sends BYE to terminate
 * the call.
 *
 * <p>This code illustrates the handling of offer/answer in INVITE
 * and re-INVITE transactions.
 */
public class PlayerServlet extends SipServlet
{
	private static final long serialVersionUID = 20080821L;

	// Each incoming call goes through the following states:
	public final static String WAITING_FOR_MEDIA_SERVER = "WAITING_FOR_MEDIA_SERVER";
	public final static String WAITING_FOR_ACK = "WAITING_FOR_ACK";
	public final static String WAITING_FOR_MEDIA_SERVER_2 = "WAITING_FOR_MEDIA_SERVER_2"; // (only if the initial INVITE had no SDP offer)
	public final static String DIALOG = "DIALOG";
	public final static String BYE_SENT = "BYE_SENT";

	// Reference the media session factory initialized from init()
    protected MsControlFactory msControlFactory;

	// Listener for SdpPortManager events
	private MyRtpPortsListener networkConnectionListener;
	
	// Listener for MediaGroup events
	protected MyPlayerListener playerListener;
	
	// The prompt to play
	protected URI prompt;

	@Override
	public void init() throws ServletException {
		super.init();
		try {
			// create the Media Session Factory
			msControlFactory = DriverManager.getDrivers().next().getFactory(null);
		} catch (Exception e) {
			throw new ServletException(e);
		}
		networkConnectionListener = new MyRtpPortsListener();
		playerListener = new MyPlayerListener();
		 // Could use a static property to set the prompt, or some signalling parameter (like annc in RFC 4240)
		prompt = URI.create("/prompts/welcome.wav");
	}

	@Override
    public void doInvite(final SipServletRequest req)
		throws ServletException,IOException
	{
		NetworkConnection conn = null;

		SipSession sipSession = req.getSession();
		if (req.isInitial()) {
			// New Call
			try {

				// Create new media session and store in SipSession
				MediaSession mediaSession = msControlFactory.createMediaSession();
				sipSession.setAttribute("MEDIA_SESSION", mediaSession);
				mediaSession.setAttribute("SIP_SESSION", sipSession);

				// Create a new NetworkConnection and store in SipSession
				conn = mediaSession.createNetworkConnection(NetworkConnection.BASIC);
				// Set this servlet class as listener of the RTP ports manager
				conn.getSdpPortManager().addListener(networkConnectionListener);
				sipSession.setAttribute("NETWORK_CONNECTION", conn);
			} catch (MediaConfigException e) {
				req.createResponse(SipServletResponse.SC_SERVER_INTERNAL_ERROR).send();
				return;
			} catch (MsControlException e) {
				// Probably out of resources, or other media server problem.  send 503
				req.createResponse(SipServletResponse.SC_SERVICE_UNAVAILABLE).send();
				return;
			}
		} else {
			// Existing call.  This is an re-INVITE
			// Get NetworkConnection from SipSession
			conn = (NetworkConnection) sipSession.getAttribute("NETWORK_CONNECTION");
		}
		
		// set SDP of peer UA to NetworkConnection ()
		try {
			// Store INVITE so it can be responded to later
			sipSession.setAttribute("UNANSWERED_INVITE", req);

			// set state
			setState(sipSession, WAITING_FOR_MEDIA_SERVER);
			
			// assume here that the only possible body is an SDP
			// may be null, indicating an INVITE w/o SDP
			byte[] sdpOffer = req.getRawContent();
			if (sdpOffer == null)
				conn.getSdpPortManager().generateSdpOffer();
			else
				conn.getSdpPortManager().processSdpOffer(sdpOffer);
			
		} catch (SdpException e) {
			req.createResponse(SipServletResponse.SC_NOT_ACCEPTABLE_HERE).send();
			return;
		} catch (SdpPortManagerException e) {
			// Unknown exception, just send 503
			req.createResponse(SipServletResponse.SC_SERVICE_UNAVAILABLE).send();
			return;
		} catch (MsControlException e) {
			// Unknown exception, just send 503
			req.createResponse(SipServletResponse.SC_SERVICE_UNAVAILABLE).send();
			return;
		}
	}

	@Override
	protected void doAck(SipServletRequest req)
		throws ServletException, IOException
	{
		SipSession sipSession = req.getSession();
		// Get NetworkConnection from SipSession
		NetworkConnection conn = (NetworkConnection) sipSession.getAttribute("NETWORK_CONNECTION");

		// Check if ACK contains an SDP (assume here that the only possible body is an SDP)
		byte[] remoteSdp = req.getRawContent();
		if (remoteSdp != null) {
			// ACK contains an SDP, should be an answer.
			// set SDP of peer UA.
			try {
				// set state
				setState(sipSession, WAITING_FOR_MEDIA_SERVER_2);
	
				conn.getSdpPortManager().processSdpAnswer(	// The local (media server) side has already been set
						remoteSdp);	// remote side is the peer UA
			} catch (Exception e) {
				// Not much to do.  Hope for the best and carry on.
				log(e.toString());
			}
		}

		if (compareState(sipSession, WAITING_FOR_ACK)) {
			// Play file now
			runDialog(sipSession);
		} 
	}

	@Override
	protected void doCancel(SipServletRequest req) throws ServletException,
			IOException {
		MediaSession mediaSession = (MediaSession) req.getSession().getAttribute("MEDIA_SESSION");
		mediaSession.release();
		req.getApplicationSession().invalidate();
	}

	@Override
    public void doBye(final SipServletRequest req)
		throws ServletException,IOException
	{
		MediaSession mediaSession = (MediaSession) req.getSession().getAttribute("MEDIA_SESSION");
		mediaSession.release();
		req.createResponse(SipServletResponse.SC_OK).send();
		req.getApplicationSession().invalidate();
	}

	private boolean compareState(SipSession sipSession, String state)
	{
		return state.equals((String) sipSession.getAttribute("STATE"));
	}

	protected void setState(SipSession sipSession, String state)
	{
		sipSession.setAttribute("STATE", state);
	}

	private class MyRtpPortsListener implements MediaEventListener<SdpPortManagerEvent>
	{
		public void onEvent(SdpPortManagerEvent event)
		{
			MediaSession mediaSession = event.getSource().getMediaSession();

			SipSession sipSession = (SipSession) mediaSession.getAttribute("SIP_SESSION");

			SipServletRequest inv = (SipServletRequest) sipSession.getAttribute("UNANSWERED_INVITE");
			sipSession.removeAttribute("UNANSWERED_INVITE");

			try {
				if (event.isSuccessful()) {
					if (compareState(sipSession, WAITING_FOR_MEDIA_SERVER)) {
						// Return an SDP attached to a 200 OK message
						SipServletResponse resp = inv.createResponse(SipServletResponse.SC_OK);
						// Get SDP from NetworkConnection
						byte[] sdp = event.getMediaServerSdp();
						resp.setContent(sdp, "application/sdp");
						// Send 200 OK
						resp.send();
						setState(sipSession, WAITING_FOR_ACK);
					} else if (compareState(sipSession, WAITING_FOR_MEDIA_SERVER_2)) {
						// The media server has updated the remote SDP received with the ACK.
						// The INVITE is complete, we are ready to play.
						runDialog(sipSession);
					}
				} else {
					if (SdpPortManagerEvent.SDP_NOT_ACCEPTABLE.equals(event.getError())) {
						// Send 488 error response to INVITE
						inv.createResponse(SipServletResponse.SC_NOT_ACCEPTABLE_HERE).send();
					} else if (SdpPortManagerEvent.RESOURCE_UNAVAILABLE.equals(event.getError())) {
						// Send 486 error response to INVITE
						inv.createResponse(SipServletResponse.SC_BUSY_HERE).send();
					} else {
						// Some unknown error. Send 500 error response to INVITE
						inv.createResponse(SipServletResponse.SC_SERVER_INTERNAL_ERROR).send();
					}
					// Clean up media session
					sipSession.removeAttribute("MEDIA_SESSION");
					mediaSession.release();
				} 
			} catch (Exception e) {
				e.printStackTrace();
				// Clean up
				sipSession.getApplicationSession().invalidate();
				mediaSession.release();
			}
		}
	}

	/**
	 * Play the prompt to the remote user agent.
	 * <br>Create and join a MediaGroup (on the first call only).
	 * @param sipSession
	 */
	protected void runDialog(SipSession sipSession) {
		
		try {
			MediaGroup mg = null;
			mg = (MediaGroup)sipSession.getAttribute("MEDIAGROUP");
			if (mg == null) {
				// Create a MediaGroup
				MediaSession ms = (MediaSession)sipSession.getAttribute("MEDIA_SESSION");
				mg = ms.createMediaGroup(MediaGroup.PLAYER);
				// Save reference for future use
				sipSession.setAttribute("MEDIAGROUP", mg);
				// Attach a listener to the Player
				mg.getPlayer().addListener(playerListener);
				// Join it to the NetworkConnection
				mg.join(Direction.DUPLEX, (NetworkConnection)sipSession.getAttribute("NETWORK_CONNECTION"));
			}
			// Play prompt
			mg.getPlayer().play(prompt, RTC.NO_RTC, Parameters.NO_PARAMETER);
			setState(sipSession, DIALOG);
		} catch (Exception e) {
			// Clean up media session
			MediaSession mediaSession = (MediaSession)sipSession.getAttribute("MEDIA_SESSION");
			terminate(sipSession, mediaSession);
			return;
		}
	}

	/**
	 * The <code>onEvent</code> method will be called by the Player to notify us when the play terminates.
	 */
	class MyPlayerListener implements MediaEventListener<PlayerEvent> {

		public void onEvent(PlayerEvent event) {
			log("Play terminated with: "+event);
			// Release the call and terminate
			MediaSession mediaSession = event.getSource().getMediaSession();
			SipSession sipSession = (SipSession) mediaSession.getAttribute("SIP_SESSION");
			terminate(sipSession, mediaSession);
		}
	}
	
	protected void terminate(SipSession sipSession, MediaSession mediaSession) {
		SipServletRequest bye = sipSession.createRequest("BYE");
		try {
			bye.send();
			// Clean up media session
			mediaSession.release();
			sipSession.removeAttribute("MEDIA_SESSION");
			setState(sipSession, BYE_SENT);
		} catch (Exception e1) {
			log("Terminating: Cannot send BYE: "+e1);
		}		
	}
}
