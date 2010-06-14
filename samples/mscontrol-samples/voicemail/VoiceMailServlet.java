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

import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.sip.SipServlet;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipSession;

/**
 * Call Management for the voicemail application. <br>
 * Since a voicemail is not playing any tricks with SIP, this is fairly basic.
 * 
 */
public class VoiceMailServlet extends SipServlet {
	final static long serialVersionUID = -1;

	Map<String, Object> params = new HashMap<String, Object>();

	/** Taking a new incoming call, step 1: INVITE */
	@Override
	protected void doInvite(final SipServletRequest req)
			throws ServletException {
		SipSession sipSession = req.getSession();
		if (req.isInitial()) {
			params.put("remote.uri", req.getHeader("from"));
			params.put("local.uri", req.getHeader("to"));
			// Create a media session, giving it a reference to this SipSession
			VoiceMailSession service;
			String remoteUri = (String) params.get("remote.uri");
			// Select type of dialog, here faked based on user-info
			if (remoteUri != null && remoteUri.endsWith("priviledged customer"))
				service = new VxmlMailSession(sipSession);
			else
				service = new CoreMailSession(sipSession);
			// Store media session reference
			sipSession.setAttribute("media-service", service);
			try {
				// Initialize mediasession and networkconnection
				service.init(req);
				// Initialize the dialog (this part is dialog-type dependent)
				service.initDialog();
			} catch (Exception e) {
				service.release();
				throw new ServletException(e);
			}
		}
	}

	/** Taking a new call, step 2: ACK is received, the party is INVITE'd */
	@Override
	protected void doAck(SipServletRequest req) throws ServletException {

		final CoreMailSession service = (CoreMailSession) req.getSession().getAttribute("media-service");
		// Start playing
		service.startDialog(params);
	}

	/** released by the network */
	@Override
	protected void doBye(SipServletRequest req) throws ServletException {
		final CoreMailSession service = (CoreMailSession) req.getSession().getAttribute("media-service");
		service.terminateDialog();
	}

}
