package de.fu_berlin.inf.dpp.stf.server.rmiSarosSWTBot.conditions;

import org.eclipse.swtbot.swt.finder.waits.DefaultCondition;

import de.fu_berlin.inf.dpp.stf.server.rmiSarosSWTBot.eclipse.workbench.basicWidgets.Table;

public class ExistsContextMenuOfTableItem extends DefaultCondition {

    private Table table;
    private String itemText;
    private String contextName;

    ExistsContextMenuOfTableItem(Table table, String itemText, String contextName) {
        this.table = table;
        this.itemText = itemText;
        this.contextName = contextName;
    }

    public String getFailureMessage() {

        return null;
    }

    public boolean test() throws Exception {
        return table.isContextMenuOfTableItemEnabled(itemText, contextName);
    }
}
