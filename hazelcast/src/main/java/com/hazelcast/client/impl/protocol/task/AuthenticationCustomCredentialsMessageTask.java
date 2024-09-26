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

package com.hazelcast.client.impl.protocol.task;

import com.hazelcast.client.impl.TpcToken;
import com.hazelcast.client.impl.protocol.ClientMessage;
import com.hazelcast.client.impl.protocol.codec.ClientAuthenticationCustomCodec;
import com.hazelcast.instance.impl.Node;
import com.hazelcast.cluster.Address;
import com.hazelcast.internal.cluster.MemberInfo;
import com.hazelcast.internal.nio.Connection;
import com.hazelcast.security.SimpleTokenCredentials;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Custom Authentication with custom credential impl
 */
public class AuthenticationCustomCredentialsMessageTask
        extends AuthenticationBaseMessageTask<ClientAuthenticationCustomCodec.RequestParameters> {

    public AuthenticationCustomCredentialsMessageTask(ClientMessage clientMessage, Node node, Connection connection) {
        super(clientMessage, node, connection);
    }

    @Override
    @SuppressWarnings("checkstyle:npathcomplexity")
    protected ClientAuthenticationCustomCodec.RequestParameters decodeClientMessage(ClientMessage clientMessage) {
        ClientAuthenticationCustomCodec.RequestParameters parameters
                = ClientAuthenticationCustomCodec.decodeRequest(clientMessage);
        assert parameters.uuid != null;
        clientUuid = parameters.uuid;
        clusterName = parameters.clusterName;
        credentials = new SimpleTokenCredentials(parameters.credentials);
        clientSerializationVersion = parameters.serializationVersion;
        clientVersion = parameters.clientHazelcastVersion;
        clientName = parameters.clientName;
        labels = Collections.unmodifiableSet(new HashSet<>(parameters.labels));
        routingMode = parameters.isRoutingModeExists ? parameters.routingMode : -1;
        cpDirectToLeaderRouting = parameters.isCpDirectToLeaderRoutingExists && parameters.cpDirectToLeaderRouting;
        return parameters;
    }

    @Override
    @SuppressWarnings("checkstyle:ParameterNumber")
    protected ClientMessage encodeAuthenticationResponse(byte status, Address thisAddress, UUID uuid, byte serializationVersion,
                                                         String serverVersion, int partitionCount, UUID clusterId,
                                                         boolean clientFailoverSupported, List<Integer> tpcPorts, byte[] tpcToken,
                                                         int memberListVersion, List<MemberInfo> members,
                                                         int partitionsVersion, List<Map.Entry<UUID, List<Integer>>> partitions,
                                                         Map<String, String> keyValuePairs) {
        return ClientAuthenticationCustomCodec.encodeResponse(status, thisAddress, uuid, serializationVersion,
                serverVersion, partitionCount, clusterId, clientFailoverSupported, tpcPorts, tpcToken,
                memberListVersion, members, partitionsVersion, partitions, keyValuePairs);
    }

    @Override
    protected void setTpcTokenToEndpoint() {
        if (!nodeEngine.getTpcServerBootstrap().isEnabled()) {
            return;
        }

        TpcToken token = new TpcToken();
        endpoint.setTpcToken(token);
    }

    @Override
    protected String getClientType() {
        return parameters.clientType;
    }

}
