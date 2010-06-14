package com.voxeo.prism.sample;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.media.mscontrol.EventType;
import javax.media.mscontrol.MediaConfigException;
import javax.media.mscontrol.MediaEventListener;
import javax.media.mscontrol.MediaSession;
import javax.media.mscontrol.MsControlException;
import javax.media.mscontrol.MsControlFactory;
import javax.media.mscontrol.Parameter;
import javax.media.mscontrol.Parameters;
import javax.media.mscontrol.join.Joinable.Direction;
import javax.media.mscontrol.mediagroup.MediaGroup;
import javax.media.mscontrol.mediagroup.PlayerEvent;
import javax.media.mscontrol.mediagroup.Recorder;
import javax.media.mscontrol.mediagroup.RecorderEvent;
import javax.media.mscontrol.mediagroup.SpeechDetectorConstants;
import javax.media.mscontrol.mediagroup.signals.SignalDetector;
import javax.media.mscontrol.mediagroup.signals.SignalDetectorEvent;
import javax.media.mscontrol.mediagroup.signals.SpeechRecognitionEvent;
import javax.media.mscontrol.networkconnection.NetworkConnection;
import javax.media.mscontrol.networkconnection.SdpPortManagerEvent;
import javax.media.mscontrol.networkconnection.SdpPortManagerException;
import javax.media.mscontrol.resource.RTC;
import javax.media.mscontrol.spi.DriverManager;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.sip.SipFactory;
import javax.servlet.sip.SipServlet;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipSession;
import javax.servlet.sip.SipURI;
import javax.servlet.sip.URI;

/**
 * @author zhuwillie
 */
public class PrismServlet extends SipServlet {

  private static final long serialVersionUID = -5920362399748093984L;

  protected SipFactory _sipFactory = null;

  protected MsControlFactory _msControlFactory;

  // Each incoming call goes through the following states:
  public final static String SESSIONSTATE_WAITING_FOR_MEDIA_SERVER = "WAITING_FOR_MEDIA_SERVER";

  public final static String SESSIONSTATE_WAITING_FOR_ACK = "WAITING_FOR_ACK";

  // (only if the initial INVITE had no SDP offer)
  public final static String SESSIONSTATE_WAITING_FOR_MEDIA_SERVER_2 = "WAITING_FOR_MEDIA_SERVER_2";

  public final static String SESSIONSTATE_DIALOG_START = "SESSIONSTATE_DIALOG_START";

  public final static String SESSIONSTATE_DIALOG_MAINMENU = "SESSIONSTATE_DIALOG_MAINMENU";

  public final static String SESSIONSTATE_DIALOG_TTSFUNCTION = "SESSIONSTATE_DIALOG_TTSFUNCTION";

  public final static String SESSIONSTATE_DIALOG_RECORDFUNCTION = "SESSIONSTATE_DIALOG_RECORDFUNCTION";

  public final static String ADDRESSES_ATTRIBUTE_NAME = "com.voxeo.prism.sample.ivr.Addresses";

  public final static String SESSIONS_ATTRIBUTE_NAME = "com.voxeo.prism.sample.ivr.Sessiones";

  /**
   * init() initializes the servelt.
   */
  @Override
  public void init() throws ServletException {
    super.init();
    // Create addresses map and put it in context attribute.
    final ServletContext context = getServletContext();
    final Map<String, URI> addresses = new ConcurrentHashMap<String, URI>();

    final Map<String, SipSession> sessions = new ConcurrentHashMap<String, SipSession>();

    context.setAttribute(ADDRESSES_ATTRIBUTE_NAME, addresses);

    context.setAttribute(SESSIONS_ATTRIBUTE_NAME, sessions);
    _sipFactory = (SipFactory) getServletContext().getAttribute(SIP_FACTORY);

    try {
      // create the Media Session Factory
      _msControlFactory = DriverManager.getDrivers().next().getFactory(null);
    }
    catch (final Exception e) {
      throw new ServletException(e);
    }
  }

