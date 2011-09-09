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
package multiparty;

import java.io.IOException;

import javax.media.mscontrol.MediaEventListener;
import javax.media.mscontrol.MediaSession;
import javax.media.mscontrol.MsControlFactory;
import javax.media.mscontrol.join.Joinable;
import javax.media.mscontrol.mixer.MediaMixer;
import javax.media.mscontrol.networkconnection.NetworkConnection;
import javax.media.mscontrol.networkconnection.SdpPortManager;
import javax.media.mscontrol.networkconnection.SdpPortManagerEvent;
import javax.media.mscontrol.spi.DriverManager;
import javax.servlet.ServletException;
import javax.servlet.sip.SipServlet;
import javax.servlet.sip.SipServletMessage;
import javax.servlet.sip.SipServletRequest;

import org.apache.log4j.Logger;

/**
 * The minimal conference service: all callers are conferenced together.
 * A single conference for all, everybody can talk/listen.
 */
@SuppressWarnings("serial")
public class MinimalConferenceServlet extends SipServlet {

	/**
	 * Use a single MediaSession, hosting the mixer, and all NetworkConnections
	 */
	MediaSession theMediaSession; 
	MediaMixer theMixer;

	@Override
	public void init() throws ServletException {
		super.init();
		MsControlFactory theMsControlFactory = null;
		try {
			theMsControlFactory = DriverManager.getDrivers().next().getFactory(null);
			theMediaSession = theMsControlFactory.createMediaSession();
			theMixer = theMediaSession.createMediaMixer(MediaMixer.AUDIO);
		} catch (Exception e) {
			log.fatal("Cannot create MediaSession or MediaSessionFactory :", e);
			throw new ServletException(e);
		}
	}

	/**
	 * A new caller comes in
	 */
	@Override
	protected void doInvite(final SipServletRequest req) throws ServletException, IOException {
		try {
			// Create a NetworkConnection that will handle the RTP streams of the new caller
			final NetworkConnection myNetworkConnection = theMediaSession.createNetworkConnection(NetworkConnection.BASIC);
			// Save the NetworkConnection reference as an attribute of the SipSession
			req.getSession().setAttribute("media-connection", myNetworkConnection);
						
			// Join the NetworkConnection to the conference
			theMixer.join(Joinable.Direction.DUPLEX , myNetworkConnection);
			
			// Get the RTP ports manager
			final SdpPortManager mySDPPortSet = myNetworkConnection.getSdpPortManager();
			
			// Register a listener to the RTP ports, to define what we'll do when the connection is setup.
			MediaEventListener<SdpPortManagerEvent> mySDPPortSetListener = new MediaEventListener<SdpPortManagerEvent>() {

				public void onEvent(SdpPortManagerEvent event) {
					try {
						if (event.getEventType().equals(SdpPortManagerEvent.ANSWER_GENERATED)) {
							// The NetworkConnection has been setup properly.
							// Send a 200 OK, with negociated SDP attached.
							byte[] sdpAnswer = event.getMediaServerSdp();
							SipServletMessage msg = req.createResponse(200, "OK");
							msg.setContent(sdpAnswer, "application/sdp");
							msg.send();
						} else {
							// SDP not accepted
							req.createResponse(500, "Unsupported Media Type").send();
							myNetworkConnection.release();
						}
					} catch (Exception e) {
						myNetworkConnection.release();
					}
				}
			};
			mySDPPortSet.addListener(mySDPPortSetListener);
			
			// Request the media server to start SDP negociation
			// Assume that all INVITE's carry an SDP offer
			mySDPPortSet.processSdpOffer(req.getRawContent());
			
		} catch (Exception e) {
			throw new ServletException(e);
		}		
	}

	/**
	 * A caller hangs up
	 */
	@Override
	protected void doBye(SipServletRequest req) throws ServletException, IOException {
		final NetworkConnection myNetworkConnection = (NetworkConnection)req.getSession().getAttribute("media-connection");
		myNetworkConnection.release();
	}

	@Override
	public void destroy() {
		theMediaSession.release();
		super.destroy();
	}

	static Logger log = Logger.getLogger(MinimalConferenceServlet.class);
}
