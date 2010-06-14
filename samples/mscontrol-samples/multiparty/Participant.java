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
import java.net.URI;

import javax.media.mscontrol.MediaEvent;
import javax.media.mscontrol.MediaEventListener;
import javax.media.mscontrol.MediaSession;
import javax.media.mscontrol.MsControlException;
import javax.media.mscontrol.Parameters;
import javax.media.mscontrol.join.Joinable;
import javax.media.mscontrol.mediagroup.MediaGroup;
import javax.media.mscontrol.mediagroup.Player;
import javax.media.mscontrol.mediagroup.PlayerEvent;
import javax.media.mscontrol.mediagroup.Recorder;
import javax.media.mscontrol.mediagroup.RecorderEvent;
import javax.media.mscontrol.mediagroup.signals.SignalDetector;
import javax.media.mscontrol.mediagroup.signals.SignalDetectorEvent;
import javax.media.mscontrol.mixer.MixerAdapter;
import javax.media.mscontrol.networkconnection.NetworkConnection;
import javax.media.mscontrol.networkconnection.SdpPortManager;
import javax.media.mscontrol.networkconnection.SdpPortManagerEvent;
import javax.media.mscontrol.resource.RTC;
import javax.servlet.ServletException;
import javax.servlet.sip.SipServletMessage;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipSession;
import javax.media.mscontrol.resource.common.VolumeConstants;

import org.apache.log4j.Logger;

/**
 * Conference Participant - Own a MediaSession, a MediaGroup, a MixerAdapter,
 * and of course a NetworkConnection
 */
public class Participant {

	public static Logger log = Logger.getLogger(Participant.class);

	private final ConferenceServlet myConferenceServlet;
	private ConferenceSession myConferenceSession;
	private final SipSession mySipSession;

	private final NetworkConnection myNetworkConnection;
	private final MediaGroup myMediaGroup;
	private final MediaSession myMediaSession;
	private MixerAdapter myMixerAdapter;

