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

import java.net.URL;
import java.util.Map;

import javax.media.mscontrol.MediaEventListener;
import javax.media.mscontrol.join.Joinable;
import javax.media.mscontrol.vxml.VxmlDialog;
import javax.media.mscontrol.vxml.VxmlDialogEvent;
import javax.servlet.sip.SipSession;

import org.apache.log4j.Logger;

public class VxmlMailSession extends VoiceMailSession {

	public VxmlMailSession(SipSession aSipSession) {
		super(aSipSession);
	}

	VxmlDialog myDialog;

	enum DialogState {
		IDLE, START_REQUESTED, PREPARED, STARTED, TERMINATED
	};

	DialogState myState = DialogState.IDLE;

	private void setState(DialogState state) {
		log.info("Moving from state " + myState + " to state " + state);
		myState = state;
	}

	Map<String, Object> params;

	/*
	 * (non-Javadoc)
	 * 
	 * @see voicemail.VoiceMailSession#initDialog()
	 */
	@Override
	public void initDialog() throws Exception {
		myDialog = myMediaSession.createVxmlDialog(null);
		myDialog.addListener(new VoiceMailVxmlListener());
		myNetworkConnection.join(Joinable.Direction.DUPLEX, myDialog);
		myDialog.prepare(new URL("http://vxmlserver/voicemail.vxml"), null,
				null);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see voicemail.VoiceMailSession#start()
	 */
	@Override
	public void startDialog(Map<String, Object> params) {
		// If the dialog is already prepared, start right away.
		if (myState == DialogState.PREPARED)
			myDialog.start(params);
		// else, save the params, and wait for the dialog to be prepared.
		else {
			setState(DialogState.START_REQUESTED);
			this.params = params;
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see voicemail.VoiceMailSession#terminateDialog()
	 */
	@Override
	public void terminateDialog() {
		myDialog.terminate(true);
	}

	class VoiceMailVxmlListener implements MediaEventListener<VxmlDialogEvent> {
		/*
		 * (non-Javadoc)
		 * 
		 * @see javax.media.mscontrol.vxml.VxmlDialogListener#onEvent(javax.media.mscontrol.vxml.VxmlDialogEvent)
		 */
		public void onEvent(VxmlDialogEvent event) {

			if (event.getEventType().equals(VxmlDialogEvent.PREPARED)) {
				if (myState == DialogState.IDLE) {
					setState(DialogState.PREPARED);
				} else if (myState == DialogState.START_REQUESTED) {
					myDialog.start(params);
				}
			} else if (event.getEventType().equals(VxmlDialogEvent.STARTED)) {
				setState(DialogState.STARTED);
			} else if (event.getEventType().equals(VxmlDialogEvent.EXITED)) {
				setState(DialogState.TERMINATED);
				myMediaSession.release();
				Map<String, Object> dialogResults = event.getNameList();
				@SuppressWarnings("unused")
				String newBalance = (String) dialogResults.get("New balance");
				// update the customers's account, or whatever ...
			} else
				log.error("Unexpected event: " + event);
		}
	}

	static Logger log = Logger.getLogger(VxmlMailSession.class);
}
