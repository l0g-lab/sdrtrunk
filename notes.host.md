# BladeRF Frequency Setting - Full Breakdown

Since C isn't object oriented, there is a lot of abstraction to ensure code and logic work for multiple versions of the bladeRF (v1 / v2).

We need to peel back all the layers to get down to the lower level registry reads/writes to configure the bladeRF.

Below contains code snippets only for setting the frequency. My hope is, once we work all this out, we can implement other requirements to fully configure the bladeRF to finally pass data into sdr-trunk.

With the above, I'm still not sure if we need to use "FPGA" or "Host" as the controller... it seems like the object oriented like C code allows for either, and I don't know which we should use. Below is based on FPGA just cause that's where I'm at up to now.



**Top-level API**

https://github.com/Nuand/bladeRF/blob/master/host/libraries/libbladeRF/src/bladerf.c
```c
/******************************************************************************/
/* Frequency */
/******************************************************************************/

int bladerf_set_frequency(struct bladerf *dev,
                          bladerf_channel ch,
                          bladerf_frequency frequency)
{
    int status;
    MUTEX_LOCK(&dev->lock);

    status = dev->board->set_frequency(dev, ch, frequency);

    if (dev->gain_tbls[ch].enabled && status == 0) {
        status = apply_gain_correction(dev, ch, frequency);
        if (status != 0) {
            log_error("Failed to set gain correction\n");
        }
    }

    MUTEX_UNLOCK(&dev->lock);
    return status;
}
```

This is the user-facing API. It passes arguments (device, channel, frequency) to the `dev->board->set_frequency` function pointer. 

Here is a snippet of the `set_frequency` section of board.h

https://github.com/Nuand/bladeRF/blob/master/host/libraries/libbladeRF/src/board/board.h
```c
struct bladerf {
    /* Backend-specific implementations */
    const struct backend_fns *backend;

    /* Board-specific implementations */
    const struct board_fns *board;
}

struct board_fns {
    int (*set_frequency)(struct bladerf *dev,
                         bladerf_channel ch,
                         bladerf_frequency frequency);
}

extern const struct board_fns *bladerf_boards[];
```

Below is the entire content of the `board.c` file. The ID of the board (1 or 2) will determine which function pointers to use.

https://github.com/Nuand/bladeRF/blob/master/host/libraries/libbladeRF/src/board/board.c
```c
#include "host_config.h"

#include "board.h"

extern const struct board_fns bladerf1_board_fns;
extern const struct board_fns bladerf2_board_fns;

const struct board_fns *bladerf_boards[] = {
    &bladerf1_board_fns,
    &bladerf2_board_fns,
};

const unsigned int bladerf_boards_len = ARRAY_SIZE(bladerf_boards);
```

Below is the board/bladerf2.c file where the next function lives




**Second-layer: bladerf2.c** 

This is the second layer which gets passed via `dev->board->set_frequency(dev, ch, frequency);` from the first section of code above.

It passes parameters off to another function pointer - `board_data->rfic->set_frequency` - via the import of board/bladerf2/common.h which contains a structure for bladerf2.

https://github.com/Nuand/bladeRF/blob/master/host/libraries/libbladeRF/src/board/bladerf2/bladerf2.c

```c
/******************************************************************************/
/* Frequency */
/******************************************************************************/
static int bladerf2_set_frequency(struct bladerf *dev,
                                  bladerf_channel ch,
                                  bladerf_frequency frequency)
{
    CHECK_BOARD_STATE(STATE_INITIALIZED);

    struct bladerf2_board_data *board_data = dev->board_data;

    return board_data->rfic->set_frequency(dev, ch, frequency);
}


...


struct board_fns const bladerf2_board_fns = {
    FIELD_INIT(.set_frequency, bladerf2_set_frequency)
}
```












**Third-layer: rfic_host.c**

https://github.com/Nuand/bladeRF/blob/master/host/libraries/libbladeRF/src/board/bladerf2/rfic_host.c

