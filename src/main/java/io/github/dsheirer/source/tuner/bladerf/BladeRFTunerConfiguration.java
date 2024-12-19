package io.github.dsheirer.source.tuner.bladerf;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import io.github.dsheirer.source.tuner.TunerType;
import io.github.dsheirer.source.tuner.configuration.TunerConfiguration;
import io.github.dsheirer.source.tuner.bladerf.BladeRFTunerController.BladeRFLNAGain;
import io.github.dsheirer.source.tuner.bladerf.BladeRFTunerController.BladeRFSampleRate;

public class BladeRFTunerConfiguration extends TunerConfiguration {
	private BladeRFSampleRate mSampleRate = BladeRFSampleRate.RATE_5_0;
	private BladeRFLNAGain mLNAGain = BladeRFLNAGain.GAIN_16;

    /**
     * Default constructor for JAXB
     */
    public BladeRFTunerConfiguration()
    {
        super(BladeRFTunerController.MINIMUM_TUNABLE_FREQUENCY_HZ, BladeRFTunerController.MAXIMUM_TUNABLE_FREQUENCY_HZ);
    }

    public BladeRFTunerConfiguration(String uniqueID)
    {
        super(uniqueID);
    }

    @JsonIgnore
    @Override
    public TunerType getTunerType()
    {
        return TunerType.BLADERF;
    }

    @JacksonXmlProperty(isAttribute = true, localName = "lna_gain")
    public BladeRFLNAGain getLNAGain()
    {
        return mLNAGain;
    }

    public void setLNAGain(BladeRFLNAGain lnaGain)
    {
        mLNAGain = lnaGain;
    }

    @JacksonXmlProperty(isAttribute = true, localName = "sample_rate")
    public BladeRFSampleRate getSampleRate()
    {
        return mSampleRate;
    }

    public void setSampleRate(BladeRFSampleRate sampleRate)
    {
        mSampleRate = sampleRate;
    }
}
