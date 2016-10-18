/*
 * Copyright 2016-present Open Networking Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onosproject;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableSet;
import org.onlab.osgi.ServiceDirectory;
import org.onlab.packet.IpAddress;
import org.onosproject.cluster.ClusterEvent;
import org.onosproject.cluster.ControllerNode;
import org.onosproject.cluster.DefaultControllerNode;
import org.onosproject.cluster.NodeId;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DefaultAnnotations;
import org.onosproject.net.DefaultDevice;
import org.onosproject.net.DefaultLink;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.HostId;
import org.onosproject.net.Link;
import org.onosproject.net.PortNumber;
import org.onosproject.net.device.DeviceEvent;
import org.onosproject.net.device.DeviceListener;
import org.onosproject.net.link.LinkEvent;
import org.onosproject.net.link.LinkListener;
import org.onosproject.net.provider.ProviderId;
//import org.onosproject.tetopology.management.api.InternalTeNetwork;
//import org.onosproject.tetopology.management.api.KeyId;
//import org.onosproject.tetopology.management.api.Network;
//import org.onosproject.tetopology.management.api.TeTopologyType;
//import org.onosproject.tetopology.management.api.link.NetworkLink;
//import org.onosproject.tetopology.management.api.node.ConnectivityMatrix;
//import org.onosproject.tetopology.management.api.node.DefaultNetworkNode;
//import org.onosproject.tetopology.management.api.node.NetworkNode;
//import org.onosproject.tetopology.management.api.node.NetworkNodeKey;
//import org.onosproject.tetopology.management.api.node.TeNode;
//import org.onosproject.tetopology.management.api.node.TerminationPointKey;
import org.onosproject.ui.JsonUtils;
import org.onosproject.ui.RequestHandler;
import org.onosproject.ui.UiConnection;
import org.onosproject.ui.topo.PropertyPanel;

import java.util.Collection;
//import java.util.List;
import java.util.concurrent.ExecutorService;

import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static org.onlab.util.Tools.groupedThreads;
import static org.onosproject.cluster.ClusterEvent.Type.INSTANCE_ADDED;
import static org.onosproject.net.DeviceId.deviceId;
import static org.onosproject.net.HostId.hostId;
import static org.onosproject.net.device.DeviceEvent.Type.DEVICE_ADDED;
import static org.onosproject.net.device.DeviceEvent.Type.PORT_STATS_UPDATED;
import static org.onosproject.net.link.LinkEvent.Type.LINK_ADDED;
import static org.onosproject.ui.JsonUtils.envelope;
import static org.onosproject.ui.topo.TopoJson.json;

/**
 * Skeletal ONOS UI Custom-View message handler.
 */
public class TeTopoUiMessageHandler extends TeTopoUiMessageHandlerBase {

//    private static final String SAMPLE_CUSTOM_DATA_REQ = "sampleCustomDataRequest";
//    private static final String SAMPLE_CUSTOM_DATA_RESP = "sampleCustomDataResponse";

    // == Topo param ====

    // incoming event types
    private static final String REQ_DETAILS = "requestDetails";
    private static final String REQ_SUMMARY = "requestSummary";

    private static final String TOPO_START = "meowTopoStart";
    // private static final String TOPO_HEARTBEAT = "topoHeartbeat";
    private static final String TOPO_STOP = "meowTopoStop";

    // outgoing event types

    private static final String SHOW_DETAILS = "showDetails";
    private static final String TOPO_START_DONE = "meowTopoStartDone";
    private static final String TOPO_VIS_HIDDEN = "visHidden";


    // fields
    private static final String ID = "id";
    private static final String DEVICE = "device";
    private static final String HOST = "host";
    private static final String CLASS = "class";
    private static final String UNKNOWN = "unknown";

    private final ExecutorService msgSender =
            newSingleThreadExecutor(groupedThreads("onos/gui", "msg-sender", log));


    private final DeviceListener deviceListener = new InternalDeviceListener();
    private final LinkListener linkListener = new InternalLinkListener();

    private volatile boolean listenersRemoved = false;

