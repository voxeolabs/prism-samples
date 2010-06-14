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

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import javax.media.mscontrol.EventType;
import javax.media.mscontrol.MediaEventListener;
import javax.media.mscontrol.Parameter;
import javax.media.mscontrol.Parameters;
import javax.media.mscontrol.Qualifier;
import javax.media.mscontrol.join.Joinable;
import javax.media.mscontrol.mediagroup.MediaGroup;
import javax.media.mscontrol.mediagroup.Player;
import javax.media.mscontrol.mediagroup.PlayerEvent;
import javax.media.mscontrol.mediagroup.Recorder;
import javax.media.mscontrol.mediagroup.RecorderEvent;
import javax.media.mscontrol.mediagroup.signals.SignalDetector;
import javax.media.mscontrol.mediagroup.signals.SignalDetectorEvent;
import javax.media.mscontrol.resource.RTC;
import javax.media.mscontrol.resource.Trigger;
import javax.servlet.sip.SipSession;

import org.apache.log4j.Logger;

/**
 * Manage all the media objects required to perform the voicemail service. Play
 * prompts, catch DTMF's, record messages. <br>
 * Dialog progression is driven by a Finite State Machine, see CallFSM.
 */
public class CoreMailSession extends VoiceMailSession {
	static Logger log = Logger.getLogger(CoreMailSession.class);

	public CoreMailSession(SipSession sipSession) {
		super(sipSession);
	}

	/* ***************************************************************************** */
	/* *** 1 - Instance and class variables ** */
	/* ***************************************************************************** */
	MediaGroup myMediaGroup;
	Player myPlayer;
	Recorder myRecorder;
	SignalDetector mySignalDetector;

	String userID;
	URI[] msgs; // messages contained in the user's mailbox just before starting
				// to listen
	int current_msg = 0; // Index (in msgs[]) of the message being played
							// back
	Vector<String> userDestinationList = new Vector<String>(); // A message can
																// be deposited
																// in multiple
																// mailboxes:
																// this is the
																// list

	static List<String> connectedUsers = new ArrayList<String>(); // To avoid
																	// concurrent
																	// accesses
																	// by
																	// multiple
																	// SIP calls

	/* ***************************************************************************** */
	/*
	 * *** 2 - Define the DTMF patterns that we will use throughout the
	 * application **
	 */
	/* ***************************************************************************** */
	/**
	 * Define meaningful local names (aliases), for the pattern Parameter's and Trigger's that we
	 * will use later.
	 */
	static final Parameter userId = SignalDetector.PATTERN[0]; // see below
																// what a userID
																// looks like
	static final Qualifier userIdQualifier = SignalDetectorEvent.PATTERN_MATCHING[0];

	static final Parameter sendMessage = SignalDetector.PATTERN[1];
	static final Qualifier sendMessageQualifier = SignalDetectorEvent.PATTERN_MATCHING[1];
	static final Trigger sendMessageTrigger = SignalDetector.PATTERN_MATCH[1];

	static final Parameter listenMessage = SignalDetector.PATTERN[2];
	static final Qualifier listenMessageQualifier = SignalDetectorEvent.PATTERN_MATCHING[2];
	static final Trigger listenMessageTrigger = SignalDetector.PATTERN_MATCH[2];

	static final Parameter quit = SignalDetector.PATTERN[3];
	static final Qualifier quitQualifier = SignalDetectorEvent.PATTERN_MATCHING[3];
	static final Trigger quitTrigger = SignalDetector.PATTERN_MATCH[3];

	static final Parameter stopRecord = SignalDetector.PATTERN[4];
	static final Trigger stopRecordTrigger = SignalDetector.PATTERN_MATCH[4];

	static final Parameter skipMessage = SignalDetector.PATTERN[5];
	static final Qualifier skipMessageQualifier = SignalDetectorEvent.PATTERN_MATCHING[5];
	static final Trigger skipMessageTrigger = SignalDetector.PATTERN_MATCH[5];