  /**
   * Invoked for SIP REGISTER requests, which are sent by X-Lite for sign-in and
   * sign-off.
   */
  @Override
  protected void doRegister(final SipServletRequest req) throws IOException, ServletException {
    // get addresses map from context attribute.
    final ServletContext context = getServletContext();
    final Map<String, URI> addresses = (Map<String, URI>) context.getAttribute(ADDRESSES_ATTRIBUTE_NAME);

    final String aor = req.getFrom().getURI().toString().toLowerCase();

    int expire = req.getExpires();
    if (expire == -1) {
      expire = req.getAddressHeader("Contact").getExpires();
    }

    // The non-zero value of Expires header indicates a sign-in.
    if (expire > 0) {
      // Keep the name/address mapping.
      addresses.put(aor, req.getAddressHeader("Contact").getURI());

      // reset addresses map in context attribute for replication.
      context.setAttribute(ADDRESSES_ATTRIBUTE_NAME, addresses);
    }
    else {
      // The zero value of Expires header indicates a sign-off.
      // Remove the name/address mapping.
      addresses.remove(aor);

      // reset addresses map in context attribute for replication.
      context.setAttribute(ADDRESSES_ATTRIBUTE_NAME, addresses);
    }

    // We accept the sign-in or sign-off by returning 200 OK response.
    req.createResponse(SipServletResponse.SC_OK).send();

    req.getApplicationSession().invalidate();
  }

  /**
   * Invoked for SIP INVITE requests, which are sent by X-Lite to establish a
   * chat session.
   */
  @Override
  protected void doInvite(final SipServletRequest req) throws IOException, ServletException {
    NetworkConnection conn = null;

    final SipSession sipSession = req.getSession();
    // set the contact used to send out IM message in tts function.
    sipSession.setAttribute("RemoteContact", req.getAddressHeader("Contact").getURI());

    if (req.isInitial()) {
      // New Call
      try {
        // Create new media session and store in SipSession
        final MediaSession mediaSession = _msControlFactory.createMediaSession();
        sipSession.setAttribute("MEDIA_SESSION", mediaSession);
        mediaSession.setAttribute("SIP_SESSION", sipSession);

        // Create a new NetworkConnection and store in SipSession
        conn = mediaSession.createNetworkConnection(NetworkConnection.BASIC);
        // Set this servlet class as listener of the RTP ports manager
        conn.getSdpPortManager().addListener(new SdpPortsListener());
        sipSession.setAttribute("NETWORK_CONNECTION", conn);
      }
      catch (final MediaConfigException e) {
        req.createResponse(SipServletResponse.SC_SERVER_INTERNAL_ERROR).send();
        return;
      }
      catch (final MsControlException e) {
        // Probably out of resources, or other media server problem. send 503
        req.createResponse(SipServletResponse.SC_SERVICE_UNAVAILABLE).send();
        return;
      }
    }
    else {
      // Existing call. This is an re-INVITE
      // Get NetworkConnection from SipSession
      conn = (NetworkConnection) sipSession.getAttribute("NETWORK_CONNECTION");
    }

    // set SDP of peer UA to NetworkConnection ()
    try {
      // Store INVITE so it can be responded to later
      sipSession.setAttribute("UNANSWERED_INVITE", req);

      // set state
      setState(sipSession, SESSIONSTATE_WAITING_FOR_MEDIA_SERVER);

      // assume here that the only possible body is an SDP
      // may be null, indicating an INVITE w/o SDP
      final byte[] sdpOffer = req.getRawContent();
      if (sdpOffer == null) {
        conn.getSdpPortManager().generateSdpOffer();
      }

      else {
        conn.getSdpPortManager().processSdpOffer(sdpOffer);
      }

    }
    catch (final SdpPortManagerException e) {
      // Unknown exception, just send 503
      req.createResponse(SipServletResponse.SC_SERVICE_UNAVAILABLE).send();
      return;
    }
    catch (final MsControlException e) {
      // Unknown exception, just send 503
      req.createResponse(SipServletResponse.SC_SERVICE_UNAVAILABLE).send();
      return;
    }
  }