    // == TE Topology param ===
    private static final String TETOPO_NATIVE = "NATIVE";
    private static final String TETOPO_SUBORDINATE = "SUBORDINATE";

    // == End Topo param ====


    @Override
    public void init(UiConnection connection, ServiceDirectory directory) {
        super.init(connection, directory);
//        appId = directory.get(CoreService.class).registerApplication(MY_APP_ID);
//        traffic = new TrafficMonitor(TRAFFIC_PERIOD, servicesBundle, this);
    }

    @Override
    public void destroy() {
        cancelAllRequests();
        removeListeners();
        super.destroy();
    }

    @Override
    protected Collection<RequestHandler> createRequestHandlers() {
        return ImmutableSet.of(
//                new SampleCustomDataRequestHandler(),
                // Topo handlers
                new TopoStart(),
                // new TopoHeartbeat(),
                new TopoStop(),
                new RequestDetails()
        );
    }


    // == TE Topo Event ============================================


    private final class RequestDetails extends RequestHandler {
        private RequestDetails() {
            super(REQ_DETAILS);
        }

        @Override
        public void process(long sid, ObjectNode payload) {
            String type = string(payload, CLASS, UNKNOWN);
            String id = string(payload, ID);
            PropertyPanel pp = null;

            if (type.equals(DEVICE)) {
                DeviceId did = deviceId(id);

                // TODO: will delete, test detail info,
                if (did.toString().equals("demo01")
                        || did.toString().equals("demo02")) {
                    pp = demoDetails(did.toString(), (did.toString() + "Device"));
                } else {
                    pp = deviceDetails(did, sid);
                }
//                overlayCache.currentOverlay().modifyDeviceDetails(pp, did);

            } else if (type.equals(HOST)) {
                HostId hid = hostId(id);
                pp = hostDetails(hid, sid);
//                overlayCache.currentOverlay().modifyHostDetails(pp, hid);
            }

            sendMessage(envelope(SHOW_DETAILS, sid, json(pp)));
        }
    }

    private final class TopoStart extends RequestHandler {
        private TopoStart() {
            super(TOPO_START);
        }

        @Override
        public void process(long sid, ObjectNode payload) {
            addListeners();
            sendAllInstances(null);

            // normal topo
            sendAllDevices();
            sendAllLinks();

            // hidden offline device
//            sendTopoVisHidden();

            // Te topo: gui view init
//            sendTeTopology();
//            sendAllHosts();
            sendTopoStartDone();
        }
    }

    private final class TopoStop extends RequestHandler {
        private TopoStop() {
            super(TOPO_STOP);
        }

        @Override
        public void process(long sid, ObjectNode payload) {
            removeListeners();
            // stopSummaryMonitoring();
            // traffic.stopMonitoring();
        }
    }


    //== Meow Topo Function =====================================================================


    private void cancelAllRequests() {
//        stopSummaryMonitoring();
//        traffic.stopMonitoring();
    }

    // Sends all controller nodes to the client as node-added messages.
    private void sendAllInstances(String messageType) {
        NodeId id = new NodeId("te_topo");
        IpAddress ip = IpAddress.valueOf("127.0.0.1");
        ControllerNode node = new DefaultControllerNode(id, ip);

        sendMessage(instanceMeowMessage(new ClusterEvent(INSTANCE_ADDED,
                node), messageType));
    }


    private void sendTopoStartDone() {
        sendMessage(JsonUtils.envelope(TOPO_START_DONE, objectNode()));
    }

    private void sendTopoVisHidden() {
        sendMessage(JsonUtils.envelope(TOPO_VIS_HIDDEN, objectNode()));
    }

    // Sends all devices to the client as device-added messages.
    private void sendAllDevices() {
        // Send optical first, others later for layered rendering
        for (Device device : deviceService.getDevices()) {
            if ((device.type() == Device.Type.ROADM) ||
                    (device.type() == Device.Type.OTN)) {
                sendMessage(deviceMessage(new DeviceEvent(DEVICE_ADDED, device)));
            }
        }
        for (Device device : deviceService.getDevices()) {
            if ((device.type() != Device.Type.ROADM) &&
                    (device.type() != Device.Type.OTN)) {
                sendMessage(deviceMessage(new DeviceEvent(DEVICE_ADDED, device)));
            }
        }
        sendDemoDevices();
    }

