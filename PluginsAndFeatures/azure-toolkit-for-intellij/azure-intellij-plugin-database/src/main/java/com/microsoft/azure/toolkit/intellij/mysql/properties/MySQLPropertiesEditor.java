/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.intellij.mysql.properties;

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.microsoft.azure.toolkit.intellij.common.AzureHideableTitledSeparator;
import com.microsoft.azure.toolkit.intellij.common.BaseEditor;
import com.microsoft.azure.toolkit.intellij.database.DatabaseComboBox;
import com.microsoft.azure.toolkit.intellij.database.ui.ConnectionSecurityPanel;
import com.microsoft.azure.toolkit.intellij.database.ui.ConnectionStringsOutputPanel;
import com.microsoft.azure.toolkit.intellij.database.ui.MySQLPropertyActionPanel;
import com.microsoft.azure.toolkit.lib.auth.AzureAccount;
import com.microsoft.azure.toolkit.lib.common.event.AzureEventBus;
import com.microsoft.azure.toolkit.lib.common.messager.AzureMessager;
import com.microsoft.azure.toolkit.lib.common.model.Subscription;
import com.microsoft.azure.toolkit.lib.common.task.AzureTask;
import com.microsoft.azure.toolkit.lib.common.task.AzureTaskManager;
import com.microsoft.azure.toolkit.lib.mysql.MySqlServer;
import com.microsoft.azure.toolkit.lib.mysql.model.MySqlDatabaseEntity;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.swing.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;

import static com.microsoft.azure.toolkit.lib.Azure.az;

public class MySQLPropertiesEditor extends BaseEditor {

    public static final String ID = "com.microsoft.intellij.helpers.mysql.MySQLPropertyView";
    private final MySqlServer server;

    private AzureHideableTitledSeparator overviewSeparator;
    private MySQLPropertyOverviewPanel overview;
    private AzureHideableTitledSeparator connectionSecuritySeparator;
    private ConnectionSecurityPanel connectionSecurity;
    private AzureHideableTitledSeparator connectionStringsSeparator;
    private ConnectionStringsOutputPanel connectionStringsJDBC;
    private ConnectionStringsOutputPanel connectionStringsSpring;
    private JPanel rootPanel;
    private JPanel contextPanel;
    private JScrollPane scrollPane;
    private MySQLPropertyActionPanel propertyActionPanel;
    private DatabaseComboBox databaseComboBox;
    private JLabel databaseLabel;
    public static final String MYSQL_OUTPUT_TEXT_PATTERN_SPRING =
            "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver" + System.lineSeparator() +
                    "spring.datasource.url=jdbc:mysql://%s:3306/%s?useSSL=true&requireSSL=false" + System.lineSeparator() +
                    "spring.datasource.username=%s" + System.lineSeparator() + "spring.datasource.password={your_password}";

    public static final String MYSQL_OUTPUT_TEXT_PATTERN_JDBC =
            "String url =\"jdbc:mysql://%s:3306/%s?useSSL=true&requireSSL=false\";" + System.lineSeparator() +
                    "myDbConn = DriverManager.getConnection(url, \"%s\", {your_password});";

    private Boolean originalAllowAccessToAzureServices;
    private Boolean originalAllowAccessToLocal;
    private final Project project;
    private final VirtualFile virtualFile;