  @Override
  protected void doAck(final SipServletRequest req) throws ServletException, IOException {
    final SipSession sipSession = req.getSession();
    // Get NetworkConnection from SipSession
    final NetworkConnection conn = (NetworkConnection) sipSession.getAttribute("NETWORK_CONNECTION");

    // Check if ACK contains an SDP (assume here that the only possible body is
    // an SDP)
    final byte[] remoteSdp = req.getRawContent();
    if (remoteSdp != null) {
      // ACK contains an SDP, should be an answer.
      // set SDP of peer UA.
      try {
        // set state
        setState(sipSession, SESSIONSTATE_WAITING_FOR_MEDIA_SERVER_2);

        conn.getSdpPortManager().processSdpAnswer( // The local (media server)
            // side has already been set
            remoteSdp); // remote side is the peer UA
      }
      catch (final Exception e) {
        // Not much to do. Hope for the best and carry on.
        log(e.getMessage());
      }
    }

    final String aor = req.getFrom().getURI().toString().toLowerCase();
    ((Map<String, SipSession>) getServletContext().getAttribute(SESSIONS_ATTRIBUTE_NAME)).put(aor, req.getSession());

    if (compareState(sipSession, SESSIONSTATE_WAITING_FOR_ACK)) {
      runDialog(sipSession);
    }
  }

  /**
   * Invoked for SIP MESSAGE requests, which are sent by X-Lite for instant
   * messages.
   */
  @Override
  protected void doMessage(final SipServletRequest req) throws IOException, ServletException {

    // We accept the instant message by returning 200 OK response.
    req.createResponse(SipServletResponse.SC_OK).send();

    if (req.getContentType().equalsIgnoreCase("text/plain") || req.getContentType().equalsIgnoreCase("text/html")) {

      final String aor = req.getFrom().getURI().toString().toLowerCase();
      final SipSession callSession = ((Map<String, SipSession>) getServletContext().getAttribute(
          SESSIONS_ATTRIBUTE_NAME)).get(aor);

      // if it is in the TTS test step.
      if (callSession != null && compareState(callSession, SESSIONSTATE_DIALOG_TTSFUNCTION)) {
        final String text = getContent(req);
        if (text != null && text.length() > 0) {
          final MediaGroup mediaGroup = (MediaGroup) callSession.getAttribute("MEDIAGROUP");

          try {
            if (text.equalsIgnoreCase("exit") || text.equalsIgnoreCase("quit") || text.equalsIgnoreCase("bye")) {
              mediaGroup.getPlayer().play(new java.net.URI[] {createTTSResource("Now return to main menu.")},
                  RTC.NO_RTC, null);
              mainMenu(callSession);
            }
            else {
              mediaGroup.getPlayer()
                  .play(new java.net.URI[] {createTTSResource("You typed "), createTTSResource(text + ".")},
                      RTC.NO_RTC, null);
            }

          }
          catch (final MsControlException e) {
            e.printStackTrace();
            terminate(req.getSession());
          }
        }
      }
      // else just echo the message.
      else {
        // get addresses map from context attribute.
        final ServletContext context = getServletContext();
        final Map<String, URI> addresses = (Map<String, URI>) context.getAttribute(ADDRESSES_ATTRIBUTE_NAME);

        // Create an echo SIP MESSAGE request with the same content.
        final SipServletRequest echo = req.getSession().createRequest("MESSAGE");

        final String charset = req.getCharacterEncoding();
        if (charset != null) {
          echo.setCharacterEncoding(charset);
        }

        // Get the previous registered address for the sender.
        SipURI uri = (SipURI) addresses.get(req.getFrom().getURI().toString().toLowerCase());
        if (uri == null) {
          echo.setContent("You haven't registered", "text/plain");
          if (req.getAddressHeader("Contact") != null) {
            uri = (SipURI) req.getAddressHeader("Contact").getURI();
          }
          else {
            final SipURI requesturi = (SipURI) req.getRequestURI();
            final String user = requesturi.getUser();
            uri = _sipFactory.createSipURI(user, req.getRemoteAddr());
            uri.setPort(req.getRemotePort());
            uri.setTransportParam(req.getTransport());
          }
        }
        else {
          echo.setContent(req.getContent(), req.getContentType());
        }
        echo.setRequestURI(uri);

        // Send the echo MESSAGE request back to Windows Messenger.
        echo.send();
      }
    }

    req.getApplicationSession().invalidate();
  }

