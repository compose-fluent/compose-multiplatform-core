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

package androidx.compose.ui.semantics

import androidx.compose.ui.unit.IntRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WinUISemanticsRegionTest {
    @Test
    fun intersectUpdatesBoundsToSharedArea() {
        val region = SemanticsRegion().also {
            it.set(IntRect(0, 0, 20, 20))
        }
        val clip = SemanticsRegion().also {
            it.set(IntRect(10, 5, 30, 15))
        }

        assertTrue(region.intersect(clip))

        assertEquals(IntRect(10, 5, 20, 15), region.bounds)
        assertFalse(region.isEmpty)
    }

    @Test
    fun intersectClearsWhenRegionsDoNotOverlap() {
        val region = SemanticsRegion().also {
            it.set(IntRect(0, 0, 10, 10))
        }
        val clip = SemanticsRegion().also {
            it.set(IntRect(20, 20, 30, 30))
        }

        assertFalse(region.intersect(clip))

        assertTrue(region.isEmpty)
        assertEquals(IntRect.Zero, region.bounds)
    }

    @Test
    fun differenceRemovesPartialOverlapWithoutClearingRegion() {
        val region = SemanticsRegion().also {
            it.set(IntRect(0, 0, 20, 20))
        }

        assertTrue(region.difference(IntRect(0, 0, 10, 20)))

        assertFalse(region.isEmpty)
        assertEquals(IntRect(10, 0, 20, 20), region.bounds)
    }

    @Test
    fun differenceKeepsBoundingBoxWhenMiddleIsRemoved() {
        val region = SemanticsRegion().also {
            it.set(IntRect(0, 0, 30, 30))
        }

        assertTrue(region.difference(IntRect(10, 10, 20, 20)))

        assertFalse(region.isEmpty)
        assertEquals(IntRect(0, 0, 30, 30), region.bounds)
    }

    @Test
    fun differenceClearsFullyCoveredRegion() {
        val region = SemanticsRegion().also {
            it.set(IntRect(0, 0, 10, 10))
        }

        assertFalse(region.difference(IntRect(0, 0, 10, 10)))

        assertTrue(region.isEmpty)
        assertEquals(IntRect.Zero, region.bounds)
    }
}
