package io.github.dsheirer.source.tuner.bladerf;

import io.github.dsheirer.source.SourceException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.EnumSet;
import org.apache.commons.io.EndianUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BladeRFNIOS {
	// Setup logger
	private final static Logger mLog = LoggerFactory.getLogger(BladeRFNIOS.class);
	
	//Constants
	public final static int NIOS_PKT_LEN = 16;

    public final class Nios8x32Packet {
		private static final byte MAGIC			 	= (byte) 'C';

		// Request packet indices
		private static final int IDX_MAGIC       	= 0;
		private static final int IDX_TARGET_ID   	= 1;
		private static final int IDX_FLAGS       	= 2;
		private static final int IDX_RESV1       	= 3;
		private static final int IDX_ADDR        	= 4;
		private static final int IDX_DATA        	= 5;
		private static final int IDX_RESV2       	= 9;

		// Target IDs
		private static final byte TARGET_VERSION  	= (byte) 0x00;  // FPGA version (read only)
		private static final byte TARGET_CONTROL  	= (byte) 0x01;  // FPGA control/config register
		private static final byte TARGET_ADF4351  	= (byte) 0x02;  // XB-200 ADF4351 register access (write-only)
		private static final byte TARGET_RFFE_CSR 	= (byte) 0x03;  // RFFE control & status GPIO
		private static final byte TARGET_ADF400X  	= (byte) 0x04;  // ADF400x config
		private static final byte TARGET_FASTLOCK 	= (byte) 0x05;  // Save AD9361 fast lock profile to Nios
					
		// IDs 0x80 through 0xff will not be assigned by Nuand. These are reserved for user customizations
		private static final byte TARGET_USR1     	= (byte) 0x80;
		private static final byte TARGET_USR128   	= (byte) 0xff;

		// Flag bits
		private static final int FLAG_WRITE      	= (1 << 0);
		private static final int FLAG_SUCCESS    	= (1 << 1);

		private Nios8x32Packet() {}
	}

	public static class Nios8x32Response {
		public BladeNiosPKT target;
		public boolean write;
		public int addr;
		public long data;
	}

    public void nios_access(ByteBuffer buf) throws SourceException {
		//print_buf(buf);
		// Send command
		//write(BladeUSBBackend.PERIPHERAL_EP_OUT);

		// Retreive the request
		//read(BladeUSBBackend.PERIPHERAL_EP_IN);
		//print_buf(buf);
	}

	public void nios_rffe_control_read() throws SourceException {
		Nios8x32Response response = nios_8x32_read(BladeNiosPKT.NIOS_PKT_8x32_TARGET_RFFE_CSR, 0);
		mLog.info("RFFE read: 0x{}", Long.toHexString(response.data));
	}

	public void nios_rffe_control_write(long reg) throws SourceException {
		nios_8x32_write(BladeNiosPKT.NIOS_PKT_8x32_TARGET_RFFE_CSR, 0, reg);
	}

	public Nios8x32Response nios_8x32_read(BladeNiosPKT target, int addr) throws SourceException {
		ByteBuffer buf = ByteBuffer.allocate(NIOS_PKT_LEN);
		nios_pkt_8x32_pack(buf, target, false, addr, 0);
		nios_access(buf);
		return nios_pkt_8x32_unpack(buf);
	}

	public void nios_8x32_write(BladeNiosPKT target, int addr, long reg) throws SourceException {
		ByteBuffer buf = ByteBuffer.allocate(NIOS_PKT_LEN);

		nios_pkt_8x32_pack(buf, BladeNiosPKT.NIOS_PKT_8x32_TARGET_RFFE_CSR, true, addr, reg);
		nios_access(buf);
		//nios_pkt_8x32_unpack(buf, null, null, null, null);
	}

	// Pack request buffer
	public void nios_pkt_8x32_pack(ByteBuffer buf, BladeNiosPKT target, boolean write, int addr, long data) throws SourceException {
		buf.put(Nios8x32Packet.IDX_MAGIC, Nios8x32Packet.MAGIC);
    	buf.put(Nios8x32Packet.IDX_TARGET_ID, target.getRequestNumber());
		buf.put(Nios8x32Packet.IDX_FLAGS, write ? (byte) Nios8x32Packet.FLAG_WRITE : (byte) 0x00);
		buf.put(Nios8x32Packet.IDX_RESV1, (byte) 0x00);
		buf.put(Nios8x32Packet.IDX_ADDR, (byte) (addr & 0xFF));

		buf.put(Nios8x32Packet.IDX_DATA + 0, (byte) (data & 0xFF));
		buf.put(Nios8x32Packet.IDX_DATA + 1, (byte) ((data >> 8) & 0xFF));
		buf.put(Nios8x32Packet.IDX_DATA + 2, (byte) ((data >> 16) & 0xFF));
		buf.put(Nios8x32Packet.IDX_DATA + 3, (byte) ((data >> 24) & 0xFF));

		for (int i = 0; i < 7; i++) {
			buf.put(Nios8x32Packet.IDX_RESV2 + i, (byte) 0x00);
		}
	}

	// Unpack request buffer
	public Nios8x32Response nios_pkt_8x32_unpack(ByteBuffer buf) throws SourceException {
		Nios8x32Response resp = new Nios8x32Response();

		byte targetByte = buf.get(Nios8x32Packet.IDX_TARGET_ID);
		resp.target = BladeNiosPKT.fromByte(targetByte);  // You'll define this below

		byte flags = buf.get(Nios8x32Packet.IDX_FLAGS);
		resp.write = (flags & Nios8x32Packet.FLAG_WRITE) != 0;

		resp.addr = buf.get(Nios8x32Packet.IDX_ADDR) & 0xFF;

		int b0 = buf.get(Nios8x32Packet.IDX_DATA + 0) & 0xFF;
		int b1 = buf.get(Nios8x32Packet.IDX_DATA + 1) & 0xFF;
		int b2 = buf.get(Nios8x32Packet.IDX_DATA + 2) & 0xFF;
		int b3 = buf.get(Nios8x32Packet.IDX_DATA + 3) & 0xFF;

		resp.data = ((long)b0) | ((long)b1 << 8) | ((long)b2 << 16) | ((long)b3 << 24);
		return resp;
	}

	// Pack response buffer
	public void nios_pkt_8x32_resp_pack(ByteBuffer buf, BladeNiosPKT target, boolean write, int addr, long data) throws SourceException {
		nios_pkt_8x32_pack(buf, target, write, addr, data);
	}

	public Nios8x32Response nios_pkt_8x32_resp_unpack(ByteBuffer buf) throws SourceException {
 		return nios_pkt_8x32_unpack(buf);
	}

	// Enums
	public enum BladeNiosPKT {
		NIOS_PKT_8x32_TARGET_RFFE_CSR((byte) 0x03); // RFFE control & status GPIO

		private byte mRequestNumber;

		BladeNiosPKT(byte number) {
			mRequestNumber = number;
		}

		public byte getRequestNumber() {
			return mRequestNumber;
		}

		public static BladeNiosPKT fromByte(byte b) throws SourceException {
			for (BladeNiosPKT pkt : BladeNiosPKT.values()) {
				if (pkt.getRequestNumber() == b) {
					return pkt;
				}
			}
			throw new SourceException("Unknown");
		}
	}

}