package order;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class NodeTest {

	/** Insert higher prices until node split. */
	@Test
	public void placeAscending() {
		BuyNode n = BuyNode.placeOrderInTreeOrNull(null, 99, 1000, 42);
		assertNotNull("top node after initial place", n);
		assertEquals("node of 1", "@0/0:[ 99 1000 42 ]", n.toString());
		assertNull("above top node after initial place", n.above);

		n = BuyNode.placeOrderInTreeOrNull(n, 105, 999, 41);
		assertNotNull("top node after second place", n);
		assertEquals("node of 2", "@0/0:[ 99 1000 42 ][ 105 999 41 ]", n.toString());
		assertNull("above top node after second place", n.above);

		n = BuyNode.placeOrderInTreeOrNull(n, 115, 99, 40);
		assertNotNull("top node after third place", n);
		assertEquals("top node after third place", "@1/1:[ 105 999 41 ]", n.toString());
		assertNull("above top node after third place", n.above);

		assertNotNull("left node below", n.below[0]);
		assertEquals("left node below", "@0/1:[ 99 1000 42 ]", n.below[0].toString());
		assertSame("above left node below", n.below[0].above, n);

		assertNotNull("right node below", n.below[1]);
		assertEquals("right node below", "@0/1:[ 115 99 40 ]", n.below[1].toString());
		assertSame("above right node below", n.below[1].above, n);
	}

	/** Insert lower prices until node split. */
	@Test
	public void placeDescending() {
		BuyNode n = BuyNode.placeOrderInTreeOrNull(null, 99, 1000, 42);
		assertNotNull("top after initial place", n);
		assertEquals("node of 1", "@0/0:[ 99 1000 42 ]", n.toString());
		assertNull("above top after initial place", n.above);

		n = BuyNode.placeOrderInTreeOrNull(n, 98, 1001, 43);
		assertNotNull("top after second place", n);
		assertEquals("node of 2", "@0/0:[ 98 1001 43 ][ 99 1000 42 ]", n.toString());
		assertNull("above top after second place", n.above);

		n = BuyNode.placeOrderInTreeOrNull(n, 97, 1002, 44);
		assertNotNull("top node after third place", n);
		assertEquals("top node after thrird place", "@1/1:[ 98 1001 43 ]", n.toString());
		assertNull("above top node after third place", n.above);

		assertNotNull("left node below", n.below[0]);
		assertEquals("left node below", "@0/1:[ 97 1002 44 ]", n.below[0].toString());
		assertSame("above left node below", n.below[0].above, n);

		assertNotNull("right node below", n.below[1]);
		assertEquals("right node below", "@0/1:[ 99 1000 42 ]", n.below[1].toString());
		assertSame("above right node below", n.below[1].above, n);
	}

	/** Insert same price until node split. */
	@Test
	public void placeSame() {
		BuyNode n = BuyNode.placeOrderInTreeOrNull(null, 99, 1000, 42);
		assertNotNull("top node after initial place", n);
		assertEquals("node of 1", "@0/0:[ 99 1000 42 ]", n.toString());
		assertNull("above top node after initial place", n.above);

		n = BuyNode.placeOrderInTreeOrNull(n, 99, 1000, 41);
		assertNotNull("top node after second place", n);
		assertEquals("node of 2", "@0/0:[ 99 1000 42 ][ 99 1000 41 ]", n.toString());
		assertNull("above top node after second place", n.above);

		n = BuyNode.placeOrderInTreeOrNull(n, 99, 1000, 40);
		assertNotNull("top node after third place", n);
		assertEquals("top node after third place", "@1/1:[ 99 1000 41 ]", n.toString());
		assertNull("above top node after third place", n.above);

		assertNotNull("left node below", n.below[0]);
		assertEquals("left node below", "@0/1:[ 99 1000 42 ]", n.below[0].toString());
		assertSame("above left node below", n.below[0].above, n);

		assertNotNull("right node below", n.below[1]);
		assertEquals("right node below", "@0/1:[ 99 1000 40 ]", n.below[1].toString());
		assertSame("above right node below", n.below[1].above, n);
	}

}
