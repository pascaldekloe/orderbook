package order;

import java.util.Arrays;

/**
 * BuyNode sorts orders ascending on price, with the latest entries placed
 * higher than any previous ones with the same price (for FIFO behaviour).
 *
 * B-tree nodes were chosen for sorting orders because they perform descent both
 * on inserts and deletes, and no worst-case outlines.
 *
 * Logic is fully spelled out (rather than loops over arrays) to hint branche
 * prediction of CPUs. Operation is mostly centered around the upper prizes for
 * buy orders. See <https://github.com/tidwall/btree-benchmark/pull/4> for an
 * example of how much of a difference the branch prediction can make.
 *
 * Node size 2/3 was chosen as it limits the amount of code spelled out, and it
 * grows with space for insertion on both sides, all at the cost of more memory
 * overhead and more (jumps on) nodes. The node count should be managed later as
 * improvement by keeping only a subset with the highest prices in the book. 
 */
final class BuyNode {

	/** A {@code null} value is for the top node exclusively. */
	BuyNode above;

	/**
	 * Only the first {@link #size} + 1 nodes are in use. The bottom row of
	 * nodes in a B-Tree all have {@code null} values exclusively, and vise
	 * versa.
	 * TODO: Do 3 pointer fields save memory (without Java's array header)?
	 */
        final BuyNode[] below = new BuyNode[3];

	/**
	 * The actual number of orders in a BuyNode is either 1 or 2.
	 * Larger nodes split up in two, and the empty ones resolve.
	 */
	int size;

	/**
	 * Payload embeds the {@link #size} count of orders in sequetial memory
	 * as [ price quantity order_ID ] tuples to offload garbage collection.
	 * TODO: Do 6 long fields save memory (without Java's array header)?
	 */
        final long[] payload = new long[(below.length - 1) * ORDER_LEN];

	// field indices in payload array
	private static final int PRICE_POS = 0;
	private static final int QUANT_POS = 1;
	private static final int IDENT_POS = 2;
	private static final int ORDER_LEN = 3; // number of fields

	static BuyNode newInstance(long price, long quant, long ident) {
		BuyNode n = new BuyNode();
		n.payload[PRICE_POS] = price;
		n.payload[QUANT_POS] = quant;
		n.payload[IDENT_POS] = ident;
		n.size = 1;
		return n;
	}

	/**
	 * ToString returns a textual representation for debugging and testing
	 * purposes.
	 */
	@Override
	public String toString() {
		int level = 0; // floor
		for (BuyNode n = below[0]; n != null; n = n.below[0])
			level++;
		int levelMax = level;
		for (BuyNode n = above; n != null; n = n.above)
			levelMax++;

		switch (size) {
		case 1:
			return String.format("@%d/%d:[ %d %d %d ]", level, levelMax,
				payload[PRICE_POS], payload[QUANT_POS], payload[IDENT_POS]);
		case 2:
			return String.format("@%d/%d:[ %d %d %d ][ %d %d %d ]", level, levelMax,
				payload[PRICE_POS + 0*ORDER_LEN], payload[QUANT_POS + 0*ORDER_LEN], payload[IDENT_POS + 0*ORDER_LEN],
				payload[PRICE_POS + 1*ORDER_LEN], payload[QUANT_POS + 1*ORDER_LEN], payload[IDENT_POS + 1*ORDER_LEN]);
		default:
			return String.format("@%d/%d:<size %d ☠>", level, levelMax, size);
		}
	}

	/**
	 * Insert without presence/duplicate check, sorted by price in ascending
	 * order, and then by insertion order.
	 * @param price order limit.
	 * @param quant order volume.
	 * @param ident order reference.
	 */
	public static void placeOrder(BuyNode n, long price, long quant, long ident) {
		while (true) {
			if (n.size == 1) {
				if (price >= n.payload[PRICE_POS]) {
					if (n.below[1] != null) {
						n = n.below[1];
						continue;
					} else { // at bottom level
						n.payload[PRICE_POS + ORDER_LEN] = price;
						n.payload[QUANT_POS + ORDER_LEN] = quant;
						n.payload[IDENT_POS + ORDER_LEN] = ident;
						n.size = 2;
					}
				} else {
					if (n.below[0] != null) {
						n = n.below[0];
						continue;
					} else { // at bottom level
						n.payload[PRICE_POS + ORDER_LEN] = n.payload[PRICE_POS];
						n.payload[QUANT_POS + ORDER_LEN] = n.payload[QUANT_POS];
						n.payload[IDENT_POS + ORDER_LEN] = n.payload[IDENT_POS];
						n.payload[PRICE_POS] = price;
						n.payload[QUANT_POS] = quant;
						n.payload[IDENT_POS] = ident;
						n.size = 2;
					}
				}
				return;
			}
			assert(n.size == 2);

			if (price >= n.payload[PRICE_POS + ORDER_LEN]) {
				if (n.below[2] != null) {
					n = n.below[2];
					continue;
				} else { // at bottom level
					BuyNode right = BuyNode.newInstance(price, quant, ident);
					n.size = 1;
					n.pushUp(n.payload[PRICE_POS + ORDER_LEN],
						n.payload[QUANT_POS + ORDER_LEN],
						n.payload[IDENT_POS + ORDER_LEN],
						right);
				}
			} else if (price >= n.payload[PRICE_POS]) {
				if (n.below[1] != null) {
					n = n.below[1];
					continue;
				} else { // at bottom level
					BuyNode right = BuyNode.newInstance(
						n.payload[PRICE_POS + ORDER_LEN],
						n.payload[QUANT_POS + ORDER_LEN],
						n.payload[IDENT_POS + ORDER_LEN]);
					n.size = 1;
					n.pushUp(price, quant, ident, right);
				}
			} else {
				assert(price < n.payload[PRICE_POS]);
				if (n.below[0] != null) {
					n = n.below[0];
					continue;
				} else { // at bottom level
					BuyNode right = BuyNode.newInstance(
						n.payload[PRICE_POS + ORDER_LEN],
						n.payload[QUANT_POS + ORDER_LEN],
						n.payload[IDENT_POS + ORDER_LEN]);
					n.size = 1;
					n.pushUp(n.payload[PRICE_POS],
						n.payload[QUANT_POS],
						n.payload[IDENT_POS],
						right);
					n.payload[PRICE_POS] = price;
					n.payload[QUANT_POS] = quant;
					n.payload[IDENT_POS] = ident;
				}
			}
			return;
		}
	}

