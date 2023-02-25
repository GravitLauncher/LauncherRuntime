package pro.gravit.launcher.client.gui.service;

import pro.gravit.launcher.events.request.AuthRequestEvent;
import pro.gravit.launcher.events.request.GetAvailabilityAuthRequestEvent;
import pro.gravit.launcher.events.request.ProfilesRequestEvent;
import pro.gravit.launcher.profiles.ClientProfile;
import pro.gravit.launcher.profiles.PlayerProfile;
import pro.gravit.launcher.profiles.optional.OptionalFile;
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
    private GetAvailabilityAuthRequestEvent.AuthAvailability authAvailability;

    public void setAuthResult(String authId, AuthRequestEvent rawAuthResult) {
        this.rawAuthResult = rawAuthResult;
        if(rawAuthResult.oauth != null) {
            Request.setOAuth(authId, rawAuthResult.oauth);
        }
    }

    public void setAuthAvailability(GetAvailabilityAuthRequestEvent.AuthAvailability info) {
        this.authAvailability = info;
    }

    public GetAvailabilityAuthRequestEvent.AuthAvailability getAuthAvailability() {
        return authAvailability;
    }

    public boolean isSupportedAuthFeature(String feature) {
        if(authAvailability == null || authAvailability.apiFeatures == null) {
            return false;
        }
        return authAvailability.apiFeatures.contains(feature);
    }

    public String getApiUrl() {
        if(authAvailability == null || authAvailability.apiUrl == null) {
            return null;
        }
        return authAvailability.apiUrl;
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
        for (ClientProfile profile : profiles) {
            OptionalView oldView = this.optionalViewMap.get(profile);
            OptionalView newView = oldView != null ? new OptionalView(profile, oldView) : new OptionalView(profile);
            this.optionalViewMap.put(profile, newView);
        }
    }

    public String getUsername() {
        if (rawAuthResult == null || rawAuthResult.playerProfile == null)
            return "Player";
        return rawAuthResult.playerProfile.username;
    }

    public String getMainRole() {
        if (rawAuthResult == null || rawAuthResult.permissions == null || rawAuthResult.permissions.getRoles() == null || rawAuthResult.permissions.getRoles().isEmpty())
            return "";
        return rawAuthResult.permissions.getRoles().get(0);
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
