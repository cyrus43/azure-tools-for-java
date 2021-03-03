/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.intellij.link;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.microsoft.azure.toolkit.intellij.common.AzureDialog;
import com.microsoft.azure.toolkit.intellij.link.mysql.BasicLinkMySQLPanel;
import com.microsoft.azure.toolkit.intellij.link.mysql.MySQLResourceConfig;
import com.microsoft.azure.toolkit.lib.common.form.AzureForm;
import com.microsoft.azure.toolkit.lib.common.task.AzureTask;
import com.microsoft.azure.toolkit.lib.common.task.AzureTaskManager;
import com.microsoft.azure.toolkit.lib.link.AzureLinkService;
import com.microsoft.tooling.msservices.components.DefaultLoader;
import com.microsoft.tooling.msservices.serviceexplorer.azure.mysql.MySQLNode;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class LinkMySQLToModuleDialog extends AzureDialog<LinkConfig<MySQLResourceConfig, ModuleResourceConfig>> {
    private JPanel rootPanel;
    private BasicLinkMySQLPanel basicPanel;

    private MySQLNode node;
    private Project project;
    private Module module;

    public LinkMySQLToModuleDialog(Project project, MySQLNode node, Module module) {
        super(project);
        this.project = project;
        this.node = node;
        this.module = module;
        this.init();
        // listener
        this.setOkActionListener((data) -> doLink(data, project, this));
    }

    @Override
    public AzureForm<LinkConfig<MySQLResourceConfig, ModuleResourceConfig>> getForm() {
        return basicPanel;
    }

    @Override
    protected String getDialogTitle() {
        return "Connect Azure Database for MySQL";
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        return rootPanel;
    }

    private void createUIComponents() {
        // TODO: place custom component creation code here
        MySQLResourceConfig source = MySQLResourceConfig.getDefaultConfig(node);
        ModuleResourceConfig target = ModuleResourceConfig.getDefaultConfig(module);
        LinkConfig<MySQLResourceConfig, ModuleResourceConfig> linkComposite = new LinkConfig<>(source, target);
        basicPanel = new BasicLinkMySQLPanel(project, () -> linkComposite);
    }

    private void doLink(LinkConfig<MySQLResourceConfig, ModuleResourceConfig> linkComposite, Project project, LinkMySQLToModuleDialog dialog) {
        final Runnable runnable = () -> {
            final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
            indicator.setIndeterminate(true);
            DefaultLoader.getIdeHelper().invokeLater(dialog::close);
            AzureLinkService.getInstance().link(project, linkComposite);
        };
        String progressMessage = "Linking Azure Database for MySQL with Module...";
        final AzureTask task = new AzureTask(null, progressMessage, false, runnable);
        AzureTaskManager.getInstance().runInBackground(task);
    }

}
