/*
 *
 *  Copyright 2013 Netflix, Inc.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 */
package com.netflix.zeno.fastblob.record;

import com.netflix.zeno.fastblob.record.FastBlobSchema.FieldType;
import com.netflix.zeno.serializer.NFDeserializationRecord;

/**
 * Produces a set of offsets into the fields for a specific object.<p/>
 *
 * The schema tells us how to read the data encoded in the SegmentedByteArray.<p/>
 *
 * When we position to a specific object instance's offset, we use the schema to guide us to set the pointers for each field in that instance.
 *
 * @author dkoszewnik
 *
 */
public class FastBlobDeserializationRecord implements NFDeserializationRecord {

    private final FastBlobSchema schema;

    private final ByteData byteData;
    private final int fieldPointers[];

    public FastBlobDeserializationRecord(FastBlobSchema schema, ByteData byteData) {
        this.schema = schema;
        this.fieldPointers = new int[schema.numFields()];
        this.byteData = byteData;
    }

    /**
     * Position this record to the byte at index <code>objectBeginOffset</code>.
     *
     * @param objectBeginOffset
     * @return The length of the object's data, in bytes.
     */
    public int position(int objectBeginOffset) {
        int currentPosition = objectBeginOffset;

        for(int i=0;i<fieldPointers.length;i++) {
            fieldPointers[i] = currentPosition;

            FieldType type = schema.getFieldType(i);

            currentPosition += fieldLength(currentPosition, type);
        }

        return currentPosition - objectBeginOffset;
    }

    /**
     * Get the underlying byte data where this record is contained.
     */
    public ByteData getByteData() {
        return byteData;
    }

    /**
     * get the offset into the byte data for the field represented by the String.
     */
    public int getPosition(String fieldName) {
        int fieldPosition = schema.getPosition(fieldName);

        if(fieldPosition == -1)
            return -1;

        return fieldPointers[fieldPosition];
    }

    /**
     * get the length of the specified field for this record
     */
    public int getFieldLength(String fieldName) {
        int fieldPosition = schema.getPosition(fieldName);
        FieldType fieldType = schema.getFieldType(fieldPosition);

        return fieldLength(fieldPointers[fieldPosition], fieldType);
    }

    private int fieldLength(int currentPosition, FieldType type) {
        if(type.startsWithVarIntEncodedLength()) {
            if(VarInt.readVNull(byteData, currentPosition)) {
                return 1;
            } else {
                int fieldLength = VarInt.readVInt(byteData, currentPosition);
                return VarInt.sizeOfVInt(fieldLength) + fieldLength;
            }
        } else if(type.getFixedLength() != -1) {
            return type.getFixedLength();
        } else {
            if(VarInt.readVNull(byteData, currentPosition)) {
                return 1;
            } else {
                long value = VarInt.readVLong(byteData, currentPosition);
                return VarInt.sizeOfVLong(value);
            }
        }
    }

}