	public Participant(final SipServletRequest req, ConferenceServlet servlet) throws ServletException {
		try {
			mySipSession = req.getSession();
			myConferenceServlet = servlet;
			myMediaSession = ConferenceServlet.theMsControlFactory.createMediaSession();

			myNetworkConnection = myMediaSession.createNetworkConnection(NetworkConnection.BASIC);

			myMediaGroup = myMediaSession.createMediaGroup(MediaGroup.PLAYER_RECORDER_SIGNALDETECTOR);
			myMediaGroup.getPlayer().addListener(new ConfListener<PlayerEvent>());
			myMediaGroup.getRecorder().addListener(new ConfListener<RecorderEvent>());
			myMediaGroup.getSignalDetector().addListener(new ConfListener<SignalDetectorEvent>());

			myNetworkConnection.join(Joinable.Direction.DUPLEX, myMediaGroup);

			// Get the RTP ports manager
			final SdpPortManager mySDPPortSet = myNetworkConnection.getSdpPortManager();
			
			mySDPPortSet.addListener(new MediaEventListener<SdpPortManagerEvent>() {
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
						}
					} catch (Exception e) {
						myMediaSession.release();
					}
				}
			});
			// Request the media server to start SDP negociation
			// Assume that all INVITE's carry an SDP offer
			mySDPPortSet.processSdpOffer(req.getRawContent());
		} catch (Exception e) {
			log.fatal("Cannot create MediaSession or MediaSessionFactory :", e);
			throw new ServletException(e);
		}
	}

	public void start() {
		setState(State.EnterConfId);
		try {
			myMediaGroup.getPlayer().play(
					URI.create("/prompts/PleaseEnterYourConferenceID.wav"),
					new RTC[] { new RTC(
							SignalDetector.DETECTION_OF_ONE_SIGNAL,
							Player.STOP) },
					Parameters.NO_PARAMETER);
		} catch (Exception e) {
			terminate(e);
		}
	}

	/**
	 * Release Participant from its conference and release its MediaSession
	 */
	public void release() {
		myConferenceSession.removeParticipant(this);
		myMediaSession.release();
	}

	/**
	 * Delete Participant because of unexpected error
	 */
	public void terminate(Exception e) {
		myConferenceSession.removeParticipant(this);
		myMediaSession.release();
		try {
			myConferenceServlet.sendBye(mySipSession);
		} catch (IOException ioe) {
			log.error("Unable to send SIP BYE to the User Agent", ioe);
		}
	}
	
	private class ConfListener<T extends MediaEvent<?>> implements MediaEventListener<T> {
		public void onEvent(T event) {
			log.debug(event);
			try {
				if (event instanceof PlayerEvent)
					myState.onPlayerEvent((PlayerEvent)event, Participant.this);
				else if (event instanceof RecorderEvent)
					myState.onRecorderEvent((RecorderEvent)event, Participant.this);
				else if (event instanceof SignalDetectorEvent)
					myState.onSignalDetectorEvent((SignalDetectorEvent)event, Participant.this);
			} catch (Exception e) {
				terminate(e);
			}
		}
	}

	private enum State {
		Initial, EnterConfId {
			public void onPlayerEvent(PlayerEvent event, Participant part)
					throws MsControlException {
				part.setState(CollectingID);
				event.getSource().getContainer().getSignalDetector().receiveSignals(4, SignalDetector.NO_PATTERN,
						RTC.NO_RTC, Parameters.NO_PARAMETER);
			}
		},
		CollectingID {
			public void onSignalDetectorEvent(SignalDetectorEvent event,
					Participant part) throws MsControlException {
				part.getConferenceSession(event.getSignalString());
				part.setState(PleaseSayYourName);
				part.myMediaGroup.getPlayer().play(
						URI.create("/prompts/PleaseSayYourNameEndWith#.wav"),
						RTC.NO_RTC, Parameters.NO_PARAMETER);
			}
		},
		PleaseSayYourName {
			public void onPlayerEvent(PlayerEvent event,
					Participant part) throws MsControlException {
				if (event.getQualifier().equals(PlayerEvent.END_OF_PLAY_LIST)) {
					part.myMediaGroup.getRecorder().record(
							URI.create("/prompts/MyParticipantName.wav"),
							new RTC[] { new RTC(
									SignalDetector.DETECTION_OF_ONE_SIGNAL,
									Recorder.STOP) },
							Parameters.NO_PARAMETER);
					part.setState(RecordingName);
				}
			}
		},
		RecordingName {
			public void onRecorderEvent(RecorderEvent event, Participant part)
					throws MsControlException {
				if (event.getEventType().equals(RecorderEvent.RECORD_COMPLETED) && 
						event.isSuccessful()) {
					part.setState(Conferencing);
					part.enterConference();
					part.myMediaGroup.getSignalDetector().receiveSignals(1, SignalDetector.NO_PATTERN,
							RTC.NO_RTC, Parameters.NO_PARAMETER);
				}
			}
		},
		Conferencing {
			public void onSignalDetectorEvent(SignalDetectorEvent event,
					Participant part) throws MsControlException {
				if (event.getSignalString().equalsIgnoreCase("4")) {
					// Increase participant volume
					part.myMixerAdapter.triggerAction(VolumeConstants.VOLUME_DOWN);
				}
				else if (event.getSignalString().equalsIgnoreCase("7")) {
					// Decrease volume
					part.myMixerAdapter.triggerAction(VolumeConstants.VOLUME_UP);
				}
				else if (event.getSignalString().equalsIgnoreCase("6")) {
					// Mute participant line
					part.myNetworkConnection.join(Joinable.Direction.RECV, part.myMixerAdapter);
				}
				else if (event.getSignalString().equalsIgnoreCase("1")) {
					// Unmute line
					part.myNetworkConnection.join(Joinable.Direction.DUPLEX, part.myMixerAdapter);
				}
				part.myMediaGroup.getSignalDetector().receiveSignals(1, SignalDetector.NO_PATTERN,
						RTC.NO_RTC, Parameters.NO_PARAMETER);
			}
		};

		public void onPlayerEvent(PlayerEvent event, Participant part)
				throws MsControlException {
			Participant.log.error("Unexpected player event: " + event
					+ " in state " + this + " - releasing");
			event.getSource().getMediaSession().release();
		}

		public void onRecorderEvent(RecorderEvent event, Participant part)
				throws MsControlException {
			Participant.log.error("Unexpected recorder event: " + event
					+ " in state " + this + " - releasing");
			event.getSource().getMediaSession().release();
		}

		public void onSignalDetectorEvent(SignalDetectorEvent event,
				Participant part) throws MsControlException {
			Participant.log.error("Unexpected signal detector event: "
					+ event + " in state " + this + " - releasing");
			event.getSource().getMediaSession().release();
		}
	}

	private State myState = State.Initial;

	private void setState(State newState) {
		log.info("Moving from " + myState + " to " + newState);
		myState = newState;
	}

	/**
	 * Enter conference room
	 */
	private void enterConference() {
		try {
			myMixerAdapter = myConferenceSession.getMediaMixer()
					.createMixerAdapter(MixerAdapter.DTMFCLAMP_VOLUME);
			myNetworkConnection.join(Joinable.Direction.DUPLEX, myMixerAdapter);
		} catch (Exception e) {
			terminate(e);
		}
	}

	/**
	 * Set conference session reference from given identifier
	 * 
	 * @param confID
	 * @throws MsControlException
	 */
	private void getConferenceSession(String confId) throws MsControlException {
		myConferenceSession = myConferenceServlet.addParticipant(confId, this);
	}

}