	static final Parameter replayMessage = SignalDetector.PATTERN[6];
	static final Qualifier replayMessageQualifier = SignalDetectorEvent.PATTERN_MATCHING[6];
	static final Trigger replayMessageTrigger = SignalDetector.PATTERN_MATCH[6];

	static final Parameter pauseMessage = SignalDetector.PATTERN[7];
	static final Qualifier pauseMessageQualifier = SignalDetectorEvent.PATTERN_MATCHING[7];
	static final Trigger pauseMessageTrigger = SignalDetector.PATTERN_MATCH[7];

	static final Parameter resumeMessage = SignalDetector.PATTERN[8];
	static final Qualifier resumeMessageQualifier = SignalDetectorEvent.PATTERN_MATCHING[8];
	static final Trigger resumeMessageTrigger = SignalDetector.PATTERN_MATCH[8];

	static final Parameter endOfList = SignalDetector.PATTERN[9];
	static final Qualifier endOfListQualifier = SignalDetectorEvent.PATTERN_MATCHING[9];
	// let's start the demo
	static final Parameter go = SignalDetector.PATTERN[10];
	static final Qualifier goQualifier = SignalDetectorEvent.PATTERN_MATCHING[10];
	static final Trigger goTrigger = SignalDetector.PATTERN_MATCH[10];

	/**
	 * Define which DTMF(s) compose each pattern
	 */
	static Parameters patterns;
	static {
		patterns = myMsControlFactory.createParameters();
		patterns.put(sendMessage, "1"); // "Press 1 to send a message"
		patterns.put(listenMessage, "2"); // "Press 2 to listen to messages"
		patterns.put(quit, "0");
		patterns.put(stopRecord, "#");
		patterns.put(skipMessage, "1");
		patterns.put(replayMessage, "2");
		patterns.put(pauseMessage, "3"); // pause/resume on DTMFs 3/4
		patterns.put(resumeMessage, "4");
		patterns.put(endOfList, "*");
		patterns.put(go, "*");

		// A userID is "any combination of 4 digits"
		try {
			patterns.put(userId, URI.create("data:" + URLEncoder.encode("application/srgs+xml,"+
				   "<grammar xmlns=\"http://www.w3.org/2001/06/grammar\" mode=\"dtmf\" version=\"1.0\">"+
			        "<rule scope=\"public\">"+
			          "<item repeat=\"4\">"+
			            "<one-of>"+
			              "<item> 0 </item>"+
			              "<item> 1 </item>"+
			              "<item> 2 </item>"+
			              "<item> 3 </item>"+
			              "<item> 4 </item>"+
			              "<item> 5 </item>"+
			              "<item> 6 </item>"+
			              "<item> 7 </item>"+
			              "<item> 8 </item>"+
			              "<item> 9 </item>"+
			            "</one-of>"+
			          "</item>"+
			         "</rule>"+
			        "</grammar>", "UTF-8")));
		} catch (UnsupportedEncodingException e) {
			log.fatal("Cannot encode SRGS grammar", e);
		}
	}

	/**
	 * Define the arrays of patterns that will be used in the various phases of
	 * user interaction. <br>
	 * For example, using <code>menu</code> will activate PATTERN[1],
	 * PATTERN[2] and PATTERN[3]. This will cause detection of "1", "2" or
	 * "0".
	 */
	static Parameter[] startDemo = { go };
	static Parameter[] userIdentification = { userId };
	static Parameter[] destUserIdList = { userId, endOfList };
	static Parameter[] menu = { sendMessage, listenMessage, quit };
	static Parameter[] listen = { skipMessage, replayMessage, pauseMessage,
			resumeMessage, quit };
	static Parameter[] cont = { go, quit };

	/* ********************************************************************************* */
	/*
	 * *** 3 - Define the RunTimeControls that we will use throughout the
	 * application **
	 */
	/* ********************************************************************************* */
	/**
	 * A RTC to stop the prompt when a DTMF is detected (barge-in):
	 */
	static RTC promptRTC[] = { new RTC(SignalDetector.DETECTION_OF_ONE_SIGNAL,
			Player.STOP) };

