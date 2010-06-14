package com.micromethod.sipmethod.sample.userjoin;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.sip.SipSession;
import javax.servlet.sip.URI;

/**
 * 
 * this class represent a user who is calling with others.
 *
 */
public class User {
	/**
	 * user's Address-of-Record.
	 */
	private String aor;
	/**
	 * user's current contact address.
	 */
	private URI address;
	
	/**
	 * 
	 */
	private Map<SipSession,SipSession> peerSessions = new HashMap<SipSession,SipSession>();
	

	public String getAor() {
		return aor;
	}

	public void setAor(String aor) {
		this.aor = aor;
	}

	public URI getAddress() {
		return address;
	}
	
	public void setAddress(URI address) {
		this.address = address;
	}

	public void addSession(SipSession ownSession,SipSession peerSession) {
		peerSessions.put(ownSession, peerSession);
	}

	public void delSession(SipSession ownSession) {
		peerSessions.remove(ownSession);
	}

}