  @Override
  protected void doBye(final SipServletRequest req) throws ServletException, IOException {
    // release media session.
    final MediaSession mediaSession = (MediaSession) req.getSession().getAttribute("MEDIA_SESSION");
    MediaGroup mediaGroup = (MediaGroup) req.getSession().getAttribute("MEDIAGROUP");
    if (mediaGroup != null) {
      mediaGroup.release();
    }
    if (mediaSession != null) {
      mediaSession.release();
    }

    // clear the sessions map.
    final String aor = req.getSession().getRemoteParty().getURI().toString().toLowerCase();
    ((Map<String, SipSession>) getServletContext().getAttribute(SESSIONS_ATTRIBUTE_NAME)).remove(aor);

    req.createResponse(200).send();
  }

  // used to process subscribe request, just return 200 ok.
  @Override
  protected void doSubscribe(SipServletRequest sipservletrequest) throws ServletException, IOException {
    sipservletrequest.createResponse(200).send();

    // send a simple presence message.
    SipServletRequest notify = sipservletrequest.getSession().createRequest("NOTIFY");
    notify.addHeader("Event", "presence");
    notify.addHeader("Subscription-State", "active");
    String pidf = "<?xml version='1.0' encoding='UTF-8'?><impp:presence xmlns:impp='urn:ietf:params:xml:ns:pidf'>"
        + "<impp:tuple id='sg89ae'>" + "<impp:status>" + "<impp:basic>open</impp:basic>" + "</impp:status>"
        + "</impp:tuple>" + "</impp:presence>";

    notify.setContent(pidf.getBytes(), "application/pidf+xml");
    notify.send();

    sipservletrequest.getSession().invalidate();
  }

  // used to listen to SdpPortManagerEvent to negotiate media session with
  // client.
  private class SdpPortsListener implements MediaEventListener<SdpPortManagerEvent> {
    public void onEvent(final SdpPortManagerEvent event) {
      final MediaSession mediaSession = event.getSource().getMediaSession();

      final SipSession sipSession = (SipSession) mediaSession.getAttribute("SIP_SESSION");

      final SipServletRequest inv = (SipServletRequest) sipSession.getAttribute("UNANSWERED_INVITE");
      sipSession.removeAttribute("UNANSWERED_INVITE");

      try {
        if (event.isSuccessful()) {
          if (compareState(sipSession, SESSIONSTATE_WAITING_FOR_MEDIA_SERVER)) {
            // Return an SDP attached to a 200 OK message
            final SipServletResponse resp = inv.createResponse(SipServletResponse.SC_OK);
            // Get SDP from NetworkConnection
            final byte[] sdp = event.getMediaServerSdp();
            resp.setContent(sdp, "application/sdp");
            // Send 200 OK
            resp.send();
            setState(sipSession, SESSIONSTATE_WAITING_FOR_ACK);
          }
          else if (compareState(sipSession, SESSIONSTATE_WAITING_FOR_MEDIA_SERVER_2)) {
            // The media server has updated the remote SDP received with the
            // ACK.
            // The INVITE is complete, we are ready to play.
            runDialog(sipSession);
          }
        }
        else {
          if (SdpPortManagerEvent.SDP_NOT_ACCEPTABLE.equals(event.getError())) {
            // Send 488 error response to INVITE
            inv.createResponse(SipServletResponse.SC_NOT_ACCEPTABLE_HERE).send();
          }
          else if (SdpPortManagerEvent.RESOURCE_UNAVAILABLE.equals(event.getError())) {
            // Send 486 error response to INVITE
            inv.createResponse(SipServletResponse.SC_BUSY_HERE).send();
          }
          else {
            // Some unknown error. Send 500 error response to INVITE
            inv.createResponse(SipServletResponse.SC_SERVER_INTERNAL_ERROR).send();
          }
          // Clean up media session
          sipSession.removeAttribute("MEDIA_SESSION");
          mediaSession.release();
        }
      }
      catch (final Exception e) {
        e.printStackTrace();
        // Clean up
        terminate(sipSession);
      }
    }
  }

