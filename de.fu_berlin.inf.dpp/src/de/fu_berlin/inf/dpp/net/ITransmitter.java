/*
 * DPP - Serious Distributed Pair Programming (c) Freie Universitaet Berlin -
 * Fachbereich Mathematik und Informatik - 2006 (c) Riad Djemili - 2006
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 1, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 675 Mass
 * Ave, Cambridge, MA 02139, USA.
 */

package de.fu_berlin.inf.dpp.net;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.SubMonitor;
import org.jivesoftware.smack.packet.IQ;

import de.fu_berlin.inf.dpp.FileList;
import de.fu_berlin.inf.dpp.Saros;
import de.fu_berlin.inf.dpp.User;
import de.fu_berlin.inf.dpp.activities.serializable.FileActivityDataObject;
import de.fu_berlin.inf.dpp.annotations.Component;
import de.fu_berlin.inf.dpp.concurrent.management.DocumentChecksum;
import de.fu_berlin.inf.dpp.exceptions.LocalCancellationException;
import de.fu_berlin.inf.dpp.exceptions.UserCancellationException;
import de.fu_berlin.inf.dpp.invitation.IInvitationProcess;
import de.fu_berlin.inf.dpp.net.internal.DefaultInvitationInfo;
import de.fu_berlin.inf.dpp.net.internal.SarosPacketCollector;
import de.fu_berlin.inf.dpp.net.internal.XStreamExtensionProvider;
import de.fu_berlin.inf.dpp.net.internal.TransferDescription.FileTransferType;
import de.fu_berlin.inf.dpp.project.ISharedProject;
import de.fu_berlin.inf.dpp.util.VersionManager.VersionInfo;

/**
 * An humble interface that is responsible for network functionality. The idea
 * behind this interface is to only encapsulates the least possible amount of
 * functionality - the one that can't be easily tested.
 * 
 * @author rdjemili
 */
@Component(module = "net")
public interface ITransmitter {

    /* ---------- invitations --------- */

    /**
     * Sends an invitation message for given shared project to given user.
     * 
     * @param sharedProject
     *            the shared project to which the user should be invited to.
     * @param jid
     *            the Jabber ID of the user that is to be invited.
     * @param description
     *            a informal description text that can be provided with the
     *            invitation. Can not be <code>null</code>.
     */
    public void sendInvitation(ISharedProject sharedProject, JID jid,
        String description, int colorID, VersionInfo versionInfo,
        String invitationID);

    /**
     * Sends an cancellation message that tells the receiver that the invitation
     * is canceled.
     * 
     * @param jid
     *            the Jabber ID of the recipient.
     * @param errorMsg
     *            the reason why the invitation was canceled or
     *            <code>null</code>.
     */
    public void sendCancelInvitationMessage(JID jid, String errorMsg);

    /* ---------- files --------- */

    /**
     * Sends given file list to given Jabber user. This methods blocks until the
     * file transfer is done or failed.
     * 
     * @param jid
     *            the Jabber ID of the user to which the file list is to be
     *            sent.
     * 
     * @param fileList
     *            the file list that is to be sent.
     * 
     * @param monitor
     *            a monitor to which progress will be reported and which is
     *            queried for cancellation.
     * 
     * @throws UserCancellationException
     *             if the operation was canceled via the given progress monitor
     *             an LocalCancellationException is thrown. If the operation was
     *             canceled via the monitor and the exception is not received,
     *             the operation completed successfully, before noticing the
     *             cancellation.
     * 
     *             if the operation was canceled by the recipient (remotely) a
     *             RemoteCancellationException is thrown
     * 
     * @throws IOException
     *             if the operation fails because of a problem with the XMPP
     *             Connection.
     */
    public void sendFileList(JID jid, String invitationID, FileList fileList,
        SubMonitor monitor) throws IOException, UserCancellationException;

    /**
     * @throws CancellationException
     *             A User canceled.
     * @throws IOException
     *             If the operation fails because of a problem with the XMPP
     *             Connection.
     * @throws LocalCancellationException
     */
    public DefaultInvitationInfo receiveFileListRequest(String invitationID,
        SubMonitor monitor) throws IOException, LocalCancellationException;

    /**
     * @param archiveCollector
     * @blocking If forceWait is true.
     * 
     * @throws CancellationException
     *             A User canceled.
     * @throws IOException
     *             If the operation fails because of a problem with the XMPP
     *             Connection.
     */
    public FileList receiveFileList(SarosPacketCollector archiveCollector,
        SubMonitor monitor, boolean forceWait)
        throws UserCancellationException, IOException;

