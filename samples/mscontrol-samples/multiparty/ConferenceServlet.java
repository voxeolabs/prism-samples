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
package multiparty;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.media.mscontrol.MsControlFactory;
import javax.media.mscontrol.MsControlException;
import javax.media.mscontrol.spi.DriverManager;
import javax.servlet.ServletException;
import javax.servlet.sip.SipServlet;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipSession;

/**
 * Servlet answering SIP conference service incoming calls
 */
@SuppressWarnings("serial")
public class ConferenceServlet extends SipServlet {

	// Store Participants linked to this Servlet
	private Map<SipSession, Participant> myParticipants;
	private static Map<String, ConferenceSession> theConferences;

	// Common factory for JSR 309 objects used by all service classes
	public static MsControlFactory theMsControlFactory;

	@Override
	public void init() throws ServletException {
		try {
			super.init();
			theMsControlFactory = DriverManager.getDrivers().next().getFactory(null);
			myParticipants = new HashMap<SipSession, Participant>();
			theConferences = new HashMap<String, ConferenceSession>();
		} catch (Exception msce) {
			throw new ServletException(
					"Cannot initialize ConferenceServlet due to internale service error",
					msce);
		}
	}

	@Override
	protected void doInvite(SipServletRequest arg0) throws ServletException,
			IOException {
		try {
			// Create a new Participant
			Participant newParticipant = new Participant(arg0, this);
			myParticipants.put(arg0.getSession(), newParticipant);
		} catch (Exception e) {
			throw new ServletException(e);
		}
	}

	@Override
	protected void doAck(SipServletRequest arg0) throws ServletException,
			IOException {
		// Launch the media service
		myParticipants.get(arg0.getSession()).start();
	}

	@Override
	protected void doBye(SipServletRequest arg0) throws ServletException,
			IOException {
		// Terminate the service
		myParticipants.get(arg0.getSession()).release();
		myParticipants.remove(arg0.getSession());
		// Send 200 OK to the UA
		SipServletResponse resp = arg0.createResponse(SipServletResponse.SC_OK);
		resp.send();
	}

	/**
	 * Add a participant to the give conference room, create the
	 * ConferenceSession if not existing
	 * 
	 * @param confId
	 * @param participant
	 * @return
	 * @throws MsControlException
	 */
	public ConferenceSession addParticipant(String confId,
			Participant participant) throws MsControlException {
		ConferenceSession confRoom;
		if (!theConferences.containsKey(confId)) {
			// ConferenceSession does not exist, create a new one
			confRoom = new ConferenceSession(confId, participant);
			theConferences.put(confId, confRoom);
		} else {
			// Add the participant to existing conference
			confRoom = theConferences.get(confId);
			confRoom.addParticipant(participant);
		}
		return confRoom;
	}

	/**
	 * Send a SIP BYE to the User Agent, remove Participant on Servlet side
	 * 
	 * @param theSipSession
	 *            participant SipSession
	 */
	public void sendBye(SipSession theSipSession) throws IOException {
		SipServletRequest req = theSipSession.createRequest("BYE");
		req.send();
		myParticipants.remove(theSipSession);
	}

}
