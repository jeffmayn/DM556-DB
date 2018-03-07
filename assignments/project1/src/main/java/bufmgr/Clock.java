package bufmgr;

public class Clock extends Replacer{


	protected static final int AVAILABLE = 1;
	protected static final int REFERENCED = 2;
	protected static final int PINNED = 3;

	protected int head;

	protected Clock(BufMgr bufmgr) {
		super(bufmgr);
		for (int i = 0; i < frametab.length; i++) {
      frametab[i].state = AVAILABLE;
    }
		head = -1;
	}

	@Override
	public void newPage(FrameDesc fdesc) {
		// TODO Auto-generated method stub

	}

	@Override
	public void freePage(FrameDesc fdesc) {
		fdesc.state = AVAILABLE;
	}

	@Override
	public void pinPage(FrameDesc fdesc) {
		fdesc.state = PINNED;
	}

	@Override
	public void unpinPage(FrameDesc fdesc) {
		if (0 == fdesc.pincnt)
		fdesc.state = REFERENCED;
	}

	@Override
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
}
