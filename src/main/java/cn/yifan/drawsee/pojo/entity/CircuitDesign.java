package cn.yifan.drawsee.pojo.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CircuitDesign {
    private List<CircuitElement> elements;
    private List<CircuitConnection> connections;
    private CircuitMetadata metadata;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CircuitElement {
        private String id;
        private String type;
        private Position position;
        private int rotation;
        private Map<String, Object> properties;
        private List<Port> ports;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CircuitConnection {
        private String id;
        private PortReference source;
        private PortReference target;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Position {
        private double x;
        private double y;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Port {
        private String id;
        private String name;
        private String type;
        private PortPosition position;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PortPosition {
        private String side;
        private double x;
        private double y;
        private String align;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PortReference {
        private String elementId;
        private String portId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CircuitMetadata {
        private String title;
        private String description;
        private String createdAt;
        private String updatedAt;
    }
} 