package github.zerorooot.nap511.player

import org.junit.Assert.*
import org.junit.Test

class HoldSpeedGestureTest {
    @Test fun pressOnPlayingVideoActivatesUntilRelease() {
        val gesture = HoldSpeedGesture()
        gesture.down(100f, 200f, true)
        assertTrue(gesture.isPending)
        assertTrue(gesture.activate(true, true))
        assertTrue(gesture.isActive)
        assertFalse(gesture.isPending)
        gesture.reset()
        assertFalse(gesture.isActive)
        assertFalse(gesture.activate(true, true))
    }
    @Test fun pressingControlsNeverActivates() {
        val gesture = HoldSpeedGesture()
        gesture.down(100f, 200f, false)
        assertFalse(gesture.activate(true, true))
    }
    @Test fun slidingCancelsPendingHoldButAllowsNormalSeeking() {
        val gesture = HoldSpeedGesture()
        gesture.down(100f, 200f, true)
        gesture.move(125f, 200f, 1, 20f)
        assertFalse(gesture.activate(true, true))
        gesture.down(100f, 200f, true)
        gesture.move(100f, 175f, 1, 20f)
        assertFalse(gesture.activate(true, true))
    }
    @Test fun minorFingerMovementDoesNotCancelHold() {
        val gesture = HoldSpeedGesture()
        gesture.down(100f, 200f, true)
        gesture.move(108f, 193f, 1, 20f)
        assertTrue(gesture.activate(true, true))
    }
    @Test fun secondFingerCancelsPendingHold() {
        val gesture = HoldSpeedGesture()
        gesture.down(100f, 200f, true)
        gesture.move(100f, 200f, 2, 20f)
        assertFalse(gesture.activate(true, true))
    }
    @Test fun pauseOrLockBeforeTimeoutPreventsActivation() {
        val gesture = HoldSpeedGesture()
        gesture.down(100f, 200f, true)
        assertFalse(gesture.activate(false, true))
        gesture.reset()
        gesture.down(100f, 200f, true)
        assertFalse(gesture.activate(true, false))
    }
    @Test fun activeHoldSurvivesMovementButNotCancelOrANewGesture() {
        val gesture = HoldSpeedGesture()
        gesture.down(100f, 200f, true)
        assertTrue(gesture.activate(true, true))
        gesture.move(400f, 500f, 1, 20f)
        assertTrue(gesture.isActive)
        gesture.reset()
        assertFalse(gesture.isActive)
        gesture.down(10f, 10f, false)
        assertFalse(gesture.isActive)
        assertFalse(gesture.isPending)
    }
    @Test fun timeoutCannotActivateTwice() {
        val gesture = HoldSpeedGesture()
        gesture.down(100f, 200f, true)
        assertTrue(gesture.activate(true, true))
        assertFalse(gesture.activate(true, true))
    }
}
