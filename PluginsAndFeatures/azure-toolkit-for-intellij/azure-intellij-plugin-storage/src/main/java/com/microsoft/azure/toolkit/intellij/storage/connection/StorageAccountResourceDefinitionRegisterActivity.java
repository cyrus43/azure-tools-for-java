/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.intellij.storage.connection;

import com.intellij.openapi.application.PreloadingActivity;
import com.intellij.openapi.progress.ProgressIndicator;
import com.microsoft.azure.toolkit.intellij.connector.ResourceManager;
import org.jetbrains.annotations.NotNull;

public class StorageAccountResourceDefinitionRegisterActivity extends PreloadingActivity {
    @Override
    public void preload(@NotNull ProgressIndicator progressIndicator) {
        ResourceManager.registerDefinition(StorageAccountResourceDefinition.INSTANCE);
    }
}
