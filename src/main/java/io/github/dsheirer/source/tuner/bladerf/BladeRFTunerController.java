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
	public static final int PERIPHERAL_TIMEOUT = 250;
	public static final long CTRL_TIMEOUT_MS = 1000l;
	public static final long BULK_TIMEOUT_MS = 1000l;
	public static final long USB_MSG_SIZE_SS = 2048l;
	public static final long USB_MSG_SIZE_HS = 1024l;
	public static final int GAIN_MIN = 5;
	public static final int GAIN_MAX = 66;
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
			mLog.info("Here is the serial number: " + getSerial());
			mLog.info("Here is the firmware version: " + getFirmwareVersion());

			mLog.info("The first step is to do the configuration on the BladeRF... setting receive mode, channel, etc");
			//setMode(Mode.RECEIVE);
			//setMode(Mode.ENABLE);
			mLog.info("This is second step");
			//setFrequency(DEFAULT_FREQUENCY);
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

	/**
	 * BladeRF firmware version string
	 */
	public String getFirmwareVersion() throws UsbException {
		String firmwareVersion = readDescriptor(BladeUSBStringDescriptors.BLADE_USB_STR_INDEX_FW_VER, 33);
		return new String(firmwareVersion);
	}

	/**
	 * BladeRF serial number
	 */
	public String getSerial() throws UsbException {
		String serialNumber = readDescriptor(BladeUSBStringDescriptors.BLADE_USB_STR_INDEX_SERIAL, 33);
		return new String(serialNumber);
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

	//@Override
	public void setTunedFrequency1(long frequency) throws SourceException {
		int addr = getRFICAddress(0x04, 0x01);
		
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

	public static int getRFICAddress(int cmd, int ch) {
		int address = (cmd & 0xFF) + ((ch & 0xF) << 8);
		return address;
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

	public String readDescriptor(BladeUSBStringDescriptors descriptor, int length) throws UsbException {
		StringBuffer buffer = new StringBuffer(length);
		int bytesRead = LibUsb.getStringDescriptorAscii(getDeviceHandle(), descriptor.getRequestNumber(), buffer);
		if (bytesRead < 0) {
			throw new UsbException("Error reading string descriptor: " + LibUsb.errorName(bytesRead));
		}
		return buffer.toString().trim();
	}

	public ByteBuffer resetDevice(Request request) throws UsbException {
		ByteBuffer buffer = ByteBuffer.allocateDirect(255);
		int reset = LibUsb.controlTransfer(getDeviceHandle(), REQUEST_HOST_TO_DEVICE, Request.BLADE_USB_CMD_RESET.getRequestNumber(), (short)0, (short)0, buffer, USB_TIMEOUT_US);
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

	public enum BladeUSBBackend {
		SAMPLE_EP_IN((byte) 0x81),
		SAMPLE_EP_OUT((byte) 0x01),
		PERIPHERAL_EP_IN((byte) 0x82),
		PERIPHERAL_EP_OUT((byte) 0x02);

		private byte mRequestNumber;

		BladeUSBBackend(byte number) {
			mRequestNumber = number;
		}

		public byte getRequestNumber() {
			return mRequestNumber;
		}
	}

	public enum BladeUSBStringDescriptors {
		BLADE_USB_STR_INDEX_MFR((byte) 0x01),
		BLADE_USB_STR_INDEX_PRODUCT((byte) 0x02),
		BLADE_USB_STR_INDEX_SERIAL((byte) 0x03),
		BLADE_USB_STR_INDEX_FW_VER((byte) 0x04);

		private byte mRequestNumber;

		BladeUSBStringDescriptors(byte number) {
			mRequestNumber = number;
		}

		public byte getRequestNumber() {
			return mRequestNumber;
		}
	}

    public enum BladeUSBRequest {
		BLADE_USB_CMD_QUERY_VERSION((byte) 0x00),
		BLADE_USB_CMD_QUERY_FPGA_STATUS((byte) 0x01),
		BLADE_USB_CMD_BEGIN_PROG((byte) 0x02),
		BLADE_USB_CMD_END_PROG((byte) 0x03),
		BLADE_USB_CMD_RF_RX((byte) 0x04),
		BLADE_USB_CMD_RF_TX((byte) 0x05),
		BLADE_USB_CMD_QUERY_DEVICE_READY((byte) 0x06),
		BLADE_USB_CMD_QUERY_FLASH_ID((byte) 0x07),
		BLADE_USB_CMD_QUERY_FPGA_SOURCE((byte) 0x08),
		BLADE_USB_CMD_FLASH_READ((byte) 0x100),
		BLADE_USB_CMD_READ_OTP((byte) 0x103),
		BLADE_USB_CMD_WRITE_OTP((byte) 0x104),
		BLADE_USB_CMD_RESET((byte) 0x105),
		BLADE_USB_CMD_JUMP_TO_BOOTLOADER((byte) 0x106),
		BLADE_USB_CMD_READ_PAGE_BUFFER((byte) 0x107),
		BLADE_USB_CMD_WRITE_PAGE_BUFFER((byte) 0x108),
		BLADE_USB_CMD_LOCK_OTP((byte) 0x109),
		BLADE_USB_CMD_READ_CAL_CACHE((byte) 0x110),
		BLADE_USB_CMD_INVALIDATE_CAL_CACHE((byte) 0x111),
		BLADE_USB_CMD_REFRESH_CAL_CACHE((byte) 0x112),
		BLADE_USB_CMD_SET_LOOPBACK((byte) 0x113),
		BLADE_USB_CMD_GET_LOOPBACK((byte) 0x114),
		BLADE_USB_CMD_READ_LOG_ENTRY((byte) 0x115);

		private byte mRequestNumber;

		BladeUSBRequest(byte number) {
			mRequestNumber = number;
		}

		public byte getRequestNumber() {
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

	public enum Request {
		OPEN_BLADERF((byte) 0x01),
		SET_CHANNEL((byte) 0x02),
		CHANNEL_CONFIG((byte) 0x03),
		SET_SAMPLE_RATE((byte) 0x05),
		BOARD_PARTID_SERIALNO_READ((byte) 0x06),
		SET_LNA_GAIN((byte) 0x07), //the below were added so it doesn't break
		VERSION_STRING_READ((byte) 0x09),
		SET_BANDWIDTH((byte) 0x10),
		SET_TRANSCEIVER_MODE((byte) 0x11),
		SET_FREQUENCY((byte) 0x12),
		BLADE_USB_CMD_RESET((byte) 0x105);

        private byte mRequestNumber;

        Request(byte number) {
            mRequestNumber = number;
        }

        public byte getRequestNumber() {
            return mRequestNumber;
        }
    }

	public enum BladeRFrxChannel {
		RX_CHANNEL_0((byte) 0x00),
		RX_CHANNEL_1((byte) 0x01);

		private byte mChannel;

		BladeRFrxChannel(byte number) {
			mChannel = number;
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
		OFF((byte) 0x00, "Off"),
		RECEIVE((byte) 0x01, "Receive"),
		TRANSMIT((byte) 0x02, "Transmit"),
		SS((byte) 0x33, "SS");

		private byte mNumber;
		private String mLabel;

		Mode(byte number, String label) {
			mNumber = number;
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

}