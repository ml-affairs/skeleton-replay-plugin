package com.mlaffairs.skeleton.plugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefJSQuery
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter

class SkeletonReportBridge(
    private val browser: JBCefBrowser,
    private val onSelectionPayload: (String) -> Unit,
) {
    private val query = JBCefJSQuery.create(browser)
    private var installed = false

    fun install() {
        if (installed) {
            return
        }
        installed = true
        query.addHandler { payloadJson ->
            onSelectionPayload(payloadJson)
            null
        }
        browser.jbCefClient.addLoadHandler(
            object : CefLoadHandlerAdapter() {
                override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                    if (frame?.isMain != true) {
                        return
                    }
                    ApplicationManager.getApplication().invokeLater {
                        inject()
                    }
                }
            },
            browser.cefBrowser,
        )
    }

    private fun inject() {
        val script = """
            (function() {
              window.SkeletonReplay = window.SkeletonReplay || {};
              const previousHandler = window.SkeletonReplay.onSelectionChanged;
              const sendToIde = function(payload) {
                if (!payload) return;
                ${query.inject("JSON.stringify(payload)")}
              };
              window.SkeletonReplay.onSelectionChanged = function(payload) {
                window.SkeletonReplay.currentSelection = payload;
                sendToIde(payload);
                if (typeof previousHandler === "function" && previousHandler !== window.SkeletonReplay.onSelectionChanged) {
                  try {
                    previousHandler(payload);
                  } catch (error) {
                    console.warn("SkeletonReplay onSelectionChanged handler failed", error);
                  }
                }
              };
              if (window.SkeletonReplay.currentSelection) {
                sendToIde(window.SkeletonReplay.currentSelection);
              }
            })();
        """.trimIndent()
        browser.cefBrowser.executeJavaScript(script, browser.cefBrowser.url, 0)
    }
}
