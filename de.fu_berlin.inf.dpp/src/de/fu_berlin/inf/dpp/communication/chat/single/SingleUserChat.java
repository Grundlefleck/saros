package de.fu_berlin.inf.dpp.communication.chat.single;

import java.util.Collections;
import java.util.Date;
import java.util.Set;

import org.apache.log4j.Logger;
import org.jivesoftware.smack.Chat;
import org.jivesoftware.smack.MessageListener;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smackx.ChatState;
import org.jivesoftware.smackx.ChatStateListener;
import org.jivesoftware.smackx.ChatStateManager;

import de.fu_berlin.inf.dpp.communication.chat.AbstractChat;
import de.fu_berlin.inf.dpp.communication.chat.ChatElement;
import de.fu_berlin.inf.dpp.communication.chat.ChatHistory;
import de.fu_berlin.inf.dpp.net.JID;
import de.fu_berlin.inf.dpp.net.SarosNet;
import de.fu_berlin.inf.dpp.net.util.RosterUtils;

/**
 * This object represents a chat with a single user.
 */
public class SingleUserChat extends AbstractChat {
    private static final Logger LOG = Logger.getLogger(SingleUserChat.class);

    private SarosNet sarosNet;

    private final ChatStateListener chatStateListener = new ChatStateListener() {

        @Override
        public void processMessage(Chat chat, Message message) {
            LOG.trace(this + " : received message from: " + message.getFrom()
                + " : " + message.getBody());

            if (message.getFrom() == null || message.getBody() == null)
                return;

            addHistoryEntry(new ChatElement(message, new Date(
                System.currentTimeMillis())));

            notifyJIDMessageReceived(new JID(message.getFrom()),
                message.getBody());
        }

        @Override
        public void stateChanged(Chat chat, ChatState state) {
            notifyJIDStateChanged(new JID(chat.getParticipant()), state);
        }

    };

    private ChatStateManager chatStateManager;
    private Chat chat;

    private String userJID;

    private boolean isConnected;

    SingleUserChat(SarosNet sarosNet) {
        this.sarosNet = sarosNet;
    }

    /**
     * Initializes the chat so that it is possible to exchange messages with the
     * participant.
     * 
     * @param userJID
     *            {@link JID} of the local user
     * @param chat
     *            {@link Chat} object from Smack, contains the recipient
     * @param chatStateManager
     *            {@link ChatStateManager} of the current connection
     */
    synchronized void initChat(String userJID, Chat chat,
        ChatStateManager chatStateManager) {
        if (this.chat != null)
            this.chat.removeMessageListener(chatStateListener);

        this.chat = chat;
        this.chat.addMessageListener(chatStateListener);
        this.chatStateManager = chatStateManager;
        this.userJID = userJID;
    }

    /**
     * Returns the chat's {@link MessageListener}.
     * 
     * @return the chat's {@link MessageListener}
     */
    synchronized MessageListener getMessagerListener() {
        return chatStateListener;
    }

    /**
     * Notify the chat that it has been connected or disconnected which causes
     * it to notify listeners and add {@link ChatElement}s to its
     * {@link ChatHistory} representing the event.
     * 
     * @param isConnected
     */
    void setConnected(boolean isConnected) {
        JID participant;
        synchronized (SingleUserChat.this) {
            LOG.trace("new connection state, connected=" + isConnected);
            this.isConnected = isConnected;
            participant = new JID(chat.getParticipant());
        }

        if (isConnected) {
            notifyJIDConnected(participant);
        } else {
            notifyJIDDisconnected(participant);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized JID getJID() {
        return new JID(userJID);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized Set<JID> getParticipants() {
        return Collections.singleton(new JID(chat.getParticipant()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized String getThreadID() {
        return chat.getThreadID();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendMessage(Message message) throws XMPPException {
        Chat currentChat;

        synchronized (SingleUserChat.this) {
            currentChat = chat;

            chat.sendMessage(message);
            message.setFrom(userJID);
        }

        chatStateListener.processMessage(currentChat, message);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendMessage(String text) throws XMPPException {
        String participant;
        String threadId;

        synchronized (SingleUserChat.this) {
            threadId = chat.getThreadID();
            participant = chat.getParticipant();
        }

        Message message = new Message(participant, Message.Type.chat);
        message.setThread(threadId);
        message.setBody(text);
        sendMessage(message);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void setCurrentState(ChatState newState)
        throws XMPPException {
        chatStateManager.setCurrentState(newState, chat);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getTitle() {
        JID participant;
        synchronized (SingleUserChat.this) {
            participant = new JID(chat.getParticipant());
        }

        String nickname = RosterUtils.getNickname(sarosNet, participant);

        return nickname != null ? nickname : participant.getBareJID()
            .toString();
    }

    /**
     * This method does nothing as {@link SingleUserChat}s are stateless despite
     * the global connection status.
     * 
     * @return <code>true</code>
     */
    @Override
    public boolean connect() {
        return true;
    }

    /**
     * This method does nothing as {@link SingleUserChat}s are stateless despite
     * the global connection status.
     * 
     * @return <code>true</code>
     */
    @Override
    public boolean disconnect() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized boolean isConnected() {
        return isConnected;
    }

}