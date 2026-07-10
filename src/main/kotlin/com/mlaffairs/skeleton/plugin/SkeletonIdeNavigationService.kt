package com.mlaffairs.skeleton.plugin

import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.TextAttributes
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
import java.awt.Color
import java.awt.Font
import java.awt.Graphics
import java.awt.Rectangle
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
    private var activeInlay: Inlay<*>? = null
    private var lastActivatedFocusKey: String? = null
    private var traceIndex: SkeletonTraceIndex = SkeletonTraceIndex.EMPTY

    fun isFollowEnabled(): Boolean = followEnabled

    fun setTraceIndex(traceIndex: SkeletonTraceIndex) {
        this.traceIndex = traceIndex
        debouncer.reset()
        lastActivatedFocusKey = null
        clearHighlight()
    }

    fun clearTraceIndex() {
        setTraceIndex(SkeletonTraceIndex.EMPTY)
    }

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
        val endpoint = traceIndex.eventFor(selection)?.callee
            ?.takeIf { candidate -> candidate.file != null && hasProjectLocalSourceFile(candidate) }
            ?: SkeletonReplayEndpointResolver(::hasProjectLocalSourceFile).resolve(selection)
            ?: return
        val focusKey = endpoint.focusKey()
        lastActivatedFocusKey = focusKey
        navigateTo(endpoint, SkeletonTraceInlayFormatter.textForSelection(selection, traceIndex))
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

    private fun navigateTo(endpoint: SkeletonReplayEndpoint, inlayText: String?) {
        val virtualFile = endpoint.file?.let(::findProjectLocalFile) ?: return
        ProjectView.getInstance(project).select(null, virtualFile, false)
        val lineIndex = max(0, (endpoint.line ?: 1) - 1)
        val editor = FileEditorManager.getInstance(project).openTextEditor(OpenFileDescriptor(project, virtualFile, lineIndex, 0), true) ?: return
        highlightEndpoint(editor, virtualFile, lineIndex, endpoint.callable_kind, inlayText)
    }

    private fun highlightEndpoint(editor: Editor, virtualFile: VirtualFile, lineIndex: Int, callableKind: String?, inlayText: String?) {
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

        val attributes = highlightAttributes(callableKind)
        activeHighlight = editor.markupModel.addRangeHighlighter(
            highlightRange.startOffset,
            highlightRange.endOffset,
            HighlighterLayer.SELECTION - 1,
            attributes,
            HighlighterTargetArea.EXACT_RANGE,
        )
        if (inlayText != null) {
            activeInlay = editor.inlayModel.addAfterLineEndElement(
                lineEnd,
                true,
                SkeletonTraceInlayRenderer(inlayText, callableKind),
            )
        }
    }

    private fun clearHighlight() {
        activeHighlight?.let { oldHighlight ->
            runCatching { oldHighlight.dispose() }
        }
        activeHighlight = null
        activeInlay?.let { oldInlay ->
            runCatching { oldInlay.dispose() }
        }
        activeInlay = null
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

private class SkeletonTraceInlayRenderer(
    private val text: String,
    callableKind: String?,
) : EditorCustomElementRenderer {
    private val accent = callableKind.accentColor()

    override fun calcWidthInPixels(inlay: Inlay<*>): Int {
        val font = inlay.editor.colorsScheme.getFont(com.intellij.openapi.editor.colors.EditorFontType.PLAIN)
        return inlay.editor.contentComponent.getFontMetrics(font).stringWidth(text) + 18
    }

    override fun paint(inlay: Inlay<*>, graphics: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
        val copy = graphics.create()
        try {
            copy.font = inlay.editor.colorsScheme.getFont(com.intellij.openapi.editor.colors.EditorFontType.PLAIN).deriveFont(Font.PLAIN)
            copy.color = accent
            copy.fillRect(targetRegion.x + 4, targetRegion.y + 3, 2, max(1, targetRegion.height - 6))
            copy.color = INLAY_GREY
            copy.drawString(text, targetRegion.x + 10, targetRegion.y + inlay.editor.ascent)
        } finally {
            copy.dispose()
        }
    }
}

private fun highlightAttributes(callableKind: String?): TextAttributes =
    TextAttributes(
        null,
        callableKind.accentColor(52),
        callableKind.accentColor(),
        null,
        Font.PLAIN,
    ).takeUnless { callableKind == null }
        ?: EditorColorsManager.getInstance().globalScheme.getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES)

private fun String?.accentColor(alpha: Int = 255): Color =
    when (this) {
        "instance_method" -> Color(245, 158, 11, alpha)
        "class_method" -> Color(249, 115, 22, alpha)
        "static_method" -> Color(234, 179, 8, alpha)
        "module_function" -> Color(34, 197, 94, alpha)
        else -> Color(56, 220, 226, alpha)
    }

private val INLAY_GREY = Color(128, 139, 152)
