package bufmgr;

import global.GlobalConst;
import global.Page;
import global.PageId;
import java.util.HashMap;
import global.Minibase;
import java.util.Arrays;

public class BufMgr implements GlobalConst {

	protected Page[] bufpool;

	protected FrameDesc[] frametab;

	protected HashMap<Integer, FrameDesc> pagemap;

	protected Replacer replacer;
	/**
	* Constructs a buffer mamanger with the given settings.
	*
	* @param numbufs number of buffers in the buffer pool
	*/
	public BufMgr(int numbufs) {

		frametab = new FrameDesc[numbufs];						// Creating Frame Table
		bufpool = new Page[numbufs];							// Creating Buffer Pool

		// Initialize each frametab and bufferpool
		for (int i=0; i<numbufs; i++) {
			frametab[i] = new FrameDesc(i);
			bufpool[i] = new Page();
		}


		pagemap = new HashMap<Integer, FrameDesc>(numbufs);		// Creating Page Map
		replacer = new Clock(this);								// Creating Clock object
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
	*
	* @param pageno identifies the page to remove
	* @throws IllegalArgumentException if the page is pinned
	*/
	public void freePage(PageId pageno) {

		final FrameDesc fd = pagemap.get(pageno.pid); // Sets the frameIndex variable to the page Id which is to be freed

		if(null == fd) return;
		if(0 < fd.pincnt) throw new IllegalArgumentException("Page(" + pageno.pid + ") is pinned, can not be removed.");

		fd.pageno.pid = INVALID_PAGEID;
		pagemap.remove(pageno.pid);
		replacer.freePage(fd);

		Minibase.DiskManager.deallocate_page(pageno);
	}

	/**
	* Pins a disk page into the buffer pool. If the page is already pinned, this
	* simply increments the pin count. Otherwise, this selects another page in
	* the pool to replace, flushing it to disk if dirty.
	*
	* @param pageno identifies the page to pin
	* @param page holds contents of the page, either an input or output param
	* @param skipRead PIN_MEMCPY (replace in pool); PIN_DISKIO (read the page in)
	* @throws IllegalArgumentException if PIN_MEMCPY and the page is pinned
	* @throws IllegalStateException if all pages are pinned (i.e. pool exceeded)
	*/
	public void pinPage(PageId pageno, Page page, boolean skipRead) {

		final FrameDesc fd = pagemap.get(pageno.pid); //Put the page Id to be pinned in frameIndex variable

		if(null != fd){
			if(skipRead) throw new IllegalArgumentException(""); //TODO : good exception messeage.
			fd.pincnt++; // Increase pin count of page by 1
			page.setPage(bufpool[fd.index]);
			replacer.pinPage(fd); // Update frame state to PINNED
		} else {
			final int index = replacer.pickVictim();
			if(EMPTY_SLOT == index) throw new IllegalStateException("Buffer is full, all pages are pinned");
			final FrameDesc victimfd = frametab[index];
			if(EMPTY_SLOT != victimfd.pageno.pid){
				pagemap.remove(victimfd.pageno.pid);
				if(victimfd.dirty) Minibase.DiskManager.write_page(victimfd.pageno, bufpool[index]);
			}
			if(skipRead) bufpool[index].copyPage(page);
			else Minibase.DiskManager.read_page(pageno, bufpool[index]);

			victimfd.pincnt = 1; // Setting pincount to 1
			page.setPage(bufpool[index]);
			pagemap.put(pageno.pid, victimfd); // Including the page in hashmap
			victimfd.pageno.pid = pageno.pid;
			replacer.pinPage(victimfd);
		}
	}

	/**
	* Unpins a disk page from the buffer pool, decreasing its pin count.
	*
	* @param pageno identifies the page to unpin
	* @param dirty UNPIN_DIRTY if the page was modified, UNPIN_CLEAN otherrwise
	* @throws IllegalArgumentException if the page is not present or not pinned
	*/
	public void unpinPage(PageId pageno, boolean dirty) {

		final FrameDesc fd = pagemap.get(pageno.pid); //Put the page Id to be unpinned in frameIndex variable

		if (null == fd) throw new IllegalArgumentException("Page is not present");
		if (0 < fd.pincnt){
			fd.pincnt--;
			fd.dirty = dirty;
			replacer.unpinPage(fd); // Updating frame state to REFERENCED
		}

	}

	/**
	* Immediately writes a page in the buffer pool to disk, if dirty.
	*/
	public void flushPage(PageId pageno) {
		Minibase.DiskManager.write_page(pageno, bufpool[pagemap.get(pageno.pid).index]);
	}

	/**
	* Immediately writes all dirty pages in the buffer pool to disk.
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
	public int getNumUnpinned() {

		return (int)Arrays.stream(frametab).filter(i-> 0 == i.pincnt).count();
	}

} // public class BufMgr implements GlobalConst
