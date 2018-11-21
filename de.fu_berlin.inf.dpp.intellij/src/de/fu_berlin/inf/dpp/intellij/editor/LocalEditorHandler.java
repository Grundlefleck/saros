package de.fu_berlin.inf.dpp.intellij.editor;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.vfs.VirtualFile;

import de.fu_berlin.inf.dpp.activities.SPath;
import de.fu_berlin.inf.dpp.filesystem.IProject;
import de.fu_berlin.inf.dpp.filesystem.IResource;
import de.fu_berlin.inf.dpp.intellij.filesystem.IntelliJProjectImplV2;

import de.fu_berlin.inf.dpp.session.IReferencePointManager;
import org.apache.log4j.Logger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Class for handling activities on local editors and transforming them to calls to
 * {@link EditorManager} for generating activities .
 */
public class LocalEditorHandler {

    private final static Logger LOG = Logger
        .getLogger(LocalEditorHandler.class);

    private final ProjectAPI projectAPI;
    private final VirtualFileConverter virtualFileConverter;

    /**
     * This is just a reference to {@link EditorManager}'s editorPool and not a
     * separate pool.
     */
    private EditorPool editorPool;

    private EditorManager manager;

    public LocalEditorHandler(ProjectAPI projectAPI,
        VirtualFileConverter virtualFileConverter) {

        this.projectAPI = projectAPI;
        this.virtualFileConverter = virtualFileConverter;
    }

    /**
     * Initializes all fields that require an EditorManager. It has to be called
     * after the constructor and before the object is used, otherwise it will not
     * work.
     * <p/>
     * The reason for this late initialization is that this way the LocalEditorHandler
     * can be instantiated by the PicoContainer, otherwise there would be a cyclic
     * dependency.
     *
     * @param editorManager - an EditorManager
     */
    public void initialize(EditorManager editorManager) {
        this.editorPool = editorManager.getEditorPool();
        this.manager = editorManager;
        projectAPI
            .addFileEditorManagerListener(editorManager.getFileListener());
    }

    /**
     * Opens an editor for the passed virtualFile, adds it to the pool of
     * currently open editors and calls
     * {@link EditorManager#startEditor(Editor)} with it.
     * <p>
     * <b>Note:</b> This only works for shared resources.
     * </p>
     *
     * @param virtualFile path of the file to open
     * @param activate    activate editor after opening
     * @return the opened <code>Editor</code> or <code>null</code> if the given
     * file does not belong to a shared module
     */
    @Nullable
    public Editor openEditor(
        @NotNull
            VirtualFile virtualFile, boolean activate) {

        if (!manager.hasSession()) {
            return null;
        }

        SPath path = virtualFileConverter.convertToPath(virtualFile);

        if (path == null) {
            LOG.debug("Ignored open editor request for file " + virtualFile +
                " as it does not belong to a shared module");

            return null;
        }

        return openEditor(virtualFile,path,activate);
    }

    /**
     * Opens an editor for the passed virtualFile, adds it to the pool of
     * currently open editors and calls
     * {@link EditorManager#startEditor(Editor)} with it.
     * <p>
     * <b>Note:</b> This only works for shared resources that belong to the
     * given module.
     * </p>
     *
     * @param virtualFile path of the file to open
     * @param project     module the file belongs to
     * @param activate    activate editor after opening
     * @return the opened <code>Editor</code> or <code>null</code> if the given
     * file does not belong to a shared module
     */
    @Nullable
    public Editor openEditor(
        @NotNull
            VirtualFile virtualFile,
        @NotNull
            IProject project, boolean activate) {

        IResource resource = virtualFileConverter
            .getResource(virtualFile, project);

        if (resource == null) {
            LOG.debug("Could not open Editor for file " + virtualFile +
                " as it does not belong to the given module " + project);

            return null;
        }

        IReferencePointManager referencePointManager = manager.getSession().
            getComponent(IReferencePointManager.class);

        return openEditor(virtualFile, new SPath(resource, referencePointManager), activate);
    }

