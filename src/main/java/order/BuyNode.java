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
	 */
	BuyNode below0, below1, below2;

	/**
	 * The actual number of orders in a BuyNode is either 1 or 2.
	 * Larger nodes split up in two, and the empty ones resolve.
	 */
	int size;

	/** Embed up to 2 orders to offload Java's garbage collection .*/
	private long price0, price1, quant0, quant1, ident0, ident1;

	static BuyNode newInstance(long price, long quant, long ident) {
		BuyNode n = new BuyNode();
		n.price0 = price;
		n.quant0 = quant;
		n.ident0 = ident;
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
		for (BuyNode n = below0; n != null; n = n.below0)
			level++;
		int levelMax = level;
		for (BuyNode n = above; n != null; n = n.above)
			levelMax++;

		switch (size) {
		case 1:
			return String.format("@%d/%d:[ %d %d %d ]", level, levelMax,
				price0, quant0, ident0);
		case 2:
			return String.format("@%d/%d:[ %d %d %d ][ %d %d %d ]", level, levelMax,
				price0, quant0, ident0, price1, quant1, ident1);
		default:
			return String.format("@%d/%d:<size %d ☠>", level, levelMax, size);
		}
	}

	/**
	 * Insert without presence/duplicate check, sorted by price in ascending
	 * order, and then by insertion order.
	 * @param treeOrNull the top node or {@code null} when empty.
	 * @param price the order limit.
	 * @param quant the order volume.
	 * @param ident the order reference.
	 * @return the new top node.
	 */
	static BuyNode placeOrderInTreeOrNull(BuyNode treeOrNull, long price, long quant, long ident) {
		if (treeOrNull == null)
			return BuyNode.newInstance(price, quant, ident);

		BuyNode top = treeOrNull;
		BuyNode.placeOrder(top, price, quant, ident);
		if (top.above != null)
			top = top.above;
		return top;
	}

	/** @see #placeOrderInTree */
	private static void placeOrder(BuyNode n, long price, long quant, long ident) {
		while (true) {
			if (n.size == 1) {
				if (price >= n.price0) {
					if (n.below1 != null) {
						n = n.below1;
						continue;
					} else { // at bottom level
						n.price1 = price;
						n.quant1 = quant;
						n.ident1 = ident;
						n.size = 2;
					}
				} else {
					if (n.below0 != null) {
						n = n.below0;
						continue;
					} else { // at bottom level
						n.price1 = n.price0;
						n.quant1 = n.quant0;
						n.ident1 = n.ident0;
						n.price0 = price;
						n.quant0 = quant;
						n.ident0 = ident;
						n.size = 2;
					}
				}
				return;
			}
			assert(n.size == 2);

			if (price >= n.price1) {
				if (n.below2 != null) {
					n = n.below2;
					continue;
				} else { // at bottom level
					BuyNode right = BuyNode.newInstance(price, quant, ident);
					n.size = 1;
					n.pushUp(n.price1, n.quant1, n.ident1, right);
				}
			} else if (price >= n.price0) {
				if (n.below1 != null) {
					n = n.below1;
					continue;
				} else { // at bottom level
					BuyNode right = BuyNode.newInstance(n.price1, n.quant1, n.ident1);
					n.size = 1;
					n.pushUp(price, quant, ident, right);
				}
			} else {
				assert(price < n.price0);
				if (n.below0 != null) {
					n = n.below0;
					continue;
				} else { // at bottom level
					BuyNode right = BuyNode.newInstance(n.price1, n.quant1, n.ident1);
					n.size = 1;
					n.pushUp(n.price0, n.quant0, n.ident0, right);
					n.price0 = price;
					n.quant0 = quant;
					n.ident0 = ident;
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
		if (this.above != null) {
			this.above.push(price, quant, ident, this, right);
			return;
		}

		BuyNode top = BuyNode.newInstance(price, quant, ident);
		top.below0 = this;
		top.below1 = right;
		this.above = top;
		right.above = top;
	}

	/**
	 * Push receives order [ {@code price} {@code quant} {@code ident} ]
	 * from below.
	 * @param left MUST be present one level {@link #below}.
	 * @param right the new node to place (right of {@code left}).
	 */
	private void push(long price, long quant, long ident, BuyNode left, BuyNode right) {
		assert(left == below0 || left == below1 || (size > 1 && left == below2));
		assert(right != below0 && right != below1 && right != below2);
		assert(left.above == this);

		if (size == 1) {
			size = 2; // grow

			if (left == below1) {
				// push (L)eft, (R)ight and new (C)enter:
				//
				//   (A)          (T) (C)
				//   / \    👉    / \ / \
				// (B) (L)      (B) (L) (R)

				below2 = right;
				below2.above = this;
				price1 = price;
				quant1 = quant;
				ident1 = ident;
			} else {
				assert(left == below0);
				// push (L)eft, (R)ight and new (C)enter:
				//
				//   (A)          (C) (A)
				//   / \    👉    / \ / \
				// (L) (B)      (L) (R) (B)

				below2 = below1;
				below1 = right;
				below1.above = this;
				price1 = price0;
				quant1 = quant0;
				ident1 = ident0;
				price0 = price;
				quant0 = quant;
				ident0 = ident;
			}

			return;
		}
		assert(size == 2);

		// split up
		if (left == below2) {
			// push (L)eft, (R)ight and new (C)enter:
			//                               (A2)
			//    (A1)  (A2)           (A1)   🔝   (C)
			//    /  \  /  \    👉     /  \        / \
			// (B1)  (B2)  (L)      (B1)  (B2)   (L) (R)

			BuyNode split = BuyNode.newInstance(price, quant, ident);
			split.below0 = left;
			split.below0.above = split;
			split.below1 = right;
			split.below1.above = split;
			size = 1;
			below2 = null; // free (L); in split now
			pushUp(price1, quant1, ident1, split);
			return;
		}

		BuyNode split = BuyNode.newInstance(price1, quant1, ident1);
		if (left == below1) {
			// push (L)eft, (R)ight and new (C)enter:
			//                               (C)
			//    (A1) (A2)            (A1)   🔝   (A2)
			//    /  \ /  \     👉     /  \        /  \
			// (B1)  (L)  (B2)      (B1)  (L)    (R)  (B2)
			split.below0 = right;
			split.below0.above = split;
			split.below1 = below2;
			split.below1.above = split;
			size = 1;
			below2 = null; // free (B2); in split now
			pushUp(price, quant, ident, split);
		} else {
			assert(left == below0);
			// push (L)eft, (R)ight and new (C)enter:
			//                              (A1)
			//   (A1)  (A2)            (C)   🔝   (A2)
			//   /  \  /  \     👉     / \        /  \
			// (L)  (B1)  (B2)      (L)  (R)   (B1)   (B2)
			split.below0 = below1;
			split.below0.above = split;
			split.below1 = below2;
			split.below1.above = split;
			size = 1;
			below2 = null; // free (B2); in split now
			below1 = right;
			below1.above = this;
			pushUp(price0, quant0, ident0, split);
			price0 = price;
			quant0 = quant;
			ident0 = ident;
		}
	}

}
