package org.onosproject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.onlab.osgi.ServiceDirectory;
import org.onlab.util.DefaultHashMap;
import org.onosproject.cluster.ClusterEvent;
import org.onosproject.cluster.ControllerNode;
import org.onosproject.cluster.NodeId;
import org.onosproject.mastership.MastershipService;
import org.onosproject.net.Annotated;
import org.onosproject.net.AnnotationKeys;
import org.onosproject.net.Annotations;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Host;
import org.onosproject.net.HostId;
import org.onosproject.net.Link;
import org.onosproject.net.device.DeviceEvent;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.FlowEntry;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.host.HostService;
import org.onosproject.net.link.LinkEvent;
import org.onosproject.net.link.LinkService;
import org.onosproject.tetopology.management.api.TeTopologyService;
import org.onosproject.ui.JsonUtils;
import org.onosproject.ui.UiConnection;
import org.onosproject.ui.UiMessageHandler;
import org.onosproject.ui.topo.PropertyPanel;
import org.onosproject.ui.topo.TopoConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Strings.isNullOrEmpty;
import static org.onosproject.ui.topo.TopoUtils.compactLinkString;

/**
 * Facility for creating messages bound for the topology viewer.
 */
public abstract class TeTopoUiMessageHandlerBase extends UiMessageHandler {
    // default to an "add" event...
    private static final DefaultHashMap<ClusterEvent.Type, String> CLUSTER_EVENT =
            new DefaultHashMap<>("addInstance");

    // default to an "update" event...
    private static final DefaultHashMap<DeviceEvent.Type, String> DEVICE_EVENT =
            new DefaultHashMap<>("updateDevice");
    private static final DefaultHashMap<LinkEvent.Type, String> LINK_EVENT =
            new DefaultHashMap<>("updateLink");

    // but call out specific events that we care to differentiate...
    static {
        DEVICE_EVENT.put(DeviceEvent.Type.DEVICE_ADDED, "addDevice");
        DEVICE_EVENT.put(DeviceEvent.Type.DEVICE_REMOVED, "removeDevice");

        LINK_EVENT.put(LinkEvent.Type.LINK_ADDED, "addLink");
        LINK_EVENT.put(LinkEvent.Type.LINK_REMOVED, "removeLink");
    }


    protected static final Logger log =
            LoggerFactory.getLogger(TeTopoUiMessageHandlerBase.class);


    protected DeviceService deviceService;
    protected LinkService linkService;
    protected HostService hostService;
    protected MastershipService mastershipService;
    protected FlowRuleService flowService;
//    protected TunnelService tunnelService;
    protected TeTopologyService teTopologyService;


    // multi topo layer define
    protected static final String LAYER_KEY = "ctrl_layer";
    protected static final String LAYER_CONTROLLER = "pkt"; // controller
    protected static final String LAYER_TRAFFIC = "opt"; // traffic
    protected static final String LAYER_MASTER = "layermaster"; // master


    @Override
    public void init(UiConnection connection, ServiceDirectory directory) {
        super.init(connection, directory);
//        this.directory = checkNotNull(directory, "Directory cannot be null");
//        clusterService = directory.get(ClusterService.class);
        deviceService = directory.get(DeviceService.class);
        linkService = directory.get(LinkService.class);
        hostService = directory.get(HostService.class);
        mastershipService = directory.get(MastershipService.class);
//        intentService = directory.get(IntentService.class);
        flowService = directory.get(FlowRuleService.class);
//        flowStatsService = directory.get(StatisticService.class);
//        portStatsService = directory.get(PortStatisticsService.class);
//        topologyService = directory.get(TopologyService.class);
//        tunnelService = directory.get(TunnelService.class);
//
//        servicesBundle = new ServicesBundle(intentService, deviceService,
//                                            hostService, linkService,
//                                            flowService,
//                                            flowStatsService, portStatsService);
//
//        String ver = directory.get(CoreService.class).version().toString();
//        version = ver.replace(".SNAPSHOT", "*").replaceFirst("~.*$", "");

        teTopologyService = directory.get(TeTopologyService.class);
    }



