package pro.gravit.launcher.client.gui.service;

import pro.gravit.launcher.events.request.AuthRequestEvent;
import pro.gravit.launcher.events.request.ProfilesRequestEvent;
import pro.gravit.launcher.profiles.ClientProfile;
import pro.gravit.launcher.profiles.PlayerProfile;
import pro.gravit.launcher.profiles.optional.OptionalView;
import pro.gravit.launcher.request.Request;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StateService {
    private AuthRequestEvent rawAuthResult;

    private List<ClientProfile> profiles;
    private ClientProfile profile;
    private Map<ClientProfile, OptionalView> optionalViewMap;

    public void setAuthResult(String authId, AuthRequestEvent rawAuthResult) {
        this.rawAuthResult = rawAuthResult;
        if(rawAuthResult.oauth != null) {
            Request.setOAuth(authId, rawAuthResult.oauth);
        }
    }

    public Map<ClientProfile, OptionalView> getOptionalViewMap() {
        return optionalViewMap;
    }

    public void setOptionalView(ClientProfile profile, OptionalView view) {
        optionalViewMap.put(profile, view);
    }

    public void setProfilesResult(ProfilesRequestEvent rawProfilesResult) {
        this.profiles = rawProfilesResult.profiles;
        this.profiles.sort(ClientProfile::compareTo);
        if (this.optionalViewMap == null) this.optionalViewMap = new HashMap<>();
        else this.optionalViewMap.clear();
        for (ClientProfile profile : profiles) {
            this.optionalViewMap.put(profile, new OptionalView(profile));
        }
    }

    public String getUsername() {
        if (rawAuthResult == null || rawAuthResult.playerProfile == null)
            return "Player";
        return rawAuthResult.playerProfile.username;
    }

    public boolean checkPermission(String name) {
        if(rawAuthResult == null || rawAuthResult.permissions == null) {
            return false;
        }
        return rawAuthResult.permissions.hasPerm(name);
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

    public OptionalView getOptionalView(ClientProfile profile) {
        return this.optionalViewMap.get(profile);
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