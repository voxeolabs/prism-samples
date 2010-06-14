package com.micromethod.sipmethod.sample.userjoin;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.ServletException;
import javax.servlet.sip.SipServlet;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipSession;
import javax.servlet.sip.SipURI;
import javax.servlet.sip.URI;

/**
 * UserJoinServlet provides a simple example of a SIP servlet.<br/> 
 * 3-way Conference (Third Party Joins) According to the SIP Service Example from http://www.tech-invite.com.
 */
public class UserJoinServlet extends SipServlet {

	private static final long serialVersionUID = -8776306340340837021L;
	
	/**
	 * map used to save user register name/address information.
	 */
	ConcurrentHashMap<String,URI> addresses = new ConcurrentHashMap<String,URI>();	
	
	/**
	 * Invoked for SIP REGISTER requests, which are sent by a UAC for
	 * sign-in and sign-off.
	 */
	@Override
	protected void doRegister(SipServletRequest req)
			throws ServletException, IOException {	
		
		// get user's AOR
		final String aor = req.getFrom().getURI().toString().toLowerCase();
		// The non-zero value of Expires header indicates a sign-in.
		if(req.getExpires() != 0){
			//put the user/address to the addresses map.
			addresses.put(aor, req.getAddressHeader("Contact").getURI());			
		}
		else{ // The zero value of Expires header indicates a sign-off.
			// remove the user from the map.
			addresses.remove(aor);			
		}
		// return a 200/OK response.
		req.createResponse(SipServletResponse.SC_OK).send();
	}
	
	/**
	 * Invoked for SIP INVITE requests.
	 */
	@Override
	protected void doInvite(SipServletRequest req)
			throws ServletException, IOException {

		// get caller address from addresses map.
		SipURI uriCaller = null;
		uriCaller = (SipURI)addresses.get(req.getFrom().getURI().toString().toLowerCase());		
		if(uriCaller == null){
			// reject the request if it is not from a registered user.
			req.createResponse(SipServletResponse.SC_FORBIDDEN).send();
			return;
		}
		
		// get callee address from addresses map.
		SipURI uriCallee = null;
		uriCallee = (SipURI)addresses.get(req.getTo().getURI().toString().toLowerCase());		
		if(uriCallee == null){
			// Reject the message if the callee has not registered.
			req.createResponse(SipServletResponse.SC_TEMPORARLY_UNAVAILABLE).send();
			return;
		}
		
		if(req.isInitial()){//if it is initial INVITE.
			// Relay the request from the caller to the callee.
			// create new request.
			SipServletRequest bReq = req.getB2buaHelper().createRequest(req,true,null);
			bReq.setRequestURI(uriCallee);
			bReq.setContent(req.getRawContent(), req.getContentType());
			bReq.send();	
		}		
		else{// if it is re-INVITE.
			// Relay the request from the caller to the callee.
			// get the peer session.
			SipSession peerSession = req.getB2buaHelper().getLinkedSession(req.getSession());
			// create request from session.
			SipServletRequest bReq = req.getB2buaHelper().createRequest(peerSession,req,null);
			bReq.setRequestURI(uriCallee);
			bReq.setContent(req.getRawContent(), req.getContentType());
			bReq.send();
		}				
	}
		
	/**
	 * Invoked for SIP 1xx class responses.
	 */
	@Override
	protected void doProvisionalResponse(SipServletResponse resp)
			throws ServletException, IOException {

		// relay the provision response to the peer session.		
		// get the peer request.
		SipServletRequest aReq = resp.getRequest().getB2buaHelper().getLinkedSipServletRequest(resp.getRequest());
		// create response and send .
		if(aReq!=null){
			SipServletResponse aResp = aReq.createResponse(resp.getStatus(),resp.getReasonPhrase());
			aResp.setContent(resp.getRawContent(), resp.getContentType());	
			aResp.send();
		}	
	}
	
	/**
	 * Invoked for SIP 4xx-6xx class responses.
	 */
	@Override
	protected void doErrorResponse(SipServletResponse resp)
			throws ServletException, IOException {

		// if the response is for INVITE request.
		if(resp.getMethod().equals("INVITE")){			
			// Keep the reference to the response so we can forward ACK properly
	        // later.
	        resp.getSession().setAttribute("RESP_INV", resp);
		}
		// relay the response to the peer session.
		// get the peer request.
		SipServletRequest aReq = resp.getRequest().getB2buaHelper().getLinkedSipServletRequest(resp.getRequest());
		// create response and send .
		if(aReq!=null){
			SipServletResponse aResp = aReq.createResponse(resp.getStatus(),resp.getReasonPhrase());
			aResp.setContent(resp.getRawContent(), resp.getContentType());	
			aResp.send();
		}	
	}
	
	/**
	 * Invoked for SIP 2xx class responses.
	 */
	@Override
	protected void doSuccessResponse(SipServletResponse resp)
			throws ServletException, IOException {

		// if the response is for INVITE request.
		if(resp.getMethod().equals("INVITE")){
			// Keep the reference to the response so we can forward ACK properly
	        // later.
	        resp.getSession().setAttribute("RESP_INV", resp);
		}	
		// relay the response to the peer session.
		// get the request.
		SipServletRequest aReq = resp.getRequest().getB2buaHelper().getLinkedSipServletRequest(resp.getRequest());		
		// create response and send .
		if(aReq!=null){
			SipServletResponse aResp = aReq.createResponse(resp.getStatus(),resp.getReasonPhrase());
			aResp.setContent(resp.getRawContent(), resp.getContentType());	
			aResp.send();
		}	
		
	}
	
	/**
	 * Invoked for SIP ACK requests.
	 */
	@Override
	protected void doAck(SipServletRequest req)
			throws ServletException, IOException {

		// get the peer session.
		SipSession session = req.getB2buaHelper().getLinkedSession(req.getSession());
		//get the invite response from session. 
		SipServletResponse bResp = (SipServletResponse)session.getAttribute("RESP_INV");
		//create ACK from the response and send.
		SipServletRequest bReq = bResp.createAck();
		bReq.setContent(req.getRawContent(), req.getContentType());
		bReq.send();
	}
	
	/**
	 * Invoked for SIP BYE requests.
	 */
	@Override
	protected void doBye(SipServletRequest req)
			throws ServletException, IOException {		
		
		// Relay the BYE request from the caller to the callee.
		SipServletRequest bReq = req.getB2buaHelper().createRequest(req.getB2buaHelper().getLinkedSession(req.getSession())
				,req,null);
		bReq.setContent(req.getRawContent(), req.getContentType());
		bReq.send();	

	}	
}