    // Produces a device event message to the client.
    protected ObjectNode deviceMessage(DeviceEvent event) {
        Device device = event.subject();
        ObjectNode payload = objectNode()
                .put("id", device.id().toString())
                .put("type", device.type().toString().toLowerCase())
                .put("online", deviceService.isAvailable(device.id()))
                .put("master", "127.0.0.1");

        // Generate labels: id, chassis id, no-label, optional-name
        String name = device.annotations().value(AnnotationKeys.NAME);
        ArrayNode labels = arrayNode();
        labels.add("");
        labels.add(isNullOrEmpty(name) ? device.id().toString() : name);
        labels.add(device.id().toString());

        // Add labels, props and stuff the payload into envelope.
        payload.set("labels", labels);
        payload.set("props", props(device.annotations()));
        addGeoLocation(device, payload);
        addMetaUi(device.id().toString(), payload);

        String type = DEVICE_EVENT.get(event.type());
        return JsonUtils.envelope(type, 0, payload);
    }


    protected ObjectNode deviceMessageLayer(DeviceEvent event) {
        Device device = event.subject();
        String ctrlLayer = device.annotations().value(LAYER_KEY);
        ObjectNode payload = objectNode()
                .put("id", device.id().toString())
                .put("type", device.type().toString().toLowerCase())
                .put("online", ctrlLayer.equals(LAYER_CONTROLLER))
                .put("master", "127.0.0.1");

        // Generate labels: id, chassis id, no-label, optional-name
        String name = device.annotations().value(AnnotationKeys.NAME);
        ArrayNode labels = arrayNode();
        labels.add("");
        labels.add(isNullOrEmpty(name) ? device.id().toString() : name);
        labels.add(device.id().toString());

        // Add labels, props and stuff the payload into envelope.
        payload.set("labels", labels);
        payload.set("props", props(device.annotations()));
        addGeoLocation(device, payload);
        addMetaUi(device.id().toString(), payload);

        String type = DEVICE_EVENT.get(event.type());
        return JsonUtils.envelope(type, 0, payload);
    }


    // Produces a link event message to the client.
    protected ObjectNode linkMessage(LinkEvent event) {
        Link link = event.subject();
        ObjectNode payload = objectNode()
                .put("id", compactLinkString(link))
                .put("type", link.type().toString().toLowerCase())
                .put("expected", link.isExpected())
                .put("online", link.state() == Link.State.ACTIVE)
                .put("linkWidth", 1.2)
                .put("src", link.src().deviceId().toString())
                .put("srcPort", link.src().port().toString())
                .put("dst", link.dst().deviceId().toString())
                .put("dstPort", link.dst().port().toString());
        String type = LINK_EVENT.get(event.type());
        return JsonUtils.envelope(type, 0, payload);
    }

    // Returns the name of the master node for the specified device id.
    private String master(DeviceId deviceId) {
        NodeId master = mastershipService.getMasterFor(deviceId);
        return master != null ? master.toString() : "";
    }


    // Produces JSON structure from annotations.
    private JsonNode props(Annotations annotations) {
        ObjectNode props = objectNode();
        if (annotations != null) {
            for (String key : annotations.keys()) {
                props.put(key, annotations.value(key));
            }
        }
        return props;
    }


    // Adds a geo location JSON to the specified payload object.
    private void addGeoLocation(Annotated annotated, ObjectNode payload) {
        Annotations annotations = annotated.annotations();
        if (annotations == null) {
            return;
        }

        String slng = annotations.value(AnnotationKeys.LONGITUDE);
        String slat = annotations.value(AnnotationKeys.LATITUDE);
        boolean haveLng = slng != null && !slng.isEmpty();
        boolean haveLat = slat != null && !slat.isEmpty();
        try {
            if (haveLng && haveLat) {
                double lng = Double.parseDouble(slng);
                double lat = Double.parseDouble(slat);
                ObjectNode loc = objectNode()
                        .put("type", "lnglat")
                        .put("lng", lng)
                        .put("lat", lat);
                payload.set("location", loc);
            } else {
                log.trace("missing Lng/Lat: lng={}, lat={}", slng, slat);
            }
        } catch (NumberFormatException e) {
            log.warn("Invalid geo data: longitude={}, latitude={}", slng, slat);
        }
    }


