/* ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 * Copyright (c) 2008 Hewlett-Packard, Inc. All rights reserved.
 * Copyright (c) 2008 Oracle and/or its affiliates. All rights reserved.
 *
 * Use is subject to license terms.
 * 
 * This code should only be used for further understanding of the
 * specifications and is not of production quality in terms of robustness,
 * scalability etc.
 * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 */
package voicemail;

import java.util.Map;

import javax.media.mscontrol.MediaEventListener;
import javax.media.mscontrol.MediaSession;
import javax.media.mscontrol.MsControlFactory;
import javax.media.mscontrol.networkconnection.NetworkConnection;
import javax.media.mscontrol.networkconnection.SdpPortManager;
import javax.media.mscontrol.networkconnection.SdpPortManagerEvent;
import javax.media.mscontrol.spi.DriverManager;
import javax.servlet.sip.SipServletMessage;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipSession;

import org.apache.log4j.Logger;

public abstract class VoiceMailSession {
	static Logger log = Logger.getLogger(VoiceMailSession.class);

	static MsControlFactory myMsControlFactory;
	static {
		myMsControlFactory = null;
		try {
			myMsControlFactory = DriverManager.getDrivers().next().getFactory(null);
		} catch (Exception e) {
			log.fatal("Cannot create MediaSessionFactory :", e);
		}
	}

	public VoiceMailSession(SipSession sipSession) {
		mySipSession = sipSession;
	}

	final SipSession mySipSession;

	MediaSession myMediaSession;
	NetworkConnection myNetworkConnection;

	public void init(final SipServletRequest req) throws Exception {
			// First, create a MediaSession that will host the media objects
			myMediaSession = myMsControlFactory.createMediaSession();

			// Create a NetworkConnection that will handle the UA's RTP streams
			myNetworkConnection = myMediaSession.createNetworkConnection(NetworkConnection.BASIC);

			// Get the RTP ports manager
			final SdpPortManager mySDPPortSet = myNetworkConnection.getSdpPortManager();
			
			// Register a listener, to define what we'll do when the connection is setup.
			MediaEventListener<SdpPortManagerEvent> myNetworkConnectionListener = new MediaEventListener<SdpPortManagerEvent>() {

				public void onEvent(SdpPortManagerEvent event) {
					try {
						if (SdpPortManagerEvent.ANSWER_GENERATED.equals(event.getEventType())) {
							// The NetworkConnection has been setup properly.
							// Create some sort of dialog object, vxml or
							// core-based.
							// This is defined by the derived class.
							initDialog();
							// send a 200 OK, with negociated SDP attached
							byte[] sdpAnswer = event.getMediaServerSdp();
							SipServletMessage msg = req.createResponse(200, "OK");
							msg.setContent(sdpAnswer, "application/sdp");
							msg.send();
						} else {
							// sdp not accepted
							req.createResponse(500, "Unsupported Media Type").send();
						}
					} catch (Exception e) {
						myMediaSession.release();
					}
				}
			};
			mySDPPortSet.addListener(myNetworkConnectionListener);

			// Request the media server to start SDP negociation			
			// Assume that all INVITE's carry an SDP offer
			mySDPPortSet.processSdpOffer(req.getRawContent());
	}
	
	void release() {
		if (myMediaSession != null)
			myMediaSession.release();
	}

	/** ***************************************************************************************** */
	/**
	 * ** The methods below must be provided by a derived class, to handle the
	 * media dialog ****
	 */
	/** ***************************************************************************************** */

	/**
	 * Called upon reception of an INVITE
	 */
	public abstract void initDialog() throws Exception;

	/**
	 * Called upon reception of the ACK of the INVITE response
	 */
	public abstract void startDialog(Map<String, Object> params);

	/**
	 * Terminate request, from servlet (caller has hang up)
	 */
	public abstract void terminateDialog();
}
