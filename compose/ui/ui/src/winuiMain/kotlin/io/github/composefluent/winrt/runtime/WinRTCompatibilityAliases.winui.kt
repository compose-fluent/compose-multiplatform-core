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

package io.github.composefluent.winrt.runtime

import microsoft.ui.xaml.data.INotifyPropertyChanged
import windows.foundation.EventRegistrationToken as FoundationEventRegistrationToken

// TODO(KWINRT-048): Remove after kotlin-winrt generator and runtime snapshots agree
// on the Windows.Foundation-owned built-in type and XAML notifier facade names.
typealias EventRegistrationToken = FoundationEventRegistrationToken

object WinRTPropertyChangedNotifierProjection {
    fun fromAbi(reference: IUnknownReference): INotifyPropertyChanged =
        INotifyPropertyChangedProjection.fromAbi(reference)

    fun fromAbi(reference: IInspectableReference): INotifyPropertyChanged =
        INotifyPropertyChangedProjection.fromAbi(reference)

    fun fromAbi(pointer: RawAddress): INotifyPropertyChanged? =
        INotifyPropertyChangedProjection.fromAbi(pointer)
}
