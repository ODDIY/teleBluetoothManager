package de.platfrom.tele.blueconn;
//https://github.com/themarpe/cobs-java

//with some correction from o.walter

class Cobs {

    static int encodeDstBufMaxLen(int srcLen){
        return ((srcLen) + (((srcLen) + 253)/254));
    }

    static int decodeDstBufMaxLen(int srcLen){
        return (((srcLen) == 0) ? 0 : ((srcLen) - 1));
    }


    enum EncodeStatus {
        OK,
        NULL_POINTER,
        OUT_BUFFER_OVERFLOW
    }

    static class EncodeResult {
        int inLen;
        int outLen;
        EncodeStatus status;
    }

    enum DecodeStatus {
        OK,
        NULL_POINTER,
        OUT_BUFFER_OVERFLOW,
        ZERO_BYTE_IN_INPUT,
        INPUT_TOO_SHORT
    }

    static class DecodeResult {
        int outLen;
        int inLen;
        DecodeStatus status;
    }

    static EncodeResult encode(byte[] dst_buf_ptr, int dst_buf_len, byte[] src_ptr, int src_len){
        EncodeResult result = new EncodeResult();
        result.outLen = 0;
        result.status = EncodeStatus.OK;

        int dst_write_counter = 1;
        int dst_code_write_counter = 0;

        int src_ptr_counter = 0;

        int search_len = 1;

        if(dst_buf_ptr == null || src_ptr == null){
            result.status = EncodeStatus.NULL_POINTER;
            return result;
        }


        if (src_len != 0)
        {
            /* Iterate over the source bytes */
            for (;;)
            {
                /* Check for running out of output buffer space */
                if (dst_write_counter >= dst_buf_len)
                {
                    result.status = EncodeStatus.OUT_BUFFER_OVERFLOW;
                    break;
                }

                int src_byte = src_ptr[src_ptr_counter++];

                if (src_byte == 0)
                {
                    /* We found a zero byte */
                    dst_buf_ptr[dst_code_write_counter] = (byte) (search_len & 0xFF);
                    dst_code_write_counter = dst_write_counter++;
                    search_len = 1;
                    if (src_ptr_counter >= src_len)
                    {
                        break;
                    }
                }
                else
                {
                    /* Copy the non-zero byte to the destination buffer */
                    dst_buf_ptr[dst_write_counter++] = (byte) (src_byte & 0xFF);

                    search_len++;
                    if (src_ptr_counter >= src_len)
                    {
                        break;
                    }
                    if (search_len == 0xFF)
                    {
                        /* We have a long string of non-zero bytes, so we need
                         * to write out a length code of 0xFF. */
                        dst_buf_ptr[dst_code_write_counter] = (byte) (search_len & 0xFF);

                        dst_code_write_counter = dst_write_counter++;
                        search_len = 1;
                    }
                }
            }
        }

        /* We've reached the end of the source data (or possibly run out of output buffer)
         * Finalise the remaining output. In particular, write the code (length) byte.
         * Update the pointer to calculate the final output length.
         */
        if (dst_code_write_counter >= dst_buf_len)
        {
            /* We've run out of output buffer to write the code byte. */
            result.status = EncodeStatus.OUT_BUFFER_OVERFLOW;
            dst_write_counter = dst_buf_len;
        }
        else
        {
            /* Write the last code (length) byte. */
            dst_buf_ptr[dst_code_write_counter] = (byte) (search_len & 0xFF);
        }

        /* Calculate the output length, from the value of dst_code_write_ptr */
        result.outLen = dst_write_counter;
        result.inLen = src_ptr_counter;
        return result;
    }



    static DecodeResult decode(byte[] dst_buf_ptr, int dst_buf_len, byte[] src_ptr, int src_len){
        DecodeResult result = new DecodeResult();
        result.outLen = 0;
        result.status = DecodeStatus.OK;

        int src_ptr_counter = 0;
        int dst_write_counter = 0;
        int remaining_bytes;
        int src_byte;
        int i;
        int len_code;



        /* First, do a NULL pointer check and return immediately if it fails. */
        if ((dst_buf_ptr == null) || (src_ptr == null))
        {
            result.status = DecodeStatus.NULL_POINTER;
            return result;
        }

        if (src_len != 0)
        {
            for (;;)
            {
                len_code = src_ptr[src_ptr_counter++];
                if (len_code == 0)
                {

                    if(src_len < 2) {
                        result.status = DecodeStatus.ZERO_BYTE_IN_INPUT;
                    } else {
                        //at the end 00 is ok
                        result.status = DecodeStatus.OK;
                        dst_write_counter--;
                    }


                    break;
                }
                len_code--;

                /* Check length code against remaining input bytes */
                remaining_bytes = src_len - src_ptr_counter;
                if (len_code > remaining_bytes)
                {
                    result.status = DecodeStatus.INPUT_TOO_SHORT;
                    len_code = remaining_bytes;
                }

                /* Check length code against remaining output buffer space */
                remaining_bytes = dst_buf_len - dst_write_counter;
                if (len_code > remaining_bytes)
                {
                    result.status = DecodeStatus.OUT_BUFFER_OVERFLOW;
                    len_code = remaining_bytes;
                }

                for (i = len_code; i != 0; i--)
                {
                    src_byte = (char) (src_ptr[src_ptr_counter++] & 0xFF);
                    if (src_byte == 0)
                    {
                        result.status = DecodeStatus.ZERO_BYTE_IN_INPUT;
                    }

                    dst_buf_ptr[dst_write_counter++] = (byte) src_byte;
                }

                if (src_ptr_counter >= src_len)
                {
                    break;
                }

                /* Add a zero to the end */
                if (len_code != 0xFE)
                {
                    if (dst_write_counter >= dst_buf_len)
                    {
                        result.status = DecodeStatus.OUT_BUFFER_OVERFLOW;
                        break;
                    }
                    dst_buf_ptr[dst_write_counter++] = 0;
                }
            }
        }

        result.outLen = dst_write_counter;
        result.inLen = src_ptr_counter;
        return result;
    }
}