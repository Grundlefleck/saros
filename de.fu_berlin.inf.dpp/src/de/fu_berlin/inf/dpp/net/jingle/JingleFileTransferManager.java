package de.fu_berlin.inf.dpp.net.jingle;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;

import org.apache.log4j.Logger;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smackx.jingle.IncomingJingleSession;
import org.jivesoftware.smackx.jingle.JingleManager;
import org.jivesoftware.smackx.jingle.JingleSessionRequest;
import org.jivesoftware.smackx.jingle.OutgoingJingleSession;
import org.jivesoftware.smackx.jingle.JingleNegotiator.JingleException;
import org.jivesoftware.smackx.jingle.JingleNegotiator.State;
import org.jivesoftware.smackx.jingle.listeners.JingleMediaListener;
import org.jivesoftware.smackx.jingle.listeners.JingleSessionRequestListener;
import org.jivesoftware.smackx.jingle.listeners.JingleSessionStateListener;
import org.jivesoftware.smackx.jingle.listeners.JingleTransportListener;
import org.jivesoftware.smackx.jingle.media.PayloadType;
import org.jivesoftware.smackx.jingle.nat.ICETransportManager;
import org.jivesoftware.smackx.jingle.nat.TransportCandidate;

import de.fu_berlin.inf.dpp.FileList;
import de.fu_berlin.inf.dpp.Saros.ConnectionState;
import de.fu_berlin.inf.dpp.net.ITransmitter;
import de.fu_berlin.inf.dpp.net.JID;
import de.fu_berlin.inf.dpp.net.internal.JingleFileTransferData;
import de.fu_berlin.inf.dpp.net.internal.XMPPChatTransmitter.FileTransferData;

public class JingleFileTransferManager {

	private static Logger logger = Logger
			.getLogger(JingleFileTransferManager.class);

	private XMPPConnection xmppConnection;
	private IJingleFileTransferListener transmitter;
	private JingleManager jm;

	private HashMap<JID, IncomingJingleSession> incomingSessions = null;
	private HashMap<JID, OutgoingJingleSession> outgoingSessions = null;
	private FileTransferMediaManager mediaManager = null;

	public enum JingleConnectionState{
		INIT,
		ESTABLISHED,
		CLOSED,
		ERROR,
		DEFAULT
	}
	
	private class NegotiationProcess{
		JingleConnectionState state = JingleConnectionState.DEFAULT;
		String jid = null;
	}
	
	/**
	 * list of all negotiation processes.
	 */
	private HashMap<String, NegotiationProcess> negotiations = new HashMap<String, NegotiationProcess>();
	
	/* current negotiation state of jingle listener. */
	private boolean NEGOTIATION = false;
	
	private JingleConnectionState state = JingleConnectionState.INIT;
	
	public JingleFileTransferManager(XMPPConnection connection,
			IJingleFileTransferListener transmitter) {
		this.xmppConnection = connection;
		this.transmitter = transmitter;
		incomingSessions = new HashMap<JID, IncomingJingleSession>();
		outgoingSessions = new HashMap<JID, OutgoingJingleSession>();
		logger.debug("initialized jingle file transfer manager.");
		initialize();
	}

	public void initialize() {

		/* other stun server. */
		ICETransportManager icetm0 = new ICETransportManager(xmppConnection,
				"jivesoftware.com", 3478);

		mediaManager = new FileTransferMediaManager();
		mediaManager.addJingleFileTransferListener(transmitter);

		jm = new JingleManager(xmppConnection, icetm0, mediaManager);
		jm.addCreationListener(icetm0);

		jm.addJingleSessionRequestListener(new JingleSessionRequestListener() {
			public void sessionRequested(JingleSessionRequest request) {

				JID jid = new JID(request.getFrom());
				IncomingJingleSession incoming = incomingSessions.get(jid);
				
				if (incoming != null)
					return;

				try {

					// Accept the call
					incoming = request.accept();
					/* put to current session list. */
					incomingSessions.put(jid, incoming);
					// Start the call
					incoming.start();
				} catch (XMPPException e) {
					e.printStackTrace();
				}

			}
		});

	}

//	/**
//	 * wait for establish connection and get connection state.
//	 * @return
//	 */
//	public synchronized JingleConnectionState getState(JID jid){
//		NegotiationProcess process = negotiations.get(jid.toString());
//		if(process == null){
//			return JingleConnectionState.ERROR;
//		}
//		
//		while(process.state == JingleConnectionState.INIT){
//			try {
//				wait();
//			} catch (InterruptedException e) {
//				e.printStackTrace();
//			}
//		}
//		
//		notifyAll();
//		return process.state;
//	}
	
	/**
	 * wait for establish connection and get connection state.
	 * @return
	 */
	public JingleConnectionState getState(JID jid){
		NegotiationProcess process = negotiations.get(jid.toString());
		if(process == null){
			return JingleConnectionState.ERROR;
		}
		
		return process.state;
	}
	