    /**
     * @param b
     * @param archiveCollector
     * @throws IOException
     *             If the operation fails because of a problem with the XMPP
     *             Connection.
     * @throws UserCancellationException
     */
    public InputStream receiveArchive(SarosPacketCollector archiveCollector,
        SubMonitor monitor, boolean b) throws IOException,
        UserCancellationException;

    public void sendUserList(JID to, String invitationID, Collection<User> user);

    public boolean receiveUserListConfirmation(List<User> fromUsers,
        SubMonitor monitor) throws CancellationException, IOException;

    /**
     * Sends a request-for-file-list-message to given user.
     * 
     * @param recipient
     *            the Jabber ID of the recipient.
     */
    public void sendFileListRequest(JID recipient, String invitationID);

    /**
     * Sends given file to given recipient with given sequence number.
     * 
     * @param recipient
     *            the Jabber ID of the recipient.
     * @param project
     *            the project of which the given path contains the file to be
     *            sent.
     * @param path
     *            the project-relative path of the resource that is to be sent.
     * @param sequenceNumber
     *            the sequence number that will be associated with this
     *            activityDataObject.
     * @param callback
     *            an call-back for the file transfer state. CANNOT be null.
     * 
     * @param monitor
     *            a monitor to which progress will be reported and which is
     *            queried for cancellation.
     * 
     * @throws IOException
     *             If the file could not be read, other errors are reported to
     *             the call-back.
     * 
     * @throws CancellationException
     *             if the operation was canceled via the given progress monitor
     *             an CancellationException is thrown. If the operation was
     *             canceled via the monitor and the exception is not received,
     *             the operation completed successfully, before noticing the
     *             cancellation.
     */
    public void sendFileAsync(JID recipient, IProject project, IPath path,
        int sequenceNumber, IFileTransferCallback callback, SubMonitor monitor)
        throws IOException;

    /**
     * Sends given file to given recipient with given sequence number
     * SYNCHRONOUSLY.
     * 
     * This methods thus block until the file has been sent or it failed.
     * 
     * @param to
     *            the Jabber ID of the recipient.
     * @param project
     *            the project of which the given path contains the file to be
     *            sent.
     * @param path
     *            the project-relative path of the resource that is to be sent.
     * @param sequenceNumber
     *            the time that will be associated with this activityDataObject.
     * 
     * @param monitor
     *            a monitor to which progress will be reported and which is
     *            queried for cancellation.
     * 
     * @throws UserCancellationException
     *             if the operation was canceled via the given progress monitor
     *             an LocalCancellationException is thrown. If the operation was
     *             canceled via the monitor and the exception is not received,
     *             the operation completed successfully, before noticing the
     *             cancellation.
     * 
     *             if the operation was canceled by the recipient a
     *             RemoteCancellationException is thrown
     * 
     * @throws IOException
     *             If the given path identifies no file (but a directory), the
     *             file could not be read or an error occurred while sending
     */
    public void sendFile(JID to, IProject project, IPath path,
        int sequenceNumber, SubMonitor monitor) throws IOException,
        UserCancellationException;

    /**
     * Sends given archive file to given recipient.
     * 
     * This is a blocking method.
     * 
     * @param recipient
     *            the Jabber ID of the recipient.
     * @param project
     *            the project of which the given path contains the file to be
     *            sent.
     * @param archive
     *            the project-relative path of the resource that is to be sent.
     * @param monitor
     *            a monitor to which progress will be reported and which is
     *            queried for cancellation.
     * 
     * @throws IOException
     *             If the file could not be read or an error occurred while
     *             sending or a technical error happened.
     * @throws UserCancellationException
     *             if the operation was canceled via the given progress monitor
     *             an LocalCancellationException is thrown. If the operation was
     *             canceled via the monitor and the exception is not received,
     *             the operation completed successfully, before noticing the
     *             cancellation.
     * 
     *             if the operation was canceled by the recipient a
     *             RemoteCancellationException is thrown
     * 
     * @blocking Blocks until the transfer is complete.
     */
    public void sendProjectArchive(JID recipient, String invitationID,
        IProject project, File archive, SubMonitor monitor) throws IOException,
        UserCancellationException;

