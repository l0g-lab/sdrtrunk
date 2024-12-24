package io.github.dsheirer.source.tuner.bladerf;

import io.github.dsheirer.buffer.INativeBufferFactory;
import io.github.dsheirer.buffer.SignedByteNativeBufferFactory;
import io.github.dsheirer.source.SourceException;
import io.github.dsheirer.source.tuner.ITunerErrorListener;
import io.github.dsheirer.source.tuner.TunerType;
import io.github.dsheirer.source.tuner.configuration.TunerConfiguration;
import io.github.dsheirer.source.tuner.usb.USBTunerController;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.EnumSet;
import org.apache.commons.io.EndianUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.usb4java.LibUsb;
import org.usb4java.LibUsbException;

import javax.usb.UsbException;

public class BladeRFTunerController extends USBTunerController {
	private final static Logger mLog = LoggerFactory.getLogger(BladeRFTunerController.class);

	public final static long USB_TIMEOUT_US = 1000000L; //uSeconds - 1000ms
	public static final int USB_TRANSFER_BUFFER_SIZE = 262144; // not sure yet
    //public static final byte REQUEST_TYPE_IN = (byte)(LibUsb.ENDPOINT_IN | LibUsb.REQUEST_TYPE_VENDOR | LibUsb.RECIPIENT_DEVICE);
    //public static final byte REQUEST_TYPE_OUT = (byte)(LibUsb.ENDPOINT_OUT | LibUsb.REQUEST_TYPE_VENDOR | LibUsb.RECIPIENT_DEVICE);
    public static final byte REQUEST_HOST_TO_DEVICE = (byte)0x00; // LibUsb.ENDPOINT_OUT
    public static final byte REQUEST_DEVICE_TO_HOST = (byte)0x80; // LibUsb.ENDPOINT_IN
    public static final long MINIMUM_TUNABLE_FREQUENCY_HZ = 47000000l; // 47MHz
    public static final long MAXIMUM_TUNABLE_FREQUENCY_HZ = 6000000000l; // 6GHz
    public static final long DEFAULT_FREQUENCY = 101100000; // 101.1MHz
    public static final double USABLE_BANDWIDTH = 0.90;
    public static final int DC_HALF_BANDWIDTH = 5000;

    private INativeBufferFactory mNativeBufferFactory = new SignedByteNativeBufferFactory();
    private BladeRFSampleRate mSampleRate = BladeRFSampleRate.RATE_5_0;

	/**
	 * Constructs an instance
	 * @param bus usb
	 * @param portAddress usb
	 */
	public BladeRFTunerController(int bus, String portAddress, ITunerErrorListener tunerErrorListener) {
		super(bus, portAddress, MINIMUM_TUNABLE_FREQUENCY_HZ, MAXIMUM_TUNABLE_FREQUENCY_HZ, DC_HALF_BANDWIDTH, USABLE_BANDWIDTH, tunerErrorListener);
	}

	/**
	 * Tuner type for this BladeRF
	 */
	@Override
	public TunerType getTunerType()	{
		return TunerType.BLADERF;
	}

	@Override
	protected INativeBufferFactory getNativeBufferFactory() {
		return mNativeBufferFactory;
	}

	@Override
	protected int getTransferBufferSize() {
		return USB_TRANSFER_BUFFER_SIZE;
	}
	
	@Override
	public int getBufferSampleCount() {
		return USB_TRANSFER_BUFFER_SIZE / 2; // may need to modify this
	}

