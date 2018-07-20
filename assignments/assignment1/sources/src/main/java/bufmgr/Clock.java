package bufmgr;

/**
 * The "Clock" replacement policy.
 */

class Clock extends Replacer {

  // Frame State Constants

  protected static final int AVAILABLE = 10;
  protected static final int REFERENCED = 11;
  protected static final int PINNED = 12;

  /** Clock head; required for the default clock algorithm. */
  protected int head;

  /**
   * Constructs a clock replacer.
   */

  public Clock(BufMgr bufmgr) {

    super(bufmgr);

    // Initialize the frame states to AVAILABLE
    for (int i = 0; i < frametab.length; i++) {
      frametab[i].state = AVAILABLE;
    }

    // Initialize the clock head
    head = -1;

  } // public Clock(BufMgr bufmgr)

  /**
   * Notifies the replacer of a new page.
   */

  public void newPage(FrameDesc fdesc) {
    // no need to update frame state
  }

  /**
   * Notifies the replacer of a free page.
   */

  public void freePage(FrameDesc fdesc) {

    fdesc.state = AVAILABLE;

  }

  /**
   * Notifies the replacer of a pined page.
   */
  public void pinPage(FrameDesc fdesc) {

	   fdesc.state = PINNED;

  }

  /**
   * Notifies the replacer of an unpinned page.
   */

  public void unpinPage(FrameDesc fdesc) {

	  if (0 == fdesc.pincnt)
		  fdesc.state = REFERENCED;

  }

  /**
   * Selects the best frame to use for pinning a new page.
   *
   * @return victim frame number, or -1 if none available
   */

  public int pickVictim() {
    for ( int i = 0 ; i < frametab.length << 1; i++ ) {
      head = (head+1)%frametab.length;

      final FrameDesc fd = frametab[head];

      if(AVAILABLE == fd.state){
        return head;
      }
      if(REFERENCED == fd.state){
        fd.state = AVAILABLE;
      }
    }
    return -1;
  }
} // class Clock extends Replacer
