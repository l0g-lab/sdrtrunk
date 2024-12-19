package io.github.dsheirer.source.tuner.bladerf;

import io.github.dsheirer.preference.source.ChannelizerType;
import io.github.dsheirer.source.tuner.ITunerErrorListener;
import io.github.dsheirer.source.tuner.Tuner;
import io.github.dsheirer.source.tuner.TunerClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BladeRF Tuner
 */
public class BladeRFTuner extends Tuner {
	private final static Logger mLog = LoggerFactory.getLogger(BladeRFTuner.class);

	/**
	 * Constructs an instance
     * @param controller for the BladeRF
     * @param tunerErrorListener to listen for errors from this tuner
     * @param channelizerType for the channelizer
	 */
	public BladeRFTuner(BladeRFTunerController controller, ITunerErrorListener tunerErrorListener,
					ChannelizerType channelizerType) {
		super(controller, tunerErrorListener, channelizerType);
	}

	@Override
	public String getPreferredName() {
		try {
			BladeRFTunerController.Serial serial = getController().getSerial();
			return "BladeRF " + getController().getTunerType() + " " + serial.getSerialNumber();
		}
		catch(Exception e) {
			//No - op
		}

		return "BladeRF " + getController().getTunerType();
	}

	public BladeRFTunerController getController() {
		return (BladeRFTunerController)getTunerController();
	}

	@Override
	public TunerClass getTunerClass() {
		return TunerClass.BLADERF;
	}

	@Override
	public double getSampleSize() {
		return 11.0;
	}

	/**@Override
	public String getUniqueID()	{
		try {
			return getTunerClass().toString() + " " + getController().getSerial().getSerialNumber();
		}
		catch(Exception e) {
			mLog.error("error getting serial number", e);
		}

		return Serial().getSerialNumber();
	}*/

	@Override
	public int getMaximumUSBBitsPerSecond() {
		//16 bits per sample * 20MSPS
		return 320000000;
	}
}