    // Adds meta UI information for the specified object.
    private void addMetaUi(String id, ObjectNode payload) {
//        ObjectNode meta = metaUi.get(id);
//        if (meta != null) {
//            payload.set("metaUi", meta);
//        }
    }

    // -----------------------------------------------------------------------
    // Create models of the data to return, that overlays can adjust / augment


    // Returns property panel model for device details response.
    protected PropertyPanel deviceDetails(DeviceId deviceId, long sid) {
        Device device = deviceService.getDevice(deviceId);
        Annotations annot = device.annotations();
        String name = annot.value(AnnotationKeys.NAME);
        int portCount = deviceService.getPorts(deviceId).size();
        int flowCount = getFlowCount(deviceId);
//        int tunnelCount = getTunnelCount(deviceId);

        String title = isNullOrEmpty(name) ? deviceId.toString() : name;
        String typeId = device.type().toString().toLowerCase();

        PropertyPanel pp = new PropertyPanel(title, typeId)
                .id(deviceId.toString())

                .addProp(TopoConstants.Properties.URI, deviceId.toString())
                .addProp(TopoConstants.Properties.VENDOR, device.manufacturer())
                .addProp(TopoConstants.Properties.HW_VERSION, device.hwVersion())
                .addProp(TopoConstants.Properties.SW_VERSION, device.swVersion())
                .addProp(TopoConstants.Properties.SERIAL_NUMBER, device.serialNumber())
                .addProp(TopoConstants.Properties.PROTOCOL, annot.value(AnnotationKeys.PROTOCOL))
                .addSeparator()

                .addProp(TopoConstants.Properties.LATITUDE, annot.value(AnnotationKeys.LATITUDE))
                .addProp(TopoConstants.Properties.LONGITUDE, annot.value(AnnotationKeys.LONGITUDE))
                .addSeparator()

                .addProp(TopoConstants.Properties.PORTS, portCount)
                .addProp(TopoConstants.Properties.FLOWS, flowCount)
//                .addProp(TopoConstants.Properties.TUNNELS, tunnelCount)

                .addButton(TopoConstants.CoreButtons.SHOW_DEVICE_VIEW)
                .addButton(TopoConstants.CoreButtons.SHOW_FLOW_VIEW)
                .addButton(TopoConstants.CoreButtons.SHOW_PORT_VIEW)
                .addButton(TopoConstants.CoreButtons.SHOW_GROUP_VIEW)
                .addButton(TopoConstants.CoreButtons.SHOW_METER_VIEW);

        return pp;
    }

    protected PropertyPanel demoDetails(String deviceId, String name) {
        String title = name;
        String typeId = Device.Type.ROADM.toString().toLowerCase();

        PropertyPanel pp = new PropertyPanel(title, typeId)
                .id(deviceId)

                .addProp(TopoConstants.Properties.URI, deviceId.toString())
                .addButton(TopoConstants.CoreButtons.SHOW_DEVICE_VIEW)
                .addButton(TopoConstants.CoreButtons.SHOW_FLOW_VIEW)
                .addButton(TopoConstants.CoreButtons.SHOW_PORT_VIEW)
                .addButton(TopoConstants.CoreButtons.SHOW_GROUP_VIEW)
                .addButton(TopoConstants.CoreButtons.SHOW_METER_VIEW);

        return pp;
    }