  // start dialog
  private void runDialog(final SipSession sipSession) {
    MediaSession mediaSession = null;
    try {
      mediaSession = (MediaSession) sipSession.getAttribute("MEDIA_SESSION");
      MediaGroup mg = null;
      mg = (MediaGroup) sipSession.getAttribute("MEDIAGROUP");
      if (mg == null) {
        // Create a MediaGroup
        mg = mediaSession.createMediaGroup(MediaGroup.PLAYER_RECORDER_SIGNALDETECTOR);
        // Save reference for future use
        sipSession.setAttribute("MEDIAGROUP", mg);
        // Attach a listener to the SignalDetector, player and recorder.
        mg.getSignalDetector().addListener(new SignalDetectorListener());
        mg.getPlayer().addListener(new PlayerListener());
        mg.getRecorder().addListener(new RecorderListener());
        // Join it to the NetworkConnection
        mg.join(Direction.DUPLEX, (NetworkConnection) sipSession.getAttribute("NETWORK_CONNECTION"));
      }

      setState(sipSession, SESSIONSTATE_DIALOG_START);

      mg.getPlayer().play(createTTSResource("Welcome to Voxeo Prism Test Application"), RTC.NO_RTC, null);
    }
    catch (final Exception e) {
      e.printStackTrace();
      terminate(sipSession);
    }
  }

  private void mainMenu(final SipSession sipSession) {
    setState(sipSession, SESSIONSTATE_DIALOG_MAINMENU);
    // Indicate the speech grammar
    final MediaGroup mediaGroup = (MediaGroup) sipSession.getAttribute("MEDIAGROUP");
    try {
      final Parameters collectOptions = mediaGroup.createParameters();

      collectOptions.put(SignalDetector.PATTERN[0], "1,2");
      collectOptions.put(SignalDetector.PATTERN[1], creatASRGrammar());
      collectOptions.put(SpeechDetectorConstants.SENSITIVITY, 1);
      // play prompt
      collectOptions.put(SignalDetector.PROMPT,
          createTTSResource("Press or say  1 for testing TTS, Press or say 2 for testing Recording"));

      mediaGroup.getSignalDetector().receiveSignals(-1,
          new Parameter[] {SignalDetector.PATTERN[0], SignalDetector.PATTERN[1]}, RTC.NO_RTC, collectOptions);
    }
    catch (final Exception ex) {
      ex.printStackTrace();
      terminate(sipSession);
    }
  }

  private void ttsFunction(final SipSession sipSession) {
    try {
      setState(sipSession, SESSIONSTATE_DIALOG_TTSFUNCTION);

      // send a prompt message to client.
      sendMessage(sipSession, "Please type your message. Type exit, quit or bye to return to the main menu.");
    }
    catch (final Exception e) {
      e.printStackTrace();
      terminate(sipSession);
    }
  }

  private void recordFunction(final SipSession sipSession) {
    try {
      setState(sipSession, SESSIONSTATE_DIALOG_RECORDFUNCTION);

      final MediaGroup mediaGroup = (MediaGroup) sipSession.getAttribute("MEDIAGROUP");

      // detect '#' signal to stop record.
      final Parameters collectOptions = mediaGroup.createParameters();
      collectOptions.put(SignalDetector.PATTERN[0], "#");
      mediaGroup.getSignalDetector().receiveSignals(-1, new Parameter[] {SignalDetector.PATTERN[0]}, RTC.NO_RTC,
          collectOptions);

      // record
      final Parameters options = mediaGroup.createParameters();
      options.put(Recorder.START_BEEP, true);
      options.put(SpeechDetectorConstants.BARGE_IN_ENABLED, false);
      options.put(Recorder.PROMPT,
          createTTSResource("Please record your message after the beep. Press hash to stop record."));

      // construct record file location.
      final String path = sipSession.getServletContext().getRealPath(
          ((SipURI) sipSession.getRemoteParty().getURI()).getUser() + "_" + new Date().getTime() + "_Recording.au");

      File file = new File(path);
      final java.net.URI record = file.toURI();

      sipSession.setAttribute("RecordFileLocation", record);

      mediaGroup.getRecorder().record(record, RTC.NO_RTC, options);
    }
    catch (final Exception e) {
      e.printStackTrace();
      terminate(sipSession);
    }
  }

