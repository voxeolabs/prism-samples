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

import javax.media.mscontrol.MediaSession;
import javax.media.mscontrol.Parameters;
import javax.media.mscontrol.join.JoinEvent;
import javax.media.mscontrol.join.JoinEventListener;
import javax.media.mscontrol.join.Joinable.Direction;
import javax.media.mscontrol.mediagroup.MediaGroup;
import javax.media.mscontrol.networkconnection.NetworkConnection;
import javax.media.mscontrol.resource.RTC;
import javax.servlet.ServletException;
import javax.servlet.sip.SipSession;

/**
 * This servlet is a variation of PlayerServlet that uses <code>Joinable.joinInitiate</code>
 * to perform the join operation asynchronously.
 * An intermediate state appears: JOINING
 */
public class AsyncPlayerServlet extends PlayerServlet {

	private static final long serialVersionUID = 20080826L;

	public final static String JOINING = "JOINING";

	private MyJoinEventListener joinListener;
	
	@Override
	public void init() throws ServletException {
		super.init();
		joinListener = new MyJoinEventListener();
	}
	/**
	 * Play the prompt to the UA.
	 * <br>Create and join the MediaGroup, if this is not yet done.
	 * This method starts like its counterpart in PlayerServlet, but
	 * stops after calling <code>joinInitiate</code>, in the JOINING state.
	 * @param sipSession
	 */
	protected void runDialog(SipSession sipSession) {
		
		MediaSession mediaSession = (MediaSession)sipSession.getAttribute("MEDIA_SESSION");
		try {
			MediaGroup mediaGroup = (MediaGroup)sipSession.getAttribute("MEDIAGROUP");
			if (mediaGroup == null) {
				// Create a MediaGroup
				mediaGroup = mediaSession.createMediaGroup(MediaGroup.PLAYER);
				// Save reference for future use
				sipSession.setAttribute("MEDIAGROUP", mediaGroup);
				// Attach a listener to the Player
				mediaGroup.getPlayer().addListener(playerListener);
				// Attach a status listener for join events
				mediaGroup.addListener(joinListener);
				// Request to join it to the NetworkConnection
				mediaGroup.joinInitiate(Direction.DUPLEX, (NetworkConnection)sipSession.getAttribute("NETWORK_CONNECTION"), null);
				// Processing continues in the join completion listener
				setState(sipSession, JOINING);
			} else {
				// MediaGroup is already setup, proceed with the prompt.
				playPrompt(sipSession, mediaSession, mediaGroup);
			}
		} catch (Exception e) {
			log("cannot runDialog: "+e);
			terminate(sipSession, mediaSession);			
		}
	}
	
	class MyJoinEventListener implements JoinEventListener {

		public void onEvent(JoinEvent event) {
			if (event.isSuccessful()) {
				// Successfully joined, proceed with the prompt.
				MediaGroup mg = (MediaGroup)event.getSource();
				MediaSession mediaSession = (MediaSession)mg.getMediaSession();
				SipSession sipSession = (SipSession)mediaSession.getAttribute("SIP_SESSION");
				playPrompt(sipSession, mediaSession, mg);
			}
		}
		
	}
	
	private void playPrompt(SipSession sipSession, MediaSession mediaSession, MediaGroup aMG) {
		try {
			aMG.getPlayer().play(prompt, RTC.NO_RTC, Parameters.NO_PARAMETER);
			setState(sipSession, DIALOG);
		} catch (Exception e) {
			log("cannot play prompt: "+e);
			terminate(sipSession, mediaSession);
		}
	}
}