    // Returns host details response.
    protected PropertyPanel hostDetails(HostId hostId, long sid) {
        Host host = hostService.getHost(hostId);
        Annotations annot = host.annotations();
        String type = annot.value(AnnotationKeys.TYPE);
        String name = annot.value(AnnotationKeys.NAME);
        String vlan = host.vlan().toString();

        String title = isNullOrEmpty(name) ? hostId.toString() : name;
        String typeId = isNullOrEmpty(type) ? "endstation" : type;

        PropertyPanel pp = new PropertyPanel(title, typeId)
                .id(hostId.toString())
                .addProp(TopoConstants.Properties.MAC, host.mac())
                .addProp(TopoConstants.Properties.IP, host.ipAddresses(), "[\\[\\]]")
                .addProp(TopoConstants.Properties.VLAN, vlan.equals("-1") ? "none" : vlan)
                .addSeparator()
                .addProp(TopoConstants.Properties.LATITUDE, annot.value(AnnotationKeys.LATITUDE))
                .addProp(TopoConstants.Properties.LONGITUDE, annot.value(AnnotationKeys.LONGITUDE));

        // Potentially add button descriptors here
        return pp;
    }

    protected int getFlowCount(DeviceId deviceId) {
        int count = 0;
        for (FlowEntry flowEntry : flowService.getFlowEntries(deviceId)) {
            count++;
        }
        return count;
    }


    // Produces a cluster instance message to the client.
    protected ObjectNode instanceMessage(ClusterEvent event, String msgType) {
        ControllerNode node = event.subject();
        int switchCount = mastershipService.getDevicesOf(node.id()).size();
        ObjectNode payload = objectNode()
                .put("id", node.id().toString())
                .put("ip", node.ip().toString())
//                .put("online", clusterService.getState(node.id()).isActive())
//                .put("ready", clusterService.getState(node.id()).isReady())
//                .put("uiAttached", node.equals(clusterService.getLocalNode()))
                .put("switches", switchCount);

        ArrayNode labels = arrayNode();
        labels.add(node.id().toString());
        labels.add(node.ip().toString());

        // Add labels, props and stuff the payload into envelope.
        payload.set("labels", labels);
        addMetaUi(node.id().toString(), payload);

        String type = msgType != null ? msgType : CLUSTER_EVENT.get(event.type());
        return JsonUtils.envelope(type, 0, payload);
    }

    protected  ObjectNode instanceMeowMessage(ClusterEvent event, String msgType) {
        ControllerNode node = event.subject();
        int switchCount = mastershipService.getDevicesOf(node.id()).size();
        ObjectNode payload = objectNode()
                .put("id", node.id().toString())
                .put("ip", node.ip().toString())
//                .put("online", clusterService.getState(node.id()).isActive())
//                .put("ready", clusterService.getState(node.id()).isReady())
//                .put("uiAttached", node.equals(clusterService.getLocalNode()))
                .put("switches", switchCount);

        ArrayNode labels = arrayNode();
        labels.add(node.id().toString());
        labels.add(node.ip().toString());

        // Add labels, props and stuff the payload into envelope.
        payload.set("labels", labels);
        addMetaUi(node.id().toString(), payload);
        String type = msgType != null ? msgType : CLUSTER_EVENT.get(event.type());
        return JsonUtils.envelope(type, 0, payload);
    }


//    protected int getTunnelCount(DeviceId deviceId) {
//        int count = 0;
//        Collection<Tunnel> tunnels = tunnelService.queryAllTunnels();
//        for (Tunnel tunnel : tunnels) {
//            //Only OpticalTunnelEndPoint has a device
//            if (!(tunnel.src() instanceof OpticalTunnelEndPoint) ||
//                    !(tunnel.dst() instanceof OpticalTunnelEndPoint)) {
//                continue;
//            }
//
//            Optional<ElementId> srcElementId = ((OpticalTunnelEndPoint) tunnel.src()).elementId();
//            Optional<ElementId> dstElementId = ((OpticalTunnelEndPoint) tunnel.dst()).elementId();
//            if (!srcElementId.isPresent() || !dstElementId.isPresent()) {
//                continue;
//            }
//            DeviceId srcDeviceId = (DeviceId) srcElementId.get();
//            DeviceId dstDeviceId = (DeviceId) dstElementId.get();
//            if (srcDeviceId.equals(deviceId) || dstDeviceId.equals(deviceId)) {
//                count++;
//            }
//        }
//        return count;
//    }
}