	/**
	 * A RTC to stop recording the message when pattern <code>stopRecord</code>
	 * ("#") is detected.
	 */
	static RTC recordRTC[] = { new RTC(stopRecordTrigger, Recorder.STOP) };

	/**
	 * When the user is listening to a message, we want to catch 5 different
	 * conditions (Trigger's). <br>
	 */
	static RTC[] messageRTC = { new RTC(quitTrigger, Player.STOP),
			new RTC(skipMessageTrigger, Player.STOP),
			new RTC(replayMessageTrigger, Player.STOP),
			new RTC(pauseMessageTrigger, Player.PAUSE),
			new RTC(resumeMessageTrigger, Player.RESUME), };

	static RTC[] menuRTC = { new RTC(sendMessageTrigger, Player.STOP),
			new RTC(listenMessageTrigger, Player.STOP),
			new RTC(quitTrigger, Player.STOP) };

	static RTC[] enterlistenRTC = { new RTC(goTrigger, Player.STOP),
			new RTC(quitTrigger, Player.STOP) };

	static RTC[] startDemoRTC = { new RTC(goTrigger, Player.STOP) };

	/* ***************************************************************************** */
	/* *** 4 - Define the access path to the prompts ** */
	/* ***************************************************************************** */
	/**
	 * This basic application uses plain local files to store the prompts.
	 */
	static final String promptsRoot = "/mediafiles/voicemail/";
	static final URI welcomeMessage = URI.create(promptsRoot + "welcome.wav");
	static final URI byebyeMessage = URI.create(promptsRoot
			+ "byebyeMessage.wav");
	static final URI identificationMessage = URI.create(promptsRoot
			+ "identificationMessage.wav");
	static final URI youHaveNoMessage = URI.create(promptsRoot
			+ "youHaveNoMessage.wav");
	static final URI youHaveOneMessage = URI.create(promptsRoot
			+ "youHaveOneMessage.wav");
	static final URI youHaveMessages = URI.create(promptsRoot
			+ "youHaveMessages.wav");
	static final URI voiceMailMenu = URI.create(promptsRoot
			+ "voiceMailMenu.wav");
	static final URI userDestinationMessage = URI.create(promptsRoot
			+ "userDestinationMessage.wav");
	static final URI recordMessage = URI.create(promptsRoot
			+ "recordMessage.wav");
	static final URI alreadyConnectedUserMessage = URI.create(promptsRoot
			+ "alreadyConnectedUserMessage.wav");
	static final URI choiceListenMessage = URI.create(promptsRoot
			+ "choiceListenMessage.wav");
	static final URI silence = URI.create(promptsRoot + "silence.wav");

	/* ***************************************************************************** */
	/* *** 5 - Methods to play prompts, record messages, catch DTMFs ** */
	/* ***************************************************************************** */
	/*
	 * These are mostly wrappers on MediaGroup methods, adding exception
	 * handling.
	 */
	/* Very basic error handling: the call is released. */

	/**
	 * Play a message; The play may be interrupted by the given RTC's. <br>
	 * If anything goes wrong, release the session/call.
	 * 
	 * @param file
	 *            The message to play
	 * @param rtcs
	 *            The set of RunTimeControl's that can interrupt the playing.
	 */
	void playMessage(URI file, RTC[] rtcs) {
		try {
			myPlayer.play(file, rtcs, Parameters.NO_PARAMETER);
		} catch (Exception e) {
			log.error("Unexpected exception " + e, e);
			myCurrentState = released;
			release();
		}
	}

	/**
	 * Play a message, with the predefined <code>promptRTC</code> set of
	 * RunTimeControls.
	 */
	void playPrompt(URI file) {
		playMessage(file, promptRTC);
	}

	/**
	 * Record the caller's media stream into the given file. <br>
	 * The recording may be interrupted by the <code>recordRTC</code> set of
	 * RunTimeControls.
	 * 
	 * @param file
	 *            The file to record into.
	 */
	void recordMessage(URI file) {
		try {
			myRecorder.record(file, recordRTC, Parameters.NO_PARAMETER);
		} catch (Exception e) {
			log.error("Unexpected exception " + e, e);
			myCurrentState = released;
			release();
		}
	}

