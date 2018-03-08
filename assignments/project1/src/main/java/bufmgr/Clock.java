package bufmgr;

public class Clock extends Replacer{

	// TAGS for each state of a page
	protected static final int free = 1;
	protected static final int no_reference = 2;
	protected static final int pinned = 3;

	// a pointer to keep track of location in the frame table.
	protected int pointer;

	/*
	*	We start by initializing the buffermanager and then set all the frames
		to the tag free.
	*/
	protected Clock(BufMgr bufmgr) {
		super(bufmgr);
		for (int i = 0; i < frametab.length; i++) {
      frametab[i].state = free;
    }
	}

	@Override
	public void newPage(FrameDesc fdesc) {
		// There is no need for this function because
		// we evaluate the need of a new page in the buffermanager.
	}

	@Override
	public void freePage(FrameDesc fdesc) {
		fdesc.state = free;
	}

	@Override
	public void pinPage(FrameDesc fdesc) {
		fdesc.state = pinned;
	}

	@Override
	public void unpinPage(FrameDesc fdesc) {
		if (0 == fdesc.pincnt)
		fdesc.state = no_reference;
	}

	/*
	* We want to pick a victim that is free. Meaning look through the
	* frame table for a free page. If a page has no reference we set its tag to
	*	free. We go through the frame table once more to catch the pages which are
	*	changed from no_reference to free.
	*/
	@Override
	public int pickVictim() {
		for ( int i = 0 ; i < frametab.length << 1; i++ ) {

			final FrameDesc fd = frametab[pointer];

			if(free == fd.state){
				return pointer;
			}
			if(no_reference == fd.state){
				fd.state = free;
			}
			pointer = (pointer+1) % frametab.length;
		}
		return -1;
	}
}