    /**
     * Opens an editor for the passed virtualFile, adds it to the pool of
     * currently open editors and calls
     * {@link EditorManager#startEditor(Editor)} with it.
     * <p>
     * <b>Note:</b> This only works for shared resources.
     * </p>
     * <p>
     * <b>Note:</b> This method expects the VirtualFile and the SPath to point
     * to the same resource.
     * </p>
     *
     * @param virtualFile path of the file to open
     * @param path        saros resource representation of the file
     * @param activate    activate editor after opening
     * @return the opened <code>Editor</code> or <code>null</code> if the given
     * file does not exist or does not belong to a shared module
     */
    @Nullable
    private Editor openEditor(@NotNull VirtualFile virtualFile,
        @NotNull SPath path, boolean activate){

        if(!virtualFile.exists()){
            LOG.debug("Could not open Editor for file " + virtualFile +
                " as it does not exist");

            return null;

        }else if (!manager.getSession().isShared(path.getResource())) {
            LOG.debug("Ignored open editor request for file " + virtualFile +
                " as it is not shared");

            return null;
        }

        Editor editor = projectAPI.openEditor(virtualFile, activate);

        editorPool.add(path, editor);
        manager.startEditor(editor);

        LOG.debug("Opened Editor " + editor + " for file " + virtualFile);

        return editor;
    }

    /**
     * Removes a file from the editorPool and calls
     * {@link EditorManager#generateEditorClosed(SPath)}
     *
     * @param virtualFile
     */
    public void closeEditor(@NotNull VirtualFile virtualFile) {
        SPath path = virtualFileConverter.convertToPath(virtualFile);
        if (path != null) {
            editorPool.removeEditor(path);
            manager.generateEditorClosed(path);
        }
    }

    /**
     * Removes the resource belonging to the given path from the editor pool
     *
     * @param path path
     */
    public void removeEditor(@NotNull SPath path){
        editorPool.removeEditor(path);
    }

    /**
     * Saves the document under path, thereby flushing its contents to disk.
     *
     * @param path the path for the document to save
     * @see Document
     */
    public void saveDocument(
        @NotNull
            SPath path) {

        Document document = editorPool.getDocument(path);

        if (document == null) {
            IntelliJProjectImplV2 project = (IntelliJProjectImplV2) path
                .getProject().getAdapter(IntelliJProjectImplV2.class);

            VirtualFile file = project
                .findVirtualFile(path.getRelativePathFromReferencePoint());

            if (file == null || !file.exists()) {
                LOG.warn("Failed to save document for " + path
                    + " - could not get a valid VirtualFile");

                return;
            }

            document = projectAPI.getDocument(file);

            if (document == null) {
                LOG.warn("Failed to save document for " + file
                    + " - could not get a matching Document");

                return;
            }
        }

        projectAPI.saveDocument(document);
    }

    /**
     * Calls {@link EditorManager#generateEditorActivated(SPath)}.
     *
     * @param file
     */
    public void activateEditor(@NotNull VirtualFile file) {
        SPath path = virtualFileConverter.convertToPath(file);
        if (path != null) {
            manager.generateEditorActivated(path);
        }
    }

    public void sendEditorActivitySaved(SPath path) {
        // FIXME: not sure how to do it intelliJ
    }

    /**
     * @param path
     * @return <code>true</code>, if the path is opened in an editor.
     */
    public boolean isOpenEditor(SPath path) {
        Document doc = editorPool.getDocument(path);
        if (doc == null) {
            return false;
        }

        return projectAPI.isOpen(doc);
    }

    /**
     * Returns an <code>SPath</code> representing the passed file.
     *
     * @param virtualFile file to get the <code>SPath</code> for
     *
     * @return an <code>SPath</code> representing the passed file or
     *         <code>null</code> if the passed file is null or does not exist,
     *         there currently is no session, or the file does not belong to a
     *         shared module
     */
    @Nullable
    private SPath toPath(VirtualFile virtualFile) {
        if (virtualFile == null || !virtualFile.exists() || !manager
            .hasSession()) {
            return null;
        }

        IResource resource = null;

        IReferencePointManager referencePointManager = manager.getSession()
        .getComponent(IReferencePointManager.class);

        for (IProject project : referencePointManager.getProjects(
            manager.getSession().getReferencePoints())) {
            resource = getResource(virtualFile, project);

            if(resource != null){
                break;
            }
        }        
        
        return resource == null ? null : new SPath(resource, referencePointManager);
    }

    /**
     * Returns an <code>IResource</code> for the passed VirtualFile.
     *
     * @param virtualFile file to get the <code>IResource</code> for
     * @param project module the file belongs to
     * @return an <code>IResource</code> for the passed file or
     *         <code>null</code> it does not belong to the passed module.
     */
    @Nullable
    private static IResource getResource(@NotNull VirtualFile virtualFile,
        @NotNull IProject project) {

        IntelliJProjectImplV2 module = (IntelliJProjectImplV2) project
            .getAdapter(IntelliJProjectImplV2.class);

        return module.getResource(virtualFile);
    }
}
