/*
 * Copyright (c) 2008-2024, Hazelcast, Inc. All Rights Reserved.
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

package com.hazelcast.client.impl.protocol.codec;

import com.hazelcast.client.impl.protocol.ClientMessage;
import com.hazelcast.client.impl.protocol.Generated;
import com.hazelcast.client.impl.protocol.codec.builtin.*;
import com.hazelcast.client.impl.protocol.codec.custom.*;

import javax.annotation.Nullable;

import static com.hazelcast.client.impl.protocol.ClientMessage.*;
import static com.hazelcast.client.impl.protocol.codec.builtin.FixedSizeTypesCodec.*;

/*
 * This file is auto-generated by the Hazelcast Client Protocol Code Generator.
 * To change this file, edit the templates or the protocol
 * definitions on the https://github.com/hazelcast/hazelcast-client-protocol
 * and regenerate it.
 */

/**
 * Returns a lazy Set view of the key contained in this map. A LazySet is optimized for querying speed
 * (preventing eager deserialization and hashing on HashSet insertion) and does NOT provide all operations.
 * Any kind of mutating function will throw an UNSUPPORTED_OPERATION. Same is true for operations
 * like java.util.Set#contains(Object) and java.util.Set#containsAll(java.util.Collection) which would result in
 * very poor performance if called repeatedly (for example, in a loop). If the use case is different from querying
 * the data, please copy the resulting set into a new java.util.HashSet.
 */
@SuppressWarnings("unused")
@Generated("913d6e1e92cae24a7c8c5ad0ef74d1da")
public final class ReplicatedMapKeySetCodec {
    //hex: 0x0D0F00
    public static final int REQUEST_MESSAGE_TYPE = 855808;
    //hex: 0x0D0F01
    public static final int RESPONSE_MESSAGE_TYPE = 855809;
    private static final int REQUEST_INITIAL_FRAME_SIZE = PARTITION_ID_FIELD_OFFSET + INT_SIZE_IN_BYTES;
    private static final int RESPONSE_INITIAL_FRAME_SIZE = RESPONSE_BACKUP_ACKS_FIELD_OFFSET + BYTE_SIZE_IN_BYTES;

    private ReplicatedMapKeySetCodec() {
    }

    public static ClientMessage encodeRequest(java.lang.String name) {
        ClientMessage clientMessage = ClientMessage.createForEncode();
        clientMessage.setRetryable(true);
        clientMessage.setOperationName("ReplicatedMap.KeySet");
        ClientMessage.Frame initialFrame = new ClientMessage.Frame(new byte[REQUEST_INITIAL_FRAME_SIZE], UNFRAGMENTED_MESSAGE);
        encodeInt(initialFrame.content, TYPE_FIELD_OFFSET, REQUEST_MESSAGE_TYPE);
        encodeInt(initialFrame.content, PARTITION_ID_FIELD_OFFSET, -1);
        clientMessage.add(initialFrame);
        StringCodec.encode(clientMessage, name);
        return clientMessage;
    }

    /**
     * Name of the ReplicatedMap
     */
    public static java.lang.String decodeRequest(ClientMessage clientMessage) {
        ClientMessage.ForwardFrameIterator iterator = clientMessage.frameIterator();
        //empty initial frame
        iterator.next();
        return StringCodec.decode(iterator);
    }

    public static ClientMessage encodeResponse(java.util.Collection<com.hazelcast.internal.serialization.Data> response) {
        ClientMessage clientMessage = ClientMessage.createForEncode();
        ClientMessage.Frame initialFrame = new ClientMessage.Frame(new byte[RESPONSE_INITIAL_FRAME_SIZE], UNFRAGMENTED_MESSAGE);
        encodeInt(initialFrame.content, TYPE_FIELD_OFFSET, RESPONSE_MESSAGE_TYPE);
        clientMessage.add(initialFrame);

        ListMultiFrameCodec.encode(clientMessage, response, DataCodec::encode);
        return clientMessage;
    }

    /**
     * A lazy set view of the keys contained in this map.
     */
    public static java.util.List<com.hazelcast.internal.serialization.Data> decodeResponse(ClientMessage clientMessage) {
        ClientMessage.ForwardFrameIterator iterator = clientMessage.frameIterator();
        //empty initial frame
        iterator.next();
        return ListMultiFrameCodec.decode(iterator, DataCodec::decode);
    }
}
