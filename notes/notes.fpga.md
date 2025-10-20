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

Below is the entire content of the `board.c` file. With the ID of the board (1 or 2), it will know which function pointers to use.

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












**Third-layer: rfic_fpga.c**

https://github.com/Nuand/bladeRF/blob/master/host/libraries/libbladeRF/src/board/bladerf2/rfic_fpga.c

```c
/******************************************************************************/
/* Frequency */
/******************************************************************************/

static int _rfic_fpga_set_frequency(struct bladerf *dev,
                                    bladerf_channel ch,
                                    bladerf_frequency frequency)
{
    struct bladerf_range const *range = NULL;

    CHECK_STATUS(dev->board->get_frequency_range(dev, ch, &range));

    if (!is_within_range(range, frequency)) {
        return BLADERF_ERR_RANGE;
    }

    return _rfic_cmd_write(dev, ch, BLADERF_RFIC_COMMAND_FREQUENCY, frequency);
}


...


struct controller_fns const rfic_fpga_control = {
    FIELD_INIT(.set_frequency, _rfic_fpga_set_frequency),
}
```

The `BLADERF_RFIC_COMMAND_FREQUENCY` can be found in memory address `0x04` per https://github.com/Nuand/bladeRF/blob/master/fpga_common/include/bladerf2_common.h line #231









**Lower-level: Command write to RFIC**

I don't know why we see `_rfic_cmd_write` function not only by itself, but also specifically for setting frequency as shown above. How would it know which one to use?? We see this function in these places in the `rfic_fpga.c` file:
    - by itself (line 51)
    - low level RFIC accessors (line 145)
    - Setting frequency (line 375)

https://github.com/Nuand/bladeRF/blob/master/host/libraries/libbladeRF/src/board/bladerf2/rfic_fpga.c

```c
static int _rfic_cmd_write(struct bladerf *dev,
                           bladerf_channel ch,
                           bladerf_rfic_command cmd,
                           uint64_t data);


...


#define RFIC_ADDRESS(cmd, ch) ((cmd & 0xFF) + ((ch & 0xF) << 8))


...


/******************************************************************************/
/* Low level RFIC Accessors */
/******************************************************************************/

static int _rfic_cmd_write(struct bladerf *dev,
                           bladerf_channel ch,
                           bladerf_rfic_command cmd,
                           uint64_t data)
{
    /* Perform the write command. */
    CHECK_STATUS(
        dev->backend->rfic_command_write(dev, RFIC_ADDRESS(cmd, ch), data));

    /* Block until the job has been completed. */
    return _rfic_fpga_spinwait(dev);
}


...


struct controller_fns const rfic_fpga_control = {
    FIELD_INIT(.set_frequency, _rfic_fpga_set_frequency)
}
```



This uses the device backend (like the FPGA logic) to send a command.

- It forms the address with **RFIC_ADDRESS(cmd, ch)**.
- Then it calls:

```c
dev->backend->rfic_command_write(dev, RFIC_ADDRESS(cmd, ch), data)
```

After writing, it calls **_rfic_fpga_spinwait** to wait until the FPGA completes the job.







**Lower-level: Getting to USB write**

In the above function pointer (yet again), we have `dev->backend->rfic_command_write` within the `_rfic_cmd_write` function. Looking around the codebase, I found a FIELD_INIT which maps `.rfic_command_write` to the associated USB function pointer for writing to memory register

https://github.com/Nuand/bladeRF/blob/master/host/libraries/libbladeRF/src/backend/usb/usb.c
```c


...


const struct backend_fns backend_fns_usb_legacy = {
    FIELD_INIT(.rfic_command_write, nios_legacy_rfic_command_write),
    ...
    FIELD_INIT(.rfic_command_write, nios_rfic_command_write),
}
```




**Very Low-level: FPGA Command Write**

https://github.com/Nuand/bladeRF/blob/master/host/libraries/libbladeRF/src/backend/usb/nios_access.h#L238
```c
int nios_rfic_command_write(struct bladerf *dev, uint16_t cmd, uint64_t data)
```

https://github.com/Nuand/bladeRF/blob/master/host/libraries/libbladeRF/src/backend/usb/nios_access.c
```c
int nios_rfic_command_read(struct bladerf *dev, uint16_t cmd, uint64_t *data)
{
    int status;

    status = nios_16x64_read(dev, NIOS_PKT_16x64_TARGET_RFIC, cmd, data);

#ifdef ENABLE_LIBBLADERF_NIOS_ACCESS_LOG_VERBOSE
    if (status == 0) {
        log_verbose("%s: Read 0x%04x 0x%08x\n", __FUNCTION__, cmd, *data);
    }
#endif

    return status;
}
```

- Uses **nios_16x64_write(...)** to do a write over a 16x64 register bus.
- If verbose logging is enabled, it logs the write.
- Returns status.

✅ **Summary**: Final point before the hardware write. This talks to the FPGA logic (Nios II soft processor, typically).






**Very very Low-leve: nios_16x64_write**

https://github.com/Nuand/bladeRF/blob/master/host/libraries/libbladeRF/src/backend/usb/nios_access.c#L319
```c
static int nios_16x64_write(struct bladerf *dev,
                            uint8_t id,
                            uint16_t addr,
                            uint64_t data)
{
    int status;
    uint8_t buf[NIOS_PKT_LEN];
    bool success;

    nios_pkt_16x64_pack(buf, id, true, addr, data);

    /* RFIC access times out occasionally, and this is fine. */
    if (NIOS_PKT_16x64_TARGET_RFIC == id) {
        status = nios_access_quiet(dev, buf);
    } else {
        status = nios_access(dev, buf);
    }

    if (status != 0) {
        return status;
    }

    nios_pkt_16x64_resp_unpack(buf, NULL, NULL, NULL, NULL, &success);

    if (success) {
        return 0;
    } else {
        log_debug("%s: response packet reported failure.\n", __FUNCTION__);
        return BLADERF_ERR_FPGA_OP;
    }
}
```

And finally, we have arrived to the USB control transfers

```c
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

/* Variant that doesn't output to log_error on error. */
static int nios_access_quiet(struct bladerf *dev, uint8_t *buf)
{
    struct bladerf_usb *usb = dev->backend_data;
    int status;

    print_buf("NIOS II REQ:", buf, NIOS_PKT_LEN);

    /* Send the command */
    status = usb->fn->bulk_transfer(usb->driver, PERIPHERAL_EP_OUT, buf,
                                    NIOS_PKT_LEN, PERIPHERAL_TIMEOUT_MS);
    if (status != 0) {
        return status;
    }

    /* Retrieve the request */
    status = usb->fn->bulk_transfer(usb->driver, PERIPHERAL_EP_IN, buf,
                                    NIOS_PKT_LEN, PERIPHERAL_TIMEOUT_MS);

    print_buf("NIOS II res:", buf, NIOS_PKT_LEN);

    return status;
}
```