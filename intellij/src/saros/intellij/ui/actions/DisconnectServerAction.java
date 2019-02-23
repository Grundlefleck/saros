package saros.intellij.ui.actions;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.picocontainer.annotations.Inject;
import saros.communication.connection.ConnectionHandler;
import saros.intellij.ui.util.UIActiveProjectProvider;

/** Disconnects from XMPP/Jabber server */
public class DisconnectServerAction extends AbstractSarosAction {
  public static final String NAME = "disconnect";

  @Inject private ConnectionHandler connectionHandler;

  @Inject private UIActiveProjectProvider activeProjectProvider;

  @Override
  public String getActionName() {
    return NAME;
  }

  @Override
  public void execute() {

    Project project = activeProjectProvider.getProject();

    ProgressManager.getInstance()
        .run(
            new Task.Modal(project, "Disconnecting...", false) {

              @Override
              public void run(ProgressIndicator indicator) {

                LOG.info(
                    "Disconnecting current connection: " + connectionHandler.getConnectionID());

                indicator.setIndeterminate(true);

                try {
                  connectionHandler.disconnect();
                } finally {
                  indicator.stop();
                }
              }
            });
  }
}