    private void sendDemoDevices() {
        sendDemoDevice("demo01", "demo01Device", LAYER_CONTROLLER, "");
        sendDemoDevice("demo02", "demo02Device", LAYER_CONTROLLER, "");
        sendDemoDevice("demo03", "demo03Device", LAYER_CONTROLLER, "");

        sendDemoDevice("demo0101",
                       "demo0101Device", LAYER_TRAFFIC, "demo01");
        sendDemoDevice("demo0102",
                       "demo0102Device", LAYER_TRAFFIC, "demo01");
        sendDemoDevice("demo0103",
                       "demo0103Device", LAYER_TRAFFIC, "demo01");

        sendDemoDevice("demo0201",
                       "demo0201Device", LAYER_TRAFFIC, "demo02");
        sendDemoDevice("demo0202",
                       "demo0202Device", LAYER_TRAFFIC, "demo02");
        sendDemoDevice("demo0203",
                       "demo0203Device", LAYER_TRAFFIC, "demo02");
        sendDemoDevice("demo0204",
                       "demo0204Device", LAYER_TRAFFIC, "demo02");
        sendDemoDevice("demo0205",
                       "demo0205Device", LAYER_TRAFFIC, "demo02");
        sendDemoDevice("demo0301",
                       "demo0301Device", LAYER_TRAFFIC, "demo03");
        sendDemoDevice("demo0302",
                       "demo0302Device", LAYER_TRAFFIC, "demo03");
        sendDemoDevice("demo0303",
                       "demo0303Device", LAYER_TRAFFIC, "demo03");
    }

    private void sendDemoDevice(String deviceId, String name, String
            layer, String master) {
        Device device = createDemoDevice(deviceId,
                                         name, layer, master);
        sendMessage(deviceMessageLayer(
                new DeviceEvent(DEVICE_ADDED, device)));
    }

    private Device createDemoDevice(String deviceId, String name, String
            layer, String master) {
        DefaultAnnotations annotations =
                DefaultAnnotations.builder().set("name", name)
                        .set(LAYER_KEY, layer)
                        .set(LAYER_MASTER, master)
                        .build();
        Device device = new DefaultDevice(null,
                                          DeviceId.deviceId(deviceId),
                                          Device.Type.SWITCH,
                                          "",
                                          "",
                                          "",
                                          "",
                                          null,
                                          annotations);

        return device;
    }

    private Device createUplayerDevice(String deviceId, String name, String
            layer, String master) {
        DefaultAnnotations annotations =
                DefaultAnnotations.builder().set("name", name)
                        .set(LAYER_KEY, layer)
                        .set(LAYER_MASTER, master)
                        .build();
        Device device = new DefaultDevice(null,
                                          DeviceId.deviceId(deviceId),
                                          Device.Type.SWITCH,
                                          "",
                                          "",
                                          "",
                                          "",
                                          null,
                                          annotations);

        return device;
    }

    // Sends all links to the client as link-added messages.
    private void sendAllLinks() {
        // Send optical first, others later for layered rendering
        for (Link link : linkService.getLinks()) {
            if (link.type() == Link.Type.OPTICAL) {
                sendMessage(composeLinkMessage(new LinkEvent(LINK_ADDED, link)));
            }
        }
        for (Link link : linkService.getLinks()) {
            if (link.type() != Link.Type.OPTICAL) {
                sendMessage(composeLinkMessage(new LinkEvent(LINK_ADDED, link)));
            }
        }

        sendDemoLinks();
    }

