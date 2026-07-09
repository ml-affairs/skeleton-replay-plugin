package com.mlaffairs.skeleton.plugin

import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.Alarm
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.math.max

@Service(Service.Level.PROJECT)
class SkeletonIdeNavigationService(private val project: Project) {
    private val decoder = SkeletonReplaySelectionDecoder()
    private val debouncer = SkeletonReplaySelectionDebouncer()
    private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, project)
    private var followEnabled = true
    private var activeHighlight: RangeHighlighter? = null
    private var lastActivatedFocusKey: String? = null

    fun isFollowEnabled(): Boolean = followEnabled

    fun setFollowEnabled(enabled: Boolean) {
        followEnabled = enabled
        if (enabled) {
            lastActivatedFocusKey = null
            debouncer.reset()
        } else {
            alarm.cancelAllRequests()
            clearHighlight()
        }
    }

    fun disengage() {
        followEnabled = false
        alarm.cancelAllRequests()
        debouncer.reset()
        lastActivatedFocusKey = null
        clearHighlight()
    }

    fun consumeBridgePayload(payloadJson: String) {
        if (!followEnabled) {
            return
        }
        val selection = runCatching { decoder.decode(payloadJson) }.getOrNull() ?: return
        if (!debouncer.submit(selection)) {
            return
        }
        alarm.cancelAllRequests()
        alarm.addRequest({ applyLatestSelection() }, NAVIGATION_DEBOUNCE_MS)
    }

    private fun applyLatestSelection() {
        if (!followEnabled) {
            return
        }
        val selection = debouncer.drainLatest() ?: return
        val endpoint = SkeletonReplayEndpointResolver(::hasProjectLocalSourceFile).resolve(selection) ?: return
        val focusKey = endpoint.focusKey()
        if (focusKey == lastActivatedFocusKey) {
            return
        }
        lastActivatedFocusKey = focusKey
        navigateTo(endpoint)
    }

    private fun hasProjectLocalSourceFile(endpoint: SkeletonReplayEndpoint): Boolean =
        endpoint.file?.let(::findProjectLocalFile) != null

    private fun findProjectLocalFile(filePath: String): VirtualFile? {
        val path = try {
            Path.of(filePath)
        } catch (_: InvalidPathException) {
            return null
        }
        val virtualFile = VfsUtil.findFile(path, true) ?: return null
        return virtualFile.takeIf { ProjectFileIndex.getInstance(project).isInContent(it) }
    }

    private fun navigateTo(endpoint: SkeletonReplayEndpoint) {
        val virtualFile = endpoint.file?.let(::findProjectLocalFile) ?: return
        ProjectView.getInstance(project).select(null, virtualFile, false)
        val lineIndex = max(0, (endpoint.line ?: 1) - 1)
        val editor = FileEditorManager.getInstance(project).openTextEditor(OpenFileDescriptor(project, virtualFile, lineIndex, 0), true) ?: return
        highlightEndpoint(editor, virtualFile, lineIndex)
    }

    private fun highlightEndpoint(editor: Editor, virtualFile: VirtualFile, lineIndex: Int) {
        clearHighlight()

        val document = editor.document
        if (document.lineCount == 0) {
            return
        }
        val boundedLine = lineIndex.coerceIn(0, document.lineCount - 1)
        val lineStart = document.getLineStartOffset(boundedLine)
        val lineEnd = document.getLineEndOffset(boundedLine)
        PsiDocumentManager.getInstance(project).commitDocument(document)
        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document)
            ?: com.intellij.psi.PsiManager.getInstance(project).findFile(virtualFile)
        val highlightRange = psiFile
            ?.findElementAt(lineStart.coerceAtMost(max(0, document.textLength - 1)))
            ?.let(::findPythonFunctionParent)
            ?.textRange
            ?.takeIf { range -> range.startOffset < range.endOffset }
            ?: TextRange(lineStart, lineEnd)

        val attributes = EditorColorsManager.getInstance()
            .globalScheme
            .getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES)
        activeHighlight = editor.markupModel.addRangeHighlighter(
            highlightRange.startOffset,
            highlightRange.endOffset,
            HighlighterLayer.SELECTION - 1,
            attributes,
            HighlighterTargetArea.EXACT_RANGE,
        )
    }

    private fun clearHighlight() {
        activeHighlight?.let { oldHighlight ->
            runCatching { oldHighlight.dispose() }
        }
        activeHighlight = null
    }

    private fun findPythonFunctionParent(element: PsiElement): PsiElement? =
        generateSequence(element) { current -> current.parent }
            .firstOrNull { current ->
                current.javaClass.name.startsWith("com.jetbrains.python.psi.") &&
                    current.javaClass.simpleName.contains("PyFunction")
            }

    companion object {
        private const val NAVIGATION_DEBOUNCE_MS = 120

        fun getInstance(project: Project): SkeletonIdeNavigationService = project.getService(SkeletonIdeNavigationService::class.java)
    }
}
