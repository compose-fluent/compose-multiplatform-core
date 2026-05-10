/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.compose.ui.platform

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.text.AnnotatedString
import io.github.composefluent.winrt.runtime.ComVtableInvoker
import io.github.composefluent.winrt.runtime.Guid
import io.github.composefluent.winrt.runtime.HString
import io.github.composefluent.winrt.runtime.HResult
import io.github.composefluent.winrt.runtime.IInspectableReference
import io.github.composefluent.winrt.runtime.IUnknownReference
import io.github.composefluent.winrt.runtime.IWinRTObject
import io.github.composefluent.winrt.runtime.PlatformAbi
import io.github.composefluent.winrt.runtime.WinRtAsyncProjectionInterop
import io.github.composefluent.winrt.runtime.WinRtInstanceProjectionInterop
import io.github.composefluent.winrt.runtime.WinRtReadOnlyListProjection
import io.github.composefluent.winrt.runtime.WinRtReferenceValueAdapters
import io.github.composefluent.winrt.runtime.WinRtStaticProjectionInterop
import io.github.composefluent.winrt.runtime.WinRtTypeSignature
import io.github.composefluent.winrt.runtime.await
import windows.applicationmodel.datatransfer.DataPackage
import windows.applicationmodel.datatransfer.Clipboard as WinRTClipboardClass
import windows.applicationmodel.datatransfer.StandardDataFormats as WinRTStandardDataFormats

actual typealias NativeClipboard = IUnknownReference

@Suppress("DEPRECATION")
internal class WinUIClipboardManager(
    private val clipboard: WinUIClipboard,
) : ClipboardManager {
    override fun getText(): AnnotatedString? =
        clipboard.getTextBlocking()?.let { AnnotatedString(it) }

    override fun setText(annotatedString: AnnotatedString) {
        clipboard.setText(annotatedString.text)
    }

    override fun hasText(): Boolean =
        clipboard.hasText()

    override fun getClip(): ClipEntry? =
        clipboard.getClipEntryBlocking()

    @Suppress("GetterSetterNames")
    override fun setClip(clipEntry: ClipEntry?) {
        clipboard.setClipEntryBlocking(clipEntry)
    }

    override val nativeClipboard: NativeClipboard
        get() = winUIClipboardStatics
}

internal class WinUIClipboard : Clipboard {
    override suspend fun getClipEntry(): ClipEntry? =
        readClipEntry()

    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        setClipEntryBlocking(clipEntry)
    }

    override val nativeClipboard: NativeClipboard
        get() = winUIClipboardStatics

    internal fun hasText(): Boolean =
        lastPlainText != null || getWinUIContent().contains(winUITextFormat)

    internal fun getTextBlocking(): String? {
        // KWINRT-012: WinRT DataPackageView.GetTextAsync cannot be synchronously
        // joined on the UI thread. Preserve Android-like ClipboardManager
        // setText/getText round-trips for in-process writes without blocking.
        lastPlainText?.let { return it }
        val content = getWinUIContent()
        if (!content.contains(winUITextFormat)) {
            return null
        }
        return null
    }

    internal fun setText(text: String) {
        lastPlainText = text
        // KWINRT-012: Windows clipboard can be transiently locked
        // (CLIPBRD_E_CANT_OPEN / OpenClipboard failed) during repeated
        // offscreen composition smokes. Keep Compose API state coherent and
        // treat the native clipboard write as best effort until the runtime has
        // a retry/dispatcher-safe helper.
        runCatching { setWinUIContent(DataPackage().apply { setText(text) }) }
    }

    internal fun getClipEntryBlocking(): ClipEntry? {
        lastPlainText?.let { return ClipEntry(it) }
        val content = getWinUIContent()
        if (content.availableFormats.isEmpty()) return null
        return ClipEntry(content)
    }

    private suspend fun readClipEntry(): ClipEntry? {
        lastPlainText?.let { return ClipEntry(it) }
        val content = getWinUIContent()
        if (content.availableFormats.isEmpty()) return null
        if (content.contains(winUITextFormat)) {
            return ClipEntry(content.getText())
        }
        return ClipEntry(content)
    }

    internal fun setClipEntryBlocking(clipEntry: ClipEntry?) {
        val dataPackage = when (val nativeClipEntry = clipEntry?.nativeClipEntry) {
            null -> {
                lastPlainText = null
                clearWinUIContent()
                return
            }
            is DataPackage -> nativeClipEntry
            is WinUIDataPackageView -> DataPackage().also { dataPackage ->
                if (nativeClipEntry.contains(winUITextFormat)) {
                    lastPlainText?.let(dataPackage::setText)
                }
            }
            is String -> DataPackage().apply {
                lastPlainText = nativeClipEntry
                setText(nativeClipEntry)
            }
            else -> return
        }
        if (clipEntry?.nativeClipEntry !is String) {
            lastPlainText = null
        }
        runCatching { setWinUIContent(dataPackage) }
    }

    private fun clearWinUIContent() {
        runCatching {
            // KWINRT-010: generated static class shells do not expose callable static members yet.
            WinRtStaticProjectionInterop.callUnit(winUIClipboardStatics, clipboardClearSlot)
        }
    }

    private fun setWinUIContent(dataPackage: DataPackage) {
        // KWINRT-010: generated static class shells do not expose callable static members yet.
        val hr = ComVtableInvoker.invokeArgs(
            instance = winUIClipboardStatics.pointer,
            slot = clipboardSetContentSlot,
            arg0 = PlatformAbi.fromRawComPtr((dataPackage as IWinRTObject).nativeObject.pointer),
        )
        HResult(hr).requireSuccess()
    }
}

