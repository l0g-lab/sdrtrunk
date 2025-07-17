package io.github.dsheirer.source.tuner.bladerf;

import io.github.dsheirer.source.SourceException;
import io.github.dsheirer.source.tuner.bladerf.BladeRFTunerController.BladeRFrxChannel;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.EnumSet;
import org.apache.commons.io.EndianUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BladeRFControlRFFE {
	private final static Logger mLog = LoggerFactory.getLogger(BladeRFTunerController.class);

    /**
	 * Select band based on frequency
	 */
	public void rfic_host_select_band(BladeRFrxChannel ch, long freq) throws SourceException {
		long reg;
		// read RFFE control register
		// CHECK_STATUS(nios_rffe_control_read(reg));

		// Modify SPDT bits - have to do this for all channels sharing the same LO
		int i;
		//for (i=0; i<2; ++i) {
			// CHECK_STATUS(modifySpdtBitsByFreq(reg, i, freq))
		//}
	}

	public void rfic_host_initialize() throws SourceException {
		mLog.info("Initializing BladeRF in Host Mode");
		BladeRFNIOS packet = new BladeRFNIOS();

		int rffe_control_enable = BladeRFFEControl.RFFE_CONTROL_ENABLE.getRequestNumber();
		int rffe_control_txnrx = BladeRFFEControl.RFFE_CONTROL_TXNRX.getRequestNumber();

		packet.nios_rffe_control_write((1 << rffe_control_enable) | (1 << rffe_control_txnrx));
	}

	//public void print_buf(ByteBuffer buf) throws SourceException {
	//	try {
	//		String msg = new String(buf.array(), "UTF-8");
	//		mLog.info("Buffer Content: " + msg);
	//	} catch (SourceException e) {
	//		mLog.error("Cannot print buffer", e);
	//	}

	//}


    public enum BladeRFFEControl {
		RFFE_CONTROL_RESET_N(0),
		RFFE_CONTROL_ENABLE(1),
		RFFE_CONTROL_TXNRX(2),
		RFFE_CONTROL_EN_AGC(3),
		RFFE_CONTROL_SYNC_IN(4),
		RFFE_CONTROL_RX_BIAN_EN(5),
		RFFE_CONTROL_RX_SPDT_1(6), // 6 and 7
		RFFE_CONTROL_RX_SPDT_2(8), // 8 and 9
		RFFE_CONTROL_TX_BIAN_EN(10),
		RFFE_CONTROL_TX_SPDT_1(11), // 11 and 12
		RFFE_CONTROL_TX_SPDT_2(13), // 13 and 14
		RFFE_CONTROL_MIMO_RX_EN_0(15),
		RFFE_CONTROL_MIMO_TX_EN_0(16),
		RFFE_CONTROL_MIMO_RX_EN_1(17),
		RFFE_CONTROL_MIMO_TX_EN_1(18),
		RFFE_CONTROL_AD_MUXOUT(19), // input only
		RFFE_CONTROL_CTRL_OUT(24), // input only, 24 through 31
		RFFE_CONTROL_SPDT_MASK(0x3),
		RFFE_CONTROL_SPDT_SHUTDOWN(0x0), // no connection
		RFFE_CONTROL_SPDT_LOWBAND(0x2), // RF1 <-> RF3
		RFFE_CONTROL_SPDT_HIGHBAND(0x1); // RF1 <-> RF2

		private int mRequestNumber;

		BladeRFFEControl(int number) {
			mRequestNumber = number;
		}

		public int getRequestNumber() {
			return mRequestNumber;
		}
	}

    public enum BladeRFICCommand {
		BLADERF_RFIC_COMMAND_STATUS((byte) 0x00), // query the status register (read)
		BLADERF_RFIC_COMMAND_INIT((byte) 0x01), // initalized RFIC (read/write)
		BLADERF_RFIC_COMMAND_ENABLE((byte) 0x02), // enable or disable a channel (read/write)
		BLADERF_RFIC_COMMAND_SAMPLERATE((byte) 0x03), // sample rate for channel (read/write)
		BLADERF_RFIC_COMMAND_FREQUENCY((byte) 0x04), // center freq for channel (read/write)
		BLADERF_RFIC_COMMAND_BANDWIDTH((byte) 0x05), // bandwidth for channel (read/write)
		BLADERF_RFIC_COMMAND_GAINMODE((byte) 0x06), // gain mode for channel (read/write)
		BLADERF_RFIC_COMMAND_GAIN((byte) 0x07), // overall gain for channel (read/write)
		BLADERF_RFIC_COMMAND_RSSI((byte) 0x08), // RSSI for channel (read)
		BLADERF_RFIC_COMMAND_FILTER((byte) 0x09), // FIR filter for channel (read/write)
		BLADERF_RFIC_COMMAND_TXMUTE((byte) 0x0A); // TX Mute for channel (write)

		private byte mRequestNumber;

		BladeRFICCommand(byte number) {
			mRequestNumber = number;
		}

		public byte getRequestNumber() {
			return mRequestNumber;
		}
	}
}