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

package androidx.compose.ui.input.indirect

internal class WinUIIndirectPointerEvent(
    override val changes: List<IndirectPointerInputChange>,
    override val type: IndirectPointerEventType,
    override val primaryDirectionalMotionAxis: IndirectPointerEventPrimaryDirectionalMotionAxis,
) : PlatformIndirectPointerEvent {
    init {
        require(changes.isNotEmpty()) { "changes cannot be empty" }
    }
}

/**
 * Create an [IndirectPointerEvent] for WinUI test use cases.
 *
 * In production this event should come from the platform input pipeline.
 */
fun IndirectPointerEvent(
    changes: List<IndirectPointerInputChange>,
    type: IndirectPointerEventType,
    primaryDirectionalMotionAxis: IndirectPointerEventPrimaryDirectionalMotionAxis =
        IndirectPointerEventPrimaryDirectionalMotionAxis.None,
): IndirectPointerEvent =
    WinUIIndirectPointerEvent(
        changes = changes,
        type = type,
        primaryDirectionalMotionAxis = primaryDirectionalMotionAxis,
    )