	/**
	 * Device startup actions invoked by the parent start() method
	 * @throws SourceException if there is an error performing these operations
	 */
	protected void deviceStart() throws SourceException {
		try {
			mLog.info("============= This is BladeRFTunerController which extends the USBTunerController... USBTunerController calls the deviceStart() function ==============");
			mLog.info("Here is the LibUsb device from the getDevice() call: " + getDevice());
			mLog.info("Here is the device handle from the getDeviceHandle() call: " + getDeviceHandle());
			mLog.info("Here is the device descriptor from the getDeviceDescriptor() call: \n" + getDeviceDescriptor());
			mLog.info("Here is the serial number: " + getSerial().getSerialNumber());

			mLog.info("The first step is to do the configuration on the BladeRF... setting receive node, channel, etc");
			setMode(Mode.RECEIVE);
			mLog.info("This is second step");
			setFrequency(DEFAULT_FREQUENCY);
		}
		catch(Exception e) {
			mLog.error("Error on BladeRF startup", e);
			throw new SourceException("BladeRF Tuner Controller - couldn't claim USB interface or get endpoint or pipe", e);
		}
	}

	/**
	 * Devices shutdown actions invoked by the parent stop() method
	 */
	@Override
	protected void deviceStop() {
		try {
			setMode(Mode.OFF); // does this work for BladeRF??
		}
		catch(UsbException ue) {
			mLog.error("Error while settings BladeRF mode to OFF", ue);
		}
	}

	public BoardID getBoardID() throws UsbException {
		int id = readByte(Request.BOARD_ID_READ, (byte)0, (byte)0, false);
		return BoardID.fromValue(id);
	}

	/**
	 * BladeRF firmware version string -- is this needed for BladeRF??
	 */
	public String getFirmwareVersion() throws UsbException {
		ByteBuffer buffer = readArray(Request.VERSION_STRING_READ, 0, 0, 255);
		byte[] data = new byte[255];
		buffer.get(data);
		return new String(data);
	}

	/**
	 * BladeRF part id number and serial number -- this will likely need to be updated bladerf specific
	 */
	public Serial getSerial() throws UsbException {
		ByteBuffer buffer = readArray(Request.BOARD_PARTID_SERIALNO_READ, 0x0303, 0, 66);
		return new Serial(buffer);
	}

	/**
	 * Sets the BladeRF transciever mode -- this will likely need to be updated, bladerf specific
	 */
	public void setMode(Mode mode) throws UsbException {
		write(Request.SET_TRANSCEIVER_MODE, mode.getNumber(), 0);
	}

	/**
	 * Sets the BladeRF RX Channel
	 */
	public void setChannel(BladeRFrxChannel channel) throws UsbException {
		write(Request.SET_CHANNEL, channel.getNumber(), 0);
	}

	/**
	 * Sets the IF LNA Gain
	 */
	public void setLNAGain(BladeRFLNAGain gain) throws UsbException {
		int result = readByte(Request.SET_LNA_GAIN, 0, gain.getValue(), true);

		if(result != 1) {
			throw new UsbException("couldn't set lna gain to " + gain);
		}
	}

	/**
	 * Not implemented
	 */
	public long getTunedFrequency() throws SourceException {
		return mFrequencyController.getTunedFrequency();
	}

	@Override
	public void setTunedFrequency(long frequency) throws SourceException {
			ByteBuffer buffer = ByteBuffer.allocateDirect(8);
			buffer.order(ByteOrder.LITTLE_ENDIAN);

			int mhz = (int)(frequency / 1E6);
			int hz = (int)(frequency - (mhz * 1E6));

			buffer.putInt(mhz);
			buffer.putInt(hz);

			buffer.rewind();

			try {
				write(Request.SET_FREQUENCY, 0, 0, buffer);
			}
			catch(UsbException e) {
				mLog.error("error setting frequency [" + frequency + "]", e);
				throw new SourceException("error setting frequency [" + frequency + "]", e);
			}
	}

	@Override
	public double getCurrentSampleRate() {
		return mSampleRate.getRate();
	}

	@Override
    public void apply(TunerConfiguration config) throws SourceException {
        //Invoke super for frequency, frequency correction and autoPPM
        super.apply(config);

        if(config instanceof BladeRFTunerConfiguration BladeRFConfig) {
            //Convert legacy sample rate setting to new sample rates
            if(!BladeRFConfig.getSampleRate().isValidSampleRate()) {
                mLog.warn("Changing legacy BladeRF Sample Rates Setting [" + BladeRFConfig.getSampleRate().name() +
                        "] to current valid setting");
                BladeRFConfig.setSampleRate(BladeRFSampleRate.RATE_5_0);
            }

            try {
                setSampleRate(BladeRFConfig.getSampleRate());
				setChannel(BladeRFConfig.getChannel());
                setLNAGain(BladeRFConfig.getLNAGain());
            }
            catch(UsbException e) {
                throw new SourceException("Error while applying tuner configuration", e);
            }
        }
        else {
            throw new IllegalArgumentException("Invalid tuner configuration type [" + config.getClass() + "]");
        }
    }

