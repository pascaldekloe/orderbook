package order;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

public class NodeTest {

	/** Insert higher prices until node split. */
	@Test
	public void placeAscending() {
		BuyNode n = BuyNode.newInstance(99, 1000, 42);
		assertEquals("node of 1", "@0/0:[ 99 1000 42 ]", n.toString());

		n.placeOrder(105, 999, 41);
		assertEquals("node of 2", "@0/0:[ 99 1000 42 ][ 105 999 41 ]", n.toString());

		n.placeOrder(115, 99, 40);
		assertEquals("left node", "@0/1:[ 99 1000 42 ]", n.toString());
		assertNotNull("super node of left", n.above);
		assertSame("left subnode", n.above.below[0], n);
		assertEquals("super node", "@1/1:[ 105 999 41 ]", n.above.toString());
		assertNotNull("right subnode", n.above.below[1]);
		assertSame("super node of right", n.above.below[1].above, n.above);
		assertEquals("right node", "@0/1:[ 115 99 40 ]", n.above.below[1].toString());
	}

	/** Insert lower prices until node split. */
	@Test
	public void placeDescending() {
		BuyNode n = BuyNode.newInstance(99, 1000, 42);
		assertEquals("node of 1", "@0/0:[ 99 1000 42 ]", n.toString());

		n.placeOrder(98, 1001, 43);
		assertEquals("node of 2", "@0/0:[ 98 1001 43 ][ 99 1000 42 ]", n.toString());

		n.placeOrder(97, 1002, 44);
		assertEquals("left node", "@0/1:[ 97 1002 44 ]", n.toString());
		assertNotNull("super node of left", n.above);
		assertSame("left subnode", n.above.below[0], n);
		assertEquals("super node", "@1/1:[ 98 1001 43 ]", n.above.toString());
		assertNotNull("right subnode", n.above.below[1]);
		assertSame("super node of right", n.above.below[1].above, n.above);
		assertEquals("right node", "@0/1:[ 99 1000 42 ]", n.above.below[1].toString());
	}

	/** Insert same price until node split. */
	@Test
	public void placeSame() {
		BuyNode n = BuyNode.newInstance(99, 1000, 42);
		assertEquals("node of 1", "@0/0:[ 99 1000 42 ]", n.toString());

		n.placeOrder(99, 1000, 41);
		assertEquals("node of 2", "@0/0:[ 99 1000 42 ][ 99 1000 41 ]", n.toString());

		n.placeOrder(99, 1000, 40);
		assertEquals("left node", "@0/1:[ 99 1000 42 ]", n.toString());
		assertNotNull("super node of left", n.above);
		assertSame("left subnode", n.above.below[0], n);
		assertEquals("super node", "@1/1:[ 99 1000 41 ]", n.above.toString());
		assertNotNull("right subnode", n.above.below[1]);
		assertSame("super node of right", n.above.below[1].above, n.above);
		assertEquals("right node", "@0/1:[ 99 1000 40 ]", n.above.below[1].toString());
	}

}
