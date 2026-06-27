package pro.gravit.launcher.gui.core.service;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import pro.gravit.launcher.core.backend.LauncherBackendAPI;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PingService {
    private final Map<UUID, ObjectProperty<PingServerReport>> reports = new ConcurrentHashMap<>();

    public ObservableValue<PingServerReport> getPingReport(UUID serverName) {
        return getPingReportProperty(serverName);
    }

    public ObjectProperty<PingServerReport> getPingReportProperty(UUID serverName) {
        return reports.computeIfAbsent(serverName, k -> new SimpleObjectProperty<>());
    }

    public void addReports(Map<UUID, PingServerReport> map) {
        map.forEach((k, v) -> {
            getPingReportProperty(k).set(v);
        });
    }

    public void addReport(UUID name, LauncherBackendAPI.ServerPingInfo result) {
        PingServerReport value = new PingServerReport(name, result.getMaxOnline(), result.getOnline());
        getPingReportProperty(name).set(value);
    }

    public void clear() {
        reports.values().forEach(report -> report.set(null));
    }

    public static class PingServerReport {
        public final UUID name;
        public final int maxPlayers;
        public final int playersOnline;

        public PingServerReport(UUID name, int maxPlayers, int playersOnline) {
            this.name = name;
            this.maxPlayers = maxPlayers;
            this.playersOnline = playersOnline;
        }
    }
}
