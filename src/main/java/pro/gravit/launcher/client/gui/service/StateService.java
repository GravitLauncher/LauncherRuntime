package pro.gravit.launcher.client.gui.service;

import pro.gravit.launcher.client.ServerPinger;
import pro.gravit.launcher.events.request.AuthRequestEvent;
import pro.gravit.launcher.events.request.PingServerRequestEvent;
import pro.gravit.launcher.events.request.ProfilesRequestEvent;
import pro.gravit.launcher.profiles.ClientProfile;
import pro.gravit.launcher.profiles.PlayerProfile;
import pro.gravit.launcher.profiles.optional.OptionalFile;
import pro.gravit.launcher.profiles.optional.OptionalTrigger;
import pro.gravit.launcher.profiles.optional.OptionalView;
import pro.gravit.launcher.request.Request;
import pro.gravit.launcher.request.management.PingServerReportRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StateService {
    private AuthRequestEvent rawAuthResult;

    private List<ClientProfile> profiles;
    private ClientProfile profile;
    private Map<String, PingServerReportRequest.PingServerReport> serverPingReport;
    private Map<ClientProfile, OptionalView> optionalViewMap;
    @FunctionalInterface
    public interface OnServerPingReportCallback
    {
        void onServerPingReport(PingServerReportRequest.PingServerReport report);
    }
    private final Map<String, OnServerPingReportCallback> serverPingReportCallbackMap = new HashMap<>();

    public void setAuthResult(String authId, AuthRequestEvent rawAuthResult) {
        this.rawAuthResult = rawAuthResult;
        if(rawAuthResult.oauth != null) {
            Request.setOAuth(authId, rawAuthResult.oauth);
        }
        else if (rawAuthResult.session != null)
            Request.setSession(rawAuthResult.session);
    }

    public Map<String, PingServerReportRequest.PingServerReport> getServerPingReport() {
        return serverPingReport;
    }

    public void setServerPingReport(Map<String, PingServerReportRequest.PingServerReport> serverPingReport) {
        this.serverPingReport = serverPingReport;
        serverPingReportCallbackMap.forEach((name, callback) -> {
            PingServerReportRequest.PingServerReport report = serverPingReport.get(name);
            callback.onServerPingReport(report);
        });
    }

    public void addServerSocketPing(ClientProfile.ServerProfile profile, ServerPinger.Result result) {
        PingServerReportRequest.PingServerReport report = new PingServerReportRequest.PingServerReport(profile.name, result.maxPlayers, result.onlinePlayers);
        if(this.serverPingReport != null) {
            this.serverPingReport.put(profile.name, report);
        }
        OnServerPingReportCallback cb = serverPingReportCallbackMap.get(profile.name);
        if(cb != null) {
            cb.onServerPingReport(report);
        }
    }

    public void addServerPingCallback(String name, OnServerPingReportCallback callback)
    {
        if(serverPingReport != null)
        {
            PingServerReportRequest.PingServerReport report = serverPingReport.get(name);
            callback.onServerPingReport(report);
        }
        serverPingReportCallbackMap.put(name, callback);
    }

    public Map<ClientProfile, OptionalView> getOptionalViewMap() {
        return optionalViewMap;
    }

    public void setOptionalView(ClientProfile profile, OptionalView view) {
        optionalViewMap.put(profile, view);
    }

    public void clearServerPingCallbacks()
    {
        serverPingReportCallbackMap.clear();
    }

    public void setProfilesResult(ProfilesRequestEvent rawProfilesResult) {
        this.profiles = rawProfilesResult.profiles;
        this.profiles.sort(ClientProfile::compareTo);
        if(this.optionalViewMap == null) this.optionalViewMap = new HashMap<>();
        else this.optionalViewMap.clear();
        for(ClientProfile profile : profiles)
        {
            this.optionalViewMap.put(profile, new OptionalView(profile));
        }
        //Triggers
        this.optionalViewMap.forEach((profile, view) -> {
            for (OptionalFile optionalFile : profile.getOptional()) {
                if (optionalFile.triggers == null)
                    continue;
                if(optionalFile.permissions != 0 && rawAuthResult != null && rawAuthResult.permissions != null) {
                    optionalFile.visible = (optionalFile.permissions & rawAuthResult.permissions.permissions) == optionalFile.permissions;
                }

                boolean anyTriggered = false;
                boolean anyNeed = false;
                boolean allNeedTriggered = false;

                for (OptionalTrigger trigger : optionalFile.triggers) {
                    boolean isTriggered = trigger.isTriggered();
                    if (isTriggered)
                        anyTriggered = true;
                    if (trigger.need) {
                        if (!anyNeed) {
                            anyNeed = true;
                            allNeedTriggered = isTriggered;
                        } else {
                            if (allNeedTriggered)
                                allNeedTriggered = isTriggered;
                        }
                    }
                }

                if (!anyNeed) {
                    if (anyTriggered)
                        view.enable(optionalFile, false, null);
                } else {
                    if (allNeedTriggered) {
                        view.enable(optionalFile, false, null);
                    } else {
                        optionalFile.visible = false;
                        view.disable(optionalFile, null);
                    }
                }
            }
        });
    }

    public String getUsername() {
        if (rawAuthResult == null || rawAuthResult.playerProfile == null)
            return "Player";
        return rawAuthResult.playerProfile.username;
    }

    public List<ClientProfile> getProfiles() {
        return profiles;
    }

    public ClientProfile getProfile() {
        return profile;
    }

    public void setProfile(ClientProfile profile) {
        this.profile = profile;
    }

    public OptionalView getOptionalView() {
        return this.optionalViewMap.get(this.profile);
    }

    public PlayerProfile getPlayerProfile() {
        if (rawAuthResult == null)
            return null;
        return rawAuthResult.playerProfile;
    }

    public String getAccessToken() {
        if (rawAuthResult == null)
            return null;
        return rawAuthResult.accessToken;
    }

    public void exit() {
        rawAuthResult = null;
        profile = null;
    }
}
