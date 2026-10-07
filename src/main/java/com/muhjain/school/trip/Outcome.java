package com.muhjain.school.trip;

/**
 * What the attendant answered for one child.
 * <ul>
 * <li>DONE: the child boarded or arrived.</li>
 * <li>ABSENT: the child did not come.</li>
 * <li>NOT_TRAVELLING: evening only. The child went home with a parent, not by bus.</li>
 * <li>CLEARED: undo. It is only a command. It is never saved, it deletes the row.</li>
 * </ul>
 */
public enum Outcome {

	DONE, ABSENT, NOT_TRAVELLING, CLEARED

}
