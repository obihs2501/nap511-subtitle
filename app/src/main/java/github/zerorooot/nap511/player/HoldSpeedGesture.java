package github.zerorooot.nap511.player;

/** Pure gesture state, shared by the real touch handler and JVM regression tests. */
public final class HoldSpeedGesture {
    private boolean pending;
    private boolean active;
    private float downX;
    private float downY;

    public void down(float x, float y, boolean eligible) {
        reset();
        downX = x;
        downY = y;
        pending = eligible;
    }

    public void move(float x, float y, int pointers, float slop) {
        if (pointers != 1 || (!active &&
                (Math.abs(x - downX) > slop || Math.abs(y - downY) > slop))) pending = false;
    }

    public boolean activate(boolean playing, boolean unlocked) {
        if (!pending || !playing || !unlocked) return false;
        pending = false;
        active = true;
        return true;
    }

    public boolean isPending() { return pending; }
    public boolean isActive() { return active; }
    public void reset() { pending = false; active = false; }
}
