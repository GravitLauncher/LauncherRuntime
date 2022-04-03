package pro.gravit.launcher.client.gui.service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import pro.gravit.launcher.client.ServerPinger;
import pro.gravit.launcher.request.management.PingServerReportRequest;

public class PingService {
    private final Map<String, CompletableFuture<PingServerReportRequest.PingServerReport>> reports = new ConcurrentHashMap<>();

    public CompletableFuture<PingServerReportRequest.PingServerReport> getPingReport(String serverName) {
        CompletableFuture<PingServerReportRequest.PingServerReport> report = reports.get(serverName);
        if (report == null) {
            report = new CompletableFuture<>();
            reports.put(serverName, report);
        }
        return report;
    }

    public void addReports(Map<String, PingServerReportRequest.PingServerReport> map) {
        map.forEach((k, v) -> {
            CompletableFuture<PingServerReportRequest.PingServerReport> report = getPingReport(k);
            report.complete(v);
        });
    }

    public void addReport(String name, ServerPinger.Result result) {
        CompletableFuture<PingServerReportRequest.PingServerReport> report = getPingReport(name);
        PingServerReportRequest.PingServerReport value = new PingServerReportRequest.PingServerReport(name,
                result.maxPlayers, result.onlinePlayers);
        report.complete(value);
    }

    public void clear() {
        reports.forEach((k, v) -> {
            if (!v.isDone()) {
                v.completeExceptionally(new InterruptedException());
            }
        });
        reports.clear();
    }
}
