package bufmgr;

import java.util.HashMap;

import global.GlobalConst;
import global.Minibase;
import global.Page;
import global.PageId;
import java.util.Arrays;

/**
 * <h3>Minibase Buffer Manager</h3> The buffer manager reads disk pages into a
 * main memory page as needed. The collection of main memory pages (called
 * frames) used by the buffer manager for this purpose is called the buffer
 * pool. This is just an array of Page objects. The buffer manager is used by
 * access methods, heap files, and relational operators to read, write,
 * allocate, and de-allocate pages.
 */
@SuppressWarnings("unused")
public class BufMgr implements GlobalConst {

	/** Actual pool of pages (can be viewed as an array of byte arrays). */
	protected Page[] bufpool;

	/** Array of descriptors, each containing the pin count, dirty status, etc. */
	protected FrameDesc[] frametab;

	/** Maps current page numbers to frames; used for efficient lookups. */
	protected HashMap<Integer, FrameDesc> pagemap;

	/** The replacement policy to use. */
	protected Replacer replacer;

	/**
	 * Constructs a buffer manager with the given settings.
	 *
	 * @param numbufs: number of pages in the buffer pool
	 */

	 /*
	 *	Here we initialize our buffermanager with the variable numbufs which determines
	 * 	the size of the pool, and initialize the frame table in the arrays.
	 *	We initialize our HashMap for efficient lookup and our replace policy as the clock policy.
	 */
	public BufMgr(int numbufs) {
	    // initialize the buffer pool and frame table
	    bufpool = new Page[numbufs];
	    frametab = new FrameDesc[numbufs];
	    for (int i = 0; i < numbufs; i++) {
	      bufpool[i] = new Page();
	      frametab[i] = new FrameDesc(i);
	    }

	    // initialize the specialized page map and replacer
	    pagemap = new HashMap<Integer, FrameDesc>(numbufs);
	    replacer = new Clock(this);
	}

	/**
	 * Allocates a set of new pages, and pins the first one in an appropriate
	 * frame in the buffer pool.
	 *
	 * @param firstpg
	 *            holds the contents of the first page
	 * @param run_size
	 *            number of new pages to allocate
	 * @return page id of the first new page
	 * @throws IllegalArgumentException
	 *             if PIN_MEMCPY and the page is pinned
	 * @throws IllegalStateException
	 *             if all pages are pinned (i.e. pool exceeded)
	 */

	 /*
	 *	('Minibase.DiskManager.deallocate_page(firstid,run_size)': is only code we wrote)
	 *	Removed the for-loop because it was made redundant by the deallocate_page-function.
	 *	If we somehow cant pin the page, we just call the function deallocate_page which points to our
	 *	newly allocaled memory (first page) and then deallocate it based on the number of elements (run_size)
	 *
	 *	If we succeed in pinning the page(s) we tell the replacer that it has got a new page.
	 */
	public PageId newPage(Page firstpg, int run_size) {
		// allocate the run
		PageId firstid = Minibase.DiskManager.allocate_page(run_size);

		// try to pin the first page
		try {
			pinPage(firstid, firstpg, PIN_MEMCPY);
		} catch (RuntimeException exc) {
		  Minibase.DiskManager.deallocate_page(firstid,run_size);
		  throw exc;
		}
		// notify the replacer and return the first new page id
		replacer.newPage(pagemap.get(firstid.pid));
		return firstid;
	}

	/**
	 * Deallocates a single page from disk, freeing it from the pool if needed.
	 * Call Minibase.DiskManager.deallocate_page(pageno) to deallocate the page before return.
	 *
	 * @param pageno
	 *            identifies the page to remove
	 * @throws IllegalArgumentException
	 *             if the page is pinnedreplacer
	 */

	 /*
	 *	We want to free a page whenever it has no reference because it is no longer
	 *	in use and a waste of space.
	 *
	 *	If a page has references we cant remove it and throw an exception.
	 *	Else we remove it and set the page to INVALID, and tell it to the replacer.
	 *	Lastly we deallocate the page.
	 */
	public void freePage(PageId pageno) throws IllegalArgumentException {
		final FrameDesc fd = pagemap.get(pageno.pid);

		if(null == fd) return;
		if(0 < fd.pincnt) throw new IllegalArgumentException("Page(" + pageno.pid + ") is pinned, can not be removed.");

		fd.pageno.pid = INVALID_PAGEID;
		pagemap.remove(pageno.pid);
		replacer.freePage(fd);

		Minibase.DiskManager.deallocate_page(pageno);
	}

