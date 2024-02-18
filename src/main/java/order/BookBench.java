package order;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.Random;
import static java.lang.System.nanoTime;
import static java.nio.ByteOrder.nativeOrder;


/** Benchmark tests report to standard output. */
public class BookBench {

	public static void main(String[] args) {
		try {
			placeRandom();
			System.gc();
			placeAndCancelInBookSize(256 * 1024);
			placeAndCancelInBookSize(1024 * 1024);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Random packets are the worst-case scenario in terms of performance.
	 * Note that there are no illegal values in the packet format.
	 */
	static void placeRandom() throws IOException {
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

		long placeCount = b.buyPlaceCount + b.sellPlaceCount;
		System.out.printf("%.1f M random order placements in %.1f M packets took %.0f ns on average\n",
			(double)placeCount / 1000000,
			(double)packetCount / 1000000,
			(double)took / placeCount);
	}

	/**
	 * Test orders with a logarthmic price distribution to simulate the
	 * production environment. The number of buy orders is kept stable with
	 * placements and cancelations on every other packet.
	 * @param bookSize the number of orders in the book during testing.
	 */
	static void placeAndCancelInBookSize(final int bookSize) throws IOException {
		if (Long.bitCount(bookSize) != 1)
			throw new IllegalArgumentException("book size not a power of two");

		ByteBuffer packetData = ByteBuffer.allocate(1<<30);
		if (Book.PACKET_SIZE % 8 != 0)
			throw new AssertionError("packet size not a multiple of 64 bits");
		final long packetCount = packetData.limit() / Book.PACKET_SIZE;
		LongBuffer words = packetData.order(nativeOrder()).asLongBuffer();
		final int wordsPerPacket = Book.PACKET_SIZE / 8;

		// random data, yet consistent for stable measures
		Random r = new Random(123);

		// order placement packets
		for (int i = 0; i < packetCount; i++) {
			// First bookSize count all create for initial data set.
			// Then create only in even packet indices.
			if (i >= bookSize && (i & 1) != 0) continue;

			double price = 99 * Math.log(4 * r.nextDouble());

			// TODO: add sell placements
			words.put(i * wordsPerPacket, Book.BUY_NOT_SELL_FLAG | Book.PLACE_NOT_CANCEL_FLAG);
			words.put((i * wordsPerPacket) + 1, (long)i);      // order ID
			words.put((i * wordsPerPacket) + 2, r.nextLong()); // order amount
			words.put((i * wordsPerPacket) + 3, (long)price);
		}

		// order cancelation packets in odd indices after initial data set
		if (bookSize % 2 != 0) throw new AssertionError("odd number for initial data set size");
		for (int i = bookSize + 1; i < packetCount; i += 2) {
			// random packet from past (could dupe)
			int target = (int)(r.nextDouble() * i);
			// target even indices for placements and no cancelations
			target &= ~1;

			// TODO: add sell cancelations
			words.put(i * wordsPerPacket, Book.BUY_NOT_SELL_FLAG);
			// match order ID, order amount and limit price
			words.put((i * wordsPerPacket) + 1, words.get((target * wordsPerPacket) + 1));
			words.put((i * wordsPerPacket) + 2, words.get((target * wordsPerPacket) + 2));
			words.put((i * wordsPerPacket) + 3, words.get((target * wordsPerPacket) + 3));
		}

		Book b = new Book(new ByteArrayInputStream(packetData.array()));

		// load initial data set
		if (Book.READ_SIZE % Book.PACKET_SIZE != 0)
			throw new AssertionError(
				String.format("read buffer size %d not a multiple of packet size %d",
					Book.READ_SIZE, Book.PACKET_SIZE));
		final int packetsPerRead = Book.READ_SIZE / Book.PACKET_SIZE;
		if (bookSize % packetsPerRead != 0)
			throw new AssertionError(
				String.format("book size %d not a multiple of %d packets per read",
					bookSize, packetsPerRead));
		for (int n = bookSize / packetsPerRead; --n >= 0; )
			b.load();
		if (b.buyPlaceCount != bookSize)
			throw new AssertionError(
				String.format("loaded %d out of %d placements for initial data set",
					b.buyPlaceCount, bookSize));
		// TODO: implement cancelation:
		if (b.buyCancelCount != 0)
			throw new AssertionError("loaded cancelations in initial data set");

		// benchmark processing time
		long start = nanoTime();
		while (b.load());
		long took = nanoTime() - start;

		long placeCount = (b.buyPlaceCount + b.sellPlaceCount) - (long)bookSize;
		long cancelCount = b.buyCancelCount + b.sellCancelCount;
		System.out.printf("%.1f M simulated order placements and %.1f M order cancelations took %.0f ns on average with %d open orders\n",
			(double)placeCount / 1000000,
			(double)cancelCount / 1000000,
			(double)took / (double)(placeCount + cancelCount),
			bookSize);
	}

}
