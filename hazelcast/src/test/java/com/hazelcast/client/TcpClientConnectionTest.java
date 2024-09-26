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

package com.hazelcast.client;

import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.ClientNetworkConfig;
import com.hazelcast.client.impl.clientside.ClientTestUtil;
import com.hazelcast.client.impl.clientside.HazelcastClientInstanceImpl;
import com.hazelcast.client.impl.connection.ClientConnection;
import com.hazelcast.client.impl.connection.ClientConnectionManager;
import com.hazelcast.client.impl.connection.tcp.RoutingMode;
import com.hazelcast.client.properties.ClientProperty;
import com.hazelcast.client.test.ClientTestSupport;
import com.hazelcast.client.test.TestHazelcastFactory;
import com.hazelcast.client.util.ConfigRoutingUtil;
import com.hazelcast.cluster.Address;
import com.hazelcast.cluster.Member;
import com.hazelcast.config.Config;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IExecutorService;
import com.hazelcast.internal.nio.Connection;
import com.hazelcast.internal.nio.ConnectionListener;
import com.hazelcast.test.HazelcastParallelParametersRunnerFactory;
import com.hazelcast.test.HazelcastParametrizedRunner;
import com.hazelcast.test.annotation.ParallelJVMTest;
import com.hazelcast.test.annotation.QuickTest;
import org.junit.After;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;


import static com.hazelcast.client.impl.connection.tcp.RoutingMode.ALL_MEMBERS;
import static com.hazelcast.client.impl.connection.tcp.RoutingMode.SINGLE_MEMBER;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(HazelcastParametrizedRunner.class)
@Parameterized.UseParametersRunnerFactory(HazelcastParallelParametersRunnerFactory.class)
@Category({QuickTest.class, ParallelJVMTest.class})
public class TcpClientConnectionTest extends ClientTestSupport {

    @Parameterized.Parameter
    public RoutingMode routingMode;

    @Parameterized.Parameters(name = "{index}: routingMode={0}")
    public static Iterable<?> parameters() {
        return Arrays.asList(SINGLE_MEMBER, RoutingMode.ALL_MEMBERS);
    }

    protected final TestHazelcastFactory hazelcastFactory = new TestHazelcastFactory();

    @After
    public void cleanup() {
        hazelcastFactory.terminateAll();
    }

    @Test
    public void testWithIllegalAddress() {
        String illegalAddress = randomString();

        hazelcastFactory.newHazelcastInstance();
        ClientConfig config = newClientConfig();
        config.getConnectionStrategyConfig().getConnectionRetryConfig().setClusterConnectTimeoutMillis(2000);
        config.getNetworkConfig().addAddress(illegalAddress);
        assertThrows(IllegalStateException.class, () -> HazelcastClient.newHazelcastClient(config));
    }

    @Test
    public void testEmptyStringAsAddress() {
        ClientNetworkConfig networkConfig = newClientConfig().getNetworkConfig();
        assertThrows(IllegalArgumentException.class, () -> networkConfig.addAddress(""));
    }

    @Test
    public void testNullAsAddress() {
        ClientNetworkConfig networkConfig = newClientConfig().getNetworkConfig();
        assertThrows(IllegalArgumentException.class, () -> networkConfig.addAddress((String[]) null));
    }

    @Test
    public void testNullAsAddresses() {
        ClientNetworkConfig networkConfig = newClientConfig().getNetworkConfig();
        assertThrows(IllegalArgumentException.class, () -> networkConfig.addAddress(null, null));
    }

    @Test
    public void testWithLegalAndIllegalAddressTogether() {
        String illegalAddress = randomString();

        HazelcastInstance server = hazelcastFactory.newHazelcastInstance();
        Address serverAddress = server.getCluster().getLocalMember().getAddress();
        ClientConfig config = newClientConfig();
        config.setProperty(ClientProperty.SHUFFLE_MEMBER_LIST.getName(), "false");
        config.getNetworkConfig()
                .addAddress(illegalAddress)
                .addAddress(serverAddress.getHost() + ":" + serverAddress.getPort());
        HazelcastInstance client = hazelcastFactory.newHazelcastClient(config);

        Collection<Client> connectedClients = server.getClientService().getConnectedClients();
        assertEquals(1, connectedClients.size());

        Client serverSideClientInfo = connectedClients.iterator().next();
        assertEquals(serverSideClientInfo.getUuid(), client.getLocalEndpoint().getUuid());
    }

