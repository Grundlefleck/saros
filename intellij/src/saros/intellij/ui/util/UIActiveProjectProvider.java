package saros.intellij.ui.util;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import saros.session.ISarosSession;
import saros.session.ISarosSessionManager;
import saros.session.ISessionLifecycleListener;
import saros.session.SessionEndReason;

/**
 * Class to provide access to an active project object. This class should only be used for UI
 * purposes.
 */
public class UIActiveProjectProvider {
  private static final Logger log = Logger.getLogger(UIActiveProjectProvider.class);

  private static final int PROJECT_REQUEST_TIMEOUT = 1000;

  private volatile ISarosSession sarosSession;

  @SuppressWarnings("FieldCanBeLocal")
  private ISessionLifecycleListener sessionLifecycleListener =
      new ISessionLifecycleListener() {
        @Override
        public void sessionStarted(ISarosSession session) {
          sarosSession = session;
        }

        @Override
        public void sessionEnded(ISarosSession session, SessionEndReason reason) {
          sarosSession = null;
        }
      };

  public UIActiveProjectProvider(ISarosSessionManager sarosSessionManager) {
    sarosSessionManager.addSessionLifecycleListener(sessionLifecycleListener);
  }

  /**
   * Returns a current project object.
   *
   * <p>If there is currently a session, the session project will be returned. Otherwise, the
   * currently focused project will be requested and returned. In this case, <code>null</code> is
   * possible.
   *
   * <p><b>NOTE:</b> This method must not be called from the swing dispatcher thread as this will
   * cause the underlying request to obtain the currently focused project to always time out.
   *
   * <p><b>NOTE:</b> The project returned by this method should only be used to for UI purposes.
   *
   * @return a current project object or <code>null</code> if there is no session and the focused
   *     project could not be obtained
   */
  @Nullable
  public Project getProject() {
    assert !ApplicationManager.getApplication().isDispatchThread()
        : "This method must not be called from the swing dispatcher thread.";

    ISarosSession currentSession = sarosSession;

    if (currentSession != null) {
      return sarosSession.getComponent(Project.class);
    }

    Promise<DataContext> promisedDataContext =
        DataManager.getInstance().getDataContextFromFocusAsync();
    Promise<Project> promisedProject = promisedDataContext.then(DataKeys.PROJECT::getData);

    try {
      return promisedProject.blockingGet(PROJECT_REQUEST_TIMEOUT);

    } catch (TimeoutException | ExecutionException e) {
      log.warn("Failed to fetch active project", e);
    }

    return null;
  }
}
