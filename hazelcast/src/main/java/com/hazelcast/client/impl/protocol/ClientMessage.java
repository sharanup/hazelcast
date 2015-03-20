/*
 * Copyright (c) 2008-2015, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.client.impl.protocol;

import com.hazelcast.client.impl.protocol.util.BitUtil;
import com.hazelcast.client.impl.protocol.util.Flyweight;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static java.nio.ByteOrder.LITTLE_ENDIAN;


/**
 * <pre>
 * 0                   1                   2                   3
 * 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |R|                      Frame Length                           |
 * +---------------------------------------------------------------+
 * |R|                     CorrelationId                           |
 * +-------------+---------------+---------------------------------+
 * |  Version    |B|E|  Flags    |               Type              |
 * +-------------+---------------+---------------------------------+
 * |        Data Offset          |                                 |
 * +-----------------------------+
 * |                       Message Payload Data                  ...
 *
 * </pre>
 */
public class ClientMessage extends Flyweight {

    public static final int FRAME_LENGTH_FIELD_OFFSET = 0;
    public static final int CORRELATION_ID_FIELD_OFFSET = FRAME_LENGTH_FIELD_OFFSET + BitUtil.SIZE_OF_INT;
    public static final int VERSION_FIELD_OFFSET = CORRELATION_ID_FIELD_OFFSET + BitUtil.SIZE_OF_INT;
    public static final int FLAGS_FIELD_OFFSET = VERSION_FIELD_OFFSET + BitUtil.SIZE_OF_BYTE;
    public static final int TYPE_FIELD_OFFSET = FLAGS_FIELD_OFFSET + BitUtil.SIZE_OF_BYTE;
    public static final int DATA_OFFSET_FIELD_OFFSET = TYPE_FIELD_OFFSET + BitUtil.SIZE_OF_SHORT;

    public static final int HEADER_SIZE = DATA_OFFSET_FIELD_OFFSET + BitUtil.SIZE_OF_SHORT;

    /** Begin Flag */
    public static final short BEGIN_FLAG = 0x80;

    /** End Flag */
    public static final short END_FLAG = 0x40;

    /** Begin and End Flags */
    public static final short BEGIN_AND_END_FLAGS = (short) (BEGIN_FLAG | END_FLAG);

    /**
     *  variable size data length field in bytes
     */
    public static final int SIZE_OF_LENGTH_FIELD = 4;

    public void wrapForEncode(final ByteBuffer buffer, final int offset) {
        super.wrap(buffer,offset);
        dataOffset(HEADER_SIZE);
        frameLength(dataOffset());
        dataPosition(dataOffset());
    }

    public void wrapForDecode(final ByteBuffer buffer, final int offset) {
        super.wrap(buffer,offset);
        dataPosition(dataOffset());
    }

    /**
     * return version field value
     *
     * @return ver field value
     */
    public short version()
    {
        return uint8Get(offset() + VERSION_FIELD_OFFSET);
    }

    /**
     * set version field value
     *
     * @param ver field value
     * @return ClientMessage
     */
    public ClientMessage version(final short ver)
    {
        uint8Put(offset() + VERSION_FIELD_OFFSET, ver);
        return this;
    }

    /**
     * return flags field value
     *
     * @return flags field value
     */
    public short flags()
    {
        return uint8Get(offset() + FLAGS_FIELD_OFFSET);
    }

    /**
     * set the flags field value
     *
     * @param flags field value
     * @return ClientMessage
     */
    public ClientMessage flags(final short flags)
    {
        uint8Put(offset() + FLAGS_FIELD_OFFSET, flags);
        return this;
    }

    /**
     * return header type field
     *
     * @return type field value
     */
    public int headerType()
    {
        return uint16Get(offset() + TYPE_FIELD_OFFSET, LITTLE_ENDIAN);
    }

    /**
     * set header type field
     *
     * @param type field value
     * @return ClientMessage
     */
    public ClientMessage headerType(final int type)
    {
        uint16Put(offset() + TYPE_FIELD_OFFSET, (short)type, LITTLE_ENDIAN);
        return this;
    }

    /**
     * return frame length field
     *
     * @return frame length field
     */
    public int frameLength()
    {
        return (int)uint32Get(offset() + FRAME_LENGTH_FIELD_OFFSET, LITTLE_ENDIAN);
    }

    /**
     * set frame length field
     *
     * @param length field value
     * @return ClientMessage
     */
    public ClientMessage frameLength(final int length)
    {
        uint32Put(offset() + FRAME_LENGTH_FIELD_OFFSET, length, LITTLE_ENDIAN);
        return this;
    }

    /**
     * return correlation id field
     *
     * @return correlation id field
     */
    public int correlationId()
    {
        return (int)uint32Get(offset() + CORRELATION_ID_FIELD_OFFSET, LITTLE_ENDIAN);
    }

    /**
     * set correlation id field
     *
     * @param correlationId field value
     * @return ClientMessage
     */
    public ClientMessage correlationId(final int correlationId) {
        uint32Put(offset() + CORRELATION_ID_FIELD_OFFSET, correlationId, ByteOrder.LITTLE_ENDIAN);
        return this;
    }

    /**
     * return dataOffset field
     *
     * @return type field value
     */
    public int dataOffset()
    {
        return uint16Get(offset() + DATA_OFFSET_FIELD_OFFSET, LITTLE_ENDIAN);
    }

    /**
     * set dataOffset field
     *
     * @param dataOffset field value
     * @return ClientMessage
     */
    public ClientMessage dataOffset(final int dataOffset)
    {
        uint16Put(offset() + DATA_OFFSET_FIELD_OFFSET, (short)dataOffset, LITTLE_ENDIAN);
        return this;
    }

    public byte[] getVarData()
    {
        final int dataPosition = dataPosition();
        final int dataLength = uint8Get(offset() + dataPosition);

        dataPosition(dataPosition + SIZE_OF_LENGTH_FIELD + dataLength);
        byte[] data = new byte[dataLength];
        buffer().getBytes(offset() + dataPosition + SIZE_OF_LENGTH_FIELD,data);
        return data;
    }

    public ClientMessage putVarData(byte[] data){
        if(data == null) {
            throw new NullPointerException("Data cannot be null");
        }
        final int length = data.length;
        final int dataPosition = dataPosition();
        dataPosition(dataPosition + SIZE_OF_LENGTH_FIELD + length);
        uint32Put(offset() + dataPosition,(long) length, LITTLE_ENDIAN);
        buffer().putBytes(offset() + dataPosition + SIZE_OF_LENGTH_FIELD ,data, 0, length);
        frameLength(frameLength() + SIZE_OF_LENGTH_FIELD + length);
        return this;
    }

}