    private void sendDemoLinks() {
        sendDemoTwoWayLink("demo01", "demo02");
        sendDemoTwoWayLink("demo02", "demo03");
        sendDemoTwoWayLink("demo01", "demo03");

        sendDemoTwoWayLink("demo0101", "demo0102");
        sendDemoTwoWayLink("demo0102", "demo0103");
        sendDemoTwoWayLink("demo0103", "demo0101");

        sendDemoTwoWayLink("demo0201", "demo0202");
        sendDemoTwoWayLink("demo0202", "demo0203");
        sendDemoTwoWayLink("demo0203", "demo0204");
        sendDemoTwoWayLink("demo0204", "demo0205");
        sendDemoTwoWayLink("demo0205", "demo0201");

        sendDemoTwoWayLink("demo0301", "demo0302");
        sendDemoTwoWayLink("demo0302", "demo0303");
        sendDemoTwoWayLink("demo0303", "demo0301");

        sendDemoOneWayLink("demo01", "demo0101");
        sendDemoOneWayLink("demo01", "demo0102");
        sendDemoOneWayLink("demo01", "demo0103");

        sendDemoOneWayLink("demo02", "demo0201");
        sendDemoOneWayLink("demo02", "demo0202");
        sendDemoOneWayLink("demo02", "demo0203");
        sendDemoOneWayLink("demo02", "demo0204");
        sendDemoOneWayLink("demo02", "demo0205");

        sendDemoOneWayLink("demo03", "demo0301");
        sendDemoOneWayLink("demo03", "demo0302");
        sendDemoOneWayLink("demo03", "demo0303");
    }

    private void sendDemoTwoWayLink(String srcId, String dstId) {
        sendMessage(linkMessage(new LinkEvent(LINK_ADDED,
                                              createDemoLink(srcId, dstId))));
        sendMessage(linkMessage(new LinkEvent(LINK_ADDED,
                                              createDemoLink(dstId, srcId))));
    }