	/**
	 * PushUp sends order [ {@code price} {@code quant} {@code ident} ] to
	 * {@link #above}.
	 * @param right the new node to place (right of {@code this}).
	 */
	private void pushUp(long price, long quant, long ident, BuyNode right) {
		if (this.above == null) {
			BuyNode top = BuyNode.newInstance(price, quant, ident);
			top.below[0] = this;
			top.below[1] = right;
			this.above = top;
			right.above = top;
		} else {
			this.above.push(price, quant, ident, this, right);
		}
	}

	/**
	 * Push receives order [ {@code price} {@code quant} {@code ident} ]
	 * from below.
	 * @param left MUST be present one level {@link #below}.
	 * @param right the new node to place (right of {@code left}).
	 */
	private void push(long price, long quant, long ident, BuyNode left, BuyNode right) {
		assert(Arrays.asList(below).subList(0, size+1).contains(left));
		assert(!Arrays.asList(below).subList(0, size+1).contains(right));
		assert(left.above == this);

		if (size == 1) {
			size = 2; // grow

			if (left == below[1]) {
				// push (L)eft, (R)ight and new (C)enter:
				//
				//   (A)          (T) (C)
				//   / \    👉    / \ / \
				// (B) (L)      (B) (L) (R)

				below[2] = right;
				below[2].above = this;
				payload[PRICE_POS + ORDER_LEN] = price;
				payload[QUANT_POS + ORDER_LEN] = quant;
				payload[IDENT_POS + ORDER_LEN] = ident;
			} else {
				assert(left == below[0]);
				// push (L)eft, (R)ight and new (C)enter:
				//
				//   (A)          (C) (A)
				//   / \    👉    / \ / \
				// (L) (B)      (L) (R) (B)

				below[2] = below[1];
				below[2].above = this;
				below[1] = right;
				below[1].above = this;
				payload[PRICE_POS + ORDER_LEN] = payload[PRICE_POS];
				payload[QUANT_POS + ORDER_LEN] = payload[QUANT_POS];
				payload[IDENT_POS + ORDER_LEN] = payload[IDENT_POS];
				payload[PRICE_POS] = price;
				payload[QUANT_POS] = quant;
				payload[IDENT_POS] = ident;
			}

			return;
		}
		assert(size == 2);

		// split up
		if (left == below[2]) {
			// push (L)eft, (R)ight and new (C)enter:
			//                               (A2)
			//    (A1)  (A2)           (A1)   🔝   (C)
			//    /  \  /  \    👉     /  \        / \
			// (B1)  (B2)  (L)      (B1)  (B2)   (L) (R)

			BuyNode split = BuyNode.newInstance(price, quant, ident);
			split.below[0] = left;
			split.below[0].above = split;
			split.below[1] = right;
			split.below[1].above = split;
			size = 1;
			below[2] = null; // free (L); in split now
			pushUp(payload[PRICE_POS + ORDER_LEN],
				payload[QUANT_POS + ORDER_LEN],
				payload[IDENT_POS + ORDER_LEN],
				split);
			return;
		}

		BuyNode split = BuyNode.newInstance(
			payload[PRICE_POS + ORDER_LEN],
			payload[QUANT_POS + ORDER_LEN],
			payload[IDENT_POS + ORDER_LEN]);
		if (left == below[1]) {
			// push (L)eft, (R)ight and new (C)enter:
			//                               (C)
			//    (A1) (A2)            (A1)   🔝   (A2)
			//    /  \ /  \     👉     /  \        /  \
			// (B1)  (L)  (B2)      (B1)  (L)    (R)  (B2)
			split.below[0] = right;
			split.below[0].above = split;
			split.below[1] = below[2];
			split.below[1].above = split;
			size = 1;
			below[2] = null; // free (B2); in split now
			pushUp(price, quant, ident, split);
		} else {
			assert(left == below[0]);
			// push (L)eft, (R)ight and new (C)enter:
			//                              (A1)
			//   (A1)  (A2)            (C)   🔝   (A2)
			//   /  \  /  \     👉     / \        /  \
			// (L)  (B1)  (B2)      (L)  (R)   (B1)   (B2)
			split.below[0] = below[1];
			split.below[0].above = split;
			split.below[1] = below[2];
			split.below[1].above = split;
			size = 1;
			below[2] = null; // free (B2); in split now
			below[1] = right;
			below[1].above = this;
			pushUp(payload[PRICE_POS],
				payload[QUANT_POS],
				payload[IDENT_POS],
				split);
			payload[PRICE_POS] = price;
			payload[QUANT_POS] = quant;
			payload[IDENT_POS] = ident;
		}
	}

}
