package order;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

import sun.misc.Unsafe;

import static order.BuyNode.placeOrderInTreeOrNull;


/**
 * Book keeps an overview of limit orders.
 */
public final class Book {

	/** The number of bytes in an order packet is fixed. */
	public static final int PACKET_SIZE = 32;

	/** First packet word flags place with 1 and cancel with 0. */
	public static final long PLACE_NOT_CANCEL_FLAG = 1L << 60;
	/** First packet word flags buy with 1 and sell with 0. */
	public static final long BUY_NOT_SELL_FLAG     = 1L << 61;

	/** Packet streams are read with this buffer size. */
	public static final int READ_SIZE = 4096;

	// top nodes of B-trees
	private BuyNode buyTree = null;
	// private SellNode sellTree = null

	// packet stream
	private final InputStream in;
	// TODO: try to match MTU & benchmark
	private final byte[] buf = new byte[READ_SIZE];
	private int bufN = 0; // pending byte count

	/** Total number of placements applied. */
	public long buyPlaceCount = 0;
	/** Total number of cancels applied. */
	public long buyCancelCount = 0;
	/** Total number of placements applied. */
	public long sellPlaceCount = 0;
	/** Total number of cancels applied. */
	public long sellCancelCount = 0;

	// https://mishadoff.com/blog/java-magic-part-4-sun-dot-misc-dot-unsafe/
	private static final Unsafe unsafe;
	static {
		try {
			Field f = Unsafe.class.getDeclaredField("theUnsafe");
			f.setAccessible(true);
			unsafe = (Unsafe)f.get(null);
		} catch (Exception e) {
			throw new Error("Java unsafe API required", e);
		}
	}

	/** Link the order book to a packet stream. */
	public Book(InputStream packets) {
		in = packets;
	}

	/**
	 * Consume orders from the packet stream.
	 * @throws IOException form the packet stream exclusively.
	 * @return whether new data was available at all.
	 */
	public boolean load() throws IOException {
		assert(bufN >= 0 && bufN < PACKET_SIZE);
		int done = in.read(buf, bufN, buf.length - bufN);
		if (done <= 0) return false;
		bufN += done;

		final int end = (bufN / PACKET_SIZE) * PACKET_SIZE;

		// read packets
		int baseAddr = unsafe.arrayBaseOffset(buf.getClass());
		for (int i = 0; i < end; i += PACKET_SIZE) {
			assert(PACKET_SIZE >= 32);
			long header   = unsafe.getLong(buf, baseAddr + i);
			long orderID  = unsafe.getLong(buf, baseAddr + i + 8);
			long quantity = unsafe.getLong(buf, baseAddr + i + 16);
			long price    = unsafe.getLong(buf, baseAddr + i + 24);

			boolean placeNotCancel = (header & PLACE_NOT_CANCEL_FLAG) != 0;
			boolean buyNotSell     = (header & BUY_NOT_SELL_FLAG) != 0;
			if (placeNotCancel && buyNotSell) placeBuy(price, quantity, orderID);
			if (placeNotCancel && !buyNotSell) placeSell(price, quantity, orderID);
			if (!placeNotCancel && buyNotSell) cancelBuy(price, quantity, orderID);
			if (!placeNotCancel && !buyNotSell) cancelSell(price, quantity, orderID);
		}

		// keep remainder
		bufN -= end;
		if (bufN != 0 && end != 0)
			System.arraycopy(buf, end, buf, 0, bufN);

		return true;
	}

	private void placeBuy(long price, long quant, long ident) {
		buyTree = placeOrderInTreeOrNull(buyTree, price, quant, ident);
		buyPlaceCount++; // metric
	}

	private void placeSell(long price, long quant, long ident) {
		// TODO: implement
	}

	private void cancelBuy(long price, long quant, long ident) {
		// TODO: implement
	}

	private void cancelSell(long price, long quant, long ident) {
		// TODO: implement
	}

	/**
	 * Construct a textual representation of the B-tree for debugging and
	 * testing purposes.
	 */
	public String buyTreeDump() {
		if (buyTree == null) return "<empty>\n";

		StringBuilder b = new StringBuilder(buyTree.toString()).append('\n');
		List<BuyNode> row = Arrays.asList(buyTree);
		while (row.get(0).below0 != null) {
			List<BuyNode> nextRow = new ArrayList(row.size() * 2);
			for (BuyNode node : row) {
				b.append("=======================================================================\n");
				nextRow.add(node.below0);
				b.append(node.below0.toString()).append('\n');
				nextRow.add(node.below1);
				b.append(node.below1.toString()).append('\n');
				if (node.full) {
					nextRow.add(node.below2);
					b.append(node.below2.toString()).append('\n');
				}
			}
			row = nextRow;
		}

		return b.toString();
	}

}