    private void sendDemoOneWayLink(String srcId, String dstId) {
        sendMessage(linkMessage(new LinkEvent(LINK_ADDED,
                                              createDemoLink(srcId, dstId))));
    }

//    private void sendTeTopology() {
//        List<Network> networks = teTopologyService.getNetworks().networks();
//        for (Network network : networks) {
//            if (network instanceof InternalTeNetwork) {
//                if (((InternalTeNetwork) network).getTeTopologyType() == TeTopologyType.NATIVE) {
//                    sendUpLayer(network);
//                }
//            }
//        }
//    }
//
//    // send uplayer network nodes
//    private void sendUpLayer(Network network) {
//        // send te
//        List<NetworkNode> nodes = network.getNodes();
//        sendTe(nodes, network);
//
//        // send te link
//        List<NetworkLink> links = network.getLinks();
//        sendTeLink(links);
//    }
//
//    private void sendTe(List<NetworkNode> nodes, Network network) {
//        KeyId networkId = network.networkId();
//
//        for (NetworkNode node : nodes) {
//            if (node instanceof DefaultNetworkNode) {
//                DefaultNetworkNode defaultNode = (DefaultNetworkNode) node;
//                // send te
//                sendTeDevice(defaultNode, networkId);
//                // send supportingNode
//                sendInnerLayer(defaultNode);
//            }
//        }
//
//    }

//    private void sendInnerLayer(DefaultNetworkNode defaultNode) {
//        innerLayerDevices(defaultNode);
//        innerLayerLinks(defaultNode);
//    }
//
//    private void innerLayerDevices(DefaultNetworkNode defaultNode) {
//        List<NetworkNodeKey> supportingNodeIds = defaultNode
//                .getSupportingNodeIds();
//        TeNode te = defaultNode.getTe();
//        if (supportingNodeIds == null) {
//            return;
//        }
//        for (NetworkNodeKey key : supportingNodeIds) {
//            Device device = createSupportingDevice(key, te);
//            sendMessage(deviceMessageLayer(
//                    new DeviceEvent(DEVICE_ADDED, device)));
//
//            Link link = createMiddleLink(key, defaultNode);
//            sendMessage(linkMessage(new LinkEvent(LINK_ADDED, link)));
//        }
//
//    }
//
//    private void innerLayerLinks(DefaultNetworkNode defaultNode) {
//        TeNode te = defaultNode.getTe();
//        List<ConnectivityMatrix> connMatrices = te
//                .connectivityMatrices();
//        if (connMatrices == null) {
//            return;
//        }
//        for (ConnectivityMatrix matrix : connMatrices) {
//            Link link = createMatrixLink(matrix);
//
//            sendMessage(linkMessage(new LinkEvent(LINK_ADDED, link)));
//        }
//    }
//
//
//    private void sendTeDevice(DefaultNetworkNode defaultNode, KeyId networkId) {
////        TeNode te = defaultNode.getTe();
//        Device device = createTeDevice(defaultNode, networkId);
//        sendMessage(deviceMessageLayer(
//                new DeviceEvent(DEVICE_ADDED, device)));
//    }
//
//    private Device createTeDevice(DefaultNetworkNode node, KeyId networkId) {
//        DefaultAnnotations annotations =
//                DefaultAnnotations.builder().set("name", node.nodeId().toString())
//                        .set(LAYER_KEY, LAYER_CONTROLLER)
//                        .set(LAYER_MASTER, networkId.toString())
//                        .build();
//        Device device = new DefaultDevice(null,
//                                          DeviceId.deviceId(node.nodeId().toString()),
//                                          Device.Type.SWITCH,
//                                          "",
//                                          "",
//                                          "",
//                                          "",
//                                          null,
//                                          annotations);
//
//        return device;
//    }
//
//    private Device createSupportingDevice(NetworkNodeKey key, TeNode te) {
//        DefaultAnnotations annotations =
//                DefaultAnnotations.builder().set("name", key.nodeId().toString())
//                        .set(LAYER_KEY, LAYER_TRAFFIC)
//                        .set(LAYER_MASTER, te.teNodeId())
//                        .build();
//        Device device = new DefaultDevice(null,
//                                          DeviceId.deviceId(key.nodeId().toString()),
//                                          Device.Type.SWITCH,
//                                          "",
//                                          "",
//                                          "",
//                                          "",
//                                          null,
//                                          annotations);
//
//        return device;
//    }
//
//    private void sendTeLink(List<NetworkLink> links) {
//        for (NetworkLink networkLink : links) {
//            Link link = createTeLink(networkLink);
//
//            sendMessage(linkMessage(new LinkEvent(LINK_ADDED, link)));
//        }
//    }
//
//    private Link createTeLink(NetworkLink networkLink) {
//        TerminationPointKey from = networkLink.getSource();
//        TerminationPointKey to = networkLink.getDestination();
//
//        ConnectPoint src = new ConnectPoint(DeviceId.deviceId(from.nodeId().toString()),
//                                            PortNumber.portNumber(portNaNCheck(from.tpId())));
//
//        ConnectPoint dst = new ConnectPoint(DeviceId.deviceId(to.nodeId().toString()),
//                                            PortNumber.portNumber(portNaNCheck(to.tpId())));
//
//        DefaultLink.Builder builder = DefaultLink.builder();
//        builder.providerId(new ProviderId("127.0.0.1", "meow-topo"));
//        builder.src(src);
//        builder.dst(dst);
//        builder.type(Link.Type.DIRECT);
//        builder.state(Link.State.ACTIVE);
//        builder.isExpected(true);
//        Link link = builder.build();
//
//        return link;
//    }
//
//    private Link createMiddleLink(NetworkNodeKey key, DefaultNetworkNode node) {
//        ConnectPoint src = new ConnectPoint(DeviceId.deviceId(node.nodeId().toString()),
//                                            PortNumber.portNumber("0"));
//
//        ConnectPoint dst = new ConnectPoint(DeviceId.deviceId(key.nodeId().toString()),
//                                            PortNumber.portNumber("0"));
//
//        DefaultLink.Builder builder = DefaultLink.builder();
//        builder.providerId(new ProviderId("127.0.0.1", "meow-topo"));
//        builder.src(src);
//        builder.dst(dst);
//        builder.type(Link.Type.DIRECT);
//
//        builder.state(Link.State.ACTIVE);
//        builder.isExpected(true);
//        Link link = builder.build();
//
//        return link;
//    }
//
//    private Link createMatrixLink(ConnectivityMatrix matrix) {
//        TerminationPointKey from = matrix.from();
//        TerminationPointKey to = matrix.to();
//
//        ConnectPoint src = new ConnectPoint(DeviceId.deviceId(from.nodeId().toString()),
//                                            PortNumber.portNumber(portNaNCheck(from.tpId())));
//
//        ConnectPoint dst = new ConnectPoint(DeviceId.deviceId(to.nodeId().toString()),
//                                            PortNumber.portNumber(portNaNCheck(to.tpId())));
//
//        DefaultLink.Builder builder = DefaultLink.builder();
//        builder.providerId(new ProviderId("127.0.0.1", "meow-topo"));
//        builder.src(src);
//        builder.dst(dst);
//        builder.type(Link.Type.DIRECT);
//
//        builder.state(Link.State.ACTIVE);
//        builder.isExpected(true);
//        Link link = builder.build();
//
//        return link;
//    }
//
//    private String portNaNCheck(KeyId tpId) {
////        if (tpId == null) {
////            return "0";
////        }
////        return tpId.toString();
//        return "1";
//    }


