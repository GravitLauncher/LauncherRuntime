package pro.gravit.launcher.client.gui;

import pro.gravit.launcher.events.request.AuthRequestEvent;
import pro.gravit.launcher.events.request.ProfilesRequestEvent;
import pro.gravit.launcher.profiles.ClientProfile;
import pro.gravit.launcher.profiles.PlayerProfile;
import pro.gravit.launcher.request.Request;

import java.util.List;

public class RuntimeStateMachine {
    private AuthRequestEvent rawAuthResult;

    private List<ClientProfile> profiles;
    private ClientProfile profile;

    public RuntimeStateMachine setAuthResult(AuthRequestEvent rawAuthResult) {
        this.rawAuthResult = rawAuthResult;
        if(rawAuthResult.session != 0) Request.setSession(rawAuthResult.session);
        return this;
    }

    public RuntimeStateMachine setProfilesResult(ProfilesRequestEvent rawProfilesResult) {
        this.profiles = rawProfilesResult.profiles;
        return this;
    }

    public String getUsername() {
        if(rawAuthResult == null || rawAuthResult.playerProfile == null) return "Player";
        return rawAuthResult.playerProfile.username;
    }

    public List<ClientProfile> getProfiles() {
        return profiles;
    }

    public void setProfile(ClientProfile profile) {
        this.profile = profile;
    }

    public ClientProfile getProfile() {
        return profile;
    }

    public PlayerProfile getPlayerProfile()
    {
        if(rawAuthResult == null) return null;
        return rawAuthResult.playerProfile;
    }
    public String getAccessToken()
    {
        if(rawAuthResult == null) return null;
        return rawAuthResult.accessToken;
    }
}