	/**
	 * Retrieve some DTMFs. <br>
	 * We do not wait for a given number of DTMFs, but rather on the matching of
	 * any of the given DTMF patterns.
	 * 
	 * @param patterns
	 *            an array of SignalRecorder.PATTERN[i] , indicating which matching
	 *            patterns should stop the transaction.
	 */
	void receiveSignals(Parameter[] patterns) {
		try {
			mySignalDetector.receiveSignals(-1, patterns, RTC.NO_RTC, Parameters.NO_PARAMETER);
		} catch (Exception e) {
			log.error("Unexpected exception " + e, e);
			myCurrentState = released;
			release();
		}
	}

	/* ***************************************************************************** */
	/*
	 * * 6 - Define a MediaGroup listener, that will call into the state machine
	 * when a media event pops up.
	 */
	/* ***************************************************************************** */
	class VoiceMailPlayerListener implements MediaEventListener<PlayerEvent> {
		public void onEvent(PlayerEvent event) {
			log.info("Player event" + event);
			EventType eventType = event.getEventType();

			if (eventType.equals(PlayerEvent.PLAY_COMPLETED)) {
				// The prompt is complete, either because we reach the end of
				// file,
				// or because is has been stopped .
				// Let's call the state machine, to know what to do next.
				myCurrentState.playComplete(event);
			} else if (eventType.equals(PlayerEvent.PAUSED)) {
				// No action needed here, this is purely informational
				log.info("Paused");
			} else if (eventType.equals(PlayerEvent.RESUMED)) {
				log.info("Resumed");
			}
		}
	}

	class VoiceMailRecorderListener implements MediaEventListener<RecorderEvent> {
		public void onEvent(RecorderEvent event) {
			log.info("Recorder event" + event);
			EventType ev = event.getEventType();

			if (ev.equals(RecorderEvent.RECORD_COMPLETED)) {
				// The recording is complete
				myCurrentState.RecordComplete(event);
			} else if (ev.equals(RecorderEvent.PAUSED)) {
				log.info("Paused");
			} else if (ev.equals(RecorderEvent.RESUMED)) {
				log.info("Resumed");
			}
		}
	}

	class VoiceMailSignalDetectorListener implements MediaEventListener<SignalDetectorEvent> {
		public void onEvent(SignalDetectorEvent event) {
			log.info("Signal Detector event" + event);
			EventType eventType = event.getEventType();

			if (eventType.equals(SignalDetectorEvent.FLUSH_BUFFER_COMPLETED)) {
				// No action needed here, this is purely informational
				log.info("Buffer flushed");
			} else if (eventType.equals(SignalDetectorEvent.RECEIVE_SIGNALS_COMPLETED)) {
				log.info("pattern detected");
				myCurrentState.sigDetectComplete(event);
			}
		}
	}

	/* ***************************************************************************** */
	/* *** 7 - Init, start, release ** */
	/* ***************************************************************************** */

