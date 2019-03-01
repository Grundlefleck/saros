package de.fu_berlin.inf.dpp.intellij.ui.views;

import de.fu_berlin.inf.dpp.intellij.ui.actions.NotImplementedAction;
import de.fu_berlin.inf.dpp.intellij.ui.util.IconManager;
import de.fu_berlin.inf.dpp.intellij.ui.views.buttons.ConnectButton;
import de.fu_berlin.inf.dpp.intellij.ui.views.buttons.ConsistencyButton;
import de.fu_berlin.inf.dpp.intellij.ui.views.buttons.FollowButton;
import de.fu_berlin.inf.dpp.intellij.ui.views.buttons.LeaveSessionButton;
import de.fu_berlin.inf.dpp.intellij.ui.views.buttons.SimpleButton;
import java.awt.FlowLayout;
import javax.swing.JToolBar;

/**
 * Saros toolbar. Displays several buttons for interacting with Saros.
 *
 * <p>FIXME: Replace by IDEA toolbar class.
 */
class SarosToolbar extends JToolBar {

  private static final boolean ENABLE_ADD_CONTACT =
      Boolean.getBoolean("saros.intellij.ENABLE_ADD_CONTACT");
  private static final boolean ENABLE_PREFERENCES =
      Boolean.getBoolean("saros.intellij.ENABLE_PREFERENCES");

  SarosToolbar() {
    super("Saros IDEA toolbar");
    setLayout(new FlowLayout(FlowLayout.RIGHT));
    addToolbarButtons();
  }

  private void addToolbarButtons() {

    add(new ConnectButton());

    if (ENABLE_ADD_CONTACT) {
      add(
          new SimpleButton(
              new NotImplementedAction("addContact"),
              "Add contact to list",
              IconManager.ADD_CONTACT_ICON));
    }

    if (ENABLE_PREFERENCES) {
      add(
          new SimpleButton(
              new NotImplementedAction("preferences"),
              "Open preferences",
              IconManager.OPEN_PREFERENCES_ICON));
    }

    add(new FollowButton());

    add(new ConsistencyButton());

    add(new LeaveSessionButton());
  }
}