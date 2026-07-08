package com.mlaffairs.skeleton.plugin;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

public final class SkeletonToolWindowFactory implements ToolWindowFactory {
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        SkeletonWorkbenchPanel workbench = SkeletonWorkbenchService.Companion.getInstance(project).createPanel();
        Content content = ContentFactory.getInstance().createContent(workbench.getComponent(), "Workbench", false);
        toolWindow.getContentManager().addContent(content);
    }
}