	/*
	 * (non-Javadoc)
	 * 
	 * @see voicemail.VoiceMailSession#initDialog()
	 */
	@Override
	public void initDialog() throws Exception {
		// Create a MediaGroup, set it up and join it to the NetworkConnection.
		myMediaGroup = myMediaSession.createMediaGroup(MediaGroup.PLAYER_RECORDER_SIGNALDETECTOR);
		myPlayer = myMediaGroup.getPlayer();
		myPlayer.addListener(new VoiceMailPlayerListener());
		myRecorder = myMediaGroup.getRecorder();
		myRecorder.addListener(new VoiceMailRecorderListener());
		mySignalDetector = myMediaGroup.getSignalDetector();
		mySignalDetector.addListener(new VoiceMailSignalDetectorListener());

		myNetworkConnection.join(Joinable.Direction.DUPLEX, myMediaGroup);
		myMediaGroup.setParameters(patterns);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see voicemail.VoiceMailSession#startDialog(java.util.Map)
	 */
	@Override
	public void startDialog(Map<String, Object> params) {
		playMessage(welcomeMessage, startDemoRTC);
		myCurrentState.setState(welcome);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see voicemail.VoiceMailSession#terminateDialog()
	 */
	@Override
	public void terminateDialog() {
		myMediaSession.release();
	}

	/** Release on application's request */
	void release() {
		try {
			mySipSession.createRequest("BYE").send();
		} catch (Exception e) {
			log.fatal("Cannot send BYE", e);
		}
		super.release();
	}

	/* ***************************************************************************** */
	/* *** 8 - State machine managing the call progression ** */
	/* ***************************************************************************** */
	/**
	 * Each state is represented by a derived class of <code>CallFSM</code>.
	 * In the derived classes, the methods corresponding to allowed transitions,
	 * have been overloaded with appropriate code. <br>
	 * Contains little JSR309-related code: checking the reason (qualifier) in
	 * the completion events.
	 */
	class CallFSM {
		public final int state;
		URI firstMessageId = null;

		CallFSM(int aState) {
			state = aState;
		}

		// default actions: log an error and release the session
		CallFSM playComplete(PlayerEvent event) {
			log.error("Illegal state transition, releasing call");
			release();
			return null;
		}

		CallFSM RecordComplete(RecorderEvent event) {
			log.error("Illegal state transition, releasing call");
			release();
			return null;
		}

		CallFSM sigDetectComplete(SignalDetectorEvent event) {
			log.error("Illegal state transition, releasing call");
			release();
			return null;
		}

		CallFSM callPartyDisconnected() {
			log.error("Illegal state transition, releasing call");
			release();
			return null;
		}

		CallFSM setState(CallFSM newState) {
			if (myCurrentState != released && myCurrentState != newState) {
				log.info(" old state: " + myCurrentState.state + " new state: "
						+ newState.state);
				myCurrentState = newState;
			}
			return myCurrentState;
		}
	}

	final CallFSM welcome = new CallFSM(0) {
		// "welcome prompt" playing complete, retrieve DTMF pin numbers
		CallFSM playComplete(PlayerEvent event) {
			// the play has stopped because ...
			if (event.getQualifier().equals(PlayerEvent.RTC_TRIGGERED)) {
				// ... the action STOP has been executed:
				// let's get the DTMF pattern defined by "startDemo"
				receiveSignals(startDemo);
				return setState(welcome);
			} else {
				// ... we reach the end of file: let's start again
				playMessage(welcomeMessage, startDemoRTC);
				return setState(welcome);
			}
		}

		CallFSM sigDetectComplete(SignalDetectorEvent event) {
			// we got the "startDemo" DTMF pattern, let's ask the user his/her
			// pin code.
			playPrompt(identificationMessage);
			// change to the next state
			return setState(identification);
		}

		CallFSM callPartyDisconnected() {
			log.info("CallPartyDisconnected state ByeBye");
			release();
			return setState(released);
		}

	};

	CallFSM myCurrentState = welcome;

	// The user enters his/her pin code
	final CallFSM identification = new CallFSM(1) {
		CallFSM playComplete(PlayerEvent event) {
			log.info("playComplete in state \"identification\"");
			receiveSignals(userIdentification);
			return setState(identification);
		}

		CallFSM sigDetectComplete(SignalDetectorEvent event) {
			if (event.getQualifier().equals(userIdQualifier)) {
				userID = event.getSignalString();
				System.out.println("user identification: " + userID);
				boolean alreadyConnected;
				synchronized (connectedUsers) {
					alreadyConnected = connectedUsers.contains(userID);
				}
				log.warn("alreadyConnected= " + alreadyConnected);
				if (alreadyConnected) {
					playPrompt(alreadyConnectedUserMessage);
					return setState(alreadyConnectedstate);
				} else {
					synchronized (connectedUsers) {
						connectedUsers.add(userID);
					}
					log.info(" new user added");
					playPrompt(silence);
					return setState(status);
				}
			}
			playPrompt(identificationMessage);
			return setState(identification);
		}
	};

	final CallFSM alreadyConnectedstate = new CallFSM(2) {
		CallFSM playComplete(PlayerEvent event) {
			playMessage(welcomeMessage, startDemoRTC);
			return setState(welcome);
		}
	};

	final CallFSM status = new CallFSM(3) {
		CallFSM playComplete(PlayerEvent event) {
			int messageNumber = CoreMailboxMgr.getMailbox(userID)
					.getMessageNumber();
			log.info("Number of messages in mailbox :" + messageNumber);
			switch (messageNumber) {
			case 0:
				// no new message
				log.debug("Playing \"you have no message\"");
				playPrompt(youHaveNoMessage);
				return setState(entering_voiceMail);

			case 1:
				// One message has been previously stored in the mailbox
				// so play the associated prompt
				log.debug("Playing \"you have one message\"");
				playPrompt(youHaveOneMessage);
				return setState(entering_voiceMail);
			default:
				// 2 or more new messages
				log.debug("Playing \"you have messages\"");
				playPrompt(youHaveMessages);
				return setState(entering_voiceMail);
			}
		}
	};

	final CallFSM entering_voiceMail = new CallFSM(4) {
		CallFSM playComplete(PlayerEvent event) {
			playMessage(voiceMailMenu, menuRTC);
			return setState(VoiceMail);
		}
	};

	final CallFSM VoiceMail = new CallFSM(5) {
		CallFSM playComplete(PlayerEvent event) {
			log.info("playComplete in state VoiceMail");
			if (event.getQualifier().equals(PlayerEvent.RTC_TRIGGERED)) {
				log.debug("anEvent.getQualifier()=" + event.getQualifier());
				receiveSignals(menu);
				return setState(VoiceMail);
			} else {// reached the end of the file
				playMessage(voiceMailMenu, menuRTC);
				return setState(VoiceMail);
			}
		}

		CallFSM sigDetectComplete(SignalDetectorEvent event) {

			if (event.getQualifier().equals(sendMessageQualifier)) {
				log.debug("before play userDestinationMessage");
				playPrompt(userDestinationMessage);
				return setState(entering_userDestId);
			} else if (event.getQualifier().equals(listenMessageQualifier)) {
				log.debug("before play choiceListenMessage");
				playMessage(choiceListenMessage, enterlistenRTC);
				return setState(listening_menu);
			} else if (event.getQualifier().equals(quitQualifier)) {
				log.debug("before play byebyeMessage");
				playPrompt(byebyeMessage);
				return setState(ByeBye);
			}
			playPrompt(silence);
			return setState(status);

		}
	};

	// Entering the user destination Ids
	final CallFSM entering_userDestId = new CallFSM(6) {
		CallFSM playComplete(PlayerEvent event) {
			receiveSignals(destUserIdList);
			return setState(entering_userDestId);
		}

		CallFSM sigDetectComplete(SignalDetectorEvent event) {
			if (event.getQualifier().equals(userIdQualifier)) {
				userDestinationList.add(event.getSignalString());
				log.debug("added the user " + event.getSignalString()
						+ " in the destination mailboxes list");
				playPrompt(userDestinationMessage);
				return setState(entering_userDestId);
			} else if ((event.getQualifier().equals(endOfListQualifier))
					&& (userDestinationList.size() != 0)) {
				log.debug("before play recordMessage");
				playPrompt(recordMessage);
				return setState(send_Message);
			}
			playPrompt(userDestinationMessage);
			return setState(entering_userDestId);
		}
	};

	// The user asked to send/deposit a message
	final CallFSM send_Message = new CallFSM(7) {
		CallFSM playComplete(PlayerEvent event) {
			if (userDestinationList.size() > 0) {
				String firstDestUserId = userDestinationList.get(0);
				firstMessageId = CoreMailboxMgr.getMailbox(firstDestUserId)
						.newMessage();
				log.info("recording as message #" + firstMessageId
						+ " in the mailbox of user " + firstDestUserId
						+ " (first destination mailbox)");
				recordMessage(firstMessageId);
			}
			return setState(send_Message);
		}

		CallFSM RecordComplete(RecorderEvent event) {
			// The user has finished recording his/her message. Let's copy it
			// into the
			// destination mailbox(es).
			for (int ii = 1; ii < userDestinationList.size(); ii++) {
				String destUserId = userDestinationList.get(ii);
				CoreMailbox destMailbox = CoreMailboxMgr.getMailbox(destUserId);
				// creates a new unique message identifier
				URI messageId = destMailbox.newMessage();
				log.debug("copying to message #" + messageId + " of next user "
						+ destUserId);
				CoreMailboxMgr.copyMessage(firstMessageId, messageId);
				destMailbox.update();
				CoreMailboxMgr.releaseMailbox(destUserId);
			} // end of for
			userDestinationList.clear();
			playPrompt(silence);
			return setState(status);
		}
	};

	final CallFSM listening_menu = new CallFSM(8) {
		CallFSM playComplete(PlayerEvent event) {
			if (event.getQualifier().equals(PlayerEvent.RTC_TRIGGERED)) {
				receiveSignals(cont);
				return setState(listening_menu);
			} else { // probably reached the end of the file
				playMessage(choiceListenMessage, enterlistenRTC);
				return setState(listening_menu);
			}
		}

		CallFSM sigDetectComplete(SignalDetectorEvent event) {
			Qualifier qualifier = event.getQualifier();
			if (qualifier.equals(quitQualifier)) {
				log.info("Stop listening message. Return to main menu");
				current_msg = 0;
				playPrompt(silence);
				return setState(status);
			} else if ((qualifier.equals(goQualifier))
					&& (CoreMailboxMgr.getMailbox(userID).getMessageNumber() != 0)) {
				current_msg = 0;
				msgs = CoreMailboxMgr.getMailbox(userID).getMessages();
				log.info("Listening to message " + msgs[current_msg]);
				playMessage(msgs[current_msg], messageRTC);
				return setState(listening_message);
			} else if ((qualifier.equals(goQualifier))
					&& (CoreMailboxMgr.getMailbox(userID).getMessageNumber() == 0)) {
				playPrompt(silence);
				return setState(status);
			}
			playMessage(choiceListenMessage, enterlistenRTC);
			return setState(listening_menu);

		}
	};

	final CallFSM listening_message = new CallFSM(9) {

		CallFSM playComplete(PlayerEvent event) {
			receiveSignals(listen);
			return setState(listening_message);
		}

		CallFSM sigDetectComplete(SignalDetectorEvent event) {
			Qualifier qualifier = event.getQualifier();

			if (qualifier.equals(quitQualifier)) {
				log.info("Stop listening message. Return to state status");
				current_msg = 0;
				playPrompt(silence);
				return setState(status);

			} else if (qualifier.equals(replayMessageQualifier)) {
				// listen to the current message
				log.info("Replay message #" + current_msg);
				playMessage(msgs[current_msg], messageRTC);
				return setState(listening_message);

			} else if (qualifier.equals(skipMessageQualifier)) { // go to next message
				log.info("Skipping message #" + current_msg + " out of "
						+ CoreMailboxMgr.getMailbox(userID).getMessageNumber());
				CoreMailboxMgr.getMailbox(userID).removeMessage(
						msgs[current_msg]);
				current_msg++;
				if (current_msg < msgs.length) {
					playMessage(msgs[current_msg], messageRTC);
					return setState(listening_message);
				} else {
					CoreMailboxMgr.getMailbox(userID).update();
					CoreMailboxMgr.releaseMailbox(userID);
					current_msg = 0;
					playPrompt(silence);
					return setState(status);
				}
			}
			playPrompt(silence);
			return setState(status);

		}
	};

	final CallFSM ByeBye = new CallFSM(10) {
		CallFSM playComplete(PlayerEvent event) {
			log.info("byebye");
			connectedUsers.remove(userID);
			release();
			return setState(released);
		}

		CallFSM callPartyDisconnected() {
			log.info("CallPartyDisconnected state ByeBye");
			return setState(released);
		}
	};

	final CallFSM released = new CallFSM(11) {
	};
}