  // used to listen to signal detector event.
  class SignalDetectorListener implements MediaEventListener<SignalDetectorEvent> {
    public void onEvent(final SignalDetectorEvent event) {

      final EventType type = event.getEventType();

      final MediaSession mediaSession = event.getSource().getMediaSession();
      final SipSession sipSession = (SipSession) mediaSession.getAttribute("SIP_SESSION");
      final MediaGroup mediaGroup = (MediaGroup) sipSession.getAttribute("MEDIAGROUP");

      if (type == SignalDetectorEvent.RECEIVE_SIGNALS_COMPLETED
          && (event.getQualifier() == SignalDetectorEvent.PATTERN_MATCHING[0] || event.getQualifier() == SignalDetectorEvent.PATTERN_MATCHING[1])) {
        // in main menu.
        if (compareState(sipSession, SESSIONSTATE_DIALOG_MAINMENU)) {
          // enter TTS function
          if (event instanceof SpeechRecognitionEvent
              && ((SpeechRecognitionEvent) event).getUserInput().equalsIgnoreCase("one")
              || event.getSignalString() != null && event.getSignalString().equalsIgnoreCase("1")) {

            ttsFunction(sipSession);
          }
          // enter record function
          else if (event instanceof SpeechRecognitionEvent
              && ((SpeechRecognitionEvent) event).getUserInput().equalsIgnoreCase("two")
              || event.getSignalString() != null && event.getSignalString().equalsIgnoreCase("2")) {

            recordFunction(sipSession);
          }
        }
        // in TTS function, if received 0, return to main memu.
        else if (compareState(sipSession, SESSIONSTATE_DIALOG_TTSFUNCTION)
            && (event instanceof SpeechRecognitionEvent
                && ((SpeechRecognitionEvent) event).getUserInput().equalsIgnoreCase("zero") || event.getSignalString()
                .equalsIgnoreCase("0"))) {
          setState(sipSession, SESSIONSTATE_DIALOG_MAINMENU);

          mainMenu(sipSession);

        }

        // in record function if received #, stop record.
        else if (compareState(sipSession, SESSIONSTATE_DIALOG_RECORDFUNCTION) && event.getSignalString() != null
            && event.getSignalString().equalsIgnoreCase("#")) {
          try {
            mediaGroup.getRecorder().stop();
          }
          catch (final MsControlException e) {
            e.printStackTrace();
            terminate(sipSession);
          }
        }
      }
    }
  }

  // used to listen to the player event.
  protected class PlayerListener implements MediaEventListener<PlayerEvent> {
    public void onEvent(final PlayerEvent e) {
      final EventType type = e.getEventType();
      if (type == PlayerEvent.PLAY_COMPLETED) {
        final MediaSession mediaSession = e.getSource().getMediaSession();
        final SipSession sipSession = (SipSession) mediaSession.getAttribute("SIP_SESSION");

        if (compareState(sipSession, SESSIONSTATE_DIALOG_START)
            || compareState(sipSession, SESSIONSTATE_DIALOG_RECORDFUNCTION)) {
          // enter main menu.
          mainMenu(sipSession);
        }
      }
    }
  }

  // used to listen to recorder event.
  protected class RecorderListener implements MediaEventListener<RecorderEvent> {
    public void onEvent(final RecorderEvent e) {

      final EventType t = e.getEventType();
      if (t == RecorderEvent.RECORD_COMPLETED) {
        final MediaSession mediaSession = e.getSource().getMediaSession();
        final SipSession sipSession = (SipSession) mediaSession.getAttribute("SIP_SESSION");
        final MediaGroup mediaGroup = (MediaGroup) sipSession.getAttribute("MEDIAGROUP");
        try {
          // play the recorded.
          mediaGroup.getPlayer().play(
              new java.net.URI[] {createTTSResource("Here is what you said"),
                  (java.net.URI) sipSession.getAttribute("RecordFileLocation")}, RTC.NO_RTC, null);

          // enter main menu.
          mainMenu(sipSession);
        }
        catch (final Exception ex) {
          ex.printStackTrace();
          terminate(sipSession);
        }
      }
    }
  }