```c
/******************************************************************************/
/* Frequency */
/******************************************************************************/

static int _rfic_host_set_frequency(struct bladerf *dev,
                                    bladerf_channel ch,
                                    bladerf_frequency frequency)
{
    struct bladerf2_board_data *board_data = dev->board_data;
    struct ad9361_rf_phy *phy              = board_data->phy;
    struct controller_fns const *rfic      = board_data->rfic;
    struct bladerf_range const *range      = NULL;

    CHECK_STATUS(dev->board->get_frequency_range(dev, ch, &range));

    if (!is_within_range(range, frequency)) {
        return BLADERF_ERR_RANGE;
    }

    /* Set up band selection */
    CHECK_STATUS(rfic->select_band(dev, ch, frequency));

    /* Change LO frequency */
    if (BLADERF_CHANNEL_IS_TX(ch)) {
        CHECK_AD936X(ad9361_set_tx_lo_freq(phy, frequency));
    } else {
        CHECK_AD936X(ad9361_set_rx_lo_freq(phy, frequency));
    }

    return 0;
}


...


struct controller_fns const rfic_host_control = {
    FIELD_INIT(.set_frequency, _rfic_host_set_frequency),
}
```


The `CHECK_STATUS(_fn)` function is found in https://github.com/Nuand/bladeRF/blob/master/host/libraries/libbladeRF/src/board/bladerf2/common.h

```c
/**
 * @brief   Call a function and return early if it fails
 *
 * @param   _fn   The function
 *
 * @return  function return value if less than zero; continues otherwise
 */
#define CHECK_STATUS(_fn)                  \
    do {                                   \
        int _s = _fn;                      \
        if (_s < 0) {                      \
            RETURN_ERROR_STATUS(#_fn, _s); \
        }                                  \
    } while (0)
```


The `BLADERF_RFIC_COMMAND_FREQUENCY` can be found in memory address `0x04` per https://github.com/Nuand/bladeRF/blob/master/fpga_common/include/bladerf2_common.h line #231


The above will follow this logic:
1. use the CHECK_STATUS to see if we can `dev->board->get_frequency_range` from the device.. if not fail
2. If we get a range, check the frequency we're trying to set is in range.. if not fail
3. Setup band select via `rfic->select_band`
4. Determine if we are trying to set a TX or RX frequency based on channel provided
- This uses "ad9361_set_rx_lo_freq" function


**Next Stage: Select Band**

```c
static int _rfic_host_select_band(struct bladerf *dev,
                                  bladerf_channel ch,
                                  bladerf_frequency frequency)
{
    struct bladerf2_board_data *board_data = dev->board_data;
    struct ad9361_rf_phy *phy              = board_data->phy;
    uint32_t reg;
    size_t i;

    /* Read RFFE control register */
    CHECK_STATUS(dev->backend->rffe_control_read(dev, &reg));

    /* Modify the SPDT bits. */
    /* We have to do this for all the channels sharing the same LO. */
    for (i = 0; i < 2; ++i) {
        bladerf_channel bch = BLADERF_CHANNEL_IS_TX(ch) ? BLADERF_CHANNEL_TX(i)
                                                        : BLADERF_CHANNEL_RX(i);
        CHECK_STATUS(_modify_spdt_bits_by_freq(
            &reg, bch, _rffe_ch_enabled(reg, bch), frequency));
    }

    /* Write RFFE control register */
    CHECK_STATUS(dev->backend->rffe_control_write(dev, reg));

    /* Set AD9361 port */
    CHECK_STATUS(
        set_ad9361_port_by_freq(phy, ch, _rffe_ch_enabled(reg, ch), frequency));

    return 0;
}
```

In the select_band function above, we are using `rffe_control_read` to read the current value of the RFFE (Radio Frequency Front End) control register into the `reg` variable.

***Read RFFE control register***
This section we will CHECK_STATUS(dev->backend->rffe_control_read)

The `rffe_control_read` uses code found in https://github.com/Nuand/bladeRF/blob/master/host/libraries/libbladeRF/src/backend/usb/usb.c

On line 1391, we see a FIELD_INIT which maps `rffe_control_read` to `nios_rffe_control_read`

```c
int nios_rffe_control_read(struct bladerf *dev, uint32_t *value)
{
    int status;

    status = nios_8x32_read(dev, NIOS_PKT_8x32_TARGET_RFFE_CSR, 0, value);

#ifdef ENABLE_LIBBLADERF_NIOS_ACCESS_LOG_VERBOSE
    if (status == 0) {
        log_verbose("%s: Read 0x%08x\n", __FUNCTION__, *value);
    }
#endif

    return status;
}
```