    private Link createDemoLink(String srcId, String dstId) {

        ConnectPoint src = new ConnectPoint(DeviceId.deviceId(srcId),
                PortNumber.portNumber(3));

        ConnectPoint dst = new ConnectPoint(DeviceId.deviceId(dstId),
                PortNumber.portNumber(3));

        DefaultLink.Builder builder = DefaultLink.builder();
        builder.providerId(new ProviderId("127.0.0.1", "meow-topo"));
        builder.src(src);
        builder.dst(dst);
        builder.type(Link.Type.DIRECT);
        builder.state(Link.State.ACTIVE);
        builder.isExpected(true);
        Link link = builder.build();

        return link;
    }

    // Temporary mechanism to support topology overlays adding their own
    // properties to the link events.
    private ObjectNode composeLinkMessage(LinkEvent event) {
        // start with base message
        ObjectNode msg = linkMessage(event);
        // TODO: overlayCache : add  EXTRA info
//        Map<String, String> additional =
//                overlayCache.currentOverlay().additionalLinkData(event);
//
//        if (additional != null) {
//            // attach additional key-value pairs as extra data structure
//            ObjectNode payload = (ObjectNode) msg.get(PAYLOAD);
//            payload.set(EXTRA, createExtra(additional));
//        }
        return msg;
    }


    // Adds all internal listeners.
    private synchronized void addListeners() {
        listenersRemoved = false;
//        clusterService.addListener(clusterListener);
//        mastershipService.addListener(mastershipListener);
        deviceService.addListener(deviceListener);
        linkService.addListener(linkListener);
//        hostService.addListener(hostListener);
//        intentService.addListener(intentListener);
//        flowService.addListener(flowListener);
    }

    // Removes all internal listeners.
    private synchronized void removeListeners() {
        if (!listenersRemoved) {
            listenersRemoved = true;
//            clusterService.removeListener(clusterListener);
//            mastershipService.removeListener(mastershipListener);
            deviceService.removeListener(deviceListener);
            linkService.removeListener(linkListener);
//            hostService.removeListener(hostListener);
//            intentService.removeListener(intentListener);
//            flowService.removeListener(flowListener);
        }
    }

    // Device event listener.
    // TODO: Superceded by UiSharedTopologyModel.ModelEventListener
    @Deprecated
    private class InternalDeviceListener implements DeviceListener {
        @Override
        public void event(DeviceEvent event) {
            if (event.type() != PORT_STATS_UPDATED) {
                msgSender.execute(() -> sendMessage(deviceMessage(event)));
//                msgSender.execute(traffic::pokeIntent);
//                eventAccummulator.add(event);
            }
        }
    }


    // Link event listener.
    // TODO: Superceded by UiSharedTopologyModel.ModelEventListener
    @Deprecated
    private class InternalLinkListener implements LinkListener {
        @Override
        public void event(LinkEvent event) {
            msgSender.execute(() -> sendMessage(composeLinkMessage(event)));
//            msgSender.execute(traffic::pokeIntent);
//            eventAccummulator.add(event);
        }
    }
}