package cn.yifan.drawsee.parser;

import cn.yifan.drawsee.pojo.entity.CircuitDesign;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class CircuitImageNetlistParser {

    private static final Pattern KEY_VALUE_PATTERN = Pattern.compile("(\\w+)=\\\"([^\\\"]*)\\\"|(\\w+)=([^\\s]+)");

    private static final Map<String, List<PortTemplate>> PORT_TEMPLATES = createPortTemplates();

    public CircuitDesign parse(String netlist) {
        if (netlist == null || netlist.isBlank()) {
            throw new IllegalArgumentException("网表内容为空");
        }

        List<CircuitDesign.CircuitElement> elements = new ArrayList<>();
        List<CircuitDesign.CircuitConnection> connections = new ArrayList<>();
        String title = "AI识别电路";
        String description = "由电路图识别自动生成";

        String[] lines = netlist.split("\\r?\\n");
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("TITLE")) {
                title = line.substring(5).trim();
                continue;
            }
            if (line.startsWith("DESCRIPTION")) {
                description = line.substring(11).trim();
                continue;
            }
            if (line.startsWith("COMP")) {
                elements.add(parseComponent(line));
                continue;
            }
            if (line.startsWith("WIRE")) {
                CircuitDesign.CircuitConnection connection = parseWire(line);
                if (connection != null) {
                    connections.add(connection);
                }
            }
        }

        if (elements.isEmpty() || connections.isEmpty()) {
            throw new IllegalArgumentException("网表缺少元件或连接信息");
        }

        CircuitDesign.CircuitMetadata metadata = new CircuitDesign.CircuitMetadata();
        metadata.setTitle(title.isBlank() ? "AI识别电路" : title);
        metadata.setDescription(description.isBlank() ? "" : description);
        String now = OffsetDateTime.now().toString();
        metadata.setCreatedAt(now);
        metadata.setUpdatedAt(now);

        CircuitDesign design = new CircuitDesign();
        design.setElements(elements);
        design.setConnections(connections);
        design.setMetadata(metadata);
        return design;
    }

    private CircuitDesign.CircuitElement parseComponent(String line) {
        Map<String, String> kv = parseKeyValuePairs(line.substring(4));
        String id = kv.getOrDefault("id", UUID.randomUUID().toString());
        String type = kv.getOrDefault("type", "unknown");
        String label = kv.getOrDefault("label", id);
        double x = parseDouble(kv.get("x"), 0);
        double y = parseDouble(kv.get("y"), 0);
        int rotation = (int) parseDouble(kv.get("rotation"), 0);

        CircuitDesign.CircuitElement element = new CircuitDesign.CircuitElement();
        element.setId(id);
        element.setType(type);
        CircuitDesign.Position position = new CircuitDesign.Position();
        position.setX(x);
        position.setY(y);
        element.setPosition(position);
        element.setRotation(rotation);
        Map<String, Object> properties = new HashMap<>();
        properties.put("label", label);
        element.setProperties(properties);
        element.setPorts(buildPortsForType(type));
        return element;
    }

    private CircuitDesign.CircuitConnection parseWire(String line) {
        Map<String, String> kv = parseKeyValuePairs(line.substring(4));
        String id = kv.getOrDefault("id", UUID.randomUUID().toString());
        String source = kv.get("source");
        String target = kv.get("target");
        if (source == null || target == null) {
            log.warn("WIRE 缺少 source/target: {}", line);
            return null;
        }
        CircuitDesign.CircuitConnection connection = new CircuitDesign.CircuitConnection();
        connection.setId(id);
        connection.setSource(parsePortReference(source));
        connection.setTarget(parsePortReference(target));
        return connection;
    }

    private CircuitDesign.PortReference parsePortReference(String ref) {
        String[] parts = ref.split("\\.");
        if (parts.length != 2) {
            throw new IllegalArgumentException("端口引用必须为 元件ID.端口ID ：" + ref);
        }
        CircuitDesign.PortReference reference = new CircuitDesign.PortReference();
        reference.setElementId(parts[0]);
        reference.setPortId(parts[1]);
        return reference;
    }

    private List<CircuitDesign.Port> buildPortsForType(String type) {
        List<PortTemplate> templates = PORT_TEMPLATES.get(type);
        if (templates == null) {
            return Collections.emptyList();
        }
        List<CircuitDesign.Port> ports = new ArrayList<>();
        for (PortTemplate template : templates) {
            CircuitDesign.Port port = new CircuitDesign.Port();
            port.setId(template.id);
            port.setName(template.name);
            port.setType(template.type);
            CircuitDesign.PortPosition position = new CircuitDesign.PortPosition();
            position.setSide(template.side);
            position.setX(template.x);
            position.setY(template.y);
            position.setAlign("center");
            port.setPosition(position);
            ports.add(port);
        }
        return ports;
    }

    private Map<String, String> parseKeyValuePairs(String content) {
        Map<String, String> map = new LinkedHashMap<>();
        Matcher matcher = KEY_VALUE_PATTERN.matcher(content);
        while (matcher.find()) {
            if (matcher.group(1) != null) {
                map.put(matcher.group(1), matcher.group(2));
            } else {
                map.put(matcher.group(3), matcher.group(4));
            }
        }
        return map;
    }

    private double parseDouble(String value, double fallback) {
        if (value == null) return fallback;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static Map<String, List<PortTemplate>> createPortTemplates() {
        Map<String, List<PortTemplate>> map = new HashMap<>();
        map.put("digital_input", List.of(
            new PortTemplate("out", "out", "output", "right", 100, 50)
        ));
        map.put("digital_output", List.of(
            new PortTemplate("in", "in", "input", "left", 0, 50)
        ));
        map.put("digital_clock", List.of(
            new PortTemplate("out", "out", "output", "right", 100, 50)
        ));
        map.put("digital_not", List.of(
            new PortTemplate("in", "in", "input", "left", 0, 50),
            new PortTemplate("out", "out", "output", "right", 100, 50)
        ));
        map.put("digital_and", createGatePorts());
        map.put("digital_or", createGatePorts());
        map.put("digital_nand", createGatePorts());
        map.put("digital_nor", createGatePorts());
        map.put("digital_xor", createGatePorts());
        map.put("digital_xnor", createGatePorts());
        map.put("digital_dff", List.of(
            new PortTemplate("d", "d", "input", "left", 0, 30),
            new PortTemplate("clk", "clk", "input", "bottom", 50, 100),
            new PortTemplate("q", "q", "output", "right", 100, 30)
        ));
        map.put("resistor", List.of(
            new PortTemplate("port1", "port1", "bidirectional", "left", 0, 50),
            new PortTemplate("port2", "port2", "bidirectional", "right", 100, 50)
        ));
        map.put("capacitor", map.get("resistor"));
        map.put("inductor", map.get("resistor"));
        map.put("diode", List.of(
            new PortTemplate("anode", "anode", "input", "left", 0, 50),
            new PortTemplate("cathode", "cathode", "output", "right", 100, 50)
        ));
        map.put("bjt", List.of(
            new PortTemplate("base", "base", "input", "left", 0, 50),
            new PortTemplate("collector", "collector", "output", "right", 100, 25),
            new PortTemplate("emitter", "emitter", "output", "right", 100, 75)
        ));
        map.put("bjt_pnp", map.get("bjt"));
        map.put("dc_source", List.of(
            new PortTemplate("positive", "positive", "output", "right", 100, 50),
            new PortTemplate("negative", "negative", "input", "left", 0, 50)
        ));
        map.put("ac_source", map.get("dc_source"));
        map.put("ammeter", List.of(
            new PortTemplate("in", "in", "bidirectional", "left", 0, 50),
            new PortTemplate("out", "out", "bidirectional", "right", 100, 50)
        ));
        map.put("voltmeter", List.of(
            new PortTemplate("positive", "positive", "bidirectional", "left", 0, 30),
            new PortTemplate("negative", "negative", "bidirectional", "right", 100, 70)
        ));
        map.put("ground", List.of(
            new PortTemplate("ground", "ground", "input", "top", 50, 0)
        ));
        map.put("opamp", List.of(
            new PortTemplate("input1", "input1", "input", "left", 0, 30),
            new PortTemplate("input2", "input2", "input", "left", 0, 70),
            new PortTemplate("output", "output", "output", "right", 100, 50)
        ));
        return map;
    }

    private static List<PortTemplate> createGatePorts() {
        return List.of(
            new PortTemplate("in1", "in1", "input", "left", 0, 30),
            new PortTemplate("in2", "in2", "input", "left", 0, 70),
            new PortTemplate("out", "out", "output", "right", 100, 50)
        );
    }

    private record PortTemplate(String id, String name, String type, String side, double x, double y) {}
}