As we see, this links us to another function `nios_8x32_read`
```c
static int nios_8x32_read(struct bladerf *dev, uint8_t id,
                          uint8_t addr, uint32_t *data)
{
    int status;
    uint8_t buf[NIOS_PKT_LEN];
    bool success;

    nios_pkt_8x32_pack(buf, id, false, addr, 0);

    status = nios_access(dev, buf);
    if (status != 0) {
        return status;
    }

    nios_pkt_8x32_resp_unpack(buf, NULL, NULL, NULL, data, &success);

    if (success) {
        return 0;
    } else {
        *data = 0;
        log_debug("%s: response packet reported failure.\n", __FUNCTION__);
        log_debug_8x32_pkt(buf);
        return BLADERF_ERR_FPGA_OP;
    }
}
```

Another mapping... this time to just `nios_access`. This time we get to the USB control transfer:
```c
/* Buf is assumed to be NIOS_PKT_LEN bytes */
static int nios_access(struct bladerf *dev, uint8_t *buf)
{
    struct bladerf_usb *usb = dev->backend_data;
    int status;

    print_buf("NIOS II REQ:", buf, NIOS_PKT_LEN);

    /* Send the command */
    status = usb->fn->bulk_transfer(usb->driver, PERIPHERAL_EP_OUT, buf,
                                    NIOS_PKT_LEN, PERIPHERAL_TIMEOUT_MS);
    if (status != 0) {
        log_error("Failed to send NIOS II request: %s\n",
                  bladerf_strerror(status));
        return status;
    }

    /* Retrieve the request */
    status = usb->fn->bulk_transfer(usb->driver, PERIPHERAL_EP_IN, buf,
                                    NIOS_PKT_LEN, PERIPHERAL_TIMEOUT_MS);
    if (status != 0) {
        log_error("Failed to receive NIOS II response: %s\n",
                  bladerf_strerror(status));
    }

    print_buf("NIOS II res:", buf, NIOS_PKT_LEN);

    return status;
}
```


Now that the RFFE control register is read, confirmed to be OK and stored into the `reg` variable... we need to modify the SPDT bits. This is still within the `_rfic_host_select_band` function


***Next Stage: Modify SPDT bits***

I was only able to find this function in the FPGA Common source code. From investigation, it just changes some bits around, so we'll need to implement a function in Java to do this for us.

```c
int _modify_spdt_bits_by_freq(uint32_t *reg,
                              bladerf_channel ch,
                              bool enabled,
                              bladerf_frequency freq)
{
    struct band_port_map const *port_map;
    uint32_t shift;

    if (NULL == reg) {
        return BLADERF_ERR_INVAL;
    }

    /* Look up the port configuration for this frequency */
    port_map = _get_band_port_map_by_freq(ch, enabled ? freq : 0);

    if (NULL == port_map) {
        return BLADERF_ERR_INVAL;
    }

    /* Modify the reg bits accordingly */
    switch (ch) {
        case BLADERF_CHANNEL_RX(0):
            shift = RFFE_CONTROL_RX_SPDT_1;
            break;
        case BLADERF_CHANNEL_RX(1):
            shift = RFFE_CONTROL_RX_SPDT_2;
            break;
        case BLADERF_CHANNEL_TX(0):
            shift = RFFE_CONTROL_TX_SPDT_1;
            break;
        case BLADERF_CHANNEL_TX(1):
            shift = RFFE_CONTROL_TX_SPDT_2;
            break;
        default:
            return BLADERF_ERR_INVAL;
    }

    *reg &= ~(RFFE_CONTROL_SPDT_MASK << shift);
    *reg |= port_map->spdt << shift;

    return 0;
}
```

Here is important constants to be able to re-implement this:
```c
// RFFE control
#define RFFE_CONTROL_RESET_N        0
#define RFFE_CONTROL_ENABLE         1
#define RFFE_CONTROL_TXNRX          2
#define RFFE_CONTROL_EN_AGC         3
#define RFFE_CONTROL_SYNC_IN        4
#define RFFE_CONTROL_RX_BIAS_EN     5
#define RFFE_CONTROL_RX_SPDT_1      6   // 6 and 7
#define RFFE_CONTROL_RX_SPDT_2      8   // 8 and 9
#define RFFE_CONTROL_TX_BIAS_EN     10
#define RFFE_CONTROL_TX_SPDT_1      11  // 11 and 12
#define RFFE_CONTROL_TX_SPDT_2      13  // 13 and 14
#define RFFE_CONTROL_MIMO_RX_EN_0   15
#define RFFE_CONTROL_MIMO_TX_EN_0   16
#define RFFE_CONTROL_MIMO_RX_EN_1   17
#define RFFE_CONTROL_MIMO_TX_EN_1   18
#define RFFE_CONTROL_ADF_MUXOUT     19   // input only
#define RFFE_CONTROL_CTRL_OUT       24   // input only, 24 through 31
#define RFFE_CONTROL_SPDT_MASK      0x3
#define RFFE_CONTROL_SPDT_SHUTDOWN  0x0  // no connection
#define RFFE_CONTROL_SPDT_LOWBAND   0x2  // RF1 <-> RF3
#define RFFE_CONTROL_SPDT_HIGHBAND  0x1  // RF1 <-> RF2
```

