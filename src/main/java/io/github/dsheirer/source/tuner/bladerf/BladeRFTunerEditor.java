package io.github.dsheirer.source.tuner.bladerf;


import io.github.dsheirer.preference.UserPreferences;
import io.github.dsheirer.source.SourceException;
import io.github.dsheirer.source.tuner.bladerf.BladeRFTunerController.BladeRFLNAGain;
import io.github.dsheirer.source.tuner.bladerf.BladeRFTunerController.BladeRFSampleRate;
import io.github.dsheirer.source.tuner.bladerf.BladeRFTunerController.BladeRFrxChannel;
import io.github.dsheirer.source.tuner.manager.DiscoveredTuner;
import io.github.dsheirer.source.tuner.manager.TunerManager;
import io.github.dsheirer.source.tuner.ui.TunerEditor;
import net.miginfocom.swing.MigLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JSeparator;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;
import javax.usb.UsbException;

/**
 * BladeRF Tuner Editor
 */
public class BladeRFTunerEditor extends TunerEditor<BladeRFTuner,BladeRFTunerConfiguration> {
    private static final long serialVersionID = 1L;
    private static final Logger mLog = LoggerFactory.getLogger(BladeRFTunerEditor.class);
    private JButton mTunerInfo;
    private JComboBox<BladeRFSampleRate> mSampleRateCombo;
    private JComboBox<BladeRFrxChannel> mChannelCombo;
    private JComboBox<BladeRFLNAGain> mLnaGainCombo;

    /**
     * Contructs an instance
     * @param userPreferences for wide-band recordings
     * @param tunerManager to save configuration
     * @param discoveredTuner to control
     */
    public BladeRFTunerEditor(UserPreferences userPreferences, TunerManager tunerManager, DiscoveredTuner discoveredTuner) {
        super(userPreferences, tunerManager, discoveredTuner);
        init();
        tunerStatusUpdated();
    }

    @Override
    public long getMinimumTunableFrequency() {
        return BladeRFTunerController.MINIMUM_TUNABLE_FREQUENCY_HZ;
    }

    @Override
    public long getMaximumTunableFrequency()
    {
        return BladeRFTunerController.MAXIMUM_TUNABLE_FREQUENCY_HZ;
    }

    private void init()
    {
        setLayout(new MigLayout("fill,wrap 3", "[right][grow,fill][fill]",
                "[][][][][][][][][][][grow]"));

        add(new JLabel("Tuner:"));
        add(getTunerIdLabel());
        add(getTunerInfoButton());

        add(new JLabel("Status:"));
        add(getTunerStatusLabel(), "wrap");

        add(getButtonPanel(), "span,align left");

        add(new JSeparator(), "span,growx,push");

        add(new JLabel("RX Channel:")); // need to add class for this below

        add(new JLabel("Frequency (MHz):"));
        add(getFrequencyPanel(), "wrap");

        add(new JLabel("Sample Rate:"));
        add(getSampleRateCombo(), "wrap");

        add(new JSeparator(), "span,growx,push");

        add(new JLabel("Gain Control"));
        add(new JLabel("LNA:"));
        add(getLnaGainCombo(), "wrap");
    }

    @Override
    protected void tunerStatusUpdated()
    {
        setLoading(true);
        if(hasTuner())
        {
            getTunerIdLabel().setText(getTuner().getPreferredName());
        }
        else
        {
            getTunerIdLabel().setText(getDiscoveredTuner().getId());
        }

        String status = getDiscoveredTuner().getTunerStatus().toString();
        if(getDiscoveredTuner().hasErrorMessage())
        {
            status += " - " + getDiscoveredTuner().getErrorMessage();
        }
        getTunerStatusLabel().setText(status);
        getButtonPanel().updateControls();
        getFrequencyPanel().updateControls();
        getSampleRateCombo().setEnabled(hasTuner() && !getTuner().getTunerController().isLockedSampleRate());
        updateSampleRateToolTip();
        getTunerInfoButton().setEnabled(hasTuner());

        getLnaGainCombo().setEnabled(hasTuner());

        if(hasConfiguration())
        {
            getSampleRateCombo().setSelectedItem(getConfiguration().getSampleRate());
            getLnaGainCombo().setSelectedItem(getConfiguration().getLNAGain());
        }

        setLoading(false);
    }

    private JComboBox getLnaGainCombo()
    {
        if(mLnaGainCombo == null)
        {
            mLnaGainCombo = new JComboBox<BladeRFLNAGain>(BladeRFLNAGain.values());
            mLnaGainCombo.setToolTipText("<html>LNA Gain.  Adjust to set the IF gain</html>");
            mLnaGainCombo.setEnabled(false);
            mLnaGainCombo.addActionListener(arg0 ->
            {
                if(!isLoading())
                {
                    try
                    {
                        BladeRFLNAGain lnaGain = (BladeRFLNAGain) mLnaGainCombo.getSelectedItem();

                        if(lnaGain == null)
                        {
                            lnaGain = BladeRFLNAGain.GAIN_16;
                        }

                        getTuner().getController().setLNAGain(lnaGain);
                        save();
                    }
                    catch(UsbException e)
                    {
                        JOptionPane.showMessageDialog(BladeRFTunerEditor.this, "BladeRF Tuner Controller"
                                + " - couldn't apply the LNA gain setting - " + e.getLocalizedMessage());
                        mLog.error("BladeRF Tuner Controller - couldn't apply LNA gain setting - ", e);
                    }
                }
            });
        }

        return mLnaGainCombo;
    }

