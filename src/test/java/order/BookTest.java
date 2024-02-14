package order;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Random;
import static java.lang.System.nanoTime;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;


public class BookTest {

	// Instantiates a random yet consistent stream with n packets.
	private static ByteArrayInputStream randomNPackets(int n) {
		byte[] buf = new byte[n * Book.PACKET_SIZE];
		new Random(123).nextBytes(buf);
		return new ByteArrayInputStream(buf);
	}

	/** No illegal values in packet format. */
	@Test
	public void loadRandom() throws IOException {
		final int packetCount = 32 * 1024 * 1024;
		// every 1 in 4 a buy place on average
		Book b = new Book(randomNPackets(packetCount));

		long start = nanoTime();
		while (b.load());
		long took = nanoTime() - start;

		System.out.printf("%.1f M order places in %.1f M packets took %.0f ns on average\n",
			(double)b.buyPlaceCount / 1000000,
			(double)packetCount / 1000000,
			(double)took / b.buyPlaceCount);

//		System.err.println(b.buyTreeDump());
	}

}
