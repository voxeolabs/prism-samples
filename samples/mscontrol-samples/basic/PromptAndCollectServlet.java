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
package basic;

import javax.media.mscontrol.MediaEventListener;
import javax.media.mscontrol.MediaSession;
import javax.media.mscontrol.Parameters;
import javax.media.mscontrol.join.Joinable.Direction;
import javax.media.mscontrol.mediagroup.MediaGroup;
import javax.media.mscontrol.mediagroup.signals.SignalDetector;
import javax.media.mscontrol.mediagroup.signals.SignalDetectorEvent;
import javax.media.mscontrol.networkconnection.NetworkConnection;
import javax.media.mscontrol.resource.RTC;
import javax.servlet.ServletException;
import javax.servlet.sip.SipSession;



/**
 * An enhancement to the PlayerServlet, where 4 DTMFs are collected
 * after the prompt is played (or while the prompt is playing).
 * <br>Basically <code>Player.play(prompt, ...)</code> is replaced by <code>SignalDetector.receiveSignals(4, ..., prompt)</code>.
 */
public class PromptAndCollectServlet extends PlayerServlet {
	private static final long serialVersionUID = 20080825L;

	// Listener for MediaGroup events
	private MySignalDetectorListener sigDetListener;
	
	// The options to receiveSignals
	private Parameters collectOptions;

	@Override
	public void init() throws ServletException {
		super.init();
		sigDetListener = new MySignalDetectorListener();
		
		// Setup the options for receiveSignals
		collectOptions = msControlFactory.createParameters();
		// Indicate the message to play
		collectOptions.put(SignalDetector.PROMPT, prompt);
	}

	@Override
	public void runDialog(SipSession sipSession) {
		
		try {
			MediaSession ms = (MediaSession)sipSession.getAttribute("MEDIA_SESSION");
			MediaGroup mg = null;
			mg = (MediaGroup)sipSession.getAttribute("MEDIAGROUP");
			if (mg == null) {
				// Create a MediaGroup
				mg = ms.createMediaGroup(MediaGroup.PLAYER_SIGNALDETECTOR);
				// Save reference for future use
				sipSession.setAttribute("MEDIAGROUP", mg);
				// Attach a listener to the SignalDetector
				mg.getSignalDetector().addListener(sigDetListener);
				// Join it to the NetworkConnection
				mg.join(Direction.DUPLEX, (NetworkConnection)sipSession.getAttribute("NETWORK_CONNECTION"));
			}
			// Initiate the prompt and collect operation
			// RTC.bargeIn indicates that any DTMF typed when the prompt is playing, will stop the prompt
			mg.getSignalDetector().receiveSignals(4, SignalDetector.NO_PATTERN, 
					new RTC[] {MediaGroup.SIGDET_STOPPLAY}, collectOptions);
			setState(sipSession, DIALOG);
			
		} catch (Exception e) {
			// Clean up media session
			MediaSession mediaSession = (MediaSession)sipSession.getAttribute("MEDIA_SESSION");
			terminate(sipSession, mediaSession);
			return;
		}
	}
	
	class MySignalDetectorListener implements MediaEventListener<SignalDetectorEvent> {

		public void onEvent(SignalDetectorEvent event) {
			log("ReceiveSignals terminated with: "+event);
			// In this example, the collected DTMFs are just logged.
			// In real life they could be returned in a signalling parameter, or propagated to a JSP
			log("Collected: "+event.getSignalString());
			
			// Release the call and terminate
			MediaSession mediaSession = event.getSource().getMediaSession();
			SipSession sipSession = (SipSession) mediaSession.getAttribute("SIP_SESSION");
			terminate(sipSession, mediaSession);
		}
	}		
}