    private JComboBox getSampleRateCombo()
    {
        if(mSampleRateCombo == null)
        {
            BladeRFSampleRate[] validRates = BladeRFSampleRate.VALID_SAMPLE_RATES.toArray(new BladeRFSampleRate[0]);
            mSampleRateCombo = new JComboBox<>(validRates);
            mSampleRateCombo.setEnabled(false);
            mSampleRateCombo.addActionListener(e ->
            {
                if(!isLoading())
                {
                    BladeRFSampleRate sampleRate = (BladeRFSampleRate)getSampleRateCombo().getSelectedItem();

                    try
                    {
                        getTuner().getController().setSampleRate(sampleRate);
                        //Adjust the min/max values for the sample rate.
                        adjustForSampleRate(sampleRate.getRate());
                        save();
                    }
                    catch(SourceException | UsbException e2)
                    {
                        JOptionPane.showMessageDialog(BladeRFTunerEditor.this, "BladeRF Tuner Controller"
                                + " - couldn't apply the sample rate setting [" + sampleRate.getLabel() +
                                "] " + e2.getLocalizedMessage());

                        mLog.error("BladeRF Tuner Controller - couldn't apply sample rate setting [" +
                                sampleRate.getLabel() + "]", e);
                    }
                }
            });
        }

        return mSampleRateCombo;
    }

    private JButton getTunerInfoButton()
    {
        if(mTunerInfo == null)
        {
            mTunerInfo = new JButton("Tuner Info");
            mTunerInfo.setToolTipText("Provides details and information about the tuner");
            mTunerInfo.setEnabled(false);
            mTunerInfo.addActionListener(e -> JOptionPane.showMessageDialog(BladeRFTunerEditor.this,
                    getTunerInfo(), "Tuner Info", JOptionPane.INFORMATION_MESSAGE));
        }

        return mTunerInfo;
    }

    /**
     * Updates the sample rate tooltip according to the tuner controller's lock state.
     */
    private void updateSampleRateToolTip()
    {
        if(hasTuner() && getTuner().getController().isLockedSampleRate())
        {
            mSampleRateCombo.setToolTipText("Sample Rate is locked.  Disable decoding channels to unlock.");
        }
        else
        {
            mSampleRateCombo.setToolTipText("Select a sample rate for the tuner");
        }
    }

    @Override
    public void setTunerLockState(boolean locked) {
        getFrequencyPanel().updateControls();
        getSampleRateCombo().setEnabled(!locked);
        updateSampleRateToolTip();
    }

    private String getTunerInfo() {
        StringBuilder sb = new StringBuilder();

        sb.append("<html><h3>BladeRF Tuner</h3>");

        String serial = null;

        try {
            if(hasTuner()) {
                serial = getTuner().getController().getSerial();
            }
        }
        catch(Exception e) {
            mLog.error("couldn't read BladeRF serial number", e);
        }

        if(serial != null) {
            sb.append("<b>Serial: </b>");
            sb.append(serial);
            sb.append("<br>");
        }
        else {
            sb.append("<b>Serial: Unknown</b><br>");
        }

        String firmware = null;

        try {
            if(hasTuner()) {
                firmware = getTuner().getController().getFirmwareVersion();
            }
        }
        catch(Exception e) {
            mLog.error("couldn't read BladeRF firmware version", e);
        }

        if(firmware != null) {
            sb.append("<b>Firmware: </b>");
            sb.append(firmware);
            sb.append("<br>");
        }
        else {
            sb.append("<b>Firmware: Unknown</b><br>");
        }

        return sb.toString();
    }

    @Override
    public void save()
    {
        if(hasConfiguration() && !isLoading())
        {
            getConfiguration().setFrequency(getFrequencyControl().getFrequency());
            getConfiguration().setMinimumFrequency(getMinimumFrequencyTextField().getFrequency());
            getConfiguration().setMaximumFrequency(getMaximumFrequencyTextField().getFrequency());
            double value = ((SpinnerNumberModel) getFrequencyCorrectionSpinner().getModel()).getNumber().doubleValue();
            getConfiguration().setFrequencyCorrection(value);
            getConfiguration().setAutoPPMCorrectionEnabled(getAutoPPMCheckBox().isSelected());
            getConfiguration().setSampleRate((BladeRFSampleRate)getSampleRateCombo().getSelectedItem());
            getConfiguration().setLNAGain((BladeRFLNAGain)getLnaGainCombo().getSelectedItem());
            saveConfiguration();
        }
    }
}