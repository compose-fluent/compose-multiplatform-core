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

import androidx.compose.runtime.snapshots.Snapshot
import microsoft.ui.dispatching.DispatcherQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch

internal object GlobalSnapshotManager {
    private var started = false
    private var sent = false

    fun ensureStarted(dispatcherQueue: DispatcherQueue) {
        if (started) return
        started = true
        val channel = Channel<Unit>(1)
        CoroutineScope(WinUIDispatcher(dispatcherQueue)).launch {
            channel.consumeEach {
                sent = false
                Snapshot.sendApplyNotifications()
            }
        }
        Snapshot.registerGlobalWriteObserver {
            if (!sent) {
                sent = true
                channel.trySend(Unit)
            }
        }
    }
}