Here is some Java code that chatgpt generated to do something similar to what this function does:
```java
public class RffeControl {

    // Placeholder constants – you'll fill in the actual values
    public static final int RFFE_CONTROL_RX_SPDT_1 = 0;
    public static final int RFFE_CONTROL_RX_SPDT_2 = 2;
    public static final int RFFE_CONTROL_TX_SPDT_1 = 4;
    public static final int RFFE_CONTROL_TX_SPDT_2 = 6;

    public static final int RFFE_CONTROL_SPDT_MASK = 0x3; // example: 2-bit mask

    // Represents something like bladerf_channel (0 = RX0, 1 = RX1, 2 = TX0, 3 = TX1)
    public enum Channel {
        RX0, RX1, TX0, TX1
    }

    public static class BandPortMap {
        public int spdt;

        public BandPortMap(int spdt) {
            this.spdt = spdt;
        }
    }

    // Placeholder for frequency-to-port mapping function
    public static BandPortMap getBandPortMapByFreq(Channel ch, boolean enabled, long freq) {
        // Use 'freq' and 'ch' to determine proper spdt routing
        // For now, mock example
        if (!enabled) freq = 0;

        if (freq < 2200000000L) {
            return new BandPortMap(0x1); // Low band
        } else {
            return new BandPortMap(0x2); // High band
        }
    }

    public static int modifySpdtBitsByFreq(int reg, Channel ch, boolean enabled, long freq) throws IllegalArgumentException {
        BandPortMap portMap = getBandPortMapByFreq(ch, enabled, freq);
        if (portMap == null) {
            throw new IllegalArgumentException("Invalid frequency or channel");
        }

        int shift;
        switch (ch) {
            case RX0:
                shift = RFFE_CONTROL_RX_SPDT_1;
                break;
            case RX1:
                shift = RFFE_CONTROL_RX_SPDT_2;
                break;
            case TX0:
                shift = RFFE_CONTROL_TX_SPDT_1;
                break;
            case TX1:
                shift = RFFE_CONTROL_TX_SPDT_2;
                break;
            default:
                throw new IllegalArgumentException("Invalid channel");
        }

        // Clear existing SPDT bits
        reg &= ~(RFFE_CONTROL_SPDT_MASK << shift);

        // Set new bits from port map
        reg |= (portMap.spdt << shift);

        return reg;
    }
}
```

***Next Stage: Write RFFE control register***

Now that we have the register set as needed (based on frequency, this will set SDR board level switch to route RF through the correct channels), we need to write them to the board. This is already shown above, so we'll leave it out for now. Just need to use the correct values to end up at the `nios_access` function to write over USB





***Next Stage: ad9361 set frequency***

Still in the `select_band` function, we now need to set the frequency on the ad9361 RF board. This too is picked out from the firmware code, so we need to implement this as well. Here is the C function:

```c
int set_ad9361_port_by_freq(struct ad9361_rf_phy *phy,
                            bladerf_channel ch,
                            bool enabled,
                            bladerf_frequency freq)
{
    struct band_port_map const *port_map = NULL;
    int status;

    /* Look up the port configuration for this frequency */
    port_map = _get_band_port_map_by_freq(ch, enabled ? freq : 0);

    if (NULL == port_map) {
        return BLADERF_ERR_INVAL;
    }

    /* Set the AD9361 port accordingly */
    if (BLADERF_CHANNEL_IS_TX(ch)) {
        status = ad9361_set_tx_rf_port_output(phy, port_map->rfic_port);
    } else {
        status = ad9361_set_rx_rf_port_input(phy, port_map->rfic_port);
    }

    return errno_ad9361_to_bladerf(status);
}
```