    MySQLPropertiesEditor(@Nonnull Project project, @Nonnull MySqlServer server, @Nonnull final VirtualFile virtualFile) {
        super(virtualFile);
        this.project = project;
        this.server = server;
        this.virtualFile = virtualFile;
        overviewSeparator.addContentComponent(overview);
        connectionSecuritySeparator.addContentComponent(connectionSecurity);
        connectionStringsSeparator.addContentComponent(databaseLabel);
        connectionStringsSeparator.addContentComponent(databaseComboBox);
        connectionStringsSeparator.addContentComponent(connectionStringsJDBC);
        connectionStringsSeparator.addContentComponent(connectionStringsSpring);
        connectionStringsJDBC.getTitleLabel().setText("JDBC");
        connectionStringsJDBC.getOutputTextArea().setText(getConnectionString(MYSQL_OUTPUT_TEXT_PATTERN_JDBC, null, null, null));
        connectionStringsSpring.getTitleLabel().setText("Spring");
        connectionStringsSpring.getOutputTextArea().setText(getConnectionString(MYSQL_OUTPUT_TEXT_PATTERN_SPRING, null, null, null));
        init();
        initListeners();

        AzureEventBus.after("mysql|server.start", this::onMySqlServerStatusChanged);
        AzureEventBus.after("mysql|server.restart", this::onMySqlServerStatusChanged);
        AzureEventBus.after("mysql|server.stop", this::onMySqlServerStatusChanged);
        AzureEventBus.after("mysql|server.delete", this::onMySqlServerStatusDeleted);
        AzureEventBus.before("mysql|server.start", this::onMySqlServerStatusChanging);
        AzureEventBus.before("mysql|server.stop", this::onMySqlServerStatusChanging);
        AzureEventBus.before("mysql|server.restart", this::onMySqlServerStatusChanging);
        AzureEventBus.before("mysql|server.delete", this::onMySqlServerStatusDeleting);
    }

    private void onMySqlServerStatusChanged(MySqlServer server) {
        if (StringUtils.equalsIgnoreCase(this.server.id(), server.id())) {
            AzureTaskManager.getInstance().runOnPooledThread(() -> {
                this.server.refresh();
                AzureTaskManager.getInstance().runLater(this::refreshView);
            });
        }
    }

    private void onMySqlServerStatusChanging(MySqlServer server) {
        if (StringUtils.equalsIgnoreCase(this.server.id(), server.id())) {
            AzureTaskManager.getInstance().runLater(() -> overview.getStatusTextField().setText("Updating..."));
        }
    }

    private void onMySqlServerStatusDeleted(MySqlServer server) {
        if (StringUtils.equalsIgnoreCase(this.server.id(), server.id())) {
            this.closeEditor();
        }
    }

    private void onMySqlServerStatusDeleting(MySqlServer server) {
        if (StringUtils.equalsIgnoreCase(this.server.id(), server.id())) {
            AzureTaskManager.getInstance().runLater(() -> overview.getStatusTextField().setText("Deleting..."));
        }
    }

    private void closeEditor() {
        final FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
        AzureTaskManager.getInstance().runLater(() -> fileEditorManager.closeFile(virtualFile));
        AzureMessager.getMessager().info(
                String.format("The editor for MySQL server '%s' is closed.", this.server.name()),
                String.format("The MySQL server with name '%s' is deleted.", this.server.name()));
    }

    private String getConnectionString(final String pattern, final String hostname, final String database, final String username) {
        final String newHostname = StringUtils.isNotBlank(hostname) ? hostname : "{your_hostname}";
        final String newDatabase = StringUtils.isNotBlank(database) ? database : "{your_database}";
        final String newUsername = StringUtils.isNotBlank(username) ? username : "{your_username}";
        return String.format(pattern, newHostname, newDatabase, newUsername);
    }

    private void init() {
        originalAllowAccessToAzureServices = connectionSecurity.getAllowAccessFromAzureServicesCheckBox().getModel().isSelected();
        originalAllowAccessToLocal = connectionSecurity.getAllowAccessFromLocalMachineCheckBox().getModel().isSelected();
    }