	public ByteBuffer readArray(Request request, int value, int index, int length) throws UsbException {
		ByteBuffer buffer = ByteBuffer.allocateDirect(length);

		int transferred = LibUsb.controlTransfer(getDeviceHandle(), REQUEST_DEVICE_TO_HOST, request.getRequestNumber(),
				(short)value, (short)index, buffer, USB_TIMEOUT_US);

		if(transferred < 0) {
			throw new LibUsbException("read error", transferred);
		}

		return buffer;
	}

	public int read(Request request, int value, int index, int length) throws UsbException {
		if(!(length == 1 || length == 2 || length == 4)) {
            throw new IllegalArgumentException("invalid length [" + length +
                "] must be: byte=1, short=2, int=4 to read a primitive");
        }

        ByteBuffer buffer = readArray(request, value, index, length);

        byte[] data = new byte[buffer.capacity()];

        buffer.get(data);

        switch(data.length) {
            case 1:
                return data[0];
            case 2:
                return EndianUtils.readSwappedShort(data, 0);
            case 4:
                return EndianUtils.readSwappedInteger(data, 0);
            default:
                throw new UsbException("read() primitive returned an "
                    + "unrecognized byte array " + Arrays.toString(data));
        }
    }

	public synchronized int readByte(Request request, int value, int index, boolean signed) throws UsbException {
        ByteBuffer buffer = readArray(request, value, index, 1);

        if(signed) {
            return buffer.get();
        }
        else {
            return (buffer.get() & 0xFF);
        }
    }

    public synchronized void write(Request request, int value, int index, ByteBuffer buffer) throws UsbException {
        int status = LibUsb.controlTransfer(getDeviceHandle(), REQUEST_HOST_TO_DEVICE, request.getRequestNumber(),
			(short)value, (short)index, buffer, USB_TIMEOUT_US);

        if(status < LibUsb.SUCCESS) {
            throw new LibUsbException("error writing control transfer for request [" + request.name() +
                    "] value [" + value + "]", status);
        }
    }

	/**
	 * Sends a request that doesn't have a data payload
	 */
	public void write(Request request, int value, int index) throws UsbException {
		write(request, value, index, ByteBuffer.allocateDirect(0));
	}

	/**
	 * Sample Rate
	 */
	public void setSampleRate(BladeRFSampleRate rate) throws UsbException, SourceException {
        if(!rate.isValidSampleRate()) {
            rate = BladeRFSampleRate.RATE_5_0;
        }

        setSampleRateManual(rate.getRate(), 1);
        mFrequencyController.setSampleRate(rate.getRate());
        //setBandwidthSetting(rate.getFilter()); -- this will get set with the SampleRate
        mSampleRate = rate;
        getNativeBufferFactory().setSamplesPerMillisecond((float)getSampleRate() / 1000.0f);
    }

    public void setSampleRateManual(int frequency, int divider) throws UsbException {
        ByteBuffer buffer = ByteBuffer.allocateDirect(8);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(frequency);
        buffer.putInt(divider);
        write(Request.SET_SAMPLE_RATE, 0, 0, buffer);
    }

    public double getSampleRate() {
        return mSampleRate.getRate();
    }

	public enum BoardID {
		BLADERF(0, "BladeRF"),
		UNKNOWN(-1, "Unknown");

		private int mValue;
		private String mLabel;

		BoardID(int value, String label) {
			mValue = value;
			mLabel = label;
		}

		public int getValue() {
			return mValue;
		}

		public String getLabel() {
			return mLabel;
		}