	/**
	 * Pins a disk page into the buffer pool. If the page is already pinned,
	 * this simply increments the pin count. Otherwise, this selects another
	 * page in the pool to replace, flushing the replaced page to disk if
	 * it is dirty.
	 *
	 * (If one needs to copy the page from the memory instead of reading from
	 * the disk, one should set skipRead to PIN_MEMCPY. In this case, the page
	 * shouldn't be in the buffer pool. Throw an IllegalArgumentException if so. )
	 *
	 *
	 * @param pageno
	 *            identifies the page to pin
	 * @param page
	 *            if skipread == PIN_MEMCPY, works as as an input param, holding the contents to be read into the buffer pool
	 *            if skipread == PIN_DISKIO, works as an output param, holding the contents of the pinned page read from the disk
	 * @param skipRead
	 *            PIN_MEMCPY(true) (copy the input page to the buffer pool); PIN_DISKIO(false) (read the page from disk)
	 * @throws IllegalArgumentException
	 *             if PIN_MEMCPY and the page is pinned
	 * @throws IllegalStateException
	 *             if all pages are pinned (i.e. pool exceeded)
	 */

	 /*
	 *The pinpage function pins a page, given as argument a page and an id, it furthermore requires an enum(skipread)
	 * to dictate whether or not it should pin the id or create a new page.
	 */
	 public void pinPage(PageId pageno, Page page, boolean skipRead) {
		 if( pagemap.containsKey(pageno.pid) ){
			 if(skipRead) throw new IllegalArgumentException( "Page(" + pageno.pid + ") PIN_MEMCPY and the page is pinned" );

			 final FrameDesc fd = pagemap.get( pageno.pid );

			 page.setPage(bufpool[fd.index]);
			 increment(fd);
		 } else {
			 final int index = replacer.pickVictim();

			 if( EMPTY_SLOT == index ) throw new IllegalStateException("All pages are pinned");

			 final FrameDesc fd = frametab[ index ];

			 if( INVALID_PAGEID != fd.pageno.pid ){
				 pagemap.remove( fd.pageno.pid );
				 if( fd.dirty ) Minibase.DiskManager.write_page( fd.pageno, bufpool[ index ]);
			 }
			 if( skipRead ) bufpool[ index ].copyPage( page );
			 else Minibase.DiskManager.read_page( pageno, bufpool[ index ]);

			 page.setPage( bufpool[ index ]);
			 new_page( fd, pageno );
		 }
	 }

	  	private void increment (final FrameDesc fd){
	  		fd.pincnt++;
	  		replacer.pinPage(fd);
	  	}

	  	private void new_page(final FrameDesc fd, final PageId pageno){
	  		fd.pincnt = 1;
	  		pagemap.put( pageno.pid, fd );
	  		fd.pageno.pid = pageno.pid;
	  		replacer.pinPage( fd );
	  	}

	/**
	 * Unpins a disk page from the buffer pool, decreasing its pin count.
	 *
	 * @param pageno
	 *            identifies the page to unpin
	 * @param dirty
	 *            UNPIN_DIRTY if the page was modified, UNPIN_CLEAN otherrwise
	 * @throws IllegalArgumentException
	 *             if the page is not present or not pinned
	 */

	 /*
	 *	We want to remove a reference from a page, by decrementing the pin count
	 *	and if the page has been altered we set its status to dirty.
	 *	Lastly we tell it to the replacer.
	 */
	public void unpinPage(PageId pageno, boolean dirty) throws IllegalArgumentException {
		final FrameDesc fd = pagemap.get(pageno.pid);

		if (null == fd) throw new IllegalArgumentException("Page is not present");
		if (0 < fd.pincnt){
			fd.pincnt--;
			fd.dirty = dirty;
			replacer.unpinPage(fd);
		}
	}

	/**
	 * Immediately writes a page in the buffer pool to disk, if dirty.
	 */

	public void flushPage(PageId pageno) {
		if (pagemap.get(pageno.pid).dirty)
		Minibase.DiskManager.write_page(pageno, bufpool[pagemap.get(pageno.pid).index]);
	}

	/**
	 * Immediately writes all dirty pages in the buffer pool to disk.
	 */

	/*
	*	With a simple lambda we check each keys if their values are dirty, if so,
	* we flush them.
	*/
	public void flushAllPages() {
		pagemap.forEach( (k,v) -> flushPage(v.pageno));
	}

	/**
	 * Gets the total number of buffer frames.
	 */
	public int getNumBuffers() {
		return bufpool.length;
	}

	/**
	 * Gets the total number of unpinned buffer frames.
	 */

	/*
	*	We convert the frame table array to a stream, then we filter all pages
	* which has a reference (!= zero) and counts them.
	*/
	public int getNumUnpinned() {
		return (int)Arrays.stream(frametab).filter(i-> 0 == i.pincnt).count();
	}

} // public class BufMgr implements GlobalConst