    @Test
    public void testMemberConnectionOrder() {
        HazelcastInstance server1 = hazelcastFactory.newHazelcastInstance();
        HazelcastInstance server2 = hazelcastFactory.newHazelcastInstance();

        ClientConfig config = newClientConfig();
        config.setProperty(ClientProperty.SHUFFLE_MEMBER_LIST.getName(), "false");
        config.getNetworkConfig().getClusterRoutingConfig().setRoutingMode(RoutingMode.SINGLE_MEMBER);

        Address address1 = server1.getCluster().getLocalMember().getAddress();
        Address address2 = server2.getCluster().getLocalMember().getAddress();

        config.getNetworkConfig().
                addAddress(address1.getHost() + ":" + address1.getPort()).
                addAddress(address2.getHost() + ":" + address2.getPort());

        hazelcastFactory.newHazelcastClient(config);

        Collection<Client> connectedClients1 = server1.getClientService().getConnectedClients();
        assertEquals(1, connectedClients1.size());

        Collection<Client> connectedClients2 = server2.getClientService().getConnectedClients();
        assertEquals(0, connectedClients2.size());
    }

    @Test
    public void destroyConnection_whenDestroyedMultipleTimes_thenListenerRemoveCalledOnce() {
        HazelcastInstance server = hazelcastFactory.newHazelcastInstance();
        ClientConfig clientConfig = newClientConfig();
        HazelcastInstance client = hazelcastFactory.newHazelcastClient(clientConfig);
        HazelcastClientInstanceImpl clientImpl = ClientTestUtil.getHazelcastClientInstanceImpl(client);
        ClientConnectionManager connectionManager = clientImpl.getConnectionManager();

        final CountingConnectionListener listener = new CountingConnectionListener();

        connectionManager.addConnectionListener(listener);

        UUID serverUuid = server.getCluster().getLocalMember().getUuid();
        final Connection connectionToServer = connectionManager.getActiveConnection(serverUuid);

        ReconnectListener reconnectListener = new ReconnectListener();
        clientImpl.getLifecycleService().addLifecycleListener(reconnectListener);

        connectionToServer.close(null, null);
        assertOpenEventually(reconnectListener.reconnectedLatch);

        connectionToServer.close(null, null);
        assertEqualsEventually(listener.connectionRemovedCount::get, 1);
        sleepMillis(100);
        assertEquals("connection removed should be called only once", 1, listener.connectionRemovedCount.get());
    }

    private static class CountingConnectionListener implements ConnectionListener<ClientConnection> {

        final AtomicInteger connectionRemovedCount = new AtomicInteger();
        final AtomicInteger connectionAddedCount = new AtomicInteger();

        @Override
        public void connectionAdded(ClientConnection connection) {
            connectionAddedCount.incrementAndGet();
        }

        @Override
        public void connectionRemoved(ClientConnection connection) {
            connectionRemovedCount.incrementAndGet();
        }
    }