		public static BoardID fromValue(int value) {
			if(value == 0) {
				return BLADERF;
			}

			return UNKNOWN;
		}
	}

    public enum Request {
        
		/** The following is HackRF way of doing the operations on the SDR
		 * Set device to receive mode
		 * Set Frequency
		 * Set gain
		 * etc
		SET_TRANSCEIVER_MODE(1),
        MAX2837_TRANSCEIVER_WRITE(2),
        MAX2837_TRANSCEIVER_READ(3),
        SI5351C_CLOCK_GENERATOR_WRITE(4),
        SI5351C_CLOCK_GENERATOR_READ(5),
        SET_SAMPLE_RATE(6),
        BASEBAND_FILTER_BANDWIDTH_SET(7),
        RFFC5071_MIXER_WRITE(8),
        RFFC5071_MIXER_READ(9),
        SPIFLASH_ERASE(10),
        SPIFLASH_WRITE(11),
        SPIFLASH_READ(12),
        BOARD_ID_READ(14),
        VERSION_STRING_READ(15),
        SET_FREQUENCY(16),
        AMP_ENABLE(17),
        BOARD_PARTID_SERIALNO_READ(18),
        SET_LNA_GAIN(19),
        SET_VGA_GAIN(20),
        SET_TXVGA_GAIN(21),
        ANTENNA_ENABLE(23),
        SET_FREQUENCY_EXPLICIT(24),
		SET_CHANNEL(25);
		*/

		OPEN_BLADERF(1),
		SET_CHANNEL(2),
		CHANNEL_CONFIG(3),
		SET_FREQUENCY(4),
		SET_SAMPLE_RATE(5),
		BOARD_PARTID_SERIALNO_READ(6),
		SET_LNA_GAIN(7), //the below were added so it doesn't break
		BOARD_ID_READ(8),
		VERSION_STRING_READ(9),
		SET_BANDWIDTH(10),
		SET_TRANSCEIVER_MODE(11);

        private byte mRequestNumber;

        Request(int number) {
            mRequestNumber = (byte)number;
        }

        public byte getRequestNumber() {
            return mRequestNumber;
        }
    }

	public enum BladeRFrxChannel {
		RX_CHANNEL_0(0),
		RX_CHANNEL_1(1);

		private byte mChannel;

		BladeRFrxChannel(int number) {
			mChannel = (byte)number;
		}

		public byte getNumber() {
			return mChannel;
		}
	}

	public enum BladeRFSampleRate {
        //Changes to enumerate correct rates
        RATE_1_75(1750000, "1.750 MHz", BladeRFBandwidthSetting.F1_75),
        RATE_2_5(2500000, "2.500 MHz", BladeRFBandwidthSetting.F2_50),
        RATE_3_5(3500000, "3.500 MHz", BladeRFBandwidthSetting.F3_50),
        RATE_5_0(5000000, "5.000 MHz", BladeRFBandwidthSetting.F5_00),
        RATE_5_5(5500000, "5.500 MHz", BladeRFBandwidthSetting.F5_50),
        RATE_6_0(6000000, "6.000 MHz", BladeRFBandwidthSetting.F6_00),
        RATE_7_0(7000000, "7.000 MHz", BladeRFBandwidthSetting.F7_00),
        RATE_8_0(8000000, "8.000 MHz", BladeRFBandwidthSetting.F8_00),
        RATE_9_0(9000000, "9.000 MHz", BladeRFBandwidthSetting.F9_00),
        RATE_10_0(10000000, "10.000 MHz", BladeRFBandwidthSetting.F10_00),
        RATE_12_0(12000000, "12.000 MHz", BladeRFBandwidthSetting.F12_00),
        RATE_14_0(14000000, "14.000 MHz", BladeRFBandwidthSetting.F14_00),
        RATE_15_0(15000000, "15.000 MHz", BladeRFBandwidthSetting.F15_00),
        RATE_20_0(20000000, "20.000 MHz", BladeRFBandwidthSetting.F20_00);