    private void initListeners() {
        // update to trigger save/discard buttons
        connectionSecurity.getAllowAccessFromAzureServicesCheckBox().addItemListener(this::onCheckBoxChanged);
        connectionSecurity.getAllowAccessFromLocalMachineCheckBox().addItemListener(this::onCheckBoxChanged);
        // actions of copy buttons
        connectionStringsJDBC.getCopyButton().addActionListener(this::onJDBCCopyButtonClicked);
        connectionStringsSpring.getCopyButton().addActionListener(this::onSpringCopyButtonClicked);
        // save/discard buttons
        propertyActionPanel.getSaveButton().addActionListener(this::onSaveButtonClicked);
        propertyActionPanel.getDiscardButton().addActionListener(this::onDiscardButtonClicked);
        // database combox changed
        databaseComboBox.addItemListener(this::onDatabaseComboBoxChanged);
    }

    private void onCheckBoxChanged(ItemEvent itemEvent) {
        if (itemEvent.getStateChange() == ItemEvent.SELECTED || itemEvent.getStateChange() == ItemEvent.DESELECTED) {
            final boolean changed = this.changed();
            this.propertyActionPanel.getSaveButton().setEnabled(changed);
            this.propertyActionPanel.getDiscardButton().setEnabled(changed);
        }
    }

    private void onJDBCCopyButtonClicked(ActionEvent e) {
        final String text = this.connectionStringsJDBC.getOutputTextArea().getText();
        CopyPasteManager.getInstance().setContents(new StringSelection(text));
    }

    private void onSpringCopyButtonClicked(ActionEvent e) {
        final String text = this.connectionStringsSpring.getOutputTextArea().getText();
        CopyPasteManager.getInstance().setContents(new StringSelection(text));
    }

    private void onSaveButtonClicked(ActionEvent e) {
        final String actionName = "Saving";
        final String originalText = this.propertyActionPanel.getSaveButton().getText();
        this.propertyActionPanel.getSaveButton().setText(actionName);
        this.propertyActionPanel.getSaveButton().setEnabled(false);
        final Runnable runnable = () -> {
            final String subscriptionId = this.server.subscriptionId();
            // refresh property
            refreshProperty(subscriptionId, this.server.entity().getResourceGroupName(), this.server.name());
            final boolean allowAccessToAzureServices = connectionSecurity.getAllowAccessFromAzureServicesCheckBox().getModel().isSelected();
            final boolean allowAccessToLocal = connectionSecurity.getAllowAccessFromLocalMachineCheckBox().getModel().isSelected();
            if (!originalAllowAccessToAzureServices.equals(allowAccessToAzureServices)) {
                if (allowAccessToAzureServices) {
                    this.server.firewallRules().enableAzureAccessRule();
                } else {
                    this.server.firewallRules().disableAzureAccessRule();
                }
                originalAllowAccessToAzureServices = allowAccessToAzureServices;
            }
            if (!originalAllowAccessToLocal.equals(allowAccessToLocal)) {
                if (allowAccessToLocal) {
                    this.server.firewallRules().enableLocalMachineAccessRule(this.server.getPublicIpForLocalMachine());
                } else {
                    this.server.firewallRules().disableLocalMachineAccessRule();
                }
                originalAllowAccessToLocal = allowAccessToLocal;
            }
            this.propertyActionPanel.getSaveButton().setText(originalText);
            final boolean changed = this.changed();
            this.propertyActionPanel.getSaveButton().setEnabled(changed);
            this.propertyActionPanel.getDiscardButton().setEnabled(changed);
        };
        AzureTaskManager.getInstance().runInBackground(new AzureTask<>(this.project, String.format("%s...", actionName), false, runnable));
    }

    private void onDiscardButtonClicked(ActionEvent e) {
        this.propertyActionPanel.getSaveButton().setEnabled(false);
        this.propertyActionPanel.getDiscardButton().setEnabled(false);
        connectionSecurity.getAllowAccessFromAzureServicesCheckBox().setSelected(originalAllowAccessToAzureServices);
        connectionSecurity.getAllowAccessFromLocalMachineCheckBox().setSelected(originalAllowAccessToLocal);
    }