    @Test
    public void testAsyncConnectionCreationInAsyncMethods() throws InterruptedException {
        hazelcastFactory.newHazelcastInstance();
        ClientConfig config = newClientConfig();
        final HazelcastInstance client = hazelcastFactory.newHazelcastClient(config);
        final IExecutorService executorService = client.getExecutorService(randomString());

        final HazelcastInstance secondInstance = hazelcastFactory.newHazelcastInstance();

        assertTrueEventually(() -> assertEquals(2, client.getCluster().getMembers().size()));

        final AtomicReference<Future<Object>> atomicReference = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            Member secondMember = secondInstance.getCluster().getLocalMember();
            Future<Object> future = executorService.submitToMember(new DummySerializableCallable(), secondMember);
            atomicReference.set(future);
        });
        thread.start();
        try {
            assertTrueEventually(() -> assertNotNull(atomicReference.get()), 30);
        } finally {
            thread.interrupt();
            thread.join();
        }
    }

    @Test
    public void testAddingConnectionListenerTwice_shouldCauseEventDeliveredTwice() {
        hazelcastFactory.newHazelcastInstance();
        ClientConfig clientConfig = newClientConfig();
        HazelcastInstance client = hazelcastFactory.newHazelcastClient(clientConfig);

        HazelcastClientInstanceImpl clientImpl = ClientTestUtil.getHazelcastClientInstanceImpl(client);
        ClientConnectionManager connectionManager = clientImpl.getConnectionManager();

        // Block until client has connected to created HZ instance
        assertClusterSizeEventually(1, client);

        final CountingConnectionListener listener = new CountingConnectionListener();

        connectionManager.addConnectionListener(listener);
        connectionManager.addConnectionListener(listener);

        // Create a new HZ instance
        hazelcastFactory.newHazelcastInstance();

        assertTrueEventually(() -> {
            // The client should connect to new HZ instance if routingMode is ALL_MEMBERS
            // The client should "not" connect to new HZ instance if routingMode is SINGLE_MEMBER or MULTI_MEMBER
            int expectedConnectionCount = routingMode == ALL_MEMBERS ? 2 : 0;
            assertEquals(expectedConnectionCount, listener.connectionAddedCount.get());
        });
    }

    @Test
    public void testClientOpenClusterToAllEventually() {
        int memberCount = 4;
        for (int i = 0; i < memberCount; i++) {
            hazelcastFactory.newHazelcastInstance();
        }

        ClientConfig clientConfig = newClientConfig();
        HazelcastInstance client = hazelcastFactory.newHazelcastClient(clientConfig);

        int expectedConnectionCount = routingMode == ALL_MEMBERS ? memberCount : 1;
        makeSureConnectedToServers(client, expectedConnectionCount);
    }

    @Test
    public void testClientOpenClusterToAllEventually_onAsyncMode() {
        int memberCount = 4;
        for (int i = 0; i < memberCount; i++) {
            hazelcastFactory.newHazelcastInstance();
        }

        ClientConfig clientConfig = newClientConfig();
        clientConfig.getConnectionStrategyConfig().setAsyncStart(true);
        HazelcastInstance client = hazelcastFactory.newHazelcastClient(clientConfig);

        int expectedConnectionCount = routingMode == ALL_MEMBERS ? memberCount : 1;
        makeSureConnectedToServers(client, expectedConnectionCount);
    }

    @Test
    public void testAuthentication_when_clusterName_isSameAsClient_whenSkipClusterNameDefault() {
        // if the client is able to connect, it's a pass
        createClientAndServer("Test", "Test", false);
    }

    @Test
    public void testAuthentication_when_clusterName_isSameAsClient_whenSkipClusterNameTrue() {
        // if the client is able to connect, it's a pass
        createClientAndServer("Test", "Test", true);
    }

    @Test
    public void testAuthentication_when_clusterName_isNotSetInMemberOrClient_whenSkipClusterNameDefault() {
        // if the client is able to connect, it's a pass
        createClientAndServer(null, null, false);
    }

    @Test
    public void testAuthentication_when_clusterName_isNotSetInMemberOrClient_whenSkipClusterNameTrue() {
        // if the client is able to connect, it's a pass
        createClientAndServer(null, null, true);
    }

    @Test
    public void testAuthentication_when_clusterName_isNotSetOnMemberAndSetInClient_whenSkipClusterNameDefault() {
        // if the client is able to connect, it's a fail
        assertThatThrownBy(() -> createClientAndServer(null, "Test", false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unable to connect to any cluster.");
    }

    @Test
    public void testAuthentication_when_clusterName_isNotSetOnMemberAndSetInClient_whenSkipClusterNameTrue() {
        // if the client is able to connect, it's a pass
        createClientAndServer(null, "Test", true);
    }

    @Test
    public void testAuthentication_when_clusterName_isDifferentToClient_whenSkipClusterNameDefault() {
        // if the client is able to connect, it's a fail
        assertThatThrownBy(() -> createClientAndServer("Dev", "Test", false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unable to connect to any cluster.");
    }

    @Test
    public void testAuthentication_when_clusterName_isDifferentToClient_whenSkipClusterNameTrue() {
        // if the client is able to connect, it's a pass
        createClientAndServer("Dev", "Test", true);
    }

    private void createClientAndServer(String serverClusterName, String clientClusterName, boolean skipNameChecks) {
        Config config = smallInstanceConfigWithoutJetAndMetrics();
        if (serverClusterName != null) {
            config.setClusterName(serverClusterName);
        }
        if (skipNameChecks) {
            config.setProperty("hazelcast.client.internal.skip.cluster.namecheck.during.connection", "true");
        }
        hazelcastFactory.newHazelcastInstance(config);

        ClientConfig clientConfig = new ClientConfig();
        if (clientClusterName != null) {
            clientConfig.setClusterName(clientClusterName);
        }
        clientConfig.getConnectionStrategyConfig().getConnectionRetryConfig().setClusterConnectTimeoutMillis(0);
        hazelcastFactory.newHazelcastClient(clientConfig);
    }

    private static class DummySerializableCallable implements Callable<Object>, Serializable {

        @Override
        public Object call() {
            return null;
        }
    }

    protected ClientConfig newClientConfig() {
        ClientConfig clientConfig = ConfigRoutingUtil.newClientConfig(routingMode);
        clientConfig.getConnectionStrategyConfig().getConnectionRetryConfig().setClusterConnectTimeoutMillis(Long.MAX_VALUE);
        return clientConfig;
    }
}