actual class ClipEntry constructor(val nativeClipEntry: Any?) {
    actual val clipMetadata: ClipMetadata
        get() = ClipMetadata()

    @ExperimentalComposeUiApi
    fun getPlainText(): String? = when (val nativeClipEntry = nativeClipEntry) {
        is String -> nativeClipEntry
        else -> null
    }

    companion object {
        @ExperimentalComposeUiApi
        fun withPlainText(text: String): ClipEntry =
            ClipEntry(text)
    }
}

actual class ClipMetadata

private var lastPlainText: String? = null

// KWINRT-010: generated Clipboard and StandardDataFormats static shells expose
// StaticInterfaces, but not the public static members from those interfaces.
// Keep the ABI calls here until kotlin-winrt generates the forwarding members.
private val winUIClipboardStatics: IUnknownReference
    get() = WinRTClipboardClass.StaticInterfaces.iClipboardStatics()

private val winUIStandardDataFormatsStatics: IUnknownReference
    get() = WinRTStandardDataFormats.StaticInterfaces.iStandardDataFormatsStatics()

private val winUITextFormat: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
    WinRtInstanceProjectionInterop.getString(
        reference = winUIStandardDataFormatsStatics,
        slot = standardDataFormatsTextSlot,
    )
}

private fun getWinUIContent(): WinUIDataPackageView =
    WinRtStaticProjectionInterop.getProjectedRuntimeClass(
        reference = winUIClipboardStatics,
        slot = clipboardGetContentSlot,
        wrap = ::WinUIDataPackageView,
    )

// KWINRT-011: avoid generated DataPackageView.Metadata.wrap(...) here because
// samples can generate the same projection FQNs with different internal method
// names. Public RCW rewrap currently returns a SingleInterfaceOptimizedObject,
// so use the IDataPackageView ABI surface directly.
private class WinUIDataPackageView(
    private val inspectable: IInspectableReference,
) {
    private val defaultInterface: IUnknownReference by lazy(LazyThreadSafetyMode.PUBLICATION) {
        inspectable.queryInterface(dataPackageViewDefaultInterfaceIid).getOrThrow().use {
            IUnknownReference(it.getRefPointer(), dataPackageViewDefaultInterfaceIid)
        }
    }

    val availableFormats: List<String>
        get() = PlatformAbi.confinedScope().use { scope ->
            val resultOut = PlatformAbi.allocatePointerSlot(scope)
            val hr = ComVtableInvoker.invokeArgs(
                instance = defaultInterface.pointer,
                slot = dataPackageViewAvailableFormatsGetterSlot,
                arg0 = resultOut,
            )
            HResult(hr).requireSuccess()
            WinRtReadOnlyListProjection.fromAbi(
                pointer = PlatformAbi.readPointer(resultOut),
                elementAdapter = WinRtReferenceValueAdapters.string,
            ) ?: emptyList()
        }

    fun contains(formatId: String): Boolean =
        HString.createReference(formatId).use { formatIdAbi ->
            PlatformAbi.confinedScope().use { scope ->
                val resultOut = PlatformAbi.allocateInt8Slot(scope)
                val hr = ComVtableInvoker.invokeArgs(
                    instance = defaultInterface.pointer,
                    slot = dataPackageViewContainsSlot,
                    arg0 = formatIdAbi.handle,
                    arg1 = resultOut,
                )
                HResult(hr).requireSuccess()
                PlatformAbi.readInt8(resultOut).toInt() != 0
            }
    }

    suspend fun getText(): String =
        PlatformAbi.confinedScope().use { scope ->
            val resultOut = PlatformAbi.allocatePointerSlot(scope)
            val hr = ComVtableInvoker.invokeArgs(
                instance = defaultInterface.pointer,
                slot = dataPackageViewGetTextAsyncSlot,
                arg0 = resultOut,
            )
            HResult(hr).requireSuccess()
            WinRtAsyncProjectionInterop.operation(
                pointer = PlatformAbi.readPointer(resultOut),
                resultSignature = WinRtTypeSignature.string(),
                resultOut = { operationScope -> PlatformAbi.allocatePointerSlot(operationScope) },
                resultReader = { operationResultOut ->
                    HString.fromHandle(PlatformAbi.readPointer(operationResultOut), owner = true)
                        .use { it.toKString() }
                },
            ).await()
        }
}

private const val clipboardGetContentSlot = 6
private const val clipboardSetContentSlot = 7
private const val clipboardClearSlot = 9
private const val standardDataFormatsTextSlot = 6
private val dataPackageViewDefaultInterfaceIid = Guid("7B840471-5900-4D85-A90B-10CB85FE3552")
private const val dataPackageViewAvailableFormatsGetterSlot = 9
private const val dataPackageViewContainsSlot = 10
private const val dataPackageViewGetTextAsyncSlot = 12
