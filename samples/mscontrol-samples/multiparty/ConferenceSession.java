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

import java.util.List;
import java.util.Vector;

import javax.media.mscontrol.MediaSession;
import javax.media.mscontrol.MsControlException;
import javax.media.mscontrol.mixer.MediaMixer;

/**
 * Conference room - Has its own MediaSession, and a MediaMixer
 */
public class ConferenceSession {

	// Conference JSR 309 objects
	private final MediaSession myMediaSession;
	private final MediaMixer myMediaMixer;

	// Conference identifier and participants list
	private final String confId;
	private List<Participant> myParticipants;

	/**
	 * Constructor - Instantiate JSR 309 objects, add the creator to the
	 * Participants list
	 * 
	 * @param confId
	 * @param participant
	 * @throws MsControlException
	 */
	public ConferenceSession(String confId, Participant participant)
			throws MsControlException {
		this.confId = confId;
		myParticipants = new Vector<Participant>();
		myMediaSession = ConferenceServlet.theMsControlFactory
				.createMediaSession();
		myMediaMixer = myMediaSession.createMediaMixer(MediaMixer.AUDIO);
		this.addParticipant(participant);
	}

	/**
	 * Add a participant to this conference room
	 * 
	 * @param participant
	 */
	public void addParticipant(Participant participant) {
		myParticipants.add(myParticipants.size(), participant);
	}

	/**
	 * Remove a participant from this conference room, delete the conference
	 * when there is no more participant
	 * 
	 * @param participant
	 */
	public void removeParticipant(Participant participant) {
		myParticipants.remove(participant);
		if (myParticipants.size() == 0)
			myMediaSession.release();
	}

	public MediaMixer getMediaMixer() {
		return myMediaMixer;
	}

	public String getConfId() {
		return confId;
	}

}