    /**
     * Sends queued file transfers.
     */
    public void sendRemainingFiles();

    /**
     * Sends queued messages.
     */
    public void sendRemainingMessages();

    /**
     * Sends a request for activityDataObjects to all users.
     * 
     * TODO SS MR Dependency Violation - ITransmitter should not need a shared
     * project
     * 
     * @param sharedProject
     *            the shared project
     * @param requestedSequenceNumbers
     *            a map containing the sequence number to be requested as a
     *            value and the user to request them from as key
     * @param andUp
     *            true if all activityDataObjects after the requested one are requested
     *            too, false if only the activityDataObject with the
     *            requestedSequenceNumber is requested
     */
    public void sendRequestForActivity(ISharedProject sharedProject,
        Map<JID, Integer> requestedSequenceNumbers, boolean andUp);

    /* ---------- etc --------- */

    /**
     * Sends a leave message to the participants of given shared project. See
     * {@link IInvitationProcess} for more information when this is supposed be
     * sent.
     * 
     * @param sharedProject
     *            the shared project that this join message refers to.
     */
    public void sendLeaveMessage(ISharedProject sharedProject);

    /**
     * Sends given list of TimedActivities to the given recipient.
     * 
     * This list MUST not contain any {@link FileActivityDataObject}s where
     * {@link FileActivityDataObject#getType()} == {@link FileActivityDataObject.Type#Created} as
     * binary data is not supported in messages bodies.
     * 
     * @param recipient
     *            The JID of the user who is to receive the given list of timed
     *            activityDataObjects.
     * @param timedActivities
     *            The list of timed activityDataObjects to send to the user.
     * 
     * @throws IllegalArgumentException
     *             if the recipient is null, the recipient equals the local user
     *             as returned by {@link Saros#getMyJID()} or the given list is
     *             null or contains no activityDataObjects.
     * 
     * @throws AssertionError
     *             if the given list of timed activityDataObjects contains FileActivities
     *             of type created AND the application is run using asserts.
     */
    public void sendTimedActivities(JID recipient,
        List<TimedActivity> timedActivities);

    /**
     * Sends a FileChecksumErrorMessage with the given path to the given list of
     * users.
     * 
     * @param recipients
     *            The list of users to send this message to. The local user is
     *            explicitly allowed to be contained in this list.
     * 
     * @param paths
     *            The project relative path of files which are affected. This
     *            information can be shown to the user.
     * @param resolved
     *            if true then the inconsistency is resolved and clients can
     *            stop blocking all user operations. if false then a
     *            inconsistency has been detected and clients should block all
     *            further user operation.
     */
    public void sendFileChecksumErrorMessage(List<JID> recipients,
        Set<IPath> paths, boolean resolved);

    /**
     * Sends the given DocumentChecksums to all clients.
     * 
     * If the XMPP connection is closed this method will fail silently.
     * 
     * @host This method should only be called on the host.
     */
    public void sendDocChecksumsToClients(List<JID> recipients,
        Collection<DocumentChecksum> checksums);

    /**
     * Make sure that Jingle has sufficiently initialized so that a remote
     * client trying to connect to us, will not fail because we are not ready to
     * handle his Jingle negotiation attempts.
     */
    public void awaitJingleManager(JID peer);

    /**
     * Sends a query, a {@link IQ.Type} GET, to the user with given {@link JID}.
     * 
     * Example using provider:
     * <p>
     * <code>XStreamExtensionProvider<VersionInfo> versionProvider = new
     * XStreamExtensionProvider<VersionInfo>( "sarosVersion", VersionInfo.class,
     * Version.class, Compatibility.class);<br>
     * sendQuery(jid, versionProvider, 5000);
     * </code>
     * </p>
     * In this example this sends a request to the user with jid and waits 5
     * seconds for an answer. If it arrives in time, a payload of type T (in
     * this case VersionInfo) will be returned, else the result is null.
     */
    public <T> T sendQuery(JID jid, XStreamExtensionProvider<T> provider,
        T payload, long timeout);

    public SarosPacketCollector getInvitationCollector(String invitationID,
        FileTransferType filelistTransfer);

    public void receiveInvitationCompleteConfirmation(SubMonitor monitor,
        SarosPacketCollector collector) throws LocalCancellationException,
        IOException;

    public SarosPacketCollector getInvitationCompleteCollector(
        String invitationID);

    public void sendInvitationCompleteConfirmation(JID to, String invitationID);
}