  // terminate sip session, release related resource.
  private void terminate(final SipSession sipSession) {
    try {
      // send a bye request to client.
      final SipServletRequest byeReq = sipSession.createRequest("BYE");
      byeReq.send();

      // release media session.
      final MediaSession mediaSession = (MediaSession) sipSession.getAttribute("MEDIA_SESSION");
      MediaGroup mediaGroup = (MediaGroup) sipSession.getAttribute("MEDIAGROUP");
      if (mediaGroup != null) {
        mediaGroup.release();
      }
      if (mediaSession != null) {
        mediaSession.release();
      }

      sipSession.invalidate();

      // remove this session from session map.
      final String aor = sipSession.getRemoteParty().getURI().toString().toLowerCase();
      ((Map<String, SipSession>) getServletContext().getAttribute(SESSIONS_ATTRIBUTE_NAME)).remove(aor);
    }
    catch (final Exception e) {
      e.printStackTrace();
    }
  }

  private void setState(final SipSession sipSession, final String state) {
    sipSession.setAttribute("STATE", state);
  }

  private boolean compareState(final SipSession sipSession, final String state) {
    return state.equals(sipSession.getAttribute("STATE"));
  }

  private static final Pattern P = Pattern.compile("<[^>]+>([^<]+)<[^>]+>");

  // get text content in a sip request.
  private String getContent(final SipServletRequest sipReq) throws UnsupportedEncodingException, IOException {
    String s = sipReq.getContent().toString();
    if (sipReq.getContentType().equalsIgnoreCase("text/html")) {
      final Matcher m = P.matcher(s);
      String a = "";
      while (m.find()) {
        a += m.group(1);
      }
      s = a;
    }

    // used to get the exit command: quit, exit, bye.
    s = s.trim();
    if (s.endsWith("\r\n")) {
      s = s.substring(0, s.length() - 2);
      s = s.trim();
    }
    return s;
  }

  // send a message to a sip client.
  private void sendMessage(final SipSession session, final String message) throws IOException {
    // get addresses map from context attribute.
    final Map<String, SipURI> addresses = (Map<String, SipURI>) getServletContext().getAttribute(
        ADDRESSES_ATTRIBUTE_NAME);

    // Create an echo SIP MESSAGE request with the content.
    final SipServletRequest echo = _sipFactory.createRequest(session.getApplicationSession(), "MESSAGE", session
        .getLocalParty().getURI(), session.getRemoteParty().getURI());

    // Get the previous registered address for the sender.
    SipURI uri = addresses.get(session.getRemoteParty().getURI().toString().toLowerCase());

    if (uri == null) {
      uri = (SipURI) session.getAttribute("RemoteContact");
    }
    echo.setContent(message, "text/plain");

    echo.setRequestURI(uri);

    // Send the echo MESSAGE request to client.
    echo.send();
  }

  // create ssml uri used for TTS.
  private java.net.URI createTTSResource(final String text) throws UnsupportedEncodingException {
    return java.net.URI.create("data:"
        + URLEncoder.encode("application/ssml+xml," + "<?xml version=\"1.0\"?>" + "<speak>" + "<voice>" + text
            + "</voice>" + "</speak>", "UTF-8"));
  }

  private java.net.URI creatASRGrammar() throws UnsupportedEncodingException {
    return java.net.URI.create("data:"
        + URLEncoder.encode("application/srgs+xml," + "<?xml version=\"1.0\"?>" + "<grammar mode=\"voice\">"
            + "<rule scope=\"public\">" + "<one-of>" + "<item> one </item>" + "<item> two </item>" + "</one-of>"
            + "</rule>" + "</grammar>", "UTF-8"));
  }
}