        private int mRate;
        private String mLabel;
        private BladeRFBandwidthSetting mFilter;

        BladeRFSampleRate(int rate, String label, BladeRFBandwidthSetting filter) {
            mRate = rate;
            mLabel = label;
            mFilter = filter;
        }

        public static EnumSet<BladeRFSampleRate> VALID_SAMPLE_RATES = EnumSet.range(RATE_1_75, RATE_20_0);

        public int getRate() {
            return mRate;
        }

        public String getLabel() {
            return mLabel;
        }

        public String toString() {
            return mLabel;
        }

        public BladeRFBandwidthSetting getFilter() {
            return mFilter;
        }

        public boolean isValidSampleRate() {
            return VALID_SAMPLE_RATES.contains(this);
        }
    }

	public enum BladeRFBandwidthSetting {
		FAUTO(0, "AUTO"),
        F1_75(1750000, "1.75 MHz"),
        F2_50(2500000, "2.50 MHz"),
        F3_50(3500000, "3.50 MHz"),
        F5_00(5000000, "5.00 MHz"),
        F5_50(5500000, "5.50 MHz"),
        F6_00(6000000, "6.00 MHz"),
        F7_00(7000000, "7.00 MHz"),
        F8_00(8000000, "8.00 MHz"),
        F9_00(9000000, "9.00 MHz"),
        F10_00(10000000, "10.00 MHz"),
        F12_00(12000000, "12.00 MHz"),
        F14_00(14000000, "14.00 MHz"),
        F15_00(15000000, "15.00 MHz"),
        F20_00(20000000, "20.00 MHz"),
        F24_00(24000000, "24.00 MHz"),
        F28_00(28000000, "28.00 MHz");

		private int mBandwidth;
		private String mLabel;

		BladeRFBandwidthSetting(int bandwidth, String label) {
			mBandwidth = bandwidth;
			mLabel = label;
		}

		public int getBandwidth() {
			return mBandwidth;
		}

		public int getHighValue() {
			return mBandwidth >> 16;
		}

		public int getLowValue() {
			return mBandwidth & 0xFFFF;
		}

		public String getLabel() {
			return mLabel;
		}
	}

	public enum Mode {
		OFF(0, "Off"),
		RECEIVE(1, "Receive"),
		TRANSMIT(2, "Transmit"),
		SS(33, "SS");

		private byte mNumber;
		private String mLabel;

		Mode(int number, String label) {
			mNumber = (byte)number;
			mLabel = label;
		}

		public byte getNumber() {
			return mNumber;
		}

		public String getLabel() {
			return mLabel;
		}

		public String toString() {
			return mLabel;
		}
	}

	public enum BladeRFLNAGain {
        GAIN_0(0),
        GAIN_8(8),
        GAIN_16(16),
        GAIN_24(24),
        GAIN_32(32),
        GAIN_40(40),
		GAIN_50(50),
		GAIN_60(60),
		GAIN_70(70),
		GAIN_75(75);

        private int mValue;

        BladeRFLNAGain(int value)
        {
            mValue = value;
        }

        public int getValue()
        {
            return mValue;
        }

        public String toString()
        {
            return mValue + " dB";
        }
    }

	/**
	 * BladeRF part id and serial number parsing class
	 */
	public class Serial {
		private byte[] mData;

		public Serial(ByteBuffer buffer) {
			mData = new byte[buffer.capacity()];

			buffer.get(mData);
		}

		public String getPartID() {
			int part0 = EndianUtils.readSwappedInteger(mData, 0);
			int part1 = EndianUtils.readSwappedInteger(mData, 4);

			StringBuilder sb = new StringBuilder();

			sb.append(String.format("%08X", part0));
			sb.append("-");
			sb.append(String.format("%08X", part1));

			return sb.toString();
		}

		public String getSerialNumber() {

			String serialStr = new String(mData, 2, mData.length - 2, Charset.forName("UTF-16LE"));
			StringBuilder sb = new StringBuilder();
			sb.append(serialStr);

			return sb.toString();
		}
	}
}