	/**
	 * intiate a jingle session
	 * 
	 * @param jid
	 * @param transferData
	 * @param monitor
	 */
	public synchronized void createOutgoingJingleFileTransfer(JID jid,
			JingleFileTransferData[] transferData,
			JingleFileTransferProcessMonitor monitor) {
		
		/* wait for free negotiation process*/
		while(NEGOTIATION){
			try {
				wait();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		logger.debug("start jingle session negotiation process. ");
		final String jid_string = jid.toString();
		
		
		/*remove old process. */
		if(negotiations.containsKey(jid.toString())){
			negotiations.remove(jid.toString());
		}
		/* create negotiation process*/
		NegotiationProcess process = new NegotiationProcess();
		process.jid = jid_string;
		process.state = JingleConnectionState.INIT;
		negotiations.put(jid_string,process);
		
//		final JingleConnectionState state = JingleConnectionState.INIT;
		
		IncomingJingleSession incoming =incomingSessions.get(jid);
		if (incoming != null) {
			/* an incoming session already exist. */
			try {
				logger
						.debug("Incoming stream exists. Send data with current stream.");
				mediaManager.setTransferFile(transferData);
			} catch (JingleSessionException jse) {
				jse.printStackTrace();
			}
			return;
		}

		OutgoingJingleSession outgoing = outgoingSessions.get(jid);
		if (outgoing != null) {
			/* send new data with current connection. */
			try {
				mediaManager.setTransferFile(transferData);
			} catch (JingleSessionException jse) {
				jse.printStackTrace();
			}
			return;
		}
		try {
			// Set file info for media manager

			mediaManager.setTransferFile(transferData, monitor);
			outgoing = jm.createOutgoingJingleSession(jid.toString());
			
			/* transport events */
			outgoing.addTransportListener(new JingleTransportListener(){

				public void transportClosed(TransportCandidate cand) {
					logger.debug("transport closed: "+jid_string);
					NegotiationProcess p = negotiations.get(jid_string);
					p.state = JingleConnectionState.CLOSED;
//					state = JingleConnectionState.CLOSED;
					NEGOTIATION = false;
					notifyAll();
				}

				public void transportClosedOnError(XMPPException e) {
					logger.error("transport closed on error : "+jid_string);
					NegotiationProcess p = negotiations.get(jid_string);
					p.state = JingleConnectionState.ERROR;
					NEGOTIATION = false;
					notifyAll();
				}

				
				public void transportEstablished(TransportCandidate local,
						TransportCandidate remote) {
					logger.debug("transport established : "+jid_string);
					NegotiationProcess p = negotiations.get(jid_string);
					p.state = JingleConnectionState.ESTABLISHED;
					NEGOTIATION = false;
					notifyAll();
				}});
			
			/* add state listener. */
			outgoing.addStateListener(new JingleSessionStateListener(){

				public void afterChanged(State old, State newOne) {
//					logger.debug("session state after change new state : "+newOne.toString()+" JID: "+jid_string);
					
				}

				public void beforeChange(State old, State newOne)
						throws JingleException {
//					logger.debug("session state before change : "+old.toString()+" new : "+newOne.toString()+" JID: "+jid_string);
					
				}});

			/* add media listener. */
			outgoing.addMediaListener(new JingleMediaListener(){

				public void mediaClosed(PayloadType cand) {
//					logger.debug("media closed : "+jid_string);
				}

				public void mediaEstablished(PayloadType pt) {
//					logger.debug("media established : "+jid_string);
					
				}});
			
			/* add to outgoing session list. */
			outgoingSessions.put(jid, outgoing);
			outgoing.start();
		} catch (XMPPException e1) {
			e1.printStackTrace();
		}
	}

	/**
	 * intiate a jingle session
	 * 
	 * @param jid
	 * @param transferData
	 * @param monitor
	 */
	public void createOutgoingJingleFileTransferUnsync(JID jid,
			JingleFileTransferData[] transferData,
			JingleFileTransferProcessMonitor monitor) {
//		/* wait for free negotiation process*/
//		while(NEGOTIATION){
//			try {
//				wait();
//			} catch (InterruptedException e) {
//				e.printStackTrace();
//			}
//		}
//		NEGOTIATION = true;
		
		
		
		logger.debug("start jingle session negotiation process. ");
		final String jid_string = jid.toString();
		
		/* create negotiation process*/
		NegotiationProcess process = new NegotiationProcess();
		process.jid = jid_string;
		process.state = JingleConnectionState.INIT;
		negotiations.put(jid_string,process);
		
//		final JingleConnectionState state = JingleConnectionState.INIT;
		
		IncomingJingleSession incoming =incomingSessions.get(jid);
		if (incoming != null) {
			/* an incoming session already exist. */
			try {
				logger
						.debug("Incoming stream exists. Send data with current stream.");
				mediaManager.setTransferFile(transferData);
			} catch (JingleSessionException jse) {
				jse.printStackTrace();
			}
			return;
		}

		OutgoingJingleSession outgoing = outgoingSessions.get(jid);
		if (outgoing != null) {
			/* send new data with current connection. */
			try {
				mediaManager.setTransferFile(transferData);
			} catch (JingleSessionException jse) {
				jse.printStackTrace();
			}
			return;
		}
		try {
			// Set file info for media manager

			mediaManager.setTransferFile(transferData, monitor);
			outgoing = jm.createOutgoingJingleSession(jid.toString());
			
			/* transport events */
			outgoing.addTransportListener(new JingleTransportListener(){

				public void transportClosed(TransportCandidate cand) {
					logger.debug("transport closed: "+jid_string);
					NegotiationProcess p = negotiations.get(jid_string);
					p.state = JingleConnectionState.CLOSED;
//					NEGOTIATION = false;
//					notifyAll();
				}

				public void transportClosedOnError(XMPPException e) {
					logger.error("transport closed on error : "+jid_string);
					NegotiationProcess p = negotiations.get(jid_string);
					p.state = JingleConnectionState.ERROR;
//					NEGOTIATION = false;
//					notifyAll();
				}

				
				public void transportEstablished(TransportCandidate local,
						TransportCandidate remote) {
					logger.debug("transport established : "+jid_string);
					NegotiationProcess p = negotiations.get(jid_string);
					p.state = JingleConnectionState.ESTABLISHED;
//					NEGOTIATION = false;
//					notifyAll();
				}});
			
			/* add state listener. */
			outgoing.addStateListener(new JingleSessionStateListener(){

				public void afterChanged(State old, State newOne) {
//					logger.debug("session state after change new state : "+newOne.toString()+" JID: "+jid_string);
					
				}

				public void beforeChange(State old, State newOne)
						throws JingleException {
//					logger.debug("session state before change : "+old.toString()+" new : "+newOne.toString()+" JID: "+jid_string);
					
				}});

			/* add media listener. */
			outgoing.addMediaListener(new JingleMediaListener(){

				public void mediaClosed(PayloadType cand) {
					logger.debug("media closed : "+jid_string);
					NegotiationProcess p = negotiations.get(jid_string);
					p.state = JingleConnectionState.CLOSED;
//					NEGOTIATION = false;
//					notifyAll();
				}

				public void mediaEstablished(PayloadType pt) {
					logger.debug("media established : "+jid_string);
					NegotiationProcess p = negotiations.get(jid_string);
					p.state = JingleConnectionState.ESTABLISHED;
//					NEGOTIATION = false;
//					notifyAll();
				}});
			
			/* add to outgoing session list. */
			outgoingSessions.put(jid, outgoing);
			outgoing.start();
		} catch (XMPPException e1) {
			e1.printStackTrace();
		}
	}
	// private IncomingJingleSession getIncomingJingleSession(JID jid){
	// return incomingSessions.get(jid);
	// }
	//	
	// private OutgoingJingleSession getOutgoingJingleSession(JID jid){
	// return outgoingSessions.get(jid);
	// }

	// /**
	// * send datas with active jingle session.
	// * @param transferData
	// */
	// public void sendFileDatas(JingleFileTransferData[] transferData){
	//		
	// }

	/**
	 * remove all jingle sessions.
	 */
	public void terminateAllJingleSessions() {

		OutgoingJingleSession outgoing = null;
		for (JID jid : outgoingSessions.keySet()) {
			outgoing = outgoingSessions.get(jid);
			if (outgoing != null) {
				try {
					outgoing.terminate();
				} catch (XMPPException e1) {
					e1.printStackTrace();
				} finally {
					outgoing = null;
					mediaManager.removeJingleSession(jid);
					outgoingSessions.remove(jid);
				}
			}
		}

		IncomingJingleSession incoming = null;
		for (JID jid : incomingSessions.keySet()) {
			incoming = incomingSessions.get(jid);
			if (incoming != null) {
				try {
					incoming.terminate();
				} catch (XMPPException e1) {
					e1.printStackTrace();
				} finally {
					incoming = null;
					mediaManager.removeJingleSession(jid);
					incomingSessions.remove(jid);
				}
			}
		}
		/*remove all negotiation processes. */
		negotiations.clear();
	}

	/**
	 * terminate and remove jingle session for jid.
	 * @param jid
	 */
	public void terminateJingleSession(JID jid) {
		OutgoingJingleSession outgoing = outgoingSessions.get(jid);
		if (outgoing != null) {
			try {
				outgoing.terminate();
			} catch (XMPPException e1) {
				e1.printStackTrace();
			} finally {
				outgoing = null;
				mediaManager.removeJingleSession(jid);
				outgoingSessions.remove(jid);
				
			}
		}

		IncomingJingleSession incoming = incomingSessions.get(jid);
		if (incoming != null) {
			try {
				incoming.terminate();
			} catch (XMPPException e1) {
				e1.printStackTrace();
			} finally {
				incoming = null;
				mediaManager.removeJingleSession(jid);
				incomingSessions.remove(jid);
			}
		}
		
		if(negotiations.containsKey(jid.toString())){
			negotiations.remove(jid.toString());
		}
	}
}