    private void onDatabaseComboBoxChanged(ItemEvent e) {
        if (e.getStateChange() == ItemEvent.SELECTED && e.getItem() instanceof MySqlDatabaseEntity) {
            final MySqlDatabaseEntity database = (MySqlDatabaseEntity) e.getItem();
            connectionStringsJDBC.getOutputTextArea().setText(getConnectionString(MYSQL_OUTPUT_TEXT_PATTERN_JDBC,
                    this.server.entity().getFullyQualifiedDomainName(), database.getName(), overview.getServerAdminLoginNameTextField().getText()));
            connectionStringsSpring.getOutputTextArea().setText(getConnectionString(MYSQL_OUTPUT_TEXT_PATTERN_SPRING,
                    this.server.entity().getFullyQualifiedDomainName(), database.getName(), overview.getServerAdminLoginNameTextField().getText()));
        }
    }

    private boolean changed() {
        return originalAllowAccessToAzureServices != connectionSecurity.getAllowAccessFromAzureServicesCheckBox().getModel().isSelected()
                || originalAllowAccessToLocal != connectionSecurity.getAllowAccessFromLocalMachineCheckBox().getModel().isSelected();
    }

    @Override
    public @NotNull JComponent getComponent() {
        return rootPanel;
    }

    @Override
    public @NotNull String getName() {
        return ID;
    }

    public void refreshView() {
        final String sid = server.entity().getSubscriptionId();
        if (!server.exists()) {
            this.closeEditor();
            return;
        }
        final Subscription subscription = az(AzureAccount.class).account().getSubscription(sid);
        if (subscription != null) {
            overview.getSubscriptionTextField().setText(subscription.getName());
            databaseComboBox.setServer(server);
            databaseComboBox.refreshItems();
        }
        overview.getResourceGroupTextField().setText(server.entity().getResourceGroupName());
        overview.getStatusTextField().setText(server.entity().getState());
        overview.getLocationTextField().setText(server.entity().getRegion().getLabel());
        overview.getSubscriptionIDTextField().setText(sid);
        overview.getServerNameTextField().setText(server.entity().getFullyQualifiedDomainName());
        overview.getServerNameTextField().setCaretPosition(0);
        overview.getServerAdminLoginNameTextField().setText(server.entity().getAdministratorLoginName() + "@" + server.name());
        overview.getServerAdminLoginNameTextField().setCaretPosition(0);
        overview.getMysqlVersionTextField().setText(server.entity().getVersion());
        final String skuTier = server.entity().getSkuTier();
        final int skuCapacity = server.entity().getVCore();
        final int storageGB = server.entity().getStorageInMB() / 1024;
        final String performanceConfigurations = skuTier + ", " + skuCapacity + " vCore(s), " + storageGB + " GB";
        overview.getPerformanceConfigurationsTextField().setText(performanceConfigurations);
        overview.getSslEnforceStatusTextField().setText(server.entity().getSslEnforceStatus());
        if (StringUtils.equalsIgnoreCase("READY", server.entity().getState())) {
            connectionSecuritySeparator.expand();
            connectionSecuritySeparator.setEnabled(true);
            connectionStringsSeparator.expand();
            connectionStringsSeparator.setEnabled(true);
            originalAllowAccessToAzureServices = server.firewallRules().isAzureAccessRuleEnabled();
            connectionSecurity.getAllowAccessFromAzureServicesCheckBox().setSelected(originalAllowAccessToAzureServices);
            originalAllowAccessToLocal = server.firewallRules().isLocalMachineAccessRuleEnabled();
            connectionSecurity.getAllowAccessFromLocalMachineCheckBox().setSelected(originalAllowAccessToLocal);
        } else {
            connectionSecuritySeparator.collapse();
            connectionSecuritySeparator.setEnabled(false);
            connectionStringsSeparator.collapse();
            connectionStringsSeparator.setEnabled(false);
        }
    }

    @Override
    public void dispose() {

    }
}
