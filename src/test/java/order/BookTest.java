package order;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.Random;
import static java.lang.System.nanoTime;
import static java.nio.ByteOrder.nativeOrder;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;


public class BookTest {

	/**
	 * Random packets are the worst-case scenario in terms of performance.
	 * Note that there are no illegal values in the packet format.
	 */
	@Test
	public void loadRandom() throws IOException {
		// data close to maximum array size from Java
		final int packetCount = 32 * 1024 * 1024;
		// chance of buy place is 1 in 4 (with 2 flags applicable)

		// setup with test data
		byte[] packetData = new byte[packetCount * Book.PACKET_SIZE];
		new Random(123).nextBytes(packetData); // random, yet consistent
		Book b = new Book(new ByteArrayInputStream(packetData));

		// benchmark load time
		long start = nanoTime();
		while (b.load());
		long took = nanoTime() - start;

		System.out.printf("%.1f M order places in %.1f M packets took %.0f ns on average\n",
			(double)b.buyPlaceCount / 1000000,
			(double)packetCount / 1000000,
			(double)took / b.buyPlaceCount);
	}

	/**
	 * Test orders on an exponential scale to simulate more closely to a
	 * production environment. The number of buy orders is kept stable with
	 * placements and cancelations on every other packet.
	 */
	@Test
	public void loadExpOnQuartMillion() throws IOException {
		final int bookSize = 256 * 1024;

		ByteBuffer packetData = ByteBuffer.allocate(1<<30);
		final long packetCount = packetData.limit() / Book.PACKET_SIZE;

		// packet format is 64-bit aligned
		LongBuffer words = packetData.order(nativeOrder()).asLongBuffer();
		assert(Book.PACKET_SIZE == 32);
		final int wordsPerPacket = Book.PACKET_SIZE / 8;

		// random data, yet consistent for stable measures
		Random r = new Random(123);

		// order placement packets
		for (int i = 0; i < bookSize; i++) {
			// First bookSize count all create for initial data set.
			// Then create only in even packet indices.
			if (i >= bookSize && (i & 1) != 0) continue;

			double price = 99 * Math.log(4 * r.nextDouble());

			words.put(i * wordsPerPacket, Book.BUY_NOT_SELL_FLAG | Book.PLACE_NOT_CANCEL_FLAG);
			words.put((i * wordsPerPacket) + 1, (long)i);      // order ID
			words.put((i * wordsPerPacket) + 2, r.nextLong()); // order amount
			words.put((i * wordsPerPacket) + 3, (long)price);
		}

		// order cancelation packets in odd indices after initial data set
		assertTrue("even number for initial data set size", bookSize % 2 == 0);
		for (int i = bookSize + 1; i < packetCount; i += 2) {
			// random packet from past (could dupe)
			int target = (int)(r.nextDouble() * i);
			// even indices only for order places and no cancels
			target &= ~1;

			words.put(i * wordsPerPacket, Book.BUY_NOT_SELL_FLAG);
			// match order ID, order amount and limit price
			words.put((i * wordsPerPacket) + 1, words.get((target * wordsPerPacket) + 1));
			words.put((i * wordsPerPacket) + 2, words.get((target * wordsPerPacket) + 2));
			words.put((i * wordsPerPacket) + 3, words.get((target * wordsPerPacket) + 3));
		}

		Book b = new Book(new ByteArrayInputStream(packetData.array()));

		// load initial data set
		assertTrue(String.format("read buffer size %d a multiple of packet size %d", Book.READ_SIZE, Book.PACKET_SIZE),
			Book.READ_SIZE % Book.PACKET_SIZE == 0);
		int packetsPerRead = Book.READ_SIZE / Book.PACKET_SIZE;
		assertTrue(String.format("book size %d a multiple of %d packets per read", bookSize, packetsPerRead),
			bookSize % packetsPerRead == 0);
		for (int n = bookSize / packetsPerRead; --n >= 0; )
			b.load();
		assertEquals("buy places in initial data set", bookSize, b.buyPlaceCount);
		assertEquals("buy cancels in initial data set", 0, b.buyCancelCount);

		// benchmark processing time
		long start = nanoTime();
		while (b.load());
		long took = nanoTime() - start;

		long buyPlaceCount = b.buyPlaceCount - (long)bookSize;
		long buyCancelCount = b.buyCancelCount;
		System.out.printf("%.1f M order placements and %.1f M order cancelations took %.0f ns on average\n",
			(double)buyPlaceCount / 1000000,
			(double)buyCancelCount / 1000000,
			(double)took / (double)(buyPlaceCount + buyCancelCount));
	}

}
